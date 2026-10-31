package com.aresstack.ollamamanager.infrastructure.ollamaapi;

import com.aresstack.ollamamanager.domain.model.InstalledModel;
import com.aresstack.ollamamanager.domain.model.LoadedModel;
import com.aresstack.ollamamanager.domain.model.ModelDetails;
import com.aresstack.ollamamanager.domain.model.ModelIdentity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Maps parsed Ollama JSON trees to domain types. Every unexpected shape raises
 * {@link IllegalArgumentException}, which the client reports as {@code NOT_OLLAMA}.
 */
final class OllamaResponseMapper {

    private OllamaResponseMapper() {
    }

    static String version(Object root) {
        return requiredString(asMap(root, "version response"), "version");
    }

    static List<InstalledModel> installedModels(Object root) {
        List<InstalledModel> models = new ArrayList<InstalledModel>();
        for (Object entry : modelList(root)) {
            Map<String, Object> model = asMap(entry, "model");
            Map<String, Object> details = optionalMap(model, "details");
            models.add(new InstalledModel(identity(model, details), requiredLong(model, "size"),
                optionalString(details, "family"), optionalString(details, "parameter_size"),
                optionalString(model, "modified_at")));
        }
        return Collections.unmodifiableList(models);
    }

    static List<LoadedModel> loadedModels(Object root) {
        List<LoadedModel> models = new ArrayList<LoadedModel>();
        for (Object entry : modelList(root)) {
            Map<String, Object> model = asMap(entry, "model");
            Map<String, Object> details = optionalMap(model, "details");
            Long contextLength = optionalLong(model, "context_length");
            models.add(new LoadedModel(identity(model, details), requiredLong(model, "size"),
                optionalLong(model, "size_vram") == null ? 0 : optionalLong(model, "size_vram"),
                contextLength == null ? null : toInt(contextLength, "context_length"),
                optionalString(model, "expires_at")));
        }
        return Collections.unmodifiableList(models);
    }

    static ModelDetails details(String requestedName, Object root) {
        Map<String, Object> response = asMap(root, "show response");
        Map<String, Object> details = optionalMap(response, "details");
        Map<String, Object> modelInfo = optionalMap(response, "model_info");
        return new ModelDetails(requestedName, optionalString(details, "family"),
            optionalString(details, "parameter_size"), optionalString(details, "quantization_level"),
            optionalString(details, "format"), contextLength(modelInfo));
    }

    /** Return the error message of an Ollama error object, or {@code null} if the tree is none. */
    static String errorMessage(Object root) {
        if (root instanceof Map) {
            Object error = ((Map<?, ?>) root).get("error");
            if (error instanceof String) {
                return (String) error;
            }
        }
        return null;
    }

    private static Integer contextLength(Map<String, Object> modelInfo) {
        String architecture = optionalString(modelInfo, "general.architecture");
        if (architecture == null) {
            return null;
        }
        Long value = optionalLong(modelInfo, architecture + ".context_length");
        return value == null ? null : toInt(value, "context_length");
    }

    private static ModelIdentity identity(Map<String, Object> model, Map<String, Object> details) {
        String name = optionalString(model, "name");
        if (name == null) {
            name = requiredString(model, "model");
        }
        return new ModelIdentity(name, optionalString(model, "digest"),
            optionalString(details, "quantization_level"));
    }

    private static List<?> modelList(Object root) {
        Object models = asMap(root, "model list").get("models");
        if (models == null) {
            return Collections.emptyList();
        }
        if (!(models instanceof List)) {
            throw new IllegalArgumentException("models is not an array");
        }
        return (List<?>) models;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> asMap(Object value, String what) {
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException(what + " is not an object");
        }
        return (Map<String, Object>) value;
    }

    private static Map<String, Object> optionalMap(Map<String, Object> parent, String key) {
        Object value = parent.get(key);
        if (value == null) {
            return Collections.emptyMap();
        }
        return asMap(value, key);
    }

    private static String requiredString(Map<String, Object> map, String key) {
        String value = optionalString(map, key);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("missing " + key);
        }
        return value;
    }

    private static String optionalString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String)) {
            throw new IllegalArgumentException(key + " is not a string");
        }
        return (String) value;
    }

    private static long requiredLong(Map<String, Object> map, String key) {
        Long value = optionalLong(map, key);
        if (value == null) {
            throw new IllegalArgumentException("missing " + key);
        }
        return value;
    }

    static Long optionalLong(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (!(value instanceof Long)) {
            throw new IllegalArgumentException(key + " is not an integer");
        }
        return (Long) value;
    }

    private static int toInt(long value, String key) {
        if (value < 0 || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(key + " out of range: " + value);
        }
        return (int) value;
    }
}
