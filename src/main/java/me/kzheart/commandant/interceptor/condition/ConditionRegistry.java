package me.kzheart.commandant.interceptor.condition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.function.Function;
import java.util.regex.Pattern;
import me.kzheart.commandant.config.Values;
import me.kzheart.commandant.script.Scripts;

/** Extensible Java replacement for the former Kotlin DSL. */
public final class ConditionRegistry {
    private final Map<String, Function<Map<String, Object>, Condition>> factories = new LinkedHashMap<>();

    public ConditionRegistry(Scripts scripts) {
        register("regex", config -> {
            Pattern pattern = Pattern.compile(Values.string(config, "pattern"));
            return (player, command) -> pattern.matcher(command).matches();
        });
        register("all", config -> composite(config, true));
        register("any", config -> composite(config, false));
        register("has_permission", config -> permission(config, false));
        register("no_permission", config -> permission(config, true));
        register("world", config -> {
            Object worlds = config.get("worlds");
            if (!(worlds instanceof String) && !(worlds instanceof List)) {
                throw new Values.MissingParameterException("worlds 必须是字符串或列表");
            }
            List<String> names = new ArrayList<>();
            if (worlds instanceof String) names.add((String) worlds);
            else for (Object value : (List<?>) worlds) if (value instanceof String) names.add((String) value);
            String message = optionalMessage(config);
            return (player, command) -> {
                boolean matches = names.contains(player.getWorld().getName());
                if (!matches && message != null) player.sendMessage(message);
                return matches;
            };
        });
        register("script", config -> {
            String source = Values.string(config, "script");
            scripts.validateCondition(source);
            return (player, command) -> scripts.test(player, command, source);
        });
    }

    public void register(String type, Function<Map<String, Object>, Condition> factory) { factories.put(type, factory); }
    public void unregisterCondition(String type) { factories.remove(type); }
    public Condition createCondition(String type, Map<String, Object> config) {
        Function<Map<String, Object>, Condition> factory = factories.get(type);
        try {
            return factory == null ? null : factory.apply(config);
        } catch (Values.MissingParameterException missing) {
            return null; // Original DSL ignores malformed required parameters.
        }
    }

    private Condition composite(Map<String, Object> config, boolean all) {
        if (!(config.get("rules") instanceof List)) throw new Values.MissingParameterException("rules 必须是列表");
        List<Condition> children = new ArrayList<>();
        for (Object raw : Values.list(config.get("rules"))) {
            Map<String, Object> rule = Values.map(raw);
            Object type = rule.get("type");
            if (!(type instanceof String)) continue;
            Condition child = createCondition((String) type, rule);
            if (child != null) children.add(child);
        }
        return (player, command) -> {
            for (Condition child : children) {
                boolean matched = child.test(player, command);
                if (all && !matched) return false;
                if (!all && matched) return true;
            }
            return all;
        };
    }

    private Condition permission(Map<String, Object> config, boolean negate) {
        String permission = Values.string(config, "permission");
        String message = optionalMessage(config);
        return (player, command) -> {
            boolean matches = player.hasPermission(permission) != negate;
            if (!matches && message != null) player.sendMessage(message);
            return matches;
        };
    }

    private static String optionalMessage(Map<String, Object> config) {
        return config.get("message") instanceof String ? (String) config.get("message") : null;
    }
}
