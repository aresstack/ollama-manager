package com.aresstack.ollamamanager.domain.server;

import com.aresstack.ollamamanager.domain.common.Preconditions;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable set of server-wide settings. Applying a change to a running server requires a restart.
 */
public final class ServerSettings {

    private static final ServerSettings DEFAULTS = new ServerSettings(Collections.<ServerSetting<?>, Object>emptyMap());

    private final Map<ServerSetting<?>, Object> values;

    private ServerSettings(Map<ServerSetting<?>, Object> values) {
        this.values = Collections.unmodifiableMap(new LinkedHashMap<ServerSetting<?>, Object>(values));
    }

    /** Return settings where every entry uses its default (loopback bind, everything else unset). */
    public static ServerSettings defaults() {
        return DEFAULTS;
    }

    /**
     * Restore settings from {@link #toEncodedMap()} output. Unknown ids are ignored.
     */
    public static ServerSettings fromEncodedMap(Map<String, String> encoded) {
        ServerSettings result = DEFAULTS;
        for (Map.Entry<String, String> entry : encoded.entrySet()) {
            ServerSetting<?> setting = ServerSettingCatalog.byId(entry.getKey());
            if (setting != null && entry.getValue() != null) {
                result = result.withDecoded(setting, entry.getValue());
            }
        }
        return result;
    }

    /**
     * Return the effective value: the chosen one, the default, or {@code null} if unset.
     */
    public <T> T get(ServerSetting<T> setting) {
        Object value = values.get(Preconditions.requireNonNull(setting, "setting"));
        return value == null ? setting.defaultValue() : setting.valueType().cast(value);
    }

    /**
     * Return a copy with a new value; {@code null} resets to the default.
     *
     * @throws IllegalArgumentException if the value is invalid
     */
    public <T> ServerSettings with(ServerSetting<T> setting, T value) {
        String error = setting.validate(value == null ? setting.defaultValue() : value);
        if (error != null) {
            throw new IllegalArgumentException(error);
        }
        Map<ServerSetting<?>, Object> copy = new LinkedHashMap<ServerSetting<?>, Object>(values);
        if (value == null) {
            copy.remove(setting);
        } else {
            copy.put(setting, value);
        }
        return new ServerSettings(copy);
    }

    public Endpoint bind() {
        return get(ServerSettingCatalog.BIND);
    }

    public Integer parallelism() {
        return get(ServerSettingCatalog.PARALLELISM);
    }

    public Integer loadedModels() {
        return get(ServerSettingCatalog.LOADED_MODELS);
    }

    public Integer queue() {
        return get(ServerSettingCatalog.QUEUE);
    }

    public Integer contextLength() {
        return get(ServerSettingCatalog.CONTEXT_LENGTH);
    }

    public KvCacheType kvCacheType() {
        return get(ServerSettingCatalog.KV_CACHE);
    }

    public Boolean flashAttention() {
        return get(ServerSettingCatalog.FLASH_ATTENTION);
    }

    public String keepAlive() {
        return get(ServerSettingCatalog.KEEP_ALIVE);
    }

    public File modelRoot() {
        return get(ServerSettingCatalog.MODEL_ROOT);
    }

    /**
     * Return the {@code OLLAMA_*} entries for {@code ProcessBuilder.environment()}. Unset settings are omitted.
     */
    public Map<String, String> toEnvironment() {
        Map<String, String> environment = new LinkedHashMap<String, String>();
        for (ServerSetting<?> setting : ServerSettingCatalog.all()) {
            String encoded = encodedValue(setting);
            if (encoded != null) {
                environment.put(setting.environmentVariable(), encoded);
            }
        }
        return Collections.unmodifiableMap(environment);
    }

    /**
     * Return the explicitly chosen values keyed by setting id, for persistence.
     */
    public Map<String, String> toEncodedMap() {
        Map<String, String> encoded = new LinkedHashMap<String, String>();
        for (ServerSetting<?> setting : values.keySet()) {
            encoded.put(setting.id(), encodedValue(setting));
        }
        return Collections.unmodifiableMap(encoded);
    }

    /**
     * Return the settings whose effective value differs from {@code other}.
     */
    public List<ServerSetting<?>> differencesTo(ServerSettings other) {
        List<ServerSetting<?>> changed = new ArrayList<ServerSetting<?>>();
        for (ServerSetting<?> setting : ServerSettingCatalog.all()) {
            Object mine = get(setting);
            Object theirs = other.get(setting);
            if (mine == null ? theirs != null : !mine.equals(theirs)) {
                changed.add(setting);
            }
        }
        return Collections.unmodifiableList(changed);
    }

    /**
     * Return whether a server running with {@code applied} must restart to use these settings.
     */
    public boolean requiresRestartComparedTo(ServerSettings applied) {
        for (ServerSetting<?> setting : differencesTo(applied)) {
            if (setting.isRestartRequired()) {
                return true;
            }
        }
        return false;
    }

    private <T> String encodedValue(ServerSetting<T> setting) {
        T value = get(setting);
        return value == null ? null : setting.encode(value);
    }

    private <T> ServerSettings withDecoded(ServerSetting<T> setting, String encoded) {
        return with(setting, setting.decode(encoded));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ServerSettings && differencesTo((ServerSettings) other).isEmpty();
    }

    @Override
    public int hashCode() {
        List<Object> effectiveValues = new ArrayList<Object>();
        for (ServerSetting<?> setting : ServerSettingCatalog.all()) {
            effectiveValues.add(get(setting));
        }
        return effectiveValues.hashCode();
    }

    @Override
    public String toString() {
        return "ServerSettings" + toEnvironment();
    }
}
