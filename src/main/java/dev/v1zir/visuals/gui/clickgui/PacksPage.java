package dev.v1zir.visuals.gui.clickgui;

import dev.v1zir.visuals.pack.PackMixer;
import dev.v1zir.visuals.pack.PackPreviews;
import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.font.Icons;
import dev.v1zir.visuals.render.font.SdfFont;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Packs tab (Pack Mixer): every texture of the loaded resource packs, vanilla's included, as a grid of tiles
 * showing the version in use; a tile with a number is provided by that many packs. Selecting one lists its versions on
 * the right with a preview each, and clicking a version makes the game use it. Apply reloads the resources so the
 * choices show; Reset goes back to the top pack everywhere. The search field filters by name.
 */
final class PacksPage {
	private static final String[] FILTERS = {"All", "Changed", "Conflicts", "Blocks", "Items", "Entities", "GUI", "Particles", "Other"};
	private static final float GAP = 10f;
	private static final float CHIP_H = 18f;
	private static final float TILE = 34f;
	private static final float TILE_GAP = 5f;
	private static final float ROW = 28f;
	private static final float BUTTON_H = 20f;

	private final Scroller scroller = new Scroller();
	private final Scroller versionScroller = new Scroller();
	private final Anim[] chipHover = new Anim[FILTERS.length];
	private final Anim[] chipActive = new Anim[FILTERS.length];
	private final Label[] chipWidth = new Label[FILTERS.length];
	private final Anim applyHover = new Anim(0f, 22f);
	private final Anim resetHover = new Anim(0f, 22f);
	private final Label subtitleWidth = new Label();
	private int filter;
	private String query = "";
	private List<PackMixer.Entry> shown = List.of();
	private List<PackMixer.Entry> source;
	private int shownFilter = -1;
	private String shownQuery;
	private int shownChoices = -1;
	private PackMixer.Entry selected;
	// Layout of the last frame.
	private float viewX;
	private float viewY;
	private float viewW;
	private float viewH;
	private final float[] chipX = new float[FILTERS.length];
	private final float[] chipW = new float[FILTERS.length];
	private float chipY;
	private float gridX;
	private float gridY;
	private float gridW;
	private float gridH;
	private int cols = 1;
	private float scroll;
	private float listX;
	private float listY;
	private float listW;
	private float listH;
	private float versionScroll;
	private float applyX;
	private float applyW;
	private float resetX;
	private float resetW;
	private float buttonsY;

	PacksPage() {
		for (int i = 0; i < FILTERS.length; i++) {
			chipHover[i] = new Anim(0f, 22f);
			chipActive[i] = new Anim(i == 0 ? 1f : 0f, 16f);
			chipWidth[i] = new Label();
		}
	}

	void reset() {
		scroller.reset();
	}

	void search(String text) {
		query = text.toLowerCase(Locale.ROOT).strip();
		scroller.reset();
	}

	// ------------------------------------------------------------------ header

