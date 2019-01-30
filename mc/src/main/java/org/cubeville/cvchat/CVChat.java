package org.cubeville.cvchat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Effect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import de.myzelyam.api.vanish.VanishAPI;

import org.cubeville.cvipc.CVIPC;
import org.cubeville.cvipc.IPCInterface;

import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;

public class CVChat extends JavaPlugin implements Listener, IPCInterface
{
    private List<LocalRegion> localRegions;
    private Map<UUID, Set<ProtectedRegion>> worldIDRegionMap;
    private Map<ProtectedRegion, String> regionChatPrefixMap;
    private WorldGuardPlugin worldGuard;
    CVIPC ipc;
    
    @SuppressWarnings("unchecked")
    public void onEnable() {
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(this, this);

        ipc = (CVIPC) pm.getPlugin("CVIPC");
        ipc.registerInterface("locchat", this);
        ipc.registerInterface("chatquery", this);
        
        ConfigurationSerialization.registerClass(LocalRegion.class);
        
        localRegions = (List<LocalRegion>) getConfig().get("LocalRegions");
        if(localRegions == null) { localRegions = new ArrayList<LocalRegion>(); }
        worldIDRegionMap = new HashMap<UUID, Set<ProtectedRegion>>();
        regionChatPrefixMap = new HashMap<ProtectedRegion, String>();
        worldGuard = (WorldGuardPlugin) pm.getPlugin("WorldGuard");
        if(worldGuard == null) { return; }
        for(LocalRegion localRegion: localRegions) {
            World world = Bukkit.getWorld(localRegion.getWorldName());
            if(world == null) { continue; }
            RegionManager regionManager = worldGuard.getRegionManager(world);
            if(regionManager == null) { continue; }
            ProtectedRegion region = regionManager.getRegion(localRegion.getRegionName());
            if(region == null) { continue; }
            if(!worldIDRegionMap.containsKey(world.getUID())) {
                worldIDRegionMap.put(world.getUID(), new HashSet<ProtectedRegion>());
            }
            worldIDRegionMap.get(world.getUID()).add(region);
            regionChatPrefixMap.put(region, localRegion.getChatPrefix().replaceAll("&", "§"));
        }
    }

    public void onDisable() {
        getConfig().set("LocalRegions", localRegions);
        saveConfig();
        ipc.deregisterInterface("locchat");
        ipc.deregisterInterface("chatquery");
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        event.setJoinMessage(null);

        // String highestRank = "default";
        // Player player = event.getPlayer();
        // int prio = 0;
        // Set<String> ranks = getConfig().getConfigurationSection("ranks").getKeys(false);
        // for (String rank : ranks) {
        //     ConfigurationSection rankData = getConfig().getConfigurationSection("ranks").getConfigurationSection(rank);
        //     if (player.hasPermission(rankData.getString("permission")) && rankData.getInt("priority") > prio) {
        //         prio = rankData.getInt("priority");
        //         highestRank = rank;
        //     }
        // }
        // ScoreboardManager manager = Bukkit.getScoreboardManager();
        // Scoreboard mainBoard = manager.getMainScoreboard();
        // System.out.println("Set scoreboard team of player " + player.getName() + " to " + highestRank);
        // if(mainBoard.getTeam(highestRank) != null) {
        //     mainBoard.getTeam(highestRank).addEntry(player.getName().toString());
        // }
        // else {
        //     System.out.println("Team not found on scoreboard!");
        // }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        event.setQuitMessage(null);
    }

