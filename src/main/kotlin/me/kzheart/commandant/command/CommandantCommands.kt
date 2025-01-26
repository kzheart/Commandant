package me.kzheart.commandant.command

import me.kzheart.commandant.Commandant
import me.kzheart.commandant.logger.CommandantLogger
import org.bukkit.command.CommandSender
import taboolib.common.platform.ProxyCommandSender
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.subCommand
import taboolib.expansion.createHelper

@CommandHeader(name = "commandant", permission = "commandant.admin")
object CommandantCommands {

    @CommandBody
    val main = mainCommand {
        createHelper()
    }

    @CommandBody
    val reload = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            try {
                Commandant.reload()
                sender.sendMessage("§a配置重载成功!")
                CommandantLogger.info("配置已被 ${(sender as? CommandSender)?.name ?: "CONSOLE"} 重载")
            } catch (e: Exception) {
                sender.sendMessage("§c配置重载失败: ${e.message}")
                CommandantLogger.error("配置重载失败 ${e.message}")
            }
        }
    }
} 