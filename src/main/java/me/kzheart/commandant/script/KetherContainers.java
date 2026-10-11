package me.kzheart.commandant.script;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import me.kzheart.klib.script.TabooLibKetherInterop;
import me.kzheart.klib.script.TabooLibKetherInterop.OpenContainer;
import me.kzheart.klib.script.TabooLibKetherInterop.OpenResult;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/** Discovers only containers that actually implement the Kether protocol. */
final class KetherContainers implements TabooLibKetherInterop.ContainerDiscovery {
    private static final String SUFFIX = ".platform.BukkitPlugin";
    private final Plugin owner;
    private final Map<Plugin, OpenContainer> cache = new IdentityHashMap<Plugin, OpenContainer>();
    private final Map<Plugin, Boolean> warned = new IdentityHashMap<Plugin, Boolean>();

    KetherContainers(Plugin owner) { this.owner = owner; }

    @Override public List<? extends OpenContainer> discover() {
        List<OpenContainer> result = new ArrayList<OpenContainer>();
        for (Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
            String main = plugin.getClass().getName();
            if (plugin == owner || !main.endsWith(SUFFIX) || !plugin.isEnabled()) continue;
            OpenContainer container = cache.get(plugin);
            if (container == null) {
                try {
                    String apiName = main.substring(0, main.length() - SUFFIX.length()) + ".common.OpenAPI";
                    Class<?> api = Class.forName(apiName, false, plugin.getClass().getClassLoader());
                    Method call = api.getMethod("call", String.class, Object[].class);
                    container = new ReflectiveContainer(plugin.getName(), call);
                    if (!supportsKether(container)) continue;
                    cache.put(plugin, container);
                } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                    if (warned.put(plugin, Boolean.TRUE) == null) {
                        owner.getLogger().warning("Skipping incompatible Kether container " + plugin.getName() + ": " + failure);
                    }
                    continue;
                }
            }
            result.add(container);
        }
        return result;
    }

    @Override public OpenContainer find(String name) {
        for (OpenContainer container : discover()) if (container.name().equals(name)) return container;
        return null;
    }

    static boolean supportsKether(OpenContainer container) {
        // Pure object construction, no parser registration or script execution.
        OpenResult probe = container.call("kether_create_exit_status", Boolean.FALSE, Boolean.FALSE, Long.valueOf(0));
        return probe != null && probe.isSuccessful();
    }

    private static final class ReflectiveContainer implements OpenContainer {
        private final String name;
        private final Method call;
        ReflectiveContainer(String name, Method call) { this.name = name; this.call = call; }
        @Override public String name() { return name; }
        @Override public OpenResult call(String channel, Object... data) {
            try {
                Object result = call.invoke(null, channel, data);
                if (result == null) return OpenResult.failed();
                if (result instanceof OpenResult) return (OpenResult) result;
                Method successful;
                try { successful = result.getClass().getMethod("isSuccessful"); }
                catch (NoSuchMethodException missing) { successful = result.getClass().getMethod("getSuccessful"); }
                if (!Boolean.TRUE.equals(successful.invoke(result))) return OpenResult.failed();
                return OpenResult.successful(result.getClass().getMethod("getValue").invoke(result));
            } catch (InvocationTargetException failure) {
                throw new IllegalStateException("Kether container call failed in " + name, failure.getCause());
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Incompatible Kether container result in " + name, failure);
            }
        }
    }
}
