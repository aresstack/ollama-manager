package com.aresstack.ollamamanager.swing.section;

import com.aresstack.ollamamanager.domain.server.ServerSetting;
import com.aresstack.ollamamanager.domain.server.ServerSettingCatalog;
import com.aresstack.ollamamanager.domain.server.ServerSettings;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Converts edited text values into {@link ServerSettings}. An empty text means "not set": Ollama's default
 * applies. Each setting is checked on its own so that the form can mark every invalid field at once.
 */
final class SettingsDraft {

    private final ServerSettings settings;
    private final Map<ServerSetting<?>, String> errors;

    private SettingsDraft(ServerSettings settings, Map<ServerSetting<?>, String> errors) {
        this.settings = settings;
        this.errors = Collections.unmodifiableMap(errors);
    }

    /** Text form of {@code settings}, keyed by setting; unset settings map to an empty string. */
    static Map<ServerSetting<?>, String> texts(ServerSettings settings) {
        Map<String, String> encoded = settings.toEncodedMap();
        Map<ServerSetting<?>, String> texts = new LinkedHashMap<ServerSetting<?>, String>();
        for (ServerSetting<?> setting : ServerSettingCatalog.all()) {
            String value = encoded.get(setting.id());
            texts.put(setting, value == null ? "" : value);
        }
        return texts;
    }

    /** Parse all texts; missing entries count as not set. */
    static SettingsDraft parse(Map<ServerSetting<?>, String> texts) {
        ServerSettings result = ServerSettings.defaults();
        Map<ServerSetting<?>, String> errors = new LinkedHashMap<ServerSetting<?>, String>();
        for (ServerSetting<?> setting : ServerSettingCatalog.all()) {
            String text = texts.get(setting);
            try {
                result = apply(result, setting, text == null ? "" : text.trim());
            } catch (IllegalArgumentException e) {
                errors.put(setting, e.getMessage() == null ? setting.label() + " is invalid" : e.getMessage());
            }
        }
        return new SettingsDraft(errors.isEmpty() ? result : null, errors);
    }

    private static <T> ServerSettings apply(ServerSettings settings, ServerSetting<T> setting, String text) {
        return settings.with(setting, text.isEmpty() ? null : setting.decode(text));
    }

    boolean isValid() {
        return errors.isEmpty();
    }

    /** @return the parsed settings, or {@code null} when any field is invalid */
    ServerSettings settings() {
        return settings;
    }

    Map<ServerSetting<?>, String> errors() {
        return errors;
    }
}
