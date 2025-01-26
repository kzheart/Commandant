package me.kzheart.commandant.interceptor

import me.kzheart.commandant.interceptor.action.Action
import me.kzheart.commandant.interceptor.action.ActionRegistry
import me.kzheart.commandant.interceptor.condition.Condition
import me.kzheart.commandant.interceptor.condition.ConditionRegistry
import me.kzheart.commandant.logger.log
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerCommandPreprocessEvent

class Interceptor(
    val name: String,
    val config: InterceptorConfig,
    private val condition: Condition?,
    private val actions: List<Action>
) {
    /**
     * 处理命令
     * @return 如果命令应该继续执行返回true，否则返回false
     */
    fun process(player: Player, command: String, event: PlayerCommandPreprocessEvent) {
        if (!config.enabled || condition == null) {
            return
        }

        val conditionMet = condition.test(player, command)
        if (conditionMet) {
            log.info("⚡ MATCH » ${player.name} 触发拦截器 [$name]")
            if (actions.isNotEmpty()) {
                actions.forEach { it.execute(player, command, event) }
            }
        }
    }

    companion object {
        /**
         * 从配置创建拦截器
         */
        fun fromConfig(name: String, config: InterceptorConfig): Interceptor? {
            val condition = config.condition["type"]?.toString()?.let { type ->
                ConditionRegistry.createCondition(type, config.condition)
            }

            val actions = config.actions.mapNotNull { actionConfig ->
                actionConfig["type"]?.toString()?.let { type ->
                    ActionRegistry.createAction(type, actionConfig)
                }
            }

            if (condition == null) {
                log.error("⚡ ERROR » 拦截器 [$name] 配置无效")
                return null
            }

            return Interceptor(name, config, condition, actions)
        }
    }
} 