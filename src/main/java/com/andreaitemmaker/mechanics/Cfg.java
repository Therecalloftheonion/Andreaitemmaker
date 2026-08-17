package com.andreaitemmaker.mechanics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Typed access helpers for the mechanic config maps declared in item YAMLs. */
public final class Cfg {

    private Cfg() {
    }

    public static int i(Map<String, Object> cfg, String key, int def) {
        Object v = cfg == null ? null : cfg.get(key);
        return v instanceof Number n ? n.intValue() : def;
    }

    public static double d(Map<String, Object> cfg, String key, double def) {
        Object v = cfg == null ? null : cfg.get(key);
        return v instanceof Number n ? n.doubleValue() : def;
    }

    public static boolean b(Map<String, Object> cfg, String key, boolean def) {
        Object v = cfg == null ? null : cfg.get(key);
        return v instanceof Boolean bool ? bool : def;
    }

    public static String s(Map<String, Object> cfg, String key, String def) {
        Object v = cfg == null ? null : cfg.get(key);
        return v instanceof String str ? str : def;
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> listOfMaps(Map<String, Object> cfg, String key) {
        Object v = cfg == null ? null : cfg.get(key);
        List<Map<String, Object>> out = new ArrayList<>();
        if (v instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> map) {
                    out.add((Map<String, Object>) map);
                }
            }
        }
        return out;
    }
}
