package com.aresstack.ollamamanager.infrastructure.runtime;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.application.port.InstalledEnvironmentPort;
import com.aresstack.ollamamanager.infrastructure.hardware.CommandResult;
import com.aresstack.ollamamanager.infrastructure.hardware.CommandRunner;
import com.aresstack.ollamamanager.infrastructure.hardware.ProcessCommandRunner;
import com.aresstack.ollamamanager.infrastructure.json.Json;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads the {@code OLLAMA_*} configuration of an existing installation. On Windows the system environment is
 * overridden by the user environment, as for the Ollama app; on Linux a systemd {@code ollama.service} overrides the
 * manager's own environment. Variables only present in the manager's process environment are included last.
 */
public final class SystemInstalledEnvironment implements InstalledEnvironmentPort {

    static final long TIMEOUT_MILLIS = 15000;

    static final String WINDOWS_SCRIPT = "$ErrorActionPreference='Stop';[Console]::OutputEncoding=[Text.Encoding]::UTF8;"
        + "$r=@{};foreach($t in 'Machine','User'){$e=[Environment]::GetEnvironmentVariables($t);"
        + "foreach($k in $e.Keys){if($k -like 'OLLAMA_*'){$r[$k.ToUpper()]=[string]$e[$k]}}};"
        + "$r | ConvertTo-Json -Compress";

    private final CommandRunner runner;
    private final boolean windows;
    private final Map<String, String> processEnvironment;

    SystemInstalledEnvironment(CommandRunner runner, boolean windows, Map<String, String> processEnvironment) {
        this.runner = runner;
        this.windows = windows;
        this.processEnvironment = processEnvironment;
    }

    public static SystemInstalledEnvironment forCurrentSystem() {
        boolean windows = String.valueOf(System.getProperty("os.name")).toLowerCase(Locale.ROOT).startsWith("windows");
        return new SystemInstalledEnvironment(new ProcessCommandRunner(windows ? Charset.forName("UTF-8")
            : Charset.defaultCharset()), windows, System.getenv());
    }

    @Override
    public String sourceDescription() {
        return windows ? "Windows user and system environment" : "ollama.service and the environment";
    }

    @Override
    public Map<String, String> ollamaVariables(CancellationToken cancellation) throws OperationException {
        Map<String, String> result = new LinkedHashMap<String, String>();
        Map<String, String> configured = windows ? windowsVariables(cancellation) : systemdVariables(cancellation);
        result.putAll(configured);
        for (Map.Entry<String, String> entry : processEnvironment.entrySet()) {
            String name = entry.getKey().toUpperCase(Locale.ROOT);
            if (name.startsWith("OLLAMA_") && !result.containsKey(name)) {
                result.put(name, entry.getValue());
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private Map<String, String> windowsVariables(CancellationToken cancellation) throws OperationException {
        String encoded = Base64.getEncoder().encodeToString(WINDOWS_SCRIPT.getBytes(Charset.forName("UTF-16LE")));
        CommandResult result = runner.run(Arrays.asList("powershell.exe", "-NoProfile", "-NonInteractive",
            "-ExecutionPolicy", "Bypass", "-EncodedCommand", encoded), TIMEOUT_MILLIS, cancellation);
        return result.isSuccess() ? parseJsonObject(result.stdout()) : Collections.<String, String>emptyMap();
    }

    static Map<String, String> parseJsonObject(String json) {
        Map<String, String> values = new LinkedHashMap<String, String>();
        Object root;
        try {
            root = Json.parse(json.trim());
        } catch (IllegalArgumentException e) {
            return values;
        }
        if (root instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) root).entrySet()) {
                if (entry.getKey() instanceof String && entry.getValue() instanceof String) {
                    values.put(((String) entry.getKey()).toUpperCase(Locale.ROOT), (String) entry.getValue());
                }
            }
        }
        return values;
    }

    private Map<String, String> systemdVariables(CancellationToken cancellation) throws OperationException {
        CommandResult result = runner.run(Arrays.asList("systemctl", "show", "ollama.service", "-p", "Environment",
            "--value"), TIMEOUT_MILLIS, cancellation);
        return result.isSuccess() ? parseSystemdEnvironment(result.stdout()) : Collections.<String, String>emptyMap();
    }

    /** Parse {@code systemctl show -p Environment --value}: space-separated assignments, quoted if needed. */
    static Map<String, String> parseSystemdEnvironment(String text) {
        Map<String, String> values = new LinkedHashMap<String, String>();
        for (String token : tokens(text.trim())) {
            int equals = token.indexOf('=');
            if (equals > 0) {
                String name = token.substring(0, equals).toUpperCase(Locale.ROOT);
                if (name.startsWith("OLLAMA_")) {
                    values.put(name, token.substring(equals + 1));
                }
            }
        }
        return values;
    }

    private static List<String> tokens(String text) {
        List<String> tokens = new ArrayList<String>();
        StringBuilder current = new StringBuilder();
        char quote = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                } else {
                    current.append(c);
                }
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (Character.isWhitespace(c)) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            tokens.add(current.toString());
        }
        return tokens;
    }
}
