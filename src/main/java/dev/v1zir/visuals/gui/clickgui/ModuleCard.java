package dev.v1zir.visuals.gui.clickgui;

import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.font.Icons;
import dev.v1zir.visuals.render.font.SdfFont;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.KeyNames;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

/**
 * One module as a card of the grid: name, a one-line description, a toggle switch and a faint key chip.
 * An enabled card simply lights up (no enable animation); hovering brightens it and lifts it by a pixel.
 */
final class ModuleCard {
	private static final float NAME = 7.5f;

	final Module module;
	/** Staggered appearance when the grid is shown. */
	final Anim appear = new Anim(0f, 13f);
	private final Anim hover = new Anim(0f, 22f);
	private final Anim press = new Anim(0f, 30f);
	private final Label nameWidth = new Label();
	private final Label keyWidth = new Label();
	private final boolean hasSettings;
	private boolean binding;
	private boolean pressed;
	private boolean hovered;
	// Layout of the last frame, for hit testing.
	private float x;
	private float y;
	private float w;
	/** Description cut to the card width, rebuilt only when the width changes. */
	private String shownDescription = "";
	private float descriptionWidth = -1f;
	private int shownKey = GLFW.GLFW_KEY_UNKNOWN;
	private String keyName = "";

	ModuleCard(Module module) {
		this.module = module;
		this.hasSettings = !module.settings().isEmpty();
	}

	boolean isBinding() {
		return binding;
	}

	boolean isHovered() {
		return hovered;
	}

	/** Draws the card at (x, y); mouse coordinates outside the scroll viewport arrive as -1000. */
	void render(DrawContext ctx, float x, float y, float w, double mx, double my, boolean drawerOpen) {
		this.x = x;
		this.y = y;
		this.w = w;
		hovered = Ui.inside(mx, my, x, y, w, Ui.CARD_HEIGHT);
		float h = hover.target(hovered ? 1f : 0f).get();
		float p = press.target(pressed && hovered ? 1f : 0f).get();
		boolean on = module.isEnabled();
		int accent = Theme.accent();
		float lift = h * 1f;
		float cy = y - lift;
		float ch = Ui.CARD_HEIGHT;
		float r = Ui.CARD_RADIUS;

		boolean scaled = p > 0.002f;
		if (scaled) {
			// Press feedback: the card sinks back a little under the cursor.
			float s = 1f - 0.025f * p;
			ctx.getMatrices().pushMatrix();
			ctx.getMatrices().translate(x + w / 2f, cy + ch / 2f);
			ctx.getMatrices().scale(s, s);
			ctx.getMatrices().translate(-(x + w / 2f), -(cy + ch / 2f));
		}
		if (h > 0.01f) Render2D.shadow(ctx, x, cy + 2f, w, ch, r, 9f, ColorUtil.withAlpha(0x000000, Math.round(70 * h)));
		if (on) Render2D.shadow(ctx, x, cy, w, ch, r, 10f, ColorUtil.withAlpha(accent, Math.round(42 + 22 * h)));
		int fill = on ? ColorUtil.brighten(Theme.raisedLit(), 0.04f * h) : ColorUtil.lerp(Theme.raised(), Theme.raisedLit(), h * 0.7f);
		Render2D.matte(ctx, x, cy, w, ch, r, fill, Ui.LIT);
		int rim = binding ? ColorUtil.withAlpha(accent, 230)
			: on ? ColorUtil.withAlpha(ColorUtil.lerp(accent, 0xFFFFFFFF, 0.25f), Math.round(120 + 50 * h))
			: ColorUtil.withAlpha(0xFFFFFF, Math.round(20 + 26 * h));
		Render2D.rim(ctx, x, cy, w, ch, r, rim, Ui.LIT);
		if (on) {
			// A short accent line on the left edge marks enabled modules at a glance.
			Render2D.shadow(ctx, x + 0.5f, cy + 11f, 2f, ch - 22f, 1f, 4f, ColorUtil.withAlpha(accent, 140));
			Render2D.gradientV(ctx, x + 0.5f, cy + 11f, 2f, ch - 22f, 1f, accent, Theme.accent2());
		}

		float left = x + 10f;
		float right = x + w - 10f;
		float top = cy + 12f;
		if (module.canToggle()) Ui.toggle(ctx, right - Ui.SWITCH_W, top - Ui.SWITCH_H / 2f, on ? 1f : 0f, h);
		else Render2D.icon(ctx, Icons.SLIDERS_HORIZONTAL, right - 4f, top, 7.5f, ColorUtil.lerp(Theme.textDim(), Theme.text(), h));

		SdfFont font = Fonts.semibold();
		String label = binding ? "Press a key..." : module.name();
		int nameColor = binding ? ColorUtil.lerp(accent, 0xFFFFFFFF, 0.3f) : on ? 0xFFFFFFFF : ColorUtil.lerp(Theme.text(), 0xFFFFFFFF, h);
		Render2D.textMiddle(ctx, font, label, left, top, NAME, nameColor);
		if (!binding && module.key() != GLFW.GLFW_KEY_UNKNOWN) {
			if (module.key() != shownKey) {
				shownKey = module.key();
				keyName = KeyNames.name(shownKey);
			}
			Ui.keyChip(ctx, keyWidth, keyName, left + nameWidth.width(font, label, NAME) + 5f, top,
				on ? 0xB4FFFFFF : ColorUtil.withAlpha(Theme.textDim(), 200));
		}

		float descY = cy + 26f;
		float maxDesc = w - 20f - (hasSettings ? 10f : 0f);
		if (maxDesc != descriptionWidth) {
			descriptionWidth = maxDesc;
			shownDescription = Fonts.regular().trim(module.description(), Ui.TINY, maxDesc);
		}
		Render2D.textMiddle(ctx, Fonts.regular(), shownDescription, left, descY, Ui.TINY,
			ColorUtil.lerp(Theme.textDim(), Theme.text(), on ? 0.35f + 0.3f * h : 0.3f * h));
		if (hasSettings) {
			int chevron = drawerOpen ? ColorUtil.lerp(accent, 0xFFFFFFFF, 0.25f) : ColorUtil.withAlpha(Theme.text(), Math.round(70 + 140 * h));
			Render2D.icon(ctx, Icons.CHEVRON_RIGHT, right - 2f + 1.5f * h, descY, 6.5f, chevron);
		}
		if (scaled) ctx.getMatrices().popMatrix();
	}

	/** Not drawn this frame (scrolled away): no hit area, no hover. */
	void hide() {
		y = -10000f;
		hovered = false;
	}

	/** Hit test against the last frame's layout. */
	boolean over(double mx, double my) {
		return Ui.inside(mx, my, x, y - 1f, w, Ui.CARD_HEIGHT + 1f);
	}

	void press() {
		pressed = true;
	}

	/** Mouse released anywhere; returns whether it ended a press on this card (a click). */
	boolean release(double mx, double my) {
		boolean click = pressed && over(mx, my);
		pressed = false;
		return click;
	}

	boolean hasSettings() {
		return hasSettings;
	}

	void toggleBinding() {
		binding = !binding;
	}

	/** Returns true when the key was consumed by bind mode. */
	boolean keyPressed(int key) {
		if (!binding) return false;
		if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) module.setKey(GLFW.GLFW_KEY_UNKNOWN);
		else module.setKey(key);
		binding = false;
		return true;
	}

	void cancelBinding() {
		binding = false;
	}
}
