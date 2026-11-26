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
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * The Packs tab (Pack Mixer): every texture of the loaded resource packs, vanilla's included, as a grid of tiles
 * showing the version in use; a tile with a number is provided by that many packs. A sidebar sorts them into tabs the
 * way PvP packs are made (swords, axes, pickaxes, tools, bows, armour, food, potions, blocks, ores, wool and glass,
 * GUI...), each with a texture of the pack as its icon, how many textures it holds and a dot when one was changed here;
 * All, Changed and Conflicts sit on top. Hovering a tile names it; selecting one lists its versions on the right with
 * a preview each, and clicking a version makes the game use it. Apply reloads the resources so the choices show;
 * Reset goes back to the top pack everywhere. The search field filters by name.
 */
final class PacksPage {
	private static final String[] FILTERS = filters();
	/** Glyphs of the rows without a texture icon: All, Changed, Conflicts, then the tabs that have none. */
	private static final int[] GLYPHS = glyphs();
	private static final float GAP = 10f;
	private static final float SIDE_W = 112f;
	private static final float SIDE_ROW = 18f;
	/** The three overview rows are set apart from the tabs by this gap. */
	private static final float SIDE_SPLIT = 7f;
	private static final float TILE = 34f;
	private static final float TILE_GAP = 5f;
	private static final float ROW = 28f;
	private static final float BUTTON_H = 20f;

