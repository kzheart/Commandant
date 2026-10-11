package me.kzheart.commandant.interceptor.action;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import me.kzheart.commandant.config.Values;
import me.kzheart.commandant.logger.CommandantLogger;
import me.kzheart.commandant.script.Scripts;

public final class ActionRegistry {
    private final Map<String, Function<Map<String, Object>, Action>> factories = new LinkedHashMap<>();

    public ActionRegistry(Scripts scripts, CommandantLogger log) {
        register("cancel", config -> (player, command, event) -> event.setCancelled(true));
        register("log", config -> {
            String format = Values.string(config, "format");
            return (player, command, event) -> log.logToFile(format
                    .replace("{player}", player.getName()).replace("{command}", command));
        });
        register("replace", config -> {
            String template = Values.string(config, "command");
            return (player, command, event) -> player.performCommand(template
                    .replace("{player}", player.getName()).replace("{world}", player.getWorld().getName()));
        });
        register("script", config -> {
            String source = Values.string(config, "script");
            scripts.validateAction(source);
            return (player, command, event) -> scripts.execute(player, command, event, source);
        });
    }

    public void register(String type, Function<Map<String, Object>, Action> factory) { factories.put(type, factory); }
    public void unregisterAction(String type) { factories.remove(type); }
    public Action createAction(String type, Map<String, Object> config) {
        Function<Map<String, Object>, Action> factory = factories.get(type);
        try {
            return factory == null ? null : factory.apply(config);
        } catch (Values.MissingParameterException missing) {
            return null; // Original DSL ignores malformed required parameters.
        }
    }
}
