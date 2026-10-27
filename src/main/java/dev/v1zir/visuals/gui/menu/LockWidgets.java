package dev.v1zir.visuals.gui.menu;

import dev.v1zir.visuals.V1zir;
import dev.v1zir.visuals.media.AlbumArt;
import dev.v1zir.visuals.media.Media;
import dev.v1zir.visuals.media.NowPlaying;
import dev.v1zir.visuals.media.Track;
import dev.v1zir.visuals.render.BackdropBlur;
import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.font.Icons;
import dev.v1zir.visuals.render.font.SdfFont;
import dev.v1zir.visuals.render.ui.Heads;
import dev.v1zir.visuals.render.ui.Logo;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.render.ui.ShapeElement;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.SkinTextures;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2fStack;

import java.util.function.Supplier;

/**
 * The lock screen's frosted glass: a row of widgets under the clock (what is playing, with play/pause; the last world
 * or server with its ping, which rejoins it; the player's head with the game version), a notification with the
 * greeting and a tip of the day, and two round quick actions in the bottom corners. Widgets without data are left out
 * and the row closes up on springs. Texts are fitted to their widgets only when they change.
 */
final class LockWidgets {
	static final int NONE = -1;
	static final int MEDIA = 0;
	static final int PLAY = 1;
	static final int LAST = 2;
	static final int PROFILE = 3;
	static final int CARD = 4;
	static final int SINGLE = 5;
	static final int MULTI = 6;
	private static final int COUNT = 7;
	private static final String VERSION = "Minecraft 1.21.11";

	/** x, y, w, h of every element drawn this frame; w stays 0 for the ones that were not. */
	private final float[] box = new float[COUNT * 4];
	private final Anim[] hover = new Anim[COUNT];
	private final Anim[] press = new Anim[COUNT];
	/** A widget that gets data grows into the row; one that loses it shrinks out of it. */
	private final Anim mediaShown = new Anim(0f, 7f);
	private final Anim lastShown = new Anim(0f, 7f);
	private final Anim playing = new Anim(0f, 14f);
	/** The control the mouse button went down on, or NONE. */
	int pressed = NONE;
	private boolean last;
	@Nullable
	private Supplier<SkinTextures> skin;
	/** The song shown, kept while its widget shrinks away after the player stopped. */
	@Nullable
	private Track track;
	private int pingValue = Integer.MIN_VALUE;
	private String pingText = "";
	private final Fit title = new Fit();
	private final Fit artist = new Fit();
	private final Fit lastCaption = new Fit();
	private final Fit lastName = new Fit();
	private final Fit ping = new Fit();
	private final Fit version = new Fit();
	private final Fit name = new Fit();
	private final Fit cardTitle = new Fit();
	private final Fit cardTime = new Fit();
	private final Fit cardBody = new Fit();
	private final Fit singleLabel = new Fit();
	private final Fit multiLabel = new Fit();

	LockWidgets() {
		for (int i = 0; i < COUNT; i++) {
			hover[i] = new Anim(0f, 18f);
			press[i] = new Anim(0f, 26f);
		}
	}

	/** When the screen opens or resizes: checks the last world is still there and starts loading the player's skin. */
	void init(MinecraftClient client) {
		last = LastPlayed.available(client);
		if (skin == null) skin = client.getSkinProvider().supplySkinTextures(client.getGameProfile(), false);
	}

	/** Forgets last frame's elements; the render methods record the ones they draw. */
	void begin() {
		for (int i = 0; i < COUNT; i++) box[i * 4 + 2] = 0f;
	}

	/** The control (play/pause, last played, notification, quick actions) under the cursor, or NONE. */
	int hit(double x, double y) {
		if (inside(PLAY, x, y)) return PLAY;
		for (int id = LAST; id < COUNT; id++) if (id != PROFILE && inside(id, x, y)) return id;
		return NONE;
	}

	/** Whether anything drawn here is under the cursor (a long press there must not open the wallpaper picker). */
	boolean occupied(double x, double y) {
		for (int id = 0; id < COUNT; id++) if (inside(id, x, y)) return true;
		return false;
	}

