package me.kzheart.commandant.interceptor.action

import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerCommandPreprocessEvent

class ActionBuilder {
    private val parameters = mutableMapOf<String, Parameter<*>>()
    private var executor: ((Player, String, PlayerCommandPreprocessEvent, Map<String, Any?>) -> Unit)? = null

    fun <T> parameter(name: String, required: Boolean = true, parser: (Any?) -> T?) {
        parameters[name] = Parameter(required, parser)
    }

    fun execute(block: (Player, String, PlayerCommandPreprocessEvent, Map<String, Any?>) -> Unit) {
        executor = block
    }

    fun build(config: Map<String, Any?>): Action? {
        val values = mutableMapOf<String, Any?>()

        // 读取所有参数
        parameters.forEach { (name, parameter) ->
            val value = parameter.parser(config[name])
            if (value == null && parameter.required) {
                return null
            }
            values[name] = value
        }

        val exec = executor ?: return null
        return object : Action {
            override fun execute(player: Player, command: String, event: PlayerCommandPreprocessEvent) {
                return exec(player, command, event, values)
            }
        }
    }

    private data class Parameter<T>(
        val required: Boolean,
        val parser: (Any?) -> T?
    )
}

fun action(init: ActionBuilder.() -> Unit): (Map<String, Any?>) -> Action? {
    val builder = ActionBuilder().apply(init)
    return { config -> builder.build(config) }
} 