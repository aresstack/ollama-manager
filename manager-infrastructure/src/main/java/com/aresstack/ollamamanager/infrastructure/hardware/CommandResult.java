package com.aresstack.ollamamanager.infrastructure.hardware;

/**
 * Outcome of an external diagnostic command. A missing tool is a normal result, not an error.
 */
public final class CommandResult {

    private static final CommandResult NOT_FOUND = new CommandResult(false, -1, "");

    private final boolean found;
    private final int exitCode;
    private final String stdout;

    private CommandResult(boolean found, int exitCode, String stdout) {
        this.found = found;
        this.exitCode = exitCode;
        this.stdout = stdout;
    }

    public static CommandResult notFound() {
        return NOT_FOUND;
    }

    public static CommandResult completed(int exitCode, String stdout) {
        return new CommandResult(true, exitCode, stdout == null ? "" : stdout);
    }

    public boolean isFound() {
        return found;
    }

    public boolean isSuccess() {
        return found && exitCode == 0;
    }

    public int exitCode() {
        return exitCode;
    }

    public String stdout() {
        return stdout;
    }
}