	private boolean inside(int id, double x, double y) {
		int i = id * 4;
		float w = box[i + 2];
		if (w <= 0f) return false;
		if (id == PLAY || id == SINGLE || id == MULTI) {
			double r = w / 2f;
			double dx = x - box[i] - r;
			double dy = y - box[i + 1] - r;
			return dx * dx + dy * dy <= r * r;
		}
		return x >= box[i] && x <= box[i] + w && y >= box[i + 1] && y <= box[i + 1] + box[i + 3];
	}

	private void record(int id, float x, float y, float w, float h) {
		int i = id * 4;
		box[i] = x;
		box[i + 1] = y;
		box[i + 2] = w;
		box[i + 3] = h;
	}

	/** The control's hover spring; another control held down keeps it dark. Read once per frame. */
	private float hovered(int id, int mouseX, int mouseY) {
		return hover[id].target(inside(id, mouseX, mouseY) && (pressed == NONE || pressed == id) ? 1f : 0f).get();
	}

	/** The control's press spring: held down with the cursor still over it. Read once per frame. */
	private float held(int id, int mouseX, int mouseY) {
		return press[id].target(pressed == id && inside(id, mouseX, mouseY) ? 1f : 0f).get();
	}

	/** The widget row centred on cx with its top at y, h tall. */
	void renderRow(DrawContext ctx, MinecraftClient client, MenuClock clock, float cx, float y, float h, int mouseX, int mouseY) {
		NowPlaying now = Media.now();
		if (now.track() != null) track = now.track();
		float m = mediaShown.target(now.track() != null ? 1f : 0f).get();
		float l = lastShown.target(last ? 1f : 0f).get();
		if (m <= 0.001f) track = null;
		float pad = h * 0.2f;
		float inner = h - pad * 2f;
		float gap = h * 0.24f;
		float text = h * 0.255f;
		float caption = h * 0.21f;
		SdfFont semibold = Fonts.semibold();
		SdfFont medium = Fonts.medium();

		float mediaW = h * 4.4f;
		lastCaption.fit(medium, clock.lastPlayed, caption, h * 2.4f);
		lastName.fit(semibold, LastPlayed.name, text, h * 2.8f);
		float pingW = 0f;
		if (LastPlayed.server && LastPlayed.ping >= 0) {
			if (LastPlayed.ping != pingValue) {
				pingValue = LastPlayed.ping;
				pingText = pingValue + " ms";
			}
			pingW = caption * 1.1f + ping.fit(medium, pingText, caption, h * 2f).width;
		}
		float lastW = pad * 2.1f + inner + pad * 0.8f + Math.max(lastName.width, lastCaption.width + pingW);
		version.fit(medium, VERSION, caption, h * 2.8f);
		name.fit(semibold, client.getSession().getUsername(), text, h * 2.8f);
		float profileW = pad * 2.1f + inner + pad * 0.8f + Math.max(version.width, name.width);

		float alpha = Render2D.alpha();
		float x = cx - (mediaW * m + lastW * l + profileW + gap * (m + l)) / 2f;
		if (track != null) {
			grow(ctx, x, y, mediaW, h, m, alpha);
			renderMedia(ctx, now, x + mediaW * (m - 1f) / 2f, y, mediaW, h, mouseX, mouseY);
			ctx.getMatrices().popMatrix();
			x += (mediaW + gap) * m;
		}
		if (l > 0.001f) {
			grow(ctx, x, y, lastW, h, l, alpha);
			renderLast(ctx, x + lastW * (l - 1f) / 2f, y, lastW, h, mouseX, mouseY);
			ctx.getMatrices().popMatrix();
			x += (lastW + gap) * l;
		}
		Render2D.setAlpha(alpha);
		frost(ctx, x, y, profileW, h, h * 0.3f, 0f);
		record(PROFILE, x, y, profileW, h);
		SkinTextures textures = skin != null ? skin.get() : null;
		if (textures != null) {
			Heads.skin(ctx, textures, x + pad, y + pad, inner, inner * 0.24f, 0xFFFFFFFF);
		} else {
			Render2D.icon(ctx, Icons.USER, x + pad + inner / 2f, y + h / 2f, inner * 0.6f, 0xE6FFFFFF);
		}
		float tx = x + pad + inner + pad * 0.8f;
		Render2D.textMiddle(ctx, medium, version.text, tx, y + h * 0.36f, caption, 0x99FFFFFF);
		Render2D.textMiddle(ctx, semibold, name.text, tx, y + h * 0.64f, text, 0xF2FFFFFF);
	}

