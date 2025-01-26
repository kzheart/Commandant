package me.kzheart.commandant.interceptor.action

object ActionRegistry {

    private val factories = mutableMapOf<String, (Map<String, Any?>) -> Action?>()

    init {
        registerDefaultActions()
    }

    /**
     * 注册一个动作类型
     */
    fun action(type: String, init: ActionBuilder.() -> Unit) {
        factories[type] = action(init)
    }

    /**
     * 从配置创建动作实例
     */
    fun createAction(type: String, config: Map<String, Any?>): Action? {
        return factories[type]?.invoke(config)
    }

    /**
     * 注销一个动作类型
     */
    fun unregisterAction(type: String) {
        factories.remove(type)
    }
} 