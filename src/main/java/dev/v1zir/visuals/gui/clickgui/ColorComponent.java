package dev.v1zir.visuals.gui.clickgui;

import dev.v1zir.visuals.module.setting.ColorSetting;
import dev.v1zir.visuals.render.V1zirPipelines;
import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.font.SdfFont;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.render.ui.ShapeElement;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.TextureSetup;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/** Colour swatch that expands into an HSV picker (saturation/value square, hue and alpha bars). */
public final class ColorComponent extends SettingComponent<ColorSetting> {
	private static final float ROW = 15f;
	private static final float SQUARE = 58f;
	private static final float BAR = 6f;
	private static final float CHIP = 11f;
	private static final float SWATCH_W = 16f;
	private static final float SWATCH_H = 9f;
	private static final int[] HUES = {0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF, 0xFF0000FF, 0xFFFF00FF, 0xFFFF0000};

	private final Anim open = new Anim(0f, 14f);
	private final Label rainbowWidth = new Label();
	private final Label hexWidth = new Label();
	private boolean expanded;
	/** Width of the Rainbow chip in the last frame (its hit area). */
	private float rainbowW;
	private int dragging; // 0 none, 1 square, 2 hue, 3 alpha
	private float hue;
	private float sat;
	private float val;
	/** "#RRGGBB" of the stored colour, rebuilt only when it changes. */
	private int shownColor;
	private String hex;

	public ColorComponent(ColorSetting setting) {
		super(setting);
		syncFromSetting();
	}

	private void syncFromSetting() {
		float[] hsv = ColorUtil.toHsv(setting.get());
		hue = hsv[0];
		sat = hsv[1];
		val = hsv[2];
	}

	@Override
	public float estimateHeight() {
		return ROW;
	}

	private float pickerHeight() {
		return SQUARE + 6f + BAR + (setting.hasAlpha() ? 6f + BAR : 0f) + 6f + CHIP + 4f;
	}

	@Override
	protected float draw(DrawContext ctx, double mouseX, double mouseY) {
		float h = hover.target(Ui.inside(mouseX, mouseY, x, y, width, ROW) ? 1f : 0f).get();
		float t = open.target(expanded ? 1f : 0f).get();
		Render2D.textMiddle(ctx, Fonts.regular(), setting.name(), x, y + ROW / 2f, Ui.LABEL, labelColor(Math.max(h, t)));
		int color = setting.argb();
		int opaque = ColorUtil.withAlpha(color, 255);
		float sx = x + width - SWATCH_W;
		float sy = y + (ROW - SWATCH_H) / 2f;
		Render2D.shadow(ctx, sx, sy, SWATCH_W, SWATCH_H, SWATCH_H / 2f, 5f, ColorUtil.withAlpha(color, Math.round(80 + 50 * h)));
		Render2D.round(ctx, sx, sy, SWATCH_W, SWATCH_H, SWATCH_H / 2f, opaque);
		Render2D.outline(ctx, sx, sy, SWATCH_W, SWATCH_H, SWATCH_H / 2f, 1f, ColorUtil.withAlpha(0xFFFFFF, Math.round(50 + 60 * h)));
		if (t <= 0.01f) return ROW;

		float py = y + ROW + 2f;
		float alphaBackup = Render2D.alpha();
		Render2D.setAlpha(alphaBackup * t);
		// Saturation / value square: white -> hue horizontally, transparent -> black vertically.
		int hueColor = ColorUtil.fromHsv(hue, 1f, 1f, 255);
		Render2D.round(ctx, x, py, width, SQUARE, 5f, 0xFFFFFFFF, hueColor, hueColor, 0xFFFFFFFF);
		Render2D.round(ctx, x, py, width, SQUARE, 5f, 0x00000000, 0x00000000, 0xFF000000, 0xFF000000);
		Render2D.outline(ctx, x, py, width, SQUARE, 5f, 1f, ColorUtil.withAlpha(0xFFFFFF, 30));
		knob(ctx, x + sat * width, py + (1f - val) * SQUARE, opaque);

		float by = py + SQUARE + 6f;
		float seg = width / (HUES.length - 1);
		for (int i = 0; i < HUES.length - 1; i++) {
			// The end segments keep their outer corners round; inner joints stay square so the bar is seamless.
			int corners = i == 0 ? Render2D.CORNERS_LEFT : i == HUES.length - 2 ? Render2D.CORNERS_RIGHT : 0;
			float r = corners == 0 ? 0f : BAR / 2f;
			Render2D.shape(ctx, V1zirPipelines.SHAPE, TextureSetup.empty(), x + seg * i, by, seg + (i < HUES.length - 2 ? 0.5f : 0f), BAR,
				r, 0, 0, ShapeElement.FILL, corners, HUES[i], HUES[i + 1], HUES[i + 1], HUES[i]);
		}
		Render2D.outline(ctx, x, by, width, BAR, BAR / 2f, 1f, ColorUtil.withAlpha(0xFFFFFF, 30));
		knob(ctx, x + hue * width, by + BAR / 2f, hueColor);
		float next = by + BAR + 6f;
		if (setting.hasAlpha()) {
			Render2D.gradientH(ctx, x, next, width, BAR, BAR / 2f, ColorUtil.withAlpha(opaque, 0), opaque);
			Render2D.outline(ctx, x, next, width, BAR, BAR / 2f, 1f, ColorUtil.withAlpha(0xFFFFFF, 30));
			knob(ctx, x + ColorUtil.alpha(setting.get()) / 255f * width, next + BAR / 2f, color);
			next += BAR + 6f;
		}

		boolean rainbow = setting.rainbow();
		SdfFont font = Fonts.medium();
		float chipW = rainbowWidth.width(font, "Rainbow", Ui.SMALL) + 10f;
		rainbowW = chipW;
		if (rainbow) {
			Render2D.shadow(ctx, x, next, chipW, CHIP, 4f, 5f, ColorUtil.withAlpha(Theme.accent(), 80));
			Render2D.gradientH(ctx, x, next, chipW, CHIP, 4f, Theme.accent(), Theme.accent2());
		} else {
			Render2D.matte(ctx, x, next, chipW, CHIP, 4f, ColorUtil.withAlpha(0xFFFFFF, 10), Ui.LIT);
			Render2D.rim(ctx, x, next, chipW, CHIP, 4f, ColorUtil.withAlpha(0xFFFFFF, 16), Ui.LIT);
		}
		Render2D.textMiddle(ctx, font, "Rainbow", x + 5f, next + CHIP / 2f, Ui.SMALL, rainbow ? 0xFFFFFFFF : Theme.textDim());
		int rgb = setting.get() & 0xFFFFFF;
		if (hex == null || rgb != shownColor) {
			shownColor = rgb;
			hex = String.format(Locale.ROOT, "#%06X", rgb);
		}
		float hw = hexWidth.width(font, hex, Ui.SMALL);
		Render2D.textMiddle(ctx, font, hex, x + width - hw, next + CHIP / 2f, Ui.SMALL, Theme.textFaint());
		Render2D.setAlpha(alphaBackup);
		return ROW + 2f + pickerHeight() * t;
	}

