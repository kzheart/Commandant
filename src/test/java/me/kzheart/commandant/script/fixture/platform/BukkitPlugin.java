package me.kzheart.commandant.script.fixture.platform;

import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginLoader;

/** Deliberately matches the public TabooLib entrypoint shape without loading TabooLib. */
public final class BukkitPlugin implements Plugin {
    private boolean enabled = true;
    public void enabled(boolean value) { enabled = value; }
    @Override public String getName() { return "foreign-fixture"; }
    @Override public boolean isEnabled() { return enabled; }
    @Override public Logger getLogger() { return Logger.getLogger(getName()); }
    @Override public File getDataFolder() { return null; }
    @Override public PluginDescriptionFile getDescription() { return null; }
    @Override public FileConfiguration getConfig() { return null; }
    @Override public InputStream getResource(String name) { return null; }
    @Override public void saveConfig() { }
    @Override public void saveDefaultConfig() { }
    @Override public void saveResource(String name, boolean replace) { }
    @Override public void reloadConfig() { }
    @Override public PluginLoader getPluginLoader() { return null; }
    @Override public Server getServer() { return null; }
    @Override public void onDisable() { }
    @Override public void onLoad() { }
    @Override public void onEnable() { }
    @Override public boolean isNaggable() { return false; }
    @Override public void setNaggable(boolean value) { }
    @Override public ChunkGenerator getDefaultWorldGenerator(String world, String id) { return null; }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) { return false; }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) { return null; }
}
