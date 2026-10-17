package dev.v1zir.visuals.gui.clickgui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.v1zir.visuals.V1zir;
import dev.v1zir.visuals.config.ConfigManager;
import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.impl.client.ClickGuiModule;
import dev.v1zir.visuals.module.impl.client.ThemeModule;
import dev.v1zir.visuals.module.impl.hud.HudModule;
import dev.v1zir.visuals.module.setting.Setting;
import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.font.Icons;
import dev.v1zir.visuals.render.font.SdfFont;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The click GUI: category panels on frosted glass, a tab bar (Modules / Hud / Themes) and search.
 * Layout is inspired by CatLean and Nursultan: centred panels, inline settings, theme editor.
 */
public final class ClickGuiScreen extends Screen {
	private static final String[] TABS = {"Modules", "Hud", "Themes"};
	private static final int[] TAB_ICONS = {Icons.LAYOUT_GRID, Icons.LAYOUT_DASHBOARD, Icons.PALETTE};
	private static final Map<Category, float[]> POSITIONS = new EnumMap<>(Category.class);
	private static int tab;

	private final List<CategoryPanel> panels = new ArrayList<>();
	private final Anim open = new Anim(0f, 9f);
	private final Anim tabSlide = new Anim(tab, 14f);
	private final List<SettingComponent<?>> themeComponents = new ArrayList<>();
	private String search = "";
	private boolean searchFocused;
	private boolean closing;
	private HudModule draggingHud;
	private double hudDragX;
	private double hudDragY;
	private float[] tabBounds = new float[0];
	private float[] searchBounds = new float[4];
	private final List<float[]> presetCards = new ArrayList<>();

	public ClickGuiScreen() {
		super(Text.literal("V1zir Visuals"));
		ThemeModule theme = ModuleManager.get(ThemeModule.class);
		if (theme != null) {
			for (Setting<?> setting : theme.settings()) {
				if (setting != theme.preset) themeComponents.add(SettingComponent.of(setting));
			}
		}
	}

	public boolean isHudTab() {
		return tab == 1;
	}

	@Override
	protected void init() {
		panels.clear();
		Category[] categories = Category.values();
		float total = categories.length * Ui.PANEL_WIDTH + (categories.length - 1) * 10f;
		float startX = width / 2f - total / 2f;
		for (int i = 0; i < categories.length; i++) {
			float[] pos = POSITIONS.get(categories[i]);
			if (pos == null) pos = new float[]{startX + i * (Ui.PANEL_WIDTH + 10f), 48f};
			pos[0] = Math.max(0, Math.min(width - Ui.PANEL_WIDTH, pos[0]));
			pos[1] = Math.max(30, Math.min(height - Ui.HEADER_HEIGHT, pos[1]));
			panels.add(new CategoryPanel(categories[i], pos[0], pos[1]));
		}
	}

	private float scale() {
		ClickGuiModule gui = ModuleManager.get(ClickGuiModule.class);
		float base = gui != null ? gui.scale.f() : 1f;
		boolean animations = gui == null || gui.animations.on();
		float t = animations ? Anim.easeOutCubic(open.value()) : 1f;
		return base * (0.94f + 0.06f * t);
	}

	private double toGuiX(double mx) {
		float s = scale();
		return (mx - width / 2.0) / s + width / 2.0;
	}

