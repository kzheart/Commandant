package me.kzheart.commandant.kether

import org.bukkit.event.player.PlayerCommandPreprocessEvent
import taboolib.module.kether.ScriptFrame

fun ScriptFrame.playerCommandPreprocessEvent(): PlayerCommandPreprocessEvent {
    return variables().get<Any?>("@PlayerCommandPreprocessEvent").orElse(null) as? PlayerCommandPreprocessEvent
        ?: error("No command selected.")
}