package com.aresstack.ollamamanager.infrastructure.runtime;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.ErrorKind;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.application.port.UserEnvironmentPort;
import com.aresstack.ollamamanager.infrastructure.hardware.CommandResult;
import com.aresstack.ollamamanager.infrastructure.hardware.CommandRunner;
import com.aresstack.ollamamanager.infrastructure.hardware.ProcessCommandRunner;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;

/**
 * Writes user environment variables on Windows through {@code [Environment]::SetEnvironmentVariable(..., 'User')},
 * which stores them in {@code HKCU\Environment} and broadcasts {@code WM_SETTINGCHANGE}, so newly started programs
 * such as the Ollama app see them. On other systems the port reports itself unsupported.
 */
public final class WindowsUserEnvironment implements UserEnvironmentPort {

    static final long TIMEOUT_MILLIS = 60000;

    private final CommandRunner runner;
    private final boolean windows;

    WindowsUserEnvironment(CommandRunner runner, boolean windows) {
        this.runner = runner;
        this.windows = windows;
    }

    public static WindowsUserEnvironment forCurrentSystem() {
        boolean windows = String.valueOf(System.getProperty("os.name")).toLowerCase(Locale.ROOT).startsWith("windows");
        return new WindowsUserEnvironment(new ProcessCommandRunner(Charset.forName("UTF-8")), windows);
    }

    @Override
    public boolean isSupported() {
        return windows;
    }

    @Override
    public String targetDescription() {
        return "Windows user environment variables";
    }

    @Override
    public void write(Map<String, String> variables, CancellationToken cancellation) throws OperationException {
        if (!windows) {
            throw new OperationException(ErrorKind.UNSUPPORTED_PLATFORM,
                "Writing user environment variables is only supported on Windows.");
        }
        String encoded = Base64.getEncoder().encodeToString(script(variables).getBytes(Charset.forName("UTF-16LE")));
        CommandResult result = runner.run(Arrays.asList("powershell.exe", "-NoProfile", "-NonInteractive",
            "-ExecutionPolicy", "Bypass", "-EncodedCommand", encoded), TIMEOUT_MILLIS, cancellation);
        if (!result.isFound()) {
            throw new OperationException(ErrorKind.PROCESS_FAILED, "powershell.exe was not found.");
        }
        if (!result.isSuccess()) {
            throw new OperationException(ErrorKind.PROCESS_FAILED, "Writing the user environment failed (exit "
                + result.exitCode() + "): " + result.stdout().trim());
        }
    }

    /** Build the PowerShell script; names and values are single-quoted literals, so nothing is interpreted. */
    static String script(Map<String, String> variables) {
        StringBuilder script = new StringBuilder("$ErrorActionPreference='Stop';");
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            script.append("[Environment]::SetEnvironmentVariable(").append(quote(entry.getKey())).append(',')
                .append(quote(entry.getValue())).append(",'User');");
        }
        return script.toString();
    }

    static String quote(String text) {
        return "'" + text.replace("'", "''").replace("‘", "‘‘").replace("’", "’’")
            + "'";
    }
}
