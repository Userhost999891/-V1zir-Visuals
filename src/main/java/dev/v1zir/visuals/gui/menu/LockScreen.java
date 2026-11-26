package dev.v1zir.visuals.gui.menu;

import dev.v1zir.visuals.media.Media;
import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.font.Icons;
import dev.v1zir.visuals.render.font.SdfFont;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.TextureSetup;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2fStack;

/**
 * Phone-style lock screen the title screen opens on, over its own wallpaper: a padlock, the date over a big clock
 * tinted with the theme, glass widgets and a notification ({@link LockWidgets}), two quick actions in the bottom
 * corners and a shimmering "swipe up to open" hint above a handle that hops now and then while nothing moves. It is a
 * sheet: dragged up it follows the cursor with some resistance, and let go past a threshold (or flicked) it slides
 * away with a spring while its content swells and fades and the main menu comes in; let go early and it springs back.
 * Scrolling, any key, a click on the hint or the notification open it too; a quick action opens it straight into
 * singleplayer or multiplayer and the last played widget into that world or server.
 * <p>
 * From the main menu a swipe up brings it back: the sheet rises from the bottom of the screen with the cursor and,
 * let go past a threshold (or flicked), settles over the menu locked again; let go early and it sinks back.
 */
public final class LockScreen {
	/** Fraction of the screen the sheet must be dragged past for letting go to open it. */
	private static final float THRESHOLD = 0.22f;
	/** Upward speed (screen heights per second) at which letting go opens it from anywhere. */
	private static final float FLICK = 1.1f;
	/** Bottom part of the screen (the hint and the handle) where a click opens it. */
	private static final float HINT_AREA = 0.8f;
	/** How far the sheet has gone when an action it was opened into runs. */
	private static final float ACTION_AT = 0.5f;
	/** The content fades out over the first 1 / FADE of the slide. */
	private static final float FADE = 2.6f;
	private static final Render2D.TextStyle CLOCK = new Render2D.TextStyle().weight(0f);
	/** A soft, slightly offset copy of the digits under them gives the clock depth. */
	private static final Render2D.TextStyle DEPTH = new Render2D.TextStyle().weight(0.06f).soft(0.3f);
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
	private final LockWidgets widgets = new LockWidgets();
	/** Opens singleplayer, multiplayer and the last world or server; set by the menu. */
	private Runnable singleplayer;
	private Runnable multiplayer;
	private Runnable rejoin;
	/** The action the sheet was opened into; it runs once the sheet is mostly out of the way. */
	@Nullable
	private Runnable pending;
	/** Holding the media backend for the now playing widget. */
	private boolean media;
	/** The swipe up from the menu: how far the sheet has risen from the bottom (0 none, 1 covering). */
	private final Anim rise = new Anim(0f, 6f);
	private float risen;
	private boolean raising;
	private boolean raised;
	private double raiseY;
	private float raiseSample;
	private long raiseTime;
	private float raiseVelocity;
	/** 1 after a few seconds without the mouse moving: the handle starts hopping. */
	private final Anim idle = new Anim(0f, 4f);
	private int lastMouseX;
	private int lastMouseY;
	private long lastMove = System.nanoTime();

	/** Shows the lock screen again the next time the title screen opens. */
	public static void lock() {
		locked = true;
	}

	/** When the screen opens or resizes; the actions open singleplayer, multiplayer and the last world or server. */
	void init(MinecraftClient client, Runnable singleplayer, Runnable multiplayer, Runnable rejoin) {
		this.singleplayer = singleplayer;
		this.multiplayer = multiplayer;
		this.rejoin = rejoin;
		widgets.init(client);
	}

	/**
	 * Advances the slide once per frame; returns how far the sheet has gone (0 covering, 1 gone). Widgets on, it holds
	 * the media backend while the sheet can be seen.
	 */
	float update(boolean enabled, boolean showWidgets) {
		if (!enabled && locked) {
			locked = false;
			dragging = false;
			open.snap(1f);
		}
		risen = enabled ? rise.get() : 0f;
		if (!raising && risen >= 0.999f && rise.target() >= 1f) {
			// Risen all the way: locked again, the sheet back in its usual place.
			locked = true;
			open.snap(0f);
			rise.snap(0f);
			risen = 0f;
		}
		progress = open.get();
		holdMedia(showWidgets && visible());
		return progress;
	}

	/** How far the sheet has risen from the bottom in a swipe up from the menu (0 none, 1 covering). */
	float risen() {
		return risen;
	}

