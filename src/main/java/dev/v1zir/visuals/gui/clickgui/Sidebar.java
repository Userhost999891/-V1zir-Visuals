package dev.v1zir.visuals.gui.clickgui;

import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.font.SdfFont;
import dev.v1zir.visuals.render.ui.Heads;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.Arrays;
import java.util.List;

/**
 * Left column of the window: the tabs with icons from the top, Settings pinned at the bottom above the
 * profile footer, a soft accent pill that glides (spring) to the selected tab, and hover light on the
 * others.
 */
final class Sidebar {
	private static final Category[] TABS = Category.values();
	private static final float TAB_H = 28f;
	private static final float TAB_GAP = 4f;
	private static final float TABS_TOP = 8f;
	private static final float FOOTER = 36f;

	/** Top of the selection pill relative to the sidebar, so it can glide to the pinned Settings tab too. */
	private final Anim pill;
	private final Anim pillFade = new Anim(1f, 16f);
	private final Anim[] hover = new Anim[TABS.length];
	private final Label[] countWidth = new Label[TABS.length];
	/** Enabled-module count per tab, rebuilt only when it changes. */
	private final int[] counts = new int[TABS.length];
	private final String[] countText = new String[TABS.length];
	private final int[] enabled = new int[TABS.length];
	private int countedFor = -1;
	/** Player name cut to the footer width, rebuilt only when it changes. */
	private String shownName = "";
	private String trimmedName = "";
	private float nameMax;
	private float x;
	private float y;
	private float w;
	private float h;

	Sidebar(int selected) {
		pill = new Anim(TABS_TOP + selected * (TAB_H + TAB_GAP), 16f);
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
		this.h = h;
		int accent = Theme.accent();
		Render2D.matte(ctx, x, y, w, h, Ui.PANEL_RADIUS, Theme.raised(), Ui.LIT);
		Render2D.rim(ctx, x, y, w, h, Ui.PANEL_RADIUS, Ui.HAIRLINE, Ui.LIT);

		float tx = x + 8f;
		float tw = w - 16f;
		float slide = pill.target(tabY(selected)).get();
		float fade = pillFade.target(searching ? 0.35f : 1f).get();
		float py = y + slide;
		// The selection: a neutral plate with a short accent bar on its left edge, gliding between tabs.
		float alpha = Render2D.alpha();
		Render2D.setAlpha(alpha * fade);
		Render2D.matte(ctx, tx, py, tw, TAB_H, Ui.CELL_RADIUS + 2f, 0x17FFFFFF, Ui.LIT);
		Render2D.rim(ctx, tx, py, tw, TAB_H, Ui.CELL_RADIUS + 2f, Ui.HAIRLINE, Ui.LIT);
		Render2D.round(ctx, tx, py + 8f, 2f, TAB_H - 16f, 1f, accent);
		Render2D.setAlpha(alpha);

		SdfFont font = Fonts.medium();
		countEnabled();
		if (Category.SETTINGS.ordinal() < TABS.length) {
			// A hairline separates the pinned Settings tab from the others.
			Render2D.rect(ctx, tx + 4f, y + tabY(Category.SETTINGS.ordinal()) - TAB_GAP - 1f, tw - 8f, 1f, Ui.HAIRLINE);
		}
		for (int i = 0; i < TABS.length; i++) {
			float ry = y + tabY(i);
			boolean active = i == selected && !searching;
			// A tab brightens while the selection glides over it.
			float near = Math.max(0f, 1f - Math.abs(slide - tabY(i)) / (TAB_H + TAB_GAP)) * fade;
			float hv = hover[i].target(!active && Ui.inside(mx, my, tx, ry, tw, TAB_H) ? 1f : 0f).get();
			if (hv > 0.01f) Render2D.matte(ctx, tx, ry, tw, TAB_H, Ui.CELL_RADIUS + 2f, ColorUtil.withAlpha(0xFFFFFF, Math.round(9 * hv)), Ui.LIT);
			float light = Math.max(near, hv * 0.7f);
			int iconColor = ColorUtil.lerp(ColorUtil.lerp(Theme.textDim(), Theme.text(), hv), accent, near);
			Render2D.icon(ctx, TABS[i].icon(), tx + 14f, ry + TAB_H / 2f, Ui.ICON, iconColor);
			Render2D.textMiddle(ctx, font, TABS[i].title(), tx + 26f, ry + TAB_H / 2f, 7.5f, ColorUtil.lerp(Theme.textDim(), Theme.text(), light));
			if (enabled[i] > 0 && TABS[i] != Category.SETTINGS) {
				if (enabled[i] != counts[i]) {
					counts[i] = enabled[i];
					countText[i] = Integer.toString(enabled[i]);
				}
				float cw = countWidth[i].width(Fonts.medium(), countText[i], 6f);
				Render2D.textMiddle(ctx, Fonts.medium(), countText[i], tx + tw - 8f - cw, ry + TAB_H / 2f, 6f, Theme.textFaint());
			}
		}
		renderFooter(ctx, x, y + h - FOOTER, w);
	}

	/** Counts enabled (toggleable) modules per tab in one pass over the module list, only after a module was toggled. */
	private void countEnabled() {
		if (countedFor == ModuleManager.version()) return;
		countedFor = ModuleManager.version();
		Arrays.fill(enabled, 0);
		List<Module> modules = ModuleManager.all();
		for (int i = 0; i < modules.size(); i++) {
			Module module = modules.get(i);
			if (module.isEnabled() && module.canToggle()) enabled[module.category().ordinal()]++;
		}
	}

	/** Player head (rounded, fading with the window) and name over the config name; only drawn in a world. */
	private void renderFooter(DrawContext ctx, float fx, float fy, float fw) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null) return;
		Render2D.rect(ctx, fx + 8f, fy, fw - 16f, 1f, Ui.HAIRLINE);
		float head = 16f;
		float hx = fx + 12f;
		float hy = fy + (FOOTER - head) / 2f;
		Heads.skin(ctx, mc.player.getSkin(), hx, hy, head, Ui.CELL_RADIUS, 0xFFFFFFFF);
		Render2D.outline(ctx, hx, hy, head, head, Ui.CELL_RADIUS, 1f, ColorUtil.withAlpha(0xFFFFFF, 30));
		String name = mc.getSession().getUsername();
		float maxName = fw - 44f;
		if (!name.equals(shownName) || maxName != nameMax) {
			shownName = name;
			nameMax = maxName;
			trimmedName = Fonts.semibold().trim(name, 7f, maxName);
		}
		Render2D.textMiddle(ctx, Fonts.semibold(), trimmedName, hx + head + 8f, hy + 4f, 7f, Theme.text());
		Render2D.textMiddle(ctx, Fonts.regular(), "Default config", hx + head + 8f, hy + 12f, 6f, Theme.textDim());
	}

	/** The tab under the mouse, or -1. */
	int tabAt(double mx, double my) {
		float tx = x + 8f;
		float tw = w - 16f;
		for (int i = 0; i < TABS.length; i++) {
			if (Ui.inside(mx, my, tx, y + tabY(i), tw, TAB_H)) return i;
		}
		return -1;
	}

	/** Top of a tab relative to the sidebar: listed from the top, Settings just above the footer. */
	private float tabY(int index) {
		if (TABS[index] == Category.SETTINGS) return h - FOOTER - 8f - TAB_H;
		return TABS_TOP + index * (TAB_H + TAB_GAP);
	}
}
