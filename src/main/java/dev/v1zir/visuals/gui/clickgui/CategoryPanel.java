package dev.v1zir.visuals.gui.clickgui;

import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** A draggable glass panel listing the modules of one category. */
public final class CategoryPanel {
	final Category category;
	final List<ModuleRow> rows = new ArrayList<>();
	float x;
	float y;
	private boolean dragging;
	private double dragX;
	private double dragY;
	private float scroll;
	private final Anim scrollAnim = new Anim(0f, 16f);
	private float contentHeight;
	private float visibleHeight;
	private List<ModuleRow> visibleRows = new ArrayList<>();

	CategoryPanel(Category category, float x, float y) {
		this.category = category;
		this.x = x;
		this.y = y;
		for (Module module : ModuleManager.byCategory(category)) rows.add(new ModuleRow(module));
	}

	boolean matches(String search) {
		return !filtered(search).isEmpty();
	}

	private List<ModuleRow> filtered(String search) {
		if (search.isEmpty()) return rows;
		String q = search.toLowerCase(Locale.ROOT);
		List<ModuleRow> list = new ArrayList<>();
		for (ModuleRow row : rows) if (row.module.name().toLowerCase(Locale.ROOT).contains(q)) list.add(row);
		return list;
	}

	void render(DrawContext ctx, double mouseX, double mouseY, String search, int screenHeight) {
		visibleRows = filtered(search);
		float w = Ui.PANEL_WIDTH;
		float content = 0f;
		for (ModuleRow row : visibleRows) content += row.height();
		contentHeight = content;
		float maxBody = Math.max(60f, screenHeight - y - Ui.HEADER_HEIGHT - 16f);
		visibleHeight = Math.min(content + 6f, maxBody);
		float h = Ui.HEADER_HEIGHT + visibleHeight;
		float maxScroll = Math.max(0f, content + 6f - visibleHeight);
		scroll = Math.max(0f, Math.min(scroll, maxScroll));
		float sc = scrollAnim.target(scroll).get();

		int accent = Theme.accent();
		Render2D.shadow(ctx, x, y + 2f, w, h, Ui.RADIUS, 14f, 0x70000000);
		Render2D.panel(ctx, x, y, w, h, Ui.RADIUS);

		// Header: icon + title, accent hairline underneath.
		Render2D.round(ctx, x, y, w, Ui.HEADER_HEIGHT, Ui.RADIUS, Render2D.CORNERS_TOP, ColorUtil.withAlpha(0xFFFFFF, 7));
		float titleW = Fonts.semibold().width(category.title(), 8.5f);
		float iconSize = 9.5f;
		float groupW = iconSize + 5f + titleW;
		float gx = x + w / 2f - groupW / 2f;
		Render2D.icon(ctx, category.icon(), gx + iconSize / 2f, y + Ui.HEADER_HEIGHT / 2f, iconSize, accent);
		Render2D.textMiddle(ctx, Fonts.semibold(), category.title(), gx + iconSize + 5f, y + Ui.HEADER_HEIGHT / 2f, 8.5f, Theme.text());
		long enabled = rows.stream().filter(r -> r.module.isEnabled() && r.module.canToggle()).count();
		if (enabled > 0) {
			String count = String.valueOf(enabled);
			float cw = Fonts.semibold().width(count, 5.5f) + 6f;
			Render2D.round(ctx, x + w - 8f - cw, y + Ui.HEADER_HEIGHT / 2f - 4.5f, cw, 9f, 4.5f, ColorUtil.withAlpha(accent, 60));
			Render2D.textMiddle(ctx, Fonts.semibold(), count, x + w - 8f - cw + 3f, y + Ui.HEADER_HEIGHT / 2f, 5.5f, Theme.text());
		}
		Render2D.gradientH(ctx, x + 8f, y + Ui.HEADER_HEIGHT - 0.5f, w / 2f - 8f, 1f, 0.5f, ColorUtil.withAlpha(accent, 0), ColorUtil.withAlpha(accent, 170));
		Render2D.gradientH(ctx, x + w / 2f, y + Ui.HEADER_HEIGHT - 0.5f, w / 2f - 8f, 1f, 0.5f, ColorUtil.withAlpha(Theme.accent2(), 170), ColorUtil.withAlpha(Theme.accent2(), 0));

		float bodyY = y + Ui.HEADER_HEIGHT;
		ctx.enableScissor((int) Math.floor(x), (int) Math.floor(bodyY), (int) Math.ceil(x + w), (int) Math.ceil(bodyY + visibleHeight));
		float cy = bodyY + 3f - sc;
		boolean mouseInBody = Ui.inside(mouseX, mouseY, x, bodyY, w, visibleHeight);
		for (ModuleRow row : visibleRows) {
			cy += row.render(ctx, x, cy, w, mouseInBody ? mouseX : -1000, mouseInBody ? mouseY : -1000);
		}
		ctx.disableScissor();
		if (maxScroll > 0f) {
			float trackH = visibleHeight - 8f;
			float thumbH = Math.max(14f, trackH * visibleHeight / (content + 6f));
			float thumbY = bodyY + 4f + (trackH - thumbH) * (sc / maxScroll);
			Render2D.round(ctx, x + w - 3.5f, thumbY, 2f, thumbH, 1f, ColorUtil.withAlpha(0xFFFFFF, 50));
		}
	}

	float height() {
		return Ui.HEADER_HEIGHT + visibleHeight;
	}

	boolean overHeader(double mx, double my) {
		return Ui.inside(mx, my, x, y, Ui.PANEL_WIDTH, Ui.HEADER_HEIGHT);
	}

	boolean over(double mx, double my) {
		return Ui.inside(mx, my, x, y, Ui.PANEL_WIDTH, height());
	}

	boolean mouseClicked(double mx, double my, int button) {
		if (overHeader(mx, my) && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			dragging = true;
			dragX = mx - x;
			dragY = my - y;
			return true;
		}
		if (!Ui.inside(mx, my, x, y + Ui.HEADER_HEIGHT, Ui.PANEL_WIDTH, visibleHeight)) return false;
		for (ModuleRow row : visibleRows) if (row.mouseClicked(mx, my, button)) return true;
		return true;
	}

	void mouseDragged(double mx, double my, int screenWidth, int screenHeight) {
		if (dragging) {
			x = (float) Math.max(0, Math.min(screenWidth - Ui.PANEL_WIDTH, mx - dragX));
			y = (float) Math.max(24, Math.min(screenHeight - Ui.HEADER_HEIGHT, my - dragY));
			return;
		}
		for (ModuleRow row : visibleRows) row.mouseDragged(mx, my);
	}

	void mouseReleased() {
		dragging = false;
		for (ModuleRow row : rows) row.mouseReleased();
	}

	boolean mouseScrolled(double mx, double my, double amount) {
		if (!over(mx, my)) return false;
		scroll -= (float) amount * 18f;
		return true;
	}

	boolean keyPressed(int key) {
		for (ModuleRow row : rows) if (row.keyPressed(key)) return true;
		return false;
	}

	boolean isBinding() {
		for (ModuleRow row : rows) if (row.isBinding()) return true;
		return false;
	}
}
