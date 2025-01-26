package me.kzheart.commandant.logger

import me.kzheart.commandant.Commandant
import taboolib.common.platform.function.console
import taboolib.common.platform.function.getDataFolder
import taboolib.module.lang.sendErrorMessage
import taboolib.module.lang.sendInfoMessage
import taboolib.module.lang.sendWarnMessage
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object CommandantLogger {
    private val logFile by lazy {
        File(getDataFolder(), "command.log").apply {
            if (!exists()) {
                parentFile.mkdirs()
                createNewFile()
            }
        }
    }

    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    /**
     * 打印普通信息
     */
    fun info(message: String) {
        console().sendInfoMessage("&f❯ $message")
    }

    /**
     * 打印警告信息
     */
    fun warn(message: String) {
        console().sendWarnMessage("&e⚠ $message")
    }

    /**
     * 打印错误信息
     */
    fun error(message: String) {
        console().sendErrorMessage("&c✖ $message")
    }

    /**
     * 打印成功信息
     */
    fun success(message: String) {
        console().sendInfoMessage("&a✔ $message")
    }

    /**
     * 打印调试信息
     */
    fun debug(message: String) {
        if (isDebugEnabled) {
            console().sendInfoMessage("&7[⚙ Debug] &f$message")
        }
    }

    /**
     * 记录命令日志到文件
     */
    fun logToFile(message: String) {
        val now = LocalDateTime.now().format(dateFormat)
        this.info(message)
        logFile.appendText("[$now] $message\n")
    }

    /**
     * 是否启用调试模式
     */
    val isDebugEnabled
        get() = Commandant.config.getBoolean("debug", false)
}

val log = CommandantLogger