	/** A press on an empty spot of the unlocked menu: a swipe up from it may bring the lock screen back. */
	void pressRaise(double y) {
		raising = true;
		raised = false;
		raiseY = y;
		raiseSample = rise.get();
		raiseTime = System.nanoTime();
		raiseVelocity = 0f;
	}

	/** Raises the sheet with the cursor; returns whether the press has turned into a swipe. */
	boolean dragRaise(double y, int height) {
		if (!raising) return false;
		double dy = raiseY - y;
		if (!raised && dy < 6.0) return false;
		raised = true;
		float up = (float) Math.max(0.0, dy / height);
		// A little resistance at first, so a slip of the mouse does not lift it far.
		float r = Math.min(1f, (float) (1.0 - Math.exp(-2.4 * up)) * 1.05f);
		long now = System.nanoTime();
		float dt = (now - raiseTime) / 1.0e9f;
		if (dt >= 0.008f) {
			raiseVelocity = raiseVelocity * 0.3f + (r - raiseSample) / dt * 0.7f;
			raiseSample = r;
			raiseTime = now;
		}
		rise.snap(r);
		risen = r;
		return true;
	}

	/** Let go of a swipe up: past the threshold (or flicked) the sheet settles and locks, else it sinks back. */
	void releaseRaise() {
		if (!raising) return;
		raising = false;
		if (!raised) return;
		if (System.nanoTime() - raiseTime > 90_000_000L) raiseVelocity = 0f;
		if (risen > 0.35f || raiseVelocity > 1.4f && risen > 0.05f) rise.target(1f).fling(Math.max(raiseVelocity, 1f));
		else rise.target(0f).fling(raiseVelocity);
	}

	/** Runs the action the sheet was opened into once it is mostly gone; called every tick, outside rendering. */
	void tick() {
		if (pending == null || progress < ACTION_AT) return;
		Runnable action = pending;
		pending = null;
		// Back from that screen the menu is at rest instead of finishing the slide.
		open.snap(1f);
		progress = 1f;
		action.run();
	}

	/** Lets the media backend go (the screen was closed). */
	void dispose() {
		holdMedia(false);
	}

	private void holdMedia(boolean hold) {
		if (hold == media) return;
		media = hold;
		if (hold) Media.acquire(this);
		else Media.release(this);
	}

	/** While locked the sheet takes all input, also while it springs back after an early release. */
	boolean locked() {
		return locked;
	}

	boolean visible() {
		return locked || progress < 0.999f || risen > 0.001f;
	}

	/** Whether the content (and so its glass) can still be seen; it is gone early in the slide. */
	boolean contentShown() {
		return progress < 1f / FADE;
	}

	void unlock() {
		if (!locked) return;
		locked = false;
		dragging = false;
		widgets.pressed = LockWidgets.NONE;
		open.target(1f).fling(4f);
	}

	private void unlockInto(Runnable action) {
		pending = action;
		unlock();
	}

	/** Whether a widget, the notification or a quick action is under the cursor (no long press there). */
	boolean occupied(double x, double y) {
		return widgets.occupied(x, y);
	}

