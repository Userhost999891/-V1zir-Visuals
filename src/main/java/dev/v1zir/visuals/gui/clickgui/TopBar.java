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
	private static final String BRAND = "V1zir Visuals";

	private final String version = V1zir.version();
	private final Anim searchFocus = new Anim(0f, 18f);
	private final Anim searchHover = new Anim(0f, 20f);
	private final Anim hudHover = new Anim(0f, 22f);
	private final Anim hudPress = new Anim(0f, 30f);
	private final Label brandWidth = new Label();
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

	TopBar() {
		Logo.refresh();
	}

	void render(DrawContext ctx, float x, float y, float w, double mx, double my, String search, boolean focused) {
		float mid = y + Ui.BAR / 2f;
		float lx = x + 12f;
		Logo.draw(ctx, lx, mid - LOGO_SIZE / 2f, LOGO_SIZE);
		// Product name in the interface face, the version beside it as plain secondary text.
		SdfFont brand = Fonts.semibold();
		float tx = lx + LOGO_SIZE + 8f;
		Render2D.textMiddle(ctx, brand, BRAND, tx, mid, 8.5f, Theme.text());
		Render2D.textMiddle(ctx, Fonts.regular(), version, tx + brandWidth.width(brand, BRAND, 8.5f) + 8f, mid, Ui.TINY, Theme.textFaint());

		searchW = Math.max(112f, Math.min(168f, w * 0.27f));
		searchX = x + w - 12f - searchW;
		searchY = mid - SEARCH_H / 2f;
		renderSearch(ctx, mx, my, search, focused);

		hudX = searchX - 8f - BUTTON;
		hudY = mid - BUTTON / 2f;
		float hh = hudHover.target(Ui.inside(mx, my, hudX, hudY, BUTTON, BUTTON) ? 1f : 0f).get();
		float hp = hudPress.target(hudPressed ? 1f : 0f).get();
		Ui.iconButton(ctx, hudX, hudY, BUTTON, Icons.MOVE, hh, hp);

		Render2D.rect(ctx, x, y + Ui.BAR - 1f, w, 1f, Ui.HAIRLINE);
	}

	private void renderSearch(DrawContext ctx, double mx, double my, String search, boolean focused) {
		SdfFont font = Fonts.medium();
		float focus = searchFocus.target(focused ? 1f : 0f).get();
		float hv = searchHover.target(Ui.inside(mx, my, searchX, searchY, searchW, SEARCH_H) ? 1f : 0f).get();
		float r = Ui.CELL_RADIUS + 2f;
		int accent = Theme.accent();
		Render2D.matte(ctx, searchX, searchY, searchW, SEARCH_H, r, ColorUtil.withAlpha(0x000000, Math.round(40 + 16 * focus)), Ui.LIT);
		Render2D.rim(ctx, searchX, searchY, searchW, SEARCH_H, r,
			ColorUtil.lerp(ColorUtil.withAlpha(0xFFFFFF, Math.round(20 + 16 * hv)), ColorUtil.withAlpha(accent, 190), focus), Ui.LIT);
		float mid = searchY + SEARCH_H / 2f;
		Render2D.icon(ctx, Icons.SEARCH, searchX + 10f, mid, 7f, ColorUtil.lerp(Theme.textDim(), Theme.text(), focus));
		float tx = searchX + 20f;
		float size = 6.5f;
		if (search.isEmpty() && !focused) {
			Render2D.textMiddle(ctx, font, PLACEHOLDER, tx, mid, size, Theme.textFaint());
			float sw = shortcutWidth.width(Fonts.medium(), SHORTCUT, 5.5f);
			Render2D.textMiddle(ctx, Fonts.medium(), SHORTCUT, searchX + searchW - 8f - sw, mid, 5.5f, Theme.textFaint());
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
