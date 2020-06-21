package org.cubeville.cvchat;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;

import codecrafter47.bungeetablistplus.api.bungee.BungeeTabListPlusAPI;

public class Util
{
    public static String joinStrings(String[] parts, int offset) {
        String ret = "";
        for(int i = offset; i < parts.length; i++) {
            if(i > offset) ret += " ";
            ret += parts[i];
        }
        return ret;
    }

    public static String[] removeEmptyStrings(String[] args) {
        int nr = 0;
        for(int i = 0; i < args.length; i++) {
            if(args[i].length() > 0) nr++;
        }
        String ret[] = new String[nr];
        int c = 0;
        for(int i = 0; i < nr; i++) {
            while(args[c].length() == 0) {
                c++;
            }
            ret[i] = args[c];
            c++;
        }
        return ret;
    }
    
    public static String removeSectionSigns(String text) {
        text.replace("§", "");
        return text;
    }

    public static boolean getBooleanProperty(String text) {
        if(text.indexOf(':') == -1) return false;
        String s = text.substring(text.indexOf(':') + 1);
        s = s.trim();
        if(s.equals("true")) return true;
        return false;
    }

    public static String getStringProperty(String text) {
        if(text.indexOf(':') == -1) return null;
        String s = text.substring(text.indexOf(':') + 1);
        return s.trim();
    }

    public static String getPropertyName(String text) {
        if(text.indexOf(":") == -1) return null;
        return text.substring(0, text.indexOf(':')).trim();
    }

    public static void saveFile(File file, List<String> text) {
        FileWriter fileWriter = null;
        try {
            fileWriter = new FileWriter(file);
            BufferedWriter writer = new BufferedWriter(fileWriter);
            for(String s: text) {
                writer.write(s);
                writer.newLine();
            }
            writer.close();
        }
        catch(IOException exceptin) {}
        finally {
            try { fileWriter.close(); } catch (Exception e) {}
        }
    }

    public static List<String> readFile(File file) {
        if(!file.exists()) return null;
        List<String> ret = new ArrayList<>();
        FileReader fileReader = null;
        try {
            fileReader = new FileReader(file);
            BufferedReader reader = new BufferedReader(fileReader);
            while(true) {
                String l = reader.readLine();
                if(l == null) break;
                ret.add(l);
            }
            reader.close();
        }
        catch(IOException exceptin) {}
        finally {
            try { fileReader.close(); } catch (Exception e) {}
        }
        return ret;
    }

    private static String[] colorCodes = {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "a", "b", "c", "d", "e", "f", "k", "l", "m", "n", "o", "r"};
    
    public static String translateAlternateColorCodes(String text) {
        String ret = text;
        for(int i = 0; i < colorCodes.length; i++) {
            ret = ret.replace("&" + colorCodes[i], "§" + colorCodes[i]);
        }
        return ret;
    }

    public static String removeColorCodes(String text) {
        String ret = text;
        for(int i = 0; i < colorCodes.length; i++) {
            ret = ret.replace("§" + colorCodes[i], "");
        }
        return ret;
    }
    
    public static boolean playerIsHidden(ProxiedPlayer player) {
        return BungeeTabListPlusAPI.isHidden(player);
    }

    public static boolean playerIsHidden(UUID playerId) {
        ProxiedPlayer player = ProxyServer.getInstance().getPlayer(playerId);
        if(player == null) return false;
        return playerIsHidden(player);
    }
    
    public static List<ProxiedPlayer> getPlayersWithPermission(String permission) {
        List<ProxiedPlayer> ret = new ArrayList<>();
        for(ProxiedPlayer player: ProxyServer.getInstance().getPlayers()) {
            if(player.hasPermission(permission)) {
                ret.add(player);
            }
        }
        return ret;
    }
}
