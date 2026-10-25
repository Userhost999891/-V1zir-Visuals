package dev.v1zir.visuals.gui.clickgui;

import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.font.SdfFont;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.PlayerListEntry;

import java.util.Arrays;
import java.util.List;

/**
 * Left column of the window: the five tabs with icons, a soft accent pill that glides (spring) to the
 * selected tab, hover light on the others, and a small profile footer at the bottom.
 */
final class Sidebar {
	private static final Category[] TABS = Category.values();
	private static final float TAB_H = 24f;
	private static final float TAB_GAP = 3f;
	private static final float TABS_TOP = 22f;
	private static final float FOOTER = 34f;
	private static final Render2D.TextStyle CAPTION = new Render2D.TextStyle().spacing(0.8f);

	private final Anim pill;
	private final Anim pillFade = new Anim(1f, 16f);
	private final Anim[] hover = new Anim[TABS.length];
	private final Label[] countWidth = new Label[TABS.length];
	/** Enabled-module count per tab, rebuilt only when it changes. */
	private final int[] counts = new int[TABS.length];
	private final String[] countText = new String[TABS.length];
	private final int[] enabled = new int[TABS.length];
	/** Player name cut to the footer width, rebuilt only when it changes. */
	private String shownName = "";
	private String trimmedName = "";
	private float nameMax;
	private float x;
	private float y;
	private float w;

	Sidebar(int selected) {
		pill = new Anim(selected, 16f);
		for (int i = 0; i < TABS.length; i++) {
			hover[i] = new Anim(0f, 20f);
			countWidth[i] = new Label();
			counts[i] = -1;
		}
	}

	static float width(float windowW) {
		return Math.max(112f, Math.min(150f, windowW * 0.24f));
	}

	/** selected is the tab's ordinal; searching dims the pill, since results span every tab. */
	void render(DrawContext ctx, float x, float y, float w, float h, int selected, boolean searching, double mx, double my) {
		this.x = x;
		this.y = y;
		this.w = w;
		int accent = Theme.accent();
		Render2D.matte(ctx, x, y, w, h, 8f, Theme.raised(), Ui.LIT);
		Render2D.rim(ctx, x, y, w, h, 8f, ColorUtil.withAlpha(0xFFFFFF, 16), Ui.LIT);
		CAPTION.color = Theme.textFaint();
		Render2D.text(ctx, Fonts.semibold(), "MENU", x + 12f, y + 9f, 5.5f, CAPTION);

		float tx = x + 6f;
		float tw = w - 12f;
		float slide = pill.target(selected).get();
		float fade = pillFade.target(searching ? 0.35f : 1f).get();
		float py = y + TABS_TOP + slide * (TAB_H + TAB_GAP);
		float alpha = Render2D.alpha();
		Render2D.setAlpha(alpha * fade);
		Render2D.shadow(ctx, tx, py, tw, TAB_H, 7f, 9f, ColorUtil.withAlpha(accent, 40));
		Render2D.matte(ctx, tx, py, tw, TAB_H, 7f, ColorUtil.withAlpha(ColorUtil.lerp(0xFFFFFF, accent, 0.5f), 30), Ui.LIT);
		Render2D.rim(ctx, tx, py, tw, TAB_H, 7f, ColorUtil.withAlpha(ColorUtil.lerp(0xFFFFFF, accent, 0.5f), 70), Ui.LIT);
		// An accent notch on the left edge of the pill, glowing softly.
		Render2D.shadow(ctx, tx + 1f, py + 7f, 2.5f, TAB_H - 14f, 1.25f, 4f, ColorUtil.withAlpha(accent, 160));
		Render2D.gradientV(ctx, tx + 1f, py + 7f, 2.5f, TAB_H - 14f, 1.25f, accent, Theme.accent2());
		Render2D.setAlpha(alpha);

		SdfFont font = Fonts.semibold();
		countEnabled();
		for (int i = 0; i < TABS.length; i++) {
			float ry = y + TABS_TOP + i * (TAB_H + TAB_GAP);
			boolean active = i == selected && !searching;
			// The pill's own light as it passes: a tab brightens while the pill glides over it.
			float near = Math.max(0f, 1f - Math.abs(slide - i)) * fade;
			float hv = hover[i].target(!active && Ui.inside(mx, my, tx, ry, tw, TAB_H) ? 1f : 0f).get();
			if (hv > 0.01f) Render2D.matte(ctx, tx, ry, tw, TAB_H, 7f, ColorUtil.withAlpha(0xFFFFFF, Math.round(12 * hv)), Ui.LIT);
			float light = Math.max(near, hv * 0.7f);
			int iconColor = ColorUtil.lerp(Theme.textDim(), ColorUtil.lerp(accent, 0xFFFFFFFF, 0.25f), near);
			int textColor = ColorUtil.lerp(Theme.textDim(), 0xFFFFFFFF, light);
			Render2D.icon(ctx, TABS[i].icon(), tx + 13f, ry + TAB_H / 2f, 8.5f, ColorUtil.lerp(iconColor, Theme.text(), hv * (1f - near)));
			Render2D.textMiddle(ctx, font, TABS[i].title(), tx + 24f, ry + TAB_H / 2f, 7.5f, textColor);
			if (enabled[i] > 0 && TABS[i] != Category.SETTINGS) {
				if (enabled[i] != counts[i]) {
					counts[i] = enabled[i];
					countText[i] = Integer.toString(enabled[i]);
				}
				float cw = countWidth[i].width(Fonts.medium(), countText[i], 5.5f);
				Render2D.textMiddle(ctx, Fonts.medium(), countText[i], tx + tw - 9f - cw, ry + TAB_H / 2f, 5.5f,
					ColorUtil.withAlpha(ColorUtil.lerp(Theme.textFaint(), accent, near), Math.round(170 + 85 * light)));
			}
		}
		renderFooter(ctx, x, y + h - FOOTER, w);
	}