	private double toGuiY(double my) {
		float s = scale();
		return (my - height / 2.0) / s + height / 2.0;
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float deltaTicks) {
		ThemeModule theme = ModuleManager.get(ThemeModule.class);
		if (theme != null) theme.apply();
		ClickGuiModule gui = ModuleManager.get(ClickGuiModule.class);
		boolean animations = gui == null || gui.animations.on();
		float t = open.target(closing ? 0f : 1f).get();
		if (closing && t < 0.03f) {
			super.close();
			return;
		}
		float alpha = animations ? Anim.easeOutCubic(t) : 1f;

		// Background: blurred, dimmed world (not on the HUD tab, where the HUD must stay visible).
		if (!isHudTab() && (gui == null || gui.dimBackground.on())) {
			Render2D.setAlpha(alpha);
			if (client != null && client.world != null) Render2D.glass(ctx, 0, 0, width, height, 0, 0x48000000);
			else Render2D.rect(ctx, 0, 0, width, height, 0xB0000000);
		}

		double gx = toGuiX(mouseX);
		double gy = toGuiY(mouseY);
		float s = scale();
		ctx.getMatrices().pushMatrix();
		ctx.getMatrices().translate(width / 2f, height / 2f);
		ctx.getMatrices().scale(s, s);
		ctx.getMatrices().translate(-width / 2f, -height / 2f);
		Render2D.setAlpha(alpha);

		renderTabBar(ctx, gx, gy);
		switch (tab) {
			case 1 -> renderHudEditor(ctx, gx, gy);
			case 2 -> renderThemes(ctx, gx, gy);
			default -> {
				for (CategoryPanel panel : panels) {
					if (!search.isEmpty() && !panel.matches(search)) continue;
					panel.render(ctx, gx, gy, search, height);
				}
			}
		}
		SdfFont font = Fonts.medium();
		Render2D.text(ctx, font, V1zir.NAME + "  " + V1zir.version(), 8f, height - 8f - font.lineHeight(6.5f), 6.5f, ColorUtil.withAlpha(0xFFFFFF, 90));
		String hint = tab == 0 ? "Left click: toggle  ·  Right click: settings  ·  Middle click: bind"
			: tab == 1 ? "Drag elements to move them  ·  Right click to toggle" : "Pick a preset or tune every colour";
		float hw = font.width(hint, 6.5f);
		Render2D.text(ctx, font, hint, width / 2f - hw / 2f, height - 8f - font.lineHeight(6.5f), 6.5f, ColorUtil.withAlpha(0xFFFFFF, 110));
		ctx.getMatrices().popMatrix();
		Render2D.setAlpha(1f);
	}

	// ------------------------------------------------------------------ tab bar & search

	private void renderTabBar(DrawContext ctx, double mx, double my) {
		SdfFont font = Fonts.medium();
		float size = 7.5f;
		float h = 20f;
		float y = 14f;
		float pad = 3f;
		float[] widths = new float[TABS.length];
		float total = pad;
		for (int i = 0; i < TABS.length; i++) {
			widths[i] = font.width(TABS[i], size) + 26f;
			total += widths[i];
		}
		float searchW = 92f;
		total += pad + 6f + searchW + pad;
		float x = width / 2f - total / 2f;
		Render2D.shadow(ctx, x, y + 2f, total, h, h / 2f, 12f, 0x66000000);
		Render2D.glass(ctx, x, y, total, h, h / 2f, Theme.panel());
		Render2D.outline(ctx, x, y, total, h, h / 2f, 1f, Theme.outline());

		float slide = tabSlide.target(tab).get();
		float cx = x + pad;
		float[] starts = new float[TABS.length];
		for (int i = 0; i < TABS.length; i++) {
			starts[i] = cx;
			cx += widths[i];
		}
		int lo = Math.max(0, Math.min(TABS.length - 1, (int) Math.floor(slide)));
		int hi = Math.min(TABS.length - 1, lo + 1);
		float frac = slide - lo;
		float selX = starts[lo] + (starts[hi] - starts[lo]) * frac;
		float selW = widths[lo] + (widths[hi] - widths[lo]) * frac;
		Render2D.shadow(ctx, selX, y + pad, selW, h - pad * 2, (h - pad * 2) / 2f, 6f, ColorUtil.withAlpha(Theme.accent(), 90));
		Render2D.round(ctx, selX, y + pad, selW, h - pad * 2, (h - pad * 2) / 2f, Theme.accent(), Theme.accent2(), Theme.accent2(), Theme.accent());

		tabBounds = new float[TABS.length * 3];
		for (int i = 0; i < TABS.length; i++) {
			boolean selected = i == tab;
			boolean hover = Ui.inside(mx, my, starts[i], y, widths[i], h);
			int color = selected ? 0xFFFFFFFF : hover ? Theme.text() : Theme.textDim();
			Render2D.icon(ctx, TAB_ICONS[i], starts[i] + 11f, y + h / 2f, 8f, color);
			Render2D.textMiddle(ctx, font, TABS[i], starts[i] + 18f, y + h / 2f, size, color);
			tabBounds[i * 3] = starts[i];
			tabBounds[i * 3 + 1] = y;
			tabBounds[i * 3 + 2] = widths[i];
		}

		float sx = cx + 6f;
		Render2D.rect(ctx, sx - 3.5f, y + 5f, 1f, h - 10f, ColorUtil.withAlpha(0xFFFFFF, 30));
		Render2D.round(ctx, sx, y + pad, searchW, h - pad * 2, (h - pad * 2) / 2f, ColorUtil.withAlpha(0xFFFFFF, searchFocused ? 22 : 10));
		if (searchFocused) Render2D.outline(ctx, sx, y + pad, searchW, h - pad * 2, (h - pad * 2) / 2f, 1f, ColorUtil.withAlpha(Theme.accent(), 160));
		Render2D.icon(ctx, Icons.SEARCH, sx + 9f, y + h / 2f, 7.5f, Theme.textDim());
		boolean caret = searchFocused && (System.currentTimeMillis() / 500L) % 2L == 0L;
		String shown = search.isEmpty() && !searchFocused ? "Search..." : font.trim(search, 7f, searchW - 26f) + (caret ? "|" : "");
		Render2D.textMiddle(ctx, font, shown, sx + 17f, y + h / 2f, 7f, search.isEmpty() && !searchFocused ? Theme.textFaint() : Theme.text());
		searchBounds = new float[]{sx, y, searchW, h};
	}

