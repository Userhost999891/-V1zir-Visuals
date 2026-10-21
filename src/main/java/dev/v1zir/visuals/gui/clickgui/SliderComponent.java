package dev.v1zir.visuals.gui.clickgui;

import dev.v1zir.visuals.module.setting.NumberSetting;
import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.font.SdfFont;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

/** Label and value over a slim gradient track; the knob carries an accent core. */
public final class SliderComponent extends SettingComponent<NumberSetting> {
	private static final float HEIGHT = 22f;
	private static final float TRACK = 3f;
	private final Anim fill;
	private final Label valueWidth = new Label();
	private boolean dragging;
	/** The formatted value, rebuilt only when the value changes. */
	private double shownValue = Double.NaN;
	private String shownText = "";

	public SliderComponent(NumberSetting setting) {
		super(setting);
		fill = new Anim((float) setting.fraction(), 18f);
	}

	@Override
	public float estimateHeight() {
		return HEIGHT;
	}

	@Override
	protected float draw(DrawContext ctx, double mouseX, double mouseY) {
		float h = hover.target(dragging || Ui.inside(mouseX, mouseY, x, y, width, HEIGHT) ? 1f : 0f).get();
		Render2D.text(ctx, Fonts.regular(), setting.name(), x, y + 1.5f, Ui.LABEL, labelColor(h));
		if (setting.get() != shownValue) {
			shownValue = setting.get();
			shownText = setting.display();
		}
		SdfFont valueFont = Fonts.semibold();
		float vw = valueWidth.width(valueFont, shownText, Ui.LABEL);
		int accent = Theme.accent();
		int accent2 = Theme.accent2();
		Render2D.text(ctx, valueFont, shownText, x + width - vw, y + 1.5f, Ui.LABEL, ColorUtil.lerp(ColorUtil.lerp(accent, Theme.text(), 0.25f), accent, h));

		float ty = y + 15f;
		float f = fill.target((float) setting.fraction()).get();
		float fw = Math.max(TRACK, width * f);
		Render2D.round(ctx, x, ty, width, TRACK, TRACK / 2f, ColorUtil.withAlpha(0xFFFFFF, Math.round(20 + 10 * h)));
		if (h > 0.01f) Render2D.glow(ctx, x, ty, fw, TRACK, TRACK / 2f, 5f, ColorUtil.withAlpha(accent, Math.round(80 * h)), ColorUtil.withAlpha(accent2, Math.round(80 * h)));
		Render2D.gradientH(ctx, x, ty, fw, TRACK, TRACK / 2f, accent, accent2);
		float kx = x + width * f;
		float ky = ty + TRACK / 2f;
		float kr = 3.6f;
		Render2D.shadow(ctx, kx - kr, ky - kr + 0.6f, kr * 2f, kr * 2f, kr, 3f, 0x60000000);
		Render2D.circle(ctx, kx, ky, kr, 0xFFFFFFFF);
		Render2D.circle(ctx, kx, ky, 1.5f, ColorUtil.lerp(accent, accent2, f));
		return HEIGHT;
	}

	private void update(double mouseX) {
		setting.setFraction((mouseX - x) / Math.max(1f, width));
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && Ui.inside(mouseX, mouseY, x - 2f, y, width + 4f, HEIGHT)) {
			dragging = true;
			update(mouseX);
			return true;
		}
		return false;
	}

	@Override
	public void mouseDragged(double mouseX, double mouseY) {
		if (dragging) update(mouseX);
	}

	@Override
	public void mouseReleased() {
		dragging = false;
	}
}