	/** Counts enabled (toggleable) modules per tab in one pass over the module list. */
	private void countEnabled() {
		Arrays.fill(enabled, 0);
		List<Module> modules = ModuleManager.all();
		for (int i = 0; i < modules.size(); i++) {
			Module module = modules.get(i);
			if (module.isEnabled() && module.canToggle()) enabled[module.category().ordinal()]++;
		}
	}

	/** Player head and name over the config name; only drawn in a world with a player list entry. */
	private void renderFooter(DrawContext ctx, float fx, float fy, float fw) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null || mc.getNetworkHandler() == null) return;
		PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
		if (entry == null) return;
		Render2D.gradientH(ctx, fx + 10f, fy, fw - 20f, 1f, 0.5f, 0x00FFFFFF, 0x16FFFFFF);
		float head = 16f;
		float hx = fx + 11f;
		float hy = fy + (FOOTER - head) / 2f + 0.5f;
		Render2D.shadow(ctx, hx, hy, head, head, 4f, 6f, ColorUtil.withAlpha(Theme.accent(), 60));
		PlayerSkinDrawer.draw(ctx, entry.getSkinTextures(), (int) hx, (int) hy, (int) head, ColorUtil.fade(0xFFFFFFFF, Render2D.alpha()));
		String name = mc.getSession().getUsername();
		float maxName = fw - 40f;
		if (!name.equals(shownName) || maxName != nameMax) {
			shownName = name;
			nameMax = maxName;
			trimmedName = Fonts.semibold().trim(name, 7f, maxName);
		}
		Render2D.text(ctx, Fonts.semibold(), trimmedName, hx + head + 6f, hy + 0.5f, 7f, Theme.text());
		Render2D.text(ctx, Fonts.regular(), "Default config", hx + head + 6f, hy + 9.5f, 5.5f, Theme.textFaint());
	}

	/** The tab under the mouse, or -1. */
	int tabAt(double mx, double my) {
		float tx = x + 6f;
		float tw = w - 12f;
		for (int i = 0; i < TABS.length; i++) {
			if (Ui.inside(mx, my, tx, y + TABS_TOP + i * (TAB_H + TAB_GAP), tw, TAB_H)) return i;
		}
		return -1;
	}
}
