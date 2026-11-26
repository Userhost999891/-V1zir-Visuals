package dev.v1zir.visuals.gui.clickgui;

import dev.v1zir.visuals.config.ConfigManager;
import dev.v1zir.visuals.gui.hud.Notifications;
import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.font.Icons;
import dev.v1zir.visuals.render.font.SdfFont;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * The config profiles, opened from the sidebar footer: a small menu rises over it listing them, the active one
 * checked. A click switches to a profile, the pencil renames it in place (Enter keeps the name, Escape drops it),
 * the bin deletes it after a second click and "New profile" saves a copy of the current settings and names it.
 * The files live in config/v1zir/configs (ConfigManager).
 */
final class ProfileMenu {
	private static final float ROW = 18f;
	private static final float LABEL = 14f;
	private static final float PAD = 4f;
	private static final long CONFIRM_NANOS = 2_500_000_000L;
	private static final int DANGER = 0xFFFF5C6C;

	private final Anim open = new Anim(0f, 16f);
	private boolean shown;
	private List<String> names = List.of();
	/** Hover per row, the last one for "New profile"; rebuilt when the list is. */
	private Anim[] hover = new Anim[0];
	private int renaming = -1;
	private String draft = "";
	private int confirming = -1;
	private long confirmUntil;
	// Layout of the last frame.
	private float x;
	private float y;
	private float w;
	private float h;

	boolean isOpen() {
		return shown;
	}

	void toggle() {
		shown = !shown;
		renaming = -1;
		confirming = -1;
		if (shown) refresh();
	}

	private void refresh() {
		names = ConfigManager.profiles();
		hover = new Anim[names.size() + 1];
		for (int i = 0; i < hover.length; i++) hover[i] = new Anim(0f, 22f);
	}

	/** Draws the menu rising from the footer: its bottom edge at bottom, left edge at left. */
	void render(DrawContext ctx, float left, float bottom, float width, double mx, double my) {
		float t = open.target(shown ? 1f : 0f).get();
		if (t < 0.01f) return;
		w = width;
		h = PAD * 2f + LABEL + ROW * (names.size() + 1);
		x = left;
		y = bottom - h - 4f;
		float alpha = Render2D.alpha();
		Render2D.setAlpha(alpha * t);
		float rise = (1f - t) * 6f;
		float top = y + rise;
		Render2D.shadow(ctx, x, top + 2f, w, h, Ui.PANEL_RADIUS, 14f, 0x70000000);
		Render2D.matte(ctx, x, top, w, h, Ui.PANEL_RADIUS, ColorUtil.withAlpha(ColorUtil.brighten(Theme.preset().base(), 0.09f), 248), Ui.LIT);
		Render2D.rim(ctx, x, top, w, h, Ui.PANEL_RADIUS, ColorUtil.withAlpha(0xFFFFFF, 26), Ui.LIT);
		Render2D.textMiddle(ctx, Fonts.semibold(), "PROFILES", x + 10f, top + PAD + LABEL / 2f, 5.5f, Theme.textFaint());

		SdfFont font = Fonts.medium();
		String active = ConfigManager.profile();
		boolean inside = t > 0.99f;
		long now = System.nanoTime();
		if (confirming >= 0 && now > confirmUntil) confirming = -1;
		for (int i = 0; i <= names.size(); i++) {
			float ry = top + PAD + LABEL + i * ROW;
			float mid = ry + ROW / 2f;
			boolean over = inside && Ui.inside(mx, my, x + 4f, ry, w - 8f, ROW);
			float hv = hover[i].target(over ? 1f : 0f).get();
			if (hv > 0.01f) Render2D.matte(ctx, x + 4f, ry + 1f, w - 8f, ROW - 2f, Ui.CELL_RADIUS, ColorUtil.withAlpha(0xFFFFFF, Math.round(12 * hv)), Ui.LIT);
			if (i == names.size()) {
				Render2D.icon(ctx, Icons.PLUS, x + 14f, mid, 7f, ColorUtil.lerp(Theme.textDim(), Theme.text(), hv));
				Render2D.textMiddle(ctx, font, "New profile", x + 24f, mid, Ui.SMALL, ColorUtil.lerp(Theme.textDim(), Theme.text(), hv));
				continue;
			}
			String name = names.get(i);
			boolean current = name.equals(active);
			if (current) Render2D.icon(ctx, Icons.CHECK, x + 14f, mid, 7f, Theme.accent());
			if (i == renaming) {
				Render2D.matte(ctx, x + 22f, ry + 2f, w - 30f, ROW - 4f, Ui.CELL_RADIUS, 0x40000000, 0);
				Render2D.rim(ctx, x + 22f, ry + 2f, w - 30f, ROW - 4f, Ui.CELL_RADIUS, ColorUtil.withAlpha(Theme.accent(), 190), 0);
				float tw = Render2D.textMiddle(ctx, font, draft, x + 26f, mid, Ui.SMALL, Theme.text());
				// A caret that breathes instead of blinking, like the search field's.
				float pulse = 0.5f + 0.5f * (float) Math.cos(now / 1.0e9 * Math.PI * 2.0);
				Render2D.round(ctx, x + 27f + tw, mid - 3.5f, 1f, 7f, 0.5f, ColorUtil.withAlpha(Theme.accent(), Math.round(255 * pulse)));
				continue;
			}
			if (i == confirming) {
				Render2D.textMiddle(ctx, font, "Click again to delete", x + 24f, mid, Ui.SMALL, DANGER);
			} else {
				Render2D.textMiddle(ctx, font, name, x + 24f, mid, Ui.SMALL, current ? Theme.text() : ColorUtil.lerp(Theme.textDim(), Theme.text(), hv));
			}
			if (hv > 0.01f || i == confirming) {
				Render2D.icon(ctx, Icons.PENCIL, x + w - 30f, mid, 6.5f, ColorUtil.fade(iconColor(mx, my, x + w - 30f, mid), hv));
				if (names.size() > 1) Render2D.icon(ctx, Icons.TRASH_2, x + w - 15f, mid, 6.5f, i == confirming ? DANGER : ColorUtil.fade(iconColor(mx, my, x + w - 15f, mid), hv));
			}
		}
		Render2D.setAlpha(alpha);
	}

