package me.kzheart.commandant.script;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BundledJavaScriptTest {
    @Test void bundledEngineEvaluatesBindingsAndJavaInteropThenCleansUp() throws Exception {
        BundledJavaScript engine = BundledJavaScript.create();
        Path extracted = engine.extractedDirectory();
        try {
            assertEquals(Boolean.TRUE, engine.eval("command === '/spawn'", Collections.<String, Object>singletonMap("command", "/spawn")));
            assertEquals(3, ((Number) engine.eval("Java.type('java.lang.Math').abs(-3)", Collections.<String, Object>emptyMap())).intValue());
            assertEquals("undefined", engine.eval("typeof command", Collections.<String, Object>emptyMap()));
            if (extracted != null) assertTrue(Files.isDirectory(extracted));
        } finally { engine.close(); }
        if (extracted != null) assertFalse(Files.exists(extracted));
        assertThrows(IllegalStateException.class, () -> engine.eval("1", Collections.<String, Object>emptyMap()));
        engine.close();
    }
}
