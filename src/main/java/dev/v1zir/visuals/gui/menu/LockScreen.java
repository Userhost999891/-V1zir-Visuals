package dev.v1zir.visuals.gui.menu;

import dev.v1zir.visuals.render.BackdropBlur;
import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.font.SdfFont;
import dev.v1zir.visuals.render.ui.Logo;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.render.ui.ShapeElement;
import dev.v1zir.visuals.util.Anim;
import net.minecraft.client.gui.DrawContext;

/**
 * Phone-style lock screen the title screen opens on: a large clock with the date and a greeting over the blurred,
 * dimmed menu background, and a shimmering "swipe up to open" hint above a handle. It is a sheet: dragged up it
 * follows the cursor with some resistance, and let go past a threshold (or flicked) it slides away with a spring
 * while the main menu comes in; let go early and it springs back. Scrolling, any key or a click on the hint open it too.
 */
public final class LockScreen {
	/** Fraction of the screen the sheet must be dragged past for letting go to open it. */
	private static final float THRESHOLD = 0.22f;
	/** Upward speed (screen heights per second) at which letting go opens it from anywhere. */
	private static final float FLICK = 1.1f;
	/** Bottom part of the screen (the hint and the handle) where a click opens it. */
	private static final float HINT_AREA = 0.8f;
	private static final Render2D.TextStyle CLOCK = new Render2D.TextStyle().weight(0f);
	private static final Render2D.TextStyle SHINE = new Render2D.TextStyle().weight(0f);
	private static final Render2D.TextStyle SHIMMER = new Render2D.TextStyle();

	/** Locked on every game start and, when set, after leaving a world, until the player opens it. */
	private static boolean locked = true;

	/** 0 while the sheet covers the menu, 1 once it is gone; slightly negative while it is pulled down. */
	private final Anim open = new Anim(locked ? 0f : 1f, 5.5f);
	private float progress;
	private boolean dragging;
	private boolean moved;
	private double pressY;
	/** Last progress and time the drag velocity was sampled at, and the smoothed velocity. */
	private float sampleProgress;
	private long sampleTime;
	private float velocity;

	/** Shows the lock screen again the next time the title screen opens. */
	public static void lock() {
		locked = true;
	}

	/** Advances the slide once per frame; returns how far the sheet has gone (0 covering, 1 gone). */
	float update(boolean enabled) {
		if (!enabled && locked) {
			locked = false;
			dragging = false;
			open.snap(1f);
		}
		progress = open.get();
		return progress;
	}

	/** While locked the sheet takes all input, also while it springs back after an early release. */
	boolean locked() {
		return locked;
	}

	boolean visible() {
		return locked || progress < 0.999f;
	}

	void unlock() {
		if (!locked) return;
		locked = false;
		dragging = false;
		open.target(1f).fling(4f);
	}

	void press(double y, int height) {
		dragging = true;
		moved = false;
		// Caught while it springs back, the sheet carries on from where it is instead of jumping (inverse rubber band).
		float p = Math.max(0f, Math.min(progress, 0.8f));
		pressY = y - Math.log(1f - 1.2f * p) / 1.2f * height;
		sampleProgress = progress;
		sampleTime = System.nanoTime();
		velocity = 0f;
	}

	/** Moves the sheet with the cursor; called by drag events and every frame while the button is held. */
	void drag(double y, int height) {
		if (!dragging) return;
		double dy = pressY - y;
		if (!moved && Math.abs(dy) < 3.0) return;
		moved = true;
		float up = (float) (dy / height);
		// Rubber band: upwards the sheet falls behind the cursor the further it goes, downwards it barely gives.
		float p = up >= 0f ? (1f - (float) Math.exp(-1.2f * up)) / 1.2f : -0.06f * (1f - (float) Math.exp(4f * up));
		long now = System.nanoTime();
		float dt = (now - sampleTime) / 1.0e9f;
		if (dt >= 0.008f) {
			velocity = velocity * 0.3f + (p - sampleProgress) / dt * 0.7f;
			sampleProgress = p;
			sampleTime = now;
		}
		open.snap(p);
		progress = p;
	}

	void release(double y, int height) {
		if (!dragging) return;
		dragging = false;
		if (!moved) {
			// A click on the hint opens it; anywhere else the sheet hops up a little to show that it moves.
			if (y > height * HINT_AREA) unlock();
			else open.fling(1.1f);
			return;
		}
		// A cursor that stopped before letting go throws nothing.
		if (System.nanoTime() - sampleTime > 90_000_000L) velocity = 0f;
		if (progress > THRESHOLD || velocity > FLICK && progress > 0.02f) {
			locked = false;
			open.target(1f).fling(Math.max(velocity, 1f));
		} else {
			open.target(0f).fling(velocity);
		}
	}

