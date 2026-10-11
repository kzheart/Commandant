package me.kzheart.commandant.interceptor.condition;

import org.bukkit.entity.Player;

@FunctionalInterface
public interface Condition {
    boolean test(Player player, String command);
}
