package dev.v1zir.visuals.util;

import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.impl.client.ThemeModule;

/** Palette used by every UI element, derived from the Theme module. */
public final class Theme {
	public enum Preset {
		/** The default: warm amber into a soft deep orange over a neutral matte dark. */
		V1ZIR("V1zir", 0xFFF6A64B, 0xFFE8693F, 0xFF0F0F11),
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

	/** How panels are drawn. */
	public enum Style {
		NORMAL("Normal"),
		BLUR("Blur");

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
			return BLUR;
		}
	}

	/** Resolved once per settings change: every widget asks for these several times a frame. */
	private static ThemeModule module;
	private static String styleTitle;
	private static Style style = Style.BLUR;
	private static String presetTitle;
	private static Preset preset = Preset.V1ZIR;

	private Theme() {
	}

	public static Style style() {
		ThemeModule m = module();
		if (m == null) return Style.BLUR;
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
		return m == null ? 0.72f : m.panelOpacity.f();
	}

	/** Main panel colour (translucent, lies on top of the blurred world). */
	public static int panel() {
		return ColorUtil.withAlpha(preset().base(), Math.round(255 * panelOpacity()));
	}

	/** Opaque neutral grey of the Normal style. */
	public static int solidPanel() {
		return ColorUtil.withAlpha(ColorUtil.brighten(preset().base(), 0.075f), Math.round(255 * Math.max(0.93f, panelOpacity())));
	}

	/** Header strip / slightly raised surfaces. */
	public static int surface() {
		return ColorUtil.withAlpha(ColorUtil.brighten(preset().base(), 0.06f), Math.round(255 * Math.min(1f, panelOpacity() + 0.08f)));
	}

	/** Raised matte sub-surface (sidebar, cards): a step lighter than the panel, slightly see-through. */
	public static int raised() {
		return ColorUtil.withAlpha(ColorUtil.brighten(preset().base(), 0.1f), 120);
	}

	/** A raised surface that is lit: hovered, or an enabled module (a little of the accent mixes in). */
	public static int raisedLit() {
		return ColorUtil.withAlpha(ColorUtil.lerp(ColorUtil.brighten(preset().base(), 0.2f), accent(), 0.08f), 170);
	}

	/** Recessed well (tracks, fields) cut into a surface. */
	public static int well() {
		return 0x30000000;
	}

	/** Soft 1 px rim of panels and cards. */
	public static int outline() {
		return ColorUtil.withAlpha(0xFFFFFF, 24);
	}

	public static int text() {
		return 0xFFEDEFF5;
	}

	public static int textDim() {
		return 0xFF8D93A3;
	}

	public static int textFaint() {
		return 0xFF5C6170;
	}

	public static int shadow() {
		return 0x8C000000;
	}

	public static boolean textShadow() {
		ThemeModule m = module();
		return m == null || m.textShadow.on();
	}
}
