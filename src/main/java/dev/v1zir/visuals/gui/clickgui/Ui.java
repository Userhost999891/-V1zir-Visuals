package dev.v1zir.visuals.gui.clickgui;

import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.font.SdfFont;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.render.ui.ShapeElement;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.gui.DrawContext;

/** Shared metrics (in GUI pixels) and small matte widgets of the click GUI. */
public final class Ui {
	/** Corner radius of the window. */
	public static final float RADIUS = 10f;
	/** Height of the top bar (logo, title, search). */
	public static final float BAR = 34f;
	/** Inner margin between the window edge and its sub-surfaces. */
	public static final float PAD = 8f;
	public static final float CARD_HEIGHT = 38f;
	public static final float CARD_GAP = 6f;
	public static final float CARD_RADIUS = 6f;
	/** Corner radius of chips, wells and other small surfaces. */
	public static final float CELL_RADIUS = 4f;
	public static final float TEXT = 7f;
	public static final float SMALL = 6.5f;
	/** Size of setting labels. */
	public static final float LABEL = 7f;
	/** Size of descriptions and hints. */
	public static final float TINY = 6f;
	public static final float SWITCH_W = 16f;
	public static final float SWITCH_H = 9f;
	/** Shapes that catch the cursor light. */
	public static final int LIT = ShapeElement.LIT;

	private Ui() {
	}

	/** Toggle switch: a recessed matte track that fills with the accent, the knob at position t (0 off, 1 on). */
	static void toggle(DrawContext ctx, float x, float y, float t, float hover) {
		float r = SWITCH_H / 2f;
		Render2D.matte(ctx, x, y, SWITCH_W, SWITCH_H, r, ColorUtil.withAlpha(0xFFFFFF, Math.round(16 + 12 * hover)), 0);
		if (t > 0.01f) {
			Render2D.shadow(ctx, x, y, SWITCH_W, SWITCH_H, r, 5f, ColorUtil.withAlpha(Theme.accent(), Math.round((60 + 30 * hover) * t)));
			Render2D.gradientH(ctx, x, y, SWITCH_W, SWITCH_H, r, ColorUtil.fade(Theme.accent(), t), ColorUtil.fade(Theme.accent2(), t));
		}
		float kr = r - 1.5f;
		float kx = x + 1.5f + kr + t * (SWITCH_W - 3f - kr * 2f);
		float ky = y + r;
		Render2D.shadow(ctx, kx - kr, ky - kr + 0.6f, kr * 2f, kr * 2f, kr, 2.5f, 0x55000000);
		Render2D.circle(ctx, kx, ky, kr, ColorUtil.lerp(0xFFB9BDC8, 0xFFFFFFFF, Math.max(t, hover)));
	}

	/** Small key chip ("RShift") in a faint rim; returns its width. */
	static float keyChip(DrawContext ctx, Label width, String key, float x, float centerY, int textColor) {
		SdfFont font = Fonts.semibold();
		float size = 5.5f;
		float w = width.width(font, key, size) + 7f;
		float h = 9f;
		Render2D.round(ctx, x, centerY - h / 2f, w, h, 3f, 0x14FFFFFF);
		Render2D.outline(ctx, x, centerY - h / 2f, w, h, 3f, 1f, 0x1CFFFFFF);
		Render2D.textMiddle(ctx, font, key, x + 3.5f, centerY, size, textColor);
		return w;
	}

	/** Square icon button with a matte hover plate; press scales the icon down a little. */
	static void iconButton(DrawContext ctx, float x, float y, float size, int icon, float hover, float press, boolean active) {
		int accent = Theme.accent();
		if (active || hover > 0.01f) {
			int plate = active ? ColorUtil.withAlpha(accent, 40) : ColorUtil.withAlpha(0xFFFFFF, Math.round(16 * hover));
			Render2D.matte(ctx, x, y, size, size, 6f, plate, LIT);
		}
		Render2D.rim(ctx, x, y, size, size, 6f, active ? ColorUtil.withAlpha(accent, 110) : ColorUtil.withAlpha(0xFFFFFF, Math.round(18 + 22 * hover)), LIT);
		int color = active ? ColorUtil.lerp(accent, 0xFFFFFFFF, 0.3f) : ColorUtil.lerp(Theme.textDim(), Theme.text(), hover);
		Render2D.icon(ctx, icon, x + size / 2f, y + size / 2f, 8.5f * (1f - 0.12f * press), color);
	}

	/** Section heading: an accent icon and a semibold title. */
	static void heading(DrawContext ctx, float x, float y, int icon, String title) {
		Render2D.icon(ctx, icon, x + 4f, y + 4.5f, 7.5f, ColorUtil.lerp(Theme.accent(), 0xFFFFFFFF, 0.2f));
		Render2D.textMiddle(ctx, Fonts.semibold(), title, x + 12f, y + 4.5f, 7.5f, Theme.text());
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
