package me.kzheart.commandant.script;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.logging.Logger;
import me.kzheart.commandant.script.fixture.common.OpenAPI;
import me.kzheart.commandant.script.fixture.platform.BukkitPlugin;
import me.kzheart.klib.script.TabooLibKetherInterop.OpenContainer;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KetherDiscoveryTest {
    private Server previous;
    private BukkitPlugin foreign;
    private KetherContainers discovery;

    @BeforeEach void setUp() throws Exception {
        previous = Bukkit.getServer();
        OpenAPI.reset();
        foreign = new BukkitPlugin();
        Plugin owner = proxy(Plugin.class, (self, method, args) -> {
            if (method.getName().equals("getName")) return "owner";
            if (method.getName().equals("getLogger")) return Logger.getLogger("discovery-test");
            if (method.getName().equals("isEnabled")) return true;
            return null;
        });
        PluginManager manager = proxy(PluginManager.class, (self, method, args) -> {
            if (method.getName().equals("getPlugins")) return new Plugin[]{owner, foreign};
            return null;
        });
        setServer(proxy(Server.class, (self, method, args) -> {
            if (method.getName().equals("getPluginManager")) return manager;
            return null;
        }));
        discovery = new KetherContainers(owner);
    }

    @AfterEach void tearDown() throws Exception { setServer(previous); OpenAPI.reset(); }

    @Test void pluginShapeDiscoveryConvertsForeignKotlinStyleResult() {
        List<? extends OpenContainer> found = discovery.discover();
        assertEquals(1, found.size());
        assertEquals("foreign-fixture", found.get(0).name());
        Object marker = new Object();
        OpenAPI.value = marker;
        assertSame(marker, found.get(0).call("some-supported-channel").getValue());
        assertSame(found.get(0), discovery.find("foreign-fixture"));
    }

    @Test void discoveryConvertsForeignBooleanStyleResult() {
        OpenAPI.booleanGetter = true;
        OpenContainer container = discovery.discover().get(0);
        assertTrue(container.call("some-supported-channel").isSuccessful());
        assertEquals("foreign-result", container.call("some-supported-channel").getValue());
    }

    @Test void noKetherPluginCannotReceiveSharedActionRegistration() {
        OpenAPI.kether = false;
        assertTrue(discovery.discover().isEmpty());
        assertNull(discovery.find("foreign-fixture"));
        assertFalse(OpenAPI.calls.isEmpty());
        assertTrue(OpenAPI.calls.stream().allMatch("kether_create_exit_status"::equals));
    }

    @Test void disabledPluginIsNotExposedEvenAfterCaching() {
        assertEquals(1, discovery.discover().size());
        foreign.enabled(false);
        assertTrue(discovery.discover().isEmpty());
        assertNull(discovery.find("foreign-fixture"));
    }

    private static void setServer(Server server) throws Exception {
        Field field = Bukkit.class.getDeclaredField("server");
        field.setAccessible(true);
        field.set(null, server);
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