	private final Scroller scroller = new Scroller();
	private final Scroller versionScroller = new Scroller();
	private final Scroller sideScroller = new Scroller();
	private final Anim[] rowHover = new Anim[FILTERS.length];
	private final Anim[] rowActive = new Anim[FILTERS.length];
	/** Per row: textures in it, how many of them were changed here, and the texture its icon shows. */
	private final int[] counts = new int[FILTERS.length];
	private final int[] changedCounts = new int[FILTERS.length];
	private final PackMixer.Entry[] icons = new PackMixer.Entry[FILTERS.length];
	private List<PackMixer.Entry> countedFrom;
	private int countedChoices = -1;
	private final Anim applyHover = new Anim(0f, 22f);
	private final Anim panelOpen = new Anim(0f, 12f);
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
	private float sideX;
	private float sideY;
	private float sideW;
	private float sideH;
	private float sideScroll;
	/** Top of each row of the sidebar in the last frame (NaN when it was not drawn). */
	private final float[] rowY = new float[FILTERS.length];
	private float gridX;
	private float gridY;
	private float gridW;
	private float gridH;
	private int cols = 1;
	private float scroll;
	/** The tile under the cursor in the last frame, or -1. */
	private int hoveredTile = -1;
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
			rowHover[i] = new Anim(0f, 22f);
			rowActive[i] = new Anim(i == 0 ? 1f : 0f, 16f);
		}
	}

	private static String[] filters() {
		String[] filters = new String[3 + PackMixer.CATEGORIES.length];
		filters[0] = "All";
		filters[1] = "Changed";
		filters[2] = "Conflicts";
		System.arraycopy(PackMixer.CATEGORIES, 0, filters, 3, PackMixer.CATEGORIES.length);
		return filters;
	}

	private static int[] glyphs() {
		int[] glyphs = new int[3 + PackMixer.CATEGORIES.length];
		glyphs[0] = Icons.LAYOUT_GRID;
		glyphs[1] = Icons.PENCIL;
		glyphs[2] = Icons.LAYERS;
		for (int i = 0; i < PackMixer.CATEGORIES.length; i++) {
			glyphs[3 + i] = switch (PackMixer.CATEGORIES[i]) {
				case "Mobs" -> Icons.GHOST;
				case "GUI" -> Icons.LAYOUT_DASHBOARD;
				case "Swords" -> Icons.SWORD;
				case "Axes" -> Icons.AXE;
				case "Pickaxes", "Tools" -> Icons.HAMMER;
				case "Armor" -> Icons.SHIELD;
				case "Potions" -> Icons.FLASK_CONICAL;
				case "Sky" -> Icons.SUN;
				case "Particles" -> Icons.SPARKLES;
				default -> Icons.ELLIPSIS;
			};
		}
		return glyphs;
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
		count();
		float side = Math.min(SIDE_W, w * 0.3f);
		renderSidebar(ctx, x, y, side, h, hx, hy);
		float px = x + side + GAP;
		float pw = w - side - GAP;
		// The details slide in once a texture is picked; until then the tiles have the whole width.
		float open = panelOpen.target(selected != null ? 1f : 0f).get();
		float panelW = Math.max(104f, Math.min(200f, pw * 0.42f)) * open;
		gridX = px;
		gridY = y;
		gridW = pw - panelW - GAP * open;
		gridH = h;
		renderGrid(ctx, hx, hy);
		if (open > 0.01f) {
			float alpha = Render2D.alpha();
			Render2D.setAlpha(alpha * open);
			renderDetail(ctx, px + pw - panelW, y, panelW, h, hx, hy);
			Render2D.setAlpha(alpha);
		}
	}

	/** The rows' counts and icons, worked out again only when the textures or the choices changed. */
	private void count() {
		List<PackMixer.Entry> all = PackMixer.entries();
		int choices = PackMixer.choices().size();
		if (all == countedFrom && choices == countedChoices) return;
		boolean rescanned = all != countedFrom;
		countedFrom = all;
		countedChoices = choices;
		Arrays.fill(counts, 0);
		Arrays.fill(changedCounts, 0);
		for (PackMixer.Entry e : all) {
			boolean changed = PackMixer.changed(e);
			int row = 3 + Arrays.asList(PackMixer.CATEGORIES).indexOf(e.group());
			counts[0]++;
			if (changed) {
				counts[1]++;
				changedCounts[0]++;
			}
			if (e.conflict()) counts[2]++;
			if (row >= 3) {
				counts[row]++;
				if (changed) changedCounts[row]++;
			}
		}
		if (rescanned) {
			for (int i = 0; i < PackMixer.CATEGORIES.length; i++) {
				String texture = PackMixer.CATEGORY_TEXTURES[i];
				icons[3 + i] = texture != null ? PackMixer.find(texture) : null;
			}
		}
	}

	/**
	 * The tabs down the left: All, Changed and Conflicts, then every tab that has textures, each with its icon (a texture
	 * of the pack in use, or a glyph), its name, how many textures it holds and a dot when some were changed here.
	 */
	private void renderSidebar(DrawContext ctx, float x, float y, float w, float h, double mx, double my) {
		sideX = x;
		sideY = y;
		sideW = w;
		sideH = h;
		Render2D.matte(ctx, x, y, w, h, Ui.PANEL_RADIUS, Theme.raised(), Ui.LIT);
		Render2D.rim(ctx, x, y, w, h, Ui.PANEL_RADIUS, Ui.HAIRLINE, Ui.LIT);
		int visible = 0;
		for (int i = 0; i < FILTERS.length; i++) if (i < 3 || counts[i] > 0) visible++;
		float content = visible * SIDE_ROW + SIDE_SPLIT + 8f;
		sideScroll = sideScroller.update(content, h);
		boolean inside = Ui.inside(mx, my, x, y, w, h);
		ctx.enableScissor((int) Math.floor(x), (int) Math.floor(y + 2f), (int) Math.ceil(x + w), (int) Math.ceil(y + h - 2f));
		float ry = y + 4f - sideScroll;
		SdfFont bold = Fonts.semibold();
		SdfFont small = Fonts.medium();
		for (int i = 0; i < FILTERS.length; i++) {
			rowY[i] = Float.NaN;
			if (i >= 3 && counts[i] == 0) continue;
			if (i == 3) {
				// A hairline between the overview rows and the tabs.
				Render2D.rect(ctx, x + 8f, ry + SIDE_SPLIT / 2f - 0.25f, w - 16f, 0.5f, ColorUtil.withAlpha(0xFFFFFF, 18));
				ry += SIDE_SPLIT;
			}
			rowY[i] = ry;
			float rx = x + 4f;
			float rw = w - 8f;
			float hv = rowHover[i].target(inside && Ui.inside(mx, my, rx, ry, rw, SIDE_ROW - 2f) ? 1f : 0f).get();
			float on = rowActive[i].target(i == filter ? 1f : 0f).get();
			if (on > 0.01f || hv > 0.01f) {
				Render2D.round(ctx, rx, ry, rw, SIDE_ROW - 2f, Ui.CELL_RADIUS, ColorUtil.lerp(ColorUtil.withAlpha(0xFFFFFF, Math.round(14 * hv)),
					ColorUtil.withAlpha(Theme.accent(), 64), on));
				if (on > 0.01f) Render2D.round(ctx, rx, ry + 3f, 2f, SIDE_ROW - 8f, 1f, ColorUtil.withAlpha(Theme.accent(), Math.round(255 * on)));
			}
			float cy = ry + (SIDE_ROW - 2f) / 2f;
			PackMixer.Entry icon = icons[i];
			PackMixer.Version iconVersion = icon != null ? version(icon, PackMixer.active(icon)) : null;
			if (iconVersion != null) drawPreview(ctx, icon, iconVersion, rx + 5f, cy - 5.5f, 11f);
			else Render2D.icon(ctx, GLYPHS[i], rx + 10.5f, cy, 8f, ColorUtil.lerp(Theme.textDim(), Theme.text(), Math.max(on, hv)));
			String count = String.valueOf(counts[i]);
			float countW = small.width(count, Ui.TINY);
			float nameMax = rw - 22f - countW - 8f;
			Render2D.textMiddle(ctx, bold, fit(bold, FILTERS[i], Ui.SMALL, nameMax), rx + 20f, cy, Ui.SMALL,
				ColorUtil.lerp(Theme.textDim(), Theme.text(), Math.max(on, hv)));
			Render2D.textMiddle(ctx, small, count, rx + rw - 5f - countW, cy, Ui.TINY, Theme.textFaint());
			if (i != 1 && changedCounts[i] > 0) Render2D.circle(ctx, rx + rw - 9f - countW, cy, 1.6f, Theme.accent());
			ry += SIDE_ROW;
		}
		ctx.disableScissor();
		sideScroller.drawBar(ctx, x + w - 4f, y + 2f, h - 4f, content, inside);
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
		hoveredTile = -1;
		for (int i = first; i < last; i++) {
			PackMixer.Entry e = shown.get(i);
			float tx = gridX + (i % cols) * step;
			float ty = gridY + (i / cols) * step - scroll;
			boolean hovered = inView && Ui.inside(mx, my, tx, ty, TILE, TILE);
			if (hovered) hoveredTile = i;
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
		if (hoveredTile >= 0) {
			// The hovered tile's name in a pill above it (below it in the top row), kept inside the grid.
			PackMixer.Entry e = shown.get(hoveredTile);
			SdfFont font = Fonts.semibold();
			float tw = font.width(e.name(), Ui.TINY) + 10f;
			float tx = gridX + (hoveredTile % cols) * step + TILE / 2f - tw / 2f;
			float ty = gridY + (hoveredTile / cols) * step - scroll - 13f;
			if (ty < gridY) ty += TILE + 15f;
			tx = Math.max(gridX, Math.min(gridX + gridW - tw, tx));
			Render2D.round(ctx, tx, ty, tw, 11f, 5.5f, 0xE6141418);
			Render2D.rim(ctx, tx, ty, tw, 11f, 5.5f, ColorUtil.withAlpha(0xFFFFFF, 28), 0);
			Render2D.textMiddle(ctx, font, e.name(), tx + 5f, ty + 5.5f, Ui.TINY, Theme.text());
		}
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
		if (Ui.inside(mx, my, sideX, sideY, sideW, sideH)) {
			for (int i = 0; i < FILTERS.length; i++) {
				if (Float.isNaN(rowY[i]) || !Ui.inside(mx, my, sideX + 4f, rowY[i], sideW - 8f, SIDE_ROW - 2f)) continue;
				if (filter != i) {
					filter = i;
					scroller.reset();
				}
				return true;
			}
			return true;
		}
		if (Ui.inside(mx, my, gridX, gridY, gridW, gridH)) {
			float step = TILE + TILE_GAP;
			int col = (int) ((mx - gridX) / step);
			int row = (int) ((my - gridY + scroll) / step);
			if (col < cols && (mx - gridX) - col * step <= TILE && (my - gridY + scroll) - row * step <= TILE) {
				int index = row * cols + col;
				if (index >= 0 && index < shown.size()) {
					// A second click on the selected texture closes its details.
					PackMixer.Entry e = shown.get(index);
					selected = e == selected ? null : e;
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
		else if (Ui.inside(mx, my, sideX, sideY, sideW, sideH)) sideScroller.scroll(amount);
		else scroller.scroll(amount);
	}
}
