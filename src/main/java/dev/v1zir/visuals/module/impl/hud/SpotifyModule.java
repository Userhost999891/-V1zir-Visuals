package dev.v1zir.visuals.module.impl.hud;

import dev.v1zir.visuals.media.AlbumArt;
import dev.v1zir.visuals.media.Media;
import dev.v1zir.visuals.media.NowPlaying;
import dev.v1zir.visuals.media.Track;
import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.module.setting.KeySetting;
import dev.v1zir.visuals.module.setting.ModeSetting;
import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.font.Icons;
import dev.v1zir.visuals.render.font.SdfFont;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import org.lwjgl.glfw.GLFW;

/**
 * Now playing widget: album art, title and artist (scrolling when too long), a progress bar with
 * elapsed and total time, the play state and prev / play / next buttons that work with the chat
 * or the HUD editor open. Card and Compact styles; the accent can follow the album art.
 */
public final class SpotifyModule extends HudModule {
	private static final float MARQUEE_PAUSE = 2.5f;
	private static final float MARQUEE_SPEED = 20f;
	private static final float MARQUEE_GAP = 26f;
	private static final String SEPARATOR = "  ·  ";
	private static final int PREVIOUS = 0;
	private static final int PLAY = 1;
	private static final int NEXT = 2;

	public final ModeSetting style = add(new ModeSetting("Style", "Card", "Card", "Compact"));
	public final BoolSetting albumColors = add(new BoolSetting("Album colors", true));
	public final BoolSetting controls = add(new BoolSetting("Controls", true));
	public final BoolSetting hideIdle = add(new BoolSetting("Hide when idle", true));
	public final KeySetting playPauseKey = add(new KeySetting("Play/Pause key", GLFW.GLFW_KEY_UNKNOWN));
	public final KeySetting nextKey = add(new KeySetting("Next key", GLFW.GLFW_KEY_UNKNOWN));
	public final KeySetting previousKey = add(new KeySetting("Previous key", GLFW.GLFW_KEY_UNKNOWN));

	private final Anim appear = new Anim(0f, 10f);
	private final Anim playing = new Anim(0f, 6f);
	private final Anim progress = new Anim(0f, 10f);
	private final Anim[] hover = {new Anim(0f, 14f), new Anim(0f, 14f), new Anim(0f, 14f)};
	private final Anim[] press = {new Anim(0f, 9f), new Anim(0f, 9f), new Anim(0f, 9f)};
	/** Button centres and radius of the last frame, for clicks. */
	private final float[] buttonX = new float[3];
	private float buttonY;
	private float buttonR;
	private boolean buttons;

	private final Render2D.TextStyle titleStyle = new Render2D.TextStyle();
	private final Render2D.TextStyle artistStyle = new Render2D.TextStyle();

	// Per song (and font): texts and their widths, so frames neither measure nor format.
	private Track track;
	/** Media status shown in place of the artist while nothing plays. */
	private String status;
	private String title = "";
	private String artist = "";
	private String total = "";
	private String elapsed = "0:00";
	private long elapsedSecond = -1;
	private long trackSince = System.nanoTime();
	private SdfFont measuredWith;
	private boolean measuredCompact;
	private float titleW;
	private float artistW;
	private float separatorW;
	private float totalW;

	private float spin;
	private long lastFrame = System.nanoTime();

	public SpotifyModule() {
		super("Spotify", "Now playing with album art and controls", 0.008f, 0.75f);
		setSubCategory("Media");
	}

	@Override
	public boolean showInList() {
		return false;
	}

	@Override
	protected void onEnable() {
		Media.acquire(this);
	}

	@Override
	protected void onDisable() {
		Media.release(this);
		buttons = false;
	}

	@Override
	public void onKeyPressed(int key) {
		if (key == playPauseKey.key()) Media.command(Media.Command.PLAY_PAUSE);
		else if (key == nextKey.key()) Media.command(Media.Command.NEXT);
		else if (key == previousKey.key()) Media.command(Media.Command.PREVIOUS);
	}

	@Override
	public boolean hasContent() {
		appear.target(visible() ? 1f : 0f);
		return appear.get() > 0.01f;
	}

	private boolean visible() {
		return Media.now().track() != null || !hideIdle.on() || editing();
	}

