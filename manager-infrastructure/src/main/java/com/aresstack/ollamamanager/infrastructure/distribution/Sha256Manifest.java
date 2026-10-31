package com.aresstack.ollamamanager.infrastructure.distribution;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses a {@code sha256sum.txt} release manifest ({@code <hex>  <name>} per line, optional {@code *} or
 * {@code ./} before the name).
 */
final class Sha256Manifest {

    private static final Pattern LINE = Pattern.compile("^([0-9a-fA-F]{64})\\s+\\*?(?:\\./)?(\\S.*?)\\s*$");

    private Sha256Manifest() {
    }

    /**
     * Return file name to lowercase digest. Blank lines are skipped; malformed lines or conflicting duplicates
     * are rejected.
     */
    static Map<String, String> parse(String text) {
        Map<String, String> digests = new LinkedHashMap<String, String>();
        for (String raw : text.split("\r?\n")) {
            if (raw.trim().isEmpty()) {
                continue;
            }
            Matcher matcher = LINE.matcher(raw);
            if (!matcher.matches()) {
                throw new IllegalArgumentException("malformed sha256sum line: " + raw);
            }
            String digest = matcher.group(1).toLowerCase(java.util.Locale.ROOT);
            String previous = digests.put(matcher.group(2), digest);
            if (previous != null && !previous.equals(digest)) {
                throw new IllegalArgumentException("conflicting digests for " + matcher.group(2));
            }
        }
        return Collections.unmodifiableMap(digests);
    }
}
