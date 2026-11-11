package dev.v1zir.visuals.account;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import dev.v1zir.visuals.V1zir;
import dev.v1zir.visuals.mixin.MinecraftClientAccessor;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.ProfileKeys;
import net.minecraft.client.session.Session;
import net.minecraft.util.Uuids;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * The accounts to play as, switched from the main menu's name pill: the one the launcher started the game with, and
 * any number of offline accounts (nicknames for servers that do not check accounts), each with the UUID such a server
 * gives that name. Switching swaps the game's session (the name and UUID it logs in with) and its profile; an offline
 * account signs no chat (no profile keys), so a server never sees keys belonging to another account. The list and the
 * account in use are kept in config/v1zir/accounts.json, and the choice comes back on the next start.
 * <p>
 * Render thread only.
 */
public final class Accounts {
	/** A name to play as; launcher marks the account the game was started with. */
	public record Account(String name, UUID uuid, boolean launcher) {
	}

	private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
	private static final List<Account> OFFLINE = new ArrayList<>();
	private static Account launcher;
	private static Session launcherSession;
	private static ProfileKeys launcherKeys;
	private static CompletableFuture<?> launcherProfile;
	/** The launcher account's profile as fetched (with its skin), kept for showing its head while playing as another. */
	private static GameProfile launcherGameProfile;
	private static Account current;
	/** Counts the switches, so what shows the account in use knows to refresh. */
	private static int revision;
	private static boolean ready;

	private Accounts() {
	}

	/** Remembers the launcher's account and brings back the one chosen last; once, when the main menu first opens. */
	public static void init(MinecraftClient client) {
		if (ready) return;
		ready = true;
		launcherSession = client.getSession();
		try {
			MinecraftClientAccessor access = (MinecraftClientAccessor) client;
			launcherKeys = access.v1zir$getProfileKeys();
			launcherProfile = access.v1zir$getGameProfileFuture();
		} catch (RuntimeException | LinkageError e) {
			V1zir.LOGGER.warn("Account switching unavailable", e);
		}
		launcher = new Account(launcherSession.getUsername(), launcherSession.getUuidOrNull(), true);
		current = launcher;
		// Still the launcher's session here; the main menu waited on this profile already to show the player's head.
		try {
			launcherGameProfile = client.getGameProfile();
		} catch (RuntimeException e) {
			V1zir.LOGGER.warn("Could not read the launcher's profile", e);
		}
		String selected = load();
		for (Account a : OFFLINE) {
			if (a.name().equalsIgnoreCase(selected)) {
				use(client, a);
				break;
			}
		}
	}

	/** Whether switching works in this game (the accessor applied). */
	public static boolean available() {
		return launcherProfile != null;
	}

	/** The launcher's account first, then the offline ones in the order they were added. */
	public static List<Account> all() {
		List<Account> list = new ArrayList<>(OFFLINE.size() + 1);
		if (launcher != null) list.add(launcher);
		list.addAll(OFFLINE);
		return Collections.unmodifiableList(list);
	}

	public static Account current() {
		return current;
	}

	/** Goes up with every switch of account. */
	public static int revision() {
		return revision;
	}

	/** The profile to show the account's skin with: the launcher's as fetched, an offline name's default one. */
	public static GameProfile profile(Account account) {
		if (account.launcher() && launcherGameProfile != null) return launcherGameProfile;
		return Uuids.getOfflinePlayerProfile(account.name());
	}

	/** Whether a name can be an offline account: 3 to 16 letters, digits or underscores, as servers accept. */
	public static boolean valid(String name) {
		return NAME.matcher(name).matches();
	}

	/** Adds an offline account and plays as it (or plays as the one of that name already there); false for a bad name. */
	public static boolean add(MinecraftClient client, String name) {
		if (!valid(name) || !available()) return false;
		for (Account a : all()) {
			if (a.name().equalsIgnoreCase(name)) {
				use(client, a);
				return true;
			}
		}
		Account account = new Account(name, Uuids.getOfflinePlayerUuid(name), false);
		OFFLINE.add(account);
		use(client, account);
		return true;
	}

	/** Forgets an offline account; playing as it, the game goes back to the launcher's. */
	public static void remove(MinecraftClient client, Account account) {
		if (account.launcher() || !OFFLINE.remove(account)) return;
		if (account.equals(current)) use(client, launcher);
		else save();
	}

	/** Plays as the account from now on: the next server joined sees its name and UUID. */
	public static void use(MinecraftClient client, Account account) {
		if (!available()) return;
		MinecraftClientAccessor access = (MinecraftClientAccessor) client;
		if (account.launcher()) {
			access.v1zir$setSession(launcherSession);
			access.v1zir$setProfileKeys(launcherKeys);
			access.v1zir$setGameProfileFuture(launcherProfile);
		} else {
			access.v1zir$setSession(new Session(account.name(), account.uuid(), "0", Optional.empty(), Optional.empty()));
			access.v1zir$setProfileKeys(ProfileKeys.MISSING);
			// No fetched profile: the game then makes one from the session (this name and UUID).
			access.v1zir$setGameProfileFuture(CompletableFuture.completedFuture(null));
		}
		current = account;
		revision++;
		save();
	}

	// ------------------------------------------------------------------ config

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve(V1zir.MOD_ID).resolve("accounts.json");
	}

	/** Reads the offline accounts; returns the name chosen last ("" for the launcher's). */
	private static String load() {
		Path path = file();
		if (!Files.isRegularFile(path)) return "";
		try {
			JsonObject root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
			if (root.has("offline") && root.get("offline").isJsonArray()) {
				for (JsonElement e : root.getAsJsonArray("offline")) {
					String name = e.isJsonPrimitive() ? e.getAsString() : "";
					if (valid(name) && OFFLINE.stream().noneMatch(a -> a.name().equalsIgnoreCase(name))) {
						OFFLINE.add(new Account(name, Uuids.getOfflinePlayerUuid(name), false));
					}
				}
			}
			return root.has("selected") && root.get("selected").isJsonPrimitive() ? root.get("selected").getAsString() : "";
		} catch (IOException | RuntimeException e) {
			V1zir.LOGGER.warn("Could not read {}", path, e);
			return "";
		}
	}

	private static void save() {
		JsonObject root = new JsonObject();
		root.addProperty("selected", current == null || current.launcher() ? "" : current.name());
		JsonArray names = new JsonArray();
		for (Account a : OFFLINE) names.add(a.name());
		root.add("offline", names);
		Path path = file();
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, root.toString(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			V1zir.LOGGER.warn("Could not save {}", path, e);
		}
	}
}