	// ------------------------------------------------------------------ HUD editor

	private void renderHudEditor(DrawContext ctx, double mx, double my) {
		for (Module module : ModuleManager.all()) {
			if (!(module instanceof HudModule hud)) continue;
			float x = hud.x(width);
			float y = hud.y(height);
			boolean hover = Ui.inside(mx, my, x - 2, y - 2, hud.width() + 4, hud.height() + 4) || draggingHud == hud;
			int color = hud.isEnabled() ? Theme.accent() : 0xFF6B7080;
			Render2D.round(ctx, x - 2, y - 2, hud.width() + 4, hud.height() + 4, 5f, ColorUtil.withAlpha(color, hover ? 40 : 16));
			Render2D.outline(ctx, x - 2, y - 2, hud.width() + 4, hud.height() + 4, 5f, 1f, ColorUtil.withAlpha(color, hover ? 220 : 110));
			if (hover || !hud.isEnabled()) {
				SdfFont font = Fonts.semibold();
				String label = hud.name() + (hud.isEnabled() ? "" : " (off)");
				float lw = font.width(label, 6.5f) + 8f;
				float ly = y - 14f < 36f ? y + hud.height() + 4f : y - 14f;
				Render2D.round(ctx, x - 2, ly, lw, 11f, 3f, ColorUtil.withAlpha(color, 210));
				Render2D.textMiddle(ctx, font, label, x + 2, ly + 5.5f, 6.5f, 0xFFFFFFFF);
			}
		}
	}

	// ------------------------------------------------------------------ theme editor

