package me.kzheart.commandant.interceptor;

import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.*;
import me.kzheart.commandant.config.Values;
import me.kzheart.commandant.interceptor.action.*;
import me.kzheart.commandant.interceptor.condition.*;
import me.kzheart.commandant.logger.CommandantLogger;
import me.kzheart.commandant.script.Scripts;
import me.kzheart.klib.config.YamlDocument;
import org.bukkit.World;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Behavior contracts cross-checked against the original Kotlin source at d19e37c. */
class InterceptorParityTest {
    @TempDir Path directory;
    private final List<String> messages = new ArrayList<>();
    private final List<String> performed = new ArrayList<>();
    private final Set<String> permissions = new HashSet<>();
    private final RecordingScripts scripts = new RecordingScripts();
    private final List<LogRecord> records = new ArrayList<>();
    private Player player;
    private CommandantLogger log;
    private ConditionRegistry conditions;
    private ActionRegistry actions;
    private InterceptorManager manager;

    @BeforeEach void setup() {
        World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                (self, method, args) -> method.getName().equals("getName") ? "world" : defaultValue(method.getReturnType()));
        Server server = (Server) Proxy.newProxyInstance(Server.class.getClassLoader(), new Class<?>[]{Server.class},
                (self, method, args) -> method.getName().equals("getOnlinePlayers")
                        ? Collections.emptyList() : defaultValue(method.getReturnType()));
        player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class}, (self, method, args) -> {
            switch (method.getName()) {
                case "getName": return "Alice";
                case "getWorld": return world;
                case "getServer": return server;
                case "hasPermission": return permissions.contains(args[0]);
                case "sendMessage":
                    if (args[0] instanceof String[]) messages.addAll(Arrays.asList((String[]) args[0]));
                    else messages.add((String) args[0]);
                    return null;
                case "performCommand": performed.add((String) args[0]); return true;
                case "hashCode": return System.identityHashCode(self);
                case "equals": return self == args[0];
                case "toString": return "Alice";
                default: return defaultValue(method.getReturnType());
            }
        });
        Logger sink = Logger.getAnonymousLogger();
        sink.setUseParentHandlers(false);
        sink.addHandler(new Handler() {
            public void publish(LogRecord record) { records.add(record); }
            public void flush() { }
            public void close() { }
        });
        log = new CommandantLogger(sink, directory.resolve("nested"));
        conditions = new ConditionRegistry(scripts);
        actions = new ActionRegistry(scripts, log);
        manager = new InterceptorManager(conditions, actions, log);
    }

    @Test void regexRequiresAFullCaseSensitiveMatch() {
        Condition condition = condition("regex", "pattern", "/home");
        assertTrue(condition.test(player, "/home"));
        assertFalse(condition.test(player, "/home extra"));
        assertFalse(condition.test(player, "prefix/home"));
        assertFalse(condition.test(player, "/HOME"));
        assertTrue(condition("regex", "pattern", "^/give .* diamond$").test(player, "/give Alice diamond"));
    }

    @Test void allAndAnyHaveOriginalEmptyAndUnknownRuleIdentities() {
        assertTrue(condition("all", "rules", Collections.emptyList()).test(player, "/anything"));
        assertFalse(condition("any", "rules", Collections.emptyList()).test(player, "/anything"));
        List<Object> unknown = Arrays.asList(map("type", "missing"), map("pattern", ".*"));
        assertTrue(condition("all", "rules", unknown).test(player, "/anything"));
        assertFalse(condition("any", "rules", unknown).test(player, "/anything"));
    }

    @Test void compositesShortCircuitInConfiguredOrder() {
        List<Map<String, Object>> rules = Arrays.asList(map("type", "regex", "pattern", "/home"),
                map("type", "has_permission", "permission", "home", "message", "denied"));
        assertFalse(condition("all", "rules", rules).test(player, "/other"));
        assertTrue(messages.isEmpty());
        assertTrue(condition("any", "rules", rules).test(player, "/home"));
        assertTrue(messages.isEmpty());
        assertFalse(condition("all", "rules", rules).test(player, "/home"));
        assertEquals(Collections.singletonList("denied"), messages);
    }

    @Test void nestedCompositesCombineAllSevenConditionTypes() {
        permissions.add("home");
        Map<String, Object> nested = map("type", "any", "rules", Arrays.asList(
                map("type", "no_permission", "permission", "home"),
                map("type", "script", "script", "true")));
        Condition combined = condition("all", "rules", Arrays.asList(
                map("type", "regex", "pattern", "/home"),
                map("type", "has_permission", "permission", "home"),
                map("type", "world", "worlds", "world"), nested));
        assertTrue(combined.test(player, "/home"));
        assertEquals(Collections.singletonList("true"), scripts.tested);
    }

    @Test void permissionMessagesAreSentOnlyOnFailureAndRemainLiteral() {
        Condition has = condition("has_permission", "permission", "home", "message", "&cNo {player}");
        Condition lacks = condition("no_permission", "permission", "home", "message", "Already allowed");
        assertFalse(has.test(player, "/home"));
        assertTrue(lacks.test(player, "/home"));
        assertEquals(Collections.singletonList("&cNo {player}"), messages);
        permissions.add("home");
        assertTrue(has.test(player, "/home"));
        assertFalse(lacks.test(player, "/home"));
        assertEquals(Arrays.asList("&cNo {player}", "Already allowed"), messages);
    }

    @Test void absentAndNonStringOptionalMessagesAreIgnored() {
        assertFalse(condition("has_permission", "permission", "home").test(player, "/home"));
        assertFalse(condition("has_permission", "permission", "home", "message", 42).test(player, "/home"));
        assertFalse(condition("world", "worlds", "other", "message", false).test(player, "/home"));
        assertTrue(messages.isEmpty());
    }

    @Test void worldSupportsScalarAndStringMembersOfMixedLists() {
        assertTrue(condition("world", "worlds", "world").test(player, "/home"));
        assertTrue(condition("world", "worlds", Arrays.asList(42, "world", null)).test(player, "/home"));
        assertFalse(condition("world", "worlds", "WORLD", "message", "wrong world").test(player, "/home"));
        assertFalse(condition("world", "worlds", Collections.emptyList()).test(player, "/home"));
        assertEquals(Collections.singletonList("wrong world"), messages);
    }

    @Test void scriptConditionDelegatesPlayerCommandAndSource() {
        Condition condition = condition("script", "script", "return false");
        scripts.result = false;
        assertFalse(condition.test(player, "/home"));
        assertSame(player, scripts.player);
        assertEquals("/home", scripts.command);
        assertEquals(Collections.singletonList("return false"), scripts.tested);
    }

    @Test void cancelSetsCancellationWithoutRewritingTheEvent() {
        PlayerCommandPreprocessEvent event = event("/original");
        action("cancel").execute(player, "/original", event);
        assertTrue(event.isCancelled());
        assertEquals("/original", event.getMessage());
    }

    @Test void replaceExpandsOnlyPlayerAndWorldAndDoesNotCancel() {
        PlayerCommandPreprocessEvent event = event("/original");
        action("replace", "command", "home {player} {world} {command} %player_name% {player}")
                .execute(player, "/original", event);
        assertEquals(Collections.singletonList("home Alice world {command} %player_name% Alice"), performed);
        assertFalse(event.isCancelled());
        assertEquals("/original", event.getMessage());
    }

    @Test void replacePreservesAnAlreadyCancelledEvent() {
        PlayerCommandPreprocessEvent event = event("/original");
        event.setCancelled(true);
        action("replace", "command", "home").execute(player, "/original", event);
        assertTrue(event.isCancelled());
        assertEquals(Collections.singletonList("home"), performed);
    }

    @Test void logExpandsOnlyPlayerAndCommandAndAppendsUtf8() throws Exception {
        Action action = action("log", "format", "中文 {player}: {command} {world} %player_name%");
        PlayerCommandPreprocessEvent event = event("/第一");
        action.execute(player, "/第一", event);
        action.execute(player, "/第二", event("/第二"));
        List<String> lines = Files.readAllLines(directory.resolve("nested/command.log"), StandardCharsets.UTF_8);
        assertEquals(2, lines.size());
        assertTrue(lines.get(0).matches("\\[\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\] 中文 Alice: /第一 \\{world\\} %player_name%"));
        assertTrue(lines.get(1).endsWith("中文 Alice: /第二 {world} %player_name%"));
        assertFalse(event.isCancelled());
    }

    @Test void scriptActionDelegatesExactEventAndSource() {
        PlayerCommandPreprocessEvent event = event("/home");
        action("script", "script", "cancel_cmd_event").execute(player, "/home", event);
        assertSame(player, scripts.player);
        assertSame(event, scripts.event);
        assertEquals("/home", scripts.command);
        assertEquals(Collections.singletonList("cancel_cmd_event"), scripts.executed);
        assertTrue(event.isCancelled());
    }

    @Test void prioritiesAreAscendingStableAndActionsContinueAfterCancellation() {
        Map<String, Object> rules = map(
                "late", rule(100, true, actionMap("replace", "command", "late")),
                "firstTie", rule(50, true, actionMap("cancel"), actionMap("replace", "command", "firstTie")),
                "secondTie", rule(50, true, actionMap("replace", "command", "secondTie")),
                "early", rule(-1, true, actionMap("replace", "command", "early")),
                "disabled", rule(-10, false, actionMap("replace", "command", "disabled")));
        manager.reload(map("interceptors", rules));
        PlayerCommandPreprocessEvent event = event("/home");
        manager.processCommand(player, "/home", event);
        assertEquals(Arrays.asList("early", "firstTie", "secondTie", "late"), performed);
        assertTrue(event.isCancelled());
        assertEquals(5, manager.size());
    }

    @Test void alreadyCancelledCommandsStillRunAllMatchingRules() {
        manager.reload(map("interceptors", map("rule", rule(0, true, actionMap("replace", "command", "seen")))));
        PlayerCommandPreprocessEvent event = event("/home");
        event.setCancelled(true);
        manager.processCommand(player, "/home", event);
        assertEquals(Collections.singletonList("seen"), performed);
        assertTrue(event.isCancelled());
    }

    @Test void disabledInterceptorDoesNotEvaluateConditionAndCopiesActions() {
        List<Action> mutable = new ArrayList<>();
        mutable.add((p, c, e) -> performed.add("original"));
        Interceptor enabled = new Interceptor("enabled", true, 0, (p, c) -> true, mutable);
        mutable.clear();
        enabled.process(player, "/home", event("/home"), log);
        new Interceptor("disabled", false, 0, (p, c) -> { fail("disabled condition evaluated"); return true; }, mutable)
                .process(player, "/home", event("/home"), log);
        assertEquals(Collections.singletonList("original"), performed);
    }

    @Test void missingRootClearsPreviouslyLoadedRules() {
        manager.reload(map("interceptors", map("old", rule(0, true, actionMap("cancel")))));
        assertEquals(1, manager.size());
        manager.reload(Collections.emptyMap());
        assertEquals(0, manager.size());
        PlayerCommandPreprocessEvent event = event("/home");
        manager.processCommand(player, "/home", event);
        assertFalse(event.isCancelled());
    }

    @Test void malformedTopLevelRulesAreIsolatedFromGoodRules() {
        manager.reload(map("interceptors", map("notMap", "oops", "missingCondition", map(),
                "unknownCondition", map("condition", map("type", "unknown")),
                "good", rule(0, true, actionMap("cancel")))));
        assertEquals(1, manager.size());
        PlayerCommandPreprocessEvent event = event("/home");
        manager.processCommand(player, "/home", event);
        assertTrue(event.isCancelled());
    }

    @Test void absentEnabledPriorityAndActionsKeepOriginalDefaults() {
        manager.reload(map("interceptors", map("default", map("condition", map("type", "regex", "pattern", "/home")),
                "second", map("condition", map("type", "regex", "pattern", "/home"), "actions", Arrays.asList(
                        "ignored", map(), map("type", "unknown"), actionMap("replace", "command", "works"))))));
        assertEquals(2, manager.size());
        manager.processCommand(player, "/home", event("/home"));
        assertEquals(Collections.singletonList("works"), performed);
    }

    @Test void malformedActionIsSkippedWithoutDiscardingItsInterceptor() {
        // Original ActionBuilder.build returned null; Interceptor.fromConfig used mapNotNull.
        manager.reload(map("interceptors", map("rule", rule(0, true,
                actionMap("replace"), actionMap("log", "format", 42), actionMap("cancel")))));
        assertEquals(1, manager.size(), "Malformed action must not discard valid sibling cancel action");
        PlayerCommandPreprocessEvent event = event("/home");
        manager.processCommand(player, "/home", event);
        assertTrue(event.isCancelled());
    }

    @Test void malformedNestedConditionIsSkippedLikeOriginalDsl() {
        // Original composite mapNotNull ignored children whose required parameter parser returned null.
        Map<String, Object> config = map("condition", map("type", "all", "rules", Arrays.asList(
                map("type", "regex"), map("type", "world", "worlds", 42),
                map("type", "has_permission"), map("type", "regex", "pattern", "/home"))),
                "actions", Collections.singletonList(actionMap("cancel")));
        manager.reload(map("interceptors", map("rule", config)));
        assertEquals(1, manager.size());
        PlayerCommandPreprocessEvent event = event("/home");
        manager.processCommand(player, "/home", event);
        assertTrue(event.isCancelled());
    }

    @Test void malformedRequiredParametersReturnNullLikeOriginalBuilders() {
        assertNull(conditions.createCondition("regex", map()));
        assertNull(conditions.createCondition("regex", map("pattern", 42)));
        assertNull(conditions.createCondition("all", map("rules", "wrong")));
        assertNull(conditions.createCondition("any", map()));
        assertNull(conditions.createCondition("has_permission", map()));
        assertNull(conditions.createCondition("no_permission", map("permission", false)));
        assertNull(conditions.createCondition("world", map("worlds", 42)));
        assertNull(conditions.createCondition("script", map()));
        assertNull(actions.createAction("replace", map()));
        assertNull(actions.createAction("log", map("format", 42)));
        assertNull(actions.createAction("script", map()));
    }

    @Test void unmatchedInterceptorDoesNotExecuteActionsOrLogAMatch() {
        Interceptor interceptor = new Interceptor("no match", true, 0, (p, c) -> false,
                Collections.singletonList((p, c, e) -> fail("unmatched action executed")));
        interceptor.process(player, "/other", event("/other"), log);
        assertTrue(records.isEmpty());
    }

    @Test void registriesCanOverrideAndUnregisterTypes() {
        assertNull(conditions.createCondition("unknown", map()));
        assertNull(actions.createAction("unknown", map()));
        conditions.register("regex", config -> (p, c) -> false);
        assertFalse(condition("regex").test(player, "/home"));
        conditions.unregisterCondition("regex");
        assertNull(conditions.createCondition("regex", map()));
        actions.register("cancel", config -> (p, c, e) -> performed.add("override"));
        action("cancel").execute(player, "/home", event("/home"));
        assertEquals(Collections.singletonList("override"), performed);
        actions.unregisterAction("cancel");
        assertNull(actions.createAction("cancel", map()));
    }

    @Test void shippedYamlLoadsAndItsExamplesKeepWorking() throws Exception {
        manager.reload(Values.map(YamlDocument.parse("interceptors.yml", resource("interceptors.yml")).root().raw()));
        assertEquals(3, manager.size());
        PlayerCommandPreprocessEvent blocked = event("/give Alice diamond");
        manager.processCommand(player, blocked.getMessage(), blocked);
        assertTrue(blocked.isCancelled());
        assertTrue(Files.readAllLines(directory.resolve("nested/command.log"), StandardCharsets.UTF_8)
                .get(0).contains("黑名单命令拦截: Alice -> /give Alice diamond"));
        scripts.executed.clear();
        permissions.add("myhome.use");
        manager.processCommand(player, "/home", event("/home"));
        assertEquals(Collections.singletonList("tell 你拥有myhome.use权限"), scripts.executed);
        scripts.executed.clear();
        permissions.clear();
        manager.processCommand(player, "/home", event("/home"));
        assertEquals(Collections.singletonList("tell 你没有myhome.use权限"), scripts.executed);
        Map<String, Object> settings = Values.map(YamlDocument.parse("config.yml", resource("config.yml")).root().raw());
        assertEquals(Boolean.FALSE, settings.get("debug"));
    }

    @Test void debugLoggingIsOptInAndErrorDetailFollowsDebugSetting() {
        assertFalse(log.isDebugEnabled());
        log.debug("hidden");
        assertTrue(records.isEmpty());
        RuntimeException failure = new RuntimeException("failure detail");
        log.error("failed", failure);
        assertNull(records.get(0).getThrown());
        assertTrue(records.get(0).getMessage().contains("failure detail"));
        log.setDebug(true);
        log.debug("visible");
        log.error("failed", failure);
        assertTrue(log.isDebugEnabled());
        assertTrue(records.get(1).getMessage().contains("visible"));
        assertSame(failure, records.get(2).getThrown());
    }

    private Condition condition(String type, Object... pairs) { return conditions.createCondition(type, map(pairs)); }
    private Action action(String type, Object... pairs) { return actions.createAction(type, map(pairs)); }
    private PlayerCommandPreprocessEvent event(String command) { return new PlayerCommandPreprocessEvent(player, command); }
    private static Map<String, Object> actionMap(String type, Object... pairs) {
        Map<String, Object> result = map(pairs); result.put("type", type); return result;
    }
    @SafeVarargs private static Map<String, Object> rule(int priority, boolean enabled, Map<String, Object>... actions) {
        return map("priority", priority, "enabled", enabled, "condition", map("type", "regex", "pattern", ".*"),
                "actions", Arrays.asList(actions));
    }
    private static Map<String, Object> map(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) result.put((String) pairs[i], pairs[i + 1]);
        return result;
    }
    private String resource(String name) throws Exception {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(name)) {
            assertNotNull(stream, "Missing packaged resource " + name);
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[2048]; int read;
            while ((read = stream.read(buffer)) != -1) bytes.write(buffer, 0, read);
            return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        }
    }
    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        return 0;
    }
    private static final class RecordingScripts implements Scripts {
        final List<String> tested = new ArrayList<>();
        final List<String> executed = new ArrayList<>();
        boolean result = true;
        Player player;
        String command;
        PlayerCommandPreprocessEvent event;
        public void validateCondition(String source) { }
        public void validateAction(String source) { }
        public boolean test(Player player, String command, String source) {
            this.player = player; this.command = command; tested.add(source); return result;
        }
        public void execute(Player player, String command, PlayerCommandPreprocessEvent event, String source) {
            this.player = player; this.command = command; this.event = event; executed.add(source);
            if (source.contains("cancel_cmd_event")) event.setCancelled(true);
        }
    }
}
