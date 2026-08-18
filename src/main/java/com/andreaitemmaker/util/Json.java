package com.andreaitemmaker.util;

import java.util.Map;
import java.util.Map.Entry;

/**
 * Tiny JSON writer used for generating resource pack files.
 * No external dependencies; only writes well-formed JSON for the structures we need.
 */
public final class Json {

    private Json() {
    }

    /** Serialize a value (String, Number, Boolean, null, Map, Iterable, or array of objects) to JSON. */
    public static String write(Object value) {
        return serialize(value, new StringBuilder()).toString();
    }

    /** Build a JSON object from alternating key/value arguments. */
    public static String obj(Object... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("obj() requires alternating key/value pairs");
        }
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < keyValues.length; i += 2) {
            if (i > 0) {
                sb.append(',');
            }
            serialize(keyValues[i].toString(), sb);
            sb.append(':');
            serialize(keyValues[i + 1], sb);
        }
        return sb.append('}').toString();
    }

    /** Build a JSON array from values. */
    public static String arr(Object... values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            serialize(values[i], sb);
        }
        return sb.append(']').toString();
    }

    @SuppressWarnings("unchecked")
    private static StringBuilder serialize(Object value, StringBuilder sb) {
        if (value == null) {
            return sb.append("null");
        }
        if (value instanceof String s) {
            return sb.append('"').append(escape(s)).append('"');
        }
        if (value instanceof Boolean || value instanceof Number) {
            return sb.append(value.toString());
        }
        if (value instanceof Map<?, ?> map) {
            sb.append('{');
            boolean first = true;
            for (Entry<?, ?> e : map.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                serialize(e.getKey().toString(), sb).append(':');
                serialize(e.getValue(), sb);
            }
            return sb.append('}');
        }
        if (value instanceof Iterable<?> iterable) {
            sb.append('[');
            boolean first = true;
            for (Object o : iterable) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                serialize(o, sb);
            }
            return sb.append(']');
        }
        if (value.getClass().isArray()) {
            return serialize(java.util.Arrays.asList((Object[]) value), sb);
        }
        return sb.append('"').append(escape(value.toString())).append('"');
    }

    /**
     * Lightweight structural validation for JSON coming from external files (imported models):
     * trims a possible BOM, requires a top-level object and checks that braces, brackets and
     * strings are balanced (escaped quotes are handled). This is not a full parser, but it
     * catches truncated or clearly broken files before they are injected into the pack.
     */
    public static boolean looksValid(String content) {
        if (content == null) {
            return false;
        }
        String s = content.trim();
        if (s.startsWith("\uFEFF")) {
            s = s.substring(1).trim();
        }
        if (!s.startsWith("{") || !s.endsWith("}")) {
            return false;
        }
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            switch (c) {
                case '{', '[' -> depth++;
                case '}', ']' -> {
                    depth--;
                    if (depth < 0) {
                        return false;
                    }
                }
                case '"' -> inString = true;
                default -> {
                }
            }
        }
        return depth == 0 && !inString;
    }

    private static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
