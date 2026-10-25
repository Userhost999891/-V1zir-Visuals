package dev.v1zir.visuals.gui.custom;

import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.impl.client.CustomUiModule;
import dev.v1zir.visuals.module.impl.client.ThemeModule;
import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.font.SdfFont;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * Drawing of the restyled vanilla widgets (Custom UI module). Every surface follows the Theme
 * style; hover only lights the widget up, nothing moves.
 */
public final class CustomUi {
	private CustomUi() {
	}

	private static CustomUiModule module() {
		CustomUiModule module = ModuleManager.get(CustomUiModule.class);
		return module != null && module.isEnabled() ? module : null;
	}

	public static boolean buttons() {
		CustomUiModule m = module();
		return m != null && m.buttons.on();
	}

	public static boolean sliders() {
		CustomUiModule m = module();
		return m != null && m.sliders.on();
	}

	public static boolean textFields() {
		CustomUiModule m = module();
		return m != null && m.textFields.on();
	}

	public static boolean inventory() {
		CustomUiModule m = module();
		return m != null && m.inventory.on();
	}

	public static boolean hotbar() {
		CustomUiModule m = module();
		return m != null && m.hotbar.on();
	}

	public static boolean tooltips() {
		CustomUiModule m = module();
		return m != null && m.tooltips.on();
	}

	private static void applyTheme() {
		ThemeModule theme = ModuleManager.get(ThemeModule.class);
		if (theme != null) theme.apply();
	}

	/** Rounded button with a centred SDF label. */
	public static void button(DrawContext ctx, int x, int y, int w, int h, Text message, boolean hovered, boolean active) {
		applyTheme();
		float r = Math.min(6f, h / 2f);
		int accent = Theme.accent();
		Render2D.shadow(ctx, x, y + 1f, w, h, r, 6f, 0x3A000000);
		Render2D.panel(ctx, x, y, w, h, r);
		if (hovered && active) {
			Render2D.round(ctx, x, y, w, h, r, ColorUtil.withAlpha(accent, 34));
			Render2D.outline(ctx, x, y, w, h, r, 1f, ColorUtil.withAlpha(accent, 210));
		}
		int color = !active ? Theme.textFaint() : hovered ? 0xFFFFFFFF : Theme.text();
		label(ctx, message, x + w / 2f, y + h / 2f, w - 8f, color);
	}

	/** Slider: the filled part shows the value, a slim handle marks it. */
	public static void slider(DrawContext ctx, int x, int y, int w, int h, double value, Text message, boolean hovered, boolean active) {
		applyTheme();
		float r = Math.min(6f, h / 2f);
		int accent = Theme.accent();
		int accent2 = Theme.accent2();
		float v = (float) Math.max(0.0, Math.min(1.0, value));
		Render2D.shadow(ctx, x, y + 1f, w, h, r, 6f, 0x3A000000);
		Render2D.panel(ctx, x, y, w, h, r);
		float fill = Math.max(r * 2f, w * v);
		Render2D.gradientH(ctx, x, y, fill, h, r, ColorUtil.withAlpha(accent, 70), ColorUtil.withAlpha(accent2, 70));
		if (hovered && active) Render2D.outline(ctx, x, y, w, h, r, 1f, ColorUtil.withAlpha(accent, 210));
		float hx = Math.max(x + 3f, Math.min(x + w - 5f, x + w * v - 1f));
		Render2D.round(ctx, hx, y + 3f, 2.5f, h - 6f, 1.25f, active ? 0xF2FFFFFF : 0x80FFFFFF);
		int color = !active ? Theme.textFaint() : hovered ? 0xFFFFFFFF : Theme.text();
		label(ctx, message, x + w / 2f, y + h / 2f, w - 8f, color);
	}

	/** Background of a text field; vanilla still draws the text, selection and cursor on top. */
	public static void textField(DrawContext ctx, int x, int y, int w, int h, boolean focused, boolean hovered, boolean active) {
		applyTheme();
		float r = Math.min(5f, h / 2f);
		int accent = Theme.accent();
		if (focused) Render2D.shadow(ctx, x, y, w, h, r, 7f, ColorUtil.withAlpha(accent, 70));
		Render2D.round(ctx, x, y, w, h, r, ColorUtil.withAlpha(0x000000, active ? 125 : 80));
		// The border is brightest along the top, like light catching the lip of an inset field.
		Render2D.outline(ctx, x, y, w, h, r, 1f, focused ? ColorUtil.withAlpha(accent, 235)
			: hovered ? ColorUtil.withAlpha(0xFFFFFF, 70) : ColorUtil.withAlpha(0xFFFFFF, 26),
			focused ? ColorUtil.withAlpha(Theme.accent2(), 200) : hovered ? ColorUtil.withAlpha(0xFFFFFF, 45) : ColorUtil.withAlpha(0xFFFFFF, 16),
			ColorUtil.withAlpha(0xFFFFFF, focused ? 40 : 12), ColorUtil.withAlpha(0xFFFFFF, focused ? 60 : 20));
	}

