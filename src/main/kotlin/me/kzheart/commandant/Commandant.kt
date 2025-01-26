package me.kzheart.commandant

import me.kzheart.commandant.interceptor.InterceptorManager
import me.kzheart.commandant.logger.log
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import taboolib.common.platform.Plugin
import taboolib.common.platform.event.SubscribeEvent
import taboolib.module.configuration.Config
import taboolib.module.configuration.ConfigFile

object Commandant : Plugin() {
    @Config
    lateinit var config: ConfigFile
        private set

    override fun onEnable() {
        log.success("Commandant 已加载")
        InterceptorManager.reload()
    }

    @SubscribeEvent
    fun onCommand(e: PlayerCommandPreprocessEvent) {
        InterceptorManager.processCommand(e.player, e.message, e)
    }

    fun reload() {
        config.reload()
        InterceptorManager.reload()
    }
}