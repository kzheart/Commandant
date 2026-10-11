package me.kzheart.commandant.logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class CommandantLogger {
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final Logger logger;
    private final Path file;
    private volatile boolean debug;

    public CommandantLogger(Logger logger, Path directory) {
        this.logger = logger;
        this.file = directory.resolve("command.log");
    }

    public void setDebug(boolean enabled) { debug = enabled; }
    public boolean isDebugEnabled() { return debug; }
    public void info(String message) { logger.info(message); }
    public void warn(String message) { logger.warning(message); }
    public void error(String message, Throwable failure) {
        if (debug) logger.log(Level.SEVERE, message, failure);
        else logger.severe(message + ": " + failure.getMessage());
    }
    public void debug(String message) { if (debug) logger.info("[Debug] " + message); }

    public synchronized void logToFile(String message) {
        info(message);
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, ("[" + LocalDateTime.now().format(TIMESTAMP) + "] " + message + "\n")
                    .getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException failure) {
            throw new IllegalStateException("无法写入 command.log", failure);
        }
    }
}
