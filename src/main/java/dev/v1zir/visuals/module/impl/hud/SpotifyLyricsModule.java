package dev.v1zir.visuals.module.impl.hud;

import dev.v1zir.visuals.media.AlbumArt;
import dev.v1zir.visuals.media.Lyrics;
import dev.v1zir.visuals.media.LyricsService;
import dev.v1zir.visuals.media.Media;
import dev.v1zir.visuals.media.NowPlaying;
import dev.v1zir.visuals.media.Track;
import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.module.setting.ModeSetting;
import dev.v1zir.visuals.module.setting.NumberSetting;
import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.font.Icons;
import dev.v1zir.visuals.render.font.SdfFont;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.render.world.WorldEffect;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.gui.DrawContext;

/**
 * Synced lyrics of the playing song (LRCLIB) in three presets:
 * <ul>
 *   <li>Window: a card like Spotify's lyrics view; the current line big and bright, the others
 *   smaller and dimmer, spring scrolling between lines, a per-word or per-line fill and faded edges.</li>
 *   <li>3D: extruded letters appearing around the player in the world ({@link Lyrics3D}).</li>
 *   <li>Karaoke: centred subtitles above the hotbar with a fill sweeping across in time and the
 *   next line faintly below.</li>
 * </ul>
 * Lines are laid out once when the lyrics arrive (or the layout changes), never per frame.
 */
public final class SpotifyLyricsModule extends HudModule implements WorldEffect {
	private static final float WINDOW_SIZE = 9.5f;
	private static final float WINDOW_SMALL = 0.8f;
	private static final float WINDOW_ROW = 12f;
	private static final float WINDOW_GAP = 4.5f;
	private static final float WINDOW_HEADER = 22f;
	private static final float KARAOKE_SIZE = 13f;
	private static final float KARAOKE_NEXT = 8.5f;
	/** Distance of the karaoke block from the bottom of the screen: above the hotbar, hearts and armour. */
	private static final float KARAOKE_BOTTOM = 48f;
	/** How long a status (no lyrics, instrumental...) stays up before the element hides. */
	private static final float STATUS_SECONDS = 5f;

	public final ModeSetting preset = add(new ModeSetting("Preset", "Window", "Window", "3D", "Karaoke"));
	public final ModeSetting highlight = add(new ModeSetting("Highlight", "Word", "Word", "Line", "Off").visibleIf(() -> preset.is("Window")));
	public final NumberSetting lines = add(new NumberSetting("Lines", 5, 3, 9, 1).visibleIf(() -> preset.is("Window")));
	public final NumberSetting windowWidth = add(new NumberSetting("Width", 210, 160, 300, 5).visibleIf(() -> preset.is("Window")));
	public final NumberSetting size = add(new NumberSetting("Size", 1.0, 0.5, 2.0, 0.05).suffix("x").visibleIf(() -> preset.is("3D")));
	public final NumberSetting distance = add(new NumberSetting("Distance", 3.4, 2.0, 6.0, 0.1).suffix(" m").visibleIf(() -> preset.is("3D")));
	public final BoolSetting depth = add(new BoolSetting("Depth", true).visibleIf(() -> preset.is("3D")));
	public final BoolSetting glow = add(new BoolSetting("Glow", true).visibleIf(() -> preset.is("3D")));
	public final BoolSetting albumColors = add(new BoolSetting("Album colors", true));
	public final NumberSetting offset = add(new NumberSetting("Sync offset", 0, -2000, 2000, 50).suffix(" ms"));

	private final Anim appear = new Anim(0f, 10f);
	/** Fractional index of the line in the middle of the window. */
	private final Anim scroll = new Anim(0f, 5f);
	/** Fill of the current line in characters (word mode springs from word to word). */
	private final Anim wordFill = new Anim(0f, 14f);
	/** 1 right after the line changed, springing to 0: the previous line dims, karaoke lines move up. */
	private final Anim change = new Anim(0f, 6f);
	/** Whether the karaoke line is being sung (it fades out during long breaks). */
	private final Anim present = new Anim(0f, 7f);
	private final Lyrics3D world = new Lyrics3D();
	private final Render2D.TextStyle lineStyle = new Render2D.TextStyle();
	private final Render2D.TextStyle karaokeStyle = new Render2D.TextStyle().shadow(0xA0000000);

