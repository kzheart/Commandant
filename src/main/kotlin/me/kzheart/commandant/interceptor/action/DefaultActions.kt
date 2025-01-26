package me.kzheart.commandant.interceptor.action

import me.kzheart.commandant.logger.log
import taboolib.module.kether.KetherShell
import taboolib.module.kether.ScriptOptions

/**
 * 注册默认的动作
 */
fun registerDefaultActions() {
    with(ActionRegistry) {
        // 注册取消动作
        action("cancel") {
            execute { player, _, event, params ->
                event.isCancelled = true
            }
        }

        // 注册日志动作
        action("log") {
            parameter("format", parser = { it as? String })
            execute { player, command, _, params ->
                val message = (params["format"] as String)
                    .replace("{player}", player.name)
                    .replace("{command}", command)
                log.logToFile(message)
            }
        }

        // 注册替换动作
        action("replace") {
            parameter("command", parser = { it as? String })
            execute { player, _, event, params ->
                val finalCommand = (params["command"] as String)
                    .replace("{player}", player.name)
                    .replace("{world}", player.world.name)
                player.performCommand(finalCommand)
            }
        }

        // 注册脚本动作
        action("script") {
            parameter("script", parser = { it as? String })
            execute { player, _, event, params ->
                val script = (params["script"] as String)
                KetherShell.eval(script, ScriptOptions.new {
                    namespace(listOf("commandant", "kether"))
                    sender(player)
                    set("@PlayerCommandPreprocessEvent", event)
                })
            }
        }
    }
} 