package org.cubeville.cvchat.tickets;

import java.text.SimpleDateFormat;

import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;

import org.cubeville.cvipc.CVIPC;
import org.cubeville.cvipc.IPCInterface;

import org.cubeville.cvchat.CVChat;
import org.cubeville.cvchat.Util;

public class TicketManager implements IPCInterface
{
    private CVIPC ipc;
    private CVChat plugin;
    
    private List<Ticket> tickets;
    TicketDao dao;

    private Set<String> openTicketPlayerList;
    
    public TicketManager(CVChat plugin, CVIPC ipc, TicketDao dao) {
        this.ipc = ipc;
        this.plugin = plugin;
        ipc.registerInterface("modreq", this);
        this.dao = dao;
        tickets = dao.loadTickets();
        updateOpenTicketPlayerList();
    }
    
    public void process(String serverName, String channel, String message) {
        StringTokenizer tk = new StringTokenizer(message, "|");
        if(tk.countTokens() != 3) return;
        UUID playerId = UUID.fromString(tk.nextToken());
        String location = tk.nextToken();
        String text = tk.nextToken();
        ProxiedPlayer player = ProxyServer.getInstance().getPlayer(playerId);
        if(player == null) return;

        if(player.hasPermission("cvchat.ticket.multiple") == false && playerHasTicket(player.getUniqueId()) == true) {
            player.sendMessage("§6You already have an open mod request. Please wait for it to be completed.");
            return;
        }

        try {
            StringTokenizer loctk = new StringTokenizer(location, ",");
            Ticket ticket = new Ticket(null, playerId, player.getName(), text, serverName,
                                       loctk.nextToken(), Integer.valueOf(loctk.nextToken()), Integer.valueOf(loctk.nextToken()), Integer.valueOf(loctk.nextToken()),
                                       System.currentTimeMillis());
            tickets.add(ticket);
            int ticketId = dao.createTicket(ticket);
            player.sendMessage("§6Thank you. Your message has been sent. A moderator should be with you shortly.");
            sendNotification("§6New mod request #" + ticketId + " filed; use /check for more.");
            updateOpenTicketPlayerList();
        }
        catch(RuntimeException e) {
            player.sendMessage("§cTicket creation failed. Please contact an administrator.");
        }
    }

    public void playerLogin(ProxiedPlayer player) {
        UUID playerId = player.getUniqueId();
        for(Ticket ticket: tickets) {
            if(ticket.isClosed() == true && ticket.playerNotified() == false && ticket.getPlayer().equals(playerId)) {
                ticket.setPlayerNotified(true);
                updateTicketAsync(ticket);
                final String moderatorName = ticket.getModeratorName();
                final String ticketText = ticket.getText();
                final String moderatorText = ticket.getModeratorText();
                final long ticketId = ticket.getId();
                ProxyServer.getInstance().getScheduler().schedule(plugin, new Runnable() {
                        public void run() {
                            sendPlayerNotification(playerId, "§6" + moderatorName + "§6 has completed your request while you were offline:");
                            sendPlayerNotification(playerId, "§6Request - §7" + ticketText);
                            sendPlayerNotification(playerId, "§6Mod comment - §7" + moderatorText);
                        }
                    }, 5, TimeUnit.SECONDS );
            }
        }
    }

    private boolean playerHasTicket(UUID playerId) {
        for(Ticket ticket: tickets) {
            if(ticket.isClosed() == false && ticket.isHeld() == false && ticket.getPlayer().equals(playerId)) {
                return true;
            }
        }
        return false;
        
    }
    
    private void sendNotification(String text) {
        for(ProxiedPlayer p: ProxyServer.getInstance().getPlayers()) {
            if(p.hasPermission("cvchat.ticket.notify")) {
                p.sendMessage(text);
            }
        }
    }

    private boolean sendPlayerNotification(UUID playerId, String text) {
        ProxiedPlayer player = ProxyServer.getInstance().getPlayer(playerId);
        if(player != null) {
            player.sendMessage(text);
            return true;
        }
        return false;
    }

