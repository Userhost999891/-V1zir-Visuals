package dev.v1zir.visuals.gui.clickgui;

import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.font.SdfFont;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.gui.DrawContext;

/** Shared metrics (in GUI pixels) and surfaces of the click GUI. */
public final class Ui {
	public static final float PANEL_WIDTH = 116f;
	public static final float PANEL_GAP = 10f;
	public static final float HEADER_HEIGHT = 22f;
	/** Row of sub-category chips under the header (hidden when a panel has one sub-category). */
	public static final float CHIPS_HEIGHT = 17f;
	public static final float ROW_HEIGHT = 16f;
	public static final float RADIUS = 7f;
	/** Corner radius of module cells, chips and other small surfaces. */
	public static final float CELL_RADIUS = 4f;
	/** Margin between a module cell and the panel edge. */
	public static final float CELL_INSET = 5f;
	public static final float TEXT = 7f;
	public static final float SMALL = 6.5f;
	/** Size of setting labels. */
	public static final float LABEL = 7f;
	private static final float TITLE = 8.5f;
	private static final float TITLE_ICON = 8f;
	/** Slate blue-grey that tints the panels; the accent colours it slightly. */
	private static final int SLATE = 0xFF2A3A50;

	private Ui() {
	}

	/** Panel of the click GUI: the Theme surface under a slate tint, deeper towards the bottom, and a thin light rim. */
	static void surface(DrawContext ctx, float x, float y, float w, float h, int accent) {
		Render2D.shadow(ctx, x, y + 4f, w, h, RADIUS, 18f, Theme.shadow());
		Render2D.panel(ctx, x, y, w, h, RADIUS);
		int slate = ColorUtil.lerp(SLATE, accent, 0.12f);
		Render2D.gradientV(ctx, x, y, w, h, RADIUS, ColorUtil.withAlpha(slate, 100), ColorUtil.withAlpha(slate, 44));
		Render2D.outline(ctx, x, y, w, h, RADIUS, 1f, 0x38FFFFFF, 0x38FFFFFF, 0x16FFFFFF, 0x16FFFFFF);
	}

	/** Centred icon and bold title in a light accent on a lighter band, over an accent hairline. */
	static void header(DrawContext ctx, float x, float y, float w, int icon, String title, Label titleWidth, int accent, int accent2) {
		float mid = y + HEADER_HEIGHT / 2f;
		Render2D.round(ctx, x, y, w, HEADER_HEIGHT, RADIUS, Render2D.CORNERS_TOP, 0x0DFFFFFF);

		SdfFont font = Fonts.bold();
		float tw = titleWidth.width(font, title, TITLE);
		float group = TITLE_ICON + 4f + tw;
		float gx = x + (w - group) / 2f;
		int color = ColorUtil.lerp(accent, 0xFFFFFFFF, 0.3f);
		Render2D.shadow(ctx, gx - 2f, mid - 3f, group + 4f, 6f, 3f, 10f, ColorUtil.withAlpha(accent, 50));
		Render2D.icon(ctx, icon, gx + TITLE_ICON / 2f, mid, TITLE_ICON, color);
		Render2D.text(ctx, font, title, gx + TITLE_ICON + 4f, Render2D.middle(font, mid, TITLE), TITLE, color);

		// Separator: brightest in the middle, fading out towards both edges.
		float ly = y + HEADER_HEIGHT - 0.5f;
		float half = w / 2f - 6f;
		Render2D.gradientH(ctx, x + 6f, ly, half, 1f, 0.5f, ColorUtil.withAlpha(accent, 0), ColorUtil.withAlpha(accent, 190));
		Render2D.gradientH(ctx, x + w / 2f, ly, half, 1f, 0.5f, ColorUtil.withAlpha(accent2, 190), ColorUtil.withAlpha(accent2, 0));
	}

	public static boolean inside(double mx, double my, float x, float y, float w, float h) {
		return mx >= x && mx <= x + w && my >= y && my <= y + h;
	}
}
