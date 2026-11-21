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
	private Label[] widths = new Label[0];
	private Anim[] chipHover = new Anim[0];
	/** Chip layout of the last frame: x, y and width per mode. */
	private float[] chips = new float[0];
	/** How many chips the last frame laid out (the modes can change between frames). */
	private int laid;
	private float height = estimateHeight();

	public ModeComponent(ModeSetting setting) {
		super(setting);
		fit(setting.modes().size());
	}

	/**
	 * Room for n chips. Some settings' modes follow a folder (Voice Chat Music's files, Hit Sound's sounds) and grow
	 * or shrink while the menu is open.
	 */
	private void fit(int n) {
		if (widths.length >= n) return;
		int had = widths.length;
		widths = java.util.Arrays.copyOf(widths, n);
		chipHover = java.util.Arrays.copyOf(chipHover, n);
		chips = java.util.Arrays.copyOf(chips, n * 3);
		for (int i = had; i < n; i++) {
			widths[i] = new Label();
			chipHover[i] = new Anim(0f, 20f);
		}
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
		List<String> modes = setting.modes();
		fit(modes.size());
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
				// Selected: an accent tint with an accent rim; the label stays light on every preset.
				Render2D.matte(ctx, cx, cy, w, CHIP_HEIGHT, Ui.CELL_RADIUS, ColorUtil.withAlpha(accent, Math.round(46 + 14 * ch)), Ui.LIT);
				Render2D.rim(ctx, cx, cy, w, CHIP_HEIGHT, Ui.CELL_RADIUS, ColorUtil.withAlpha(accent, 170), Ui.LIT);
			} else {
				Render2D.matte(ctx, cx, cy, w, CHIP_HEIGHT, 4f, ColorUtil.withAlpha(0xFFFFFF, Math.round(9 + 13 * ch)), Ui.LIT);
				Render2D.rim(ctx, cx, cy, w, CHIP_HEIGHT, 4f, ColorUtil.withAlpha(0xFFFFFF, Math.round(14 + 20 * ch)), Ui.LIT);
			}
			int color = selected ? ColorUtil.lerp(accent, 0xFFFFFFFF, 0.7f) : ColorUtil.lerp(Theme.textDim(), Theme.text(), ch);
			Render2D.textMiddle(ctx, font, mode, cx + 5f, cy + CHIP_HEIGHT / 2f, Ui.SMALL, color);
			chips[i * 3] = cx;
			chips[i * 3 + 1] = cy;
			chips[i * 3 + 2] = w;
			cx += w + GAP;
		}
		laid = modes.size();
		height = cy + CHIP_HEIGHT + GAP - y;
		return height;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
		List<String> modes = setting.modes();
		// Only the chips that were drawn can be clicked (the modes may have changed since).
		for (int i = 0; i < Math.min(laid, modes.size()); i++) {
			if (Ui.inside(mouseX, mouseY, chips[i * 3], chips[i * 3 + 1], chips[i * 3 + 2], CHIP_HEIGHT)) {
				setting.set(modes.get(i));
				return true;
			}
		}
		return false;
	}
}
