package dev.v1zir.visuals.gui.menu;

import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import net.minecraft.client.gui.DrawContext;

/**
 * Press and hold on an empty spot of the title screen to customise its wallpaper. After a short delay (so ordinary
 * clicks show nothing) a thin ring fills around the cursor; moving the mouse cancels it. When it completes the ring
 * swells and fades like a haptic tap.
 */
final class LongPress {
	private static final float DELAY = 0.3f;
	private static final float DURATION = 3f;
	/** Movement in GUI pixels that turns the press into something else (a drag). */
	private static final float SLOP = 6f;
	private static final float RADIUS = 11f;

	private boolean held;
	private double x;
	private double y;
	private long start;
	/** The completion pulse: 0 at the moment it completed, 1 when it has faded. */
	private final Anim pulse = new Anim(1f, 5f);
	private float pulseX;
	private float pulseY;
	/** How far the ring was filled when it was let go; it shrinks away from there. */
	private final Anim release = new Anim(0f, 14f);

	void start(double x, double y) {
		held = true;
		this.x = x;
		this.y = y;
		start = System.nanoTime();
	}

	void cancel() {
		if (!held) return;
		held = false;
		release.snap(progress(System.nanoTime()));
		release.target(0f);
	}

	private float progress(long now) {
		return Anim.clamp01(((now - start) / 1.0e9f - DELAY) / (DURATION - DELAY));
	}

	/** Follows the cursor once per frame; true on the frame the hold completes. */
	boolean update(double mouseX, double mouseY) {
		if (!held) return false;
		double dx = mouseX - x;
		double dy = mouseY - y;
		if (dx * dx + dy * dy > SLOP * SLOP) {
			cancel();
			return false;
		}
		if (progress(System.nanoTime()) < 1f) return false;
		held = false;
		release.snap(0f);
		pulseX = (float) x;
		pulseY = (float) y;
		pulse.snap(0f);
		pulse.target(1f);
		return true;
	}

	void render(DrawContext ctx) {
		float p = pulse.get();
		if (p < 1f) {
			// The full ring swells a little and fades: the hold took.
			float r = RADIUS * (1f + 0.45f * p);
			int alpha = Math.round(230 * (1f - p));
			Render2D.shadow(ctx, pulseX - r, pulseY - r, r * 2f, r * 2f, r, r * 0.8f, ColorUtil.withAlpha(0x000000, alpha / 3));
			Render2D.ring(ctx, pulseX, pulseY, r, 2f, ColorUtil.withAlpha(0xFFFFFF, alpha));
		}
		float fill;
		float appear;
		if (held) {
			long now = System.nanoTime();
			fill = progress(now);
			appear = Anim.clamp01(((now - start) / 1.0e9f - DELAY) / 0.15f);
		} else {
			fill = release.get();
			appear = fill > 0f ? 1f : 0f;
		}
		if (appear <= 0f) return;
		float cx = (float) x;
		float cy = (float) y;
		float r = RADIUS * (0.85f + 0.15f * appear);
		Render2D.shadow(ctx, cx - r, cy - r, r * 2f, r * 2f, r, r * 0.7f, ColorUtil.withAlpha(0x000000, Math.round(70 * appear)));
		Render2D.ring(ctx, cx, cy, r, 2f, ColorUtil.withAlpha(0xFFFFFF, Math.round(60 * appear)));
		Render2D.arc(ctx, cx, cy, r, 2f, fill, ColorUtil.withAlpha(0xFFFFFF, Math.round(235 * appear)));
	}
}
