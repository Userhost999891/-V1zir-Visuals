package dev.v1zir.visuals.util;

import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.impl.client.ThemeModule;

/** Palette used by every UI element, derived from the Theme module. */
public final class Theme {
	public enum Preset {
		/** The default: warm amber into a soft deep orange over a neutral matte dark. */
		V1ZIR("Aesthetic", 0xFFF6A64B, 0xFFE8693F, 0xFF0F0F11),
		MIDNIGHT("Midnight", 0xFF5B8CFF, 0xFF8F6BFF, 0xFF0B0E17),
		NEVERLOSE("Neverlose", 0xFF00A6FF, 0xFF00E1FF, 0xFF070B12),
		FATALITY("Fatality", 0xFFE5175F, 0xFF8A2BE2, 0xFF120A14),
		SKEET("Skeet", 0xFF9DDB3A, 0xFF4CB79A, 0xFF0D0F0D),
		PRIMO("Primo", 0xFFFF6A3D, 0xFFFFB347, 0xFF130D0B),
		FEMBOY("Femboy", 0xFFFF8DC7, 0xFFB7A6FF, 0xFF140E15),
		MONO("Mono", 0xFFFFFFFF, 0xFF9A9A9A, 0xFF0B0B0B),
		CATLEAN_GRAY("Catlean Gray", 0xFFBFC6D4, 0xFF8792A6, 0xFF12141A),
		CUSTOM("Custom", 0, 0, 0xFF0D0E14);

		private final String title;
		private final int accent;
		private final int accent2;
		private final int base;

		Preset(String title, int accent, int accent2, int base) {
			this.title = title;
			this.accent = accent;
			this.accent2 = accent2;
			this.base = base;
		}

		public String title() {
			return title;
		}

		public int accent() {
			return accent;
		}

		public int accent2() {
			return accent2;
		}

		public int base() {
			return base;
		}

		public static String[] titles() {
			Preset[] values = values();
			String[] titles = new String[values.length];
			for (int i = 0; i < values.length; i++) titles[i] = values[i].title;
			return titles;
		}

		public static Preset byTitle(String title) {
			for (Preset preset : values()) if (preset.title.equals(title)) return preset;
			return V1ZIR;
		}
	}

	/** How panels are drawn: solid black, or clear glass that bends the light at its edges (ShapeElement.FROST). */
	public enum Style {
		NORMAL("Normal"),
		GLASS("Glass");

		private final String title;

		Style(String title) {
			this.title = title;
		}

		public String title() {
			return title;
		}

		public static String[] titles() {
			Style[] values = values();
			String[] titles = new String[values.length];
			for (int i = 0; i < values.length; i++) titles[i] = values[i].title;
			return titles;
		}

		public static Style byTitle(String title) {
			for (Style style : values()) if (style.title.equals(title)) return style;
			// Glass took the place of the Blur, Matte glass and Glassmorphism styles.
			return "Blur".equals(title) || "Matte glass".equals(title) || "Glassmorphism".equals(title) ? GLASS : NORMAL;
		}
	}

	/** Resolved once per settings change: every widget asks for these several times a frame. */
	private static ThemeModule module;
	private static String styleTitle;
	private static Style style = Style.NORMAL;
	private static String presetTitle;
	private static Preset preset = Preset.V1ZIR;

	private Theme() {
	}

	public static Style style() {
		ThemeModule m = module();
		if (m == null) return Style.NORMAL;
		String title = m.style.get();
		if (title != styleTitle) {
			styleTitle = title;
			style = Style.byTitle(title);
		}
		return style;
	}

	private static ThemeModule module() {
		if (module == null) module = ModuleManager.get(ThemeModule.class);
		return module;
	}

	private static String styleThemeTitle;
	private static boolean ps5;

	/** Whether the PS5 style theme is on: the menus and screens take the look of a console's home screen. */
	public static boolean ps5() {
		ThemeModule m = module();
		if (m == null) return false;
		String title = m.styleTheme.get();
		if (title != styleThemeTitle) {
			styleThemeTitle = title;
			ps5 = "PS5".equals(title);
		}
		return ps5;
	}

	public static Preset preset() {
		ThemeModule m = module();
		if (m == null) return Preset.V1ZIR;
		String title = m.preset.get();
		if (title != presetTitle) {
			presetTitle = title;
			preset = Preset.byTitle(title);
		}
		return preset;
	}

	public static int accent() {
		Preset p = preset();
		ThemeModule m = module();
		return p == Preset.CUSTOM && m != null ? m.accent.argb() : p.accent();
	}

	public static int accent2() {
		Preset p = preset();
		ThemeModule m = module();
		return p == Preset.CUSTOM && m != null ? m.accent2.argb() : p.accent2();
	}

	/** Animated mix of both accents; offset lets neighbouring elements wave. */
	public static int accentWave(float offset) {
		return ColorUtil.wave(accent(), accent2(), offset, 0.25f);
	}

	public static float panelOpacity() {
		ThemeModule m = module();
		return m == null ? 0.85f : m.panelOpacity.f();
	}

	/** The black the panels are made of: near black, with a breath of the preset's own base in it. */
	private static int black() {
		return ColorUtil.lerp(0xFF030304, preset().base(), 0.3f);
	}

	/** A dark panel colour for plain backgrounds (the array list's rows in the Normal style). */
	public static int panel() {
		return ColorUtil.withAlpha(black(), Math.round(255 * Math.max(0.72f, panelOpacity())));
	}

	/**
	 * What the glass of the Glass style takes from the light passing through it: hardly anything, as clear glass does
	 * (Panel opacity sets it: 0.11 at the default 0.85, none at the lowest).
	 */
	public static int glassTint() {
		return ColorUtil.withAlpha(black(), Math.round(255 * Math.max(0f, (panelOpacity() - 0.3f) * 0.2f)));
	}

	/** Whether panels are glass (the Glass style): their cards and rims are drawn lighter on it. */
	private static boolean glass() {
		return style() == Style.GLASS;
	}

	/** The Normal style's panel: solid black. */
	public static int solidPanel() {
		return ColorUtil.withAlpha(black(), Math.round(255 * Math.max(0.96f, panelOpacity())));
	}

	/** Header strip / slightly raised surfaces. */
	public static int surface() {
		return ColorUtil.withAlpha(ColorUtil.brighten(black(), 0.03f), Math.round(255 * Math.max(0.9f, panelOpacity())));
	}

	/** Raised sub-surface (sidebar, cards): a breath lighter than the black under it; on glass, a lighter pane. */
	public static int raised() {
		return glass() ? 0x16FFFFFF : 0x09FFFFFF;
	}

	/** A raised surface that is lit: hovered, or an enabled module (a trace of the accent mixes in). */
	public static int raisedLit() {
		return ColorUtil.withAlpha(ColorUtil.lerp(0xFFFFFFFF, accent(), 0.12f), glass() ? 34 : 16);
	}

	/** Recessed well (tracks, fields) cut into a surface. */
	public static int well() {
		return 0x40000000;
	}

	/** Soft 1 px rim of panels and cards; brighter on glass, whose edges catch the light. */
	public static int outline() {
		return ColorUtil.withAlpha(0xFFFFFF, glass() ? 40 : 18);
	}

	public static int text() {
		return 0xFFEDEDEF;
	}

	public static int textDim() {
		return 0xFF9A9BA1;
	}

	public static int textFaint() {
		return 0xFF6C6D73;
	}

	public static int shadow() {
		return 0xA0000000;
	}

	public static boolean textShadow() {
		ThemeModule m = module();
		return m == null || m.textShadow.on();
	}
}
