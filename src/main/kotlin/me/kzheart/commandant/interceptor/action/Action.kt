package me.kzheart.commandant.interceptor.action

import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerCommandPreprocessEvent

interface Action {
    /**
     * 执行动作
     */
    fun execute(player: Player, command: String, event: PlayerCommandPreprocessEvent)
}
