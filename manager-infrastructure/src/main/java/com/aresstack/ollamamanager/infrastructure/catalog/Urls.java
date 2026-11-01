package com.aresstack.ollamamanager.infrastructure.catalog;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

final class Urls {

    private Urls() {
    }

    static String encode(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value.trim(), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Encode each path segment of {@code owner/name}. */
    static String encodePath(String path) {
        StringBuilder out = new StringBuilder();
        for (String segment : path.split("/")) {
            if (out.length() > 0) {
                out.append('/');
            }
            out.append(encode(segment).replace("+", "%20"));
        }
        return out.toString();
    }
}