    public String getDateStr(long timestamp) {
        SimpleDateFormat df = new SimpleDateFormat("MMM.dd@HH:mm:ss");
        GregorianCalendar c = new GregorianCalendar();
        c.setTimeInMillis(timestamp);
        return df.format(c.getTime());
    }

    public int getNumberOfOpenTickets() {
        int cnt = 0;
        for(Ticket ticket: tickets) {
            if(ticket.isClosed() == false && ticket.isHeld() == false) {
                cnt++;
            }
        }
        return cnt;
    }
    
    public void checkTickets(CommandSender sender, boolean held, boolean closed, int page) {
        // TODO: Need to async this?
        int cnt = -1;
        int pageSize = 5;
        List<String> out = new ArrayList<>();
        for(Ticket ticket: tickets) {
            if(ticket.isClosed() == closed && ticket.isHeld() == held) {
                cnt++;
                if(cnt / pageSize + 1 == page) {
                    String text;
                    if(ticket.isClaimed()) {
                        text = "§dClaimed by " + ticket.getModeratorName();
                    }
                    else {
                        text = "§7" + ticket.getText();
                    }
                    if(text.length() > 22) { text = text.substring(0, 22) + "§7..."; }
                    boolean playerOnline = false;
                    if(ProxyServer.getInstance().getPlayer(ticket.getPlayer()) != null) {
                        if(!Util.playerIsHidden(ticket.getPlayer())) {
                            playerOnline = true;
                        }
                    }
                    out.add("§6#" + ticket.getId() + ". " + getDateStr(ticket.getCreationTimestamp()) + " by §" + (playerOnline ? "a" : "c") + ticket.getPlayerName() + " §7- " + text);
                }
            }
        }
        if(cnt == -1) {
            String msg = "No open open modreqs. Also there are no closed open and no open closed modreqs.";
            if(held) msg = "No held modreqs.";
            if(closed) msg = "No closed modreqs.";
            sender.sendMessage(msg);
            return;
        }
        int from = page * 5 - 4;
        int to = Math.min(page * 5, cnt + 1);
        sender.sendMessage("--------- " + ++cnt + " " + (held ? "held" : (closed ? "closed" : "open")) + " modreqs, showing " + from + "-" + to + " ---------");
        for(String s: out) {
            sender.sendMessage(s);
        }
    }

    public void showTicketDetail(CommandSender sender, int id) {
        Ticket ticket = getTicketById(id);
        if(ticket == null) {
            sender.sendMessage("§cInvalid ticket id.");
            return;
        }

        {
            String hl = "§b--------- Mod Request #" + id + "- ";
            if(ticket.isClosed()) hl += "§aClosed";
            else if(ticket.isClaimed()) hl += "§cClaimed";
            else if(ticket.isHeld()) hl += "§dOn Hold";
            else hl += "§eOpen";
            hl += "§b ---------";
            sender.sendMessage(hl);
        }

        sender.sendMessage("§eFiled by §c" + ticket.getPlayerName() + "§e at " + getDateStr(ticket.getCreationTimestamp()) + "§e at " + ticket.getServer() + "," + ticket.getWorld() + "," + ticket.getX() + "," + ticket.getY() + "," + ticket.getZ());
        if(!ticket.isClosed() && ticket.isClaimed()) {
            sender.sendMessage("§eClaimed by §d" + ticket.getModeratorName() + "§e at §d" + getDateStr(ticket.getModeratorTimestamp()));
            sender.sendMessage("§7" + ticket.getText());
        }
        else if(ticket.isClosed()) {
            sender.sendMessage("§eHandled by §d" + ticket.getModeratorName() + "§e at §d" + getDateStr(ticket.getModeratorTimestamp()));
            sender.sendMessage("§7" + ticket.getText());
            sender.sendMessage("§6Mod comment - §7" + ticket.getModeratorText());
        }
    }
    
    private Ticket getTicketById(int id) {
        for(Ticket ticket: tickets) {
            if(id == ticket.getId()) return ticket;
        }
        return null;
    }
    
