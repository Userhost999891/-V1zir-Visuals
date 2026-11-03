package dev.v1zir.visuals.render.wallpaper;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.v1zir.visuals.V1zir;
import dev.v1zir.visuals.render.sky.SkyPreset;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * Every wallpaper the title screen can show (the dedicated shaders, the sky presets and the pictures in
 * config/v1zir/wallpapers), and which one each screen shows with what adjustments (saved in config.json).
 */
public final class Wallpapers {
	public enum Screen {
		LOCK, HOME
	}

	public static final Wallpaper EMBER = Wallpaper.shader("ember", "Ember", "Żar", 0);
	private static final List<Wallpaper> BUILT_IN = List.of(
		EMBER,
		Wallpaper.shader("tide", "Tide", "Toń", 1),
		Wallpaper.shader("silk", "Silk", "Jedwab", 2),
		Wallpaper.shader("stardust", "Stardust", "Gwiezdny pył", 3),
		Wallpaper.shader("glow", "Glow", "Poświata", 4),
		Wallpaper.shader("dunes", "Dunes", "Wydmy", 5),
		Wallpaper.sky(SkyPreset.MAGMA),
		Wallpaper.sky(SkyPreset.AURORA),
		Wallpaper.sky(SkyPreset.NEBULA),
		Wallpaper.sky(SkyPreset.INK),
		Wallpaper.sky(SkyPreset.SYNTHWAVE),
		Wallpaper.sky(SkyPreset.SUNSET),
		Wallpaper.sky(SkyPreset.BLOOD_MOON),
		Wallpaper.sky(SkyPreset.REALISTIC)
	);
	/** The new mesh gradient greets on the lock screen; Magma, the long-time favourite, stays behind the buttons. */
	private static final String[] DEFAULTS = {"live:ember", "sky:magma"};
	private static final WallpaperStyle NO_STYLE = new WallpaperStyle();
	private static final String[] EXTENSIONS = {".png", ".jpg", ".jpeg", ".gif"};

	private static List<Wallpaper> all;
	private static final String[] selected = DEFAULTS.clone();
	/** Resolved selection per screen; a deleted picture falls back to the default. */
	private static final Wallpaper[] current = new Wallpaper[2];
	@SuppressWarnings("unchecked")
	private static final Map<String, WallpaperStyle>[] styles = new Map[]{new HashMap<>(), new HashMap<>()};
	/** Id of a picture the file dialog added, until the picker takes it. */
	private static final AtomicReference<String> picked = new AtomicReference<>();
	private static volatile boolean picking;

	private Wallpapers() {
	}

	/** Shaders, then skies, then pictures by file name. */
	public static List<Wallpaper> all() {
		if (all == null) refresh();
		return all;
	}

	public static Wallpaper selected(Screen screen) {
		if (all == null) refresh();
		return current[screen.ordinal()];
	}

	/** The adjustments of a wallpaper on a screen; the returned object is shared and must not be changed. */
	public static WallpaperStyle style(Screen screen, String id) {
		WallpaperStyle style = styles[screen.ordinal()].get(id);
		return style != null ? style : NO_STYLE;
	}

	public static WallpaperStyle style(Screen screen) {
		return style(screen, selected(screen).id);
	}

	/** Shows the wallpaper on the screen with these adjustments (copied). */
	public static void apply(Screen screen, Wallpaper wallpaper, WallpaperStyle style) {
		selected[screen.ordinal()] = wallpaper.id;
		store(screen, wallpaper.id, style);
		resolve();
	}

	/** Remembers adjustments of a wallpaper on a screen without selecting it. */
	public static void store(Screen screen, String id, WallpaperStyle style) {
		Map<String, WallpaperStyle> map = styles[screen.ordinal()];
		if (style.same(NO_STYLE)) map.remove(id);
		else map.put(id, style.copy());
	}

	@Nullable
	private static Wallpaper byId(String id) {
		for (Wallpaper wallpaper : all) if (wallpaper.id.equals(id)) return wallpaper;
		return null;
	}

	private static void resolve() {
		for (Screen screen : Screen.values()) {
			int i = screen.ordinal();
			Wallpaper wallpaper = byId(selected[i]);
			current[i] = wallpaper != null ? wallpaper : byId(DEFAULTS[i]);
		}
	}

	// ------------------------------------------------------------------ the wallpapers folder

	public static Path folder() {
		return FabricLoader.getInstance().getConfigDir().resolve(V1zir.MOD_ID).resolve("wallpapers");
	}

	/** Lists the pictures of the folder again (creating it on first use). */
	public static void refresh() {
		List<Wallpaper> list = new ArrayList<>(BUILT_IN);
		Path folder = folder();
		try {
			Files.createDirectories(folder);
			try (Stream<Path> files = Files.list(folder)) {
				files.filter(Wallpapers::supported).sorted().forEach(file -> list.add(Wallpaper.image(file)));
			}
		} catch (IOException e) {
			V1zir.LOGGER.warn("Could not list {}", folder, e);
		}
		all = List.copyOf(list);
		WallpaperImages.retryFailed();
		resolve();
	}

