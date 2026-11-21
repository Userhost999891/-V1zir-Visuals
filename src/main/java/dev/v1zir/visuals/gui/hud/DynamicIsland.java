package dev.v1zir.visuals.gui.hud;

import dev.v1zir.visuals.media.AlbumArt;
import dev.v1zir.visuals.media.Media;
import dev.v1zir.visuals.media.NowPlaying;
import dev.v1zir.visuals.media.Track;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.impl.hud.CompassModule;
import dev.v1zir.visuals.module.impl.hud.MediaCard;
import dev.v1zir.visuals.module.impl.hud.NotificationsModule;
import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.font.SdfFont;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.render.ui.ShapeElement;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.gui.DrawContext;

/**
 * Notifications as a Dynamic Island: a pill of matte dark glass at the top centre that morphs (springs on its width
 * and height while the contents cross-fade and settle in) to show a short event, then shrinks back to a tiny idle
 * pill, or hides. Events: a module toggled (its tab's icon, its name and an ON / OFF chip), the click GUI opened or
 * closed, the song changed (cover, title, artist and a little equaliser while it plays) and the other
 * notifications. A new event replaces the one showing. While a song plays the idle pill carries a tiny cover and
 * equaliser, and it sits under the compass when that holds the top centre. Texts are measured and trimmed once per
 * event, so frames neither measure nor allocate.
 */
public final class DynamicIsland {
	private static final int IDLE = 0;
	private static final int MUSIC = 1;
	private static final int MODULE = 2;
	private static final int MESSAGE = 3;
	private static final int TRACK = 4;
	private static final int OFF = 0xFFFF5C6C;
	private static final float TOP = 5f;
	private static final float MAX_TEXT = 150f;
	private static final float TITLE = 7.5f;
	private static final float TEXT = 6.5f;
	private static final float CHIP = 5.5f;

	private static final Event IDLE_PILL = new Event(IDLE, 0, 0, "", "", 0f);
	private static final Event MUSIC_PILL = new Event(MUSIC, 0, 0, "", "", 0f);
	private static final Anim WIDTH = new Anim(IDLE_PILL.width, 12f);
	private static final Anim HEIGHT = new Anim(IDLE_PILL.height, 12f);
	private static final Anim SHOWN = new Anim(0f, 10f);
	/** 0 to 1 while the current contents replace the previous ones. */
	private static final Anim CHANGE = new Anim(1f, 7f);
	private static final Anim PLAYING = new Anim(0f, 6f);
	private static Event current = IDLE_PILL;
	private static Event previous = IDLE_PILL;
	private static Track lastTrack;

	private DynamicIsland() {
	}

	/** One thing the island shows, measured and trimmed when it is made. */
	private static final class Event {
		final int kind;
		final int icon;
		final int color;
		final String title;
		final String text;
		/** Seconds it stays; 0 for the idle states. */
		final float duration;
		final float width;
		final float height;
		/** Width of the ON / OFF chip of a module event. */
		final float chip;
		long shownAt;

		Event(int kind, int icon, int color, String title, String text, float duration) {
			this.kind = kind;
			this.icon = icon;
			this.color = color;
			this.duration = duration;
			this.title = title.isEmpty() ? title : Fonts.semibold().trim(title, TITLE, MAX_TEXT);
			this.text = text.isEmpty() ? text : Fonts.medium().trim(text, TEXT, MAX_TEXT);
			float titleW = this.title.isEmpty() ? 0f : Fonts.semibold().width(this.title, TITLE);
			float textW = this.text.isEmpty() ? 0f : Fonts.medium().width(this.text, TEXT);
			chip = kind == MODULE ? Fonts.bold().width(this.text, CHIP) + 10f : 0f;
			switch (kind) {
				case MUSIC -> {
					width = 66f;
					height = 14f;
				}
				case MODULE -> {
					// Icon, name and the ON / OFF chip.
					width = 6f + 14f + 7f + titleW + 10f + chip + 6f;
					height = 24f;
				}
				case MESSAGE -> {
					width = 6f + 14f + 7f + Math.max(titleW, textW) + 12f;
					height = this.text.isEmpty() ? 24f : 30f;
				}
				case TRACK -> {
					// Cover, title over artist, the equaliser.
					width = 5f + 24f + 8f + Math.max(titleW, textW) + 12f + 9f + 8f;
					height = 34f;
				}
				default -> {
					width = 36f;
					height = 10f;
				}
			}
		}
	}

