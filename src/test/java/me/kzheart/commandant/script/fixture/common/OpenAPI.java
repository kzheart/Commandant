package me.kzheart.commandant.script.fixture.common;

import java.util.ArrayList;
import java.util.List;

/** Foreign result objects intentionally do not depend on Klib's OpenResult. */
public final class OpenAPI {
    public static boolean kether;
    public static boolean booleanGetter;
    public static Object value;
    public static final List<String> calls = new ArrayList<String>();
    public static Object call(String channel, Object[] data) {
        calls.add(channel);
        boolean successful = !"kether_create_exit_status".equals(channel) || kether;
        return booleanGetter ? new BooleanResult(successful, value) : new GetterResult(successful, value);
    }
    public static void reset() { kether = true; booleanGetter = false; value = "foreign-result"; calls.clear(); }
    public static final class GetterResult {
        private final boolean successful;
        private final Object value;
        GetterResult(boolean successful, Object value) { this.successful = successful; this.value = value; }
        public boolean getSuccessful() { return successful; }
        public Object getValue() { return value; }
    }
    public static final class BooleanResult {
        private final boolean successful;
        private final Object value;
        BooleanResult(boolean successful, Object value) { this.successful = successful; this.value = value; }
        public boolean isSuccessful() { return successful; }
        public Object getValue() { return value; }
    }
}
