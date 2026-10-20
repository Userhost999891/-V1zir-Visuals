package dev.v1zir.visuals.gui.clickgui;

import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.font.SdfFont;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.gui.DrawContext;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A draggable panel of one category: a centred accent title over a hairline, a row of
 * sub-category chips that filter the panel, and a rounded cell per module.
 */
public final class CategoryPanel {
	private static final float CHIP_H = 11f;
	private static final float CHIP_GAP = 3f;
	/** Space above the first and below the last module cell. */
	private static final float BODY_TOP = 3f;
	private static final float BODY_BOTTOM = 4f;

	final Category category;
	final List<ModuleRow> rows = new ArrayList<>();
	/** Opening animation; the screen starts the panels one after another. */
	final Anim appear = new Anim(0f, 11f);
	float x;
	float y;
	private boolean dragging;
	private double dragX;
	private double dragY;
	private float scroll;
	private final Anim scrollAnim = new Anim(0f, 16f);
	private float visibleHeight;
	/** Rows shown right now: the selected sub-category, or every match while searching. */
	private final List<ModuleRow> visibleRows = new ArrayList<>();
	private String query = "";
	private final Label titleWidth = new Label();

	// Sub-category chips, in the order their first module was registered.
	private final List<String> subs = new ArrayList<>();
	private final Label[] chipLabels;
	private final Anim[] chipHover;
	/** Chip layout of the last frame, relative to the panel's left edge. */
	private final float[] chipX;
	private final float[] chipW;
	private float chipPad;
	private int sub;
	private final Anim chipsShown;
	private final Anim selX = new Anim(0f, 18f);
	private final Anim selW = new Anim(0f, 18f);
	private boolean selPlaced;

	// When the shown modules change, the body grows or shrinks from its old height and the new cells fade in.
	private float bodyFrom;
	private final Anim bodyBlend = new Anim(1f, 14f);
	private final Anim rowsFade = new Anim(1f, 12f);

	CategoryPanel(Category category, float x, float y, @Nullable String selectedSub) {
		this.category = category;
		this.x = x;
		this.y = y;
		for (Module module : ModuleManager.byCategory(category)) {
			rows.add(new ModuleRow(module));
			if (!subs.contains(module.subCategory())) subs.add(module.subCategory());
		}
		int n = subs.size();
		chipLabels = new Label[n];
		chipHover = new Anim[n];
		for (int i = 0; i < n; i++) {
			chipLabels[i] = new Label();
			chipHover[i] = new Anim(0f, 20f);
		}
		chipX = new float[n];
		chipW = new float[n];
		sub = Math.max(0, subs.indexOf(selectedSub));
		chipsShown = new Anim(hasChips() ? 1f : 0f, 16f);
		filter();
	}

	/** Sub-category whose modules are shown, remembered across sessions. */
	@Nullable
	String selectedSub() {
		return subs.isEmpty() ? null : subs.get(sub);
	}

	/** The chip row only appears when there is something to choose, and not while searching. */
	private boolean hasChips() {
		return subs.size() > 1 && query.isEmpty();
	}

	/** Applies the search query; returns whether this panel has anything to show. */
	boolean matches(String search) {
		if (!search.equals(query)) {
			query = search;
			bodyFrom = visibleHeight;
			bodyBlend.snap(0f);
			bodyBlend.target(1f);
			filter();
		}
		return !visibleRows.isEmpty();
	}

	private void filter() {
		visibleRows.clear();
		String q = query.toLowerCase(Locale.ROOT);
		String selected = selectedSub();
		for (ModuleRow row : rows) {
			boolean show = q.isEmpty() ? row.module.subCategory().equals(selected) : row.module.name().toLowerCase(Locale.ROOT).contains(q);
			if (show) visibleRows.add(row);
		}
	}

	private void select(int index) {
		if (index == sub) return;
		sub = index;
		bodyFrom = visibleHeight;
		bodyBlend.snap(0f);
		bodyBlend.target(1f);
		rowsFade.snap(0f);
		rowsFade.target(1f);
		scroll = 0f;
		scrollAnim.snap(0f);
		filter();
	}

