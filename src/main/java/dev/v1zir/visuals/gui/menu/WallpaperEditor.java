package dev.v1zir.visuals.gui.menu;

import dev.v1zir.visuals.config.ConfigManager;
import dev.v1zir.visuals.gui.clickgui.Label;
import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.font.Icons;
import dev.v1zir.visuals.render.font.SdfFont;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.render.wallpaper.Wallpaper;
import dev.v1zir.visuals.render.wallpaper.WallpaperImages;
import dev.v1zir.visuals.render.wallpaper.WallpaperRenderer;
import dev.v1zir.visuals.render.wallpaper.WallpaperStyle;
import dev.v1zir.visuals.render.wallpaper.WallpaperTarget;
import dev.v1zir.visuals.render.wallpaper.Wallpapers;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.TextureSetup;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The title screen's wallpaper picker, opened by holding the mouse button on an empty spot of the lock screen or the
 * home screen. The screen's wallpaper zooms out into the middle card of a carousel and its content shrinks and fades
 * with it. Every card is a live preview: the focused one and its neighbours animate, cards further away keep the
 * frame they were last drawn with. Under the cards: the name, a page indicator, the focused wallpaper's adjustments
 * (blur and dim; fill or fit and motion for pictures) and the actions. Done applies and zooms back in; Cancel (Esc)
 * zooms back into the wallpaper the screen had. While closed it does nothing and holds no textures.
 */
final class WallpaperEditor {
	private static final float RADIUS = 14f;
	/** Scale of the cards beside the focused one. */
	private static final float SIDE = 0.86f;
	/** Brightness the title screen draws its wallpapers at (the cards show the same). */
	static final int LEVEL = 0xD2;
	private static final long CONFIRM_NANOS = 4_000_000_000L;
	private static final float CONTROL_H = 16f;

	private final MinecraftClient client = MinecraftClient.getInstance();
	private final Anim zoom = new Anim(0f, 6f);
	private final Anim pos = new Anim(0f, 9f);
	private final Anim bothT = new Anim(0f, 14f);
	private final Anim imageT = new Anim(0f, 10f);
	private final Anim fitT = new Anim(0f, 16f);
	private final Anim motionT = new Anim(1f, 14f);
	private final Anim tabX = new Anim(0f, 12f);
	private final Anim tabW = new Anim(0f, 12f);
	private final Anim armT = new Anim(0f, 14f);
	/** Edited adjustments of this session by wallpaper id; saved on Done. */
	private final Map<String, WallpaperStyle> drafts = new HashMap<>();
	private final Map<String, WallpaperTarget> cards = new HashMap<>();
	/** Cards whose target holds a rendered frame. */
	private final Set<String> drawn = new HashSet<>();
	private final float[] rect = new float[4];

	private final Control done = new Control();
	private final Control cancel = new Control();
	private final Control both = new Control();
	private final Control add = new Control();
	private final Control folder = new Control();
	private final Control refresh = new Control();
	private final Control blur = new Control();
	private final Control dim = new Control();
	private final Control fill = new Control();
	private final Control fit = new Control();
	private final Control motion = new Control();
	private final Control delete = new Control();
	private final Control[] tabs = {new Control(), new Control(), new Control()};
	private final Label title = new Label();
	private final Label blurLabel = new Label();
	private final Label dimLabel = new Label();
	private final Label motionLabel = new Label();
	private final Label bothLabel = new Label();
	private final Label nameLabel = new Label();
	private final Label pageLabel = new Label();
	private String page = "";
	private int pageFor = -1;

	private List<Wallpaper> list = List.of();
	private Wallpapers.Screen screen = Wallpapers.Screen.HOME;
	private boolean open;
	private boolean polish;
	private boolean useForBoth;
	/** The category marker jumps to its tab the first time, then slides. */
	private boolean tabsPlaced;
	private int focus;
	/** The card that zooms: the screen's wallpaper while opening, the chosen one while closing. */
	private int zoomIndex;
	/** Whether the screen-sized target holds the zooming card this frame. */
	private boolean bigReady;
	private int armed = -1;
	private long armedAt;
	private float scroll;
	private int slider = -1;
	private boolean pressed;
	private boolean dragging;
	private double pressX;
	private float pressPos;
	private float dragPos;
	private float samplePos;
	private long sampleTime;
	private float velocity;

	private int width;
	private int height;
	private float e;
	private float p;
	private float cardW;
	private float cardH;
	private float cardY;
	private float spacing;

	void open(Wallpapers.Screen screen, boolean polish) {
		this.screen = screen;
		this.polish = polish;
		open = true;
		list = Wallpapers.all();
		drafts.clear();
		useForBoth = false;
		bothT.snap(0f);
		armed = -1;
		slider = -1;
		pressed = false;
		dragging = false;
		focus = Math.max(0, list.indexOf(Wallpapers.selected(screen)));
		zoomIndex = focus;
		pos.snap(focus);
		tabsPlaced = false;
		zoom.target(1f);
	}

	/** Accepts input (it does not while it zooms back in). */
	boolean active() {
		return open;
	}

	Wallpapers.Screen screen() {
		return screen;
	}

	/** 0 closed to 1 open; read once per frame by the screen. */
	float zoom() {
		return e;
	}

	/** Opacity of the screen's own content (clock, buttons), gone halfway through the zoom. */
	float contentAlpha() {
		return Anim.clamp01(1f - e * 2f);
	}

