package dev.v1zir.visuals.optimize;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.v1zir.visuals.V1zir;
import dev.v1zir.visuals.config.ConfigManager;
import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.module.setting.Setting;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;

import java.util.List;

/**
 * Always-on vanilla optimisations (not modules). Each one can be switched off on the Settings tab of the
 * Click GUI to rule it out if something looks wrong, and each one stands down when an installed mod
 * already does the same work or patches the same code.
 */
public final class Optimizations {
	public static final BoolSetting OCCLUSION = new BoolSetting("Entity culling", true);
	public static final BoolSetting DISTANCE = new BoolSetting("Distance culling", true);
	public static final BoolSetting PARTICLES = new BoolSetting("Particle limit", true);
	public static final BoolSetting BACKGROUND = new BoolSetting("Background FPS limit", true);
	public static final BoolSetting UPLOADS = new BoolSetting("Chunk upload pacing", true);
	/** Once: VSync off and no frame rate limit, the two settings that hold a fast computer at its screen's 60 Hz. */
	public static final BoolSetting UNLOCK = new BoolSetting("Unlock frame rate", true);
	private static final List<Setting<?>> SETTINGS = List.of(OCCLUSION, DISTANCE, PARTICLES, BACKGROUND, UPLOADS, UNLOCK);
	/** The highest Max Framerate the game offers, which it shows as Unlimited. */
	private static final int UNLIMITED = 260;
	/** The frame rate was unlocked once already: what the player sets after that is theirs. */
	private static boolean unlocked;

	// Mods that already do a job, or patch the same code: the optimisation then stands down.
	private static final boolean ENTITY_CULLING_MOD = loaded("entityculling");
	/** Shader packs render the world again for shadows, where hidden entities still cast them. */
	private static final boolean IRIS = loaded("iris") || loaded("oculus");
	private static final boolean MORE_CULLING = loaded("moreculling");
	private static final boolean DYNAMIC_FPS = loaded("dynamic_fps");
	/** Sodium replaces vanilla's chunk building and uploads with its own. */
	private static final boolean SODIUM = loaded("sodium") || loaded("embeddium");
	private static int frame;

	private Optimizations() {
	}

	private static boolean loaded(String id) {
		return FabricLoader.getInstance().isModLoaded(id);
	}

	public static List<Setting<?>> settings() {
		return SETTINGS;
	}

	/** Render thread, once per finished frame (FrameUniforms.flip). */
	public static void endFrame() {
		frame++;
		OcclusionCuller.endFrame();
		GpuTimer.endFrame();
		HitchMonitor.endFrame();
	}

	/** Counts finished frames, for per-frame budgets. */
	public static int frame() {
		return frame;
	}

	public static boolean occlusion() {
		return OCCLUSION.on() && !ENTITY_CULLING_MOD && !IRIS;
	}

	public static boolean distance() {
		return DISTANCE.on() && !MORE_CULLING;
	}

	public static boolean particles() {
		return PARTICLES.on();
	}

	public static boolean background() {
		return BACKGROUND.on() && !DYNAMIC_FPS;
	}

	public static boolean uploads() {
		return UPLOADS.on() && !SODIUM;
	}

	public static void logCompatibility() {
		if (ENTITY_CULLING_MOD || IRIS) V1zir.LOGGER.info("Entity culling stands down: {} is installed", ENTITY_CULLING_MOD ? "Entity Culling" : "a shader mod");
		if (MORE_CULLING) V1zir.LOGGER.info("Distance culling stands down: More Culling is installed");
		if (DYNAMIC_FPS) V1zir.LOGGER.info("Background FPS limit stands down: Dynamic FPS is installed");
		if (SODIUM) V1zir.LOGGER.info("Chunk upload pacing stands down: Sodium renders the chunks");
	}

	/**
	 * Unlock frame rate, once (the client has started and loaded its options): VSync off, which holds the frame rate
	 * at the screen's refresh rate (60 on most), and Max Framerate at Unlimited. Nothing looks different. Afterwards the
	 * player's own choice of either setting stays.
	 */
	public static void unlockFrameRate(MinecraftClient client) {
		if (unlocked || !UNLOCK.on()) return;
		unlocked = true;
		GameOptions options = client.options;
		boolean changed = false;
		if (options.getEnableVsync().getValue()) {
			options.getEnableVsync().setValue(false);
			changed = true;
		}
		if (options.getMaxFps().getValue() < UNLIMITED) {
			options.getMaxFps().setValue(UNLIMITED);
			changed = true;
		}
		if (changed) {
			options.write();
			V1zir.LOGGER.info("Unlock frame rate: VSync off, Max Framerate Unlimited (once; change them in Video Settings)");
		}
		ConfigManager.save();
	}

	public static JsonObject toJson() {
		JsonObject json = new JsonObject();
		for (Setting<?> setting : SETTINGS) json.add(setting.name(), setting.toJson());
		json.addProperty("frameRateUnlocked", unlocked);
		return json;
	}

	public static void fromJson(JsonObject json) {
		for (Setting<?> setting : SETTINGS) {
			JsonElement value = json.get(setting.name());
			if (value != null) setting.fromJson(value);
		}
		JsonElement done = json.get("frameRateUnlocked");
		if (done != null && done.isJsonPrimitive()) unlocked = done.getAsBoolean();
	}
}