	void renderHeader(DrawContext ctx, float subtitleX, float right, float mid, double mx, double my) {
		List<PackMixer.Entry> all = PackMixer.entries();
		String subtitle = PackMixer.reloading() ? "Reloading resources…" : all.size() + " textures · " + PackMixer.conflicts() + " in several packs";
		Render2D.textMiddle(ctx, Fonts.medium(), subtitle, subtitleX, mid + 1f, Ui.TINY, Theme.textFaint());
		buttonsY = mid - BUTTON_H / 2f;
		float x = right;
		boolean dirty = PackMixer.dirty();
		applyW = 0f;
		if (dirty) {
			applyW = Fonts.semibold().width("Apply", Ui.SMALL) + 26f;
			applyX = x - applyW;
			x = applyX - 6f;
			float h = applyHover.target(Ui.inside(mx, my, applyX, buttonsY, applyW, BUTTON_H) ? 1f : 0f).get();
			Render2D.matte(ctx, applyX, buttonsY, applyW, BUTTON_H, BUTTON_H / 2f, ColorUtil.withAlpha(Theme.accent(), Math.round(150 + 60 * h)), Ui.LIT);
			Render2D.icon(ctx, Icons.CHECK, applyX + 10f, buttonsY + BUTTON_H / 2f, 6.5f, 0xFFFFFFFF);
			Render2D.textMiddle(ctx, Fonts.semibold(), "Apply", applyX + 17f, buttonsY + BUTTON_H / 2f, Ui.SMALL, 0xFFFFFFFF);
		}
		resetW = 0f;
		if (!PackMixer.choices().isEmpty()) {
			resetW = Fonts.semibold().width("Reset all", Ui.SMALL) + 18f;
			resetX = x - resetW;
			float h = resetHover.target(Ui.inside(mx, my, resetX, buttonsY, resetW, BUTTON_H) ? 1f : 0f).get();
			Render2D.matte(ctx, resetX, buttonsY, resetW, BUTTON_H, BUTTON_H / 2f, ColorUtil.withAlpha(0xFFFFFF, Math.round(10 + 12 * h)), Ui.LIT);
			Render2D.rim(ctx, resetX, buttonsY, resetW, BUTTON_H, BUTTON_H / 2f, ColorUtil.withAlpha(0xFFFFFF, Math.round(22 + 18 * h)), Ui.LIT);
			Render2D.textMiddle(ctx, Fonts.semibold(), "Reset all", resetX + 9f, buttonsY + BUTTON_H / 2f, Ui.SMALL,
				ColorUtil.lerp(Theme.textDim(), Theme.text(), h));
		}
	}

	// ------------------------------------------------------------------ page

	void render(DrawContext ctx, float x, float y, float w, float h, double mx, double my, boolean mouseFree) {
		viewX = x;
		viewY = y;
		viewW = w;
		viewH = h;
		double hx = mouseFree ? mx : -1000;
		double hy = mouseFree ? my : -1000;
		refilter();
		renderChips(ctx, x, y, hx, hy);
		float panelW = Math.max(150f, Math.min(220f, w * 0.34f));
		gridX = x;
		gridY = y + CHIP_H + 10f;
		gridW = w - panelW - GAP;
		gridH = h - CHIP_H - 10f;
		renderGrid(ctx, hx, hy);
		renderDetail(ctx, x + w - panelW, gridY, panelW, gridH, hx, hy);
	}

	/** The entries for the filter and search, worked out again only when one of them (or the choices) changed. */
	private void refilter() {
		List<PackMixer.Entry> all = PackMixer.entries();
		int choices = PackMixer.choices().size();
		if (all == source && filter == shownFilter && query.equals(shownQuery) && (filter != 1 || choices == shownChoices)) return;
		source = all;
		shownFilter = filter;
		shownQuery = query;
		shownChoices = choices;
		List<PackMixer.Entry> list = new ArrayList<>();
		for (PackMixer.Entry e : all) {
			boolean pass = switch (filter) {
				case 0 -> true;
				case 1 -> PackMixer.changed(e);
				case 2 -> e.conflict();
				default -> e.group().equals(FILTERS[filter]);
			};
			if (!pass) continue;
			if (!query.isEmpty() && !e.name().contains(query) && !e.id().getPath().contains(query)) continue;
			list.add(e);
		}
		shown = list;
		if (selected != null && !list.contains(selected)) {
			// Keep the selection across a rescan when the same texture is still there.
			PackMixer.Entry same = null;
			for (PackMixer.Entry e : list) {
				if (e.id().equals(selected.id())) {
					same = e;
					break;
				}
			}
			selected = same;
		}
	}

