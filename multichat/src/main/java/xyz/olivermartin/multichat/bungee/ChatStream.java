package xyz.olivermartin.multichat.bungee;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import xyz.olivermartin.multichat.bungee.events.PostBroadcastEvent;
import xyz.olivermartin.multichat.bungee.events.PostGlobalChatEvent;

/**
 * Chat Stream
 * <p>A class to represent a chat stream and control the messages sent etc.</p>
 * 
 * @author Oliver Martin (Revilo410)
 *
 */
public class ChatStream {

	boolean whitelistMembers;
	protected List<UUID> members;

	boolean whitelistServers;
	protected List<String> servers;

	protected String name;
	protected String format;

	public static Map<UUID,ChatStream> currentStreams = new HashMap<UUID,ChatStream>();

	public static void setStream (UUID uuid,ChatStream stream) {
		ChatStream.currentStreams.put(uuid,stream);
	}

	public static ChatStream getStream (UUID uuid) {
		return ChatStream.currentStreams.get(uuid);
	}

	public static void removePlayer (UUID uuid) {
		ChatStream.currentStreams.remove(uuid);
	}

	public ChatStream(String name,  String format, boolean whitelistServers, boolean whitelistMembers) {

		this.name = name;
		this.whitelistServers = whitelistServers;
		this.format = format;
		this.servers = new ArrayList<String>();
		this.members = new ArrayList<UUID>();
		this.whitelistMembers = whitelistMembers;

	}

	public void addServer(String server) {

		if (!servers.contains(server)) {
			servers.add(server);
		}

	}

	public void addMember(UUID member) {

		if (!members.contains(member)) {
			members.add(member);
		}

	}

	public String getName() {
		return this.name;
	}

	public String getFormat() {
		return this.format;
	}

	public void sendMessage(ProxiedPlayer sender, String message, String format, boolean local, String playerList) {

		Set<String> players = new HashSet<String>();

		for (ProxiedPlayer receiver : ProxyServer.getInstance().getPlayers()) {

			if (receiver != null) {

				synchronized (receiver) {

					if ( (whitelistMembers && members.contains(receiver.getUniqueId())) || (!whitelistMembers && !members.contains(receiver.getUniqueId()))) {
						if ( (whitelistServers && servers.contains(receiver.getServer().getInfo().getName())) || (!whitelistServers && !servers.contains(receiver.getServer().getInfo().getName()))) {
							//TODO hiding & showing streams
							if ( (MultiChat.globalplayers.get(sender.getUniqueId()) == false
									&& sender.getServer().getInfo().getName().equals(receiver.getServer().getInfo().getName())) ||
									(MultiChat.globalplayers.get(receiver.getUniqueId()) == false
									&& sender.getServer().getInfo().getName().equals(receiver.getServer().getInfo().getName())) ||
									(MultiChat.globalplayers.get(sender.getUniqueId()).equals(true) && MultiChat.globalplayers.get(receiver.getUniqueId()))) {

								if (!ChatControl.ignores(sender.getUniqueId(), receiver.getUniqueId(), "global_chat")) {
									if (!receiver.getServer().getInfo().getName().equals(sender.getServer().getInfo().getName())) {
										if (!local) receiver.sendMessage(buildFormat(sender,receiver,format,message));
									} else {
										players.add(receiver.getName());
									}
								} else {
									ChatControl.sendIgnoreNotifications(receiver, sender, "global_chat");
								}

							}
						}

					}

				}

			}
		}

		String playerString = "";

		for (String p : players) {
			if (playerString.equals("")) {
				playerString = playerString + p;
			} else {
				playerString = playerString + " " + p;
			}
		}

		if (local) playerString = playerList;

		String newFormat = buildSpigotFormat(sender,format,message);
		BungeeComm.sendChatMessage(
				sender.getName(),
				newFormat,
				message, 
				(sender.hasPermission("multichat.chat.color") || sender.hasPermission("multichat.chat.colour")),
				playerString,
				sender.getServer().getInfo()
				);

		// Trigger PostGlobalChatEvent
		if (!local) ProxyServer.getInstance().getPluginManager().callEvent(new PostGlobalChatEvent(sender, message, format));

		sendToConsole(sender,format,message);

	}

