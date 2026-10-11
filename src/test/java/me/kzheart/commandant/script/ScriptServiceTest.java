package me.kzheart.commandant.script;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import me.kzheart.klib.scheduler.KScheduler;
import me.kzheart.klib.scheduler.SchedulerFactory;
import me.kzheart.klib.scope.ScopeImpl;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(5)
class ScriptServiceTest {
    private final Thread main = Thread.currentThread();
    private final Map<Integer, Runnable> tasks = new LinkedHashMap<Integer, Runnable>();
    private final List<String> messages = new ArrayList<String>();
    private int nextTask;
    private Server previous;
    private ScopeImpl scope;
    private ScriptService scripts;
    private Player player;

    @BeforeEach void setUp() throws Exception {
        previous = Bukkit.getServer();
        Plugin plugin = proxy(Plugin.class, (self, method, args) -> {
            if (method.getName().equals("isEnabled")) return true;
            if (method.getName().equals("getName")) return "Commandant-test";
            if (method.getName().equals("getLogger")) return Logger.getLogger("Commandant-test");
            return null;
        });
        PluginManager manager = proxy(PluginManager.class, (self, method, args) -> {
            if (method.getName().equals("getPlugins")) return new Plugin[0];
            return null;
        });
        BukkitScheduler scheduler = proxy(BukkitScheduler.class, (self, method, args) -> {
            if (method.getName().equals("runTaskLater") || method.getName().equals("runTask")) {
                int id = ++nextTask;
                tasks.put(id, (Runnable) args[1]);
                return proxy(BukkitTask.class, (task, called, values) -> {
                    if (called.getName().equals("cancel")) tasks.remove(id);
                    if (called.getName().equals("getTaskId")) return id;
                    return null;
                });
            }
            return null;
        });
        Server server = proxy(Server.class, (self, method, args) -> {
            if (method.getName().equals("isPrimaryThread")) return Thread.currentThread() == main;
            if (method.getName().equals("getPluginManager")) return manager;
            if (method.getName().equals("getScheduler")) return scheduler;
            if (method.getName().equals("getLogger")) return Logger.getLogger("Commandant-test");
            if (method.getName().equals("getOnlinePlayers")) return java.util.Collections.emptyList();
            return null;
        });
        setServer(server);
        player = proxy(Player.class, (self, method, args) -> {
            if (method.getName().equals("getName")) return "Alex";
            if (method.getName().equals("getServer")) return server;
            if (method.getName().equals("getUniqueId")) return new UUID(1, 2);
            if (method.getName().equals("isOnline")) return true;
            if (method.getName().equals("hasPermission")) return "allowed".equals(args[0]);
            if (method.getName().equals("sendMessage")) {
                assertEquals(main, Thread.currentThread());
                messages.add((String) args[0]);
            }
            return null;
        });
        scope = new ScopeImpl("test");
        scope.registerCapability(SchedulerFactory.class, child -> proxy(KScheduler.class, (self, method, args) -> {
            if (method.getName().equals("sync")) tasks.put(++nextTask, (Runnable) args[0]);
            return null;
        }));
        scripts = new ScriptService(scope, plugin);
    }

    @AfterEach void tearDown() throws Exception {
        try { if (scope != null) scope.close(); }
        finally { setServer(previous); }
    }

    @Test void preservesCommandVariableAndBooleanOnlyResults() {
        assertTrue(scripts.test(player, "/spawn", "check &@command == '/spawn'"));
        assertFalse(scripts.test(player, "/other", "check &@command == '/spawn'"));
        assertFalse(scripts.test(player, "/spawn", "true"));
        assertFalse(scripts.test(player, "/spawn", "*1"));
        assertTrue(scripts.test(player, "/spawn", "permission allowed"));
    }

    @Test void bundledJavaScriptWorksThroughKetherOnModernJava() {
        assertTrue(scripts.test(player, "/spawn", "js '1 === 1'"));
        assertFalse(scripts.test(player, "/spawn", "js '1 === 2'"));
    }

    @Test void cancelsSelectedCommandWithCustomNamespace() {
        PlayerCommandPreprocessEvent event = event();
        scripts.execute(player, "/spawn", event, "commandant:cancel_cmd_event");
        assertTrue(event.isCancelled());
    }

