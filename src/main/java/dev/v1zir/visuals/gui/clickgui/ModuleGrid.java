package dev.v1zir.visuals.gui.clickgui;

import dev.v1zir.visuals.module.Module;
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
 * a row of sub-category chips filters the grid; an outlined pill glides to the selected chip.
 */
final class ModuleGrid {
	static final String ALL = "All";
	/** Delay between two cards starting to appear, in seconds. */
	private static final float STAGGER = 0.018f;
	private static final float CHIP_H = 14f;
	private static final float CHIP_GAP = 2f;

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
		if (withChips && subs.size() > 1) {
			chips.add(ALL);
			chips.addAll(subs);
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
		chip = Math.max(0, chips.indexOf(selectedSub));
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
		visible.clear();
		String q = query.toLowerCase(Locale.ROOT);
		String sub = chip == 0 ? null : chips.get(chip);
		for (ModuleCard card : cards) {
			Module m = card.module;
			if (!q.isEmpty() && !m.name().toLowerCase(Locale.ROOT).contains(q)) continue;
			if (sub != null && !m.subCategory().equals(sub)) continue;
			visible.add(card);
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
			chipW[i] = chipLabels[i].width(font, chips.get(i), Ui.SMALL) + 14f;
			total += chipW[i] + CHIP_GAP;
		}
		float cx = rightX - total;
		for (int i = 0; i < n; i++) {
			chipX[i] = cx;
			cx += chipW[i] + CHIP_GAP;
		}
		chipY = centerY - CHIP_H / 2f;
		float rowX = rightX - total;
		// A recessed track holds the chips; the selection pill glides along it.
		Render2D.matte(ctx, rowX - 2f, chipY - 2f, total + 4f, CHIP_H + 4f, 7f, 0x22000000, 0);
		Render2D.rim(ctx, rowX - 2f, chipY - 2f, total + 4f, CHIP_H + 4f, 7f, 0x12FFFFFF, Ui.LIT);
		if (!selPlaced) {
			selX.snap(chipX[chip]);
			selW.snap(chipW[chip]);
			selPlaced = true;
		}
		float sx = selX.target(chipX[chip]).get();
		float sw = selW.target(chipW[chip]).get();
		int accent = Theme.accent();
		Render2D.matte(ctx, sx, chipY, sw, CHIP_H, 5.5f, ColorUtil.withAlpha(ColorUtil.lerp(0xFFFFFF, accent, 0.35f), 34), Ui.LIT);
		Render2D.rim(ctx, sx, chipY, sw, CHIP_H, 5.5f, ColorUtil.withAlpha(ColorUtil.lerp(0xFFFFFF, accent, 0.45f), 90), Ui.LIT);
		for (int i = 0; i < n; i++) {
			float hv = chipHover[i].target(i != chip && Ui.inside(mx, my, chipX[i], chipY, chipW[i], CHIP_H) ? 1f : 0f).get();
			int color = i == chip ? 0xFFFFFFFF : ColorUtil.lerp(Theme.textDim(), Theme.text(), hv);
			Render2D.textMiddle(ctx, font, chips.get(i), chipX[i] + 7f, centerY, Ui.SMALL, color);
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
		int cols = columns(w);
		float gap = Ui.CARD_GAP;
		float cardW = (w - 4f - gap * (cols - 1)) / cols;
		int rows = (visible.size() + cols - 1) / cols;
		float content = rows * (Ui.CARD_HEIGHT + gap) - gap + 6f;
		float scroll = scroller.update(content, h);
		boolean inView = mouseFree && Ui.inside(mx, my, x, y, w, h);
		double cmx = inView ? mx : -1000;
		double cmy = inView ? my : -1000;

		if (visible.isEmpty()) {
			SdfFont font = Fonts.medium();
			String text = "No modules found";
			Render2D.icon(ctx, Icons.SEARCH, x + w / 2f, y + h / 2f - 10f, 14f, Theme.textFaint());
			Render2D.textMiddle(ctx, font, text, x + w / 2f - emptyWidth.width(font, text, Ui.TEXT) / 2f, y + h / 2f + 8f, Ui.TEXT, Theme.textDim());
			return;
		}
		ctx.enableScissor((int) Math.floor(x - 6f), (int) Math.floor(y), (int) Math.ceil(x + w + 6f), (int) Math.ceil(y + h));
		float since = (System.nanoTime() - shownAt) / 1.0e9f;
		float alpha = Render2D.alpha();
		for (int i = 0; i < visible.size(); i++) {
			ModuleCard card = visible.get(i);
			float cx = x + 2f + (i % cols) * (cardW + gap);
			float cy = y + 3f + (i / cols) * (Ui.CARD_HEIGHT + gap) - scroll;
			if (since >= i * STAGGER) card.appear.target(1f);
			float a = card.appear.get();
			if (cy > y + h || cy + Ui.CARD_HEIGHT < y || a <= 0.005f) {
				card.hide();
				continue;
			}
			float rise = (1f - a) * 7f;
			Render2D.setAlpha(alpha * a * Ui.edgeFade(cy + rise, cy + rise + Ui.CARD_HEIGHT, y, y + h));
			card.render(ctx, cx, cy + rise, cardW, cmx, cmy, card.module == drawerModule);
		}
		Render2D.setAlpha(alpha);
		ctx.disableScissor();
		scroller.drawBar(ctx, x + w + 1f, y, h, content, inView);
	}

	@Nullable
	ModuleCard cardAt(double mx, double my) {
		if (!Ui.inside(mx, my, viewX, viewY, viewW, viewH)) return null;
		for (ModuleCard card : visible) if (card.over(mx, my)) return card;
		return null;
	}

	@Nullable
	Module hovered() {
		for (ModuleCard card : visible) if (card.isHovered()) return card.module;
		return null;
	}

	boolean over(double mx, double my) {
		return Ui.inside(mx, my, viewX, viewY, viewW, viewH);
	}

	void scroll(double amount) {
		scroller.scroll(amount);
	}
}