	private void renderThemes(DrawContext ctx, double mx, double my) {
		ThemeModule theme = ModuleManager.get(ThemeModule.class);
		if (theme == null) return;
		float w = 360f;
		float x = width / 2f - w / 2f;
		float y = 48f;
		Theme.Preset[] presets = Theme.Preset.values();
		int cols = 3;
		float gap = 6f;
		float cardW = (w - 16f - gap * (cols - 1)) / cols;
		float cardH = 30f;
		int rows = (presets.length + cols - 1) / cols;
		float gridH = rows * cardH + (rows - 1) * gap;

		float leftH = 0f;
		float rightH = 0f;
		int column = 0;
		for (SettingComponent<?> c : themeComponents) {
			if (!c.visible()) continue;
			if (column++ % 2 == 0) leftH += estimate(c) + 4f;
			else rightH += estimate(c) + 4f;
		}
		float settingsH = Math.max(leftH, rightH);
		float h = 24f + 8f + gridH + 12f + settingsH + 8f;
		h = Math.min(h, height - y - 20f);

		Render2D.shadow(ctx, x, y + 2f, w, h, Ui.RADIUS, 16f, 0x70000000);
		Render2D.glass(ctx, x, y, w, h, Ui.RADIUS, Theme.panel());
		Render2D.outline(ctx, x, y, w, h, Ui.RADIUS, 1f, Theme.outline());
		Render2D.round(ctx, x, y, w, 24f, Ui.RADIUS, Render2D.CORNERS_TOP, ColorUtil.withAlpha(0xFFFFFF, 7));
		Render2D.icon(ctx, Icons.SWATCH_BOOK, x + 14f, y + 12f, 9.5f, Theme.accent());
		Render2D.textMiddle(ctx, Fonts.semibold(), "Theme Editor", x + 24f, y + 12f, 8.5f, Theme.text());

		presetCards.clear();
		float gy = y + 32f;
		for (int i = 0; i < presets.length; i++) {
			Theme.Preset p = presets[i];
			float cx = x + 8f + (i % cols) * (cardW + gap);
			float cy = gy + (i / cols) * (cardH + gap);
			boolean selected = theme.preset.is(p.title());
			boolean hover = Ui.inside(mx, my, cx, cy, cardW, cardH);
			int a1 = p == Theme.Preset.CUSTOM ? theme.accent.argb() : p.accent();
			int a2 = p == Theme.Preset.CUSTOM ? theme.accent2.argb() : p.accent2();
			Render2D.round(ctx, cx, cy, cardW, cardH, 5f, ColorUtil.withAlpha(p.base(), 230));
			Render2D.round(ctx, cx, cy, cardW, cardH, 5f, ColorUtil.withAlpha(a1, hover ? 46 : 26), 0x00000000, 0x00000000, ColorUtil.withAlpha(a2, hover ? 40 : 20));
			Render2D.outline(ctx, cx, cy, cardW, cardH, 5f, selected ? 1.4f : 1f, selected ? a1 : ColorUtil.withAlpha(0xFFFFFF, hover ? 50 : 22));
			Render2D.text(ctx, Fonts.semibold(), p.title(), cx + 7f, cy + 5f, 7f, 0xFFEDEFF5);
			Render2D.gradientH(ctx, cx + 7f, cy + cardH - 9f, cardW - 14f, 3f, 1.5f, a1, a2);
			if (selected) Render2D.icon(ctx, Icons.CHECK, cx + cardW - 9f, cy + 9f, 7f, a1);
			presetCards.add(new float[]{cx, cy, cardW, cardH, i});
		}

		float sy = gy + gridH + 10f;
		Render2D.gradientH(ctx, x + 8f, sy - 4f, w - 16f, 1f, 0.5f, ColorUtil.withAlpha(Theme.accent(), 120), ColorUtil.withAlpha(Theme.accent2(), 0));
		ctx.enableScissor((int) x, (int) sy - 2, (int) (x + w), (int) (y + h));
		float colW = (w - 16f - 12f) / 2f;
		float leftY = sy;
		float rightY = sy;
		int index = 0;
		for (SettingComponent<?> c : themeComponents) {
			if (!c.visible()) continue;
			boolean left = index % 2 == 0;
			float cx = left ? x + 8f : x + 8f + colW + 12f;
			float cy = left ? leftY : rightY;
			float used = c.render(ctx, cx, cy, colW, mx, my) + 4f;
			if (left) leftY += used;
			else rightY += used;
			index++;
		}
		ctx.disableScissor();
	}

	private static float estimate(SettingComponent<?> component) {
		if (component instanceof SliderComponent) return 21f;
		if (component instanceof ModeComponent) return 26f;
		return 15f;
	}

	// ------------------------------------------------------------------ input

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		double mx = toGuiX(click.x());
		double my = toGuiY(click.y());
		int button = click.button();

		for (int i = 0; i < TABS.length && i * 3 + 2 < tabBounds.length; i++) {
			if (Ui.inside(mx, my, tabBounds[i * 3], tabBounds[i * 3 + 1], tabBounds[i * 3 + 2], 20f)) {
				tab = i;
				return true;
			}
		}
		searchFocused = Ui.inside(mx, my, searchBounds[0], searchBounds[1], searchBounds[2], searchBounds[3]);
		if (searchFocused) {
			tab = 0;
			return true;
		}

