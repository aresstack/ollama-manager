package com.aresstack.ollamamanager.infrastructure.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.ToNumberPolicy;

/**
 * Converts between JSON text and plain Java trees using Gson, so adapters never expose Gson types.
 * Objects map to {@link java.util.Map}, arrays to {@link java.util.List}, integral numbers to {@link Long}
 * and other numbers to {@link Double}.
 */
public final class Json {

    private static final Gson GSON = new GsonBuilder()
        .setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
        .serializeNulls()
        .disableHtmlEscaping()
        .setPrettyPrinting()
        .create();

    private Json() {
    }

    /**
     * Parse one JSON value.
     *
     * @throws IllegalArgumentException if the text is not valid JSON
     */
    public static Object parse(String text) {
        try {
            return GSON.fromJson(text, Object.class);
        } catch (JsonParseException e) {
            throw new IllegalArgumentException("invalid JSON: " + e.getMessage(), e);
        }
    }

    /** Serialize maps, lists, strings, numbers, booleans and null with indentation. */
    public static String write(Object value) {
        return GSON.toJson(value);
    }
}