	private Track track;
	private long trackSince;
	private LyricsService.Request request;
	private Lyrics lyrics;
	private long position;
	private int index = -1;
	private int previous = -1;
	/** Header text ("title · artist") fitted to the window width. */
	private String header = "";
	private SdfFont headerFont;

	private LyricLine[] layout = new LyricLine[0];
	private Lyrics layoutLyrics;
	private SdfFont layoutFont;
	private float layoutWrap;

	/** The line the 3D preset showed last, and whether it has been dissolved already. */
	private int shown3D = -1;
	private Lyrics shown3DLyrics;
	private boolean left3D;

	public SpotifyLyricsModule() {
		super("Spotify Lyrics", "Synced lyrics of the playing song", 0.992f, 0.32f);
		setSubCategory("Media");
	}

	@Override
	public boolean showInList() {
		return false;
	}

	@Override
	public String suffix() {
		return preset.get();
	}

	@Override
	protected void onEnable() {
		Media.acquire(this);
	}

	@Override
	protected void onDisable() {
		Media.release(this);
		world.clear();
		shown3D = -1;
	}

	// ------------------------------------------------------------------ state

	/** Follows the song, its lyrics and the playing line. Cheap: identity checks unless something changed. */
	private void update() {
		NowPlaying now = Media.now();
		AlbumArt.sync(now.art());
		if (now.track() != track) {
			track = now.track();
			trackSince = System.nanoTime();
			request = track != null ? LyricsService.request(track) : null;
			headerFont = null;
		}
		Lyrics current = request != null ? request.result() : null;
		if (current != lyrics) {
			lyrics = current;
			index = -1;
			previous = -1;
			scroll.snap(-0.5f);
		}
		long duration = track != null ? track.durationMs() : 0L;
		position = now.position() + offset.i();
		int next = lyrics != null ? lyrics.index(position, duration) : -1;
		if (next != index) {
			previous = index;
			index = next;
			change.snap(1f);
			change.target(0f);
			wordFill.snap(0f);
		}
	}

	private boolean synced() {
		return lyrics != null && lyrics.hasLines() && lyrics.synced();
	}

	/** Fill of the current line from 0 to 1, finishing a little before the line's end. */
	private float lineProgress() {
		if (index < 0) return 0f;
		long duration = track.durationMs();
		long start = lyrics.time(index, duration);
		long end = lyrics.end(index, duration);
		return Anim.clamp01((position - start) / (float) Math.max(1L, end - start) * 1.08f);
	}

	/** Text shown instead of lyrics, or null when there are lyrics to show. */
	private String status() {
		if (track == null) return Media.status() != null ? Media.status() : "Nothing playing";
		if (lyrics == null || lyrics.state() == Lyrics.State.LOADING) return "Looking for lyrics…";
		return switch (lyrics.state()) {
			case INSTRUMENTAL -> "Instrumental";
			case MISSING -> "No lyrics for this song";
			case ERROR -> "Lyrics are unavailable";
			default -> preset.is("Window") || lyrics.synced() ? null : "No synced lyrics";
		};
	}

	private boolean visible() {
		if (editing()) return true;
		if (track == null || preset.is("3D")) return false;
		boolean showing = preset.is("Window") ? lyrics != null && lyrics.hasLines() : synced();
		return showing || (System.nanoTime() - trackSince) / 1.0e9f < STATUS_SECONDS;
	}

	@Override
	public boolean hasContent() {
		update();
		appear.target(visible() ? 1f : 0f);
		return appear.get() > 0.01f;
	}

	private int accent() {
		return albumColors.on() ? AlbumArt.color(Theme.accent()) : Theme.accent();
	}

	/** Lays out every line for the font and wrap width (size 1 units) when either or the lyrics changed. */
	private void layout(SdfFont font, float wrap) {
		if (lyrics == layoutLyrics && font == layoutFont && wrap == layoutWrap) return;
		layoutLyrics = lyrics;
		layoutFont = font;
		layoutWrap = wrap;
		int count = lyrics != null ? lyrics.size() : 0;
		layout = new LyricLine[count];
		for (int i = 0; i < count; i++) layout[i] = LyricLine.of(lyrics.line(i), font, wrap);
	}

	// ------------------------------------------------------------------ placement

