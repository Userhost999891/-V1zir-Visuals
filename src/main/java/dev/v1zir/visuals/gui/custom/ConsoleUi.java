package dev.v1zir.visuals.gui.custom;

import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.font.SdfFont;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.gui.DrawContext;

/**
 * The game's widgets in the PS5 style theme, as the menus of the style's home screen draw theirs: frosted pills, a dark
 * base under a light veil so white text reads on any background, white text; what the pointer is on turns lighter, with
 * a white rim and a soft light around it. Sliders show their value as a thin line in the accent with a white knob under
 * the label, text fields get a ring in the accent when typed into, slots light up with a white ring. The colours are
 * the theme's (its base and accent): the style changes the shapes, not the colours. CustomUi draws through these while
 * the style is on.
 */
public final class ConsoleUi {
	/** The veils, edge and text on the surfaces (white, so the theme's colours show through), and dark text on white. */
	public static final int VEIL = 0x2EFFFFFF;
	public static final int VEIL_HOT = 0x6BFFFFFF;
	public static final int EDGE = 0x1FFFFFFF;
	public static final int TEXT = 0xF7FFFFFF;
	public static final int TEXT_DIM = 0x73FFFFFF;
	public static final int FOCUS = 0xFFF4F6FB;
	public static final int INK = 0xFF120F1E;
	public static final int GLOW = 0x8CFFFFFF;

	/** A surface in the theme's base colour, a little lighter, at the given opacity (0 to 255). */
	private static int surface(int alpha, float lighter) {
		return ColorUtil.withAlpha(ColorUtil.brighten(Theme.preset().base(), lighter), alpha);
	}

	private ConsoleUi() {
	}

	/** A frosted pill button; under the pointer it turns lighter, with a white rim and a soft light all around it. */
	public static void button(DrawContext ctx, float x, float y, float w, float h, String label, boolean focused, boolean active) {
		float r = Math.min(h / 2f, 10f);
		boolean lit = focused && active;
		pill(ctx, x, y, w, h, r, lit, active);
		label(ctx, label, x + w / 2f, y + h / 2f, w - 10f, 8f, lit ? INK : active ? TEXT : TEXT_DIM);
	}

	/**
	 * The pill under a button or a slider: a dark base so white text reads on anything, a light veil over it. In focus
	 * (under the pointer, or picked with the keys or a controller) it turns white with a soft light around it, as a
	 * console marks what is in focus; the words on it turn dark.
	 */
	private static void pill(DrawContext ctx, float x, float y, float w, float h, float r, boolean lit, boolean active) {
		Render2D.shadow(ctx, x, y + 1.5f, w, h, r, 7f, 0x4D000000);
		if (lit) {
			Render2D.glow(ctx, x, y, w, h, r, 8f, 0x40FFFFFF, 0x40FFFFFF);
			Render2D.round(ctx, x, y, w, h, r, FOCUS);
			return;
		}
		Render2D.round(ctx, x, y, w, h, r, surface(active ? 0xB3 : 0x73, 0.06f));
		if (active) Render2D.gradientV(ctx, x, y, w, h, r, VEIL, 0x1AFFFFFF);
		Render2D.outline(ctx, x, y, w, h, r, 1f, EDGE);
	}

	/** A slider: the pill with the label a little above its middle and a thin line under it, lavender up to a white knob. */
	public static void slider(DrawContext ctx, float x, float y, float w, float h, double value, String label, boolean focused, boolean active) {
		float r = Math.min(h / 2f, 10f);
		float v = (float) Math.max(0.0, Math.min(1.0, value));
		boolean lit = focused && active;
		pill(ctx, x, y, w, h, r, lit, active);
		float inset = Math.max(8f, r);
		float trackY = y + h - Math.max(4f, h * 0.2f);
		float trackW = w - inset * 2f;
		float th = Math.max(1.5f, h * 0.08f);
		Render2D.round(ctx, x + inset, trackY - th / 2f, trackW, th, th / 2f, lit ? 0x33000000 : 0x33FFFFFF);
		float fill = trackW * v;
		int accent = Theme.accent();
		if (fill > 0.5f) Render2D.round(ctx, x + inset, trackY - th / 2f, fill, th, th / 2f, active ? accent : ColorUtil.fade(accent, 0.5f));
		float kw = Math.max(6f, h * 0.36f), kh = Math.max(3.5f, h * 0.2f);
		float kx = x + inset + fill;
		Render2D.round(ctx, kx - kw / 2f, trackY - kh / 2f, kw, kh, kh / 2f, lit ? INK : active ? 0xFFFFFFFF : 0x99FFFFFF);
		label(ctx, label, x + w / 2f, y + h * 0.42f, w - 14f, 7.5f, lit ? INK : active ? TEXT : TEXT_DIM);
	}

	/** Behind a text field (the game draws its text and cursor on top): a white ring while it is typed into. */
	public static void textField(DrawContext ctx, float x, float y, float w, float h, boolean focused, boolean hovered, boolean active) {
		float r = Math.min(h / 2f, 7f);
		int accent = Theme.accent();
		if (focused) Render2D.shadow(ctx, x, y, w, h, r, 8f, ColorUtil.withAlpha(accent, 110));
		Render2D.round(ctx, x, y, w, h, r, surface(active ? 0xD9 : 0x80, 0.03f));
		Render2D.outline(ctx, x, y, w, h, r, focused ? 1.5f : 1f, focused ? accent : hovered ? 0x55FFFFFF : EDGE);
	}

	/** A tooltip around the text area (x, y, w, h as the game passes it). */
	public static void tooltip(DrawContext ctx, float x, float y, float w, float h) {
		float pad = 4f;
		Render2D.shadow(ctx, x - pad, y - pad + 2f, w + pad * 2f, h + pad * 2f, 7f, 12f, 0x70000000);
		Render2D.round(ctx, x - pad, y - pad, w + pad * 2f, h + pad * 2f, 7f, surface(0xF2, 0.05f));
		Render2D.outline(ctx, x - pad, y - pad, w + pad * 2f, h + pad * 2f, 7f, 1f, 0x2EFFFFFF);
	}

	/** The panel behind an inventory or container. */
	public static void panel(DrawContext ctx, float x, float y, float w, float h) {
		Render2D.shadow(ctx, x, y + 4f, w, h, 11f, 20f, 0x80000000);
		Render2D.gradientV(ctx, x, y, w, h, 11f, surface(0xEB, 0.09f), surface(0xEB, 0.02f));
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
		Render2D.round(ctx, x, y, w, h, 9f, surface(0xCC, 0.04f));
		Render2D.outline(ctx, x, y, w, h, 9f, 1f, 0x2EFFFFFF);
	}

	/** The hotbar's selected slot: ringed in white like a focused tile. */
	public static void selection(DrawContext ctx, float x, float y) {
		Render2D.glow(ctx, x - 1f, y - 1f, 20f, 20f, 6f, 7f, GLOW, ColorUtil.withAlpha(Theme.accent(), 120));
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
