package dev.v1zir.visuals.gui.clickgui;

import dev.v1zir.visuals.module.setting.ColorSetting;
import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

/** Colour swatch that expands into an HSV picker (saturation/value square, hue and alpha bars). */
public final class ColorComponent extends SettingComponent<ColorSetting> {
	private static final float ROW = 15f;
	private static final float SQUARE = 58f;
	private static final float BAR = 6f;
	private static final int[] HUES = {0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF, 0xFF0000FF, 0xFFFF00FF, 0xFFFF0000};

	private final Anim open = new Anim(0f, 14f);
	private boolean expanded;
	private int dragging; // 0 none, 1 square, 2 hue, 3 alpha
	private float hue;
	private float sat;
	private float val;

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

	private float pickerHeight() {
		return SQUARE + 6f + BAR + (setting.hasAlpha() ? 6f + BAR : 0f) + 6f + 11f + 4f;
	}

	@Override
	protected float draw(DrawContext ctx, double mouseX, double mouseY) {
		boolean hover = Ui.inside(mouseX, mouseY, x, y, width, ROW);
		Render2D.textMiddle(ctx, Fonts.regular(), setting.name(), x, y + ROW / 2f, Ui.SMALL + 0.5f, hover || expanded ? Theme.text() : Theme.textDim());
		int color = setting.argb();
		float r = 4.5f;
		Render2D.shadow(ctx, x + width - r * 2, y + ROW / 2f - r, r * 2, r * 2, r, 5f, ColorUtil.withAlpha(color, 110));
		Render2D.circle(ctx, x + width - r, y + ROW / 2f, r, ColorUtil.withAlpha(color, 255));
		Render2D.ring(ctx, x + width - r, y + ROW / 2f, r, 1f, ColorUtil.withAlpha(0xFFFFFF, 60));

		float t = Anim.easeOutCubic(open.target(expanded ? 1f : 0f).get());
		if (t <= 0.01f) return ROW;
		float py = y + ROW + 2f;
		float alphaBackup = Render2D.alpha();
		Render2D.setAlpha(alphaBackup * t);
		// Saturation / value square: white -> hue horizontally, transparent -> black vertically.
		int hueColor = ColorUtil.fromHsv(hue, 1f, 1f, 255);
		Render2D.round(ctx, x, py, width, SQUARE, 4f, 0xFFFFFFFF, hueColor, hueColor, 0xFFFFFFFF);
		Render2D.round(ctx, x, py, width, SQUARE, 4f, 0x00000000, 0x00000000, 0xFF000000, 0xFF000000);
		float kx = x + sat * width;
		float ky = py + (1f - val) * SQUARE;
		Render2D.ring(ctx, kx, ky, 3.2f, 1.4f, 0xFFFFFFFF);
		float by = py + SQUARE + 6f;
		float seg = width / (HUES.length - 1);
		for (int i = 0; i < HUES.length - 1; i++) {
			Render2D.gradientH(ctx, x + seg * i, by, seg + 0.5f, BAR, 0f, HUES[i], HUES[i + 1]);
		}
		Render2D.outline(ctx, x, by, width, BAR, 1f, 1f, ColorUtil.withAlpha(0xFFFFFF, 40));
		Render2D.circle(ctx, x + hue * width, by + BAR / 2f, 3.5f, 0xFFFFFFFF);
		Render2D.circle(ctx, x + hue * width, by + BAR / 2f, 2.3f, hueColor);
		float next = by + BAR + 6f;
		if (setting.hasAlpha()) {
			int opaque = ColorUtil.withAlpha(color, 255);
			Render2D.gradientH(ctx, x, next, width, BAR, BAR / 2f, ColorUtil.withAlpha(opaque, 0), opaque);
			Render2D.outline(ctx, x, next, width, BAR, BAR / 2f, 1f, ColorUtil.withAlpha(0xFFFFFF, 40));
			Render2D.circle(ctx, x + ColorUtil.alpha(setting.get()) / 255f * width, next + BAR / 2f, 3.5f, 0xFFFFFFFF);
			next += BAR + 6f;
		}
		boolean rainbow = setting.rainbow();
		float chipW = Fonts.medium().width("Rainbow", Ui.SMALL) + 10f;
		Render2D.round(ctx, x, next, chipW, 11f, 3.5f, rainbow ? ColorUtil.withAlpha(Theme.accent(), 200) : ColorUtil.withAlpha(0xFFFFFF, 14));
		Render2D.textMiddle(ctx, Fonts.medium(), "Rainbow", x + 5f, next + 5.5f, Ui.SMALL, rainbow ? 0xFFFFFFFF : Theme.textDim());
		String hex = String.format("#%06X", setting.get() & 0xFFFFFF);
		float hw = Fonts.medium().width(hex, Ui.SMALL);
		Render2D.textMiddle(ctx, Fonts.medium(), hex, x + width - hw, next + 5.5f, Ui.SMALL, Theme.textFaint());
		Render2D.setAlpha(alphaBackup);
		return ROW + 2f + pickerHeight() * t;
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
		else if (Ui.inside(mouseX, mouseY, x, next, 50f, 11f)) {
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
