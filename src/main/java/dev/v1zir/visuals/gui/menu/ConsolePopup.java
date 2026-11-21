package dev.v1zir.visuals.gui.menu;

import dev.v1zir.visuals.gui.UiSounds;
import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.font.SdfFont;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.gui.DrawContext;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * A small menu over the PS5 style home screen (a tile's "..."): its choices as large frosted pills on a dark frosted
 * panel, the one under the pointer or picked with the arrow keys lighter, with a white rim and a soft light around it.
 * It scales in from its middle over the dimmed screen; Escape, Backspace or a click beside it closes it.
 */
final class ConsolePopup {
	/** One choice: its words, its icon (0 for none) and what it does. */
	record Choice(String label, int icon, Runnable action) {
	}

	/** A choice is a faint pill; in focus it turns white and its words dark, as a console marks it. */
	private static final int PILL = 0x14FFFFFF;
	private static final int PILL_HOT = 0xF5FFFFFF;
	private static final int TEXT_HOT = 0xFF121215;

	private final List<Choice> choices = new ArrayList<>();
	private final List<Anim> hover = new ArrayList<>();
	private final Anim open = new Anim(0f, 16f);
	private String title = "";
	private boolean shown;
	private int picked;
	/** The rows as last drawn, for the mouse: top left, size and gap. */
	private float rowX;
	private float rowY;
	private float rowW;
	private float rowH;
	private float rowGap;
	private float panelX;
	private float panelY;
	private float panelW;
	private float panelH;

	void show(String title, List<Choice> next) {
		this.title = title == null ? "" : title;
		choices.clear();
		choices.addAll(next);
		hover.clear();
		for (int i = 0; i < choices.size(); i++) hover.add(new Anim(0f, 18f));
		picked = 0;
		shown = !choices.isEmpty();
		if (shown) open.snap(0f);
	}

	boolean shown() {
		return shown;
	}

	void close() {
		if (!shown) return;
		shown = false;
		UiSounds.play(UiSounds.POPUP_BACK);
	}

	/** How far it is open, 0 to 1. */
	float amount() {
		return open.target(shown ? 1f : 0f).get();
	}

	void render(DrawContext ctx, float width, float height, int mouseX, int mouseY) {
		float e = amount();
		if (e <= 0.01f) return;
		Render2D.rect(ctx, 0f, 0f, width, height, ColorUtil.withAlpha(0x05030A, Math.round(150 * e)));
		int n = choices.size();
		rowH = Math.max(18f, Math.min(34f, height * 0.1f));
		rowGap = rowH * 0.28f;
		float pad = rowH * 0.5f;
		SdfFont titleFont = Fonts.medium();
		float titleSize = Math.max(6.5f, rowH * 0.3f);
		float titleH = title.isEmpty() ? 0f : titleSize + pad * 0.6f;
		panelW = Math.max(150f, Math.min(300f, width * 0.42f));
		panelH = pad * 2f + titleH + n * rowH + Math.max(0, n - 1) * rowGap;
		panelX = (width - panelW) / 2f;
		panelY = (height - panelH) / 2f;
		rowX = panelX + pad;
		rowW = panelW - pad * 2f;
		rowY = panelY + pad + titleH;
		Matrix3x2fStack m = ctx.getMatrices();
		m.pushMatrix();
		// Opening it springs a little past its size; closing it just shrinks back.
		float s = 0.92f + 0.08f * (shown ? Anim.easeOutBack(Math.min(1f, e)) : e);
		float cx = panelX + panelW / 2f, cy = panelY + panelH / 2f;
		m.translate(cx, cy);
		m.scale(s, s);
		m.translate(-cx, -cy);
		float r = Math.min(22f, rowH * 0.8f);
		Render2D.shadow(ctx, panelX, panelY + 4f, panelW, panelH, r, 22f, ColorUtil.withAlpha(0x000000, Math.round(150 * e)));
		Render2D.round(ctx, panelX, panelY, panelW, panelH, r, ColorUtil.fade(ColorUtil.withAlpha(ColorUtil.brighten(Theme.preset().base(), 0.08f), 0xE0), e));
		Render2D.gradientV(ctx, panelX, panelY, panelW, panelH, r, ColorUtil.withAlpha(0xFFFFFF, Math.round(18 * e)), 0x00FFFFFF);
		Render2D.outline(ctx, panelX, panelY, panelW, panelH, r, 1f, ColorUtil.withAlpha(0xFFFFFF, Math.round(26 * e)));
		if (!title.isEmpty()) {
			Render2D.textCentered(ctx, titleFont, title, panelX + panelW / 2f, panelY + pad * 0.75f, titleSize, ColorUtil.fade(0x99FFFFFF, e));
		}
		int over = rowAt(mouseX, mouseY);
		if (over >= 0) picked = over;
		SdfFont font = Fonts.medium();
		float size = Math.max(7f, rowH * 0.4f);
		for (int i = 0; i < n; i++) {
			Choice choice = choices.get(i);
			float y = rowY + i * (rowH + rowGap);
			float hv = hover.get(i).target(i == picked ? 1f : 0f).get();
			// In focus the pill turns white, grows a touch and has a soft light around it; its words turn dark.
			float grow = 1f + 0.03f * hv;
			float gw = rowW * grow, gh = rowH * grow;
			float gx = rowX - (gw - rowW) / 2f, gy = y - (gh - rowH) / 2f;
			if (hv > 0.01f) Render2D.glow(ctx, gx, gy, gw, gh, gh / 2f, 10f, ColorUtil.withAlpha(0xFFFFFF, Math.round(55 * hv * e)),
				ColorUtil.withAlpha(0xFFFFFF, Math.round(55 * hv * e)));
			Render2D.round(ctx, gx, gy, gw, gh, gh / 2f, ColorUtil.fade(ColorUtil.lerp(PILL, PILL_HOT, hv), e));
			int words = ColorUtil.fade(ColorUtil.lerp(0xFFFFFFFF, TEXT_HOT, hv), e);
			String label = fit(font, choice.label(), size, rowW - rowH * 1.6f);
			float tw = font.width(label, size);
			float iconW = choice.icon() != 0 ? size * 1.5f : 0f;
			float tx = rowX + (rowW - tw - iconW) / 2f;
			if (choice.icon() != 0) Render2D.icon(ctx, choice.icon(), tx + size * 0.5f, y + rowH / 2f, size, words);
			Render2D.textMiddle(ctx, font, label, tx + iconW, y + rowH / 2f, size, words);
		}
		m.popMatrix();
	}

	private int rowAt(double mx, double my) {
		if (!shown || mx < rowX || mx > rowX + rowW) return -1;
		for (int i = 0; i < choices.size(); i++) {
			float y = rowY + i * (rowH + rowGap);
			if (my >= y && my <= y + rowH) return i;
		}
		return -1;
	}

	/** A click while it is open: a choice runs it, a click beside the panel closes it. Always taken while open. */
	boolean mouseClicked(double mx, double my) {
		if (!shown) return false;
		int row = rowAt(mx, my);
		if (row >= 0) {
			choose(row);
		} else if (mx < panelX || mx > panelX + panelW || my < panelY || my > panelY + panelH) {
			close();
		}
		return true;
	}

	/** Keys while it is open: up and down pick, Enter or Space runs, Escape or Backspace closes. Always taken while open. */
	boolean keyPressed(int key) {
		if (!shown) return false;
		switch (key) {
			case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_W -> move(-1);
			case GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_S -> move(1);
			case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> choose(picked);
			case GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_KEY_BACKSPACE -> close();
			default -> {
			}
		}
		return true;
	}

	private void move(int direction) {
		int next = Math.max(0, Math.min(choices.size() - 1, picked + direction));
		if (next != picked) {
			picked = next;
			UiSounds.play(UiSounds.MOVE);
		}
	}

	private void choose(int index) {
		if (index < 0 || index >= choices.size()) return;
		Choice choice = choices.get(index);
		shown = false;
		UiSounds.play(UiSounds.POPUP_ENTER);
		choice.action().run();
	}

	private static String fit(SdfFont font, String text, float size, float width) {
		if (font.width(text, size) <= width) return text;
		String dots = "…";
		int n = text.length();
		while (n > 0 && font.width(text.substring(0, n) + dots, size) > width) n--;
		return text.substring(0, n).strip() + dots;
	}
}
