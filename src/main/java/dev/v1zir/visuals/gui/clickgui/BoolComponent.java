package dev.v1zir.visuals.gui.clickgui;

import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

/** Label with an animated toggle switch. */
public final class BoolComponent extends SettingComponent<BoolSetting> {
	private static final float HEIGHT = 15f;
	private final Anim knob;

	public BoolComponent(BoolSetting setting) {
		super(setting);
		knob = new Anim(setting.on() ? 1f : 0f, 16f);
	}

	@Override
	protected float draw(DrawContext ctx, double mouseX, double mouseY) {
		boolean on = setting.on();
		boolean hover = Ui.inside(mouseX, mouseY, x, y, width, HEIGHT);
		Render2D.textMiddle(ctx, Fonts.regular(), setting.name(), x, y + HEIGHT / 2f, Ui.SMALL + 0.5f, hover || on ? Theme.text() : Theme.textDim());
		float sw = 17f;
		float sh = 9f;
		float sx = x + width - sw;
		float sy = y + (HEIGHT - sh) / 2f;
		float t = knob.target(on ? 1f : 0f).get();
		int track = ColorUtil.lerp(ColorUtil.withAlpha(0xFFFFFF, 28), Theme.accent(), t);
		if (t > 0.01f) Render2D.shadow(ctx, sx, sy, sw, sh, sh / 2f, 5f, ColorUtil.withAlpha(Theme.accent(), Math.round(90 * t)));
		Render2D.round(ctx, sx, sy, sw, sh, sh / 2f, track);
		float kr = sh / 2f - 1.5f;
		float kx = sx + 1.5f + kr + t * (sw - 3f - kr * 2f);
		Render2D.circle(ctx, kx, sy + sh / 2f, kr, 0xFFFFFFFF);
		return HEIGHT;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && Ui.inside(mouseX, mouseY, x, y, width, HEIGHT)) {
			setting.toggle();
			return true;
		}
		return false;
	}
}
