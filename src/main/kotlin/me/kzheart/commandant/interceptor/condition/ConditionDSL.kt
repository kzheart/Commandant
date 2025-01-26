package me.kzheart.commandant.interceptor.condition

import org.bukkit.entity.Player

class ConditionBuilder {
    private val parameters = mutableMapOf<String, Parameter<*>>()
    private var tester: ((Player, String, Map<String, Any?>) -> Boolean)? = null

    fun <T> parameter(name: String, required: Boolean = true, parser: (Any?) -> T?) {
        parameters[name] = Parameter(required, parser)
    }

    fun test(block: (Player, String, Map<String, Any?>) -> Boolean) {
        tester = block
    }

    fun build(config: Map<String, Any?>): Condition? {
        val values = mutableMapOf<String, Any?>()
        
        // 读取所有参数
        parameters.forEach { (name, parameter) ->
            val value = parameter.parser(config[name])
            if (value == null && parameter.required) {
                return null
            }
            values[name] = value
        }
        
        val test = tester ?: return null
        return object : Condition {
            override fun test(player: Player, command: String): Boolean {
                return test(player, command, values)
            }
        }
    }

    private data class Parameter<T>(
        val required: Boolean,
        val parser: (Any?) -> T?
    )
}

fun condition(init: ConditionBuilder.() -> Unit): (Map<String, Any?>) -> Condition? {
    val builder = ConditionBuilder().apply(init)
    return { config -> builder.build(config) }
} 