	/** The pointer can only reach the widget with the chat or the HUD editor open. */
	private boolean pointerActive() {
		return mc.currentScreen instanceof ChatScreen || editing();
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (!buttons || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
		for (int i = 0; i < 3; i++) {
			float dx = (float) mouseX - buttonX[i];
			float dy = (float) mouseY - buttonY;
			if (dx * dx + dy * dy > buttonR * buttonR) continue;
			press[i].snap(1f);
			press[i].target(0f);
			Media.command(i == PLAY ? Media.Command.PLAY_PAUSE : i == NEXT ? Media.Command.NEXT : Media.Command.PREVIOUS);
			return true;
		}
		return false;
	}

	@Override
	protected void render(DrawContext ctx, float tickDelta, float x, float y) {
		NowPlaying now = Media.now();
		AlbumArt.sync(now.art());
		if (now.track() != track || track == null && Media.status() != status) changeTrack(now.track());
		boolean compact = style.is("Compact");
		measure(compact);

		long nanos = System.nanoTime();
		float dt = Math.min((nanos - lastFrame) / 1.0e9f, 0.1f);
		lastFrame = nanos;
		appear.target(visible() ? 1f : 0f);
		float alpha = appear.get();
		float play = playing.target(now.playing() ? 1f : 0f).get();
		float bar = progress.target(now.progress()).get();
		long second = now.position() / 1000L;
		if (second != elapsedSecond) {
			elapsedSecond = second;
			elapsed = time(second);
		}
		int accent = Theme.accent();
		int accent2 = Theme.accent2();
		if (albumColors.on()) {
			accent = AlbumArt.color(accent);
			accent2 = ColorUtil.lerp(accent, 0xFFFFFFFF, 0.35f);
		}
		float mx = -1f;
		float my = -1f;
		if (pointerActive()) {
			mx = (float) mc.mouse.getScaledX(mc.getWindow());
			my = (float) mc.mouse.getScaledY(mc.getWindow());
		}

		Render2D.setAlpha(alpha);
		buttons = false;
		if (compact) renderCompact(ctx, x, y, play, bar, accent, accent2, dt, mx, my);
		else renderCard(ctx, x, y, play, bar, accent, accent2, mx, my);
		Render2D.setAlpha(1f);
	}

	private void renderCard(DrawContext ctx, float x, float y, float play, float bar, int accent, int accent2, float mx, float my) {
		boolean withButtons = controls.on() && track != null;
		float w = 176f;
		float h = withButtons ? 62f : 52f;
		width = w;
		height = h;
		panel(ctx, x, y, w, h, 8f);
		// A wash of the album colour from the left gives the card its tint.
		Render2D.gradientH(ctx, x, y, w * 0.75f, h, 8f, ColorUtil.withAlpha(accent, 34), ColorUtil.withAlpha(accent, 0));

		float art = h - 12f;
		Render2D.shadow(ctx, x + 6f, y + 7f, art, art, 7f, 9f, ColorUtil.withAlpha(accent, 80));
		AlbumArt.draw(ctx, x + 6f, y + 6f, art, 7f, ColorUtil.lerp(0xFF8C8C8C, 0xFFFFFFFF, track == null ? 1f : 0.35f + 0.65f * play), accent, accent2);

		float tx = x + art + 14f;
		float tw = x + w - 9f - tx;
		float time = (System.nanoTime() - trackSince) / 1.0e9f;
		SdfFont bold = Fonts.semibold();
		SdfFont font = Fonts.medium();
		marquee(ctx, bold, title, titleW, tx, y + 8f, tw - 14f, 8.5f, Theme.text(), titleStyle, time);
		marquee(ctx, font, artist, artistW, tx, y + 19.5f, tw, 6.5f, Theme.textDim(), artistStyle, time);
		equalizer(ctx, x + w - 17f, y + 15.5f, play, accent);
		if (track == null) return;

		float by = y + 33f;
		Render2D.round(ctx, tx, by, tw, 3f, 1.5f, ColorUtil.withAlpha(0xFFFFFF, 24));
		float fill = Math.max(3f, tw * bar);
		Render2D.glow(ctx, tx, by, fill, 3f, 1.5f, 4f, ColorUtil.withAlpha(accent, 70), ColorUtil.withAlpha(accent2, 70));
		Render2D.gradientH(ctx, tx, by, fill, 3f, 1.5f, accent, accent2);
		Render2D.circle(ctx, tx + fill, by + 1.5f, 2.6f, 0xFFFFFFFF);

		float ty = y + 40f;
		Render2D.text(ctx, font, elapsed, tx, ty, 5.5f, Theme.textDim());
		Render2D.text(ctx, font, total, tx + tw - totalW, ty, 5.5f, Theme.textDim());
		buttons = withButtons;
		if (withButtons) {
			float cx = tx + tw / 2f;
			buttonControls(ctx, cx - 16f, cx, cx + 16f, y + 50f, 6.5f, play, accent, mx, my);
		}
	}

	private void renderCompact(DrawContext ctx, float x, float y, float play, float bar, int accent, int accent2, float dt, float mx, float my) {
		boolean withButtons = controls.on() && track != null;
		float w = 168f;
		float h = 24f;
		width = w;
		height = h;
		panel(ctx, x, y, w, h, h / 2f);
		Render2D.gradientH(ctx, x, y, w * 0.6f, h, h / 2f, ColorUtil.withAlpha(accent, 30), ColorUtil.withAlpha(accent, 0));

		// The cover is a record that spins while the song plays.
		float disc = h - 6f;
		float cx = x + 3f + disc / 2f;
		float cy = y + h / 2f;
		spin = (spin + dt * play * (float) (Math.PI * 2.0 / 9.0)) % (float) (Math.PI * 2.0);
		Render2D.shadow(ctx, x + 3f, y + 3.5f, disc, disc, disc / 2f, 6f, ColorUtil.withAlpha(accent, 90));
		ctx.getMatrices().pushMatrix();
		ctx.getMatrices().translate(cx, cy);
		ctx.getMatrices().rotate(spin);
		ctx.getMatrices().translate(-cx, -cy);
		AlbumArt.draw(ctx, x + 3f, y + 3f, disc, disc / 2f, 0xFFFFFFFF, accent, accent2);
		ctx.getMatrices().popMatrix();
		Render2D.ring(ctx, cx, cy, disc / 2f - 0.5f, 1f, ColorUtil.withAlpha(0xFFFFFF, 40));
		Render2D.circle(ctx, cx, cy, 2.2f, ColorUtil.withAlpha(Theme.preset().base(), 230));

		float tx = x + disc + 10f;
		float right = x + w - (withButtons ? 46f : 20f);
		float tw = right - tx;
		float time = (System.nanoTime() - trackSince) / 1.0e9f;
		SdfFont font = Fonts.semibold();
		float size = 7f;
		float total = titleW + separatorW + artistW;
		float off = marqueeOffset(total, tw, time);
		float ty = Render2D.middle(font, y + (track != null ? 10.5f : h / 2f), size);
		ctx.enableScissor((int) Math.floor(tx), (int) y, (int) Math.ceil(right), (int) (y + h));
		titleStyle.color = Theme.text();
		artistStyle.color = Theme.textDim();
		fade(titleStyle, tx, right, off);
		fade(artistStyle, tx, right, off);
		for (int copy = 0; copy < (off > 0f ? 2 : 1); copy++) {
			float px = tx - off + copy * (total + MARQUEE_GAP);
			Render2D.text(ctx, font, title, px, ty, size, titleStyle);
			Render2D.text(ctx, font, SEPARATOR, px + titleW, ty, size, artistStyle);
			Render2D.text(ctx, font, artist, px + titleW + separatorW, ty, size, artistStyle);
		}
		ctx.disableScissor();
		if (track != null) {
			float by = y + h - 7f;
			Render2D.round(ctx, tx, by, tw, 1.5f, 0.75f, ColorUtil.withAlpha(0xFFFFFF, 22));
			Render2D.gradientH(ctx, tx, by, Math.max(1.5f, tw * bar), 1.5f, 0.75f, accent, accent2);
		}
		buttons = withButtons;
		if (withButtons) buttonControls(ctx, x + w - 38f, x + w - 24.5f, x + w - 11f, cy, 6f, play, accent, mx, my);
		else equalizer(ctx, x + w - 15f, cy + 3f, play, accent);
	}

	/** Previous, play/pause (on an accent bubble) and next, with hover and press feedback. */
	private void buttonControls(DrawContext ctx, float prevX, float playX, float nextX, float cy, float radius, float play, int accent, float mx, float my) {
		buttonX[PREVIOUS] = prevX;
		buttonX[PLAY] = playX;
		buttonX[NEXT] = nextX;
		buttonY = cy;
		buttonR = radius + 1.5f;
		for (int i = 0; i < 3; i++) {
			float dx = mx - buttonX[i];
			float dy = my - cy;
			float over = hover[i].target(dx * dx + dy * dy <= buttonR * buttonR ? 1f : 0f).get();
			float scale = 1f - 0.18f * press[i].get() + 0.06f * over;
			float r = radius * scale;
			float bx = buttonX[i];
			if (i == PLAY) {
				Render2D.glow(ctx, bx - r, cy - r, r * 2f, r * 2f, r, 5f, ColorUtil.withAlpha(accent, 70 + Math.round(60 * over)), ColorUtil.withAlpha(accent, 40));
				Render2D.circle(ctx, bx, cy, r, ColorUtil.brighten(accent, 0.12f * over));
				// Cross-fade between the pause and play glyphs with the play state.
				int ink = ColorUtil.darken(accent, 0.82f);
				if (play > 0.01f) Render2D.icon(ctx, Icons.PAUSE, bx, cy, r * 1.15f, ColorUtil.fade(ink, play));
				if (play < 0.99f) Render2D.icon(ctx, Icons.PLAY, bx + r * 0.08f, cy, r * 1.15f, ColorUtil.fade(ink, 1f - play));
			} else {
				if (over > 0.01f) Render2D.circle(ctx, bx, cy, r, ColorUtil.withAlpha(0xFFFFFF, Math.round(22 * over)));
				Render2D.icon(ctx, i == NEXT ? Icons.SKIP_FORWARD : Icons.SKIP_BACK, bx, cy, r * 1.15f, ColorUtil.lerp(Theme.textDim(), Theme.text(), over));
			}
		}
	}

	/** Three bars dancing while the song plays; they settle into dots when it pauses. */
	private static void equalizer(DrawContext ctx, float x, float bottom, float play, int accent) {
		float t = (float) ((System.nanoTime() / 1.0e9) % 1000.0);
		for (int i = 0; i < 3; i++) {
			float wave = 0.5f + 0.5f * (float) Math.sin(t * (6.1f + i * 1.7f) + i * 2.1f);
			float bh = 2f + 6f * play * (0.25f + 0.75f * wave);
			Render2D.round(ctx, x + i * 3.5f, bottom - bh, 2f, bh, 1f, ColorUtil.withAlpha(accent, 150 + Math.round(105 * play)));
		}
	}

	/** One line of text that scrolls in a loop (after a pause) when it is wider than its box. */
	private static void marquee(DrawContext ctx, SdfFont font, String text, float textW, float x, float y, float boxW, float size,
								int color, Render2D.TextStyle style, float time) {
		style.color = color;
		if (textW <= boxW) {
			style.fadeWidth = 0f;
			Render2D.text(ctx, font, text, x, y, size, style);
			return;
		}
		float off = marqueeOffset(textW, boxW, time);
		fade(style, x, x + boxW, off);
		ctx.enableScissor((int) Math.floor(x), (int) Math.floor(y - 2f), (int) Math.ceil(x + boxW), (int) Math.ceil(y + size * 1.4f));
		Render2D.text(ctx, font, text, x - off, y, size, style);
		if (off > 0f) Render2D.text(ctx, font, text, x - off + textW + MARQUEE_GAP, y, size, style);
		ctx.disableScissor();
	}

	private static float marqueeOffset(float textW, float boxW, float time) {
		if (textW <= boxW) return 0f;
		float span = textW + MARQUEE_GAP;
		float phase = time % (MARQUEE_PAUSE + span / MARQUEE_SPEED);
		return phase < MARQUEE_PAUSE ? 0f : (phase - MARQUEE_PAUSE) * MARQUEE_SPEED;
	}

	/** Soft edges while scrolling; the left one only grows in once the text has started to move. */
	private static void fade(Render2D.TextStyle style, float left, float right, float off) {
		style.fadeEdges(left - 10f + Math.min(off, 10f), right, off > 0f ? 10f : 0f);
	}

	private void changeTrack(Track next) {
		track = next;
		trackSince = System.nanoTime();
		measuredWith = null;
		elapsedSecond = -1;
		if (next == null) {
			title = "Nothing playing";
			status = Media.status();
			artist = status != null ? status : "Play something in Spotify";
			total = "";
		} else {
			title = next.title();
			artist = next.artist().isEmpty() ? next.album() : next.artist();
			total = time(next.durationMs() / 1000L);
		}
		progress.snap(Media.now().progress());
	}

	private void measure(boolean compact) {
		SdfFont bold = Fonts.semibold();
		if (bold == measuredWith && compact == measuredCompact) return;
		measuredWith = bold;
		measuredCompact = compact;
		SdfFont font = compact ? bold : Fonts.medium();
		titleW = bold.width(title, compact ? 7f : 8.5f);
		artistW = font.width(artist, compact ? 7f : 6.5f);
		separatorW = bold.width(SEPARATOR, 7f);
		totalW = Fonts.medium().width(total, 5.5f);
	}

	private static String time(long seconds) {
		long s = Math.max(0L, seconds);
		StringBuilder sb = new StringBuilder(8);
		if (s >= 3600L) sb.append(s / 3600L).append(':').append(s / 60L % 60L < 10L ? "0" : "");
		sb.append(s >= 3600L ? s / 60L % 60L : s / 60L).append(':');
		if (s % 60L < 10L) sb.append('0');
		return sb.append(s % 60L).toString();
	}
}
