package me.kzheart.commandant.interceptor

import me.kzheart.commandant.logger.log
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration
import taboolib.module.configuration.util.getMap

object InterceptorManager {

    @Config("interceptors.yml")
    lateinit var config: Configuration
        private set

    private val interceptors = mutableMapOf<String, Interceptor>()

    fun reload() {
        config.reload()
        interceptors.clear()
        log.info("正在加载拦截器配置...")

        val section = config.getConfigurationSection("interceptors")
        if (section == null) {
            log.warn("未找到拦截器配置节点")
            return
        }

        var loadedCount = 0
        var failedCount = 0

        section.getKeys(false).forEach { key ->
            val subsection = section.getConfigurationSection(key) ?: return@forEach
            log.debug("正在加载拦截器: $key")

            try {
                val interceptorConfig = InterceptorConfig(
                    enabled = subsection.getBoolean("enabled", true),
                    priority = subsection.getInt("priority", 0),
                    condition = subsection.getMap("condition"),
                    actions = subsection.getList("actions")?.filterIsInstance<Map<String, Any?>>() ?: emptyList()
                )
                //打印一下配置
                log.debug("拦截器配置: $interceptorConfig")

                // 创建拦截器
                Interceptor.fromConfig(key, interceptorConfig)?.let {
                    interceptors[key] = it
                    loadedCount++
                    log.debug("成功加载拦截器: $key (优先级: ${it.config.priority}, 已启用: ${it.config.enabled})")
                } ?: run {
                    failedCount++
                    log.error("加载拦截器失败: $key - 无效的配置")
                }
            } catch (e: Exception) {
                failedCount++
                log.error("加载拦截器出错: $key")
                log.error("错误信息: ${e.message}")
                if (log.isDebugEnabled) {
                    e.printStackTrace()
                }
            }
        }

        log.success("拦截器加载完成! 成功: $loadedCount, 失败: $failedCount")
    }

    fun processCommand(player: Player, command: String, event: PlayerCommandPreprocessEvent) {
        log.debug("处理命令: ${player.name} -> $command")
        interceptors.values
            .filter { it.config.enabled }
            .sortedBy { it.config.priority }
            .forEach { interceptor ->
                log.debug("检查拦截器: ${interceptor.name} (优先级: ${interceptor.config.priority})")
                interceptor.process(player, command, event)
            }
    }
} 