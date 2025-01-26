package me.kzheart.commandant.interceptor.condition

import org.bukkit.entity.Player

interface Condition {
    /**
     * 测试条件是否满足
     * @param player 执行命令的玩家
     * @param command 执行的命令
     * @return 如果条件满足返回true，否则返回false
     */
    fun test(player: Player, command: String): Boolean
}