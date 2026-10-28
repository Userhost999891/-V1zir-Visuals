package dev.v1zir.visuals.gui.clickgui;

import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.setting.Setting;
import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.font.Icons;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.KeyNames;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.gui.DrawContext;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The settings of one module in a column that slides in over the right side of the content area,
 * so the grid underneath never reflows. Header with name, description, switch and close button,
 * then the key bind and every setting in a softly scrolling list.
 */
final class SettingsDrawer {
	private static final float HEADER = 42f;
	private static final float ROW = 15f;
	private static final float CLOSE = 15f;

	private final Anim open = new Anim(0f, 15f);
	private final Anim contentFade = new Anim(1f, 14f);
	private final Anim closeHover = new Anim(0f, 22f);
	private final Anim switchHover = new Anim(0f, 22f);
	private final Anim bindHover = new Anim(0f, 22f);
	private final Anim bindListen = new Anim(0f, 16f);
	private final List<SettingComponent<?>> components = new ArrayList<>();
	private final Scroller scroller = new Scroller();
	private final Label bindWidth = new Label();
	@Nullable
	private Module module;
	private boolean closing;
	private boolean binding;
	private String description = "";
	private float descriptionWidth = -1f;
	private int shownKey = Integer.MIN_VALUE;
	private String keyName = "";
	// Layout of the last frame.
	private float x;
	private float y;
	private float w;
	private float h;
	/** Top of the key bind row, which scrolls with the settings. */
	private float bindY;

	/** The module whose settings are shown, or null while closed (it stays set during the slide out). */
	@Nullable
	Module module() {
		return closing ? null : module;
	}

	boolean visible() {
		return module != null;
	}

	void show(Module next) {
		closing = false;
		open.target(1f);
		if (next == module) return;
		module = next;
		binding = false;
		components.clear();
		for (Setting<?> setting : next.settings()) components.add(SettingComponent.of(setting));
		descriptionWidth = -1f;
		scroller.reset();
		contentFade.snap(0f);
		contentFade.target(1f);
	}

	void close() {
		if (module == null) return;
		closing = true;
		binding = false;
		open.target(0f);
	}

	/** Width of the column for a content area of the given width. */
	static float width(float contentW) {
		return Math.max(170f, Math.min(232f, contentW * 0.5f));
	}

	/** Draws the column against the right edge of the content area (x + areaW). */
	void render(DrawContext ctx, float areaX, float areaY, float areaW, float areaH, double mx, double my) {
		float t = open.get();
		if (closing && t < 0.005f) {
			module = null;
			closing = false;
			components.clear();
			return;
		}
		if (module == null) return;
		w = width(areaW);
		h = areaH;
		x = areaX + areaW - w * t;
		y = areaY;
		float alpha = Render2D.alpha();
		Render2D.setAlpha(alpha * Math.min(1f, t * 1.6f));
		Render2D.shadow(ctx, x - 2f, y, w, h, Ui.PANEL_RADIUS, 14f, 0x70000000);
		Render2D.matte(ctx, x, y, w, h, Ui.PANEL_RADIUS, ColorUtil.withAlpha(ColorUtil.brighten(Theme.preset().base(), 0.07f), 246), Ui.LIT);
		Render2D.rim(ctx, x, y, w, h, Ui.PANEL_RADIUS, Theme.outline(), Ui.LIT);
		Render2D.setAlpha(alpha * Math.min(1f, t * 1.6f) * contentFade.get());
		renderHeader(ctx, mx, my);
		renderBody(ctx, mx, my);
		Render2D.setAlpha(alpha);
	}

	private void renderHeader(DrawContext ctx, double mx, double my) {
		Module m = module;
		float cx = x + w - 10f - CLOSE;
		float ch = closeHover.target(Ui.inside(mx, my, cx, y + 8f, CLOSE, CLOSE) ? 1f : 0f).get();
		Render2D.matte(ctx, cx, y + 8f, CLOSE, CLOSE, Ui.CELL_RADIUS, ColorUtil.withAlpha(0xFFFFFF, Math.round(8 + 14 * ch)), Ui.LIT);
		Render2D.icon(ctx, Icons.X, cx + CLOSE / 2f, y + 8f + CLOSE / 2f, 7f, ColorUtil.lerp(Theme.textDim(), Theme.text(), ch));
		float right = cx - 8f;
		if (m.canToggle()) {
			float sx = right - Ui.SWITCH_W;
			float sh = switchHover.target(Ui.inside(mx, my, sx - 2f, y + 9f, Ui.SWITCH_W + 4f, 13f) ? 1f : 0f).get();
			Ui.toggle(ctx, sx, y + 15.5f - Ui.SWITCH_H / 2f, m.isEnabled() ? 1f : 0f, sh);
			right = sx - 6f;
		}
		Render2D.textMiddle(ctx, Fonts.bold(), m.name(), x + 12f, y + 15.5f, 9f, m.isEnabled() ? 0xFFFFFFFF : Theme.text());
		float maxDesc = w - 24f;
		if (maxDesc != descriptionWidth) {
			descriptionWidth = maxDesc;
			description = Fonts.regular().trim(m.description(), Ui.TINY, maxDesc);
		}
		Render2D.textMiddle(ctx, Fonts.regular(), description, x + 12f, y + 29f, Ui.TINY, Theme.textDim());
		Render2D.rect(ctx, x + 12f, y + HEADER - 1f, w - 24f, 1f, Ui.HAIRLINE);
	}

