package me.kzheart.commandant.script;

import me.kzheart.klib.script.TabooLibKetherInterop.OpenContainer;
import me.kzheart.klib.script.TabooLibKetherInterop.OpenResult;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KetherContainersTest {
    @Test void nonKetherContainerIsExcludedWithoutRegisteringAnything() {
        OpenContainer container = new OpenContainer() {
            @Override public String name() { return "TabooLib-without-Kether"; }
            @Override public OpenResult call(String channel, Object... args) {
                assertEquals("kether_create_exit_status", channel);
                return OpenResult.failed();
            }
        };
        assertFalse(KetherContainers.supportsKether(container));
    }

    @Test void actualKetherContainerIsIncludedUsingNonMutatingProbe() {
        OpenContainer container = new OpenContainer() {
            @Override public String name() { return "TabooLib-with-Kether"; }
            @Override public OpenResult call(String channel, Object... args) {
                assertEquals("kether_create_exit_status", channel);
                assertArrayEquals(new Object[]{false, false, 0L}, args);
                return OpenResult.successful(new Object());
            }
        };
        assertTrue(KetherContainers.supportsKether(container));
    }
}