	private void close(boolean apply) {
		if (!open) return;
		open = false;
		slider = -1;
		pressed = false;
		dragging = false;
		armed = -1;
		if (apply && focus < list.size()) {
			for (Map.Entry<String, WallpaperStyle> draft : drafts.entrySet()) Wallpapers.store(screen, draft.getKey(), draft.getValue());
			Wallpaper wallpaper = list.get(focus);
			WallpaperStyle style = style(focus);
			Wallpapers.apply(screen, wallpaper, style);
			if (useForBoth) Wallpapers.apply(screen == Wallpapers.Screen.LOCK ? Wallpapers.Screen.HOME : Wallpapers.Screen.LOCK, wallpaper, style);
			ConfigManager.save();
			zoomIndex = focus;
		} else {
			drafts.clear();
			zoomIndex = Math.max(0, list.indexOf(Wallpapers.selected(screen)));
		}
		pos.target(zoomIndex);
		zoom.target(0f);
	}

	private WallpaperTarget big() {
		return screen == Wallpapers.Screen.LOCK ? WallpaperRenderer.LOCK : WallpaperRenderer.HOME;
	}

	private WallpaperStyle style(int index) {
		String id = list.get(index).id;
		WallpaperStyle draft = drafts.get(id);
		return draft != null ? draft : Wallpapers.style(screen, id);
	}

	/** The focused wallpaper's adjustments, copied into this session's drafts on the first change. */
	private WallpaperStyle draft() {
		String id = list.get(focus).id;
		WallpaperStyle draft = drafts.get(id);
		if (draft == null) {
			draft = Wallpapers.style(screen, id).copy();
			drafts.put(id, draft);
		}
		return draft;
	}

	private int count() {
		return list.size() + 1;
	}

	private void go(int index) {
		focus = Math.max(0, Math.min(count() - 1, index));
		pos.target(focus);
		armed = -1;
	}

	// ------------------------------------------------------------------ rendering

	/**
	 * Queues the previews and draws the backdrop and the cards, the zooming one last; the screen then draws its own
	 * content into that card (pushContentTransform) and the controls on top. Returns whether the picker is visible.
	 */
	boolean render(DrawContext ctx, int width, int height, int mouseX, int mouseY, float px, float py) {
		e = zoom.get();
		if (!open && e <= 0.001f) {
			release();
			return false;
		}
		this.width = width;
		this.height = height;
		delete.alpha = 0f;
		takePicked();
		p = pos.get();
		if (open && e >= 0.999f) zoomIndex = Math.min(focus, list.size() - 1);
		layout();
		queue(px, py);

		// Behind the cards: the focused wallpaper again, stretched from its small preview (so it comes out soft), dark.
		Render2D.rect(ctx, 0f, 0f, width, height, ColorUtil.withAlpha(0x060607, Math.round(255 * e)));
		int lo = (int) Math.floor(p);
		float frac = p - lo;
		backdrop(ctx, lo, (1f - frac) * e);
		backdrop(ctx, lo + 1, frac * e);
		int top = ColorUtil.withAlpha(0x000000, Math.round(150 * e));
		int bottom = ColorUtil.withAlpha(0x000000, Math.round(190 * e));
		Render2D.round(ctx, 0f, 0f, width, height, 0f, top, top, bottom, bottom);

		int f = Math.round(p);
		for (int k = 3; k >= 1; k--) {
			drawCardAt(ctx, f - k, mouseX, mouseY);
			drawCardAt(ctx, f + k, mouseX, mouseY);
		}
		if (f != zoomIndex) drawCardAt(ctx, f, mouseX, mouseY);
		drawCard(ctx, zoomIndex, mouseX, mouseY);
		return true;
	}

	private void drawCardAt(DrawContext ctx, int index, int mouseX, int mouseY) {
		if (index >= 0 && index < count() && index != zoomIndex) drawCard(ctx, index, mouseX, mouseY);
	}

	private void layout() {
		float aspect = width / (float) Math.max(height, 1);
		cardH = Math.min(height * 0.48f, width * 0.56f / aspect);
		cardW = cardH * aspect;
		cardY = height * 0.43f;
		spacing = cardW * (0.5f + SIDE * 0.5f) + Math.max(10f, cardW * 0.06f);
	}

	/** Rectangle of a card at the current scroll and zoom (rect: x, y, w, h); returns its opacity. */
	private float cardRect(int index) {
		float d = index - p;
		float s = 1f - (1f - SIDE) * Math.min(Math.abs(d), 1f);
		float w = cardW * s;
		float h = cardH * s;
		float cx = width / 2f + d * spacing;
		if (index == zoomIndex) {
			rect[0] = lerp(0f, cx - w / 2f, e);
			rect[1] = lerp(0f, cardY - h / 2f, e);
			rect[2] = lerp(width, w, e);
			rect[3] = lerp(height, h, e);
			return 1f;
		}
		// The others slide in from the sides as the focused card shrinks.
		cx += Math.signum(d) * (1f - e) * width * 0.5f;
		rect[0] = cx - w / 2f;
		rect[1] = cardY - h / 2f;
		rect[2] = w;
		rect[3] = h;
		return e;
	}

