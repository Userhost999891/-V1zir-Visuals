package dev.v1zir.visuals.render.sky;

/**
 * Shader sky presets. The index must match the PRESET_* constants in sky_lib.glsl.
 * fogColor is the horizon colour used when "Sync fog" is on (0 = keep vanilla fog).
 */
public enum SkyPreset {
	REALISTIC("Realistic", 0, 0),
	MAGMA("Magma", 1, 0xFF050505),
	AURORA("Aurora", 2, 0xFF0A1820),
	NEBULA("Nebula", 3, 0xFF0B0814),
	SUNSET("Sunset", 4, 0xFFC9845A),
	INK("Ink", 5, 0xFF060606),
	SYNTHWAVE("Synthwave", 6, 0xFF5A1848),
	BLOOD_MOON("Blood Moon", 7, 0xFF1C0305),
	CUSTOM("Custom", 8, 0);

	private final String title;
	private final int index;
	private final int fogColor;

	SkyPreset(String title, int index, int fogColor) {
		this.title = title;
		this.index = index;
		this.fogColor = fogColor;
	}

	public String title() {
		return title;
	}

	public int index() {
		return index;
	}

	public int fogColor() {
		return fogColor;
	}

	/** Synthwave's grid scrolls too fast for the sky cache's refresh rate; it is cheap anyway. */
	public boolean cacheable() {
		return this != SYNTHWAVE;
	}

	public static String[] titles() {
		SkyPreset[] values = values();
		String[] titles = new String[values.length];
		for (int i = 0; i < values.length; i++) titles[i] = values[i].title;
		return titles;
	}

	public static SkyPreset byTitle(String title) {
		for (SkyPreset preset : values()) if (preset.title.equals(title)) return preset;
		return REALISTIC;
	}
}
