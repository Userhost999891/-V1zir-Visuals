package dev.v1zir.visuals.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.v1zir.visuals.V1zir;
import dev.v1zir.visuals.gui.clickgui.ClickGuiScreen;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.impl.hud.HudModule;
import dev.v1zir.visuals.module.setting.Setting;
import dev.v1zir.visuals.optimize.Optimizations;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Saves modules, settings, keybinds, HUD positions and GUI layout to config/v1zir/config.json. */
public final class ConfigManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private ConfigManager() {
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve(V1zir.MOD_ID).resolve("config.json");
	}

	public static void load() {
		Path path = file();
		if (!Files.exists(path)) {
			applyDefaults();
			return;
		}
		try {
			JsonObject root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
			JsonObject modules = root.has("modules") ? root.getAsJsonObject("modules") : new JsonObject();
			for (Module module : ModuleManager.all()) {
				if (!modules.has(module.name())) continue;
				JsonObject obj = modules.getAsJsonObject(module.name());
				if (obj.has("key")) module.setKey(obj.get("key").getAsInt());
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
			if (root.has("gui")) ClickGuiScreen.loadLayout(root.getAsJsonObject("gui"));
			if (root.has("optimizations")) Optimizations.fromJson(root.getAsJsonObject("optimizations"));
		} catch (IOException | RuntimeException e) {
			V1zir.LOGGER.error("Could not read {}, using defaults", path, e);
			applyDefaults();
		}
	}

	/** First launch: a tasteful set of modules turned on. */
	private static void applyDefaults() {
		enable("Shader Sky", "Watermark", "Array List", "Keybinds", "Potions", "Target Hud", "Notifications", "Target ESP", "Jump Circles", "Zoom");
	}

	private static void enable(String... names) {
		for (String name : names) {
			Module module = ModuleManager.byName(name);
			if (module != null) module.setEnabled(true, false);
		}
	}

	public static void save() {
		JsonObject root = new JsonObject();
		root.addProperty("version", V1zir.version());
		JsonObject modules = new JsonObject();
		for (Module module : ModuleManager.all()) {
			JsonObject obj = new JsonObject();
			obj.addProperty("enabled", module.isEnabled());
			obj.addProperty("key", module.key());
			JsonObject settings = new JsonObject();
			for (Setting<?> setting : module.settings()) settings.add(setting.name(), setting.toJson());
			obj.add("settings", settings);
			if (module instanceof HudModule hud) {
				obj.addProperty("x", hud.fx());
				obj.addProperty("y", hud.fy());
			}
			modules.add(module.name(), obj);
		}
		root.add("modules", modules);
		root.add("gui", ClickGuiScreen.saveLayout());
		root.add("optimizations", Optimizations.toJson());
		Path path = file();
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(root), StandardCharsets.UTF_8);
		} catch (IOException e) {
			V1zir.LOGGER.error("Could not save {}", path, e);
		}
	}
}