	/** The sheet: the menu background blurred and dimmed, with rounded bottom corners and a shadow while it slides. */
	void renderSheet(DrawContext ctx, int width, int height) {
		float y = -progress * height;
		float radius = Anim.clamp01(Math.abs(progress) * 12f) * 16f;
		if (progress > 0.001f) Render2D.shadow(ctx, 0f, y + height - 40f, width, 40f, radius, 24f, 0x70000000);
		if (BackdropBlur.available()) {
			Render2D.glass(ctx, 0f, y, width, height, radius, Render2D.CORNERS_BOTTOM, ShapeElement.MATTE, 0x5C000000);
		} else {
			Render2D.round(ctx, 0f, y, width, height, radius, Render2D.CORNERS_BOTTOM, 0xA6000000);
		}
	}

	/** Logo, greeting, clock, date and hint; t is the intro time and time the seconds since the screen opened. */
	void renderContent(DrawContext ctx, int width, int height, float t, float time, MenuClock clock) {
		float appear = Anim.easeOutQuart(Anim.progress(t, 0.25f, 0.9f));
		float alpha = appear * Anim.clamp01(1f - Math.max(progress, 0f) * 1.6f);
		if (alpha <= 0.004f) return;
		float lift = -progress * height + (1f - appear) * 10f;
		float cx = width / 2f;

		float logo = Math.max(12f, Math.min(20f, height * 0.05f));
		Render2D.setAlpha(alpha * 0.8f);
		Logo.draw(ctx, cx - logo / 2f, height * 0.06f + lift, logo);
		Render2D.setAlpha(alpha);

		// Clock: light tabular digits, optically centred on their own height rather than the line box.
		float size = Math.min(height * 0.2f, width * 0.15f);
		float digits = MenuClock.digitHeight(size);
		float baseline = height * 0.36f + digits / 2f + lift;
		CLOCK.color = 0xD9FFFFFF;
		float x = clock.drawTime(ctx, cx, baseline, size, CLOCK);
		renderShine(ctx, clock.time, x, baseline, size, time);

		if (!clock.greeting.isEmpty()) {
			float gs = Math.max(7f, Math.min(12f, height * 0.034f));
			clock.drawGreeting(ctx, cx, baseline - digits - gs * 1.9f, gs, 0xE6FFFFFF);
		}

		float ds = Math.max(7.5f, Math.min(14f, height * 0.04f));
		Render2D.textCentered(ctx, Fonts.medium(), clock.date, cx, baseline + size * 0.16f, ds, 0xD9FFFFFF);

		renderHint(ctx, width, height, t, time, clock.hint, lift);
		Render2D.setAlpha(1f);
	}

	/** A soft band of light sweeps across the digits once they have appeared, then every ten seconds. */
	private static void renderShine(DrawContext ctx, String text, float x, float baseline, float size, float time) {
		float phase = (time + 8.8f) % 10f;
		if (time < 1.2f || phase > 1.4f) return;
		SdfFont font = Fonts.clock();
		float w = font.width(text, size);
		float band = size * 0.75f;
		float bx = x - band + (w + band * 2f) * Anim.easeInOutSine(phase / 1.4f);
		SHINE.color = 0xFFFFFFFF;
		Render2D.text(ctx, font, text, x, baseline - font.ascent(size), size, SHINE.fadeEdges(bx - band, bx + band, band));
	}

	/** "Swipe up to open" with a light band sweeping across it, above the handle; both fade as soon as the sheet moves. */
	private void renderHint(DrawContext ctx, int width, int height, float t, float time, String hint, float lift) {
		float a = Anim.easeOutCubic(Anim.progress(t, 0.8f, 0.6f)) * Anim.clamp01(1f - Math.abs(progress) * 5f);
		if (a <= 0.004f) return;
		float alpha = Render2D.alpha();
		Render2D.setAlpha(alpha * a);
		float cx = width / 2f;
		float handleW = Math.max(40f, Math.min(64f, width * 0.1f));
		float handleY = height - 10f + lift;
		Render2D.round(ctx, cx - handleW / 2f, handleY, handleW, 3f, 1.5f, 0xCCFFFFFF);

		SdfFont font = Fonts.medium();
		float size = Math.max(6.5f, Math.min(9f, height * 0.022f));
		float w = font.width(hint, size);
		float x = cx - w / 2f;
		float y = Render2D.middle(font, handleY - size * 1.6f, size);
		Render2D.text(ctx, font, hint, x, y, size, 0x8CFFFFFF);
		float phase = time % 2.8f / 1.8f;
		if (phase < 1f) {
			float band = Math.max(16f, w * 0.2f);
			float bx = x - band + (w + band * 2f) * phase;
			SHIMMER.color = 0xFFFFFFFF;
			Render2D.text(ctx, font, hint, x, y, size, SHIMMER.fadeEdges(bx - band, bx + band, band));
		}
		Render2D.setAlpha(alpha);
	}
}