    @Test void validationHasNoSideEffectsAndRejectsBrokenSyntax() {
        scripts.validateAction("cancel_cmd_event\ntell 'do not send'");
        scripts.validateCondition("check &@command == '/spawn'");
        assertTrue(messages.isEmpty());
        assertThrows(RuntimeException.class, () -> scripts.validateAction("if"));
    }

    @Test void pendingConditionFailsPromptlyAndCancelsItsDelay() {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> scripts.test(player, "/spawn", "wait 1s\ncheck 1 == 1"));
        assertTrue(failure.getMessage().contains("synchronously"));
        assertTrue(tasks.isEmpty());
    }

    @Test void detachedConditionWorkCannotEscape() {
        assertTrue(scripts.test(player, "/spawn", "async { wait 1s\ntell stale }\ncheck 1 == 1"));
        drain();
        assertTrue(messages.isEmpty());
    }

    @Test void delayedActionsResumeAndReloadCancelsOldWork() {
        scripts.execute(player, "/spawn", event(), "wait 1s\ntell later");
        assertTrue(messages.isEmpty());
        drain();
        assertEquals(java.util.Collections.singletonList("later"), messages);
        scripts.execute(player, "/spawn", event(), "wait 1s\ntell stale");
        scripts.cancelPending();
        drain();
        assertEquals(1, messages.size());
    }

    @Test void cancellationAfterYieldDoesNotPretendToVetoCommand() {
        PlayerCommandPreprocessEvent event = event();
        scripts.execute(player, "/spawn", event, "wait 1s\ncancel_cmd_event");
        drain();
        assertFalse(event.isCancelled());
    }

    @Test void foreignFrameNeedsNoPrivateKlibContext() {
        PlayerCommandPreprocessEvent event = event();
        me.kzheart.klib.script.kether.core.QuestContext.VarTable variables = proxy(
                me.kzheart.klib.script.kether.core.QuestContext.VarTable.class, (self, method, args) -> {
                    if (method.getName().equals("get")) return java.util.Optional.ofNullable(
                            "@PlayerCommandPreprocessEvent".equals(args[0]) ? event : null);
                    return null;
                });
        me.kzheart.klib.script.kether.core.QuestContext.Frame frame = proxy(
                me.kzheart.klib.script.kether.core.QuestContext.Frame.class, (self, method, args) -> {
                    if (method.getName().equals("variables")) return variables;
                    throw new AssertionError("Foreign frame private context must not be accessed");
                });
        scripts.cancelSelectedEvent(frame);
        assertTrue(event.isCancelled());
    }

    @Test void runtimeFailureDoesNotPreventLaterCancelAction() {
        PlayerCommandPreprocessEvent event = event();
        assertDoesNotThrow(() -> scripts.execute(player, "/spawn", event, "js \"throw new Error('expected')\""));
        scripts.execute(player, "/spawn", event, "cancel_cmd_event");
        assertTrue(event.isCancelled());
    }

    @Test void scopeCloseCancelsDetachedAction() {
        scripts.execute(player, "/spawn", event(), "async { wait 1s\ntell stale }\n*done");
        scope.close();
        drain();
        assertTrue(messages.isEmpty());
    }

    private PlayerCommandPreprocessEvent event() { return new PlayerCommandPreprocessEvent(player, "/spawn"); }

    private void drain() {
        for (int pass = 0; pass < 50 && !tasks.isEmpty(); pass++) {
            for (Integer id : new ArrayList<Integer>(tasks.keySet())) {
                Runnable task = tasks.remove(id);
                if (task != null) task.run();
            }
        }
        assertTrue(tasks.isEmpty(), "Unexpected nonterminating scheduled work");
    }

    private static void setServer(Server server) throws Exception {
        Field field = Bukkit.class.getDeclaredField("server");
        field.setAccessible(true);
        field.set(null, server);
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (self, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                if (method.getName().equals("equals")) return self == args[0];
                if (method.getName().equals("hashCode")) return System.identityHashCode(self);
                return "Fake " + type.getSimpleName();
            }
            Object value = handler.invoke(self, method, args);
            if (value != null || !method.getReturnType().isPrimitive()) return value;
            if (method.getReturnType() == boolean.class) return false;
            if (method.getReturnType() == int.class) return 0;
            if (method.getReturnType() == long.class) return 0L;
            if (method.getReturnType() == double.class) return 0D;
            if (method.getReturnType() == float.class) return 0F;
            return null;
        }));
    }
}