    public void claimTicket(CommandSender sender, int ticketId) {
        if(!(sender instanceof ProxiedPlayer)) return;
        ProxiedPlayer player = (ProxiedPlayer) sender;

        Ticket ticket = getTicketById(ticketId);
        if(ticket == null) {
            player.sendMessage("§cInvalid ticket id.");
            return;
        }

        if(ticket.isClosed()) {
            player.sendMessage("§cCan't claim a closed ticket.");
            return;
        }

        if(ticket.isClaimed()) {
            player.sendMessage("§cTicket is already claimed.");
            return;
        }

        ticket.setClaimed(true);
        ticket.setModerator(player.getUniqueId());
        ticket.setModeratorName(player.getName());
        ticket.setModeratorTimestamp(System.currentTimeMillis());
        updateTicketAsync(ticket);

        sendNotification("§6" + player.getName() + "§6 calls dibs on request #" + ticket.getId() + ".");
        
        if(Util.playerIsHidden(player)) {
            sendPlayerNotification(ticket.getPlayer(), "§6Your request is being handled.");
        }
        else {
            sendPlayerNotification(ticket.getPlayer(), "§6" + player.getName() + "§6 is now handling your request.");
        }
    }

    public void closeTicket(CommandSender sender, int ticketId, String text) {
        if(!(sender instanceof ProxiedPlayer)) return;
        ProxiedPlayer player = (ProxiedPlayer) sender;

        Ticket ticket = getTicketById(ticketId);
        if(ticket == null) {
            player.sendMessage("§cInvalid ticket id.");
            return;
        }

        if(ticket.isClosed()) {
            player.sendMessage("§cTicket is already closed.");
            return;
        }

        ticket.setClosed(true);
        ticket.setModeratorText(text);
        ticket.setModerator(player.getUniqueId());
        ticket.setModeratorName(player.getName()); // TODO: Get current name from playerdata module (not just here, everywhere)
        ticket.setModeratorTimestamp(System.currentTimeMillis());

        sendNotification("§6Request #" + ticket.getId() + " has been completed.");
        sendNotification("§6Mod comment - §7" + text);
        
        if(Util.playerIsHidden(player)) {
            sendPlayerNotification(ticket.getPlayer(), "§6Your request has been completed.");
        }
        else {
            sendPlayerNotification(ticket.getPlayer(), "§6" + player.getName() + "§6 has completed your request.");
        }
        
        if(sendPlayerNotification(ticket.getPlayer(), "§6Mod comment - §7" + text)) {
            ticket.setPlayerNotified(true);
        }

        updateOpenTicketPlayerList();        
        updateTicketAsync(ticket);
    }

    public void reopenTicket(CommandSender sender, int ticketId) {
        if(!(sender instanceof ProxiedPlayer)) return;
        ProxiedPlayer player = (ProxiedPlayer) sender;

        Ticket ticket = getTicketById(ticketId);
        if(ticket == null) {
            player.sendMessage("§cInvalid ticket id.");
            return;
        }

        if(!ticket.isClosed() && !ticket.isClaimed()) {
            player.sendMessage("§cTicket is not closed.");
            return;
        }

        ticket.setClosed(false);
        ticket.setClaimed(false);
        ticket.setHeld(false);
        ticket.setModerator(player.getUniqueId());
        ticket.setModeratorName(player.getName());
        ticket.setModeratorTimestamp(System.currentTimeMillis());
        ticket.setPlayerNotified(false);

        updateOpenTicketPlayerList();
        updateTicketAsync(ticket);

        sendNotification("§6Request #" + ticket.getId() + " has been reopened.");
    }
    
    public void unclaimTicket(CommandSender sender, int ticketId) {
        if(!(sender instanceof ProxiedPlayer)) return;
        ProxiedPlayer player = (ProxiedPlayer) sender;

        Ticket ticket = getTicketById(ticketId);
        if(ticket == null) {
            player.sendMessage("§cInvalid ticket id.");
            return;
        }

        if(ticket.isClosed()) {
            player.sendMessage("§cTicket is closed.");
            return;
        }

        if(!ticket.isClaimed()) {
            player.sendMessage("§cTicket is not claimed.");
            return;
        }

        ticket.setClaimed(false);
        ticket.setModerator(player.getUniqueId());
        ticket.setModeratorName(player.getName());
        ticket.setModeratorTimestamp(System.currentTimeMillis());
        updateTicketAsync(ticket);

        sendNotification("§6Request #" + ticket.getId() + " is no longer assigned.");
        
        if(Util.playerIsHidden(player)) {
            sendPlayerNotification(ticket.getPlayer(), "§6Your request is no longer being handled.");
        }
        else {
            sendPlayerNotification(ticket.getPlayer(), "§6" + player.getName() + "§6 is no longer handling your request. Please wait for another mod.");
        }
    }

