package dev.v1zir.visuals.gui.clickgui;

import dev.v1zir.visuals.module.setting.NumberSetting;
import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

/** Label + value with a gradient slider underneath. */
public final class SliderComponent extends SettingComponent<NumberSetting> {
	private static final float HEIGHT = 21f;
	private final Anim fill;
	private boolean dragging;

	public SliderComponent(NumberSetting setting) {
		super(setting);
		fill = new Anim((float) setting.fraction(), 18f);
	}

	@Override
	protected float draw(DrawContext ctx, double mouseX, double mouseY) {
		boolean hover = Ui.inside(mouseX, mouseY, x, y, width, HEIGHT) || dragging;
		Render2D.text(ctx, Fonts.regular(), setting.name(), x, y + 1.5f, Ui.SMALL + 0.5f, hover ? Theme.text() : Theme.textDim());
		String value = setting.display();
		float vw = Fonts.semibold().width(value, Ui.SMALL + 0.5f);
		Render2D.text(ctx, Fonts.semibold(), value, x + width - vw, y + 1.5f, Ui.SMALL + 0.5f, Theme.accent());
		float ty = y + 14f;
		float th = 3f;
		float f = fill.target((float) setting.fraction()).get();
		Render2D.round(ctx, x, ty, width, th, th / 2f, ColorUtil.withAlpha(0xFFFFFF, 26));
		Render2D.gradientH(ctx, x, ty, Math.max(th, width * f), th, th / 2f, Theme.accent(), Theme.accent2());
		float kx = x + width * f;
		float kr = dragging ? 4f : hover ? 3.6f : 3.2f;
		if (hover) Render2D.shadow(ctx, kx - kr, ty + th / 2f - kr, kr * 2, kr * 2, kr, 6f, ColorUtil.withAlpha(Theme.accent2(), 120));
		Render2D.circle(ctx, kx, ty + th / 2f, kr, 0xFFFFFFFF);
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