	private void renderChips(DrawContext ctx, float x, float y, double mx, double my) {
		chipY = y;
		float cx = x;
		for (int i = 0; i < FILTERS.length; i++) {
			String label = FILTERS[i];
			float tw = chipWidth[i].width(Fonts.semibold(), label, Ui.SMALL);
			chipW[i] = tw + 20f;
			chipX[i] = cx;
			if (cx + chipW[i] > viewX + viewW) {
				chipW[i] = 0f;
				continue;
			}
			float hv = chipHover[i].target(Ui.inside(mx, my, cx, y, chipW[i], CHIP_H) ? 1f : 0f).get();
			float on = chipActive[i].target(i == filter ? 1f : 0f).get();
			Render2D.matte(ctx, cx, y, chipW[i], CHIP_H, CHIP_H / 2f, ColorUtil.lerp(ColorUtil.withAlpha(0xFFFFFF, Math.round(10 + 10 * hv)),
				ColorUtil.withAlpha(Theme.accent(), 60), on), Ui.LIT);
			Render2D.rim(ctx, cx, y, chipW[i], CHIP_H, CHIP_H / 2f, ColorUtil.lerp(ColorUtil.withAlpha(0xFFFFFF, Math.round(20 + 16 * hv)),
				ColorUtil.withAlpha(Theme.accent(), 180), on), Ui.LIT);
			Render2D.textMiddle(ctx, Fonts.semibold(), label, cx + 10f, y + CHIP_H / 2f, Ui.SMALL, ColorUtil.lerp(Theme.textDim(), Theme.text(), Math.max(on, hv)));
			cx += chipW[i] + 5f;
		}
	}

	/** The tiles: the version in use, a count when several packs have the texture, the accent when it was chosen here. */
	private void renderGrid(DrawContext ctx, double mx, double my) {
		cols = Math.max(1, (int) ((gridW + TILE_GAP) / (TILE + TILE_GAP)));
		float step = TILE + TILE_GAP;
		int rows = (shown.size() + cols - 1) / cols;
		float content = Math.max(0f, rows * step - TILE_GAP);
		scroll = scroller.update(content, gridH);
		boolean inView = Ui.inside(mx, my, gridX, gridY, gridW, gridH);
		if (shown.isEmpty()) {
			Render2D.textMiddle(ctx, Fonts.medium(), PackMixer.reloading() ? "Reloading…" : "No textures here", gridX + 4f, gridY + 10f, Ui.SMALL,
				Theme.textFaint());
			return;
		}
		ctx.enableScissor((int) Math.floor(gridX - 2f), (int) Math.floor(gridY), (int) Math.ceil(gridX + gridW + 2f), (int) Math.ceil(gridY + gridH));
		int first = Math.max(0, (int) (scroll / step) * cols);
		int last = Math.min(shown.size(), first + ((int) (gridH / step) + 2) * cols);
		for (int i = first; i < last; i++) {
			PackMixer.Entry e = shown.get(i);
			float tx = gridX + (i % cols) * step;
			float ty = gridY + (i / cols) * step - scroll;
			boolean hovered = inView && Ui.inside(mx, my, tx, ty, TILE, TILE);
			boolean sel = e == selected;
			boolean changed = PackMixer.changed(e);
			int fill = sel ? ColorUtil.withAlpha(Theme.accent(), 70) : hovered ? Theme.raisedLit() : Theme.raised();
			Render2D.matte(ctx, tx, ty, TILE, TILE, Ui.CELL_RADIUS + 1f, fill, Ui.LIT);
			int rim = sel || changed ? ColorUtil.withAlpha(Theme.accent(), sel ? 220 : 150) : ColorUtil.withAlpha(0xFFFFFF, hovered ? 40 : 18);
			Render2D.rim(ctx, tx, ty, TILE, TILE, Ui.CELL_RADIUS + 1f, rim, Ui.LIT);
			PackMixer.Version active = version(e, PackMixer.active(e));
			if (active != null) drawPreview(ctx, e, active, tx + 4f, ty + 4f, TILE - 8f);
			if (e.conflict()) {
				String count = String.valueOf(e.versions().size());
				Render2D.circle(ctx, tx + TILE - 5f, ty + 5f, 4.2f, changed ? Theme.accent() : 0xE0303036);
				Render2D.textMiddle(ctx, Fonts.semibold(), count, tx + TILE - 5f - Fonts.semibold().width(count, 5f) / 2f, ty + 5.2f, 5f, 0xFFFFFFFF);
			}
		}
		ctx.disableScissor();
		scroller.drawBar(ctx, gridX + gridW + 3f, gridY, gridH, content, inView);
	}