	/** Scales and fades a widget that grows into the row (or shrinks out of it) about its slot's centre; pop afterwards. */
	private static void grow(DrawContext ctx, float x, float y, float w, float h, float shown, float alpha) {
		float s = 0.8f + 0.2f * shown;
		float cx = x + w * shown / 2f;
		float cy = y + h / 2f;
		Matrix3x2fStack matrices = ctx.getMatrices();
		matrices.pushMatrix();
		matrices.translate(cx, cy);
		matrices.scale(s, s);
		matrices.translate(-cx, -cy);
		Render2D.setAlpha(alpha * shown);
	}

	/** Album art, title and artist with the song's progress under them, and a play/pause button. */
	private void renderMedia(DrawContext ctx, NowPlaying now, float x, float y, float w, float h, int mouseX, int mouseY) {
		float pad = h * 0.2f;
		float inner = h - pad * 2f;
		frost(ctx, x, y, w, h, h * 0.3f, 0f);
		record(MEDIA, x, y, w, h);
		AlbumArt.sync(now.art());
		AlbumArt.draw(ctx, x + pad, y + pad, inner, inner * 0.24f, 0xFFFFFFFF, Theme.accent(), Theme.accent2());

		float br = h * 0.27f;
		float bx = x + w - pad - br;
		float by = y + h / 2f;
		record(PLAY, bx - br, by - br, br * 2f, br * 2f);
		float hov = hovered(PLAY, mouseX, mouseY);
		float down = held(PLAY, mouseX, mouseY);
		float r = br * (1f + 0.06f * hov - 0.1f * down);
		Render2D.circle(ctx, bx, by, r, ColorUtil.withAlpha(0xFFFFFF, Math.round(34 + 30 * hov + 50 * down)));
		float play = playing.target(now.playing() ? 1f : 0f).get();
		if (play > 0.004f) Render2D.icon(ctx, Icons.PAUSE, bx, by, r * 0.95f, ColorUtil.withAlpha(0xFFFFFF, Math.round(242 * play)));
		if (play < 0.996f) Render2D.icon(ctx, Icons.PLAY, bx + r * 0.06f, by, r * 0.95f, ColorUtil.withAlpha(0xFFFFFF, Math.round(242 * (1f - play))));

		float tx = x + pad + inner + pad * 0.75f;
		float max = bx - br - pad * 0.6f - tx;
		float text = h * 0.255f;
		float caption = h * 0.21f;
		SdfFont semibold = Fonts.semibold();
		SdfFont medium = Fonts.medium();
		title.fit(semibold, track.title(), text, max);
		artist.fit(medium, track.artist(), caption, max);
		Render2D.textMiddle(ctx, semibold, title.text, tx, y + h * 0.31f, text, 0xF2FFFFFF);
		Render2D.textMiddle(ctx, medium, artist.text, tx, y + h * 0.55f, caption, 0xA6FFFFFF);
		float barH = Math.max(1.5f, h * 0.045f);
		float barY = y + h * 0.76f;
		Render2D.round(ctx, tx, barY, max, barH, barH / 2f, 0x33FFFFFF);
		float done = now.progress();
		if (done > 0f) Render2D.round(ctx, tx, barY, Math.max(barH, max * done), barH, barH / 2f, 0xD9FFFFFF);
	}

