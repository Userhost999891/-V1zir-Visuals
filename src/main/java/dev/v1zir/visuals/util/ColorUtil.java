package dev.v1zir.visuals.util;

public final class ColorUtil {
	private ColorUtil() {
	}

	public static int alpha(int argb) {
		return argb >>> 24;
	}

	public static int red(int argb) {
		return argb >> 16 & 0xFF;
	}

	public static int green(int argb) {
		return argb >> 8 & 0xFF;
	}

	public static int blue(int argb) {
		return argb & 0xFF;
	}

	public static int argb(int a, int r, int g, int b) {
		return (clamp(a) << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
	}

	public static int rgb(int rgb) {
		return 0xFF000000 | rgb;
	}

	private static int clamp(int v) {
		return Math.max(0, Math.min(255, v));
	}

	/** Replaces the alpha channel. */
	public static int withAlpha(int argb, int alpha) {
		return (clamp(alpha) << 24) | (argb & 0xFFFFFF);
	}

	/** Multiplies the alpha channel by a factor (used for fades). */
	public static int fade(int argb, float factor) {
		return withAlpha(argb, Math.round(alpha(argb) * Math.max(0f, Math.min(1f, factor))));
	}

	public static int lerp(int from, int to, float t) {
		t = Math.max(0f, Math.min(1f, t));
		return argb(
			Math.round(alpha(from) + (alpha(to) - alpha(from)) * t),
			Math.round(red(from) + (red(to) - red(from)) * t),
			Math.round(green(from) + (green(to) - green(from)) * t),
			Math.round(blue(from) + (blue(to) - blue(from)) * t));
	}

	public static int brighten(int argb, float amount) {
		return lerp(argb, withAlpha(0xFFFFFF, alpha(argb)), amount);
	}

	public static int darken(int argb, float amount) {
		return lerp(argb, withAlpha(0x000000, alpha(argb)), amount);
	}

	public static float[] toHsv(int argb) {
		float r = red(argb) / 255f;
		float g = green(argb) / 255f;
		float b = blue(argb) / 255f;
		float max = Math.max(r, Math.max(g, b));
		float min = Math.min(r, Math.min(g, b));
		float delta = max - min;
		float h = 0;
		if (delta > 1e-5f) {
			if (max == r) h = ((g - b) / delta) % 6f;
			else if (max == g) h = (b - r) / delta + 2f;
			else h = (r - g) / delta + 4f;
			h /= 6f;
			if (h < 0) h += 1f;
		}
		float s = max <= 1e-5f ? 0 : delta / max;
		return new float[]{h, s, max};
	}

	public static int fromHsv(float h, float s, float v, int alpha) {
		h = (h % 1f + 1f) % 1f;
		float c = v * s;
		float x = c * (1 - Math.abs((h * 6f) % 2f - 1));
		float m = v - c;
		float r, g, b;
		int sector = (int) (h * 6f);
		switch (sector) {
			case 0 -> { r = c; g = x; b = 0; }
			case 1 -> { r = x; g = c; b = 0; }
			case 2 -> { r = 0; g = c; b = x; }
			case 3 -> { r = 0; g = x; b = c; }
			case 4 -> { r = x; g = 0; b = c; }
			default -> { r = c; g = 0; b = x; }
		}
		return argb(alpha, Math.round((r + m) * 255), Math.round((g + m) * 255), Math.round((b + m) * 255));
	}

	/** Colour of a two stop gradient that scrolls with time - used for accents everywhere. */
	public static int wave(int a, int b, float offset, float speed) {
		double t = (System.currentTimeMillis() / 1000.0) * speed + offset;
		float k = (float) (Math.sin(t * Math.PI * 2) * 0.5 + 0.5);
		return lerp(a, b, k);
	}

	public static float[] rgbFloats(int argb) {
		return new float[]{red(argb) / 255f, green(argb) / 255f, blue(argb) / 255f, alpha(argb) / 255f};
	}
}
