package dev.v1zir.visuals.gui.clickgui;

import dev.v1zir.visuals.module.setting.ModeSetting;
import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.font.SdfFont;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** Label with every option laid out as wrapping chips; the selected one is filled with the accent. */
public final class ModeComponent extends SettingComponent<ModeSetting> {
	private static final float CHIP_HEIGHT = 11f;
	private static final float GAP = 3f;
	private final Label[] widths;
	private final Anim[] chipHover;
	/** Chip layout of the last frame: x, y and width per mode. */
	private final float[] chips;
	private float height = estimateHeight();

	public ModeComponent(ModeSetting setting) {
		super(setting);
		int n = setting.modes().size();
		widths = new Label[n];
		chipHover = new Anim[n];
		for (int i = 0; i < n; i++) {
			widths[i] = new Label();
			chipHover[i] = new Anim(0f, 20f);
		}
		chips = new float[n * 3];
	}

	@Override
	public float estimateHeight() {
		return 12f + CHIP_HEIGHT + GAP;
	}

	@Override
	protected float draw(DrawContext ctx, double mouseX, double mouseY) {
		SdfFont font = Fonts.medium();
		float h = hover.target(Ui.inside(mouseX, mouseY, x, y, width, height) ? 1f : 0f).get();
		Render2D.text(ctx, Fonts.regular(), setting.name(), x, y + 1.5f, Ui.LABEL, labelColor(h));
		int accent = Theme.accent();
		int accent2 = Theme.accent2();
		List<String> modes = setting.modes();
		float cx = x;
		float cy = y + 12f;
		for (int i = 0; i < modes.size(); i++) {
			String mode = modes.get(i);
			float w = widths[i].width(font, mode, Ui.SMALL) + 10f;
			if (cx + w > x + width && cx > x) {
				cx = x;
				cy += CHIP_HEIGHT + GAP;
			}
			boolean selected = setting.is(mode);
			float ch = chipHover[i].target(Ui.inside(mouseX, mouseY, cx, cy, w, CHIP_HEIGHT) ? 1f : 0f).get();
			if (selected) {
				Render2D.shadow(ctx, cx, cy, w, CHIP_HEIGHT, 4f, 5f, ColorUtil.withAlpha(accent, Math.round(70 + 40 * ch)));
				Render2D.gradientH(ctx, cx, cy, w, CHIP_HEIGHT, 4f, accent, accent2);
			} else {
				Render2D.round(ctx, cx, cy, w, CHIP_HEIGHT, 4f, ColorUtil.withAlpha(0xFFFFFF, Math.round(9 + 13 * ch)));
				Render2D.outline(ctx, cx, cy, w, CHIP_HEIGHT, 4f, 1f, ColorUtil.withAlpha(0xFFFFFF, Math.round(12 + 18 * ch)));
			}
			int color = selected ? 0xFFFFFFFF : ColorUtil.lerp(Theme.textDim(), Theme.text(), ch);
			Render2D.textMiddle(ctx, font, mode, cx + 5f, cy + CHIP_HEIGHT / 2f, Ui.SMALL, color);
			chips[i * 3] = cx;
			chips[i * 3 + 1] = cy;
			chips[i * 3 + 2] = w;
			cx += w + GAP;
		}
		height = cy + CHIP_HEIGHT + GAP - y;
		return height;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
		List<String> modes = setting.modes();
		for (int i = 0; i < modes.size(); i++) {
			if (Ui.inside(mouseX, mouseY, chips[i * 3], chips[i * 3 + 1], chips[i * 3 + 2], CHIP_HEIGHT)) {
				setting.set(modes.get(i));
				return true;
			}
		}
		return false;
	}
}
