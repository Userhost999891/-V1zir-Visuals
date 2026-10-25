package dev.v1zir.visuals.gui.clickgui;

import dev.v1zir.visuals.V1zir;
import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.font.Icons;
import dev.v1zir.visuals.render.font.SdfFont;
import dev.v1zir.visuals.render.ui.Logo;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.gui.DrawContext;

/**
 * Top bar of the window: the logo, "V1zir Visuals" with a version tag, the HUD editor button and the
 * search field. A hairline fading out towards both ends separates it from the content.
 * The logo is the shared brand mark (render/ui/Logo), drawn at 20x20 GUI pixels.
 */
final class TopBar {
	private static final float LOGO_SIZE = 20f;
	private static final float BUTTON = 20f;
	private static final float SEARCH_H = 20f;
	private static final String PLACEHOLDER = "Search modules";
	private static final String SHORTCUT = "Ctrl F";
	private static final Render2D.TextStyle BRAND = new Render2D.TextStyle().weight(0.03f);

	private final String version = V1zir.version();

	TopBar() {
		Logo.refresh();
	}
	private final Anim searchFocus = new Anim(0f, 18f);
	private final Anim searchHover = new Anim(0f, 20f);
	private final Anim hudHover = new Anim(0f, 22f);
	private final Anim hudPress = new Anim(0f, 30f);
	private final Label brandWidth = new Label();
	private final Label visualsWidth = new Label();
	private final Label versionWidth = new Label();
	private final Label caretWidth = new Label();
	private final Label shortcutWidth = new Label();
	private String shownSearch = "";
	private String shownSearchFor = "";
	private boolean hudPressed;
	// Layout of the last frame.
	private float searchX;
	private float searchY;
	private float searchW;
	private float hudX;
	private float hudY;

	void render(DrawContext ctx, float x, float y, float w, double mx, double my, String search, boolean focused) {
		float mid = y + Ui.BAR / 2f;
		int accent = Theme.accent();
		int accent2 = Theme.accent2();

		float lx = x + 12f;
		float ly = mid - LOGO_SIZE / 2f;
		Logo.draw(ctx, lx, ly, LOGO_SIZE);

		SdfFont brand = Fonts.title();
		float size = 10f;
		float tx = lx + LOGO_SIZE + 8f;
		float ty = Render2D.middle(brand, mid, size);
		BRAND.color = ColorUtil.lerp(accent, 0xFFFFFFFF, 0.15f);
		BRAND.colorEnd = ColorUtil.lerp(accent2, 0xFFFFFFFF, 0.15f);
		Render2D.text(ctx, brand, "V1zir", tx, ty, size, BRAND);
		float vx = tx + brandWidth.width(brand, "V1zir", size) + 3.5f;
		Render2D.text(ctx, brand, "Visuals", vx, ty, size, Theme.text());
		SdfFont small = Fonts.semibold();
		float tagX = vx + visualsWidth.width(brand, "Visuals", size) + 6f;
		float tagW = versionWidth.width(small, version, 5.5f) + 8f;
		Render2D.matte(ctx, tagX, mid - 5f, tagW, 10f, 5f, ColorUtil.withAlpha(accent, 34), 0);
		Render2D.rim(ctx, tagX, mid - 5f, tagW, 10f, 5f, ColorUtil.withAlpha(accent, 80), 0);
		Render2D.textMiddle(ctx, small, version, tagX + 4f, mid, 5.5f, ColorUtil.lerp(accent, 0xFFFFFFFF, 0.45f));

		searchW = Math.max(110f, Math.min(170f, w * 0.27f));
		searchX = x + w - 10f - searchW;
		searchY = mid - SEARCH_H / 2f;
		renderSearch(ctx, mx, my, search, focused);

		hudX = searchX - 6f - BUTTON;
		hudY = mid - BUTTON / 2f;
		float hh = hudHover.target(Ui.inside(mx, my, hudX, hudY, BUTTON, BUTTON) ? 1f : 0f).get();
		float hp = hudPress.target(hudPressed ? 1f : 0f).get();
		Ui.iconButton(ctx, hudX, hudY, BUTTON, Icons.MOVE, hh, hp, false);

		// Separator: brightest in the middle, fading out towards both ends.
		float sy = y + Ui.BAR - 0.5f;
		float half = w / 2f - 12f;
		Render2D.gradientH(ctx, x + 12f, sy, half, 1f, 0.5f, 0x00FFFFFF, 0x1EFFFFFF);
		Render2D.gradientH(ctx, x + w / 2f, sy, half, 1f, 0.5f, 0x1EFFFFFF, 0x00FFFFFF);
	}

