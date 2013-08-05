package com.minecraftdimensions.bungeesuite.managers;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;

import com.minecraftdimensions.bungeesuite.BungeeSuite;
import com.minecraftdimensions.bungeesuite.configs.ChatConfig;
import com.minecraftdimensions.bungeesuite.configs.MainConfig;
import com.minecraftdimensions.bungeesuite.objects.BSPlayer;
import com.minecraftdimensions.bungeesuite.objects.Messages;

public class PlayerManager {

	private static HashMap<String, BSPlayer> onlinePlayers = new HashMap<String, BSPlayer>();
	static ProxyServer proxy = ProxyServer.getInstance();
	static BungeeSuite plugin = BungeeSuite.instance;

	public static boolean playerExists(String player) {
		if(getSimilarPlayer(player)!=null){
			return true;
		}
		return SQLManager.existanceQuery("SELECT playername FROM BungeePlayers WHERE playername = '"+player+"'");
	}

	public static void loadPlayer(ProxiedPlayer player) throws SQLException {
		String nickname = null;
		String channel = null;
		boolean muted = false;
		boolean chatspying = false;
		boolean dnd = false;
		boolean tps = true;
		
		if(playerExists(player.getName())){
		ResultSet res = SQLManager
				.sqlQuery("SELECT playername,nickname,channel,muted,chat_spying,dnd,tps FROM BungeePlayers WHERE playername = '"
						+ player + "'");
		while (res.next()) {
			nickname = res.getString("nickname");
			channel = res.getString("channel");
			muted = res.getBoolean("muted");
			chatspying = res.getBoolean("chat_spying");
			dnd = res.getBoolean("dnd");
			tps = res.getBoolean("tps");
		}
		res.close();
		BSPlayer bsplayer = new BSPlayer(player.getName(), nickname, channel, muted, chatspying, dnd, tps);
			addPlayer(bsplayer);
			IgnoresManager.LoadPlayersIgnores(bsplayer);
		} else {
			createNewPlayer(player);
		}
	}

	private static void createNewPlayer(ProxiedPlayer player) throws SQLException {
		String ip = player.getAddress().getAddress().toString();
		SQLManager.standardQuery("INSERT INTO BungeePlayers (playername,lastonline,ipaddress,channel) VALUES ('"
				+ player.getName() + "', NOW(), '" + ip.substring(1, ip.length()) + "','"+ChatConfig.defaultChannel+"')");
		BSPlayer bsplayer = new BSPlayer(player.getName(),null,ChatConfig.defaultChannel,false,false,false,true);
		if (MainConfig.newPlayerBroadcast) {
			sendBroadcast(Messages.NEW_PLAYER_BROADCAST.replace("{player}",
					player.getName()));
		}
		addPlayer(bsplayer);
		
	}

	private static void addPlayer(BSPlayer player){
		onlinePlayers.put(player.getName(), player);
		LoggingManager.log(Messages.PLAYER_LOAD.replace("{player}", player.getName()));
	}
	
	public static void unloadPlayer(String player) {
		onlinePlayers.remove(player);
		LoggingManager.log(Messages.PLAYER_UNLOAD.replace("{player}", player));
	}

	public static BSPlayer getPlayer(String player) {
		return onlinePlayers.get(player);
	}
	
	public static BSPlayer getSimilarPlayer(String player){
		if(onlinePlayers.containsKey(player)){
			return onlinePlayers.get(player);
		}
		for(String p: onlinePlayers.keySet()){
			if(p.toLowerCase().contains(player.toLowerCase())){
				return onlinePlayers.get(p);
			}
		}
		return null;
	}
	
	public static void sendPrivateMessageToPlayer(BSPlayer from, String receiver, String message){
		BSPlayer rec = getPlayer(receiver);
		if(rec==null){
			from.sendMessage(Messages.PLAYER_NOT_ONLINE);
			return;
		}
		from.sendMessage(Messages.PRIVATE_MESSAGE_OTHER_PLAYER.replace("{player}", rec.getName()).replace("{message}", message));
		rec.sendMessage(Messages.PRIVATE_MESSAGE_RECEIVE.replace("{player}", from.getName()).replace("{message}", message));
		rec.setReplyPlayer(from.getName());
		sendPrivateMessageToSpies(from, rec, message);
	}
	
	public static void sendMessageToPlayer(String player, String message){
		if(player.equals("CONSOLE")){
			ProxyServer.getInstance().getConsole().sendMessage(message);
		}else{
			getPlayer(player).sendMessage(message);
		}
	}

	public static String getPlayersIP(String player) throws SQLException{
		BSPlayer p = getSimilarPlayer(player);
		String ip = null;
		if(p==null){
			ResultSet res = SQLManager.sqlQuery("SELECT ipaddress FROM BungeePlayers WHERE player = '"+player+"'");
			while(res.next()){
				ip = res.getString("ipaddress");
			}
			res.close();
		}else{
			ip = p.getProxiedPlayer().getAddress().getAddress().toString();
		}
		return ip;
	}
	
	public static void sendBroadcast(String message) {
		for (ProxiedPlayer p : proxy.getPlayers()) {
			for (String line : message.split("\n")) {
				p.sendMessage(line);
			}
		}
		LoggingManager.log(message);
	}
	
	public static boolean isPlayerOnline(String player){
		return onlinePlayers.containsKey(player);
	}
	public static boolean isSimilarPlayerOnline(String player){
		return getSimilarPlayer(player)!=null;
	}
	