	/** Picker handle: white ring with a dark rim so it reads on light and dark colours alike. */
	private static void knob(DrawContext ctx, float cx, float cy, int fill) {
		Render2D.shadow(ctx, cx - 3.5f, cy - 3.5f + 0.5f, 7f, 7f, 3.5f, 3f, 0x66000000);
		Render2D.circle(ctx, cx, cy, 3.5f, 0xFFFFFFFF);
		Render2D.circle(ctx, cx, cy, 2.3f, ColorUtil.withAlpha(fill, 255));
	}

	private void apply() {
		int alpha = ColorUtil.alpha(setting.get());
		setting.set(ColorUtil.fromHsv(hue, sat, val, setting.hasAlpha() ? alpha : 255));
	}

	private void drag(double mouseX, double mouseY) {
		float py = y + ROW + 2f;
		float fx = (float) Math.max(0, Math.min(1, (mouseX - x) / width));
		switch (dragging) {
			case 1 -> {
				sat = fx;
				val = 1f - (float) Math.max(0, Math.min(1, (mouseY - py) / SQUARE));
				apply();
			}
			case 2 -> {
				hue = Math.min(fx, 0.999f);
				apply();
			}
			case 3 -> setting.set(ColorUtil.withAlpha(setting.get(), Math.round(fx * 255)));
			default -> {
			}
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (Ui.inside(mouseX, mouseY, x, y, width, ROW)) {
			expanded = !expanded;
			syncFromSetting();
			return true;
		}
		if (!expanded || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
		float py = y + ROW + 2f;
		float by = py + SQUARE + 6f;
		float ay = by + BAR + 6f;
		float next = setting.hasAlpha() ? ay + BAR + 6f : ay;
		if (Ui.inside(mouseX, mouseY, x, py, width, SQUARE)) dragging = 1;
		else if (Ui.inside(mouseX, mouseY, x, by - 2f, width, BAR + 4f)) dragging = 2;
		else if (setting.hasAlpha() && Ui.inside(mouseX, mouseY, x, ay - 2f, width, BAR + 4f)) dragging = 3;
		else if (Ui.inside(mouseX, mouseY, x, next, rainbowW, CHIP)) {
			setting.setRainbow(!setting.rainbow());
			return true;
		} else return false;
		drag(mouseX, mouseY);
		return true;
	}

	@Override
	public void mouseDragged(double mouseX, double mouseY) {
		if (dragging != 0) drag(mouseX, mouseY);
	}

	@Override
	public void mouseReleased() {
		dragging = 0;
	}
}
