package dev.v1zir.visuals.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.v1zir.visuals.V1zir;
import dev.v1zir.visuals.gui.clickgui.ClickGuiScreen;
import dev.v1zir.visuals.gui.menu.LastPlayed;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.impl.hud.HudModule;
import dev.v1zir.visuals.module.setting.Setting;
import dev.v1zir.visuals.optimize.Optimizations;
import dev.v1zir.visuals.render.wallpaper.Wallpapers;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Saves modules, settings, keybinds, favourites, HUD positions, GUI layout, wallpapers and the last world played to
 * config/v1zir/config.json.
 * <p>
 * Profiles: named sets of the module part (state, key, favourite, settings, HUD position) in config/v1zir/configs.
 * config.json always holds the live state and the active profile's name; switching first writes the live state into
 * the active profile's file, then applies the other one.
 */
public final class ConfigManager {
	public static final String DEFAULT_PROFILE = "Default";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static String profile = DEFAULT_PROFILE;

	private ConfigManager() {
	}

	private static Path dir() {
		return FabricLoader.getInstance().getConfigDir().resolve(V1zir.MOD_ID);
	}

	private static Path file() {
		return dir().resolve("config.json");
	}

	private static Path profileFile(String name) {
		return dir().resolve("configs").resolve(name + ".json");
	}

	public static void load() {
		Path path = file();
		if (!Files.exists(path)) {
			applyDefaults();
			return;
		}
		try {
			JsonObject root = read(path);
			applyModules(root.has("modules") ? root.getAsJsonObject("modules") : new JsonObject());
			String active = root.has("profile") ? root.get("profile").getAsString() : DEFAULT_PROFILE;
			// The name becomes a file name: a hand-edited one that is not valid falls back to the default.
			profile = validName(active) ? active : DEFAULT_PROFILE;
			if (root.has("gui")) ClickGuiScreen.loadLayout(root.getAsJsonObject("gui"));
			if (root.has("optimizations")) Optimizations.fromJson(root.getAsJsonObject("optimizations"));
			Wallpapers.fromJson(root);
			LastPlayed.fromJson(root);
		} catch (IOException | RuntimeException e) {
			V1zir.LOGGER.error("Could not read {}, using defaults", path, e);
			applyDefaults();
		}
	}

	private static JsonObject read(Path path) throws IOException {
		return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
	}

	/** Applies the modules saved in json; modules it does not mention keep their state. */
	private static void applyModules(JsonObject modules) {
		for (Module module : ModuleManager.all()) {
			if (!modules.has(module.name())) continue;
			JsonObject obj = modules.getAsJsonObject(module.name());
			if (obj.has("key")) module.setKey(obj.get("key").getAsInt());
			module.setFavourite(obj.has("favourite") && obj.get("favourite").getAsBoolean());
			if (obj.has("settings")) {
				JsonObject settings = obj.getAsJsonObject("settings");
				for (Setting<?> setting : module.settings()) {
					JsonElement value = settings.get(setting.name());
					if (value != null) setting.fromJson(value);
				}
			}
			if (module instanceof HudModule hud && obj.has("x") && obj.has("y")) {
				hud.setPosition(obj.get("x").getAsFloat(), obj.get("y").getAsFloat());
			}
			if (obj.has("enabled")) module.setEnabled(obj.get("enabled").getAsBoolean(), false);
		}
	}

	/** First launch: a tasteful set of modules turned on. */
	private static void applyDefaults() {
		enable("Shader Sky", "Watermark", "Array List", "Keybinds", "Potions", "Target Hud", "Notifications", "Target ESP", "Jump Circles", "Zoom", "Rain Effects");
	}

	private static void enable(String... names) {
		for (String name : names) {
			Module module = ModuleManager.byName(name);
			if (module != null) module.setEnabled(true, false);
		}
	}

	private static JsonObject modulesJson() {
		JsonObject modules = new JsonObject();
		for (Module module : ModuleManager.all()) {
			JsonObject obj = new JsonObject();
			obj.addProperty("enabled", module.isEnabled());
			obj.addProperty("key", module.key());
			if (module.isFavourite()) obj.addProperty("favourite", true);
			JsonObject settings = new JsonObject();
			for (Setting<?> setting : module.settings()) settings.add(setting.name(), setting.toJson());
			obj.add("settings", settings);
			if (module instanceof HudModule hud) {
				obj.addProperty("x", hud.fx());
				obj.addProperty("y", hud.fy());
			}
			modules.add(module.name(), obj);
		}
		return modules;
	}