	private void renderBody(DrawContext ctx, double mx, double my) {
		float bx = x + 12f;
		float bw = w - 24f;
		float top = y + HEADER + 4f;
		float bottom = y + h - 6f;
		float viewH = bottom - top;
		float content = ROW + 6f;
		for (SettingComponent<?> c : components) if (c.visible()) content += c.height() + 4f;
		float scroll = scroller.update(content, viewH);
		boolean inView = Ui.inside(mx, my, x, top, w, viewH);
		double cmx = inView ? mx : -1000;
		double cmy = inView ? my : -1000;

		ctx.enableScissor((int) Math.floor(x), (int) Math.floor(top), (int) Math.ceil(x + w), (int) Math.ceil(bottom));
		float alpha = Render2D.alpha();
		float cy = top + 3f - scroll;
		Render2D.setAlpha(alpha * Ui.edgeFade(cy, cy + ROW, top, bottom));
		renderBind(ctx, bx, cy, bw, cmx, cmy);
		cy += ROW + 6f;
		for (SettingComponent<?> c : components) {
			if (!c.visible()) continue;
			// Components out of view still lay out (their shapes are clipped away), so hit areas stay current.
			Render2D.setAlpha(alpha * Ui.edgeFade(cy, cy + Math.min(c.height(), 22f), top, bottom));
			cy += c.render(ctx, bx, cy, bw, cmx, cmy) + 4f;
		}
		Render2D.setAlpha(alpha);
		ctx.disableScissor();
		scroller.drawBar(ctx, x + w - 5f, top, viewH, content, inView);
	}

	/** "Keybind" row: click the chip, then press a key (Esc, Backspace or Delete clears it). */
	private void renderBind(DrawContext ctx, float bx, float by, float bw, double mx, double my) {
		Module m = module;
		bindY = by;
		if (m.key() != shownKey) {
			shownKey = m.key();
			keyName = shownKey == GLFW.GLFW_KEY_UNKNOWN ? "None" : KeyNames.name(shownKey);
		}
		float hv = bindHover.target(Ui.inside(mx, my, bx, by, bw, ROW) ? 1f : 0f).get();
		float t = bindListen.target(binding ? 1f : 0f).get();
		Render2D.icon(ctx, Icons.KEYBOARD, bx + 4f, by + ROW / 2f, 7f, ColorUtil.lerp(ColorUtil.lerp(Theme.textDim(), Theme.text(), hv), Theme.accent(), t));
		Render2D.textMiddle(ctx, Fonts.regular(), "Keybind", bx + 12f, by + ROW / 2f, Ui.LABEL, ColorUtil.lerp(Theme.textDim(), Theme.text(), Math.max(hv, t)));
		String label = binding ? "..." : keyName;
		float cw = Math.max(22f, bindWidth.width(Fonts.semibold(), label, Ui.SMALL) + 10f);
		float cx = bx + bw - cw;
		float cy = by + (ROW - 11f) / 2f;
		int accent = Theme.accent();
		Render2D.matte(ctx, cx, cy, cw, 11f, Ui.CELL_RADIUS, ColorUtil.lerp(ColorUtil.withAlpha(0xFFFFFF, Math.round(10 + 10 * hv)), ColorUtil.withAlpha(accent, 50), t), Ui.LIT);
		Render2D.rim(ctx, cx, cy, cw, 11f, Ui.CELL_RADIUS, ColorUtil.lerp(ColorUtil.withAlpha(0xFFFFFF, Math.round(18 + 16 * hv)), ColorUtil.withAlpha(accent, 170), t), Ui.LIT);
		Render2D.textCentered(ctx, Fonts.semibold(), label, cx + cw / 2f, Render2D.middle(Fonts.semibold(), cy + 5.5f, Ui.SMALL), Ui.SMALL,
			ColorUtil.lerp(Theme.textDim(), 0xFFFFFFFF, Math.max(t, hv)));
	}

	boolean over(double mx, double my) {
		return module != null && !closing && Ui.inside(mx, my, x, y, w, h);
	}

	/** Returns true when the click landed in the column (it is consumed either way). */
	boolean mouseClicked(double mx, double my, int button) {
		if (!over(mx, my)) return false;
		Module m = module;
		float cx = x + w - 10f - CLOSE;
		if (Ui.inside(mx, my, cx, y + 8f, CLOSE, CLOSE)) {
			close();
			return true;
		}
		if (m.canToggle() && Ui.inside(mx, my, cx - 8f - Ui.SWITCH_W - 2f, y + 9f, Ui.SWITCH_W + 4f, 13f)) {
			m.toggle();
			return true;
		}
		if (my < y + HEADER + 4f) return true;
		if (Ui.inside(mx, my, x, bindY, w, ROW)) {
			binding = !binding;
			return true;
		}
		binding = false;
		for (SettingComponent<?> c : components) if (c.visible() && c.mouseClicked(mx, my, button)) return true;
		return true;
	}

	void mouseDragged(double mx, double my) {
		for (SettingComponent<?> c : components) c.mouseDragged(mx, my);
	}

	void mouseReleased() {
		for (SettingComponent<?> c : components) c.mouseReleased();
	}

	boolean mouseScrolled(double mx, double my, double amount) {
		if (!over(mx, my)) return false;
		scroller.scroll(amount);
		return true;
	}

	boolean isBinding() {
		if (binding) return true;
		for (SettingComponent<?> c : components) if (c instanceof KeyComponent key && key.listening()) return true;
		return false;
	}

	/** Returns true when the key was consumed (bind mode or a key setting). */
	boolean keyPressed(int key) {
		if (module == null) return false;
		if (binding) {
			boolean clear = key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE;
			module.setKey(clear ? GLFW.GLFW_KEY_UNKNOWN : key);
			binding = false;
			return true;
		}
		for (SettingComponent<?> c : components) if (c.keyPressed(key)) return true;
		return false;
	}
}
