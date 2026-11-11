package dev.v1zir.visuals.gui.menu;

import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.ServerList;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The world or server the player was in last, which the lock screen offers to rejoin: remembered on joining, with
 * the ping followed while on a server, and saved in config.json. Realms are left out (they cannot be joined by address).
 * A world shows with its name and icon, a server with its address and its icon from the server list.
 */
public final class LastPlayed {
	/** The world's or the server entry's name; empty before anything was played. */
	static String name = "";
	/** The world's folder in saves, or the server's address. */
	static String target = "";
	static boolean server;
	/** Last ping on the server in milliseconds, or -1. */
	static int ping = -1;
	/** The server's icon as it was when joined (PNG), for one joined by address and not kept in the list; null if none. */
	private static byte[] favicon;
	/** The session in progress is the one remembered, so its ping counts. */
	private static boolean tracking;
	private static int ticks;

	private LastPlayed() {
	}

	/** A world or server was joined. */
	public static void joined(MinecraftClient client) {
		IntegratedServer local = client.getServer();
		ServerInfo entry = client.getCurrentServerEntry();
		tracking = false;
		if (local != null) {
			server = false;
			name = local.getSaveProperties().getLevelName();
			target = local.getSavePath(WorldSavePath.ROOT).normalize().getFileName().toString();
			ping = -1;
		} else if (entry != null && !entry.isRealm()) {
			server = true;
			tracking = true;
			if (!entry.address.equals(target)) ping = -1;
			name = entry.name;
			target = entry.address;
			favicon = entry.getFavicon();
		}
	}

	/** What to call it: a world's name, a server's address (its name in the list is often just "Minecraft Server"). */
	static String label() {
		if (!server) return name;
		String address = target.strip();
		return address.endsWith(":25565") ? address.substring(0, address.length() - 6) : address;
	}

	/**
	 * The icon to show for it (PNG bytes): the world's icon.png, or the server's icon from the server list (or as it was
	 * when joined); null when it has none. Reads the disk: call rarely.
	 */
	static byte[] icon(MinecraftClient client) {
		if (name.isEmpty() || target.isEmpty()) return null;
		try {
			if (!server) {
				Path file = client.getLevelStorage().getSavesDirectory().resolve(target).resolve("icon.png");
				return Files.isRegularFile(file) && Files.size(file) < 1_048_576L ? Files.readAllBytes(file) : null;
			}
			ServerList list = new ServerList(client);
			list.loadFile();
			ServerInfo info = list.get(target);
			byte[] icon = info != null ? info.getFavicon() : null;
			return icon != null ? icon : favicon;
		} catch (IOException | RuntimeException e) {
			return null;
		}
	}

	/** Follows the ping once a second while on the remembered server; called every tick in a world. */
	public static void tick(MinecraftClient client) {
		if (!tracking || ++ticks % 20 != 0) return;
		ClientPlayNetworkHandler network = client.getNetworkHandler();
		PlayerListEntry self = network != null && client.player != null ? network.getPlayerListEntry(client.player.getUuid()) : null;
		if (self != null && self.getLatency() > 0) ping = self.getLatency();
	}

	/** Whether there is something to rejoin: a server, or a world whose folder is still there. Reads the disk: call rarely. */
	static boolean available(MinecraftClient client) {
		if (name.isEmpty() || target.isEmpty()) return false;
		if (server) return true;
		try {
			return Files.isRegularFile(client.getLevelStorage().getSavesDirectory().resolve(target).resolve("level.dat"));
		} catch (RuntimeException e) {
			return false;
		}
	}

	/** Loads the world or connects to the server; cancelling returns to parent. */
	static void join(MinecraftClient client, Screen parent) {
		if (!server) {
			client.createIntegratedServerLoader().start(target, () -> client.setScreen(parent));
			return;
		}
		// The saved entry keeps the player's resource pack choice for the server.
		ServerList list = new ServerList(client);
		list.loadFile();
		ServerInfo info = list.get(target);
		if (info == null) info = new ServerInfo(name, target, ServerInfo.ServerType.OTHER);
		ConnectScreen.connect(parent, client, ServerAddress.parse(target), info, false, null);
	}

	public static JsonObject toJson() {
		JsonObject json = new JsonObject();
		json.addProperty("name", name);
		json.addProperty("target", target);
		json.addProperty("server", server);
		json.addProperty("ping", ping);
		return json;
	}

	public static void fromJson(JsonObject root) {
		if (!root.has("lastPlayed")) return;
		JsonObject json = root.getAsJsonObject("lastPlayed");
		if (json.has("name")) name = json.get("name").getAsString();
		if (json.has("target")) target = json.get("target").getAsString();
		if (json.has("server")) server = json.get("server").getAsBoolean();
		if (json.has("ping")) ping = json.get("ping").getAsInt();
	}
}
