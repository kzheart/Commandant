package me.kzheart.commandant.script;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

/** Script boundary used by condition/action factories and embedders. */
public interface Scripts {
    void validateCondition(String script);
    void validateAction(String script);
    boolean test(Player player, String command, String script);
    void execute(Player player, String command, PlayerCommandPreprocessEvent event, String script);
}
