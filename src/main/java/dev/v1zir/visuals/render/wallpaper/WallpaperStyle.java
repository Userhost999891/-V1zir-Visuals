package dev.v1zir.visuals.render.wallpaper;

import com.google.gson.JsonObject;

/** Adjustments of a wallpaper on one screen: blur and dim, and for pictures how they fit and whether they drift. */
public final class WallpaperStyle {
	/** 0..1, a gaussian of up to about 2% of the screen height. */
	public float blur;
	/** 0..1, how much darker the wallpaper is drawn. */
	public float dim;
	/** Pictures: the whole picture fits inside the screen instead of filling it. */
	public boolean fit;
	/** Pictures: the slow Ken Burns zoom and pan. */
	public boolean motion = true;

	public WallpaperStyle() {
	}

	public WallpaperStyle(float blur, float dim) {
		this.blur = blur;
		this.dim = dim;
	}

	public WallpaperStyle set(WallpaperStyle other) {
		blur = other.blur;
		dim = other.dim;
		fit = other.fit;
		motion = other.motion;
		return this;
	}

	public WallpaperStyle copy() {
		return new WallpaperStyle().set(this);
	}

	public boolean same(WallpaperStyle other) {
		return blur == other.blur && dim == other.dim && fit == other.fit && motion == other.motion;
	}

	/** Opaque grey that draws the wallpaper at this brightness (0..255) and the dim on top; even fully dimmed a hint stays. */
	public int tint(int level) {
		int v = Math.round(level * (1f - 0.85f * dim));
		return 0xFF000000 | v << 16 | v << 8 | v;
	}

	JsonObject toJson() {
		JsonObject json = new JsonObject();
		json.addProperty("blur", blur);
		json.addProperty("dim", dim);
		json.addProperty("fit", fit);
		json.addProperty("motion", motion);
		return json;
	}

	static WallpaperStyle fromJson(JsonObject json) {
		WallpaperStyle style = new WallpaperStyle();
		if (json.has("blur")) style.blur = clamp(json.get("blur").getAsFloat());
		if (json.has("dim")) style.dim = clamp(json.get("dim").getAsFloat());
		if (json.has("fit")) style.fit = json.get("fit").getAsBoolean();
		if (json.has("motion")) style.motion = json.get("motion").getAsBoolean();
		return style;
	}

	private static float clamp(float value) {
		return Math.max(0f, Math.min(1f, value));
	}
}
