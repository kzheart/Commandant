package me.kzheart.commandant.interceptor.action;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

@FunctionalInterface
public interface Action {
    void execute(Player player, String command, PlayerCommandPreprocessEvent event);
}
