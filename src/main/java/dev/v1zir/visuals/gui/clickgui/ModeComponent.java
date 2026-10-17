package dev.v1zir.visuals.gui.clickgui;

import dev.v1zir.visuals.module.setting.ModeSetting;
import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.font.SdfFont;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Label with every option laid out as wrapping chips (like the reference "Interact" setting). */
public final class ModeComponent extends SettingComponent<ModeSetting> {
	private static final float CHIP_HEIGHT = 11f;
	private final List<float[]> chips = new ArrayList<>();

	public ModeComponent(ModeSetting setting) {
		super(setting);
	}

	@Override
	protected float draw(DrawContext ctx, double mouseX, double mouseY) {
		SdfFont font = Fonts.medium();
		float size = Ui.SMALL;
		Render2D.text(ctx, Fonts.regular(), setting.name(), x, y + 1.5f, Ui.SMALL + 0.5f, Theme.textDim());
		float cx = x;
		float cy = y + 12f;
		chips.clear();
		for (String mode : setting.modes()) {
			float w = font.width(mode, size) + 9f;
			if (cx + w > x + width && cx > x) {
				cx = x;
				cy += CHIP_HEIGHT + 3f;
			}
			boolean selected = setting.is(mode);
			boolean hover = mouseX >= cx && mouseX <= cx + w && mouseY >= cy && mouseY <= cy + CHIP_HEIGHT;
			int bg = selected ? ColorUtil.withAlpha(Theme.accent(), 200) : ColorUtil.withAlpha(0xFFFFFF, hover ? 26 : 12);
			if (selected) Render2D.shadow(ctx, cx, cy, w, CHIP_HEIGHT, 3.5f, 5f, ColorUtil.withAlpha(Theme.accent(), 80));
			Render2D.round(ctx, cx, cy, w, CHIP_HEIGHT, 3.5f, bg);
			Render2D.textMiddle(ctx, font, mode, cx + 4.5f, cy + CHIP_HEIGHT / 2f, size, selected ? 0xFFFFFFFF : hover ? Theme.text() : Theme.textDim());
			chips.add(new float[]{cx, cy, w});
			cx += w + 3f;
		}
		return cy + CHIP_HEIGHT + 3f - y;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
		for (int i = 0; i < chips.size(); i++) {
			float[] c = chips.get(i);
			if (mouseX >= c[0] && mouseX <= c[0] + c[2] && mouseY >= c[1] && mouseY <= c[1] + CHIP_HEIGHT) {
				setting.set(setting.modes().get(i));
				return true;
			}
		}
		return false;
	}
}
