package com.aresstack.ollamamanager.application.installation;

/**
 * Compares release tags such as {@code v0.12.6} numerically.
 */
public final class ReleaseVersions {

    private ReleaseVersions() {
    }

    /**
     * Compare dotted numeric versions, ignoring a leading {@code v}. Non-numeric parts compare as text.
     */
    public static int compare(String left, String right) {
        String[] a = strip(left).split("[.-]");
        String[] b = strip(right).split("[.-]");
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            String x = i < a.length ? a[i] : "0";
            String y = i < b.length ? b[i] : "0";
            int result;
            if (x.matches("\\d{1,9}") && y.matches("\\d{1,9}")) {
                result = Integer.compare(Integer.parseInt(x), Integer.parseInt(y));
            } else {
                result = x.compareTo(y);
            }
            if (result != 0) {
                return result;
            }
        }
        return 0;
    }

    private static String strip(String version) {
        String value = version == null ? "" : version.trim();
        return value.startsWith("v") || value.startsWith("V") ? value.substring(1) : value;
    }
}
