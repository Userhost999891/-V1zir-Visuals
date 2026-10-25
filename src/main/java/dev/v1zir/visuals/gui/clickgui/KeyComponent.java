package dev.v1zir.visuals.gui.clickgui;

import dev.v1zir.visuals.module.setting.KeySetting;
import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.font.SdfFont;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.KeyNames;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

/** Label with a key chip: click it, then press a key (Esc, Backspace or Delete clears the key). */
public final class KeyComponent extends SettingComponent<KeySetting> {
	private static final float HEIGHT = 15f;
	private static final float CHIP_HEIGHT = 10f;
	private static final String WAITING = "...";
	private final Anim listen = new Anim(0f, 16f);
	private final Label chipWidth = new Label();
	private boolean listening;
	/** Name of the key, rebuilt only when the key changes. */
	private int shownKey = Integer.MIN_VALUE;
	private String shownName = "";

	public KeyComponent(KeySetting setting) {
		super(setting);
	}

	boolean listening() {
		return listening;
	}

	@Override
	public float estimateHeight() {
		return HEIGHT;
	}

	@Override
	protected float draw(DrawContext ctx, double mouseX, double mouseY) {
		if (shownKey != setting.key()) {
			shownKey = setting.key();
			shownName = KeyNames.name(shownKey);
		}
		SdfFont font = Fonts.semibold();
		float h = hover.target(Ui.inside(mouseX, mouseY, x, y, width, HEIGHT) ? 1f : 0f).get();
		float t = listen.target(listening ? 1f : 0f).get();
		Render2D.textMiddle(ctx, Fonts.regular(), setting.name(), x, y + HEIGHT / 2f, Ui.LABEL, labelColor(Math.max(h, t)));

		String label = listening ? WAITING : shownName;
		float cw = Math.max(18f, chipWidth.width(font, label, Ui.SMALL) + 8f);
		float cx = x + width - cw;
		float cy = y + (HEIGHT - CHIP_HEIGHT) / 2f;
		if (t > 0.01f) Render2D.shadow(ctx, cx, cy, cw, CHIP_HEIGHT, Ui.CELL_RADIUS, 5f, ColorUtil.withAlpha(Theme.accent(), Math.round(90 * t)));
		Render2D.matte(ctx, cx, cy, cw, CHIP_HEIGHT, Ui.CELL_RADIUS, ColorUtil.lerp(ColorUtil.withAlpha(0xFFFFFF, Math.round(9 + 13 * h)), Theme.accent(), t), Ui.LIT);
		Render2D.rim(ctx, cx, cy, cw, CHIP_HEIGHT, Ui.CELL_RADIUS, ColorUtil.withAlpha(0xFFFFFF, Math.round((14 + 20 * h) * (1f - t))), Ui.LIT);
		Render2D.textCentered(ctx, font, label, cx + cw / 2f, Render2D.middle(font, cy + CHIP_HEIGHT / 2f, Ui.SMALL), Ui.SMALL,
			ColorUtil.lerp(labelColor(h), 0xFFFFFFFF, t));
		return HEIGHT;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && Ui.inside(mouseX, mouseY, x, y, width, HEIGHT)) {
			listening = !listening;
			return true;
		}
		listening = false;
		return false;
	}

	@Override
	public boolean keyPressed(int key) {
		if (!listening) return false;
		boolean clear = key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE;
		setting.set(clear ? GLFW.GLFW_KEY_UNKNOWN : key);
		listening = false;
		return true;
	}
}
