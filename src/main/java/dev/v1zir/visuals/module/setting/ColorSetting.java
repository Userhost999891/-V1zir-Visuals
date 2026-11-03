package dev.v1zir.visuals.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.v1zir.visuals.util.ColorUtil;

/** ARGB colour with an optional hue cycling ("rainbow") flag. */
public class ColorSetting extends Setting<Integer> {
	private final boolean alpha;
	private boolean rainbow;

	public ColorSetting(String name, int defaultArgb) {
		this(name, defaultArgb, true);
	}

	public ColorSetting(String name, int defaultArgb, boolean alpha) {
		super(name, defaultArgb);
		this.alpha = alpha;
	}

	public boolean hasAlpha() {
		return alpha;
	}

	public boolean rainbow() {
		return rainbow;
	}

	public void setRainbow(boolean rainbow) {
		this.rainbow = rainbow;
	}

	/** Current colour, with the hue animated when rainbow is enabled. */
	public int argb() {
		if (!rainbow) return value;
		float[] hsv = ColorUtil.toHsv(value);
		float hue = (float) ((System.currentTimeMillis() % 6000L) / 6000.0);
		return ColorUtil.fromHsv(hue, Math.max(hsv[1], 0.55f), Math.max(hsv[2], 0.85f), ColorUtil.alpha(value));
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(String.format("#%08X", value) + (rainbow ? ":rainbow" : ""));
	}

	@Override
	public void fromJson(JsonElement element) {
		if (!element.isJsonPrimitive()) return;
		String s = element.getAsString();
		rainbow = s.endsWith(":rainbow");
		if (rainbow) s = s.substring(0, s.length() - ":rainbow".length());
		try {
			set((int) Long.parseLong(s.replace("#", ""), 16));
		} catch (NumberFormatException ignored) {
		}
	}
}