	private static boolean supported(Path file) {
		if (!Files.isRegularFile(file)) return false;
		String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
		for (String extension : EXTENSIONS) if (name.endsWith(extension)) return true;
		return false;
	}

	public static void openFolder() {
		try {
			Files.createDirectories(folder());
		} catch (IOException e) {
			V1zir.LOGGER.warn("Could not create {}", folder(), e);
		}
		Util.getOperatingSystem().open(folder());
	}

	/**
	 * Asks for a picture with the system's file dialog (on its own thread, it blocks) and copies it into the folder;
	 * the picker then takes its id with {@link #takePicked()}. Without a native dialog the folder opens instead.
	 */
	public static void pick(String title) {
		if (picking) return;
		picking = true;
		Thread thread = new Thread(() -> {
			try {
				String chosen;
				try (MemoryStack stack = MemoryStack.stackPush()) {
					PointerBuffer filters = stack.mallocPointer(EXTENSIONS.length);
					for (String extension : EXTENSIONS) filters.put(stack.UTF8("*" + extension));
					filters.flip();
					chosen = TinyFileDialogs.tinyfd_openFileDialog(title, null, filters, "Images (PNG, JPEG, GIF)", false);
				}
				if (chosen != null) picked.set(copyIn(Path.of(chosen)));
			} catch (Exception | LinkageError e) {
				V1zir.LOGGER.warn("No file dialog, opening the wallpapers folder instead", e);
				openFolder();
			} finally {
				picking = false;
			}
		}, "V1zir Wallpaper Picker");
		thread.setDaemon(true);
		thread.start();
	}

	/** The id of a picture the file dialog added since the last call, or null. */
	@Nullable
	public static String takePicked() {
		return picked.getAndSet(null);
	}

	/** Copies a picture into the folder under a free name; returns its wallpaper id. */
	private static String copyIn(Path source) throws IOException {
		Path folder = folder();
		Files.createDirectories(folder);
		if (source.toAbsolutePath().getParent().equals(folder.toAbsolutePath())) return "image:" + source.getFileName();
		String name = source.getFileName().toString();
		int dot = name.lastIndexOf('.');
		String base = dot > 0 ? name.substring(0, dot) : name;
		String extension = dot > 0 ? name.substring(dot) : "";
		Path target = folder.resolve(name);
		for (int n = 2; Files.exists(target); n++) target = folder.resolve(base + " (" + n + ")" + extension);
		Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
		return "image:" + target.getFileName();
	}

	/** Deletes a picture from the folder; screens that showed it go back to their default. */
	public static boolean delete(Wallpaper wallpaper) {
		if (wallpaper.file == null) return false;
		try {
			Files.deleteIfExists(wallpaper.file);
		} catch (IOException e) {
			V1zir.LOGGER.warn("Could not delete {}", wallpaper.file, e);
			return false;
		}
		WallpaperImages.forget(wallpaper.id);
		for (Map<String, WallpaperStyle> map : styles) map.remove(wallpaper.id);
		refresh();
		return true;
	}

	// ------------------------------------------------------------------ config

	public static JsonObject toJson() {
		JsonObject json = new JsonObject();
		JsonObject styleJson = new JsonObject();
		for (Screen screen : Screen.values()) {
			String key = screen.name().toLowerCase(Locale.ROOT);
			json.addProperty(key, selected[screen.ordinal()]);
			JsonObject map = new JsonObject();
			styles[screen.ordinal()].forEach((id, style) -> map.add(id, style.toJson()));
			styleJson.add(key, map);
		}
		json.add("styles", styleJson);
		return json;
	}

	/**
	 * Reads the "wallpapers" section of the config root. Configs from before it existed had one "Background" sky preset
	 * for the whole title screen: it becomes the home screen's wallpaper.
	 */
	public static void fromJson(JsonObject root) {
		JsonObject json = root.has("wallpapers") ? root.getAsJsonObject("wallpapers") : null;
		if (json == null) {
			JsonElement legacy = legacyBackground(root);
			if (legacy != null) selected[Screen.HOME.ordinal()] = "sky:" + SkyPreset.byTitle(legacy.getAsString()).name().toLowerCase(Locale.ROOT);
		} else {
			JsonObject styleJson = json.has("styles") ? json.getAsJsonObject("styles") : new JsonObject();
			for (Screen screen : Screen.values()) {
				String key = screen.name().toLowerCase(Locale.ROOT);
				if (json.has(key)) selected[screen.ordinal()] = json.get(key).getAsString();
				Map<String, WallpaperStyle> map = styles[screen.ordinal()];
				map.clear();
				if (!styleJson.has(key)) continue;
				for (Map.Entry<String, JsonElement> entry : styleJson.getAsJsonObject(key).entrySet()) {
					if (entry.getValue().isJsonObject()) map.put(entry.getKey(), WallpaperStyle.fromJson(entry.getValue().getAsJsonObject()));
				}
			}
		}
		if (all != null) resolve();
	}

	@Nullable
	private static JsonElement legacyBackground(JsonObject root) {
		try {
			return root.getAsJsonObject("modules").getAsJsonObject("Main Menu").getAsJsonObject("settings").get("Background");
		} catch (RuntimeException e) {
			return null;
		}
	}
}
