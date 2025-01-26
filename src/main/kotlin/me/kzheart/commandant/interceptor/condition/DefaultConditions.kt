package me.kzheart.commandant.interceptor.condition

import me.kzheart.commandant.logger.log
import taboolib.module.kether.KetherShell
import taboolib.module.kether.ScriptOptions

/**
 * 注册默认的条件
 */
fun registerDefaultConditions() {
    with(ConditionRegistry) {
        // 注册正则表达式条件
        condition("regex") {
            parameter("pattern", parser = { it as? String })
            test { _, command, params ->
                val regex = params["pattern"] as String
                command.matches(regex.toRegex())
            }
        }

        // 注册"与"条件
        condition("all") {
            parameter("rules", parser = { it as? List<Map<String, Any?>> })
            test { player, command, params ->
                val rules = params["rules"] as List<Map<String, Any?>>
                val conditions = rules.mapNotNull { rule ->
                    val ruleType = rule["type"] as? String ?: return@mapNotNull null
                    createCondition(ruleType, rule)
                }
                conditions.all { it.test(player, command) }
            }
        }

        // 注册"或"条件
        condition("any") {
            parameter("rules", parser = { it as? List<Map<String, Any?>> })
            test { player, command, params ->
                val rules = params["rules"] as List<Map<String, Any?>>
                val conditions = rules.mapNotNull { rule ->
                    val ruleType = rule["type"] as? String ?: return@mapNotNull null
                    createCondition(ruleType, rule)
                }
                conditions.any { it.test(player, command) }
            }
        }

        // 注册有权限条件
        condition("has_permission") {
            parameter("permission", parser = { it as? String })
            parameter("message", required = false, parser = { it as? String })
            test { player, command, params ->
                val permission = params["permission"] as String
                val message = params["message"] as? String
                val hasPermission = player.hasPermission(permission)
                if (!hasPermission && message != null) {
                    player.sendMessage(message)
                }
                hasPermission
            }
        }

        // 注册无权限条件
        condition("no_permission") {
            parameter("permission", parser = { it as? String })
            parameter("message", required = false, parser = { it as? String })
            test { player, command, params ->
                val permission = params["permission"] as String
                val message = params["message"] as? String
                val noPermission = !player.hasPermission(permission)
                if (!noPermission && message != null) {
                    player.sendMessage(message)
                }
                noPermission
            }
        }

        // 注册世界条件
        condition("world") {
            parameter("worlds", parser = {
                when (it) {
                    is String -> listOf(it)
                    is List<*> -> it.filterIsInstance<String>()
                    else -> null
                }
            })
            parameter("message", required = false, parser = { it as? String })
            test { player, command, params ->
                val worlds = params["worlds"] as List<*>
                val message = params["message"] as? String
                val inWorld = worlds.contains(player.world.name)
                if (!inWorld && message != null) {
                    player.sendMessage(message)
                }
                inWorld
            }
        }

        // 注册脚本条件
        condition("script") {
            parameter("script", parser = { it as? String })
            test { player, command, params ->
                val script = params["script"] as String
                val resultComplete = KetherShell.eval(script, ScriptOptions.new {
                    sender(player)
                    namespace(listOf("kether", "commandant"))
                    set("@command", command)
                })
                val result = resultComplete.get()
                (result as? Boolean) ?: false
            }
        }
    }
} 