	/** The selected texture large, its id, and its versions, the one in use checked; a click on one uses it. */
	private void renderDetail(DrawContext ctx, float x, float y, float w, float h, double mx, double my) {
		Render2D.matte(ctx, x, y, w, h, Ui.PANEL_RADIUS, Theme.raised(), Ui.LIT);
		Render2D.rim(ctx, x, y, w, h, Ui.PANEL_RADIUS, Ui.HAIRLINE, Ui.LIT);
		if (selected == null) {
			Render2D.icon(ctx, Icons.LAYERS, x + w / 2f, y + h / 2f - 10f, 16f, Theme.textFaint());
			String hint = "Pick a texture";
			Render2D.textMiddle(ctx, Fonts.medium(), hint, x + w / 2f - Fonts.medium().width(hint, Ui.SMALL) / 2f, y + h / 2f + 10f, Ui.SMALL, Theme.textFaint());
			return;
		}
		PackMixer.Entry e = selected;
		String activeId = PackMixer.active(e);
		PackMixer.Version active = version(e, activeId);
		float big = Math.min(w - 24f, 84f);
		float bx = x + (w - big) / 2f;
		float by = y + 10f;
		Render2D.round(ctx, bx - 4f, by - 4f, big + 8f, big + 8f, Ui.CELL_RADIUS + 2f, 0x30000000);
		if (active != null) drawPreview(ctx, e, active, bx, by, big);
		float ty = by + big + 12f;
		SdfFont bold = Fonts.semibold();
		Render2D.textMiddle(ctx, bold, fit(bold, e.name(), Ui.TEXT, w - 16f), x + 8f, ty, Ui.TEXT, Theme.text());
		Render2D.textMiddle(ctx, Fonts.medium(), fit(Fonts.medium(), e.id().toString(), Ui.TINY, w - 16f), x + 8f, ty + 10f, Ui.TINY, Theme.textFaint());
		listX = x + 6f;
		listY = ty + 20f;
		listW = w - 12f;
		listH = y + h - 6f - listY;
		List<PackMixer.Version> versions = e.versions();
		float content = versions.size() * ROW;
		versionScroll = versionScroller.update(content, listH);
		boolean inList = Ui.inside(mx, my, listX, listY, listW, listH);
		ctx.enableScissor((int) Math.floor(listX), (int) Math.floor(listY), (int) Math.ceil(listX + listW), (int) Math.ceil(listY + listH));
		for (int i = 0; i < versions.size(); i++) {
			PackMixer.Version v = versions.get(i);
			float ry = listY + i * ROW - versionScroll;
			if (ry + ROW < listY || ry > listY + listH) continue;
			boolean hovered = inList && Ui.inside(mx, my, listX, ry, listW, ROW - 3f);
			boolean using = v.packId().equals(activeId);
			int fill = using ? ColorUtil.withAlpha(Theme.accent(), 55) : ColorUtil.withAlpha(0xFFFFFF, hovered ? 16 : 6);
			Render2D.round(ctx, listX, ry, listW, ROW - 3f, Ui.CELL_RADIUS, fill);
			drawPreview(ctx, e, v, listX + 4f, ry + 3f, ROW - 9f);
			float nx = listX + ROW + 2f;
			float nw = listW - ROW - 18f;
			Render2D.textMiddle(ctx, Fonts.semibold(), fit(Fonts.semibold(), v.packName(), Ui.SMALL, nw), nx, ry + (ROW - 3f) / 2f - 3.5f, Ui.SMALL,
				using ? Theme.text() : Theme.textDim());
			String note = i == 0 ? "top pack" : v.packId().equals("vanilla") ? "Minecraft" : "pack " + (versions.size() - i);
			Render2D.textMiddle(ctx, Fonts.medium(), note, nx, ry + (ROW - 3f) / 2f + 4.5f, Ui.TINY, Theme.textFaint());
			if (using) Render2D.icon(ctx, Icons.CHECK, listX + listW - 9f, ry + (ROW - 3f) / 2f, 6.5f, Theme.accent());
		}
		ctx.disableScissor();
		if (versions.size() == 1) {
			String only = "Only one pack has this texture";
			Render2D.textMiddle(ctx, Fonts.medium(), fit(Fonts.medium(), only, Ui.TINY, listW), listX + 2f, listY + ROW + 6f, Ui.TINY, Theme.textFaint());
		}
	}