	public static ArrayList<String> getPlayersAltAccounts(String player) throws SQLException{
		ArrayList<String>accounts = new ArrayList<String>();
		ResultSet res =SQLManager.sqlQuery("SELECT playername from BungeePlayers WHERE ipaddress = (SELECT ipaddress FROM BungeePlayers WHERE playername = '"+player+"')");
		while(res.next()){
			accounts.add(res.getString("playername"));
		}
		return accounts;
	}
	public static ArrayList<String> getPlayersAltAccountsByIP(String ip) throws SQLException{
		ArrayList<String>accounts = new ArrayList<String>();
		ResultSet res =SQLManager.sqlQuery("SELECT playername from BungeePlayers WHERE ipaddress = '"+ip+"'");
		while(res.next()){
			accounts.add(res.getString("playername"));
		}
		res.close();
		return accounts;
	}

	public static BSPlayer getPlayer(CommandSender sender) {
			return onlinePlayers.get(sender.getName());
	}

	public static void setPlayerAFK(String player, boolean isAFK,
			boolean sendGlobal, boolean hasDisplayPerm) {
		BSPlayer p = getPlayer(player);
		if(!p.isAFK()){
			p.setAFK(true);
			if(sendGlobal){
				sendBroadcast(Messages.PLAYER_AFK.replace("{player}", p.getDisplayingName()));
			}else{
				sendServerMessage(p.getServer(),Messages.PLAYER_AFK.replace("{player}", p.getDisplayingName()));
			}
			if(hasDisplayPerm){
				p.setDisplayingName(Messages.AFK_DISPLAY+p.getDisplayingName());
			}
		}else{
			p.setAFK(false);
			if(hasDisplayPerm){
				p.setDisplayingName(p.getDisplayingName().substring(Messages.AFK_DISPLAY.length(),p.getDisplayingName().length()));
			}
			if(sendGlobal){
				sendBroadcast(Messages.PLAYER_NOT_AFK.replace("{player}", p.getDisplayingName()));
			}else{
				sendServerMessage(p.getServer(),Messages.PLAYER_NOT_AFK.replace("{player}", p.getDisplayingName()));
			}
		}
		
	}

	private static void sendServerMessage(Server server, String message) {
		for(ProxiedPlayer p: server.getInfo().getPlayers()){
			for (String line : message.split("\n")) {
				p.sendMessage(line);
			}
		}
	}
	
	public static ArrayList<BSPlayer> getChatSpies(){
		ArrayList<BSPlayer>spies = new ArrayList<BSPlayer>();
		for(BSPlayer p: onlinePlayers.values()){
			if(p.isChatSpying()){
				spies.add(p);
			}
		}
		return spies;
	}
	
	public static void sendPrivateMessageToSpies(BSPlayer sender,BSPlayer receiver, String message) {
		for(BSPlayer p: getChatSpies()){
			if(!(p.equals(sender)||p.equals(receiver))){
				p.sendMessage(Messages.PRIVATE_MESSAGE_SPY.replace("{sender}", sender.getName()).replace("{player}", receiver.getName()));
			}
		}
	}

	public static void sendMessageToSpies(Server server, String message) {
		for(BSPlayer p: getChatSpies()){
			if(!p.getServer().equals(server)){
				p.sendMessage(message);
			}
		}
	}

	public static void setPlayerChatSpy(BSPlayer p) {
		if(p.isChatSpying()){
			p.setChatSpying(false);
			p.sendMessage(Messages.CHATSPY_DISABLED);
		}else{
			p.setChatSpying(true);
			p.sendMessage(Messages.CHATSPY_ENABLED);
		}
		
	}

	public static boolean nickNameExists(String nick) {
		return SQLManager.existanceQuery("SELECT nickname FROM BungeePlayers WHERE nickname ='"+nick+"'");
	}
	
	public static void setPlayersNickname(String p, String nick) throws SQLException{
		if(isPlayerOnline(p)){
			getPlayer(p).setNickname(nick);
			getPlayer(p).updateDisplayName();
		}
		if(nick==null){
		SQLManager.standardQuery("UPDATE BungeePlayers nickname =NULL WHERE playername ='"+p+"'");
		}else{
			SQLManager.standardQuery("UPDATE BungeePlayers nickname ='"+nick+"' WHERE playername ='"+p+"'");
		}
	}

	public static boolean isPlayerMuted(String target) {
	if(getPlayer(target)!=null){
		return getPlayer(target).isMuted();
	}else{
		return SQLManager.existanceQuery("SELECT muted FROM BungeePlayers WHERE playername ='"+target+"' AND muted = 1");
	}
		
	}

	public static void mutePlayer(String target) throws SQLException {
		BSPlayer p = getSimilarPlayer(target);
		if(p!=null){
			if(p.isMuted()){
				p.setMute(false);
				p.sendMessage(Messages.UNMUTED);
			}else{
				p.setMute(true);
				p.sendMessage(Messages.MUTED);
			}
		}
		boolean isMuted = isPlayerMuted(target);
		SQLManager.standardQuery("UPDATE BungeePlayers muted = "+isMuted+" WHERE playername ='"+target+"'");
		
	}

	public static void tempMutePlayer(final BSPlayer t, int minutes) throws SQLException {
		mutePlayer(t.getName());
		BungeeSuite.proxy.getScheduler().schedule(plugin, new Runnable(){
			@Override
			public void run() {
				if(t.isMuted()){
					try {
						mutePlayer(t.getName());
					} catch (SQLException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				
			}		
		}, minutes, TimeUnit.MINUTES);
	}

	public static void getPlayerInformation(CommandSender arg0, String string) {
	
	}

	public static boolean playerUsingNickname(String string) {
		return SQLManager.existanceQuery("SELECT playername FROM BungeePlayers WHERE nickname LIKE '%"+string+"%'");
	}
}
