package dev.v1zir.visuals.module.impl.effects;

import dev.v1zir.visuals.V1zir;
import dev.v1zir.visuals.audio.AlPlayer;
import dev.v1zir.visuals.audio.AudioFiles;
import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.module.setting.ModeSetting;
import dev.v1zir.visuals.module.setting.NumberSetting;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A sound on every hit you land: one of the client's own, made for it (Bubble, a drop of water, by default; Pop, Click,
 * Ding, Crit, Thump, Chime, Zap), or a file of your own from config/v1zir/hitsounds (MP3, OGG or WAV), picked under File.
 * The client's own play through the game's sounds (assets/v1zir/sounds.json); your own straight through the audio
 * device, at the game's master volume.
 */
public final class HitSoundModule extends Module {
	private static final String NONE = "None";
	private static final String CUSTOM = "Custom";

	public final ModeSetting sound = add(new ModeSetting("Sound", "Bubble", "Bubble", "Pop", "Click", "Ding", "Crit", "Thump", "Chime", "Zap", CUSTOM));
	public final ModeSetting file = add(new ModeSetting("File", NONE, files()).visibleIf(() -> sound.is(CUSTOM)));
	public final NumberSetting volume = add(new NumberSetting("Volume", 0.8, 0.05, 1.0, 0.05));
	public final NumberSetting pitch = add(new NumberSetting("Pitch", 1.0, 0.5, 2.0, 0.05).suffix("x"));
	public final BoolSetting vary = add(new BoolSetting("Vary pitch", true));
	/** When the folder last changed, as last seen; the list of files is read again when it changes. */
	private long folderStamp = Long.MIN_VALUE;
	private int ticks;

	public HitSoundModule() {
		super("Hit Sound", "A sound on every hit: Bubble, Pop, Click... or your own file", Category.COMBAT);
		setSubCategory("Hits");
	}

	@Override
	public String suffix() {
		return sound.is(CUSTOM) ? file.get() : sound.get();
	}

	/** Where your own sounds go. */
	public static Path folder() {
		return FabricLoader.getInstance().getConfigDir().resolve(V1zir.MOD_ID).resolve("hitsounds");
	}

	private static String[] files() {
		List<String> names = new ArrayList<>();
		names.add(NONE);
		for (Path file : AudioFiles.list(folder())) names.add(file.getFileName().toString());
		return names.toArray(new String[0]);
	}

	@Override
	protected void onEnable() {
		try {
			Files.createDirectories(folder());
		} catch (IOException e) {
			V1zir.LOGGER.warn("Could not create {}", folder(), e);
		}
		refresh();
	}

	@Override
	public void onTick() {
		// Files added to the folder show up under File while Custom is picked.
		if (sound.is(CUSTOM) && ++ticks % 40 == 0) refresh();
	}

	private void refresh() {
		long stamp;
		try {
			stamp = Files.getLastModifiedTime(folder()).toMillis();
		} catch (IOException e) {
			stamp = -1L;
		}
		if (stamp == folderStamp) return;
		folderStamp = stamp;
		file.setModes(List.of(files()));
	}

	/** You hit something (Effects). */
	public void onHit() {
		float p = pitch.f();
		if (vary.on()) p *= 0.94f + 0.12f * ThreadLocalRandom.current().nextFloat();
		if (sound.is(CUSTOM)) {
			if (file.is(NONE)) return;
			Path path = folder().resolve(file.get());
			long stamp;
			try {
				stamp = Files.getLastModifiedTime(path).toMillis();
			} catch (IOException e) {
				return;
			}
			float master = mc.options.getSoundVolume(SoundCategory.MASTER);
			// Keyed by the file's time too, so an edited file is read again.
			AlPlayer.play(path + "@" + stamp, () -> AudioFiles.decode(path), volume.f() * master, p);
			return;
		}
		SoundEvent event = SoundEvent.of(V1zir.id("hit." + sound.get().toLowerCase(Locale.ROOT)));
		mc.getSoundManager().play(PositionedSoundInstance.master(event, p, volume.f()));
	}
}
