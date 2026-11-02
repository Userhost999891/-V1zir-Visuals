package dev.v1zir.visuals.gui.clickgui;

import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.font.Icons;
import dev.v1zir.visuals.render.font.SdfFont;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.gui.DrawContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Module cards of one tab (or of a search) in a scrolling grid of two or three columns. Above it,
 * a row of chips filters the grid: the starred favourites, all, and the sub-categories; an outlined
 * pill glides to the selected chip. Searches list favourites first. The arrow keys move a focus
 * ring over the cards.
 */
final class ModuleGrid {
	static final String ALL = "All";
	/** The favourites chip, drawn as a star. */
	static final String FAVOURITES = "Favourites";
	/** Delay between two cards starting to appear, in seconds. */
	private static final float STAGGER = 0.018f;
	private static final float CHIP_H = 16f;
	private static final float CHIP_GAP = 0f;

	private final List<ModuleCard> cards = new ArrayList<>();
	private final List<ModuleCard> visible = new ArrayList<>();
	private final List<String> chips = new ArrayList<>();
	private final Label[] chipLabels;
	private final Anim[] chipHover;
	private final float[] chipX;
	private final float[] chipW;
	private final Anim selX = new Anim(0f, 18f);
	private final Anim selW = new Anim(0f, 18f);
	private boolean selPlaced;
	private int chip;
	private String query = "";
	private final Scroller scroller = new Scroller();
	private long shownAt = System.nanoTime();
	private final Label emptyWidth = new Label();
	private final Label hintWidth = new Label();
	/** Module list version the visible cards were filtered for: a star or a toggle filters again. */
	private int filteredFor = -1;
	@Nullable
	private ModuleCard focus;
	private int columns = 1;
	// Viewport of the last frame.
	private float viewX;
	private float viewY;
	private float viewW;
	private float viewH;
	private float chipY;

	/**
	 * Cards are grouped by sub-category: first in the order the tab lists them, then any other in the order
	 * it first appears. Chips only show when there are several.
	 */
	ModuleGrid(List<ModuleCard> source, List<String> order, boolean withChips, @Nullable String selectedSub) {
		List<String> subs = new ArrayList<>();
		for (String sub : order) for (ModuleCard card : source) if (card.module.subCategory().equals(sub) && !subs.contains(sub)) subs.add(sub);
		for (ModuleCard card : source) if (!subs.contains(card.module.subCategory())) subs.add(card.module.subCategory());
		for (String sub : subs) for (ModuleCard card : source) if (card.module.subCategory().equals(sub)) cards.add(card);
		if (withChips) {
			chips.add(FAVOURITES);
			chips.add(ALL);
			if (subs.size() > 1) chips.addAll(subs);
		}
		int n = chips.size();
		chipLabels = new Label[n];
		chipHover = new Anim[n];
		for (int i = 0; i < n; i++) {
			chipLabels[i] = new Label();
			chipHover[i] = new Anim(0f, 20f);
		}
		chipX = new float[n];
		chipW = new float[n];
		int saved = chips.indexOf(selectedSub);
		chip = saved >= 0 ? saved : Math.max(0, chips.indexOf(ALL));
		filter();
	}

	/** Sub-category whose cards are shown, remembered across sessions; null when the grid has no chips. */
	@Nullable
	String selectedSub() {
		return chips.isEmpty() ? null : chips.get(chip);
	}

	int count() {
		return visible.size();
	}

	/** Restarts the staggered appearance of the cards (tab switch, opening the GUI). */
	void show(boolean animate) {
		shownAt = System.nanoTime();
		for (ModuleCard card : cards) card.appear.snap(animate ? 0f : 1f);
	}

	/** Applies a search query (case-insensitive name match); returns whether it changed. */
	boolean search(String search) {
		if (search.equals(query)) return false;
		query = search;
		scroller.reset();
		filter();
		show(true);
		return true;
	}

