package com.aresstack.ollamamanager.application.configuration;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.application.port.InstalledEnvironmentPort;
import com.aresstack.ollamamanager.domain.configuration.ManagerConfiguration;
import com.aresstack.ollamamanager.domain.server.Endpoint;
import com.aresstack.ollamamanager.domain.server.ServerSetting;
import com.aresstack.ollamamanager.domain.server.ServerSettingCatalog;
import com.aresstack.ollamamanager.domain.server.ServerSettings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Takes over the server settings an existing Ollama installation is configured with, such as its model folder.
 * <p>
 * A configured variable counts as the user's explicit choice: it is stored as a chosen setting, so a server the
 * manager starts uses the same value. A setting the user already chose in the manager is never overwritten.
 * Writes go through {@link ConfigurationService}.
 */
public final class ExistingSettingsImport {

    private final ConfigurationService configuration;
    private final InstalledEnvironmentPort environment;

    public ExistingSettingsImport(ConfigurationService configuration, InstalledEnvironmentPort environment) {
        if (configuration == null || environment == null) {
            throw new IllegalArgumentException("configuration and environment must not be null");
        }
        this.configuration = configuration;
        this.environment = environment;
    }

    /** @return where detected values come from, for display */
    public String sourceDescription() {
        return environment.sourceDescription();
    }

    /** Read the configured values; variables that are malformed for their setting are left out. */
    public Detected detect(CancellationToken cancellation) throws OperationException {
        Map<String, String> variables = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> entry : environment.ollamaVariables(cancellation).entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                variables.put(entry.getKey().toUpperCase(Locale.ROOT), entry.getValue());
            }
        }
        Map<ServerSetting<?>, String> values = new LinkedHashMap<ServerSetting<?>, String>();
        Map<String, String> rejected = new LinkedHashMap<String, String>();
        ServerSettings probe = ServerSettings.defaults();
        for (ServerSetting<?> setting : ServerSettingCatalog.all()) {
            String raw = variables.get(setting.environmentVariable());
            if (raw == null || raw.trim().isEmpty()) {
                continue;
            }
            try {
                probe = withDecoded(probe, setting, raw.trim());
                values.put(setting, raw.trim());
            } catch (IllegalArgumentException e) {
                rejected.put(setting.environmentVariable(), String.valueOf(e.getMessage()));
            }
        }
        return new Detected(values, probe, rejected);
    }

    /**
     * Store every detected value whose setting the user has not chosen yet. Nothing is written when there is
     * nothing to take over.
     *
     * @return the settings that were taken over
     */
    public List<ServerSetting<?>> importUnchosen(CancellationToken cancellation) throws OperationException {
        final Detected detected = detect(cancellation);
        if (detected.values().isEmpty()) {
            return Collections.emptyList();
        }
        final List<ServerSetting<?>> imported = new ArrayList<ServerSetting<?>>();
        ManagerConfiguration current = configuration.current();
        if (missing(current.desiredServerSettings(), detected).isEmpty()) {
            return Collections.emptyList();
        }
        cancellation.throwIfCancelled();
        configuration.update(latest -> {
            imported.clear();
            ServerSettings settings = latest.desiredServerSettings();
            for (ServerSetting<?> setting : missing(settings, detected)) {
                settings = copy(settings, detected.settings(), setting);
                imported.add(setting);
            }
            return imported.isEmpty() ? latest : latest.withDesiredServerSettings(settings);
        });
        return Collections.unmodifiableList(imported);
    }

    private static List<ServerSetting<?>> missing(ServerSettings settings, Detected detected) {
        List<ServerSetting<?>> missing = new ArrayList<ServerSetting<?>>();
        for (ServerSetting<?> setting : detected.values().keySet()) {
            if (!settings.isChosen(setting)) {
                missing.add(setting);
            }
        }
        return missing;
    }

    private static <T> ServerSettings withDecoded(ServerSettings settings, ServerSetting<T> setting, String text) {
        if (setting == ServerSettingCatalog.BIND) {
            // Ollama accepts more OLLAMA_HOST forms than host:port, for example "0.0.0.0" or "http://host:port".
            return settings.with(ServerSettingCatalog.BIND, Endpoint.parseOllamaHost(text));
        }
        return settings.with(setting, setting.decode(text));
    }

    private static <T> ServerSettings copy(ServerSettings target, ServerSettings source, ServerSetting<T> setting) {
        return target.with(setting, source.get(setting));
    }

    /** Values found for an existing installation. */
    public static final class Detected {
        private final Map<ServerSetting<?>, String> values;
        private final ServerSettings settings;
        private final Map<String, String> rejected;

        Detected(Map<ServerSetting<?>, String> values, ServerSettings settings, Map<String, String> rejected) {
            this.values = Collections.unmodifiableMap(values);
            this.settings = settings;
            this.rejected = Collections.unmodifiableMap(rejected);
        }

        /** @return raw variable value per setting, only for settings with a usable value */
        public Map<ServerSetting<?>, String> values() {
            return values;
        }

        /** @return the detected values as chosen settings, everything else not chosen */
        public ServerSettings settings() {
            return settings;
        }

        /** @return variable name to reason for values that could not be used */
        public Map<String, String> rejected() {
            return rejected;
        }

        /** @return whether {@code stored} uses the detected value for {@code setting} */
        public boolean matches(ServerSettings stored, ServerSetting<?> setting) {
            return values.containsKey(setting) && stored.isChosen(setting)
                && stored.get(setting).equals(settings.get(setting));
        }
    }
}