	@Override
	public float x(int screenWidth) {
		return preset.is("Karaoke") ? (screenWidth - width) / 2f : super.x(screenWidth);
	}

	@Override
	public float y(int screenHeight) {
		return preset.is("Karaoke") ? screenHeight - KARAOKE_BOTTOM - height : super.y(screenHeight);
	}

	@Override
	public void moveTo(float x, float y, int screenWidth, int screenHeight) {
		// Karaoke subtitles have a fixed place.
		if (!preset.is("Karaoke")) super.moveTo(x, y, screenWidth, screenHeight);
	}

	// ------------------------------------------------------------------ 2D presets

	@Override
	protected void render(DrawContext ctx, float tickDelta, float x, float y) {
		update();
		appear.target(visible() ? 1f : 0f);
		Render2D.setAlpha(appear.get());
		if (preset.is("Window")) renderWindow(ctx, x, y);
		else if (preset.is("Karaoke")) renderKaraoke(ctx, x, y);
		else renderWorldChip(ctx, x, y);
		Render2D.setAlpha(1f);
	}

	private void renderWindow(DrawContext ctx, float x, float y) {
		float w = windowWidth.f();
		float areaH = lines.i() * (WINDOW_ROW + WINDOW_GAP * 0.6f);
		float h = WINDOW_HEADER + areaH + 6f;
		width = w;
		height = h;
		int accent = accent();
		panel(ctx, x, y, w, h, 8f);
		Render2D.gradientV(ctx, x, y, w, h * 0.6f, 8f, ColorUtil.withAlpha(accent, 26), ColorUtil.withAlpha(accent, 0));

		// Header: the cover, "title · artist" and the microphone.
		AlbumArt.draw(ctx, x + 8f, y + 5f, 12f, 3f, 0xFFFFFFFF, accent, Theme.accent2());
		SdfFont small = Fonts.medium();
		if (headerFont != small) {
			headerFont = small;
			header = track == null ? "Spotify Lyrics" : small.trim(track.artist().isEmpty() ? track.title() : track.title() + " · " + track.artist(), 6.5f, w - 50f);
		}
		Render2D.textMiddle(ctx, small, header, x + 25f, y + 11f, 6.5f, Theme.textDim());
		Render2D.icon(ctx, Icons.MIC_VOCAL, x + w - 13f, y + 11f, 8f, accent);
		Render2D.rect(ctx, x + 8f, y + WINDOW_HEADER - 2f, w - 16f, 1f, ColorUtil.withAlpha(0xFFFFFF, 14));

		float top = y + WINDOW_HEADER;
		float bottom = y + h - 4f;
		float tx = x + 10f;
		String status = status();
		if (status != null || lyrics == null || !lyrics.hasLines()) {
			Render2D.textCentered(ctx, Fonts.semibold(), status != null ? status : "", x + w / 2f,
				Render2D.middle(Fonts.semibold(), (top + bottom) / 2f, 8f), 8f, Theme.textDim());
			return;
		}
		SdfFont font = Fonts.bold();
		layout(font, (w - 20f) / WINDOW_SIZE);
		int n = layout.length;
		float f = scroll.target(index < 0 ? -0.5f : index).get();
		float cy = (top + bottom) / 2f;
		// The line at the fractional scroll position sits in the middle; walk up to the first visible line, then draw down.
		int k = Math.max(0, Math.min(n - 1, (int) Math.floor(f)));
		float c = cy - (f - k) * (half(k, f) + half(Math.min(k + 1, n - 1), f));
		int i = k;
		while (i > 0 && c - half(i, f) > top) {
			c -= half(i, f) + half(i - 1, f);
			i--;
		}
		ctx.enableScissor((int) x, (int) top, (int) (x + w), (int) Math.ceil(bottom));
		while (i < n && c - half(i, f) < bottom) {
			windowLine(ctx, i, tx, c, f, top, bottom, accent);
			c += half(i, f) + (i + 1 < n ? half(i + 1, f) : 0f);
			i++;
		}
		ctx.disableScissor();
	}

	/** Half the height of a window line at the scroll position (lines shrink away from the middle). */
	private float half(int i, float f) {
		float scale = WINDOW_SMALL + (1f - WINDOW_SMALL) * Anim.clamp01(1f - Math.abs(i - f));
		return (layout[i].rowCount() * WINDOW_ROW * scale + WINDOW_GAP) * 0.5f;
	}

