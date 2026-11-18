package dev.v1zir.visuals.gui.custom;

import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.font.SdfFont;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.ColorUtil;
import net.minecraft.client.gui.DrawContext;

/**
 * The game's widgets in the PS5 style theme: dark blue translucent surfaces with a faint edge, white text, and the
 * focus of a console's menus: what the pointer is on turns white with dark text and a soft white glow around it.
 * Buttons are pills; sliders have a white knob, text fields a white ring when typed into, slots light up with a white
 * ring. CustomUi draws through these while the style is on.
 */
public final class ConsoleUi {
	/** Surfaces, their edge, text on them, and the dark text on a white focused surface. */
	public static final int SURFACE = 0xB3161D38;
	public static final int SURFACE_DIM = 0x66101528;
	public static final int EDGE = 0x24FFFFFF;
	public static final int TEXT = 0xF2FFFFFF;
	public static final int TEXT_DIM = 0x73FFFFFF;
	public static final int FOCUS = 0xFFF4F6FB;
	public static final int INK = 0xFF0E1224;
	public static final int GLOW = 0x8CFFFFFF;
	public static final int BLUE = 0xFFA9C4FF;

	private ConsoleUi() {
	}

	/** A pill button; focused it turns white. */
	public static void button(DrawContext ctx, float x, float y, float w, float h, String label, boolean focused, boolean active) {
		float r = Math.min(h / 2f, 10f);
		boolean lit = focused && active;
		if (lit) {
			Render2D.shadow(ctx, x, y, w, h, r, 9f, GLOW);
			Render2D.round(ctx, x, y, w, h, r, FOCUS);
		} else {
			Render2D.shadow(ctx, x, y + 1.5f, w, h, r, 8f, 0x50000000);
			Render2D.round(ctx, x, y, w, h, r, active ? SURFACE : SURFACE_DIM);
			Render2D.outline(ctx, x, y, w, h, r, 1f, EDGE);
		}
		label(ctx, label, x + w / 2f, y + h / 2f, w - 10f, 8f, lit ? INK : active ? TEXT : TEXT_DIM);
	}

	/** A slider: a pill whose filled part runs to a white knob. */
	public static void slider(DrawContext ctx, float x, float y, float w, float h, double value, String label, boolean focused, boolean active) {
		float r = Math.min(h / 2f, 10f);
		float v = (float) Math.max(0.0, Math.min(1.0, value));
		Render2D.shadow(ctx, x, y + 1.5f, w, h, r, 8f, 0x50000000);
		Render2D.round(ctx, x, y, w, h, r, active ? SURFACE : SURFACE_DIM);
		float fill = Math.max(r * 2f, w * v);
		Render2D.gradientH(ctx, x, y, fill, h, r, 0x2EFFFFFF, ColorUtil.withAlpha(BLUE, 90));
		Render2D.outline(ctx, x, y, w, h, r, focused && active ? 1.5f : 1f, focused && active ? 0xCCFFFFFF : EDGE);
		float knob = Math.max(3f, h * 0.3f);
		float kx = Math.max(x + r, Math.min(x + w - r, x + w * v));
		if (focused && active) Render2D.glow(ctx, kx - knob, y + h / 2f - knob, knob * 2f, knob * 2f, knob, 6f, GLOW, GLOW);
		Render2D.circle(ctx, kx, y + h / 2f, knob, active ? 0xFFFFFFFF : 0x99FFFFFF);
		label(ctx, label, x + w / 2f, y + h / 2f, w - 12f, 8f, active ? TEXT : TEXT_DIM);
	}

	/** Behind a text field (the game draws its text and cursor on top): a white ring while it is typed into. */
	public static void textField(DrawContext ctx, float x, float y, float w, float h, boolean focused, boolean hovered, boolean active) {
		float r = Math.min(h / 2f, 7f);
		if (focused) Render2D.shadow(ctx, x, y, w, h, r, 8f, 0x66FFFFFF);
		Render2D.round(ctx, x, y, w, h, r, active ? 0xCC0E1430 : 0x800E1430);
		Render2D.outline(ctx, x, y, w, h, r, focused ? 1.5f : 1f, focused ? 0xF2FFFFFF : hovered ? 0x55FFFFFF : EDGE);
	}

