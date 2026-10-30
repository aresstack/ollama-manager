package com.aresstack.ollamamanager.infrastructure.persistence;

import com.aresstack.ollamamanager.domain.configuration.ManagerConfiguration;
import com.aresstack.ollamamanager.domain.hardware.GpuFingerprint;
import com.aresstack.ollamamanager.domain.model.ModelIdentity;
import com.aresstack.ollamamanager.domain.model.ModelOptions;
import com.aresstack.ollamamanager.domain.profile.ModelProfile;
import com.aresstack.ollamamanager.domain.profile.ProfileScope;
import com.aresstack.ollamamanager.domain.server.InstallationKind;
import com.aresstack.ollamamanager.domain.server.ServerSettings;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps {@link ManagerConfiguration} to and from the JSON document tree of schema {@code formatVersion 1}.
 */
final class ConfigurationDocumentMapper {

    private ConfigurationDocumentMapper() {
    }

    static Map<String, Object> toDocument(ManagerConfiguration configuration) {
        Map<String, Object> document = new LinkedHashMap<String, Object>();
        document.put("formatVersion", (long) ManagerConfiguration.FORMAT_VERSION);
        document.put("serverSettings", new LinkedHashMap<String, Object>(
            configuration.desiredServerSettings().toEncodedMap()));
        document.put("selectedInstallationKind", configuration.selectedInstallationKind() == null
            ? null : configuration.selectedInstallationKind().name());
        document.put("selectedBinaryDirectory", configuration.selectedBinaryDirectory() == null
            ? null : configuration.selectedBinaryDirectory().getPath());
        List<Object> profiles = new ArrayList<Object>();
        for (ModelProfile profile : configuration.profiles()) {
            profiles.add(profileToDocument(profile));
        }
        document.put("profiles", profiles);
        document.put("activeProfileId", configuration.activeProfileId());
        return document;
    }

    static ManagerConfiguration fromDocument(Object root) {
        Map<String, Object> document = asMap(root, "document");
        Object version = document.get("formatVersion");
        if (!(version instanceof Long) || (Long) version != ManagerConfiguration.FORMAT_VERSION) {
            throw new IllegalArgumentException("unsupported formatVersion: " + version);
        }
        List<ModelProfile> profiles = new ArrayList<ModelProfile>();
        for (Object profile : asList(document.get("profiles"))) {
            profiles.add(profileFromDocument(asMap(profile, "profile")));
        }
        String kind = optionalString(document, "selectedInstallationKind");
        String directory = optionalString(document, "selectedBinaryDirectory");
        return new ManagerConfiguration(
            settingsFromDocument(document.get("serverSettings")),
            kind == null ? null : InstallationKind.valueOf(kind),
            directory == null ? null : new File(directory),
            profiles,
            optionalString(document, "activeProfileId"));
    }

    private static Map<String, Object> profileToDocument(ModelProfile profile) {
        Map<String, Object> document = new LinkedHashMap<String, Object>();
        document.put("id", profile.id());
        document.put("name", profile.name());
        document.put("revision", (long) profile.revision());
        Map<String, Object> scope = new LinkedHashMap<String, Object>();
        scope.put("model", profile.scope().model().name());
        scope.put("digest", profile.scope().model().digest());
        scope.put("quantization", profile.scope().model().quantization());
        scope.put("gpuFingerprint", profile.scope().gpuFingerprint().value());
        scope.put("ollamaVersion", profile.scope().ollamaVersion());
        scope.put("workload", profile.scope().workload());
        document.put("scope", scope);
        document.put("serverSettings", new LinkedHashMap<String, Object>(profile.serverSettings().toEncodedMap()));
        document.put("modelOptions", new LinkedHashMap<String, Object>(profile.modelOptions().toRequestOptions()));
        return document;
    }

    private static ModelProfile profileFromDocument(Map<String, Object> document) {
        Map<String, Object> scope = asMap(document.get("scope"), "scope");
        ProfileScope profileScope = new ProfileScope(
            new ModelIdentity(requiredString(scope, "model"), optionalString(scope, "digest"),
                optionalString(scope, "quantization")),
            new GpuFingerprint(requiredString(scope, "gpuFingerprint")),
            requiredString(scope, "ollamaVersion"),
            requiredString(scope, "workload"));
        Map<String, Object> options = asMap(document.get("modelOptions"), "modelOptions");
        ModelOptions modelOptions = new ModelOptions(
            optionalInteger(options, "num_ctx"),
            optionalInteger(options, "num_predict"),
            options.get("temperature") == null ? null : ((Number) options.get("temperature")).doubleValue(),
            optionalInteger(options, "seed"));
        return new ModelProfile(requiredString(document, "id"), requiredString(document, "name"),
            ((Number) document.get("revision")).intValue(), profileScope,
            settingsFromDocument(document.get("serverSettings")), modelOptions);
    }

    private static ServerSettings settingsFromDocument(Object value) {
        Map<String, String> encoded = new LinkedHashMap<String, String>();
        for (Map.Entry<String, Object> entry : asMap(value, "serverSettings").entrySet()) {
            if (entry.getValue() != null) {
                encoded.put(entry.getKey(), String.valueOf(entry.getValue()));
            }
        }
        return ServerSettings.fromEncodedMap(encoded);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value, String name) {
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException(name + " must be a JSON object");
        }
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object value) {
        if (value == null) {
            return new ArrayList<Object>();
        }
        if (!(value instanceof List)) {
            throw new IllegalArgumentException("profiles must be a JSON array");
        }
        return (List<Object>) value;
    }

    private static String requiredString(Map<String, Object> map, String key) {
        String value = optionalString(map, key);
        if (value == null) {
            throw new IllegalArgumentException("missing " + key);
        }
        return value;
    }

    private static String optionalString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static Integer optionalInteger(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? null : ((Number) value).intValue();
    }
}
