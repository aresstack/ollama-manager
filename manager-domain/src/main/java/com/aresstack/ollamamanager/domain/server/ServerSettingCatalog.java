package com.aresstack.ollamamanager.domain.server;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * All server-wide settings the manager controls. Every entry maps to one {@code OLLAMA_*} variable
 * and requires a restart of an owned server.
 */
public final class ServerSettingCatalog {

    private static final Pattern DURATION = Pattern.compile("-1|0|\\d+(ms|s|m|h)");

    public static final ServerSetting<Endpoint> BIND = ServerSetting.builder("bind", Endpoint.class)
        .label("Network address")
        .tooltip("Address and port the server listens on. Keep 127.0.0.1 to allow only local programs.")
        .optionType(OptionType.ENDPOINT)
        .environmentVariable("OLLAMA_HOST")
        .defaultValue(Endpoint.DEFAULT)
        .encoder(Endpoint::toHostValue)
        .decoder(Endpoint::parse)
        .build();

    public static final ServerSetting<Integer> PARALLELISM = integer("parallelism", "OLLAMA_NUM_PARALLEL",
        "Parallel requests per model",
        "How many requests one loaded model answers at the same time. Higher values need more memory.", 1, 256);

    public static final ServerSetting<Integer> LOADED_MODELS = integer("loadedModels", "OLLAMA_MAX_LOADED_MODELS",
        "Models kept loaded", "How many different models may stay in memory at once.", 1, 64);

    public static final ServerSetting<Integer> QUEUE = integer("queue", "OLLAMA_MAX_QUEUE",
        "Waiting requests", "How many requests may wait before the server answers 'busy' (HTTP 503).", 1, 100000);

    public static final ServerSetting<Integer> CONTEXT_LENGTH = integer("contextLength", "OLLAMA_CONTEXT_LENGTH",
        "Default context length",
        "Tokens of context a model uses when the client does not ask for a specific size.", 256, 1048576);

    public static final ServerSetting<KvCacheType> KV_CACHE = ServerSetting.builder("kvCache", KvCacheType.class)
        .label("Context memory precision")
        .tooltip("Lower precision lets longer contexts or more parallel requests fit into GPU memory.")
        .optionType(OptionType.CHOICE)
        .environmentVariable("OLLAMA_KV_CACHE_TYPE")
        .choices(Collections.unmodifiableList(Arrays.asList(KvCacheType.values())))
        .encoder(KvCacheType::environmentValue)
        .decoder(KvCacheType::fromEnvironmentValue)
        .build();

    public static final ServerSetting<Boolean> FLASH_ATTENTION = ServerSetting.builder("flashAttention", Boolean.class)
        .label("Flash attention")
        .tooltip("Faster, more memory-efficient attention on supported GPUs. Required for reduced context precision.")
        .optionType(OptionType.BOOLEAN)
        .environmentVariable("OLLAMA_FLASH_ATTENTION")
        .encoder(value -> value ? "1" : "0")
        .decoder(ServerSettingCatalog::parseBoolean)
        .build();

    public static final ServerSetting<String> KEEP_ALIVE = ServerSetting.builder("keepAlive", String.class)
        .label("Keep models loaded for")
        .tooltip("How long an idle model stays in memory, for example 5m or 1h. -1 keeps it loaded, 0 unloads at once.")
        .optionType(OptionType.DURATION)
        .environmentVariable("OLLAMA_KEEP_ALIVE")
        .validator(value -> DURATION.matcher(value).matches() ? null : "use a duration like 30s, 5m, 1h, 0 or -1")
        .decoder(String::trim)
        .build();

    public static final ServerSetting<File> MODEL_ROOT = ServerSetting.builder("modelRoot", File.class)
        .label("Model folder")
        .tooltip("Folder where downloaded models are stored. Leave empty to use Ollama's default folder.")
        .optionType(OptionType.DIRECTORY)
        .environmentVariable("OLLAMA_MODELS")
        .validator(value -> value.isAbsolute() ? null : "use an absolute path")
        .encoder(File::getPath)
        .decoder(File::new)
        .build();

    private static final List<ServerSetting<?>> ALL = Collections.unmodifiableList(Arrays.<ServerSetting<?>>asList(
        BIND, PARALLELISM, LOADED_MODELS, QUEUE, CONTEXT_LENGTH, KV_CACHE, FLASH_ATTENTION, KEEP_ALIVE, MODEL_ROOT));

    private ServerSettingCatalog() {
    }

    public static List<ServerSetting<?>> all() {
        return ALL;
    }

    /**
     * Return every environment variable the manager controls. A runtime adapter removes these from the
     * inherited child environment before applying {@link ServerSettings#toEnvironment()}, so "unset" really
     * means Ollama's built-in default and not a value inherited from the user's environment.
     */
    public static Set<String> environmentVariables() {
        Set<String> names = new LinkedHashSet<String>();
        for (ServerSetting<?> setting : ALL) {
            names.add(setting.environmentVariable());
        }
        return Collections.unmodifiableSet(names);
    }

    /**
     * Find a setting by its persistent id, or return {@code null}.
     */
    public static ServerSetting<?> byId(String id) {
        for (ServerSetting<?> setting : ALL) {
            if (setting.id().equals(id)) {
                return setting;
            }
        }
        return null;
    }

    private static ServerSetting<Integer> integer(String id, String variable, String label, String tooltip,
                                                  int min, int max) {
        return ServerSetting.builder(id, Integer.class)
            .label(label)
            .tooltip(tooltip)
            .optionType(OptionType.INTEGER)
            .environmentVariable(variable)
            .range(min, max)
            .decoder(text -> Integer.valueOf(text.trim()))
            .build();
    }

    private static Boolean parseBoolean(String text) {
        String value = text.trim();
        if (value.equals("1") || value.equalsIgnoreCase("true")) {
            return Boolean.TRUE;
        }
        if (value.equals("0") || value.equalsIgnoreCase("false")) {
            return Boolean.FALSE;
        }
        throw new IllegalArgumentException("not a boolean: " + text);
    }
}