	public static void moduleToggled(Module module) {
		boolean on = module.isEnabled();
		show(new Event(MODULE, module.category().icon(), on ? Theme.accent() : OFF, module.name(), on ? "ON" : "OFF", 1.8f));
	}

	public static void message(int icon, String title, String text, boolean positive) {
		show(new Event(MESSAGE, icon, positive ? Theme.accent() : OFF, title, text, 2.4f));
	}

	private static void show(Event event) {
		event.shownAt = System.nanoTime();
		previous = current;
		current = event;
		CHANGE.snap(0f);
		CHANGE.target(1f);
	}

	/** Draws the island centred at the top of a screenWidth wide screen. */
	public static void render(DrawContext ctx, int screenWidth, int screenHeight, NotificationsModule settings) {
		long now = System.nanoTime();
		NowPlaying playing = Media.now();
		Track track = playing.track();
		if (track != lastTrack) {
			lastTrack = track;
			if (track != null && settings.songs.on()) {
				show(new Event(TRACK, 0, 0, track.title(), track.artist().isEmpty() ? track.album() : track.artist(), 3.6f));
			}
		}
		Event idle = settings.songs.on() && track != null && playing.playing() ? MUSIC_PILL : IDLE_PILL;
		if (current.duration > 0f ? (now - current.shownAt) / 1.0e9f > current.duration : current != idle) show(idle);
		boolean visible = current.duration > 0f || settings.idle.is("Pill");
		float shown = SHOWN.target(visible ? 1f : 0f).get();
		float w = WIDTH.target(current.width).get();
		float h = HEIGHT.target(current.height).get();
		float change = CHANGE.get();
		float play = PLAYING.target(playing.playing() ? 1f : 0f).get();
		if (shown <= 0.004f) return;

		boolean music = current.kind == MUSIC || current.kind == TRACK || previous.kind == MUSIC || previous.kind == TRACK;
		if (music) AlbumArt.sync(playing.art());
		int accent = music ? AlbumArt.color(Theme.accent()) : Theme.accent();
		float cx = screenWidth / 2f;
		float top = top(screenWidth, screenHeight);
		float x = cx - w / 2f;
		float r = Math.min(h / 2f, 13f);
		float alpha = Render2D.alpha();
		Render2D.setAlpha(alpha * shown);
		Render2D.shadow(ctx, x, top + 2f, w, h, r, 10f, 0x70000000);
		Render2D.glass(ctx, x, top, w, h, r, 0, ShapeElement.FILL | ShapeElement.MATTE, 0xEC060608);
		Render2D.outline(ctx, x, top, w, h, r, 1f, 0x1CFFFFFF);

		// The old contents leave quickly, the new ones come in after them and settle from a little smaller.
		float cy = top + h / 2f;
		ctx.enableScissor((int) Math.floor(x), (int) Math.floor(top), (int) Math.ceil(x + w), (int) Math.ceil(top + h));
		float out = Anim.clamp01(1f - change * 2f);
		if (out > 0.004f && previous != current) {
			Render2D.setAlpha(alpha * shown * out);
			contents(ctx, previous, cx, cy, 1f, play, accent);
		}
		float in = Anim.clamp01((change - 0.25f) / 0.75f);
		if (in > 0.004f) {
			Render2D.setAlpha(alpha * shown * in);
			contents(ctx, current, cx, cy, 0.92f + 0.08f * in, play, accent);
		}
		ctx.disableScissor();
		Render2D.setAlpha(alpha);
	}