	private static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}

	/** Renders the previews this frame needs: the zooming card at screen size, nearby cards live, far ones once. */
	private void queue(float px, float py) {
		float scale = (float) client.getWindow().getScaleFactor();
		float aspect = width / (float) Math.max(height, 1);
		int stills = 2;
		int f = Math.round(p);
		for (int i = Math.max(0, f - 3); i <= Math.min(list.size() - 1, f + 3); i++) {
			cardRect(i);
			if (rect[0] + rect[2] < 0f || rect[0] > width) continue;
			Wallpaper wallpaper = list.get(i);
			boolean live = Math.abs(i - p) < 1.5f;
			if (!live && (drawn.contains(wallpaper.id) || stills-- <= 0)) continue;
			WallpaperTarget target = cards.get(wallpaper.id);
			if (target == null) {
				target = new WallpaperTarget("V1zir wallpaper card");
				cards.put(wallpaper.id, target);
			}
			WallpaperStyle style = style(i);
			float s = WallpaperRenderer.scale(wallpaper, style) * scale;
			if (WallpaperRenderer.queue(target, wallpaper, style, Math.round(cardW * s), Math.round(cardH * s), aspect, 0f, 0f, true)) {
				drawn.add(wallpaper.id);
			}
		}
		bigReady = false;
		if (e < 0.999f && zoomIndex < list.size()) {
			Wallpaper wallpaper = list.get(zoomIndex);
			// The mouse parallax of the screen fades out as the card shrinks, so the card matches its small preview.
			bigReady = WallpaperRenderer.queueScreen(big(), wallpaper, style(zoomIndex), px * (1f - e), py * (1f - e));
			if (bigReady) WallpaperRenderer.backdrop(big());
		}
	}

	private void backdrop(DrawContext ctx, int index, float alpha) {
		if (alpha <= 0.004f || index < 0 || index >= list.size()) return;
		TextureSetup texture = preview(list.get(index));
		if (texture == null) return;
		int v = Math.round(100 * (1f - 0.85f * style(index).dim));
		Render2D.wallpaper(ctx, texture, 0f, 0f, width, height, 0f, 0, ColorUtil.argb(Math.round(255 * alpha), v, v, v));
	}

	/** The card's small preview, or null before it was rendered. */
	private TextureSetup preview(Wallpaper wallpaper) {
		WallpaperTarget target = cards.get(wallpaper.id);
		return target != null && target.ready() && drawn.contains(wallpaper.id) ? target.textureSetup() : null;
	}

	/** Moves the matrices so the screen's own content shrinks with the zooming card; pop them afterwards. */
	void pushContentTransform(DrawContext ctx) {
		cardRect(zoomIndex);
		float s = rect[2] / Math.max(width, 1);
		Matrix3x2fStack matrices = ctx.getMatrices();
		matrices.pushMatrix();
		matrices.translate(rect[0] + rect[2] / 2f, rect[1] + rect[3] / 2f);
		matrices.scale(s, s);
		matrices.translate(-width / 2f, -height / 2f);
	}

	private void drawCard(DrawContext ctx, int index, int mouseX, int mouseY) {
		float alpha = cardRect(index);
		if (alpha <= 0.004f) return;
		float x = rect[0];
		float y = rect[1];
		float w = rect[2];
		float h = rect[3];
		if (x + w < 0f || x > width) return;
		float near = 1f - Math.min(Math.abs(index - p), 1f);
		float radius = RADIUS * (w / cardW) * Anim.clamp01(e * 1.6f);
		float saved = Render2D.alpha();
		Render2D.setAlpha(saved * alpha);
		Render2D.shadow(ctx, x, y + 5f * e, w, h, radius, 22f, ColorUtil.withAlpha(0x000000, Math.round(130 * e)));
		if (index == list.size()) {
			drawAddCard(ctx, x, y, w, h, radius, near);
			Render2D.setAlpha(saved);
			return;
		}
		Wallpaper wallpaper = list.get(index);
		TextureSetup texture = index == zoomIndex && e < 0.999f && bigReady ? big().textureSetup() : preview(wallpaper);
		int level = Math.round(LEVEL * (0.72f + 0.28f * near));
		if (texture != null) {
			Render2D.wallpaper(ctx, texture, x, y, w, h, radius, 0, style(index).tint(level));
		} else {
			placeholder(ctx, wallpaper, x, y, w, h, radius);
		}
		if (index == focus && open) {
			Render2D.outline(ctx, x, y, w, h, radius, 1f, ColorUtil.withAlpha(0xFFFFFF, Math.round(46 * e)));
			if (wallpaper.kind == Wallpaper.Kind.IMAGE) drawDelete(ctx, x + w, y, mouseX, mouseY);
		}
		Render2D.setAlpha(saved);
	}

	private void placeholder(DrawContext ctx, Wallpaper wallpaper, float x, float y, float w, float h, float radius) {
		Render2D.round(ctx, x, y, w, h, radius, 0xFF0D0D10);
		boolean failed = wallpaper.kind == Wallpaper.Kind.IMAGE && WallpaperImages.get(wallpaper, true) == null;
		float cx = x + w / 2f;
		float cy = y + h / 2f;
		Render2D.icon(ctx, Icons.IMAGE, cx, cy - 5f, 14f, 0x66FFFFFF);
		String text = failed ? (polish ? "Nie można odczytać pliku" : "Can't read this file") : (polish ? "Wczytywanie…" : "Loading…");
		Render2D.textCentered(ctx, Fonts.medium(), text, cx, cy + 6f, 6.5f, 0x80FFFFFF);
	}

	private void drawAddCard(DrawContext ctx, float x, float y, float w, float h, float radius, float near) {
		Render2D.matte(ctx, x, y, w, h, radius, 0xFF101013, 0);
		Render2D.outline(ctx, x, y, w, h, radius, 1f, ColorUtil.withAlpha(0xFFFFFF, Math.round(28 + 30 * near)));
		float cx = x + w / 2f;
		float cy = y + h / 2f;
		float r = Math.min(w, h) * 0.12f;
		Render2D.circle(ctx, cx, cy - r * 0.4f, r, ColorUtil.withAlpha(0xFFFFFF, Math.round(18 + 20 * near)));
		Render2D.icon(ctx, Icons.PLUS, cx, cy - r * 0.4f, r * 1.1f, 0xE6FFFFFF);
		float size = Math.max(6.5f, Math.min(8.5f, h * 0.06f));
		Render2D.textCentered(ctx, Fonts.semibold(), polish ? "Dodaj obraz" : "Add image", cx, cy + r * 0.8f + 2f, size, 0xE6FFFFFF);
		Render2D.textCentered(ctx, Fonts.medium(), polish ? "PNG, JPG lub GIF" : "PNG, JPG or GIF", cx, cy + r * 0.8f + size + 5f, size * 0.85f, 0x80FFFFFF);
	}

	/** Trash button in the corner of a focused picture; the first press arms it, the second deletes the file. */
	private void drawDelete(DrawContext ctx, float right, float top, int mouseX, int mouseY) {
		if (armed >= 0 && System.nanoTime() - armedAt > CONFIRM_NANOS) armed = -1;
		float a = armT.target(armed == focus ? 1f : 0f).get();
		SdfFont font = Fonts.semibold();
		String confirm = polish ? "Usunąć?" : "Delete?";
		float size = 18f;
		float w = size + a * (delete.label.width(font, confirm, 7f) + 8f);
		delete.set(right - 8f - w, top + 8f, w, size, Anim.clamp01(e * 2f - 1f));
		float hover = delete.hover(mouseX, mouseY);
		Render2D.shadow(ctx, delete.x, delete.y + 1f, delete.w, delete.h, size / 2f, 8f, 0x60000000);
		int fill = ColorUtil.lerp(ColorUtil.withAlpha(0x000000, Math.round(130 + 40 * hover)), ColorUtil.withAlpha(0xE5484D, Math.round(225 + 30 * hover)), a);
		Render2D.round(ctx, delete.x, delete.y, delete.w, delete.h, size / 2f, fill);
		Render2D.icon(ctx, Icons.TRASH_2, delete.x + size / 2f, delete.y + size / 2f, 8.5f, 0xF2FFFFFF);
		if (a > 0.05f) {
			float saved = Render2D.alpha();
			Render2D.setAlpha(saved * a);
			Render2D.textMiddle(ctx, font, confirm, delete.x + size - 2f, delete.y + size / 2f, 7f, 0xFFFFFFFF);
			Render2D.setAlpha(saved);
		}
	}

	/** Title, categories, name, page indicator, adjustments and actions; drawn over the screen's shrunk content. */
	void renderControls(DrawContext ctx, int mouseX, int mouseY) {
		// They come in once the card has nearly settled, and go first when it zooms back in.
		float alpha = Anim.clamp01((e - 0.7f) / 0.3f);
		if (alpha <= 0.004f) return;
		int mx = open ? mouseX : -10000;
		int my = open ? mouseY : -10000;
		Render2D.setAlpha(alpha);
		boolean picture = focus >= list.size() || list.get(focus).kind == Wallpaper.Kind.IMAGE;
		float imageShown = imageT.target(picture ? 1f : 0f).get();
		renderHeader(ctx, mx, my);
		// Name and dots sit under the zooming card until it has settled into place.
		cardRect(zoomIndex);
		float below = Math.max(cardY + cardH / 2f, rect[1] + rect[3]);
		renderName(ctx, below);
		float rowY = renderPages(ctx, below) + 10f;
		if (focus < list.size()) renderAdjustments(ctx, rowY, mx, my, imageShown);
		else hideAdjustments();
		renderActions(ctx, mx, my, imageShown);
		Render2D.setAlpha(1f);
	}

	private void renderHeader(DrawContext ctx, int mx, int my) {
		SdfFont font = Fonts.semibold();
		float size = Math.max(8f, Math.min(11f, height * 0.03f));
		String text = screen == Wallpapers.Screen.LOCK
			? (polish ? "Tapeta ekranu blokady" : "Lock Screen wallpaper")
			: (polish ? "Tapeta ekranu głównego" : "Home wallpaper");
		float w = title.width(font, text, size);
		float y = Math.max(10f, height * 0.045f) + size / 2f;
		float icon = size * 1.1f;
		float x = width / 2f - (w + icon + 5f) / 2f;
		Render2D.icon(ctx, screen == Wallpapers.Screen.LOCK ? Icons.LOCK : Icons.HOUSE, x + icon / 2f, y, icon, 0xCCFFFFFF);
		Render2D.textMiddle(ctx, font, text, x + icon + 5f, y, size, 0xF2FFFFFF);

		// Categories: the one the focused card belongs to is marked; clicking jumps to its first card.
		SdfFont tabFont = Fonts.medium();
		float tabSize = Math.max(6.5f, size * 0.72f);
		float tabY = y + size + 4f;
		float total = 0f;
		for (int i = 0; i < 3; i++) total += tabs[i].label.width(tabFont, tabName(i), tabSize) + 14f;
		float tx = width / 2f - total / 2f;
		int active = category(Math.round(p));
		for (int i = 0; i < 3; i++) {
			Control tab = tabs[i];
			float tw = tab.label.width(tabFont, tabName(i), tabSize) + 14f;
			tab.set(tx, tabY, tw, tabSize + 7f, 1f);
			float hover = tab.hover(mx, my);
			if (i == active) {
				if (tabsPlaced) {
					tabX.target(tx);
					tabW.target(tw);
				} else {
					tabX.snap(tx);
					tabW.snap(tw);
					tabsPlaced = true;
				}
			}
			int color = i == active ? 0xF2FFFFFF : ColorUtil.lerp(0x8CFFFFFF, 0xCCFFFFFF, hover);
			Render2D.textCentered(ctx, tabFont, tabName(i), tx + tw / 2f, Render2D.middle(tabFont, tabY + tab.h / 2f, tabSize), tabSize, color);
			tx += tw;
		}
		Render2D.round(ctx, tabX.get(), tabY, tabW.get(), tabs[0].h, tabs[0].h / 2f, 0x1FFFFFFF);
	}

	private String tabName(int category) {
		return switch (category) {
			case 0 -> polish ? "Animowane" : "Live";
			case 1 -> polish ? "Niebo" : "Skies";
			default -> polish ? "Obrazy" : "Pictures";
		};
	}

	private int category(int index) {
		if (index >= list.size()) return 2;
		return switch (list.get(index).kind) {
			case SHADER -> 0;
			case SKY -> 1;
			case IMAGE -> 2;
		};
	}

	private int firstOf(int category) {
		for (int i = 0; i < list.size(); i++) if (category(i) == category) return i;
		return list.size();
	}

	/** The focused card's name under it; while scrolling the names slide and cross-fade with their cards. */
	private void renderName(DrawContext ctx, float below) {
		SdfFont font = Fonts.semibold();
		float size = Math.max(7f, Math.min(10f, height * 0.026f));
		float y = below + size + 2f;
		int lo = (int) Math.floor(p);
		for (int i = lo; i <= lo + 1; i++) {
			if (i < 0 || i >= count()) continue;
			float a = 1f - Math.min(Math.abs(i - p), 1f);
			if (a <= 0.02f) continue;
			String name = i < list.size() ? list.get(i).name(polish) : (polish ? "Dodaj obraz" : "Add image");
			float x = width / 2f + (i - p) * spacing;
			float saved = Render2D.alpha();
			Render2D.setAlpha(saved * a);
			Render2D.textCentered(ctx, font, name, x, Render2D.middle(font, y, size), size, 0xF2FFFFFF);
			Render2D.setAlpha(saved);
		}
	}

	/** Dots, the current one stretched into a pill; with very many wallpapers "n / total" instead. Returns its bottom. */
	private float renderPages(DrawContext ctx, float below) {
		float size = Math.max(7f, Math.min(10f, height * 0.026f));
		float y = below + size * 2f + 9f;
		int n = count();
		if (n > 28) {
			if (pageFor != focus) {
				page = (focus + 1) + " / " + n;
				pageFor = focus;
			}
			SdfFont font = Fonts.medium();
			Render2D.textCentered(ctx, font, page, width / 2f, Render2D.middle(font, y, 6.5f), 6.5f, 0x99FFFFFF);
			return y + 4f;
		}
		float dot = 4f;
		float gap = 3f;
		float total = n * dot + (n - 1) * gap + 8f;
		float x = width / 2f - total / 2f;
		for (int i = 0; i < n; i++) {
			float c = Math.max(0f, 1f - Math.abs(i - p));
			float w = dot + 8f * c;
			Render2D.round(ctx, x, y - dot / 2f, w, dot, dot / 2f, ColorUtil.lerp(0x4DFFFFFF, 0xF2FFFFFF, c));
			x += w + gap;
		}
		return y + dot / 2f;
	}

	private void hideAdjustments() {
		blur.alpha = 0f;
		dim.alpha = 0f;
		fill.alpha = 0f;
		fit.alpha = 0f;
		motion.alpha = 0f;
	}

	/** Blur and dim sliders; for pictures also Fill / Fit and the motion switch, which slide in beside them. */
	private void renderAdjustments(DrawContext ctx, float y, int mx, int my, float imageShown) {
		WallpaperStyle style = style(focus);
		SdfFont font = Fonts.medium();
		float size = 6.5f;
		String blurText = polish ? "Rozmycie" : "Blur";
		String dimText = polish ? "Przyciemnienie" : "Dim";
		String fillText = polish ? "Wypełnij" : "Fill";
		String fitText = polish ? "Dopasuj" : "Fit";
		String motionText = polish ? "Ruch" : "Motion";
		float track = Math.max(56f, Math.min(90f, width * 0.11f));
		float blurW = blurLabel.width(font, blurText, size) + 6f + track;
		float dimW = dimLabel.width(font, dimText, size) + 6f + track;
		float half = Math.max(fill.label.width(font, fillText, size), fit.label.width(font, fitText, size)) + 12f;
		float motionW = motionLabel.width(font, motionText, size) + 6f + 16f;
		float imageW = (16f + half * 2f + 14f + motionW) * imageShown;
		float x = width / 2f - (blurW + 16f + dimW + imageW) / 2f;
		float cy = y + CONTROL_H / 2f;

		x = sliderRow(ctx, blur, blurLabel, blurText, x, y, track, style.blur, mx, my) + 16f;
		x = sliderRow(ctx, dim, dimLabel, dimText, x, y, track, style.dim, mx, my) + 16f * imageShown;
		if (imageShown <= 0.01f) {
			fill.alpha = 0f;
			fit.alpha = 0f;
			motion.alpha = 0f;
			return;
		}
		float saved = Render2D.alpha();
		Render2D.setAlpha(saved * imageShown);
		// Fill | Fit: a segmented switch whose light plate slides to the chosen half.
		float segH = CONTROL_H - 2f;
		float sy = cy - segH / 2f;
		fill.set(x, sy, half, segH, imageShown);
		fit.set(x + half, sy, half, segH, imageShown);
		Render2D.round(ctx, x, sy, half * 2f, segH, segH / 2f, 0x1AFFFFFF);
		float plate = x + half * fitT.target(style.fit ? 1f : 0f).get();
		Render2D.round(ctx, plate + 1f, sy + 1f, half - 2f, segH - 2f, (segH - 2f) / 2f, 0x33FFFFFF);
		int fillColor = ColorUtil.lerp(0xF2FFFFFF, ColorUtil.lerp(0x8CFFFFFF, 0xCCFFFFFF, fill.hover(mx, my)), fitT.get());
		int fitColor = ColorUtil.lerp(ColorUtil.lerp(0x8CFFFFFF, 0xCCFFFFFF, fit.hover(mx, my)), 0xF2FFFFFF, fitT.get());
		Render2D.textCentered(ctx, font, fillText, x + half / 2f, Render2D.middle(font, cy, size), size, fillColor);
		Render2D.textCentered(ctx, font, fitText, x + half * 1.5f, Render2D.middle(font, cy, size), size, fitColor);
		x += half * 2f + 14f;

		motion.set(x, y, motionW, CONTROL_H, imageShown);
		float hover = motion.hover(mx, my);
		Render2D.textMiddle(ctx, font, motionText, x, cy, size, ColorUtil.lerp(0xB3FFFFFF, 0xF2FFFFFF, hover));
		toggle(ctx, x + motionW - 16f, cy - 4f, motionT.target(style.motion ? 1f : 0f).get(), hover);
		Render2D.setAlpha(saved);
	}

	private float sliderRow(DrawContext ctx, Control control, Label label, String text, float x, float y, float w, float value, int mx, int my) {
		SdfFont font = Fonts.medium();
		float size = 6.5f;
		float lw = label.width(font, text, size);
		float cy = y + CONTROL_H / 2f;
		control.set(x + lw + 6f - 4f, y, w + 8f, CONTROL_H, 1f);
		float hover = Math.max(control.hover(mx, my), slider >= 0 && control == (slider == 0 ? blur : dim) ? 1f : 0f);
		Render2D.textMiddle(ctx, font, text, x, cy, size, ColorUtil.lerp(0xB3FFFFFF, 0xF2FFFFFF, hover));
		float tx = x + lw + 6f;
		Render2D.round(ctx, tx, cy - 1.5f, w, 3f, 1.5f, 0x2EFFFFFF);
		if (value > 0f) Render2D.round(ctx, tx, cy - 1.5f, w * value, 3f, 1.5f, Theme.accent());
		float kx = tx + w * value;
		float kr = 3.6f + 0.9f * hover;
		Render2D.shadow(ctx, kx - kr, cy - kr + 0.5f, kr * 2f, kr * 2f, kr, 3f, 0x66000000);
		Render2D.circle(ctx, kx, cy, kr, 0xFFFFFFFF);
		return tx + w;
	}

	/** A switch like the click GUI's: the track fills with the accent and the knob slides. */
	private static void toggle(DrawContext ctx, float x, float y, float t, float hover) {
		Render2D.round(ctx, x, y, 16f, 8f, 4f, ColorUtil.withAlpha(0xFFFFFF, Math.round(30 + 16 * hover)));
		if (t > 0.01f) Render2D.round(ctx, x, y, 16f, 8f, 4f, ColorUtil.fade(Theme.accent(), t));
		Render2D.circle(ctx, x + 4f + t * 8f, y + 4f, 3f, ColorUtil.lerp(0xFFC9CACF, 0xFFFFFFFF, Math.max(t, hover)));
	}

	/** Cancel on the left, Done on the right; between them "use for both" and, for pictures, the folder tools. */
	private void renderActions(DrawContext ctx, int mx, int my, float imageShown) {
		float h = 18f;
		float y = height - h - Math.max(8f, height * 0.03f);
		float edge = Math.max(10f, width * 0.03f);
		String cancelText = polish ? "Anuluj" : "Cancel";
		String doneText = polish ? "Gotowe" : "Done";
		pill(ctx, cancel, cancelText, 0, edge, y, h, mx, my, false, 1f);
		SdfFont font = Fonts.semibold();
		float doneW = done.label.width(font, doneText, 7f) + 31f;
		pill(ctx, done, doneText, Icons.CHECK, width - edge - doneW, y, h, mx, my, true, focus < list.size() ? 1f : 0.4f);

		String bothText = polish ? "Na obu ekranach" : "Use for both screens";
		SdfFont medium = Fonts.medium();
		float bothW = bothLabel.width(medium, bothText, 7f) + 8f + 16f;
		String addText = polish ? "Dodaj obraz…" : "Add image…";
		String folderText = polish ? "Otwórz folder" : "Open folder";
		String refreshText = polish ? "Odśwież" : "Refresh";
		float addW = add.label.width(font, addText, 7f) + 32f;
		float folderW = folder.label.width(font, folderText, 7f) + 32f;
		float refreshW = refresh.label.width(font, refreshText, 7f) + 32f;
		float tools = (addW + folderW + refreshW + 3f * 6f) * imageShown;
		float x = width / 2f - (bothW + tools) / 2f;

		both.set(x, y, bothW, h, 1f);
		float hover = both.hover(mx, my);
		Render2D.textMiddle(ctx, medium, bothText, x, y + h / 2f, 7f, ColorUtil.lerp(0xB3FFFFFF, 0xF2FFFFFF, hover));
		toggle(ctx, x + bothW - 16f, y + h / 2f - 4f, bothT.target(useForBoth ? 1f : 0f).get(), hover);
		x += bothW + 12f * imageShown;
		if (imageShown <= 0.01f) {
			add.alpha = 0f;
			folder.alpha = 0f;
			refresh.alpha = 0f;
			return;
		}
		float saved = Render2D.alpha();
		Render2D.setAlpha(saved * imageShown);
		x = pill(ctx, add, addText, Icons.PLUS, x, y, h, mx, my, false, imageShown) + 6f;
		x = pill(ctx, folder, folderText, Icons.FOLDER, x, y, h, mx, my, false, imageShown) + 6f;
		pill(ctx, refresh, refreshText, Icons.REFRESH_CW, x, y, h, mx, my, false, imageShown);
		Render2D.setAlpha(saved);
	}

	/** A rounded button with an optional icon; primary ones are filled with the accent. Returns its right edge. */
	private float pill(DrawContext ctx, Control control, String text, int icon, float x, float y, float h, int mx, int my, boolean primary, float enabled) {
		SdfFont font = Fonts.semibold();
		float size = 7f;
		float iconW = icon != 0 ? 11f : 0f;
		float w = control.label.width(font, text, size) + 20f + iconW;
		control.set(x, y, w, h, enabled >= 0.99f ? 1f : 0f);
		float hover = control.hover(mx, my);
		int fill = primary
			? ColorUtil.withAlpha(ColorUtil.lerp(Theme.accent(), 0xFFFFFF, 0.12f * hover), Math.round(255 * enabled))
			: ColorUtil.withAlpha(0xFFFFFF, Math.round(24 + 18 * hover));
		if (primary) Render2D.shadow(ctx, x, y + 2f, w, h, h / 2f, 10f, ColorUtil.withAlpha(Theme.accent(), Math.round(70 * hover * enabled)));
		Render2D.round(ctx, x, y, w, h, h / 2f, fill);
		if (!primary) Render2D.outline(ctx, x, y, w, h, h / 2f, 1f, ColorUtil.withAlpha(0xFFFFFF, Math.round(22 + 16 * hover)));
		int textColor = ColorUtil.withAlpha(0xFFFFFF, Math.round((primary ? 255 : 210 + 45 * hover) * (0.5f + 0.5f * enabled)));
		float tx = x + 10f;
		if (icon != 0) {
			Render2D.icon(ctx, icon, tx + 4f, y + h / 2f, 8f, textColor);
			tx += iconW;
		}
		Render2D.textMiddle(ctx, font, text, tx, y + h / 2f, size, textColor);
		return x + w;
	}

	// ------------------------------------------------------------------ input

	boolean mouseClicked(double mx, double my) {
		if (done.contains(mx, my)) {
			close(true);
		} else if (cancel.contains(mx, my)) {
			close(false);
		} else if (both.contains(mx, my)) {
			useForBoth = !useForBoth;
		} else if (add.contains(mx, my)) {
			pick();
		} else if (folder.contains(mx, my)) {
			Wallpapers.openFolder();
		} else if (refresh.contains(mx, my)) {
			reload(focus < list.size() ? list.get(focus).id : null);
		} else if (delete.contains(mx, my)) {
			deleteFocused();
		} else if (blur.contains(mx, my)) {
			slider = 0;
			slide(mx);
		} else if (dim.contains(mx, my)) {
			slider = 1;
			slide(mx);
		} else if (fill.contains(mx, my)) {
			draft().fit = false;
		} else if (fit.contains(mx, my)) {
			draft().fit = true;
		} else if (motion.contains(mx, my)) {
			WallpaperStyle draft = draft();
			draft.motion = !draft.motion;
		} else if (!clickTab(mx, my)) {
			pressed = true;
			dragging = false;
			pressX = mx;
			pressPos = p;
			dragPos = p;
			samplePos = p;
			sampleTime = System.nanoTime();
			velocity = 0f;
		}
		return true;
	}

	private boolean clickTab(double mx, double my) {
		for (int i = 0; i < 3; i++) {
			if (!tabs[i].contains(mx, my)) continue;
			go(firstOf(i));
			return true;
		}
		return false;
	}

	void mouseDragged(double mx) {
		if (slider >= 0) {
			slide(mx);
			return;
		}
		if (!pressed) return;
		double dx = mx - pressX;
		if (!dragging && Math.abs(dx) < 4.0) return;
		dragging = true;
		float target = pressPos - (float) (dx / spacing);
		// Past the first and last card it gives way only a little.
		float max = count() - 1;
		if (target < 0f) target = -0.3f * (1f - (float) Math.exp(target / 0.3f * 1f));
		else if (target > max) target = max + 0.3f * (1f - (float) Math.exp(-(target - max) / 0.3f));
		long now = System.nanoTime();
		float dt = (now - sampleTime) / 1.0e9f;
		if (dt >= 0.008f) {
			velocity = velocity * 0.3f + (target - samplePos) / dt * 0.7f;
			samplePos = target;
			sampleTime = now;
		}
		dragPos = target;
		pos.snap(target);
	}

	void mouseReleased(double mx, double my) {
		if (slider >= 0) {
			slider = -1;
			return;
		}
		if (!pressed) return;
		pressed = false;
		if (dragging) {
			dragging = false;
			if (System.nanoTime() - sampleTime > 90_000_000L) velocity = 0f;
			focus = Math.max(0, Math.min(count() - 1, Math.round(dragPos + velocity * 0.12f)));
			pos.target(focus).fling(velocity);
			armed = -1;
			return;
		}
		// A click: the focused card applies (or adds a picture), a side card comes to the middle.
		int f = Math.round(p);
		for (int i = Math.max(0, f - 2); i <= Math.min(count() - 1, f + 2); i++) {
			cardRect(i);
			if (mx < rect[0] || mx > rect[0] + rect[2] || my < rect[1] || my > rect[1] + rect[3]) continue;
			if (i != focus) go(i);
			else if (i == list.size()) pick();
			else close(true);
			return;
		}
	}

	void mouseScrolled(double amount) {
		scroll += (float) amount;
		while (Math.abs(scroll) >= 1f) {
			float step = Math.signum(scroll);
			scroll -= step;
			go(focus - (int) step);
		}
	}

	void keyPressed(int key) {
		switch (key) {
			case GLFW.GLFW_KEY_ESCAPE -> close(false);
			case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> {
				if (focus == list.size()) pick();
				else close(true);
			}
			case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_A -> go(focus - 1);
			case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_D -> go(focus + 1);
			case GLFW.GLFW_KEY_HOME -> go(0);
			case GLFW.GLFW_KEY_END -> go(count() - 1);
			case GLFW.GLFW_KEY_DELETE -> deleteFocused();
			default -> {
			}
		}
	}

	private void slide(double mx) {
		Control control = slider == 0 ? blur : dim;
		float value = Anim.clamp01((float) (mx - control.x - 4f) / (control.w - 8f));
		WallpaperStyle draft = draft();
		if (slider == 0) draft.blur = value;
		else draft.dim = value;
	}

	private void pick() {
		Wallpapers.pick(polish ? "Wybierz tapetę" : "Choose a wallpaper");
	}

	/** A picture the file dialog copied into the folder: list it and scroll to it. */
	private void takePicked() {
		String id = Wallpapers.takePicked();
		if (id != null && open) reload(id);
	}

	/** Lists the folder again and focuses the wallpaper with this id (or the add card). */
	private void reload(String id) {
		Wallpapers.refresh();
		list = Wallpapers.all();
		int index = list.size();
		for (int i = 0; i < list.size(); i++) if (list.get(i).id.equals(id)) index = i;
		zoomIndex = Math.min(zoomIndex, list.size() - 1);
		go(index);
	}

	private void deleteFocused() {
		if (focus >= list.size() || list.get(focus).kind != Wallpaper.Kind.IMAGE) return;
		if (armed != focus) {
			armed = focus;
			armedAt = System.nanoTime();
			return;
		}
		Wallpaper wallpaper = list.get(focus);
		armed = -1;
		if (!Wallpapers.delete(wallpaper)) return;
		drafts.remove(wallpaper.id);
		drawn.remove(wallpaper.id);
		WallpaperTarget target = cards.remove(wallpaper.id);
		if (target != null) WallpaperRenderer.retire(target::close);
		list = Wallpapers.all();
		zoomIndex = Math.min(zoomIndex, list.size() - 1);
		go(focus);
	}

	/** The title screen went away: the picker closes without saving and frees its cards. */
	void dispose() {
		open = false;
		zoom.snap(0f);
		e = 0f;
		drafts.clear();
		release();
	}

	/** Frees every card once the picker has zoomed back in. */
	private void release() {
		if (cards.isEmpty()) return;
		for (WallpaperTarget target : cards.values()) WallpaperRenderer.retire(target::close);
		cards.clear();
		drawn.clear();
	}

	/** A clickable area laid out while drawing; hidden ones (alpha 0) take no clicks. */
	private static final class Control {
		final Anim hoverT = new Anim(0f, 18f);
		final Label label = new Label();
		float x;
		float y;
		float w;
		float h;
		float alpha;

		void set(float x, float y, float w, float h, float alpha) {
			this.x = x;
			this.y = y;
			this.w = w;
			this.h = h;
			this.alpha = alpha;
		}

		boolean contains(double mx, double my) {
			return alpha > 0.5f && mx >= x && mx <= x + w && my >= y && my <= y + h;
		}

		float hover(int mx, int my) {
			return hoverT.target(contains(mx, my) ? 1f : 0f).get();
		}
	}
}