		if (tab == 1) {
			for (Module module : ModuleManager.all()) {
				if (!(module instanceof HudModule hud)) continue;
				float x = hud.x(width);
				float y = hud.y(height);
				if (Ui.inside(mx, my, x - 2, y - 2, hud.width() + 4, hud.height() + 4)) {
					if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) hud.toggle();
					else {
						draggingHud = hud;
						hudDragX = mx - x;
						hudDragY = my - y;
					}
					return true;
				}
			}
			return false;
		}
		if (tab == 2) {
			ThemeModule theme = ModuleManager.get(ThemeModule.class);
			for (float[] card : presetCards) {
				if (theme != null && Ui.inside(mx, my, card[0], card[1], card[2], card[3])) {
					theme.preset.set(Theme.Preset.values()[(int) card[4]].title());
					return true;
				}
			}
			for (SettingComponent<?> c : themeComponents) if (c.visible() && c.mouseClicked(mx, my, button)) return true;
			return false;
		}
		for (int i = panels.size() - 1; i >= 0; i--) {
			CategoryPanel panel = panels.get(i);
			if (!search.isEmpty() && !panel.matches(search)) continue;
			if (panel.over(mx, my) && panel.mouseClicked(mx, my, button)) {
				// Bring the clicked panel to the front.
				panels.remove(i);
				panels.add(panel);
				return true;
			}
		}
		return super.mouseClicked(click, doubled);
	}

	@Override
	public boolean mouseDragged(Click click, double offsetX, double offsetY) {
		double mx = toGuiX(click.x());
		double my = toGuiY(click.y());
		if (draggingHud != null) {
			draggingHud.moveTo((float) (mx - hudDragX), (float) (my - hudDragY), width, height);
			return true;
		}
		if (tab == 2) {
			for (SettingComponent<?> c : themeComponents) c.mouseDragged(mx, my);
			return true;
		}
		for (CategoryPanel panel : panels) panel.mouseDragged(mx, my, width, height);
		return true;
	}

	@Override
	public boolean mouseReleased(Click click) {
		draggingHud = null;
		for (CategoryPanel panel : panels) {
			panel.mouseReleased();
			POSITIONS.put(panel.category, new float[]{panel.x, panel.y});
		}
		for (SettingComponent<?> c : themeComponents) c.mouseReleased();
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		double mx = toGuiX(mouseX);
		double my = toGuiY(mouseY);
		for (int i = panels.size() - 1; i >= 0; i--) {
			if (panels.get(i).mouseScrolled(mx, my, verticalAmount)) return true;
		}
		return false;
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		int key = input.key();
		for (CategoryPanel panel : panels) if (panel.keyPressed(key)) return true;
		if (searchFocused) {
			if (key == GLFW.GLFW_KEY_BACKSPACE && !search.isEmpty()) {
				search = search.substring(0, search.length() - 1);
				return true;
			}
			if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER) {
				searchFocused = false;
				if (key == GLFW.GLFW_KEY_ESCAPE) search = "";
				return true;
			}
		}
		ClickGuiModule gui = ModuleManager.get(ClickGuiModule.class);
		if (key == GLFW.GLFW_KEY_ESCAPE || gui != null && key == gui.key() && !searchFocused) {
			close();
			return true;
		}
		return super.keyPressed(input);
	}

	@Override
	public boolean charTyped(CharInput input) {
		if (!input.isValidChar()) return false;
		for (CategoryPanel panel : panels) if (panel.isBinding()) return true;
		if (!searchFocused) {
			if (tab != 0) return false;
			searchFocused = true;
		}
		if (search.length() < 32) search += input.asString();
		return true;
	}

	@Override
	public void close() {
		ClickGuiModule gui = ModuleManager.get(ClickGuiModule.class);
		for (CategoryPanel panel : panels) POSITIONS.put(panel.category, new float[]{panel.x, panel.y});
		ConfigManager.save();
		if (gui != null && gui.animations.on() && !closing) {
			closing = true;
			return;
		}
		super.close();
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	// ------------------------------------------------------------------ persistence

	public static JsonObject saveLayout() {
		JsonObject json = new JsonObject();
		json.addProperty("tab", tab);
		JsonObject panels = new JsonObject();
		for (Map.Entry<Category, float[]> entry : POSITIONS.entrySet()) {
			JsonArray pos = new JsonArray();
			pos.add(entry.getValue()[0]);
			pos.add(entry.getValue()[1]);
			panels.add(entry.getKey().name(), pos);
		}
		json.add("panels", panels);
		return json;
	}

	public static void loadLayout(JsonObject json) {
		if (json.has("tab")) tab = Math.max(0, Math.min(TABS.length - 1, json.get("tab").getAsInt()));
		if (!json.has("panels")) return;
		JsonObject panels = json.getAsJsonObject("panels");
		for (Category category : Category.values()) {
			if (!panels.has(category.name())) continue;
			JsonArray pos = panels.getAsJsonArray(category.name());
			POSITIONS.put(category, new float[]{pos.get(0).getAsFloat(), pos.get(1).getAsFloat()});
		}
	}
}