	void press(double x, double y, int height) {
		dragging = true;
		moved = false;
		widgets.pressed = widgets.hit(x, y);
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
		// Moving turns a press on a control into a swipe.
		moved = true;
		widgets.pressed = LockWidgets.NONE;
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

	/** Whether the press being held has started moving the sheet. */
	boolean dragged() {
		return dragging && moved;
	}

	/** Forgets the press being held (it became a long press): letting go then does nothing. */
	void cancelPress() {
		dragging = false;
		raising = false;
		widgets.pressed = LockWidgets.NONE;
	}

	void release(double x, double y, int height) {
		if (!dragging) return;
		dragging = false;
		if (!moved) {
			int control = widgets.pressed;
			widgets.pressed = LockWidgets.NONE;
			if (control != LockWidgets.NONE && widgets.hit(x, y) == control) {
				activate(control);
			} else if (y > height * HINT_AREA) {
				// A click on the hint opens it; anywhere else the sheet hops up a little to show that it moves.
				unlock();
			} else {
				open.fling(1.1f);
			}
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

	private void activate(int control) {
		switch (control) {
			case LockWidgets.PLAY -> Media.command(Media.Command.PLAY_PAUSE);
			case LockWidgets.CARD -> unlock();
			case LockWidgets.LAST -> unlockInto(rejoin);
			case LockWidgets.SINGLE -> unlockInto(singleplayer);
			case LockWidgets.MULTI -> unlockInto(multiplayer);
			default -> {
			}
		}
	}

	/**
	 * The sheet: the lock screen's wallpaper (null while a picture loads), with rounded bottom corners and a shadow
	 * while it slides. tint dims it.
	 */
	void renderSheet(DrawContext ctx, int width, int height, @Nullable TextureSetup wallpaper, int tint) {
		if (rising()) {
			// Swiped up from the menu: the sheet rises from the bottom, its top corners rounded until it settles.
			float y = (1f - risen) * height;
			float radius = Anim.clamp01((1f - risen) * 12f) * 16f;
			Render2D.shadow(ctx, 0f, y, width, 40f, radius, 24f, 0x70000000);
			if (wallpaper != null) Render2D.wallpaper(ctx, wallpaper, 0f, y, width, height, radius, Render2D.CORNERS_TOP, tint);
			else Render2D.round(ctx, 0f, y, width, height, radius, Render2D.CORNERS_TOP, 0xFF050505);
			return;
		}
		float y = -progress * height;
		float radius = Anim.clamp01(Math.abs(progress) * 12f) * 16f;
		if (progress > 0.001f) Render2D.shadow(ctx, 0f, y + height - 40f, width, 40f, radius, 24f, 0x70000000);
		if (wallpaper != null) {
			Render2D.wallpaper(ctx, wallpaper, 0f, y, width, height, radius, Render2D.CORNERS_BOTTOM, tint);
		} else {
			Render2D.round(ctx, 0f, y, width, height, radius, Render2D.CORNERS_BOTTOM, 0xFF050505);
		}
	}

	/** The sheet is rising from the bottom in a swipe up from the menu. */
	private boolean rising() {
		return !locked && risen > 0.001f;
	}

	/**
	 * Everything on the sheet; t is the intro time, time the seconds since the screen opened and fade the opacity left
	 * by the wallpaper picker. While the sheet goes up its content rises with it, swells a little and fades.
	 */
	void renderContent(DrawContext ctx, MinecraftClient client, int width, int height, float t, float time, MenuClock clock,
					   float fade, int mouseX, int mouseY, boolean showWidgets, boolean showCard) {
		// Rising from the bottom, the content rides on the sheet as it is when it covers the menu.
		float progress = rising() ? 0f : this.progress;
		float appear = Anim.easeOutQuart(Anim.progress(t, 0.25f, 0.9f));
		float lift = -progress * height + (1f - appear) * 10f + (rising() ? (1f - risen) * height : 0f);
		renderHint(ctx, width, height, t, time, clock.hint, lift, fade, mouseX, mouseY);
		widgets.begin();
		float p = Math.max(progress, 0f);
		float alpha = appear * Anim.clamp01(1f - p * FADE) * fade;
		if (alpha <= 0.004f) return;
		float cx = width / 2f;
		Matrix3x2fStack matrices = ctx.getMatrices();
		boolean swell = p > 0.001f;
		if (swell) {
			float s = 1f + 0.12f * p;
			float pivot = height * 0.45f + lift;
			matrices.pushMatrix();
			matrices.translate(cx, pivot);
			matrices.scale(s, s);
			matrices.translate(-cx, -pivot);
		}
		Render2D.setAlpha(alpha);
		int tint = ColorUtil.withAlpha(ColorUtil.lerp(0xFFFFFFFF, Theme.accent(), 0.12f), 0xF2);

		// The padlock opens as soon as the sheet moves.
		float lockSize = Math.max(7f, Math.min(14f, height * 0.026f));
		float lockY = height * 0.075f + lift;
		float opened = Anim.clamp01(Math.abs(progress) * 12f);
		Render2D.icon(ctx, Icons.LOCK, cx, lockY, lockSize, ColorUtil.withAlpha(0xFFFFFF, Math.round(220 * (1f - opened))));
		if (opened > 0.004f) Render2D.icon(ctx, Icons.UNLOCK, cx, lockY, lockSize, ColorUtil.withAlpha(0xFFFFFF, Math.round(220 * opened)));

		// The date in a small semibold over the time.
		SdfFont semibold = Fonts.semibold();
		float dateSize = Math.max(7.5f, Math.min(16f, height * 0.031f));
		float dateY = height * 0.15f + lift;
		Render2D.textCentered(ctx, semibold, clock.date, cx, Render2D.middle(semibold, dateY, dateSize), dateSize, ColorUtil.fade(tint, 0.9f));

		// The time: big display digits standing on a soft shadow, with a band of light sweeping across now and then.
		float size = Math.min(height * 0.26f, width * 0.19f);
		float baseline = dateY + dateSize * 0.9f + height * 0.012f + clock.digitHeight(size);
		DEPTH.color = 0x38000000;
		clock.drawTime(ctx, cx, baseline + size * 0.02f, size, DEPTH);
		CLOCK.color = tint;
		float x = clock.drawTime(ctx, cx, baseline, size, CLOCK);
		renderShine(ctx, clock, x, baseline, size, time);

		if (showWidgets) {
			Render2D.setAlpha(alpha * Anim.easeOutCubic(Anim.progress(t, 0.55f, 0.6f)));
			float h = Math.max(24f, Math.min(48f, height * 0.085f));
			widgets.renderRow(ctx, client, clock, cx, baseline + height * 0.05f, h, mouseX, mouseY);
		}
		if (showCard) {
			Render2D.setAlpha(alpha * Anim.easeOutCubic(Anim.progress(t, 0.7f, 0.6f)));
			widgets.renderCard(ctx, clock, width, height, height - Math.max(46f, height * 0.18f) + lift, mouseX, mouseY);
		}
		Render2D.setAlpha(alpha * Anim.easeOutCubic(Anim.progress(t, 0.85f, 0.6f)));
		float radius = Math.max(12f, Math.min(24f, height * 0.044f));
		float inset = Math.max(16f, height * 0.075f);
		widgets.renderActions(ctx, clock, width, height, height - inset - radius + lift, radius, inset, mouseX, mouseY);
		if (swell) matrices.popMatrix();
		Render2D.setAlpha(1f);
	}

	/** A soft band of light sweeps across the digits once they have appeared, then every ten seconds. */
	private static void renderShine(DrawContext ctx, MenuClock clock, float x, float baseline, float size, float time) {
		float phase = (time + 8.8f) % 10f;
		if (time < 1.2f || phase > 1.4f) return;
		SdfFont font = clock.font();
		float w = font.width(clock.time, size);
		float band = size * 0.75f;
		float bx = x - band + (w + band * 2f) * Anim.easeInOutSine(phase / 1.4f);
		SHINE.color = 0xFFFFFFFF;
		Render2D.text(ctx, font, clock.time, x, baseline - font.ascent(size), size, SHINE.fadeEdges(bx - band, bx + band, band));
	}

	/**
	 * "Swipe up to open" with a light band sweeping across it, above the handle; both fade as soon as the sheet moves.
	 * After a few seconds without the mouse moving they hop up a little every few seconds, as a nudge.
	 */
	private void renderHint(DrawContext ctx, int width, int height, float t, float time, String hint, float lift, float fade,
							int mouseX, int mouseY) {
		long now = System.nanoTime();
		if (mouseX != lastMouseX || mouseY != lastMouseY || dragging) {
			lastMouseX = mouseX;
			lastMouseY = mouseY;
			lastMove = now;
		}
		float rest = idle.target(now - lastMove > 3_000_000_000L ? 1f : 0f).get();
		float progress = rising() ? 0f : this.progress;
		float a = Anim.easeOutCubic(Anim.progress(t, 0.8f, 0.6f)) * Anim.clamp01(1f - Math.abs(progress) * 5f) * fade;
		if (a <= 0.004f) return;
		Render2D.setAlpha(a);
		float hop = (time % 2.8f) / 0.7f;
		float bob = hop < 1f ? (0.5f - 0.5f * (float) Math.cos(hop * Math.PI * 2.0)) * rest : 0f;
		float cx = width / 2f;
		float handleW = Math.max(40f, Math.min(64f, width * 0.1f));
		float handleY = height - 10f + lift - bob * Math.max(2f, height * 0.008f);
		Render2D.round(ctx, cx - handleW / 2f, handleY, handleW, 3f, 1.5f, 0xCCFFFFFF);

		SdfFont font = Fonts.medium();
		float size = Math.max(6.5f, Math.min(9f, height * 0.022f));
		float w = font.width(hint, size);
		float x = cx - w / 2f;
		float y = Render2D.middle(font, handleY - size * 1.6f - bob * Math.max(1f, height * 0.004f), size);
		Render2D.text(ctx, font, hint, x, y, size, 0x8CFFFFFF);
		float phase = time % 2.8f / 1.8f;
		if (phase < 1f) {
			float band = Math.max(16f, w * 0.2f);
			float bx = x - band + (w + band * 2f) * phase;
			SHIMMER.color = 0xFFFFFFFF;
			Render2D.text(ctx, font, hint, x, y, size, SHIMMER.fadeEdges(bx - band, bx + band, band));
		}
		Render2D.setAlpha(1f);
	}
}