	private void windowLine(DrawContext ctx, int i, float tx, float center, float f, float top, float bottom, int accent) {
		LyricLine line = layout[i];
		float focus = Anim.clamp01(1f - Math.abs(i - f));
		float scale = WINDOW_SMALL + (1f - WINDOW_SMALL) * focus;
		float textSize = WINDOW_SIZE * scale;
		float rowH = WINDOW_ROW * scale;
		// Faded towards the top and bottom edges of the window.
		float edge = Anim.clamp01(Math.min(center - top, bottom - center) / 18f);
		float y0 = center - line.rowCount() * rowH / 2f;
		if (line.isBreak()) {
			dots(ctx, tx + 3f * scale, center, scale, i == index, ColorUtil.fade(i == index ? 0xFFFFFFFF : 0x88FFFFFF, edge));
			return;
		}
		boolean current = i == index;
		int base;
		if (current) base = 0x80FFFFFF;
		else if (i < index) base = ColorUtil.lerp(0x5AFFFFFF, 0xFFFFFFFF, i == previous ? change.get() : 0f);
		else base = 0x78FFFFFF;
		lineStyle.color = ColorUtil.fade(base, edge);
		lineStyle.sweepColor = 0;
		float fill = 0f;
		if (current && lyrics.synced()) {
			int length = line.text.length();
			fill = switch (highlight.get()) {
				case "Line" -> lineProgress() * length;
				case "Word" -> wordFill.target(line.wordEnd(lineProgress() * length)).get();
				default -> length;
			};
		} else if (current) {
			fill = line.text.length();
		}
		if (current && focus > 0.5f) {
			// A soft accent glow behind the sung line.
			Render2D.shadow(ctx, tx - 4f, y0, line.widest() * textSize + 8f, line.rowCount() * rowH, 6f, 10f,
				ColorUtil.withAlpha(accent, Math.round(26 * edge * (focus - 0.5f) * 2f)));
		}
		SdfFont font = Fonts.bold();
		for (int r = 0; r < line.rowCount(); r++) {
			if (current) {
				float sweep = fill >= line.end[r] ? Float.MAX_VALUE / 4f : tx + line.x(r, fill) * textSize;
				lineStyle.sweep(ColorUtil.fade(0xFFFFFFFF, edge), sweep, 5f * scale);
			}
			Render2D.text(ctx, font, line.rows[r], tx, y0 + r * rowH, textSize, lineStyle);
		}
	}

	/** Three dots for an instrumental break; they breathe one after another while it plays. */
	private static void dots(DrawContext ctx, float x, float cy, float scale, boolean active, int color) {
		float t = (float) ((System.nanoTime() / 1.0e9) % 1000.0);
		for (int d = 0; d < 3; d++) {
			float pulse = active ? 0.5f + 0.5f * (float) Math.sin(t * 4f - d * 0.9f) : 0f;
			float r = (1.6f + 0.6f * pulse) * scale;
			Render2D.circle(ctx, x + d * 6.5f * scale, cy, r, ColorUtil.fade(color, 0.55f + 0.45f * pulse));
		}
	}

