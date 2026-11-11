package dev.v1zir.visuals.module.impl.hud;

import dev.v1zir.visuals.render.font.SdfFont;

/**
 * A number shown by a HUD element. The string is built only when the shown value changes and
 * measured only when the string, font or size changes, so drawing it every frame allocates nothing.
 */
final class NumberText {
	private static final String[] SMALL = new String[1000];
	private static final int[] SCALE = {1, 10, 100};

	private final int decimals;
	private final String suffix;
	private long shown = Long.MIN_VALUE;
	private String text = "";
	private SdfFont font;
	private float size;
	private float width;

	NumberText(int decimals, String suffix) {
		this.decimals = decimals;
		this.suffix = suffix;
	}

	/** Small non-negative integers as text, built once per value for the whole session. */
	static String of(int n) {
		if (n < 0 || n >= SMALL.length) return Integer.toString(n);
		String s = SMALL[n];
		if (s == null) SMALL[n] = s = Integer.toString(n);
		return s;
	}

	/** Sets the value and returns its text. */
	String set(double value) {
		long scaled = Math.round(value * SCALE[decimals]);
		if (scaled != shown) {
			shown = scaled;
			text = format(scaled);
			font = null;
		}
		return text;
	}

	String text() {
		return text;
	}

	float width(SdfFont font, float size) {
		if (font != this.font || size != this.size) {
			this.font = font;
			this.size = size;
			width = font.width(text, size);
		}
		return width;
	}

	private String format(long scaled) {
		StringBuilder sb = new StringBuilder(12);
		if (scaled < 0) sb.append('-');
		long abs = Math.abs(scaled);
		sb.append(abs / SCALE[decimals]);
		if (decimals > 0) {
			String fraction = Long.toString(abs % SCALE[decimals]);
			sb.append('.');
			for (int i = fraction.length(); i < decimals; i++) sb.append('0');
			sb.append(fraction);
		}
		return sb.append(suffix).toString();
	}
}