	/** The last world or server: its icon, "Last played" with the ping, and its name; clicked, it rejoins. */
	private void renderLast(DrawContext ctx, float x, float y, float w, float h, int mouseX, int mouseY) {
		float hov = hovered(LAST, mouseX, mouseY);
		float sink = h * 0.03f * held(LAST, mouseX, mouseY);
		float pad = h * 0.2f;
		float inner = h - pad * 2f;
		record(LAST, x, y, w, h);
		frost(ctx, x + sink, y + sink, w - sink * 2f, h - sink * 2f, h * 0.3f, hov);
		float ix = x + pad + inner / 2f;
		float iy = y + h / 2f;
		Render2D.circle(ctx, ix, iy, inner / 2f, ColorUtil.withAlpha(0xFFFFFF, Math.round(36 + 24 * hov)));
		Render2D.icon(ctx, LastPlayed.server ? Icons.SERVER : Icons.BOX, ix, iy, inner * 0.5f, 0xF2FFFFFF);

		float tx = x + pad + inner + pad * 0.8f;
		float caption = h * 0.21f;
		float cy = y + h * 0.36f;
		Render2D.textMiddle(ctx, Fonts.medium(), lastCaption.text, tx, cy, caption, 0x99FFFFFF);
		if (LastPlayed.server && LastPlayed.ping >= 0) {
			float dot = caption * 0.36f;
			float dx = tx + lastCaption.width + caption * 0.55f;
			int ms = LastPlayed.ping;
			Render2D.circle(ctx, dx, cy, dot / 2f, ms < 80 ? 0xFF4ADE80 : ms < 160 ? 0xFFFACC15 : 0xFFF87171);
			Render2D.textMiddle(ctx, Fonts.medium(), ping.text, dx + caption * 0.55f, cy, caption, 0x99FFFFFF);
		}
		Render2D.textMiddle(ctx, Fonts.semibold(), lastName.text, tx, y + h * 0.64f, h * 0.255f, 0xF2FFFFFF);
	}

	/**
	 * A notification from the client with its icon, the greeting and the tip of the day, over a second one peeking
	 * out under it like a phone's stack; its bottom edge at bottom. Clicked, it opens the menu.
	 */
	void renderCard(DrawContext ctx, MenuClock clock, int width, int height, float bottom, int mouseX, int mouseY) {
		float h = Math.max(30f, Math.min(56f, height * 0.1f));
		float w = Math.min(width * 0.46f, h * 7.2f);
		float peek = h * 0.2f;
		float x = width / 2f - w / 2f;
		float y = bottom - h - peek;
		float r = h * 0.3f;
		record(CARD, x, y, w, h + peek);
		float hov = hovered(CARD, mouseX, mouseY);
		float sink = h * 0.025f * held(CARD, mouseX, mouseY);
		// Only the part of the card behind that sticks out is drawn, so nothing shows through while they fade.
		float alpha = Render2D.alpha();
		Render2D.setAlpha(alpha * 0.7f);
		ctx.enableScissor((int) Math.floor(x), Math.round(y + h), (int) Math.ceil(x + w), (int) Math.ceil(y + h + peek + 1f));
		frost(ctx, x + w * 0.05f, y + peek, w * 0.9f, h, r, 0f);
		ctx.disableScissor();
		Render2D.setAlpha(alpha);
		frost(ctx, x + sink, y + sink, w - sink * 2f, h - sink * 2f, r, hov * 0.6f);

		float icon = h * 0.56f;
		float ix = x + h * 0.22f;
		float iy = y + (h - icon) / 2f;
		int accent = Theme.accent();
		int accent2 = Theme.accent2();
		Render2D.round(ctx, ix, iy, icon, icon, icon * 0.27f, accent, ColorUtil.lerp(accent, accent2, 0.5f), accent2, ColorUtil.lerp(accent, accent2, 0.5f));
		Logo.draw(ctx, ix + icon * 0.18f, iy + icon * 0.18f, icon * 0.64f);

		SdfFont semibold = Fonts.semibold();
		SdfFont medium = Fonts.medium();
		float tx = ix + icon + h * 0.2f;
		float right = x + w - h * 0.26f;
		float titleSize = h * 0.22f;
		float small = h * 0.185f;
		cardTime.fit(medium, clock.nowLabel, small, h * 2f);
		cardTitle.fit(semibold, clock.greeting.isEmpty() ? V1zir.NAME : clock.greeting, titleSize, right - tx - cardTime.width - h * 0.2f);
		cardBody.fit(medium, clock.tip, small, right - tx);
		float ty = y + h * 0.37f;
		Render2D.textMiddle(ctx, semibold, cardTitle.text, tx, ty, titleSize, 0xF2FFFFFF);
		Render2D.textMiddle(ctx, medium, cardTime.text, right - cardTime.width, ty, small, 0x8CFFFFFF);
		Render2D.textMiddle(ctx, medium, cardBody.text, tx, y + h * 0.65f, small, 0xC7FFFFFF);
	}

