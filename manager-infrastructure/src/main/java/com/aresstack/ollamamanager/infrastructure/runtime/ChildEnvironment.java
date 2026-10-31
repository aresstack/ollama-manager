package com.aresstack.ollamamanager.infrastructure.runtime;

import com.aresstack.ollamamanager.domain.server.ServerSettingCatalog;
import com.aresstack.ollamamanager.domain.server.ServerSettings;

import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Prepares the environment of an {@code ollama serve} child process.
 * <p>
 * Names are compared case-insensitively because Windows environment names are case-insensitive: an inherited
 * {@code ollama_host} would otherwise survive next to the managed {@code OLLAMA_HOST} and the child would see
 * an ambiguous or stale value.
 */
final class ChildEnvironment {

    private ChildEnvironment() {
    }

    /** Remove every managed variable in any spelling, then apply the explicit settings. */
    static void apply(Map<String, String> environment, ServerSettings settings) {
        Set<String> managed = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
        managed.addAll(ServerSettingCatalog.environmentVariables());
        for (Iterator<String> names = environment.keySet().iterator(); names.hasNext(); ) {
            if (managed.contains(names.next())) {
                names.remove();
            }
        }
        for (Map.Entry<String, String> entry : settings.toEnvironment().entrySet()) {
            environment.put(entry.getKey().toUpperCase(Locale.ROOT), entry.getValue());
        }
    }
}
