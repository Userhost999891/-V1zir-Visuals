package dev.v1zir.visuals.gui.clickgui;

import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.gui.DrawContext;

/** Spring-smoothed vertical scrolling of one area, with a slim bar that shows while it moves or is hovered. */
final class Scroller {
	private static final float STEP = 26f;
	private final Anim offset = new Anim(0f, 15f);
	private final Anim bar = new Anim(0f, 10f);
	private float target;
	private float max;

	void scroll(double amount) {
		target = Math.max(0f, Math.min(max, target - (float) amount * STEP));
	}

	void reset() {
		target = 0f;
		offset.snap(0f);
	}

	/** Sets the content and viewport heights of this frame; returns the smoothed offset. */
	float update(float content, float viewport) {
		max = Math.max(0f, content - viewport);
		target = Math.max(0f, Math.min(max, target));
		return offset.target(target).get();
	}

	/** Thin thumb along the right edge; only drawn when there is something to scroll. */
	void drawBar(DrawContext ctx, float x, float y, float viewport, float content, boolean hovered) {
		float moving = Math.abs(offset.value() - target) > 0.5f ? 1f : 0f;
		float show = bar.target(max > 0f ? Math.max(moving, hovered ? 0.6f : 0f) : 0f).get();
		if (show < 0.01f) return;
		float track = viewport - 8f;
		float thumb = Math.max(16f, track * viewport / content);
		float ty = y + 4f + (track - thumb) * (offset.value() / Math.max(1f, max));
		Render2D.round(ctx, x, ty, 2f, thumb, 1f, ColorUtil.withAlpha(ColorUtil.lerp(Theme.accent(), 0xFFFFFFFF, 0.4f), Math.round(150 * show)));
	}
}
