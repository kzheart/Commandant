package me.kzheart.commandant.config;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Small typed boundary for the original untyped YAML format. */
public final class Values {
    private Values() { }

    public static final class MissingParameterException extends IllegalArgumentException {
        public MissingParameterException(String message) { super(message); }
    }

    public static String string(Map<String, Object> config, String key) {
        Object value = config.get(key);
        if (!(value instanceof String)) throw new MissingParameterException(key + " 必须是字符串");
        return (String) value;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> map(Object value) {
        if (!(value instanceof Map)) return Collections.emptyMap();
        return (Map<String, Object>) value;
    }

    public static List<?> list(Object value) {
        return value instanceof List ? (List<?>) value : Collections.emptyList();
    }

    public static boolean bool(Object value, boolean fallback) {
        return value instanceof Boolean ? (Boolean) value : fallback;
    }

    public static int integer(Object value, int fallback) {
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }
}
