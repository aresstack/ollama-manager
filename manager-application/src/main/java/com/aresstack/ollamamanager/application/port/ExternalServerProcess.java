package com.aresstack.ollamamanager.application.port;

import java.io.File;
import java.util.Locale;

/**
 * Operating system process that listens on a server endpoint, as found by {@link ExternalServerControlPort}.
 */
public final class ExternalServerProcess {

    private final long pid;
    private final String name;
    private final File executable;
    private final long supervisorPid;
    private final String supervisorName;

    /**
     * @param executable     full path of the binary, or {@code null} when the system does not report it
     * @param supervisorPid  parent process id, or {@code 0} when unknown
     * @param supervisorName parent process name, or {@code null} when unknown
     */
    public ExternalServerProcess(long pid, String name, File executable, long supervisorPid, String supervisorName) {
        if (pid <= 0) {
            throw new IllegalArgumentException("pid must be positive: " + pid);
        }
        this.pid = pid;
        this.name = name == null ? "" : name.trim();
        this.executable = executable;
        this.supervisorPid = supervisorPid;
        this.supervisorName = supervisorName == null ? null : supervisorName.trim();
    }

    public long pid() {
        return pid;
    }

    public String name() {
        return name;
    }

    /** @return the binary of the process, or {@code null} if unknown */
    public File executable() {
        return executable;
    }

    /** @return whether the process image is an Ollama binary, judged by its name */
    public boolean isOllama() {
        return isOllamaName(name);
    }

    /**
     * @return whether the parent is an Ollama process too, typically the Windows tray app that restarts the server
     *         when it exits
     */
    public boolean hasOllamaSupervisor() {
        return supervisorPid > 0 && isOllamaName(supervisorName);
    }

    public long supervisorPid() {
        return supervisorPid;
    }

    public String supervisorName() {
        return supervisorName;
    }

    static boolean isOllamaName(String value) {
        return value != null && value.trim().toLowerCase(Locale.ROOT).startsWith("ollama");
    }

    @Override
    public String toString() {
        return name + " (pid " + pid + ")";
    }
}
