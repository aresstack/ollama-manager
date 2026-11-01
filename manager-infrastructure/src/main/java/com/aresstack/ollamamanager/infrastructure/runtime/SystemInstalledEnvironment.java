package com.aresstack.ollamamanager.infrastructure.runtime;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.application.port.InstalledEnvironmentPort;
import com.aresstack.ollamamanager.infrastructure.hardware.CommandResult;
import com.aresstack.ollamamanager.infrastructure.hardware.CommandRunner;
import com.aresstack.ollamamanager.infrastructure.hardware.ProcessCommandRunner;
import com.aresstack.ollamamanager.infrastructure.json.Json;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the {@code OLLAMA_*} configuration of an existing installation. On Windows the system environment is
 * overridden by the user environment, as for the Ollama app; on Linux a systemd {@code ollama.service} overrides the
 * manager's own environment. Variables only present in the manager's process environment are included next.
 * <p>
 * The Ollama Windows app can set the model folder in its own settings and pass it to the server it starts, so it
 * appears in no environment. The server writes its effective configuration to {@code server.log} at start; a model
 * folder found there is taken over when it differs from Ollama's default folder. Other values from the log are not
 * used, because the log lists defaults the user never chose.
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
    private final File serverLog;
    private final File defaultModelFolder;

    SystemInstalledEnvironment(CommandRunner runner, boolean windows, Map<String, String> processEnvironment) {
        this(runner, windows, processEnvironment, null, null);
    }

    /**
     * @param serverLog          the Ollama app's {@code server.log}, or {@code null}
     * @param defaultModelFolder Ollama's default model folder, used to tell a configured folder from the default
     */
    SystemInstalledEnvironment(CommandRunner runner, boolean windows, Map<String, String> processEnvironment,
                               File serverLog, File defaultModelFolder) {
        this.runner = runner;
        this.windows = windows;
        this.processEnvironment = processEnvironment;
        this.serverLog = serverLog;
        this.defaultModelFolder = defaultModelFolder;
    }

    public static SystemInstalledEnvironment forCurrentSystem() {
        boolean windows = String.valueOf(System.getProperty("os.name")).toLowerCase(Locale.ROOT).startsWith("windows");
        Map<String, String> environment = System.getenv();
        String localAppData = environment.get("LOCALAPPDATA");
        File log = windows && localAppData != null ? new File(new File(localAppData, "Ollama"), "server.log") : null;
        File models = new File(new File(System.getProperty("user.home"), ".ollama"), "models");
        return new SystemInstalledEnvironment(new ProcessCommandRunner(windows ? Charset.forName("UTF-8")
            : Charset.defaultCharset()), windows, environment, log, models);
    }

    @Override
    public String sourceDescription() {
        return windows ? "Windows environment and the Ollama app" : "ollama.service and the environment";
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
        if (!result.containsKey(MODELS) && serverLog != null) {
            String logged = loggedModelFolder(serverLog);
            if (logged != null && !sameFolder(logged, defaultModelFolder)) {
                result.put(MODELS, logged);
            }
        }
        return Collections.unmodifiableMap(result);
    }

    static final String MODELS = "OLLAMA_MODELS";
    private static final Pattern CONFIG_ENV = Pattern.compile("msg=\"server config\" env=\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern MAP_KEY = Pattern.compile("(?:^|\\s)([A-Z][A-Z0-9_]*):");

    /** Return the model folder of the most recent server start in the log, or {@code null}. */
    static String loggedModelFolder(File log) {
        String[] lines;
        try {
            // Decode leniently: one malformed byte from another program must not hide the configuration.
            lines = new String(Files.readAllBytes(log.toPath()), StandardCharsets.UTF_8).split("\r?\n");
        } catch (IOException | SecurityException e) {
            return null;
        }
        for (int i = lines.length - 1; i >= 0; i--) {
            Matcher matcher = CONFIG_ENV.matcher(lines[i]);
            if (matcher.find()) {
                String value = parseConfigMap(unescape(matcher.group(1))).get(MODELS);
                return value == null || value.trim().isEmpty() ? null : value.trim();
            }
        }
        return null;
    }

    /** Parse Go's {@code map[KEY:value KEY:value]} output; values may contain spaces. */
    static Map<String, String> parseConfigMap(String text) {
        Map<String, String> values = new LinkedHashMap<String, String>();
        String body = text.startsWith("map[") && text.endsWith("]") ? text.substring(4, text.length() - 1) : text;
        Matcher matcher = MAP_KEY.matcher(body);
        String key = null;
        int valueStart = 0;
        while (matcher.find()) {
            if (key != null) {
                values.put(key, body.substring(valueStart, matcher.start()).trim());
            }
            key = matcher.group(1);
            valueStart = matcher.end();
        }
        if (key != null) {
            values.put(key, body.substring(valueStart).trim());
        }
        return values;
    }

    private static String unescape(String quoted) {
        StringBuilder out = new StringBuilder(quoted.length());
        for (int i = 0; i < quoted.length(); i++) {
            char c = quoted.charAt(i);
            if (c == '\\' && i + 1 < quoted.length()) {
                out.append(quoted.charAt(++i));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static boolean sameFolder(String value, File folder) {
        if (folder == null) {
            return false;
        }
        String a = new File(value).getAbsolutePath().replace('\\', '/');
        String b = folder.getAbsolutePath().replace('\\', '/');
        while (a.endsWith("/")) {
            a = a.substring(0, a.length() - 1);
        }
        return a.equalsIgnoreCase(b);
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
