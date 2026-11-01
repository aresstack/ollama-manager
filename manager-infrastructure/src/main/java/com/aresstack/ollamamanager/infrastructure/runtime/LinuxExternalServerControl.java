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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Linux adapter: finds the listener with {@code ss} (or {@code lsof}) and {@code /proc}, and terminates it with
 * {@code kill}. Processes of other users, such as a systemd service, are not revealed and therefore not found.
 */
public final class LinuxExternalServerControl implements ExternalServerControlPort {

    static final long COMMAND_TIMEOUT_MILLIS = 10000;
    private static final Pattern SS_PID = Pattern.compile("pid=(\\d+)");
    private static final Pattern FIRST_NUMBER = Pattern.compile("(?m)^\\s*(\\d+)\\s*$");

    private final CommandRunner runner;
    private final File procRoot;

    public LinuxExternalServerControl(CommandRunner runner) {
        this(runner, new File("/proc"));
    }

    LinuxExternalServerControl(CommandRunner runner, File procRoot) {
        this.runner = runner;
        this.procRoot = procRoot;
    }

    @Override
    public ExternalServerProcess find(Endpoint endpoint, CancellationToken cancellation) throws OperationException {
        long pid = listenerPid(endpoint.port(), cancellation);
        if (pid <= 0) {
            return null;
        }
        String name = read(new File(new File(procRoot, Long.toString(pid)), "comm"));
        File executable = null;
        try {
            executable = Files.readSymbolicLink(new File(new File(procRoot, Long.toString(pid)), "exe").toPath())
                .toFile();
        } catch (IOException | UnsupportedOperationException | SecurityException ignored) {
            // The binary stays unknown.
        }
        long parent = parentPid(pid);
        String parentName = parent > 0 ? read(new File(new File(procRoot, Long.toString(parent)), "comm")) : null;
        return new ExternalServerProcess(pid, name, executable, parent, parentName);
    }

    long listenerPid(int port, CancellationToken cancellation) throws OperationException {
        CommandResult ss = runner.run(Arrays.asList("ss", "-H", "-l", "-t", "-n", "-p", "sport = :" + port),
            COMMAND_TIMEOUT_MILLIS, cancellation);
        if (ss.isSuccess()) {
            Matcher matcher = SS_PID.matcher(ss.stdout());
            if (matcher.find()) {
                return Long.parseLong(matcher.group(1));
            }
        }
        CommandResult lsof = runner.run(Arrays.asList("lsof", "-nP", "-t", "-iTCP:" + port, "-sTCP:LISTEN"),
            COMMAND_TIMEOUT_MILLIS, cancellation);
        if (lsof.isFound()) {
            Matcher matcher = FIRST_NUMBER.matcher(lsof.stdout());
            if (matcher.find()) {
                return Long.parseLong(matcher.group(1));
            }
        }
        return 0;
    }

    /** Parse the parent id from {@code /proc/<pid>/stat}; the name field may contain spaces and parentheses. */
    long parentPid(long pid) {
        String stat = read(new File(new File(procRoot, Long.toString(pid)), "stat"));
        if (stat == null) {
            return 0;
        }
        int end = stat.lastIndexOf(')');
        if (end < 0) {
            return 0;
        }
        String[] fields = stat.substring(end + 1).trim().split("\\s+");
        try {
            return fields.length > 1 ? Long.parseLong(fields[1]) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public void terminate(ExternalServerProcess process, long gracePeriodMillis, CancellationToken cancellation)
        throws OperationException {
        // Never kill a supervisor here: on Linux the parent is a shell, a terminal or systemd, not an Ollama app.
        long pid = process.pid();
        String id = Long.toString(pid);
        runner.run(Arrays.asList("kill", "-TERM", id), COMMAND_TIMEOUT_MILLIS, cancellation);
        long deadline = System.currentTimeMillis() + gracePeriodMillis;
        while (System.currentTimeMillis() < deadline) {
            if (!isAlive(pid)) {
                return;
            }
            pause(100);
        }
        runner.run(Arrays.asList("kill", "-KILL", id), COMMAND_TIMEOUT_MILLIS, cancellation);
        for (int i = 0; i < 50; i++) {
            if (!isAlive(pid)) {
                return;
            }
            pause(100);
        }
        throw new OperationException(ErrorKind.PROCESS_FAILED, process + " could not be stopped; it may belong to"
            + " another user");
    }

    private boolean isAlive(long pid) {
        String stat = read(new File(new File(procRoot, Long.toString(pid)), "stat"));
        if (stat == null) {
            return false;
        }
        int end = stat.lastIndexOf(')');
        // A zombie has exited and only waits for its parent.
        return end < 0 || !stat.substring(end + 1).trim().startsWith("Z");
    }

    private static String read(File file) {
        try {
            return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).trim();
        } catch (IOException | SecurityException e) {
            return null;
        }
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
