package com.aresstack.ollamamanager.infrastructure.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON reader and writer for persistence and Ollama/GitHub responses.
 * Objects map to {@link LinkedHashMap}, arrays to {@link List}, numbers to {@link Long} or {@link Double}.
 */
public final class Json {

    private final String text;
    private int position;

    private Json(String text) {
        this.text = text;
    }

    /**
     * Parse one JSON value.
     *
     * @throws IllegalArgumentException if the text is not valid JSON
     */
    public static Object parse(String text) {
        Json parser = new Json(text);
        parser.skipWhitespace();
        Object value = parser.readValue();
        parser.skipWhitespace();
        if (parser.position != text.length()) {
            throw parser.error("unexpected trailing content");
        }
        return value;
    }

    /** Serialize maps, lists, strings, numbers, booleans and null with two-space indentation. */
    public static String write(Object value) {
        StringBuilder out = new StringBuilder();
        writeValue(value, out, 0);
        return out.toString();
    }

    private Object readValue() {
        if (position >= text.length()) {
            throw error("unexpected end of input");
        }
        char c = text.charAt(position);
        switch (c) {
            case '{':
                return readObject();
            case '[':
                return readArray();
            case '"':
                return readString();
            case 't':
                return readLiteral("true", Boolean.TRUE);
            case 'f':
                return readLiteral("false", Boolean.FALSE);
            case 'n':
                return readLiteral("null", null);
            default:
                return readNumber();
        }
    }

    private Map<String, Object> readObject() {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        position++;
        skipWhitespace();
        if (peek() == '}') {
            position++;
            return result;
        }
        while (true) {
            skipWhitespace();
            if (peek() != '"') {
                throw error("expected object key");
            }
            String key = readString();
            skipWhitespace();
            expect(':');
            skipWhitespace();
            result.put(key, readValue());
            skipWhitespace();
            if (peek() == ',') {
                position++;
            } else {
                expect('}');
                return result;
            }
        }
    }

    private List<Object> readArray() {
        List<Object> result = new ArrayList<Object>();
        position++;
        skipWhitespace();
        if (peek() == ']') {
            position++;
            return result;
        }
        while (true) {
            skipWhitespace();
            result.add(readValue());
            skipWhitespace();
            if (peek() == ',') {
                position++;
            } else {
                expect(']');
                return result;
            }
        }
    }

    private String readString() {
        expect('"');
        StringBuilder out = new StringBuilder();
        while (true) {
            if (position >= text.length()) {
                throw error("unterminated string");
            }
            char c = text.charAt(position++);
            if (c == '"') {
                return out.toString();
            }
            if (c != '\\') {
                out.append(c);
                continue;
            }
            if (position >= text.length()) {
                throw error("unterminated escape");
            }
            char escaped = text.charAt(position++);
            switch (escaped) {
                case 'b': out.append('\b'); break;
                case 'f': out.append('\f'); break;
                case 'n': out.append('\n'); break;
                case 'r': out.append('\r'); break;
                case 't': out.append('\t'); break;
                case 'u': out.append(readUnicodeEscape()); break;
                case '"': case '\\': case '/': out.append(escaped); break;
                default: throw error("invalid escape \\" + escaped);
            }
        }
    }

    private char readUnicodeEscape() {
        if (position + 4 > text.length()) {
            throw error("truncated unicode escape");
        }
        try {
            char c = (char) Integer.parseInt(text.substring(position, position + 4), 16);
            position += 4;
            return c;
        } catch (NumberFormatException e) {
            throw error("invalid unicode escape");
        }
    }

    private Object readLiteral(String literal, Object value) {
        if (!text.startsWith(literal, position)) {
            throw error("unexpected token");
        }
        position += literal.length();
        return value;
    }

    private Number readNumber() {
        int start = position;
        while (position < text.length() && "+-0123456789.eE".indexOf(text.charAt(position)) >= 0) {
            position++;
        }
        String number = text.substring(start, position);
        if (number.isEmpty()) {
            throw error("unexpected character");
        }
        try {
            if (number.indexOf('.') >= 0 || number.indexOf('e') >= 0 || number.indexOf('E') >= 0) {
                return Double.valueOf(number);
            }
            return Long.valueOf(number);
        } catch (NumberFormatException e) {
            throw error("invalid number " + number);
        }
    }

    private char peek() {
        if (position >= text.length()) {
            throw error("unexpected end of input");
        }
        return text.charAt(position);
    }

    private void expect(char c) {
        if (peek() != c) {
            throw error("expected '" + c + "'");
        }
        position++;
    }

    private void skipWhitespace() {
        while (position < text.length() && Character.isWhitespace(text.charAt(position))) {
            position++;
        }
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException("invalid JSON at offset " + position + ": " + message);
    }

    private static void writeValue(Object value, StringBuilder out, int indent) {
        if (value == null || value instanceof Boolean || value instanceof Long || value instanceof Integer) {
            out.append(value);
        } else if (value instanceof Number) {
            writeDouble(((Number) value).doubleValue(), out);
        } else if (value instanceof String) {
            writeString((String) value, out);
        } else if (value instanceof Map) {
            writeObject((Map<?, ?>) value, out, indent);
        } else if (value instanceof List) {
            writeArray((List<?>) value, out, indent);
        } else {
            throw new IllegalArgumentException("unsupported JSON value: " + value.getClass().getName());
        }
    }

    private static void writeDouble(double value, StringBuilder out) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException("JSON cannot represent " + value);
        }
        out.append(value);
    }

    private static void writeObject(Map<?, ?> map, StringBuilder out, int indent) {
        if (map.isEmpty()) {
            out.append("{}");
            return;
        }
        out.append("{\n");
        int index = 0;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            indent(out, indent + 1);
            writeString(String.valueOf(entry.getKey()), out);
            out.append(": ");
            writeValue(entry.getValue(), out, indent + 1);
            out.append(++index < map.size() ? ",\n" : "\n");
        }
        indent(out, indent);
        out.append('}');
    }

    private static void writeArray(List<?> list, StringBuilder out, int indent) {
        if (list.isEmpty()) {
            out.append("[]");
            return;
        }
        out.append("[\n");
        for (int i = 0; i < list.size(); i++) {
            indent(out, indent + 1);
            writeValue(list.get(i), out, indent + 1);
            out.append(i + 1 < list.size() ? ",\n" : "\n");
        }
        indent(out, indent);
        out.append(']');
    }

    private static void writeString(String value, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"': out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        out.append('"');
    }

    private static void indent(StringBuilder out, int level) {
        for (int i = 0; i < level; i++) {
            out.append("  ");
        }
    }
}
