package dev.v1zir.visuals.render.wallpaper;

import dev.v1zir.visuals.render.sky.SkyPreset;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Locale;

/**
 * One wallpaper of the title screen: a dedicated wallpaper shader (wallpaper.fsh), a sky preset seen from the menu's
 * drifting camera, or a picture (PNG, JPEG or animated GIF) from the wallpapers folder. The id is what the config stores.
 */
public final class Wallpaper {
	public enum Kind {
		SHADER, SKY, IMAGE
	}

	public final Kind kind;
	public final String id;
	private final String name;
	private final String polishName;
	/** SHADER: the index wallpaper.fsh switches on. */
	public final int shader;
	@Nullable
	public final SkyPreset preset;
	@Nullable
	public final Path file;
	private final boolean gif;

	private Wallpaper(Kind kind, String id, String name, String polishName, int shader, @Nullable SkyPreset preset, @Nullable Path file) {
		this.kind = kind;
		this.id = id;
		this.name = name;
		this.polishName = polishName;
		this.shader = shader;
		this.preset = preset;
		this.file = file;
		gif = file != null && file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".gif");
	}

	static Wallpaper shader(String key, String name, String polishName, int index) {
		return new Wallpaper(Kind.SHADER, "live:" + key, name, polishName, index, null, null);
	}

	static Wallpaper sky(SkyPreset preset) {
		return new Wallpaper(Kind.SKY, "sky:" + preset.name().toLowerCase(Locale.ROOT), preset.title(), preset.title(), -1, preset, null);
	}

	static Wallpaper image(Path file) {
		String fileName = file.getFileName().toString();
		int dot = fileName.lastIndexOf('.');
		String name = dot > 0 ? fileName.substring(0, dot) : fileName;
		return new Wallpaper(Kind.IMAGE, "image:" + fileName, name, name, -1, null, file);
	}

	public String name(boolean polish) {
		return polish ? polishName : name;
	}

	/** An animated picture (its frames are small already, and cards share them). */
	public boolean gif() {
		return gif;
	}
}