	private void renderKaraoke(DrawContext ctx, float x, float y) {
		int screen = ctx.getScaledWindowWidth();
		SdfFont font = Fonts.bold();
		layout(font, Math.min(screen * 0.8f, 440f) / KARAOKE_SIZE);
		float rowH = KARAOKE_SIZE * 1.25f;
		LyricLine line = synced() && index >= 0 ? layout[index] : null;
		boolean singing = line != null && !line.isBreak() && position < lyrics.end(index, track.durationMs());
		float shown = present.target(singing ? 1f : 0f).get();
		int rows = line != null ? line.rowCount() : 1;
		float widest = line != null ? line.widest() * KARAOKE_SIZE : 120f;
		float w = Math.max(widest, 120f) + 28f;
		float h = rows * rowH + KARAOKE_NEXT * 1.6f + 10f;
		width = w;
		height = h;
		float cx = x + w / 2f;
		int fillColor = ColorUtil.lerp(accent(), 0xFFFFFFFF, 0.25f);
		String status = status();
		if (status != null && line == null) {
			Render2D.textCentered(ctx, Fonts.semibold(), status, cx, y + h / 2f - 4f, 8f, ColorUtil.withAlpha(0xFFFFFF, 170));
			return;
		}
		if (line == null) return;
		float moving = change.get();
		float alpha = Render2D.alpha();
		// A soft scrim keeps the subtitles readable on any background.
		Render2D.shadow(ctx, cx - w / 2f + 6f, y + 2f, w - 12f, rows * rowH + 4f, 12f, 16f, ColorUtil.withAlpha(0, Math.round(80 * shown)));

		// The previous line moves up and fades out while the new one rises from the "next" spot.
		if (previous >= 0 && previous < layout.length && moving > 0.01f && !layout[previous].isBreak()) {
			LyricLine old = layout[previous];
			karaokeStyle.sweep(fillColor, Float.MAX_VALUE / 4f, 1f);
			karaokeStyle.color = 0xFFFFFFFF;
			Render2D.setAlpha(alpha * moving * 0.8f);
			float oy = y - (1f - moving) * 12f;
			for (int r = 0; r < old.rowCount(); r++) {
				Render2D.textCentered(ctx, font, old.rows[r], cx, oy + r * rowH, KARAOKE_SIZE, karaokeStyle);
			}
		}
		float enter = 1f - moving;
		float scale = KARAOKE_NEXT / KARAOKE_SIZE + (1f - KARAOKE_NEXT / KARAOKE_SIZE) * enter;
		float textSize = KARAOKE_SIZE * scale;
		float ly = y + moving * (rows * rowH + 4f);
		Render2D.setAlpha(alpha * shown * (0.45f + 0.55f * enter));
		float chars = lineProgress() * line.text.length();
		karaokeStyle.color = 0xC8FFFFFF;
		for (int r = 0; r < line.rowCount(); r++) {
			float rowW = line.width(r) * textSize;
			float left = cx - rowW / 2f;
			float sweep = chars >= line.end[r] ? Float.MAX_VALUE / 4f : left + line.x(r, chars) * textSize;
			karaokeStyle.sweep(fillColor, sweep, 8f);
			Render2D.text(ctx, font, line.rows[r], left, ly + r * rowH * scale, textSize, karaokeStyle);
		}
		// The next line, faint and small, below.
		if (index + 1 < layout.length && !layout[index + 1].isBreak()) {
			Render2D.setAlpha(alpha * 0.5f * enter);
			LyricLine next = layout[index + 1];
			Render2D.textCentered(ctx, Fonts.semibold(), next.rows[0], cx, y + rows * rowH + 4f, KARAOKE_NEXT, 0xFFFFFFFF);
		}
		Render2D.setAlpha(alpha);
	}

	/** In the HUD editor the 3D preset shows a small chip, so the element can still be found and switched. */
	private void renderWorldChip(DrawContext ctx, float x, float y) {
		width = 92f;
		height = 18f;
		panel(ctx, x, y, width, height, 9f);
		Render2D.icon(ctx, Icons.MIC_VOCAL, x + 11f, y + 9f, 8f, accent());
		Render2D.textMiddle(ctx, Fonts.medium(), "Lyrics in 3D", x + 20f, y + 9f, 6.5f, Theme.text());
	}

	// ------------------------------------------------------------------ 3D preset

	@Override
	public void render3D(float tickDelta) {
		if (!preset.is("3D")) {
			if (!world.isEmpty()) world.clear();
			shown3D = -1;
			return;
		}
		update();
		float letter = 0.26f * size.f();
		if (synced() && index >= 0) {
			if (index != shown3D || lyrics != shown3DLyrics) {
				shown3D = index;
				shown3DLyrics = lyrics;
				left3D = false;
				world.show(lyrics.line(index), letter, distance.f());
			} else if (!left3D && position >= lyrics.end(index, track.durationMs())) {
				left3D = true;
				world.leaveAll();
			}
		} else if (!left3D) {
			left3D = true;
			shown3D = -1;
			world.leaveAll();
		}
		if (world.isEmpty()) return;
		int accent = accent();
		int face = ColorUtil.lerp(0xFFFFFFFF, accent, 0.12f);
		world.render(letter, face, depth.on() ? ColorUtil.darken(accent, 0.1f) : 0, glow.on() ? accent : 0);
	}
}