	private void renderSearch(DrawContext ctx, double mx, double my, String search, boolean focused) {
		SdfFont font = Fonts.medium();
		float focus = searchFocus.target(focused ? 1f : 0f).get();
		float hv = searchHover.target(Ui.inside(mx, my, searchX, searchY, searchW, SEARCH_H) ? 1f : 0f).get();
		float r = SEARCH_H / 2f;
		int accent = Theme.accent();
		if (focus > 0.01f) Render2D.shadow(ctx, searchX, searchY, searchW, SEARCH_H, r, 8f, ColorUtil.withAlpha(accent, Math.round(50 * focus)));
		Render2D.matte(ctx, searchX, searchY, searchW, SEARCH_H, r, ColorUtil.withAlpha(0x000000, Math.round(46 + 20 * focus)), Ui.LIT);
		Render2D.rim(ctx, searchX, searchY, searchW, SEARCH_H, r,
			ColorUtil.lerp(ColorUtil.withAlpha(0xFFFFFF, Math.round(20 + 22 * hv)), ColorUtil.withAlpha(accent, 170), focus), Ui.LIT);
		float mid = searchY + SEARCH_H / 2f;
		Render2D.icon(ctx, Icons.SEARCH, searchX + 10f, mid, 7.5f, ColorUtil.lerp(Theme.textDim(), ColorUtil.lerp(accent, 0xFFFFFFFF, 0.2f), focus));
		float tx = searchX + 18f;
		float size = 6.5f;
		if (search.isEmpty() && !focused) {
			Render2D.textMiddle(ctx, font, PLACEHOLDER, tx, mid, size, Theme.textFaint());
			float sw = shortcutWidth.width(Fonts.semibold(), SHORTCUT, 5f) + 6f;
			float sx = searchX + searchW - 6f - sw;
			Render2D.round(ctx, sx, mid - 4.5f, sw, 9f, 3f, 0x12FFFFFF);
			Render2D.textMiddle(ctx, Fonts.semibold(), SHORTCUT, sx + 3f, mid, 5f, ColorUtil.withAlpha(Theme.textDim(), 190));
			return;
		}
		if (!search.equals(shownSearchFor)) {
			shownSearchFor = search;
			shownSearch = font.trim(search, size, searchW - 28f);
		}
		Render2D.textMiddle(ctx, font, shownSearch, tx, mid, size, Theme.text());
		if (focused) {
			// Caret that breathes instead of blinking.
			float pulse = 0.5f + 0.5f * (float) Math.cos(System.nanoTime() / 1.0e9 * Math.PI * 2.0);
			float caretX = tx + caretWidth.width(font, shownSearch, size) + 1f;
			Render2D.round(ctx, caretX, mid - 3.5f, 1f, 7f, 0.5f, ColorUtil.withAlpha(accent, Math.round(255 * pulse)));
		}
	}

	boolean overSearch(double mx, double my) {
		return Ui.inside(mx, my, searchX, searchY, searchW, SEARCH_H);
	}

	boolean overHudButton(double mx, double my) {
		return Ui.inside(mx, my, hudX, hudY, BUTTON, BUTTON);
	}

	void pressHud(boolean pressed) {
		hudPressed = pressed;
	}
}
