package dev.v1zir.visuals.gui.clickgui;

import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.Anim;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

/** Label with a toggle switch: a recessed matte track that fills with the accent while the knob glides across. */
public final class BoolComponent extends SettingComponent<BoolSetting> {
	private static final float HEIGHT = 15f;
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

		Ui.toggle(ctx, x + width - Ui.SWITCH_W, y + (HEIGHT - Ui.SWITCH_H) / 2f, t, h);
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
