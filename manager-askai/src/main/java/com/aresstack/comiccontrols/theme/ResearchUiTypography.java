package com.aresstack.comiccontrols.theme;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * The ONE font source of the dark research-UI design study: Inter when installed, otherwise the
 * uniform Segoe UI fallback — never a per-control mix of system fonts. Only two weights exist in
 * the study (Regular, Semi Bold); Semi Bold prefers a real semi-bold family and degrades to BOLD.
 */
public final class ResearchUiTypography {

    private static volatile Set<String> installedFamilies;

    private ResearchUiTypography() {
    }

    /** Inter/Segoe UI Regular at the given pixel size. */
    public static Font regular(float size) {
        String family = firstInstalled("Inter", "Segoe UI");
        return new Font(family == null ? Font.SANS_SERIF : family, Font.PLAIN, 1)
                .deriveFont(size);
    }

    /** Inter/Segoe UI Semi Bold at the given pixel size (BOLD of the base family as last resort). */
    public static Font semiBold(float size) {
        String family = firstInstalled(
                "Inter SemiBold", "Inter Semi Bold", "Segoe UI Semibold");
        if (family != null) {
            return new Font(family, Font.PLAIN, 1).deriveFont(size);
        }
        String base = firstInstalled("Inter", "Segoe UI");
        return new Font(base == null ? Font.SANS_SERIF : base, Font.BOLD, 1).deriveFont(size);
    }

    private static String firstInstalled(String... candidates) {
        Set<String> families = families();
        for (String candidate : candidates) {
            if (families.contains(candidate.toLowerCase(Locale.ROOT))) {
                return candidate;
            }
        }
        return null;
    }

    private static Set<String> families() {
        Set<String> cached = installedFamilies;
        if (cached == null) {
            Set<String> lower = new HashSet<String>();
            for (String family : GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getAvailableFontFamilyNames()) {
                lower.add(family.toLowerCase(Locale.ROOT));
            }
            // The installed set only changes with the OS font list — caching it avoids re-querying
            // the graphics environment on every paint. Benign to compute twice under a race.
            installedFamilies = lower;
            cached = lower;
        }
        return cached;
    }

    /** For tests: the families this JVM sees (lower-cased). */
    static Set<String> familiesForTest() {
        return new HashSet<String>(families());
    }
}