	/** Under the compass when it sits at the top centre, else at the top of the screen. */
	private static float top(int screenWidth, int screenHeight) {
		CompassModule compass = ModuleManager.get(CompassModule.class);
		if (compass == null || !compass.isEnabled()) return TOP;
		float y = compass.y(screenHeight);
		float x = compass.x(screenWidth);
		boolean centre = x < screenWidth / 2f + 30f && x + compass.width() > screenWidth / 2f - 30f;
		return centre && y < 40f ? y + compass.height() + 4f : TOP;
	}

	/** An event's contents centred on (cx, cy), scaled by k around that centre. */
	private static void contents(DrawContext ctx, Event e, float cx, float cy, float k, float play, int accent) {
		if (e.kind == IDLE) return;
		if (k != 1f) {
			ctx.getMatrices().pushMatrix();
			ctx.getMatrices().translate(cx, cy);
			ctx.getMatrices().scale(k, k);
			ctx.getMatrices().translate(-cx, -cy);
		}
		float left = cx - e.width / 2f;
		float right = cx + e.width / 2f;
		SdfFont bold = Fonts.semibold();
		SdfFont medium = Fonts.medium();
		switch (e.kind) {
			case MUSIC -> {
				AlbumArt.draw(ctx, left + 3f, cy - 4.5f, 9f, 3f, 0xFFFFFFFF, accent, Theme.accent2());
				MediaCard.equalizer(ctx, right - 15f, cy + 4f, play, accent);
			}
			case MODULE -> {
				badge(ctx, e.icon, left + 6f + 7f, cy, e.color);
				Render2D.textMiddle(ctx, bold, e.title, left + 27f, cy, TITLE, Theme.text());
				float chipX = right - 6f - e.chip;
				Render2D.round(ctx, chipX, cy - 6f, e.chip, 12f, 6f, ColorUtil.withAlpha(e.color, 46));
				Render2D.textMiddle(ctx, Fonts.bold(), e.text, chipX + 5f, cy, CHIP, ColorUtil.lerp(e.color, 0xFFFFFFFF, 0.2f));
			}
			case MESSAGE -> {
				badge(ctx, e.icon, left + 6f + 7f, cy, e.color);
				if (e.text.isEmpty()) {
					Render2D.textMiddle(ctx, bold, e.title, left + 27f, cy, TITLE, Theme.text());
				} else {
					Render2D.textMiddle(ctx, bold, e.title, left + 27f, cy - 4.5f, TITLE, Theme.text());
					Render2D.textMiddle(ctx, medium, e.text, left + 27f, cy + 5f, TEXT, Theme.textDim());
				}
			}
			case TRACK -> {
				Render2D.shadow(ctx, left + 5f, cy - 11f, 24f, 24f, 7f, 6f, ColorUtil.withAlpha(accent, 90));
				AlbumArt.draw(ctx, left + 5f, cy - 12f, 24f, 7f, 0xFFFFFFFF, accent, Theme.accent2());
				Render2D.textMiddle(ctx, bold, e.title, left + 37f, cy - 4.5f, TITLE, Theme.text());
				Render2D.textMiddle(ctx, medium, e.text, left + 37f, cy + 5f, TEXT, Theme.textDim());
				MediaCard.equalizer(ctx, right - 17f, cy + 4f, play, accent);
			}
			default -> {
			}
		}
		if (k != 1f) ctx.getMatrices().popMatrix();
	}

	/** An icon on a soft disc of its colour. */
	private static void badge(DrawContext ctx, int icon, float cx, float cy, int color) {
		Render2D.circle(ctx, cx, cy, 7f, ColorUtil.withAlpha(color, 44));
		Render2D.icon(ctx, icon, cx, cy, 7.5f, color);
	}
}