	public void sendMessage(String message) {

		for (ProxiedPlayer receiver : ProxyServer.getInstance().getPlayers()) {
			if ( (whitelistMembers && members.contains(receiver.getUniqueId())) || (!whitelistMembers && !members.contains(receiver.getUniqueId()))) {
				if ( (whitelistServers && servers.contains(receiver.getServer().getInfo().getName())) || (!whitelistServers && !servers.contains(receiver.getServer().getInfo().getName()))) {
					//TODO hiding & showing streams

					receiver.sendMessage(TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', message)));


				}
			}
		}

		// Trigger PostBroadcastEvent
		ProxyServer.getInstance().getPluginManager().callEvent(new PostBroadcastEvent("cast", message));

		ConsoleManager.logDisplayMessage(message);

	}

	public String buildSpigotFormat(ProxiedPlayer sender, String format, String message) {

		String newFormat = format;

		newFormat = newFormat.replace("%DISPLAYNAME%", sender.getDisplayName());
		newFormat = newFormat.replace("%NAME%", sender.getName());

		Optional<PlayerMeta> opm = PlayerMetaManager.getInstance().getPlayer(sender.getUniqueId());
		if (opm.isPresent()) {
			newFormat = newFormat.replace("%PREFIX%", opm.get().prefix);
			newFormat = newFormat.replace("%SUFFIX%", opm.get().suffix);
			newFormat = newFormat.replace("%NICK%", opm.get().nick);
			newFormat = newFormat.replace("%WORLD%", opm.get().world);
		}

		newFormat = newFormat.replace("%SERVER%", sender.getServer().getInfo().getName());


		if (MultiChat.globalplayers.get(sender.getUniqueId()).equals(false)) {
			newFormat = newFormat.replace("%MODE%", "Local");
			newFormat = newFormat.replace("%M%", "L");
		}

		if (MultiChat.globalplayers.get(sender.getUniqueId()).equals(true)) {
			newFormat = newFormat.replace("%MODE%", "Global");
			newFormat = newFormat.replace("%M%", "G");
		}

		newFormat = newFormat + "%MESSAGE%";

		return newFormat;

	}

	public BaseComponent[] buildFormat(ProxiedPlayer sender, ProxiedPlayer receiver, String format, String message) {

		String newFormat = format;

		newFormat = newFormat.replace("%DISPLAYNAME%", sender.getDisplayName());
		newFormat = newFormat.replace("%NAME%", sender.getName());

		Optional<PlayerMeta> opm = PlayerMetaManager.getInstance().getPlayer(sender.getUniqueId());
		if (opm.isPresent()) {
			newFormat = newFormat.replace("%PREFIX%", opm.get().prefix);
			newFormat = newFormat.replace("%SUFFIX%", opm.get().suffix);
			newFormat = newFormat.replace("%NICK%", opm.get().nick);
			newFormat = newFormat.replace("%WORLD%", opm.get().world);
		}

		newFormat = newFormat.replace("%DISPLAYNAMET%", receiver.getDisplayName());
		newFormat = newFormat.replace("%NAMET%", receiver.getName());

		Optional<PlayerMeta> opmt = PlayerMetaManager.getInstance().getPlayer(receiver.getUniqueId());
		if (opmt.isPresent()) {
			newFormat = newFormat.replace("%PREFIXT%", opmt.get().prefix);
			newFormat = newFormat.replace("%SUFFIXT%", opmt.get().suffix);
			newFormat = newFormat.replace("%NICKT%", opmt.get().nick);
			newFormat = newFormat.replace("%WORLDT%", opmt.get().world);
		}

		newFormat = newFormat.replace("%SERVER%", sender.getServer().getInfo().getName());
		newFormat = newFormat.replace("%SERVERT%", receiver.getServer().getInfo().getName());


		if (MultiChat.globalplayers.get(sender.getUniqueId()).equals(false)) {
			newFormat = newFormat.replace("%MODE%", "Local");
			newFormat = newFormat.replace("%M%", "L");
		}

		if (MultiChat.globalplayers.get(sender.getUniqueId()).equals(true)) {
			newFormat = newFormat.replace("%MODE%", "Global");
			newFormat = newFormat.replace("%M%", "G");
		}

		newFormat = newFormat + "%MESSAGE%";

		BaseComponent[] toSend;

		if (sender.hasPermission("multichat.chat.colour") || sender.hasPermission("multichat.chat.color")) {

			newFormat = newFormat.replace("%MESSAGE%", message);
			toSend = TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', newFormat));

		} else {

			newFormat = newFormat.replace("%MESSAGE%", "");
			toSend = TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', newFormat) + message);
		}

		return toSend;

	}

	public BaseComponent[] buildFormat(String name, String displayName, String server, String world, ProxiedPlayer receiver, String format, String message) {

		String newFormat = format;

		newFormat = newFormat.replace("%DISPLAYNAME%", displayName);
		newFormat = newFormat.replace("%NAME%", name);
		newFormat = newFormat.replace("%DISPLAYNAMET%", receiver.getDisplayName());
		newFormat = newFormat.replace("%NAMET%", receiver.getName());

		Optional<PlayerMeta> opmt = PlayerMetaManager.getInstance().getPlayer(receiver.getUniqueId());
		if (opmt.isPresent()) {
			newFormat = newFormat.replace("%PREFIXT%", opmt.get().prefix);
			newFormat = newFormat.replace("%SUFFIXT%", opmt.get().suffix);
			newFormat = newFormat.replace("%NICKT%", opmt.get().nick);
			newFormat = newFormat.replace("%WORLDT%", opmt.get().world);
		}

		newFormat = newFormat.replace("%SERVER%", server);
		newFormat = newFormat.replace("%SERVERT%", receiver.getServer().getInfo().getName());

		newFormat = newFormat.replace("%WORLD%", world);


		newFormat = newFormat.replace("%MODE%", "Global");
		newFormat = newFormat.replace("%M%", "G");

		newFormat = newFormat + "%MESSAGE%";

		BaseComponent[] toSend;

		newFormat = newFormat.replace("%MESSAGE%", message);
		toSend = TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', newFormat));

		return toSend;

	}

	public void sendToConsole(ProxiedPlayer sender, String format, String message) {

		String newFormat = format;

		newFormat = newFormat.replace("%DISPLAYNAME%", sender.getDisplayName());
		newFormat = newFormat.replace("%NAME%", sender.getName());

		Optional<PlayerMeta> opm = PlayerMetaManager.getInstance().getPlayer(sender.getUniqueId());
		if (opm.isPresent()) {
			newFormat = newFormat.replace("%PREFIX%", opm.get().prefix);
			newFormat = newFormat.replace("%SUFFIX%", opm.get().suffix);
			newFormat = newFormat.replace("%NICK%", opm.get().nick);
			newFormat = newFormat.replace("%WORLD%", opm.get().world);
		}

		newFormat = newFormat.replace("%DISPLAYNAMET%", "CONSOLE");
		newFormat = newFormat.replace("%NAMET%", "CONSOLE");
		newFormat = newFormat.replace("%SERVER%", sender.getServer().getInfo().getName());
		newFormat = newFormat.replace("%SERVERT%", "CONSOLE");
		newFormat = newFormat.replace("%WORLDT%", "CONSOLE");

		if (MultiChat.globalplayers.get(sender.getUniqueId()).equals(false)) {
			newFormat = newFormat.replace("%MODE%", "Local");
			newFormat = newFormat.replace("%M%", "L");
		}

		if (MultiChat.globalplayers.get(sender.getUniqueId()).equals(true)) {
			newFormat = newFormat.replace("%MODE%", "Global");
			newFormat = newFormat.replace("%M%", "G");
		}

		newFormat = newFormat + "%MESSAGE%";

		if (sender.hasPermission("multichat.chat.colour") || sender.hasPermission("multichat.chat.color")) {

			newFormat = newFormat.replace("%MESSAGE%", message);
			ConsoleManager.logChat(newFormat);

		} else {

			newFormat = newFormat.replace("%MESSAGE%", "");
			ConsoleManager.logBasicChat(newFormat, message);

		}

	}

	public void sendToConsole(String name, String displayName, String server, String world, String format, String message) {

		String newFormat = format;

		newFormat = newFormat.replace("%DISPLAYNAME%", displayName);
		newFormat = newFormat.replace("%NAME%", name);
		newFormat = newFormat.replace("%DISPLAYNAMET%", "CONSOLE");
		newFormat = newFormat.replace("%NAMET%", "CONSOLE");
		newFormat = newFormat.replace("%SERVER%", server);
		newFormat = newFormat.replace("%SERVERT%", "CONSOLE");
		newFormat = newFormat.replace("%WORLD%", world);
		newFormat = newFormat.replace("%WORLDT%", "CONSOLE");

		newFormat = newFormat.replace("%MODE%", "Global");
		newFormat = newFormat.replace("%M%", "G");

		newFormat = newFormat + "%MESSAGE%";

		newFormat = newFormat.replace("%MESSAGE%", message);

		ConsoleManager.logChat(newFormat);

	}
}