	public static void save() {
		JsonObject root = new JsonObject();
		root.addProperty("version", V1zir.version());
		root.addProperty("profile", profile);
		root.add("modules", modulesJson());
		root.add("gui", ClickGuiScreen.saveLayout());
		root.add("optimizations", Optimizations.toJson());
		root.add("wallpapers", Wallpapers.toJson());
		root.add("lastPlayed", LastPlayed.toJson());
		write(file(), root);
	}

	private static boolean write(Path path, JsonObject json) {
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(json), StandardCharsets.UTF_8);
			return true;
		} catch (IOException e) {
			V1zir.LOGGER.error("Could not save {}", path, e);
			return false;
		}
	}

	// ------------------------------------------------------------------ profiles

	/** The active profile. */
	public static String profile() {
		return profile;
	}

	/** Saved profiles and the active one, sorted by name. */
	public static List<String> profiles() {
		List<String> names = new ArrayList<>();
		names.add(profile);
		Path dir = profileFile(profile).getParent();
		if (Files.isDirectory(dir)) {
			try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*.json")) {
				for (Path file : files) {
					String name = file.getFileName().toString();
					name = name.substring(0, name.length() - ".json".length());
					if (!names.contains(name)) names.add(name);
				}
			} catch (IOException e) {
				V1zir.LOGGER.warn("Could not list the profiles in {}", dir, e);
			}
		}
		names.sort(String.CASE_INSENSITIVE_ORDER);
		return names;
	}

	/** Letters, digits, spaces, dashes and underscores, 1 to 24 of them: safe as a file name everywhere. */
	public static boolean validName(String name) {
		return !name.isBlank() && name.length() <= 24 && name.equals(name.strip()) && name.chars().allMatch(c -> Character.isLetterOrDigit(c) || c == ' ' || c == '-' || c == '_');
	}

	/** Keeps the live modules in the active profile, then applies the named one. */
	public static void switchProfile(String name) {
		if (name.equals(profile) || !validName(name)) return;
		if (!write(profileFile(profile), modulesOnly())) return;
		Path file = profileFile(name);
		try {
			if (Files.exists(file)) applyModules(read(file).getAsJsonObject("modules"));
		} catch (IOException | RuntimeException e) {
			V1zir.LOGGER.error("Could not read the profile {}", file, e);
			return;
		}
		profile = name;
		save();
	}

	/** A new profile under a free name ("Profile 2"...) holding a copy of the live modules; it becomes the active one. */
	public static String createProfile() {
		List<String> names = profiles();
		int n = names.size() + 1;
		while (names.contains("Profile " + n)) n++;
		String name = "Profile " + n;
		if (!write(profileFile(profile), modulesOnly()) || !write(profileFile(name), modulesOnly())) return profile;
		profile = name;
		save();
		return name;
	}

	/** Renames a profile; false when the new name is taken or not valid. */
	public static boolean renameProfile(String from, String to) {
		if (from.equals(to)) return true;
		if (!validName(to) || profiles().stream().anyMatch(n -> !n.equals(from) && n.equalsIgnoreCase(to))) return false;
		Path source = profileFile(from);
		try {
			if (Files.exists(source)) Files.move(source, profileFile(to));
		} catch (IOException e) {
			V1zir.LOGGER.error("Could not rename the profile {}", source, e);
			return false;
		}
		if (from.equals(profile)) {
			profile = to;
			save();
		}
		return true;
	}

	/** Deletes a profile; the active one first hands over to another. The last one stays. */
	public static void deleteProfile(String name) {
		List<String> names = profiles();
		if (names.size() < 2) return;
		if (name.equals(profile)) switchProfile(names.get(names.get(0).equals(name) ? 1 : 0));
		if (name.equals(profile)) return;
		try {
			Files.deleteIfExists(profileFile(name));
		} catch (IOException e) {
			V1zir.LOGGER.error("Could not delete the profile {}", name, e);
		}
	}

	private static JsonObject modulesOnly() {
		JsonObject json = new JsonObject();
		json.addProperty("version", V1zir.version());
		json.add("modules", modulesJson());
		return json;
	}
}