	/** A tooltip around the text area (x, y, w, h as the game passes it). */
	public static void tooltip(DrawContext ctx, float x, float y, float w, float h) {
		float pad = 4f;
		Render2D.shadow(ctx, x - pad, y - pad + 2f, w + pad * 2f, h + pad * 2f, 7f, 12f, 0x70000000);
		Render2D.round(ctx, x - pad, y - pad, w + pad * 2f, h + pad * 2f, 7f, 0xF20E1430);
		Render2D.outline(ctx, x - pad, y - pad, w + pad * 2f, h + pad * 2f, 7f, 1f, 0x2EFFFFFF);
	}

	/** The panel behind an inventory or container. */
	public static void panel(DrawContext ctx, float x, float y, float w, float h) {
		Render2D.shadow(ctx, x, y + 4f, w, h, 11f, 20f, 0x80000000);
		Render2D.gradientV(ctx, x, y, w, h, 11f, 0xEB141B36, 0xEB0B1029);
		Render2D.outline(ctx, x, y, w, h, 11f, 1f, 0x2EFFFFFF, 0x2EFFFFFF, 0x14FFFFFF, 0x14FFFFFF);
	}

	/** A slot or framed area: a faint tile. */
	public static void well(DrawContext ctx, float x, float y, float w, float h, float radius) {
		Render2D.round(ctx, x, y, w, h, radius, 0x24FFFFFF);
		Render2D.outline(ctx, x, y, w, h, radius, 1f, 0x14FFFFFF);
	}

	/** The slot under the pointer, at a strength 0..1: a white ring with a soft glow. */
	public static void slotHighlight(DrawContext ctx, float x, float y, float strength) {
		Render2D.shadow(ctx, x - 1f, y - 1f, 18f, 18f, 4f, 6f, ColorUtil.withAlpha(0xFFFFFF, Math.round(110 * strength)));
		Render2D.round(ctx, x - 1f, y - 1f, 18f, 18f, 4f, ColorUtil.withAlpha(0xFFFFFF, Math.round(46 * strength)));
		Render2D.outline(ctx, x - 1f, y - 1f, 18f, 18f, 4f, 1.4f, ColorUtil.withAlpha(0xFFFFFF, Math.round(240 * strength)));
	}

	/** The hotbar's bar. */
	public static void bar(DrawContext ctx, float x, float y, float w, float h) {
		Render2D.shadow(ctx, x, y + 2f, w, h, 9f, 12f, 0x66000000);
		Render2D.round(ctx, x, y, w, h, 9f, 0xCC0E1430);
		Render2D.outline(ctx, x, y, w, h, 9f, 1f, 0x2EFFFFFF);
	}

	/** The hotbar's selected slot: ringed in white like a focused tile. */
	public static void selection(DrawContext ctx, float x, float y) {
		Render2D.glow(ctx, x - 1f, y - 1f, 20f, 20f, 6f, 7f, GLOW, ColorUtil.withAlpha(BLUE, 120));
		Render2D.round(ctx, x, y, 18f, 18f, 5f, 0x33FFFFFF);
		Render2D.outline(ctx, x - 1f, y - 1f, 20f, 20f, 6f, 1.5f, 0xF2FFFFFF);
	}

	private static void label(DrawContext ctx, String text, float cx, float cy, float maxWidth, float size, int color) {
		if (text == null || text.isEmpty()) return;
		SdfFont font = Fonts.medium();
		float width = font.width(text, size);
		if (width > maxWidth && width > 0f) size = Math.max(5.5f, size * maxWidth / width);
		Render2D.textCentered(ctx, font, text, cx, Render2D.middle(font, cy, size), size, color);
	}
}