	private void filter() {
		filteredFor = ModuleManager.version();
		visible.clear();
		String q = query.toLowerCase(Locale.ROOT);
		String sub = chips.isEmpty() ? ALL : chips.get(chip);
		boolean favourites = sub.equals(FAVOURITES);
		// A search lists the favourites first: two passes, starred then the rest.
		for (int pass = q.isEmpty() ? 1 : 0; pass < 2; pass++) {
			for (ModuleCard card : cards) {
				Module m = card.module;
				if (!q.isEmpty() && (pass == 0) != m.isFavourite()) continue;
				if (!q.isEmpty() && !m.name().toLowerCase(Locale.ROOT).contains(q)) continue;
				if (favourites ? !m.isFavourite() : !sub.equals(ALL) && !m.subCategory().equals(sub)) continue;
				visible.add(card);
			}
		}
	}

	private void select(int index) {
		if (index == chip) return;
		chip = index;
		scroller.reset();
		filter();
		show(true);
	}

	// ------------------------------------------------------------------ chips

	/** Lays out and draws the chips right-aligned to rightX; returns the left edge of the row (rightX without chips). */
	float renderChips(DrawContext ctx, float rightX, float centerY, double mx, double my) {
		int n = chips.size();
		if (n == 0) return rightX;
		SdfFont font = Fonts.medium();
		float total = -CHIP_GAP;
		for (int i = 0; i < n; i++) {
			chipW[i] = chips.get(i).equals(FAVOURITES) ? 24f : chipLabels[i].width(font, chips.get(i), Ui.SMALL) + 16f;
			total += chipW[i] + CHIP_GAP;
		}
		float cx = rightX - total;
		for (int i = 0; i < n; i++) {
			chipX[i] = cx;
			cx += chipW[i] + CHIP_GAP;
		}
		chipY = centerY - CHIP_H / 2f;
		float rowX = rightX - total;
		// A segmented control: a quiet track, and a plate that glides to the selected segment.
		Render2D.matte(ctx, rowX - 2f, chipY - 2f, total + 4f, CHIP_H + 4f, Ui.CELL_RADIUS + 2f, 0x1A000000, 0);
		Render2D.rim(ctx, rowX - 2f, chipY - 2f, total + 4f, CHIP_H + 4f, Ui.CELL_RADIUS + 2f, Ui.HAIRLINE, Ui.LIT);
		if (!selPlaced) {
			selX.snap(chipX[chip]);
			selW.snap(chipW[chip]);
			selPlaced = true;
		}
		float sx = selX.target(chipX[chip]).get();
		float sw = selW.target(chipW[chip]).get();
		Render2D.matte(ctx, sx, chipY, sw, CHIP_H, Ui.CELL_RADIUS, 0x1CFFFFFF, Ui.LIT);
		Render2D.rim(ctx, sx, chipY, sw, CHIP_H, Ui.CELL_RADIUS, 0x1AFFFFFF, Ui.LIT);
		for (int i = 0; i < n; i++) {
			float hv = chipHover[i].target(i != chip && Ui.inside(mx, my, chipX[i], chipY, chipW[i], CHIP_H) ? 1f : 0f).get();
			int color = i == chip ? Theme.text() : ColorUtil.lerp(Theme.textDim(), Theme.text(), hv);
			if (chips.get(i).equals(FAVOURITES)) Render2D.icon(ctx, Icons.STAR, chipX[i] + chipW[i] / 2f, centerY, 7f, i == chip ? Theme.accent() : color);
			else Render2D.textMiddle(ctx, font, chips.get(i), chipX[i] + 8f, centerY, Ui.SMALL, color);
		}
		return rowX - 2f;
	}

	boolean clickChip(double mx, double my) {
		for (int i = 0; i < chips.size(); i++) {
			if (Ui.inside(mx, my, chipX[i], chipY, chipW[i], CHIP_H)) {
				select(i);
				return true;
			}
		}
		return false;
	}

	// ------------------------------------------------------------------ grid

	static int columns(float width) {
		return width >= 470f ? 3 : width >= 230f ? 2 : 1;
	}