	/** The module under the mouse in the last frame, if any. */
	@Nullable
	Module hovered() {
		for (ModuleRow row : visibleRows) if (row.isHovered()) return row.module;
		return null;
	}

	/** Height of the header plus the (possibly collapsing) chip row. */
	private float top() {
		return Ui.HEADER_HEIGHT + Ui.CHIPS_HEIGHT * chipsShown.value();
	}

	void render(DrawContext ctx, double mouseX, double mouseY, String search, int screenHeight) {
		matches(search);
		float w = Ui.PANEL_WIDTH;
		float chips = chipsShown.target(hasChips() ? 1f : 0f).get();
		float top = Ui.HEADER_HEIGHT + Ui.CHIPS_HEIGHT * chips;
		float content = BODY_TOP + BODY_BOTTOM;
		for (ModuleRow row : visibleRows) content += row.height();
		float maxBody = Math.max(60f, screenHeight - y - top - 16f);
		float body = Math.min(content, maxBody);
		visibleHeight = bodyFrom + (body - bodyFrom) * bodyBlend.get();
		float h = top + visibleHeight;
		float maxScroll = Math.max(0f, content - body);
		scroll = Math.max(0f, Math.min(scroll, maxScroll));
		float sc = scrollAnim.target(scroll).get();
		int accent = Theme.accent();
		int accent2 = Theme.accent2();

		Ui.surface(ctx, x, y, w, h, accent);
		Ui.header(ctx, x, y, w, category.icon(), category.title(), titleWidth, accent, accent2);
		if (chips > 0.005f) renderChips(ctx, w, chips, mouseX, mouseY, accent);

		float bodyY = y + top;
		ctx.enableScissor((int) Math.floor(x), (int) Math.floor(bodyY), (int) Math.ceil(x + w), (int) Math.ceil(bodyY + visibleHeight));
		float alpha = Render2D.alpha();
		Render2D.setAlpha(alpha * rowsFade.get());
		float cy = bodyY + BODY_TOP - sc;
		boolean mouseInBody = Ui.inside(mouseX, mouseY, x, bodyY, w, visibleHeight);
		for (ModuleRow row : visibleRows) {
			cy += row.render(ctx, x, cy, w, mouseInBody ? mouseX : -1000, mouseInBody ? mouseY : -1000);
		}
		Render2D.setAlpha(alpha);
		ctx.disableScissor();
		if (maxScroll > 0f) {
			float trackH = body - 10f;
			float thumbH = Math.max(14f, trackH * body / content);
			float thumbY = bodyY + 5f + (trackH - thumbH) * (sc / maxScroll);
			Render2D.round(ctx, x + w - 3f, thumbY, 1.5f, thumbH, 0.75f, ColorUtil.withAlpha(accent, 140));
		}
	}

