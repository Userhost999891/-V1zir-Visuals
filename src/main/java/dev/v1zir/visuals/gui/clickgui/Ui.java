package dev.v1zir.visuals.gui.clickgui;

import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.font.SdfFont;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.render.ui.ShapeElement;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.gui.DrawContext;

/**
 * Shared metrics and small widgets of the click GUI. Metrics sit on a 4 px grid; radii step down from the
 * window (10) to panels (8), cards (6) and controls (4). The accent marks state only: the selected tab,
 * enabled modules and focused or active controls. Everything else stays neutral.
 */
public final class Ui {
	/** Corner radius of the window. */
	public static final float RADIUS = 10f;
	/** Corner radius of panels inside the window (sidebar, settings column, groups). */
	public static final float PANEL_RADIUS = 8f;
	/** Height of the top bar (logo, title, search). */
	public static final float BAR = 32f;
	/** Margin between the window edge and its panels, and between panels. */
	public static final float PAD = 8f;
	public static final float CARD_HEIGHT = 40f;
	public static final float CARD_GAP = 8f;
	public static final float CARD_RADIUS = 6f;
	/** Corner radius of chips, buttons, fields and other controls. */
	public static final float CELL_RADIUS = 4f;
	public static final float TEXT = 7f;
	public static final float SMALL = 6.5f;
	/** Size of setting labels. */
	public static final float LABEL = 7f;
	/** Size of descriptions and hints. */
	public static final float TINY = 6f;
	/** Size of interface icons. */
	public static final float ICON = 8f;
	public static final float SWITCH_W = 16f;
	public static final float SWITCH_H = 8f;
	/** Hairline separators and quiet rims. */
	public static final int HAIRLINE = 0x14FFFFFF;
	/** Shapes that catch the cursor light. */
	public static final int LIT = ShapeElement.LIT;

	private Ui() {
	}

	/** Toggle switch: the track fills with the accent and the knob slides as t goes from 0 (off) to 1 (on). */
	static void toggle(DrawContext ctx, float x, float y, float t, float hover) {
		float r = SWITCH_H / 2f;
		Render2D.round(ctx, x, y, SWITCH_W, SWITCH_H, r, ColorUtil.withAlpha(0xFFFFFF, Math.round(26 + 14 * hover)));
		if (t > 0.01f) Render2D.round(ctx, x, y, SWITCH_W, SWITCH_H, r, ColorUtil.fade(Theme.accent(), t));
		float kr = r - 1f;
		float kx = x + 1f + kr + t * (SWITCH_W - 2f - kr * 2f);
		Render2D.circle(ctx, kx, y + r, kr, ColorUtil.lerp(0xFFC9CACF, 0xFFFFFFFF, Math.max(t, hover)));
	}

	/** Small key chip ("RShift") with a quiet rim; returns its width. */
	static float keyChip(DrawContext ctx, Label width, String key, float x, float centerY, int textColor) {
		SdfFont font = Fonts.semibold();
		float size = 5.5f;
		float w = width.width(font, key, size) + 8f;
		float h = 9f;
		Render2D.round(ctx, x, centerY - h / 2f, w, h, 3f, 0x0FFFFFFF);
		Render2D.outline(ctx, x, centerY - h / 2f, w, h, 3f, 1f, HAIRLINE);
		Render2D.textMiddle(ctx, font, key, x + 4f, centerY, size, textColor);
		return w;
	}

	/** Square icon button: a neutral plate that lights up on hover; pressing scales the icon down a little. */
	static void iconButton(DrawContext ctx, float x, float y, float size, int icon, float hover, float press) {
		Render2D.matte(ctx, x, y, size, size, CELL_RADIUS, ColorUtil.withAlpha(0xFFFFFF, Math.round(8 + 10 * hover)), LIT);
		Render2D.rim(ctx, x, y, size, size, CELL_RADIUS, ColorUtil.withAlpha(0xFFFFFF, Math.round(18 + 14 * hover)), LIT);
		Render2D.icon(ctx, icon, x + size / 2f, y + size / 2f, ICON * (1f - 0.12f * press), ColorUtil.lerp(Theme.textDim(), Theme.text(), hover));
	}

	/** Section heading: a muted icon and a semibold title, centred on y + 4. */
	static void heading(DrawContext ctx, float x, float y, int icon, String title) {
		Render2D.icon(ctx, icon, x + 4f, y + 4f, 7f, Theme.textDim());
		Render2D.textMiddle(ctx, Fonts.semibold(), title, x + 12f, y + 4f, 7.5f, Theme.text());
	}

	/**
	 * Opacity of an item that scrolls inside a viewport: it fades out over its own height as it
	 * leaves through the top or bottom edge, which gives scroll areas their soft edges.
	 */
	static float edgeFade(float top, float bottom, float viewTop, float viewBottom) {
		float span = Math.max(8f, Math.min(bottom - top, 28f));
		float a = Math.min((bottom - viewTop) / span, (viewBottom - top) / span);
		return Math.max(0f, Math.min(1f, a));
	}

	public static boolean inside(double mx, double my, float x, float y, float w, float h) {
		return mx >= x && mx <= x + w && my >= y && my <= y + h;
	}
}