	/** Draws the grid into the viewport; drawerModule's card shows that its settings are open. */
	void render(DrawContext ctx, float x, float y, float w, float h, double mx, double my, @Nullable Module drawerModule, boolean mouseFree) {
		viewX = x;
		viewY = y;
		viewW = w;
		viewH = h;
		if (filteredFor != ModuleManager.version()) filter();
		int cols = columns(w);
		columns = cols;
		float gap = Ui.CARD_GAP;
		float cardW = (w - gap * (cols - 1)) / cols;
		int rows = (visible.size() + cols - 1) / cols;
		float content = rows * (Ui.CARD_HEIGHT + gap) - gap + 6f;
		float scroll = scroller.update(content, h);
		boolean inView = mouseFree && Ui.inside(mx, my, x, y, w, h);
		double cmx = inView ? mx : -1000;
		double cmy = inView ? my : -1000;

		if (visible.isEmpty()) {
			SdfFont font = Fonts.medium();
			boolean favourites = !chips.isEmpty() && chips.get(chip).equals(FAVOURITES);
			String text = favourites ? "No favourites yet" : "No modules found";
			Render2D.icon(ctx, favourites ? Icons.STAR : Icons.SEARCH, x + w / 2f, y + h / 2f - 8f, 12f, Theme.textFaint());
			Render2D.textMiddle(ctx, font, text, x + w / 2f - emptyWidth.width(font, text, Ui.TEXT) / 2f, y + h / 2f + 8f, Ui.TEXT, Theme.textDim());
			if (favourites) {
				String hint = "Star a module to pin it here";
				SdfFont small = Fonts.regular();
				Render2D.textMiddle(ctx, small, hint, x + w / 2f - hintWidth.width(small, hint, Ui.TINY) / 2f, y + h / 2f + 19f, Ui.TINY, Theme.textFaint());
			}
			return;
		}
		ctx.enableScissor((int) Math.floor(x - 6f), (int) Math.floor(y), (int) Math.ceil(x + w + 6f), (int) Math.ceil(y + h));
		float since = (System.nanoTime() - shownAt) / 1.0e9f;
		float alpha = Render2D.alpha();
		for (int i = 0; i < visible.size(); i++) {
			ModuleCard card = visible.get(i);
			float cx = x + (i % cols) * (cardW + gap);
			float cy = y + 2f + (i / cols) * (Ui.CARD_HEIGHT + gap) - scroll;
			if (since >= i * STAGGER) card.appear.target(1f);
			float a = card.appear.get();
			if (cy > y + h || cy + Ui.CARD_HEIGHT < y || a <= 0.005f) {
				card.hide();
				continue;
			}
			float rise = (1f - a) * 7f;
			Render2D.setAlpha(alpha * a * Ui.edgeFade(cy + rise, cy + rise + Ui.CARD_HEIGHT, y, y + h));
			card.focused = card == focus;
			card.render(ctx, cx, cy + rise, cardW, cmx, cmy, card.module == drawerModule);
		}
		Render2D.setAlpha(alpha);
		ctx.disableScissor();
		scroller.drawBar(ctx, x + w + 4f, y, h, content, inView);
	}

	@Nullable
	ModuleCard cardAt(double mx, double my) {
		if (!Ui.inside(mx, my, viewX, viewY, viewW, viewH)) return null;
		for (ModuleCard card : visible) if (card.over(mx, my)) return card;
		return null;
	}

	/** The card under the mouse this frame, for the tooltip. */
	@Nullable
	ModuleCard hovered() {
		for (ModuleCard card : visible) if (card.isHovered()) return card;
		return null;
	}

	/** Moves the keyboard focus by dx cards and dy rows (the first arrow press lands on the first card) and scrolls it into view. */
	void moveFocus(int dx, int dy) {
		if (visible.isEmpty()) return;
		int i = focus == null ? -1 : visible.indexOf(focus);
		i = i < 0 ? 0 : Math.max(0, Math.min(visible.size() - 1, i + dx + dy * columns));
		focus = visible.get(i);
		float top = 2f + (i / columns) * (Ui.CARD_HEIGHT + Ui.CARD_GAP);
		scroller.reveal(top - 4f, top + Ui.CARD_HEIGHT + 4f, viewH);
	}

	/** The card with the keyboard focus, if it is still listed. */
	@Nullable
	ModuleCard focused() {
		return focus != null && visible.contains(focus) ? focus : null;
	}

	void clearFocus() {
		focus = null;
	}

	boolean over(double mx, double my) {
		return Ui.inside(mx, my, viewX, viewY, viewW, viewH);
	}

	void scroll(double amount) {
		scroller.scroll(amount);
	}
}
