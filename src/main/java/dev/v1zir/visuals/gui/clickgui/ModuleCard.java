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
 * Toggling takes about 0.2 s on a spring with no overshoot: the knob slides, the track and the card's rim
 * and edge line take the accent, and a faint band of light runs across the card from the switch.
 * Hovering brightens the card and lifts it by a pixel.
 */
final class ModuleCard {
	private static final float NAME = 7.5f;
	private static final float PAD = 12f;
	private static final float BAND = 48f;

	final Module module;
	/** Staggered appearance when the grid is shown. */
	final Anim appear = new Anim(0f, 13f);
	private final Anim hover = new Anim(0f, 22f);
	private final Anim press = new Anim(0f, 30f);
	private final Anim enabled;
	private final Anim sweep = new Anim(1f, 11f);
	private final Label nameWidth = new Label();
	private final Label keyWidth = new Label();
	private final boolean hasSettings;
	private boolean shownOn;
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
		this.shownOn = module.isEnabled();
		this.enabled = new Anim(shownOn ? 1f : 0f, 17f);
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
		boolean on = module.isEnabled();
		if (on != shownOn) {
			// Toggled (by a click or a key bind): the light band only runs when the module turns on.
			shownOn = on;
			if (on && module.canToggle()) sweep.snap(0f);
		}
		float h = hover.target(hovered ? 1f : 0f).get();
		float p = press.target(pressed && hovered ? 1f : 0f).get();
		float e = enabled.target(on && module.canToggle() ? 1f : 0f).get();
		float s = sweep.target(1f).get();
		int accent = Theme.accent();
		float cy = y - h;
		float ch = Ui.CARD_HEIGHT;
		float r = Ui.CARD_RADIUS;

		boolean scaled = p > 0.002f;
		if (scaled) {
			// Press feedback: the card sinks back a little under the cursor.
			float k = 1f - 0.02f * p;
			ctx.getMatrices().pushMatrix();
			ctx.getMatrices().translate(x + w / 2f, cy + ch / 2f);
			ctx.getMatrices().scale(k, k);
			ctx.getMatrices().translate(-(x + w / 2f), -(cy + ch / 2f));
		}
		int fill = ColorUtil.lerp(ColorUtil.lerp(Theme.raised(), Theme.raisedLit(), 0.6f * h), ColorUtil.brighten(Theme.raisedLit(), 0.03f * h), e);
		Render2D.matte(ctx, x, cy, w, ch, r, fill, Ui.LIT);
		int idleRim = ColorUtil.withAlpha(0xFFFFFF, Math.round(18 + 18 * h));
		int rim = binding ? ColorUtil.withAlpha(accent, 220) : ColorUtil.lerp(idleRim, ColorUtil.withAlpha(accent, Math.round(110 + 40 * h)), e);
		Render2D.rim(ctx, x, cy, w, ch, r, rim, Ui.LIT);
		if (e > 0.01f) {
			// The edge line grows from its middle as the module turns on.
			float lh = (ch - 24f) * e;
			Render2D.round(ctx, x + 1f, cy + ch / 2f - lh / 2f, 2f, lh, 1f, ColorUtil.fade(accent, e));
		}
		if (s < 0.999f) {
			// A soft band of light runs from the switch across the card, fading as it goes.
			float bx = x + w - (w + BAND) * s;
			int glow = ColorUtil.withAlpha(ColorUtil.lerp(0xFFFFFF, accent, 0.4f), Math.round(26 * (1f - s)));
			ctx.enableScissor((int) Math.floor(x + 1f), (int) Math.floor(cy + 1f), (int) Math.ceil(x + w - 1f), (int) Math.ceil(cy + ch - 1f));
			Render2D.gradientH(ctx, bx, cy, BAND / 2f, ch, 0f, ColorUtil.withAlpha(glow, 0), glow);
			Render2D.gradientH(ctx, bx + BAND / 2f, cy, BAND / 2f, ch, 0f, glow, ColorUtil.withAlpha(glow, 0));
			ctx.disableScissor();
		}

		float left = x + PAD;
		float right = x + w - PAD;
		float top = cy + 14f;
		if (module.canToggle()) Ui.toggle(ctx, right - Ui.SWITCH_W, top - Ui.SWITCH_H / 2f, e, h);
		else Render2D.icon(ctx, Icons.SLIDERS_HORIZONTAL, right - 4f, top, 7f, ColorUtil.lerp(Theme.textDim(), Theme.text(), h));

		SdfFont font = Fonts.semibold();
		String label = binding ? "Press a key..." : module.name();
		int nameColor = binding ? ColorUtil.lerp(accent, 0xFFFFFFFF, 0.3f) : ColorUtil.lerp(Theme.text(), 0xFFFFFFFF, Math.max(e, h));
		Render2D.textMiddle(ctx, font, label, left, top, NAME, nameColor);
		if (!binding && module.key() != GLFW.GLFW_KEY_UNKNOWN) {
			if (module.key() != shownKey) {
				shownKey = module.key();
				keyName = KeyNames.name(shownKey);
			}
			Ui.keyChip(ctx, keyWidth, keyName, left + nameWidth.width(font, label, NAME) + 6f, top, Theme.textDim());
		}

		float descY = cy + 27f;
		float maxDesc = w - PAD * 2f - (hasSettings ? 12f : 0f);
		if (maxDesc != descriptionWidth) {
			descriptionWidth = maxDesc;
			shownDescription = Fonts.regular().trim(module.description(), Ui.TINY, maxDesc);
		}
		Render2D.textMiddle(ctx, Fonts.regular(), shownDescription, left, descY, Ui.TINY, ColorUtil.lerp(Theme.textDim(), Theme.text(), 0.25f * h));
		if (hasSettings) {
			int chevron = drawerOpen ? Theme.text() : ColorUtil.lerp(Theme.textFaint(), Theme.textDim(), h);
			Render2D.icon(ctx, Icons.CHEVRON_RIGHT, right - 3f + h, descY, 6.5f, chevron);
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

	/** The settings chevron at the end of the description line (a left click there opens the settings). */
	boolean overChevron(double mx, double my) {
		return hasSettings && Ui.inside(mx, my, x + w - 24f, y + 18f, 24f, Ui.CARD_HEIGHT - 18f);
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
}
