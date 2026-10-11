package me.kzheart.commandant;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import me.kzheart.commandant.command.CommandantCommands;
import me.kzheart.commandant.config.Values;
import me.kzheart.commandant.interceptor.InterceptorManager;
import me.kzheart.commandant.interceptor.action.ActionRegistry;
import me.kzheart.commandant.interceptor.condition.ConditionRegistry;
import me.kzheart.commandant.logger.CommandantLogger;
import me.kzheart.commandant.script.ScriptService;
import me.kzheart.klib.KPlugin;
import me.kzheart.klib.command.CommandModule;
import me.kzheart.klib.config.YamlDocument;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

public final class Commandant extends KPlugin implements Listener {
    private CommandantLogger log;
    private ScriptService scripts;
    private InterceptorManager interceptors;

    @Override
    protected void setup() {
        log = new CommandantLogger(getLogger(), getDataFolder().toPath());
        scripts = new ScriptService(context().scope(), this);
        interceptors = new InterceptorManager(new ConditionRegistry(scripts), new ActionRegistry(scripts, log), log);
        copyDefault("config.yml");
        copyDefault("interceptors.yml");
        reload();
        CommandModule.install(this);
        commands().register(new CommandantCommands(this));
        events().register(this);
        log.info("Commandant 已加载");
    }

    /** Explicit admin reload only; never rewrites existing YAML or re-registers commands. */
    public void reload() {
        Map<String, Object> settings = read("config.yml");
        Map<String, Object> rules = read("interceptors.yml");
        scripts.cancelPending();
        log.setDebug(Values.bool(settings.get("debug"), false));
        interceptors.reload(rules);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = false)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        interceptors.processCommand(event.getPlayer(), event.getMessage(), event);
    }

    public CommandantLogger commandLog() { return log; }

    private void copyDefault(String name) {
        if (!Files.exists(getDataFolder().toPath().resolve(name))) saveResource(name, false);
    }

    private Map<String, Object> read(String name) {
        try {
            String yaml = new String(Files.readAllBytes(getDataFolder().toPath().resolve(name)), StandardCharsets.UTF_8);
            return Values.map(YamlDocument.parse(name, yaml).root().raw());
        } catch (IOException failure) {
            throw new IllegalStateException("无法读取 " + name, failure);
        }
    }
}
