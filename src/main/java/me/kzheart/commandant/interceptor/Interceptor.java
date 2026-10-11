package me.kzheart.commandant.interceptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import me.kzheart.commandant.interceptor.action.Action;
import me.kzheart.commandant.interceptor.condition.Condition;
import me.kzheart.commandant.logger.CommandantLogger;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

public final class Interceptor {
    private final String name;
    private final boolean enabled;
    private final int priority;
    private final Condition condition;
    private final List<Action> actions;

    public Interceptor(String name, boolean enabled, int priority, Condition condition, List<Action> actions) {
        this.name = name;
        this.enabled = enabled;
        this.priority = priority;
        this.condition = condition;
        this.actions = Collections.unmodifiableList(new ArrayList<>(actions));
    }

    public String getName() { return name; }
    public boolean isEnabled() { return enabled; }
    public int getPriority() { return priority; }

    public void process(Player player, String command, PlayerCommandPreprocessEvent event, CommandantLogger log) {
        if (!enabled || !condition.test(player, command)) return;
        log.info("⚡ MATCH » " + player.getName() + " 触发拦截器 [" + name + "]");
        for (Action action : actions) action.execute(player, command, event);
    }
}
