package smoke;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class CommandantSmoke extends JavaPlugin {
 private final List<String> messages=new ArrayList<String>();
 private final List<String> commands=new ArrayList<String>();
 private boolean permitted;
 private Player player;
 private Plugin target;
 private Path rules;
 private byte[] original;
 public void onEnable(){Bukkit.getScheduler().runTaskLater(this,new Runnable(){public void run(){test();}},40L);}
 private void check(boolean ok,String name){if(!ok)throw new AssertionError(name);getLogger().info("CHECK_PASS "+name);}
 private PlayerCommandPreprocessEvent fire(String command){PlayerCommandPreprocessEvent event=new PlayerCommandPreprocessEvent(player,command);Bukkit.getPluginManager().callEvent(event);return event;}
 private void reload() {check(Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"commandant reload"),"reload command registered");}
 private void test(){
  try {
   target=Bukkit.getPluginManager().getPlugin("Commandant");check(target!=null&&target.isEnabled(),"plugin enabled");
   rules=target.getDataFolder().toPath().resolve("interceptors.yml");original=Files.readAllBytes(rules);
   player=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},new InvocationHandler(){public Object invoke(Object proxy,Method method,Object[] args){
    String name=method.getName();if(name.equals("getName"))return "SmokeTester";if(name.equals("getUniqueId"))return UUID.fromString("00000000-0000-0000-0000-000000000042");
    if(name.equals("getWorld"))return Bukkit.getWorlds().get(0);if(name.equals("getLocation"))return new Location(Bukkit.getWorlds().get(0),0,64,0);
    if(name.equals("getServer"))return Bukkit.getServer();if(name.equals("hasPermission")||name.equals("isPermissionSet"))return permitted;
    if(name.equals("isOnline")||name.equals("isValid"))return true;if(name.equals("sendMessage")){if(args!=null)for(Object arg:args){if(arg instanceof String)messages.add((String)arg);else if(arg instanceof String[])messages.addAll(Arrays.asList((String[])arg));}return null;}
    if(name.equals("performCommand")){commands.add((String)args[0]);return true;}if(name.equals("toString"))return "CommandantSmokePlayer";if(name.equals("hashCode"))return 42;if(name.equals("equals"))return proxy==args[0];
    Class<?> type=method.getReturnType();if(type==boolean.class)return false;if(type==int.class)return 0;if(type==long.class)return 0L;if(type==double.class)return 0d;if(type==float.class)return 0f;if(type==byte.class)return (byte)0;if(type==short.class)return (short)0;if(type==char.class)return (char)0;if(type==Set.class)return Collections.emptySet();if(type==List.class)return Collections.emptyList();return null;
   }});
   reload();check(Arrays.equals(original,Files.readAllBytes(rules)),"reload preserves YAML bytes");
   check(fire("/give SmokeTester diamond").isCancelled(),"default script cancels blocked command");check(!messages.isEmpty(),"default tell action delivered");
   messages.clear();permitted=false;check(!fire("/home").isCancelled(),"permission miss remains uncancelled");check(messages.toString().contains("没有"),"no_permission route");
   messages.clear();permitted=true;fire("/home");check(messages.toString().contains("拥有"),"has_permission route");
   String yaml="interceptors:\n  low:\n    priority: 1\n    condition: {type: regex, pattern: '^/smoke$'}\n    actions:\n      - {type: replace, command: 'say low-{player}-{world}'}\n      - {type: cancel}\n  high:\n    priority: 2\n    condition: {type: regex, pattern: '^/smoke$'}\n    actions:\n      - {type: replace, command: 'say high'}\n  disabled:\n    enabled: false\n    condition: {type: regex, pattern: '^/smoke$'}\n    actions:\n      - {type: replace, command: 'say disabled'}\n";
   Files.write(rules,yaml.getBytes(StandardCharsets.UTF_8));reload();commands.clear();check(fire("/smoke").isCancelled(),"cancel action");check(commands.size()==2&&commands.get(0).startsWith("say low-SmokeTester-")&&commands.get(1).equals("say high"),"priority, replacements, disabled rules");
   String jsYaml="interceptors:\n  js_basic:\n    condition:\n      type: all\n      rules:\n        - {type: regex, pattern: '^/js-basic$'}\n        - type: script\n          script: |\n            js '1 === 1'\n    actions:\n      - {type: cancel}\n  js_java:\n    condition:\n      type: all\n      rules:\n        - {type: regex, pattern: '^/js-java$'}\n        - type: script\n          script: |\n            js 'var HashMap = Java.type(\"java.util.HashMap\"); var map = new HashMap(); map.put(\"key\", \"value\"); map.size() === 1'\n    actions:\n      - {type: cancel}\n";
   Files.write(rules,jsYaml.getBytes(StandardCharsets.UTF_8));reload();check(fire("/js-basic").isCancelled(),"JavaScript equality condition");check(fire("/js-java").isCancelled(),"JavaScript Java.type HashMap interop");
   Files.write(rules,original);reload();check(fire("/give SmokeTester diamond").isCancelled(),"reload restores live rules");
   Bukkit.getPluginManager().disablePlugin(target);check(!target.isEnabled(),"disable lifecycle");check(!fire("/give SmokeTester diamond").isCancelled(),"disable unregisters event listener");
   getLogger().info("COMMANDANT_SMOKE_PASS");
  } catch(Throwable error){getLogger().log(java.util.logging.Level.SEVERE,"COMMANDANT_SMOKE_FAIL",error);}
  finally{try{if(original!=null)Files.write(rules,original);}catch(Exception error){getLogger().log(java.util.logging.Level.SEVERE,"restore config failed",error);}}
 }
}
