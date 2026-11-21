package dev.v1zir.visuals.module.impl.hud;

import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import net.minecraft.client.gui.DrawContext;

import java.util.Arrays;

/** Recent samples drawn as a row of slim bars, newest on the right. The scale glides to the peak. */
final class Sparkline {
	private final float[] values;
	private final Anim scale = new Anim(1f, 3f);
	private int next;

	Sparkline(int capacity) {
		values = new float[capacity];
	}

	void push(float value) {
		values[next] = value;
		next = (next + 1) % values.length;
	}

	void clear() {
		Arrays.fill(values, 0f);
	}

	/**
	 * Bars are coloured from low to high by their height. shift (0..1) slides them left by part of a
	 * slot, so a graph fed at a steady rate scrolls smoothly between samples; floor is the smallest
	 * scale, so small values stay small instead of being stretched to the full height.
	 */
	void draw(DrawContext ctx, float x, float y, float w, float h, float floor, float shift, int low, int high) {
		int n = values.length;
		float peak = floor;
		for (float v : values) peak = Math.max(peak, v);
		float top = scale.target(peak).get();
		float slot = w / n;
		float bar = Math.max(1f, slot * 0.62f);
		for (int i = 0; i < n; i++) {
			float v = values[(next + i) % n];
			if (v <= 0f) continue;
			float t = Math.min(1f, v / top);
			float bh = Math.max(1f, t * h);
			int color = ColorUtil.lerp(low, high, t);
			// The oldest bar fades out while it slides off the left edge.
			if (i == 0) color = ColorUtil.fade(color, 1f - shift);
			Render2D.gradientV(ctx, x + (i - shift) * slot + (slot - bar) / 2f, y + h - bh, bar, bh, bar / 2f,
				color, ColorUtil.fade(color, 0.35f));
		}
	}
}
