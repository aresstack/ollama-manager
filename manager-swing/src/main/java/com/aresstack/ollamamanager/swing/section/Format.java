package com.aresstack.ollamamanager.swing.section;

import java.util.Locale;

/**
 * Formatting helpers for display values.
 */
final class Format {

    private Format() {
    }

    /** Format a byte count in binary units; unknown values become "unknown". */
    static String bytes(Long value) {
        if (value == null) {
            return "unknown";
        }
        double v = value;
        String[] units = {"B", "KiB", "MiB", "GiB", "TiB"};
        int unit = 0;
        while (v >= 1024 && unit < units.length - 1) {
            v /= 1024;
            unit++;
        }
        return unit == 0 ? value + " B" : String.format(Locale.ROOT, "%.1f %s", v, units[unit]);
    }
}