	/** Singleplayer and multiplayer in the bottom corners, centred cy; their names show above them on hover. */
	void renderActions(DrawContext ctx, MenuClock clock, int width, int height, float cy, float radius, float inset, int mouseX, int mouseY) {
		action(ctx, SINGLE, Icons.USER, singleLabel.fit(Fonts.semibold(), clock.singleplayer, radius * 0.5f, width * 0.3f),
			inset + radius, cy, radius, width, mouseX, mouseY);
		action(ctx, MULTI, Icons.USERS, multiLabel.fit(Fonts.semibold(), clock.multiplayer, radius * 0.5f, width * 0.3f),
			width - inset - radius, cy, radius, width, mouseX, mouseY);
	}

	/** A round glass button; pressed, it lights up white with a dark icon, like a phone's flashlight. */
	private void action(DrawContext ctx, int id, int icon, Fit label, float cx, float cy, float radius, int width, int mouseX, int mouseY) {
		record(id, cx - radius, cy - radius, radius * 2f, radius * 2f);
		float hov = hovered(id, mouseX, mouseY);
		float p = held(id, mouseX, mouseY);
		float r = radius * (1f + 0.05f * hov - 0.08f * p);
		frost(ctx, cx - r, cy - r, r * 2f, r * 2f, r, hov);
		if (p > 0.004f) Render2D.circle(ctx, cx, cy, r, ColorUtil.withAlpha(0xFFFFFF, Math.round(235 * p)));
		Render2D.icon(ctx, icon, cx, cy, r * 0.8f, ColorUtil.lerp(0xF2FFFFFF, 0xFF1C1C1E, p));
		if (hov > 0.004f) {
			float size = radius * 0.5f;
			float lx = Math.max(label.width / 2f + 6f, Math.min(width - label.width / 2f - 6f, cx));
			float alpha = Render2D.alpha();
			Render2D.setAlpha(alpha * hov);
			Render2D.text(ctx, Fonts.semibold(), label.text, lx - label.width / 2f, Render2D.middle(Fonts.semibold(), cy - radius - size * 1.5f + 2f * (1f - hov), size), size, 0xF2FFFFFF);
			Render2D.setAlpha(alpha);
		}
	}

	/** A frosted lock screen surface: the blurred wallpaper lightened (lit brightens it more), or a light veil without blur. */
	private static void frost(DrawContext ctx, float x, float y, float w, float h, float r, float lit) {
		if (BackdropBlur.available()) {
			Render2D.glass(ctx, x, y, w, h, r, 0, ShapeElement.MATTE, ColorUtil.withAlpha(0xFFFFFF, Math.round(44 + 30 * lit)));
		} else {
			Render2D.matte(ctx, x, y, w, h, r, ColorUtil.withAlpha(0xFFFFFF, Math.round(34 + 22 * lit)), 0);
		}
		Render2D.rim(ctx, x, y, w, h, r, ColorUtil.withAlpha(0xFFFFFF, Math.round(24 + 26 * lit)), 0);
	}

	/** A text fitted to a width; measured again only when the text, font, size or width changes. */
	private static final class Fit {
		private SdfFont font;
		private String source = "";
		private float size;
		private float max;
		String text = "";
		float width;

		Fit fit(SdfFont font, String source, float size, float max) {
			if (font != this.font || size != this.size || max != this.max || !source.equals(this.source)) {
				this.font = font;
				this.source = source;
				this.size = size;
				this.max = max;
				text = font.trim(source, size, max);
				width = font.width(text, size);
			}
			return this;
		}
	}
}
