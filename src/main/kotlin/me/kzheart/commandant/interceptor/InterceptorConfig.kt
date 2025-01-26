package me.kzheart.commandant.interceptor

data class InterceptorConfig(
    val enabled: Boolean = true,
    val priority: Int = 0,
    val condition: Map<String, Any?>,
    val actions: List<Map<String, Any?>>
)

