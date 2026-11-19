package dev.v1zir.visuals.module.impl.hud;

import dev.v1zir.visuals.V1zir;
import dev.v1zir.visuals.audio.AudioFiles;
import dev.v1zir.visuals.compat.voicechat.MusicFeed;
import dev.v1zir.visuals.gui.hud.Notifications;
import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.module.setting.KeySetting;
import dev.v1zir.visuals.module.setting.ModeSetting;
import dev.v1zir.visuals.module.setting.NumberSetting;
import dev.v1zir.visuals.render.font.Icons;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Voice Chat Music: music for the others on Simple Voice Chat, through your microphone, from your own files (MP3, OGG
 * or WAV in config/v1zir/music; Open music folder opens it): the one picked under File, or all of them one after
 * another, shuffled if you like. It is merged into what your microphone sends, also while you are not talking
 * (MusicFeed, VoiceChatBridge). You hear it yourself too, quieter, unless you turn that off. It needs Simple Voice
 * Chat and plays while voice chat is connected; keys can skip to the next track and pause it.
 */
public final class VoiceChatMusicModule extends Module {
	private static final String ALL = "All files";
	/** The file that plays, or all of the folder's one after another. */
	public final ModeSetting file = add(new ModeSetting("File", ALL, choices()));
	/** Turned on, it opens the music folder in the file manager and turns itself off again. */
	public final BoolSetting openFolder = add(new BoolSetting("Open music folder", false));
	public final NumberSetting volume = add(new NumberSetting("Volume", 0.5, 0.05, 1.0, 0.05));
	public final BoolSetting hear = add(new BoolSetting("Hear it yourself", true));
	public final NumberSetting hearVolume = add(new NumberSetting("Your volume", 0.35, 0.05, 1.0, 0.05).visibleIf(hear::on));
	public final BoolSetting shuffle = add(new BoolSetting("Shuffle", false));
	public final BoolSetting repeat = add(new BoolSetting("Repeat", true));
	public final KeySetting nextKey = add(new KeySetting("Next track", GLFW.GLFW_KEY_UNKNOWN));
	public final KeySetting pauseKey = add(new KeySetting("Pause", GLFW.GLFW_KEY_UNKNOWN));
	private boolean played;
	private static long folderStamp = Long.MIN_VALUE;
	private static int idleTicks;

	public VoiceChatMusicModule() {
		super("Voice Chat Music", "Plays music to the others on Simple Voice Chat through your microphone", Category.HUD);
		setSubCategory("Media");
	}

	/** Where the music goes. */
	public static Path folder() {
		return FabricLoader.getInstance().getConfigDir().resolve(V1zir.MOD_ID).resolve("music");
	}

	private static String[] choices() {
		List<String> names = new ArrayList<>();
		names.add(ALL);
		for (Path path : AudioFiles.list(folder())) names.add(path.getFileName().toString());
		return names.toArray(new String[0]);
	}

	/**
	 * Every client tick, on or off: the Open music folder button, and the files under File read again when the folder
	 * changed (now and then).
	 */
	public static void idleTick() {
		VoiceChatMusicModule module = ModuleManager.get(VoiceChatMusicModule.class);
		if (module == null) return;
		if (module.openFolder.on()) {
			module.openFolder.set(false);
			try {
				Files.createDirectories(folder());
			} catch (IOException e) {
				V1zir.LOGGER.warn("Could not create {}", folder(), e);
			}
			Util.getOperatingSystem().open(folder());
		}
		if (++idleTicks % 40 != 0) return;
		long stamp;
		try {
			stamp = Files.getLastModifiedTime(folder()).toMillis();
		} catch (IOException e) {
			stamp = -1L;
		}
		if (stamp == folderStamp) return;
		folderStamp = stamp;
		module.file.setModes(List.of(choices()));
	}

	@Override
	public String suffix() {
		if (!MusicFeed.running()) return null;
		if (MusicFeed.paused()) return "Paused";
		return MusicFeed.connected() ? MusicFeed.playing() : "Waiting for voice chat";
	}

	@Override
	protected void onEnable() {
		try {
			Files.createDirectories(folder());
		} catch (IOException e) {
			V1zir.LOGGER.warn("Could not create {}", folder(), e);
		}
		if (!FabricLoader.getInstance().isModLoaded("voicechat")) {
			Notifications.push(Icons.MIC_VOCAL, "Voice Chat Music", "Simple Voice Chat is not installed", false);
		}
		List<Path> files = AudioFiles.list(folder());
		if (!file.is(ALL)) {
			Path one = folder().resolve(file.get());
			files = Files.isRegularFile(one) ? List.of(one) : files;
		}
		if (files.isEmpty()) {
			Notifications.push(Icons.FOLDER, "Voice Chat Music", "Put MP3, OGG or WAV files in config/v1zir/music", false);
			Util.getOperatingSystem().open(folder());
			played = false;
			return;
		}
		volumes();
		MusicFeed.start(files, shuffle.on(), repeat.on());
		played = true;
	}

	@Override
	protected void onDisable() {
		MusicFeed.stop();
		played = false;
	}

	@Override
	public void onTick() {
		volumes();
		String started = MusicFeed.takeStarted();
		if (started != null) Notifications.push(Icons.MUSIC, "Now playing", started, true);
		// The playlist ran out (Repeat off) or none of its files could be read.
		if (played && !MusicFeed.running()) {
			played = false;
			setEnabled(false, false);
		}
	}

	private void volumes() {
		float master = mc.options.getSoundVolume(SoundCategory.MASTER);
		MusicFeed.volumes(volume.f(), hear.on() ? hearVolume.f() * master : 0f);
	}

	@Override
	public void onKeyPressed(int key) {
		if (nextKey.isSet() && key == nextKey.key()) {
			MusicFeed.next();
		} else if (pauseKey.isSet() && key == pauseKey.key()) {
			MusicFeed.togglePause();
			Notifications.push(MusicFeed.paused() ? Icons.PAUSE : Icons.PLAY, "Voice Chat Music", MusicFeed.paused() ? "Paused" : "Playing", true);
		}
	}
}