	private static int iconColor(double mx, double my, float cx, float cy) {
		return Ui.inside(mx, my, cx - 6f, cy - 6f, 12f, 12f) ? Theme.text() : Theme.textFaint();
	}

	/** A click while it is open; any click goes to it (one outside closes it). */
	boolean mouseClicked(double mx, double my, int button) {
		if (!shown) return false;
		if (!Ui.inside(mx, my, x, y, w, h)) {
			shown = false;
			renaming = -1;
			return true;
		}
		if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;
		int i = (int) Math.floor((my - y - PAD - LABEL) / ROW);
		if (i < 0 || i > names.size() || i == renaming) return true;
		renaming = -1;
		if (i == names.size()) {
			// The copy is named right away: the new row opens for typing.
			ConfigManager.createProfile();
			refresh();
			startRename(names.indexOf(ConfigManager.profile()));
			return true;
		}
		String name = names.get(i);
		float mid = y + PAD + LABEL + i * ROW + ROW / 2f;
		if (Ui.inside(mx, my, x + w - 36f, mid - 6f, 12f, 12f)) {
			startRename(i);
		} else if (names.size() > 1 && Ui.inside(mx, my, x + w - 21f, mid - 6f, 12f, 12f)) {
			if (confirming == i) {
				ConfigManager.deleteProfile(name);
				refresh();
				confirming = -1;
			} else {
				confirming = i;
				confirmUntil = System.nanoTime() + CONFIRM_NANOS;
			}
		} else if (!name.equals(ConfigManager.profile())) {
			ConfigManager.switchProfile(name);
			shown = false;
			Notifications.push(Icons.FOLDER, "Profile loaded", name, true);
		}
		return true;
	}

	private void startRename(int index) {
		if (index < 0) return;
		renaming = index;
		draft = names.get(index);
		confirming = -1;
	}

	/** Typing a new name; returns whether the key was used. */
	boolean keyPressed(int key) {
		if (!shown) return false;
		if (renaming < 0) {
			if (key != GLFW.GLFW_KEY_ESCAPE) return false;
			shown = false;
			return true;
		}
		switch (key) {
			case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
				if (ConfigManager.renameProfile(names.get(renaming), draft.strip())) {
					renaming = -1;
					refresh();
				}
			}
			case GLFW.GLFW_KEY_ESCAPE -> renaming = -1;
			case GLFW.GLFW_KEY_BACKSPACE -> {
				if (!draft.isEmpty()) draft = draft.substring(0, draft.length() - 1);
			}
			default -> {
			}
		}
		return true;
	}

	/** Characters of a new name (letters, digits, spaces, dashes, underscores); returns whether it took them. */
	boolean charTyped(String text) {
		if (!shown || renaming < 0) return false;
		for (int i = 0; i < text.length() && draft.length() < 24; i++) {
			char c = text.charAt(i);
			if (Character.isLetterOrDigit(c) || c == ' ' || c == '-' || c == '_') draft += c;
		}
		return true;
	}
}
