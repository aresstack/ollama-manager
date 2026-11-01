package com.aresstack.ollamamanager.infrastructure.runtime;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.ErrorKind;
import com.aresstack.ollamamanager.application.common.OperationCancelledException;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.application.port.ExternalServerControlPort;
import com.aresstack.ollamamanager.application.port.ExternalServerProcess;
import com.aresstack.ollamamanager.domain.server.Endpoint;
import com.aresstack.ollamamanager.infrastructure.hardware.CommandResult;
import com.aresstack.ollamamanager.infrastructure.hardware.CommandRunner;
import com.aresstack.ollamamanager.infrastructure.json.Json;

import java.io.File;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

/**
 * Windows adapter: finds the listener with {@code Get-NetTCPConnection} and CIM, and terminates it with
 * {@code taskkill}. Needs no administrator rights for processes of the current user.
 */
public final class WindowsExternalServerControl implements ExternalServerControlPort {

    static final long COMMAND_TIMEOUT_MILLIS = 20000;

    private final CommandRunner runner;

    public WindowsExternalServerControl(CommandRunner runner) {
        this.runner = runner;
    }

    static String findScript(int port) {
        return "$ErrorActionPreference='Stop';[Console]::OutputEncoding=[Text.Encoding]::UTF8;"
            + "$c=@(Get-NetTCPConnection -State Listen -LocalPort " + port + " -ErrorAction SilentlyContinue);"
            + "if($c.Count -eq 0){'{}';exit 0};"
            + "$id=[int]$c[0].OwningProcess;"
            + "$p=Get-CimInstance Win32_Process -Filter (\"ProcessId=\"+$id);"
            + "$q=$null;if($p){$q=Get-CimInstance Win32_Process -Filter (\"ProcessId=\"+$p.ParentProcessId)};"
            + "[pscustomobject]@{pid=$id;name=$p.Name;path=$p.ExecutablePath;ppid=$p.ParentProcessId;"
            + "pname=$q.Name} | ConvertTo-Json -Compress";
    }

    @Override
    public ExternalServerProcess find(Endpoint endpoint, CancellationToken cancellation) throws OperationException {
        String encoded = Base64.getEncoder().encodeToString(
            findScript(endpoint.port()).getBytes(Charset.forName("UTF-16LE")));
        CommandResult result = runner.run(Arrays.asList("powershell.exe", "-NoProfile", "-NonInteractive",
            "-ExecutionPolicy", "Bypass", "-EncodedCommand", encoded), COMMAND_TIMEOUT_MILLIS, cancellation);
        if (!result.isSuccess()) {
            throw new OperationException(ErrorKind.PROCESS_FAILED,
                "could not determine the process on port " + endpoint.port() + " (PowerShell exit "
                    + result.exitCode() + ")");
        }
        return parse(result.stdout());
    }

    static ExternalServerProcess parse(String json) {
        Object root;
        try {
            root = Json.parse(json.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (!(root instanceof Map)) {
            return null;
        }
        Map<?, ?> map = (Map<?, ?>) root;
        long pid = number(map.get("pid"));
        if (pid <= 0) {
            return null;
        }
        Object path = map.get("path");
        File executable = path instanceof String && !((String) path).trim().isEmpty()
            ? new File(((String) path).trim()) : null;
        Object name = map.get("name");
        Object parentName = map.get("pname");
        return new ExternalServerProcess(pid, name instanceof String ? (String) name : "", executable,
            number(map.get("ppid")), parentName instanceof String ? (String) parentName : null);
    }

    private static long number(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0;
    }

    @Override
    public void terminate(ExternalServerProcess process, long gracePeriodMillis, CancellationToken cancellation)
        throws OperationException {
        // The Ollama tray app restarts a server that exits, so it goes first, together with its process tree.
        if (process.hasOllamaSupervisor()) {
            kill(process.supervisorPid(), gracePeriodMillis, cancellation);
        }
        kill(process.pid(), gracePeriodMillis, cancellation);
    }

    private void kill(long pid, long gracePeriodMillis, CancellationToken cancellation) throws OperationException {
        if (!isAlive(pid, cancellation)) {
            return;
        }
        String id = Long.toString(pid);
        runner.run(Arrays.asList("taskkill", "/PID", id, "/T"), COMMAND_TIMEOUT_MILLIS, cancellation);
        long deadline = System.currentTimeMillis() + gracePeriodMillis;
        while (System.currentTimeMillis() < deadline) {
            if (!isAlive(pid, cancellation)) {
                return;
            }
            pause(250);
        }
        CommandResult forced = runner.run(Arrays.asList("taskkill", "/PID", id, "/T", "/F"),
            COMMAND_TIMEOUT_MILLIS, cancellation);
        for (int i = 0; i < 20; i++) {
            if (!isAlive(pid, cancellation)) {
                return;
            }
            pause(250);
        }
        throw new OperationException(ErrorKind.PROCESS_FAILED, "process " + pid + " could not be stopped"
            + (forced.isFound() ? " (taskkill exit " + forced.exitCode() + ")" : "")
            + "; it may run as another user or as administrator");
    }

    private boolean isAlive(long pid, CancellationToken cancellation) throws OperationException {
        CommandResult result = runner.run(Arrays.asList("tasklist", "/FI", "PID eq " + pid, "/NH", "/FO", "CSV"),
            COMMAND_TIMEOUT_MILLIS, cancellation);
        return result.isSuccess() && result.stdout().contains("\"" + pid + "\"");
    }

    private static void pause(long millis) throws OperationCancelledException {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OperationCancelledException("interrupted while stopping a process");
        }
    }
}