    public void holdTicket(CommandSender sender, int ticketId) {
        if(!(sender instanceof ProxiedPlayer)) return;
        ProxiedPlayer player = (ProxiedPlayer) sender;

        Ticket ticket = getTicketById(ticketId);
        if(ticket == null) {
            player.sendMessage("§cInvalid ticket id.");
            return;
        }

        if(ticket.isClosed()) {
            player.sendMessage("§cClosed tickets can't be held.");
            return;
        }

        if(ticket.isHeld()) {
            player.sendMessage("§cTicket is already held.");
            return;
        }

        ticket.setHeld(true);
        ticket.setClaimed(false);
        ticket.setModerator(player.getUniqueId());
        ticket.setModeratorName(player.getName());
        ticket.setModeratorTimestamp(System.currentTimeMillis());
        updateOpenTicketPlayerList();        
        updateTicketAsync(ticket);

        sendNotification("§6Request #" + ticket.getId() + " is now on hold.");
    }
    
    public void unholdTicket(CommandSender sender, int ticketId) {
        if(!(sender instanceof ProxiedPlayer)) return;
        ProxiedPlayer player = (ProxiedPlayer) sender;
        
        Ticket ticket = getTicketById(ticketId);
        if(ticket == null) {
            player.sendMessage("§cInvalid ticket id.");
            return;
        }
        
        if(ticket.isClosed()) {
          player.sendMessage("§cTicket is closed, cannot hold");
          return;
        }
        
        if(!(ticket.isHeld())) {
          player.sendMessage("§cTicket is not held.");
          return;
        }
        
        ticket.setHeld(false);
        ticket.setClaimed(false);
        ticket.setModerator(player.getUniqueId());
        ticket.setModeratorName(player.getName());
        ticket.setModeratorTimestamp(System.currentTimeMillis());
        updateOpenTicketPlayerList();
        updateTicketAsync(ticket);
        
        sendNotification("§6Request #" + ticket.getId() + " removed from the hold list.");
    }
    
    public void tpid(CommandSender sender, int ticketId) {
        if(!(sender instanceof ProxiedPlayer)) return;
        ProxiedPlayer player = (ProxiedPlayer) sender;

        Ticket ticket = getTicketById(ticketId);
        if(ticket == null) {
            player.sendMessage("§cInvalid ticket id.");
            return;
        }

        String sourceServer = player.getServer().getInfo().getName();
        String targetServer = ticket.getServer();

        if(sourceServer.equals(targetServer)) {
            ipc.sendMessage(targetServer, "tplocal|" + player.getUniqueId() + "|coord:" + ticket.getWorld() + "," + ticket.getX() + "," + ticket.getY() + "," + ticket.getZ());
        }
        else {
            ipc.sendMessage(targetServer, "xwportal|" + player.getUniqueId() + "|coord:" + ticket.getWorld() + "," + ticket.getX() + "," + ticket.getY() + "," + ticket.getZ() + "|" + targetServer);
        }
    }

    public void updateTicketAsync(Ticket ticket) {
        ProxyServer.getInstance().getScheduler().runAsync(plugin, new Runnable() {
                public void run() {
                    dao.updateTicket(ticket);
                }
            });
    }

    private void updateOpenTicketPlayerList() {
        openTicketPlayerList = new HashSet<>();
        for(Ticket ticket: tickets) {
            if(ticket.isClosed() == false && ticket.isHeld() == false) {
                openTicketPlayerList.add(ticket.getPlayerName());
            }
        }
    }

    public Set<String> getOpenTicketPlayerList() {
        return new HashSet<String>(openTicketPlayerList);
    }
}
