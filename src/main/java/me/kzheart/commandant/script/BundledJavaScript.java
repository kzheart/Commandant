package me.kzheart.commandant.script;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import me.kzheart.klib.script.JavaScriptEvaluator;
import me.kzheart.klib.script.ScriptJavaScriptEngines;

/** Nashorn backed exclusively by the JDK or dependencies shipped inside this plugin. */
final class BundledJavaScript implements JavaScriptEvaluator, AutoCloseable {
    private static final String[] JARS = {
        "nashorn-core-15.6.jar", "asm-7.3.1.jar", "asm-commons-7.3.1.jar",
        "asm-tree-7.3.1.jar", "asm-util-7.3.1.jar", "asm-analysis-7.3.1.jar"
    };
    private JavaScriptEvaluator delegate;
    private URLClassLoader loader;
    private Path directory;
    private final List<Path> extracted = new ArrayList<Path>();

    static BundledJavaScript create() {
        BundledJavaScript result = new BundledJavaScript();
        try {
            ClassLoader application = BundledJavaScript.class.getClassLoader();
            int javaVersion = javaVersion();
            ScriptEngine engine;
            if (javaVersion < 11) {
                // Never ask Java 8 to inspect standalone Java 11 class files.
                Class<?> factoryType = Class.forName("jdk.nashorn.api.scripting.NashornScriptEngineFactory", true, application);
                ScriptEngineFactory factory = (ScriptEngineFactory) factoryType.getDeclaredConstructor().newInstance();
                engine = factory.getScriptEngine();
            } else {
                result.directory = Files.createTempDirectory("commandant-js-");
                // deleteOnExit is a crash/failed-cleanup fallback; normal disable removes everything.
                result.directory.toFile().deleteOnExit();
                URL[] urls = new URL[JARS.length];
                for (int index = 0; index < JARS.length; index++) {
                    String name = JARS[index];
                    Path target = result.directory.resolve(name);
                    try (InputStream source = BundledJavaScript.class.getResourceAsStream("/META-INF/commandant-js/" + name)) {
                        if (source == null) throw new IOException("Missing bundled JavaScript dependency: " + name);
                        // Fresh private directory, fixed names, no replacement of pre-existing files.
                        result.extracted.add(target);
                        Files.copy(source, target);
                        target.toFile().deleteOnExit();
                    }
                    urls[index] = target.toUri().toURL();
                }
                result.loader = new IsolatedLoader(urls, application);
                Class<?> factoryType = Class.forName("org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory", true, result.loader);
                Object factory = factoryType.getDeclaredConstructor().newInstance();
                // Java.type() must see Bukkit and this plugin, not an unrelated context loader.
                engine = (ScriptEngine) factoryType.getMethod("getScriptEngine", ClassLoader.class).invoke(factory, application);
            }
            result.delegate = ScriptJavaScriptEngines.using(engine);
            return result;
        } catch (ReflectiveOperationException | IOException | RuntimeException | LinkageError failure) {
            try { result.close(); } catch (Exception cleanup) { failure.addSuppressed(cleanup); }
            Throwable cause = failure instanceof InvocationTargetException ? failure.getCause() : failure;
            throw new IllegalStateException("Could not start bundled Nashorn JavaScript engine", cause);
        }
    }

    @Override public Object eval(String source, Map<String, Object> bindings) {
        if (delegate == null) throw new IllegalStateException("JavaScript evaluator is closed");
        return delegate.eval(source, bindings);
    }

    @Override public void close() throws Exception {
        JavaScriptEvaluator closing = delegate;
        delegate = null;
        Exception failure = null;
        try {
            if (closing instanceof AutoCloseable) ((AutoCloseable) closing).close();
        } catch (Exception error) { failure = error; }
        try {
            if (loader != null) loader.close();
        } catch (IOException error) {
            if (failure == null) failure = error; else failure.addSuppressed(error);
        } finally { loader = null; }
        for (Path path : extracted) {
            try { Files.deleteIfExists(path); }
            catch (IOException error) { if (failure == null) failure = error; else failure.addSuppressed(error); }
        }
        if (directory != null) {
            try { Files.deleteIfExists(directory); }
            catch (IOException error) { if (failure == null) failure = error; else failure.addSuppressed(error); }
        }
        if (failure != null) throw failure;
    }

    Path extractedDirectory() { return directory; }

    private static int javaVersion() {
        String version = System.getProperty("java.specification.version");
        return Integer.parseInt(version.startsWith("1.") ? version.substring(2) : version);
    }

    /** Keep this engine's ASM/Nashorn independent of server or plugin dependency versions. */
    private static final class IsolatedLoader extends URLClassLoader {
        IsolatedLoader(URL[] urls, ClassLoader parent) { super(urls, parent); }
        @Override protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("org.openjdk.nashorn.") || name.startsWith("org.objectweb.asm.")) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) loaded = findClass(name);
                if (resolve) resolveClass(loaded);
                return loaded;
            }
            return super.loadClass(name, resolve);
        }
    }
}
