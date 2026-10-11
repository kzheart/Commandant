package fixture.platform;
public final class BukkitPlugin extends org.bukkit.plugin.java.JavaPlugin {
 public void onEnable(){getLogger().info("NON_KETHER_FIXTURE_ENABLED");}
 public void onDisable(){getLogger().info("NON_KETHER_FIXTURE_CALLS="+fixture.common.OpenAPI.calls);}
}