    @SuppressWarnings("unchecked")
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if(command.getName().equals("modreq")) {
            if(!(sender instanceof Player)) return false;
            Player player = (Player) sender;
            
            if(args.length == 0) {
                sender.sendMessage("§cEnter /modreq followed by a description what you need help with.");
                return true;
            }
            
            String text = "";
            for(int i = 0; i < args.length; i++) {
                if(i > 0) text += " ";
                text += args[i];
            }

            Location loc = player.getLocation();
            ipc.sendMessage("modreq", player.getUniqueId() + "|" + loc.getWorld().getName() + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ() + "|" + text);
            return true;
        }
        else if(command.getName().equals("unlocktutorialchat")) {
            if(!(sender instanceof Player)) return false;
            Player player = (Player) sender;
            ipc.sendMessage("unlocktutorialchat", player.getUniqueId().toString());
            return true;
        }
        else if(command.getName().equals("ltr")) {
            if(!(sender instanceof Player)) return false;
            Player player = (Player) sender;

            if(args.length == 0) return true;
            Collection<Player> players = (Collection<Player>) getServer().getOnlinePlayers();
            String message = args[0];
            for(int i = 1; i < args.length; i++) message += " " + args[i];
            message = ChatColor.translateAlternateColorCodes('&', message);
            Location loc = player.getLocation();
            for(Player p: players) {
                Location pl = p.getLocation();
                if(pl.getWorld().getUID().equals(loc.getWorld().getUID()) && pl.distance(loc) < 55) {
                    p.sendMessage(message);
                }
            }
            return true;
        }
        else if(command.getName().equals("addlocalregion")) {
            if(!(sender instanceof Player)) {
                sender.sendMessage("ERROR: Only Players can use this command!");
                return false;
            }
            Player player = (Player) sender;
            if(!player.hasPermission("cvchat.local.regionchat")) {
                player.sendMessage("§fUnknown command! Type \"/help\" for help.");
                return false;
            }
            if(args.length <= 2) {
                player.sendMessage("§cSyntax: /addlocalregion <region> <chat prefix...>");
                return true;
            }
            String[] copyOfArgs = args.clone();
            for(int index = 1; index < copyOfArgs.length; index++) {
                copyOfArgs[index] = copyOfArgs[index].replaceAll("&", "§");
            }
            String chatPrefixPrimary = args[1];
            String chatPrefixSecondary = copyOfArgs[1];
            for(int index = 2; index < args.length; index++) {
                chatPrefixPrimary += (" " + args[index]);
                chatPrefixSecondary += (" " + copyOfArgs[index]);
            }
            LocalRegion localRegion = new LocalRegion(player.getWorld().getName(), args[0], chatPrefixPrimary);
            if(worldGuard == null) {
                player.sendMessage("§cERROR: No WorldGuard plugin found!");
                return true;
            }
            World world = player.getWorld();
            RegionManager regionManager = worldGuard.getRegionManager(world);
            if(regionManager == null) {
                player.sendMessage("§cERROR: No RegionManager found for this world!");
                return true;
            }
            ProtectedRegion protectedRegion = regionManager.getRegion(args[0]);
            if(protectedRegion == null) {
                player.sendMessage("§cNo region found!");
                return true;
            }
            if(!worldIDRegionMap.containsKey(world.getUID())) {
                worldIDRegionMap.put(world.getUID(), new HashSet<ProtectedRegion>());
            }
            worldIDRegionMap.get(world.getUID()).add(protectedRegion);
            regionChatPrefixMap.put(protectedRegion, chatPrefixSecondary);
            localRegions.add(localRegion);
            player.sendMessage("§aChat prefix enabled on region!");
            return true;
        }
        else if(command.getName().equals("removelocalregion")) {
            if(!(sender instanceof Player)) {
                sender.sendMessage("ERROR: Only Players can use this command!");
                return false;
            }
            Player player = (Player) sender;
            if(!player.hasPermission("cvchat.local.regionchat")) {
                player.sendMessage("§fUnknown command! Type \"/help\" for help.");
                return false;
            }
            if(args.length != 1) {
                player.sendMessage("§cSyntax: /removelocalregion <region>");
                return true;
            }
            for(int index = localRegions.size() - 1; index >= 0; index--) {
                if(localRegions.get(index).getWorldName().equals(player.getWorld().getName())) {
                    if(localRegions.get(index).getRegionName().equalsIgnoreCase(args[0])) {
                        localRegions.remove(index);
                    }
                }
            }
            if(worldGuard == null) {
                player.sendMessage("§cERROR: No WorldGuard plugin found!");
                return true;
            }
            RegionManager regionManager = worldGuard.getRegionManager(player.getWorld());
            if(regionManager == null) {
                player.sendMessage("§cERROR: No region manager for this world!");
                return true;
            }
            ProtectedRegion protectedRegion = regionManager.getRegion(args[0]);
            if(protectedRegion == null) {
                player.sendMessage("§cNo region found by that name!");
                return true;
            }
            if(regionChatPrefixMap.containsKey(protectedRegion)) {
                regionChatPrefixMap.remove(protectedRegion);
            }
            if(worldIDRegionMap.containsKey(player.getWorld().getUID())) {
                if(worldIDRegionMap.get(player.getWorld().getUID()).contains(protectedRegion)) {
                    worldIDRegionMap.get(player.getWorld().getUID()).remove(protectedRegion);
                }
            }
            player.sendMessage("§aLocal chat removed for region.");
            return true;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    public void process(String channel, String message) {
        if(channel.equals("chatquery")) {
            StringTokenizer tk = new StringTokenizer(message, "|");
            if(tk.countTokens() != 4) return;
            String channelName = tk.nextToken();
            String mId = tk.nextToken();
            UUID playerId = UUID.fromString(tk.nextToken());
            String parameter = tk.nextToken();
            if(!parameter.equals("health")) return;

            Player player = getServer().getPlayer(playerId);
            if(player == null) return;

            double health = 20.0;
            if(player.getGameMode() == GameMode.SURVIVAL) health = player.getHealth();
            ipc.sendMessage("chatquery|" + channelName + "|" + mId + "|" + playerId.toString() + "|health=" + health);
        }
        else if(channel.equals("locchat")) {
            int idx = message.indexOf("|");
            if(idx == -1) return;
            
            String idList = message.substring(0, idx);
            int sidx = idList.indexOf(";");
            UUID chatSenderId;
            Set<UUID> mutedIds = new HashSet<>();
            if(sidx == -1) {
                chatSenderId = UUID.fromString(idList);
            }
            else {
                chatSenderId = UUID.fromString(idList.substring(0, sidx));
                StringTokenizer tk = new StringTokenizer(idList.substring(sidx + 1), ",");
                while(tk.hasMoreTokens()) {
                    mutedIds.add(UUID.fromString(tk.nextToken()));
                }
            }
            message = message.substring(idx + 1);
            
            Player sender = getServer().getPlayer(chatSenderId);
            if(sender == null) return;
            Location senderLocation = sender.getLocation();
            
            ProtectedRegion selectedRegion = null;
            com.sk89q.worldedit.Vector vSenderLocation = new com.sk89q.worldedit.Vector(senderLocation.getX(), senderLocation.getY(), senderLocation.getZ());
            for(ProtectedRegion worldRegion: worldIDRegionMap.get(senderLocation.getWorld().getUID())) {
                if(worldRegion.contains(vSenderLocation)) {
                    selectedRegion = worldRegion;
                    break;
                }
            }
            
            Collection<Player> onlinePlayers = (Collection<Player>) getServer().getOnlinePlayers();
            Set<Player> visible = new HashSet<Player>();
            Set<Player> invis = new HashSet<Player>();
            Set<Player> monitor = new HashSet<Player>();
            for(Player player: onlinePlayers) {
                if(player.getUniqueId().equals(sender.getUniqueId())) { continue; }
                if(!player.getWorld().getUID().equals(sender.getWorld().getUID())) {
                    if(player.hasPermission("cvchat.monitor.local")) {
                        monitor.add(player);
                        continue;
                    }
                }
                Location playerLocation = player.getLocation();
                if(selectedRegion == null) {
                    if(playerLocation.distance(senderLocation) < 55.0D) {
                        if(isVanished(player)) { invis.add(player); }
                        else { visible.add(player); }
                    }
                    else if(player.hasPermission("cvchat.monitor.local")) {
                        monitor.add(player);
                    }
                }
                else {
                    if(selectedRegion.contains(new com.sk89q.worldedit.Vector(playerLocation.getX(), playerLocation.getY(), playerLocation.getZ()))) {
                        if(isVanished(player)) { invis.add(player); }
                        else { visible.add(player); }
                    }
                    else if(player.hasPermission("cvchat.monitor.local")) {
                        monitor.add(player);
                    }
                }
            }
            
            if(selectedRegion == null) {
                sendLocalMessage(visible, invis, monitor, message, "", sender);
            }
            else {
                sendLocalMessage(visible, invis, monitor, message, regionChatPrefixMap.get(selectedRegion) + " ", sender);
            }
            
            int gtidx = message.indexOf(">");
            if(gtidx != -1) {
                if(message.substring(gtidx + 2).equals("fus") && sender.hasPermission("cvchat.thuum.fus")) {
                    fusRoDah(sender, 1);
                }
                else if(message.substring(gtidx + 2).equals("fus ro") && sender.hasPermission("cvchat.thuum.fus.ro")) {
                    fusRoDah(sender, 2);
                }
                else if(message.substring(gtidx + 2).equals("fus ro dah") && sender.hasPermission("cvchat.thuum.fus.ro.dah")) {
                    fusRoDah(sender, 3);
                }
            }
            
        }
    }

    private static String[] colorCodes = {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "a", "b", "c", "d", "e", "f", "k", "l", "m", "n", "o", "r"};
    public static String removeColorCodes(String text) {
        String ret = text;
        for(int i = 0; i < colorCodes.length; i++) {
            ret = ret.replace("§" + colorCodes[i], "");
        }
        return ret;
    }

    private boolean isVanished(Player player) {
        return VanishAPI.isInvisible(player);
    }
    
    private void fusRoDah(Player dragonBorn, int level) {
        System.out.println("Fus ro level " + level);
        final double fusHoriStrength[] = {.5,2,7};
        final double fusVertStrength[] = {.5,.7,1.5};
        
        int distance = 5 * level;
        Vector heading = dragonBorn.getEyeLocation().getDirection();

        Vector blastVector = new Vector();
        blastVector.copy(heading).setY(0).normalize();
        blastVector.multiply(fusHoriStrength[level-1]).setY(fusVertStrength[level-1]);
        for(Entity victim : getAreaOfEffect(dragonBorn, 4, distance)) {
            victim.setVelocity(victim.getVelocity().add(blastVector));
        }

        dragonBorn.getWorld().playEffect(dragonBorn.getLocation(), Effect.GHAST_SHOOT, 0, distance + 10);
        if (level >= 2) {
            World world = dragonBorn.getWorld();
            List<Block> sight = dragonBorn.getLineOfSight(new HashSet<Material>(), 4);
            if (sight.size() >=0 ) world.createExplosion(sight.get(sight.size() - 1).getLocation(),0);
        }

        //if (level == 3){
            //List<Block> sight = dragonBorn.getLineOfSight(new HashSet<Material>(), 32);
            //for(int i = 8; i < 32 && i < sight.size() ; i += 6){
            //  Bukkit.getScheduler().scheduleSyncDelayedTask(this, new Explosion(sight.get(i).getLocation(), 0, false), i/3);
            //}

        //}
    }

    private List<Entity> getAreaOfEffect(Player dragonBorn, int radius, int length){
        Location epicenter = dragonBorn.getEyeLocation();
        Vector heading = epicenter.getDirection();
        List<Entity> returnMe = new LinkedList<Entity>();
        
        length *= 2;
        for(Entity victim : dragonBorn.getNearbyEntities(length, length, length)){
            Vector dragonBornToVictim = victim.getLocation().subtract(epicenter).toVector();
            double dotProduct = dragonBornToVictim.dot(heading);
            
            if(dotProduct < 0) continue; // This entity is behind the dovahkiin
            if(dragonBornToVictim.lengthSquared() - dotProduct * dotProduct > radius*radius) continue; // Entity is too far laterally from the shout.
            
            returnMe.add(victim);
        }
        return returnMe;
    }
    
    private void sendLocalMessage(Set<Player> visible, Set<Player> invis, Set<Player> monitor,
            String message, String prefix, Player sender) {
        
        String monitorMessage = "§7" + removeColorCodes(prefix + message);
        String unheardMessage = prefix + "§8" + removeColorCodes(message);
        
        if(visible.size() == 0) {
            sender.sendMessage(unheardMessage);
            for(Player player: invis) { player.sendMessage(prefix + message + " §4*"); }
            monitorMessage += " §4*";
        }
        else {
            sender.sendMessage(message);
            for(Player player: visible) { player.sendMessage(prefix + message); }
        }
        
        for(Player player: monitor) { player.sendMessage(monitorMessage); }
        ipc.sendMessage("localmonitor|" + monitorMessage);
    }
}
