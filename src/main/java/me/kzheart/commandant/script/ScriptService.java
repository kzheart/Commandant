package me.kzheart.commandant.script;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import me.kzheart.klib.scope.Scope;
import me.kzheart.klib.script.BukkitScriptHost;
import me.kzheart.klib.script.ScriptContext;
import me.kzheart.klib.script.PlaceholderResolver;
import me.kzheart.klib.script.ScopedScriptRuntime;
import me.kzheart.klib.script.StatementRegistry;
import me.kzheart.klib.script.TabooLibKetherInterop;
import me.kzheart.klib.script.kether.core.QuestAction;
import me.kzheart.klib.script.kether.core.QuestActionParser;
import me.kzheart.klib.script.kether.core.QuestContext;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.Plugin;

/** Standalone Kether integration. Construct, evaluate and close on the server thread. */
public final class ScriptService implements Scripts, AutoCloseable {
    private final Scope scope;
    private final Plugin plugin;
    private final BukkitScriptHost host;
    private final ScopedScriptRuntime actions;
    private final ScopedScriptRuntime conditions;

    public ScriptService(Scope parent, Plugin plugin) {
        requireMainThread();
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.scope = Objects.requireNonNull(parent, "scope").scope("commandant-scripts", ignored -> { });
        try {
            host = BukkitScriptHost.builder(plugin, scope).scoreboard(true)
                    .javascript(BundledJavaScript::create).build();
            StatementRegistry statements = new StatementRegistry();
            TabooLibKetherInterop interop = TabooLibKetherInterop.install(scope, statements, plugin.getName(), new KetherContainers(plugin));
            statements.registerShared(scope, "commandant", "cancel_cmd_event", QuestActionParser.of(reader ->
                    new QuestAction<Object>() {
                    @Override public CompletableFuture<Object> process(QuestContext.Frame frame) {
                        requireMainThread();
                        cancelSelectedEvent(frame);
                        return CompletableFuture.completedFuture(null);
                    }
                    }));
            Executor mainThread = command -> {
                if (!scope.isClosed()) scope.sync(command);
            };
            // Tolerant literals preserve the original Kether shell's parsing behavior.
            actions = new ScopedScriptRuntime(scope, statements, interop, mainThread, true);
            conditions = new ScopedScriptRuntime(scope, statements, interop, mainThread, true);
        } catch (RuntimeException | Error failure) {
            scope.close();
            throw failure;
        }
    }

    void cancelSelectedEvent(QuestContext.Frame frame) {
        requireMainThread();
        Object selected = frame.variables().get("@PlayerCommandPreprocessEvent").orElse(null);
        if (!(selected instanceof PlayerCommandPreprocessEvent)) throw new IllegalStateException("No command selected.");
        if (scope.isClosed()) throw new IllegalStateException("Commandant scripts are closed");
        // Foreign callers own their event lifetime and do not have a Klib ScriptContext.
        Object marker = frame.variables().get("@commandant-event-window").orElse(null);
        if (marker instanceof EventWindow && !((EventWindow) marker).active) {
            throw new IllegalStateException("cancel_cmd_event must run synchronously before the command event returns; move it before wait/async.");
        }
        ((PlayerCommandPreprocessEvent) selected).setCancelled(true);
    }

    /** Compile only; never executes configuration scripts during validation. */
    public void validateCondition(String script) {
        conditions.validate(script, ScriptContext.builder().namespaces("kether", "commandant").build());
    }

    /** Compile only; never executes configuration scripts during validation. */
    public void validateAction(String script) {
        actions.validate(script, ScriptContext.builder().namespaces("commandant", "kether").build());
    }

    /** Only an actual Boolean true matches, just as in the original plugin. */
    public boolean test(Player player, String command, String script) {
        requireMainThread();
        ScriptContext context = context(player)
                .namespaces("kether", "commandant").variable("@command", command).build();
        try {
            CompletableFuture<Object> result = conditions.evalChecked(script, context).toCompletableFuture();
            if (!result.isDone()) {
                result.cancel(false);
                throw new IllegalStateException("Commandant script conditions must finish synchronously; wait, pause and pending async results cannot decide a command event.");
            }
            // getNow never waits for a scheduler task on the server thread.
            return Boolean.TRUE.equals(result.getNow(null));
        } finally {
            // Also cancel detached async branches whose root has already returned.
            conditions.cancelPending();
        }
    }

    /** Starts an action immediately; asynchronous continuations are owned by this scope. */
    public void execute(Player player, String command, PlayerCommandPreprocessEvent event, String script) {
        requireMainThread();
        EventWindow window = new EventWindow();
        ScriptContext context = context(player)
                .namespaces("commandant", "kether")
                .variable("@PlayerCommandPreprocessEvent", Objects.requireNonNull(event, "event"))
                .variable("@commandant-event-window", window).build();
        try {
            CompletableFuture<Object> result = actions.evalChecked(script, context).toCompletableFuture();
            if (result.isDone()) {
                try { result.getNow(null); }
                catch (RuntimeException failure) {
                    if (!(unwrap(failure) instanceof CancellationException)) {
                        plugin.getLogger().log(Level.WARNING, "Commandant script action failed for " + command, unwrap(failure));
                    }
                }
            } else {
                result.whenComplete((value, failure) -> {
                    if (failure != null && !(unwrap(failure) instanceof CancellationException)) {
                        plugin.getLogger().log(Level.WARNING, "Commandant script action failed for " + command, unwrap(failure));
                    }
                });
            }
        } finally {
            window.active = false;
        }
    }

    private ScriptContext.Builder context(Player player) {
        return host.apply(ScriptContext.builder().sender(player))
                .service(PlaceholderResolver.class, (sender, text) -> {
                    requireMainThread();
                    Plugin papi = Bukkit.getPluginManager().getPlugin("PlaceholderAPI");
                    if (papi == null || !papi.isEnabled()) {
                        throw new IllegalStateException("The papi action requires enabled PlaceholderAPI");
                    }
                    if (!(sender instanceof Player)) throw new IllegalStateException("The papi action requires a player");
                    try {
                        Class<?> api = Class.forName("me.clip.placeholderapi.PlaceholderAPI", true,
                                papi.getClass().getClassLoader());
                        Method method = api.getMethod("setPlaceholders", Player.class, String.class);
                        return (String) method.invoke(null, sender, text);
                    } catch (InvocationTargetException failure) {
                        throw new IllegalStateException("PlaceholderAPI expansion failed", failure.getCause());
                    } catch (ReflectiveOperationException | LinkageError failure) {
                        throw new IllegalStateException("The installed PlaceholderAPI does not expose a compatible setPlaceholders API", failure);
                    }
                });
    }

    /** Cancel delayed and detached actions when replacing the loaded configuration. */
    public void cancelPending() {
        requireMainThread();
        conditions.cancelPending();
        actions.cancelPending();
    }

    @Override public void close() {
        requireMainThread();
        scope.close();
    }

    private static Throwable unwrap(Throwable failure) {
        while (failure instanceof CompletionException && failure.getCause() != null) failure = failure.getCause();
        return failure;
    }

    private static void requireMainThread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Commandant scripts must start on the server main thread");
    }

    private static final class EventWindow {
        private boolean active = true;
    }
}
