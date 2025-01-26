package me.kzheart.commandant.interceptor.condition

object ConditionRegistry {
    private val factories = mutableMapOf<String, (Map<String, Any?>) -> Condition?>()

    init {
        registerDefaultConditions()
    }

    /**
     * 注册一个条件类型
     */
    fun condition(type: String, init: ConditionBuilder.() -> Unit) {
        factories[type] = condition(init)
    }

    /**
     * 从配置创建条件实例
     */
    fun createCondition(type: String, config: Map<String, Any?>): Condition? {
        return factories[type]?.invoke(config)
    }

    /**
     * 注销一个条件类型
     */
    fun unregisterCondition(type: String) {
        factories.remove(type)
    }
} 