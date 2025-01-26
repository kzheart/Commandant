package me.kzheart.commandant.kether

import taboolib.module.kether.KetherParser
import taboolib.module.kether.actionNow
import taboolib.module.kether.scriptParser

object KetherActions {
    @KetherParser(["cancel_cmd_event"], namespace = "commandant", shared = true)
    internal fun cancelCmdEvent() = scriptParser {
        actionNow {
            playerCommandPreprocessEvent().isCancelled = true
            null
        }
    }


}