	/** Sub-category chips; an outlined pill glides to the selected one, hovering only brightens the text. */
	private void renderChips(DrawContext ctx, float w, float shown, double mx, double my, int accent) {
		SdfFont font = Fonts.medium();
		int n = subs.size();
		float text = 0f;
		for (int i = 0; i < n; i++) text += chipLabels[i].width(font, subs.get(i), Ui.SMALL);
		// Natural padding when the chips fit, tighter when a panel has many sub-categories.
		chipPad = Math.max(2f, Math.min(6f, (w - 12f - text - CHIP_GAP * (n - 1)) / (n * 2f)));
		float cx = (w - text - chipPad * 2f * n - CHIP_GAP * (n - 1)) / 2f;
		for (int i = 0; i < n; i++) {
			chipX[i] = cx;
			chipW[i] = chipLabels[i].width(font, subs.get(i), Ui.SMALL) + chipPad * 2f;
			cx += chipW[i] + CHIP_GAP;
		}
		if (!selPlaced) {
			selX.snap(chipX[sub]);
			selW.snap(chipW[sub]);
			selPlaced = true;
		}

		float rowY = y + Ui.HEADER_HEIGHT;
		boolean clip = shown < 0.999f;
		if (clip) ctx.enableScissor((int) Math.floor(x), (int) Math.floor(rowY), (int) Math.ceil(x + w), (int) Math.ceil(rowY + Ui.CHIPS_HEIGHT * shown));
		float alpha = Render2D.alpha();
		Render2D.setAlpha(alpha * shown);
		float cy = rowY + (Ui.CHIPS_HEIGHT - CHIP_H) / 2f;
		float sx = x + selX.target(chipX[sub]).get();
		float sw = selW.target(chipW[sub]).get();
		int tint = ColorUtil.lerp(0xFFFFFFFF, accent, 0.35f);
		Render2D.round(ctx, sx, cy, sw, CHIP_H, Ui.CELL_RADIUS, ColorUtil.withAlpha(tint, 30));
		Render2D.outline(ctx, sx, cy, sw, CHIP_H, Ui.CELL_RADIUS, 1f, ColorUtil.withAlpha(tint, 96));
		for (int i = 0; i < n; i++) {
			float hv = chipHover[i].target(i != sub && overChip(i, mx, my) ? 1f : 0f).get();
			int color = i == sub ? 0xFFFFFFFF : ColorUtil.lerp(Theme.textDim(), Theme.text(), hv);
			Render2D.textMiddle(ctx, font, subs.get(i), x + chipX[i] + chipPad, cy + CHIP_H / 2f, Ui.SMALL, color);
		}
		Render2D.setAlpha(alpha);
		if (clip) ctx.disableScissor();
	}

	/** Hit area of a chip: its slot in the row, the full row height. */
	private boolean overChip(int i, double mx, double my) {
		return Ui.inside(mx, my, x + chipX[i] - CHIP_GAP / 2f, y + Ui.HEADER_HEIGHT, chipW[i] + CHIP_GAP, Ui.CHIPS_HEIGHT);
	}

	float height() {
		return top() + visibleHeight;
	}

	boolean overHeader(double mx, double my) {
		return Ui.inside(mx, my, x, y, Ui.PANEL_WIDTH, Ui.HEADER_HEIGHT);
	}

	boolean over(double mx, double my) {
		return Ui.inside(mx, my, x, y, Ui.PANEL_WIDTH, height());
	}

	boolean mouseClicked(double mx, double my, int button) {
		if (overHeader(mx, my) && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			dragging = true;
			dragX = mx - x;
			dragY = my - y;
			return true;
		}
		if (hasChips() && chipsShown.value() > 0.5f) {
			for (int i = 0; i < subs.size(); i++) {
				if (overChip(i, mx, my)) {
					if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) select(i);
					return true;
				}
			}
		}
		float top = top();
		if (!Ui.inside(mx, my, x, y + top, Ui.PANEL_WIDTH, visibleHeight)) return Ui.inside(mx, my, x, y, Ui.PANEL_WIDTH, top);
		for (ModuleRow row : visibleRows) if (row.mouseClicked(mx, my, button)) return true;
		return true;
	}

	void mouseDragged(double mx, double my, int screenWidth, int screenHeight) {
		if (dragging) {
			x = (float) Math.max(0, Math.min(screenWidth - Ui.PANEL_WIDTH, mx - dragX));
			y = (float) Math.max(24, Math.min(screenHeight - Ui.HEADER_HEIGHT, my - dragY));
			return;
		}
		for (ModuleRow row : visibleRows) row.mouseDragged(mx, my);
	}

	void mouseReleased() {
		dragging = false;
		for (ModuleRow row : rows) row.mouseReleased();
	}

	boolean mouseScrolled(double mx, double my, double amount) {
		if (!over(mx, my)) return false;
		scroll -= (float) amount * 18f;
		return true;
	}

	boolean keyPressed(int key) {
		for (ModuleRow row : rows) if (row.keyPressed(key)) return true;
		return false;
	}

	boolean isBinding() {
		for (ModuleRow row : rows) if (row.isBinding()) return true;
		return false;
	}
}
