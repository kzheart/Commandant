package me.kzheart.commandant.command;

import me.kzheart.commandant.Commandant;
import me.kzheart.klib.command.annotation.Command;
import me.kzheart.klib.command.annotation.Description;
import me.kzheart.klib.command.annotation.Permission;
import me.kzheart.klib.command.annotation.Route;
import org.bukkit.command.CommandSender;

@Command(value = "commandant", help = true)
@Permission("commandant.admin")
public final class CommandantCommands {
    private final Commandant plugin;

    public CommandantCommands(Commandant plugin) { this.plugin = plugin; }

    @Route("reload")
    @Description("重载配置")
    public void reload(CommandSender sender) {
        try {
            plugin.reload();
            sender.sendMessage("§a配置重载成功!");
        } catch (RuntimeException failure) {
            sender.sendMessage("§c配置重载失败: " + failure.getMessage());
            plugin.commandLog().error("配置重载失败", failure);
        }
    }
}
