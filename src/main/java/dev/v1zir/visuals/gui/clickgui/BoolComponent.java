package dev.v1zir.visuals.gui.clickgui;

import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

/** Label with a toggle switch: a recessed track that fills with the accent while the knob glides across. */
public final class BoolComponent extends SettingComponent<BoolSetting> {
	private static final float HEIGHT = 15f;
	private static final float SWITCH_W = 17f;
	private static final float SWITCH_H = 9f;
	private final Anim knob;

	public BoolComponent(BoolSetting setting) {
		super(setting);
		knob = new Anim(setting.on() ? 1f : 0f, 16f);
	}

	@Override
	public float estimateHeight() {
		return HEIGHT;
	}

	@Override
	protected float draw(DrawContext ctx, double mouseX, double mouseY) {
		boolean on = setting.on();
		float h = hover.target(Ui.inside(mouseX, mouseY, x, y, width, HEIGHT) ? 1f : 0f).get();
		float t = knob.target(on ? 1f : 0f).get();
		Render2D.textMiddle(ctx, Fonts.regular(), setting.name(), x, y + HEIGHT / 2f, Ui.LABEL, labelColor(Math.max(h, t)));

		float sx = x + width - SWITCH_W;
		float sy = y + (HEIGHT - SWITCH_H) / 2f;
		float r = SWITCH_H / 2f;
		int accent = Theme.accent();
		Render2D.round(ctx, sx, sy, SWITCH_W, SWITCH_H, r, ColorUtil.withAlpha(0xFFFFFF, Math.round(18 + 14 * h)));
		if (t > 0.01f) {
			Render2D.shadow(ctx, sx, sy, SWITCH_W, SWITCH_H, r, 5f, ColorUtil.withAlpha(accent, Math.round((70 + 30 * h) * t)));
			Render2D.gradientH(ctx, sx, sy, SWITCH_W, SWITCH_H, r, ColorUtil.fade(accent, t), ColorUtil.fade(Theme.accent2(), t));
		}
		float kr = r - 1.5f;
		float kx = sx + 1.5f + kr + t * (SWITCH_W - 3f - kr * 2f);
		float ky = sy + r;
		Render2D.shadow(ctx, kx - kr, ky - kr + 0.6f, kr * 2f, kr * 2f, kr, 2.5f, 0x55000000);
		Render2D.circle(ctx, kx, ky, kr, ColorUtil.lerp(0xFFD9DCE4, 0xFFFFFFFF, Math.max(t, h)));
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