	/** Tooltip background (x, y, w, h are the text area, as vanilla passes them). */
	public static void tooltip(DrawContext ctx, int x, int y, int w, int h) {
		applyTheme();
		float pad = 4f;
		float bx = x - pad;
		float by = y - pad;
		float bw = w + pad * 2f;
		float bh = h + pad * 2f;
		Render2D.shadow(ctx, bx, by + 1.5f, bw, bh, 5f, 10f, 0x60000000);
		Render2D.panel(ctx, bx, by, bw, bh, 5f);
		Render2D.outline(ctx, bx, by, bw, bh, 5f, 1f,
			ColorUtil.withAlpha(Theme.accent(), 150), ColorUtil.withAlpha(Theme.accent2(), 90),
			ColorUtil.withAlpha(0xFFFFFF, 20), ColorUtil.withAlpha(0xFFFFFF, 30));
	}

	/** Container panel behind an inventory screen. */
	public static void containerPanel(DrawContext ctx, int x, int y, int w, int h) {
		applyTheme();
		Render2D.shadow(ctx, x, y + 3f, w, h, 8f, 18f, 0x70000000);
		Render2D.panel(ctx, x, y, w, h, 8f);
	}

	/** One 18x18 inventory slot frame; (x, y) is the item position (slot interior). */
	public static void slot(DrawContext ctx, int x, int y) {
		well(ctx, x - 1f, y - 1f, 18f, 18f, 4f);
	}

	/**
	 * A recessed well (slots, the player preview): darkest under the top lip, whose edge is in shadow,
	 * while the bottom edge catches the light.
	 */
	public static void well(DrawContext ctx, float x, float y, float w, float h, float radius) {
		Render2D.gradientV(ctx, x, y, w, h, radius, 0x7A000000, 0x4C000000);
		Render2D.outline(ctx, x, y, w, h, radius, 1f, 0x42000000, 0x42000000, 0x1EFFFFFF, 0x1EFFFFFF);
	}

	/** Hover highlight of a slot at the given strength (0..1); it only lights up, nothing moves. */
	public static void slotHighlight(DrawContext ctx, float x, float y, float strength) {
		int accent = Theme.accent();
		int accent2 = Theme.accent2();
		Render2D.shadow(ctx, x - 1f, y - 1f, 18f, 18f, 4f, 5f, ColorUtil.withAlpha(accent, Math.round(80 * strength)));
		Render2D.gradientV(ctx, x - 1f, y - 1f, 18f, 18f, 4f, ColorUtil.withAlpha(accent, Math.round(70 * strength)),
			ColorUtil.withAlpha(accent2, Math.round(40 * strength)));
		Render2D.outline(ctx, x - 1f, y - 1f, 18f, 18f, 4f, 1f, ColorUtil.withAlpha(accent, Math.round(230 * strength)),
			ColorUtil.withAlpha(accent, Math.round(230 * strength)), ColorUtil.withAlpha(accent2, Math.round(150 * strength)),
			ColorUtil.withAlpha(accent2, Math.round(150 * strength)));
	}

	/** A framed area (player preview, result box...). */
	public static void frame(DrawContext ctx, float x, float y, float w, float h) {
		well(ctx, x, y, w, h, 5f);
	}

	/**
	 * Strings of recently drawn widget messages, by identity: widgets keep their Text between frames, and
	 * flattening it again for every widget every frame is what vanilla's own buttons avoid too.
	 */
	private static final Text[] LABEL_KEYS = new Text[64];
	private static final String[] LABEL_TEXTS = new String[64];

	private static String string(Text message) {
		int slot = System.identityHashCode(message) & (LABEL_KEYS.length - 1);
		if (LABEL_KEYS[slot] != message) {
			LABEL_KEYS[slot] = message;
			LABEL_TEXTS[slot] = message.getString();
		}
		return LABEL_TEXTS[slot];
	}

	private static void label(DrawContext ctx, Text message, float cx, float cy, float maxWidth, int color) {
		if (message == null) return;
		String text = string(message);
		if (text.isEmpty()) return;
		SdfFont font = Fonts.medium();
		float size = 8f;
		float width = font.width(text, size);
		if (width > maxWidth && width > 0f) size = Math.max(5.5f, size * maxWidth / width);
		Render2D.textCentered(ctx, font, text, cx, Render2D.middle(font, cy, size), size, color);
	}
}