	private static void drawPreview(DrawContext ctx, PackMixer.Entry e, PackMixer.Version v, float x, float y, float size) {
		PackPreviews.Preview preview = PackPreviews.get(e, v);
		if (preview == null) {
			Render2D.round(ctx, x, y, size, size, 2f, 0x18FFFFFF);
			return;
		}
		// Keep the picture's shape, centred.
		float aspect = preview.width() / (float) Math.max(1, preview.height());
		float w = aspect >= 1f ? size : size * aspect;
		float h = aspect >= 1f ? size / aspect : size;
		Render2D.image(ctx, preview.texture(), x + (size - w) / 2f, y + (size - h) / 2f, w, h, 0f, 0xFFFFFFFF);
	}

	private static PackMixer.Version version(PackMixer.Entry e, String packId) {
		if (packId == null) return null;
		for (PackMixer.Version v : e.versions()) if (v.packId().equals(packId)) return v;
		return null;
	}

	private static String fit(SdfFont font, String text, float size, float max) {
		if (font.width(text, size) <= max) return text;
		String dots = "…";
		int end = text.length();
		while (end > 0 && font.width(text.substring(0, end) + dots, size) > max) end--;
		return text.substring(0, end) + dots;
	}

	// ------------------------------------------------------------------ input

	boolean mouseClicked(double mx, double my, int button) {
		if (applyW > 0f && Ui.inside(mx, my, applyX, buttonsY, applyW, BUTTON_H)) {
			PackMixer.apply();
			return true;
		}
		if (resetW > 0f && Ui.inside(mx, my, resetX, buttonsY, resetW, BUTTON_H)) {
			PackMixer.resetAll();
			return true;
		}
		if (!Ui.inside(mx, my, viewX, viewY, viewW, viewH)) return false;
		for (int i = 0; i < FILTERS.length; i++) {
			if (chipW[i] > 0f && Ui.inside(mx, my, chipX[i], chipY, chipW[i], CHIP_H)) {
				if (filter != i) {
					filter = i;
					scroller.reset();
				}
				return true;
			}
		}
		if (Ui.inside(mx, my, gridX, gridY, gridW, gridH)) {
			float step = TILE + TILE_GAP;
			int col = (int) ((mx - gridX) / step);
			int row = (int) ((my - gridY + scroll) / step);
			if (col < cols && (mx - gridX) - col * step <= TILE && (my - gridY + scroll) - row * step <= TILE) {
				int index = row * cols + col;
				if (index >= 0 && index < shown.size()) {
					selected = shown.get(index);
					versionScroller.reset();
				}
			}
			return true;
		}
		if (selected != null && Ui.inside(mx, my, listX, listY, listW, listH)) {
			int index = (int) ((my - listY + versionScroll) / ROW);
			if (index >= 0 && index < selected.versions().size()) PackMixer.choose(selected, selected.versions().get(index).packId());
			return true;
		}
		return false;
	}

	void scroll(double mx, double my, double amount) {
		if (selected != null && Ui.inside(mx, my, listX, listY, listW, listH)) versionScroller.scroll(amount);
		else scroller.scroll(amount);
	}
}
