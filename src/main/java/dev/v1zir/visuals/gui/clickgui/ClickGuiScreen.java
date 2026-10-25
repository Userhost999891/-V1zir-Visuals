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
import dev.v1zir.visuals.render.BackdropBlur;
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
 * The click GUI: category panels over the blurred world, a tab bar (Modules / Hud / Themes) and search.
 * Layout is inspired by CatLean and Nursultan: centred panels, inline settings, theme editor.
 */
public final class ClickGuiScreen extends Screen {
	private static final String[] TABS = {"Modules", "Hud", "Themes"};
	private static final int[] TAB_ICONS = {Icons.LAYOUT_GRID, Icons.LAYOUT_DASHBOARD, Icons.PALETTE};
	private static final String[] HINTS = {"Left click: toggle  ·  Right click: settings  ·  Middle click: bind",
		"Drag elements to move them  ·  Right click to toggle", "Pick a preset or tune every colour"};
	private static final float TAB_H = 20f;
	private static final float TAB_Y = 14f;
	private static final float SEARCH_W = 92f;
	private static final float FOOTER = 6.5f;
	/** Delay between two panels starting to open, in seconds. */
	private static final float STAGGER = 0.045f;
	private static final Theme.Preset[] PRESETS = Theme.Preset.values();
	private static final Map<Category, float[]> POSITIONS = new EnumMap<>(Category.class);
	/** Selected sub-category chip of every panel. */
	private static final Map<Category, String> SUBS = new EnumMap<>(Category.class);
	private static int tab;
	private static String versionText;

	private final List<CategoryPanel> panels = new ArrayList<>();
	private final Anim open = new Anim(0f, 9f);
	private final Anim exit = new Anim(1f, 16f);
	private final Anim tabSlide = new Anim(tab, 14f);
	private final Anim[] tabHover = {new Anim(0f, 20f), new Anim(0f, 20f), new Anim(0f, 20f)};
	private final Anim searchFocus = new Anim(0f, 18f);
	private final long openedAt = System.nanoTime();
	private final List<SettingComponent<?>> themeComponents = new ArrayList<>();
	private String search = "";
	private boolean searchFocused;
	private boolean closing;
	private HudModule draggingHud;
	private double hudDragX;
	private double hudDragY;
	// Layout of the last frame, reused for hit testing (no per-frame arrays).
	private final Label[] tabWidths = {new Label(), new Label(), new Label()};
	private final float[] tabX = new float[TABS.length];
	private final float[] tabW = new float[TABS.length];
	private float searchX;
	private final float[] presetCards = new float[PRESETS.length * 2];
	private float presetW;
	private float presetH;
	// Cached text of the search field and the footer.
	private final Label searchWidth = new Label();
	private final Label footerName = new Label();
	private final Label footerText = new Label();
	private final Label themeTitle = new Label();
	private String shownSearch = "";
	private String shownSearchFor = "";

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
		// On a resize the panels are rebuilt; they should not play the opening animation again.
		boolean reopened = !panels.isEmpty();
		panels.clear();
		Category[] categories = Category.values();
		float total = categories.length * Ui.PANEL_WIDTH + (categories.length - 1) * Ui.PANEL_GAP;
		float startX = width / 2f - total / 2f;
		for (int i = 0; i < categories.length; i++) {
			float[] pos = POSITIONS.get(categories[i]);
			if (pos == null) pos = new float[]{startX + i * (Ui.PANEL_WIDTH + Ui.PANEL_GAP), 48f};
			pos[0] = Math.max(0, Math.min(width - Ui.PANEL_WIDTH, pos[0]));
			pos[1] = Math.max(30, Math.min(height - Ui.HEADER_HEIGHT, pos[1]));
			CategoryPanel panel = new CategoryPanel(categories[i], pos[0], pos[1], SUBS.get(categories[i]));
			if (reopened) panel.appear.snap(1f);
			panels.add(panel);
		}
	}

	private float scale() {
		ClickGuiModule gui = ModuleManager.get(ClickGuiModule.class);
		float base = gui != null ? gui.scale.f() : 1f;
		boolean animations = gui == null || gui.animations.on();
		float t = animations ? open.value() * exit.value() : 1f;
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
		float t = open.target(1f).get();
		float exitT = exit.target(closing ? 0f : 1f).get();
		if (closing && exitT < 0.02f) {
			super.close();
			return;
		}
		float alpha = animations ? Anim.clamp01(t * exitT) : 1f;

		// Background: the blurred world, only lightly dimmed (not on the HUD tab, where the HUD must stay visible).
		if (!isHudTab() && (gui == null || gui.dimBackground.on())) {
			Render2D.setAlpha(alpha);
			boolean inWorld = client != null && client.world != null;
			if (inWorld && BackdropBlur.available()) Render2D.glass(ctx, 0, 0, width, height, 0, 0x30000000);
			else Render2D.rect(ctx, 0, 0, width, height, inWorld ? 0x88000000 : 0xB0000000);
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
		Module hovered = null;
		switch (tab) {
			case 1 -> renderHudEditor(ctx, gx, gy);
			case 2 -> renderThemes(ctx, gx, gy);
			default -> {
				// Panels open one after another, each on its own spring, rising into place.
				float since = (System.nanoTime() - openedAt) / 1.0e9f;
				int index = 0;
				for (CategoryPanel panel : panels) {
					if (!panel.matches(search)) continue;
					if (!animations) panel.appear.snap(1f);
					else if (since >= index++ * STAGGER) panel.appear.target(1f);
					float pt = panel.appear.get();
					if (pt <= 0.005f) continue;
					float rise = (1f - pt) * 10f;
					Render2D.setAlpha(alpha * pt);
					ctx.getMatrices().pushMatrix();
					ctx.getMatrices().translate(0f, rise);
					panel.render(ctx, gx, gy - rise, search, height);
					ctx.getMatrices().popMatrix();
					Module over = panel.hovered();
					if (over != null) hovered = over;
				}
				Render2D.setAlpha(alpha);
			}
		}
		renderFooter(ctx, hovered);
		ctx.getMatrices().popMatrix();
		Render2D.setAlpha(1f);
	}

	/** Version on the left; in the middle the hovered module's description, or the controls of the tab. */
	private void renderFooter(DrawContext ctx, Module hovered) {
		SdfFont font = Fonts.medium();
		float y = height - 8f - font.lineHeight(FOOTER);
		if (versionText == null) versionText = V1zir.NAME + "  " + V1zir.version();
		// HUD elements may sit in the corner while they are being placed.
		if (!isHudTab()) Render2D.text(ctx, font, versionText, 8f, y, FOOTER, 0x5AFFFFFF);
		if (hovered != null) {
			SdfFont bold = Fonts.semibold();
			float nw = footerName.width(bold, hovered.name(), FOOTER + 0.5f);
			float dw = footerText.width(font, hovered.description(), FOOTER + 0.5f);
			float x = width / 2f - (nw + 8f + dw) / 2f;
			Render2D.text(ctx, bold, hovered.name(), x, y - 0.5f, FOOTER + 0.5f, Theme.accent());
			Render2D.text(ctx, font, hovered.description(), x + nw + 8f, y - 0.5f, FOOTER + 0.5f, 0xC8FFFFFF);
		} else {
			float hw = footerText.width(font, HINTS[tab], FOOTER);
			Render2D.text(ctx, font, HINTS[tab], width / 2f - hw / 2f, y, FOOTER, 0x6EFFFFFF);
		}
	}

	// ------------------------------------------------------------------ tab bar & search

	private void renderTabBar(DrawContext ctx, double mx, double my) {
		SdfFont font = Fonts.medium();
		float size = 7.5f;
		float h = TAB_H;
		float y = TAB_Y;
		float pad = 3f;
		float total = pad;
		for (int i = 0; i < TABS.length; i++) {
			tabW[i] = tabWidths[i].width(font, TABS[i], size) + 26f;
			total += tabW[i];
		}
		total += pad + 6f + SEARCH_W + pad;
		float x = width / 2f - total / 2f;
		Render2D.shadow(ctx, x, y + 3f, total, h, h / 2f, 14f, Theme.shadow());
		Render2D.panel(ctx, x, y, total, h, h / 2f);

		float cx = x + pad;
		for (int i = 0; i < TABS.length; i++) {
			tabX[i] = cx;
			cx += tabW[i];
		}
		// The selection pill glides between tabs, stretching to the width of each.
		float slide = tabSlide.target(tab).get();
		int lo = Math.max(0, Math.min(TABS.length - 1, (int) Math.floor(slide)));
		int hi = Math.min(TABS.length - 1, lo + 1);
		float frac = slide - lo;
		float selX = tabX[lo] + (tabX[hi] - tabX[lo]) * frac;
		float selW = tabW[lo] + (tabW[hi] - tabW[lo]) * frac;
		float pillH = h - pad * 2;
		Render2D.shadow(ctx, selX, y + pad, selW, pillH, pillH / 2f, 7f, ColorUtil.withAlpha(Theme.accent(), 100));
		Render2D.gradientH(ctx, selX, y + pad, selW, pillH, pillH / 2f, Theme.accent(), Theme.accent2());

		for (int i = 0; i < TABS.length; i++) {
			boolean selected = i == tab;
			float hv = tabHover[i].target(!selected && Ui.inside(mx, my, tabX[i], y, tabW[i], h) ? 1f : 0f).get();
			if (hv > 0.01f) Render2D.round(ctx, tabX[i], y + pad, tabW[i], pillH, pillH / 2f, ColorUtil.withAlpha(0xFFFFFF, Math.round(14 * hv)));
			int color = selected ? 0xFFFFFFFF : ColorUtil.lerp(Theme.textDim(), Theme.text(), hv);
			Render2D.icon(ctx, TAB_ICONS[i], tabX[i] + 11f, y + h / 2f, 8f, color);
			Render2D.textMiddle(ctx, font, TABS[i], tabX[i] + 18f, y + h / 2f, size, color);
		}

		float sx = cx + 6f;
		searchX = sx;
		float focus = searchFocus.target(searchFocused ? 1f : 0f).get();
		Render2D.rect(ctx, sx - 3.5f, y + 5f, 1f, h - 10f, ColorUtil.withAlpha(0xFFFFFF, 30));
		Render2D.round(ctx, sx, y + pad, SEARCH_W, pillH, pillH / 2f, ColorUtil.withAlpha(0xFFFFFF, Math.round(10 + 12 * focus)));
		if (focus > 0.01f) {
			Render2D.outline(ctx, sx, y + pad, SEARCH_W, pillH, pillH / 2f, 1f, ColorUtil.withAlpha(Theme.accent(), Math.round(170 * focus)));
		}
		Render2D.icon(ctx, Icons.SEARCH, sx + 9f, y + h / 2f, 7.5f, ColorUtil.lerp(Theme.textDim(), Theme.accent(), focus));
		float textSize = 7f;
		float tx = sx + 17f;
		if (search.isEmpty() && !searchFocused) {
			Render2D.textMiddle(ctx, font, "Search...", tx, y + h / 2f, textSize, Theme.textFaint());
		} else {
			if (!search.equals(shownSearchFor)) {
				shownSearchFor = search;
				shownSearch = font.trim(search, textSize, SEARCH_W - 28f);
			}
			Render2D.textMiddle(ctx, font, shownSearch, tx, y + h / 2f, textSize, Theme.text());
			if (searchFocused) {
				// Caret that breathes instead of blinking.
				float pulse = 0.5f + 0.5f * (float) Math.cos(System.nanoTime() / 1.0e9 * Math.PI * 2.0);
				float caretX = tx + searchWidth.width(font, shownSearch, textSize) + 1f;
				Render2D.round(ctx, caretX, y + h / 2f - 3.5f, 1f, 7f, 0.5f, ColorUtil.withAlpha(Theme.accent(), Math.round(255 * pulse)));
			}
		}
	}

	// ------------------------------------------------------------------ HUD editor

	private void renderHudEditor(DrawContext ctx, double mx, double my) {
		SdfFont font = Fonts.semibold();
		for (Module module : ModuleManager.all()) {
			if (!(module instanceof HudModule hud)) continue;
			float x = hud.x(width);
			float y = hud.y(height);
			boolean hover = Ui.inside(mx, my, x - 2, y - 2, hud.width() + 4, hud.height() + 4) || draggingHud == hud;
			int color = hud.isEnabled() ? Theme.accent() : 0xFF6B7080;
			Render2D.round(ctx, x - 2, y - 2, hud.width() + 4, hud.height() + 4, 5f, ColorUtil.withAlpha(color, hover ? 40 : 16));
			Render2D.outline(ctx, x - 2, y - 2, hud.width() + 4, hud.height() + 4, 5f, 1f, ColorUtil.withAlpha(color, hover ? 220 : 110));
			if (hover || !hud.isEnabled()) {
				// Name tag above the element (below it near the top edge), with an "off" mark when disabled.
				float nw = font.width(hud.name(), 6.5f);
				float lw = nw + 8f + (hud.isEnabled() ? 0f : 14f);
				float ly = y - 14f < 36f ? y + hud.height() + 4f : y - 14f;
				Render2D.round(ctx, x - 2, ly, lw, 11f, 3.5f, ColorUtil.withAlpha(color, 210));
				Render2D.textMiddle(ctx, font, hud.name(), x + 2, ly + 5.5f, 6.5f, 0xFFFFFFFF);
				if (!hud.isEnabled()) Render2D.textMiddle(ctx, font, "off", x + 2 + nw + 3f, ly + 5.5f, 6.5f, 0x99FFFFFF);
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
		Theme.Preset[] presets = PRESETS;
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
			if (column++ % 2 == 0) leftH += c.estimateHeight() + 4f;
			else rightH += c.estimateHeight() + 4f;
		}
		float settingsH = Math.max(leftH, rightH);
		float h = Ui.HEADER_HEIGHT + 8f + gridH + 12f + settingsH + 8f;
		h = Math.min(h, height - y - 20f);

		Ui.surface(ctx, x, y, w, h, Theme.accent());
		Ui.header(ctx, x, y, w, Icons.SWATCH_BOOK, "Theme Editor", themeTitle, Theme.accent(), Theme.accent2());

		presetW = cardW;
		presetH = cardH;
		float gy = y + Ui.HEADER_HEIGHT + 8f;
		for (int i = 0; i < presets.length; i++) {
			Theme.Preset p = presets[i];
			float cx = x + 8f + (i % cols) * (cardW + gap);
			float cy = gy + (i / cols) * (cardH + gap);
			boolean selected = theme.preset.is(p.title());
			boolean hover = Ui.inside(mx, my, cx, cy, cardW, cardH);
			int a1 = p == Theme.Preset.CUSTOM ? theme.accent.argb() : p.accent();
			int a2 = p == Theme.Preset.CUSTOM ? theme.accent2.argb() : p.accent2();
			if (selected) Render2D.shadow(ctx, cx, cy, cardW, cardH, 5f, 7f, ColorUtil.withAlpha(a1, 70));
			Render2D.round(ctx, cx, cy, cardW, cardH, 5f, ColorUtil.withAlpha(p.base(), 230));
			Render2D.round(ctx, cx, cy, cardW, cardH, 5f, ColorUtil.withAlpha(a1, hover ? 46 : 26), 0x00000000, 0x00000000, ColorUtil.withAlpha(a2, hover ? 40 : 20));
			Render2D.outline(ctx, cx, cy, cardW, cardH, 5f, selected ? 1.4f : 1f, selected ? a1 : ColorUtil.withAlpha(0xFFFFFF, hover ? 50 : 22));
			Render2D.text(ctx, Fonts.semibold(), p.title(), cx + 7f, cy + 5f, 7f, 0xFFEDEFF5);
			Render2D.gradientH(ctx, cx + 7f, cy + cardH - 9f, cardW - 14f, 3f, 1.5f, a1, a2);
			if (selected) Render2D.icon(ctx, Icons.CHECK, cx + cardW - 9f, cy + 9f, 7f, a1);
			presetCards[i * 2] = cx;
			presetCards[i * 2 + 1] = cy;
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

	// ------------------------------------------------------------------ input

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		double mx = toGuiX(click.x());
		double my = toGuiY(click.y());
		int button = click.button();

		for (int i = 0; i < TABS.length; i++) {
			if (Ui.inside(mx, my, tabX[i], TAB_Y, tabW[i], TAB_H)) {
				tab = i;
				return true;
			}
		}
		searchFocused = Ui.inside(mx, my, searchX, TAB_Y, SEARCH_W, TAB_H);
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
					// Buttons inside an element (Spotify controls) work here too; the HUD itself is drawn unscaled.
					if (hud.isEnabled() && hud.mouseClicked(click.x(), click.y(), button)) return true;
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
			for (int i = 0; i < PRESETS.length; i++) {
				if (theme != null && Ui.inside(mx, my, presetCards[i * 2], presetCards[i * 2 + 1], presetW, presetH)) {
					theme.preset.set(PRESETS[i].title());
					return true;
				}
			}
			for (SettingComponent<?> c : themeComponents) if (c.visible() && c.mouseClicked(mx, my, button)) return true;
			return false;
		}
		for (int i = panels.size() - 1; i >= 0; i--) {
			CategoryPanel panel = panels.get(i);
			if (!panel.matches(search)) continue;
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
		for (CategoryPanel panel : panels) panel.mouseReleased();
		remember();
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
		remember();
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

	/** Keeps the panel positions and selected chips for the next time the GUI opens. */
	private void remember() {
		for (CategoryPanel panel : panels) {
			POSITIONS.put(panel.category, new float[]{panel.x, panel.y});
			String sub = panel.selectedSub();
			if (sub != null) SUBS.put(panel.category, sub);
		}
	}

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
		JsonObject subs = new JsonObject();
		for (Map.Entry<Category, String> entry : SUBS.entrySet()) subs.addProperty(entry.getKey().name(), entry.getValue());
		json.add("subs", subs);
		return json;
	}

	public static void loadLayout(JsonObject json) {
		if (json.has("tab")) tab = Math.max(0, Math.min(TABS.length - 1, json.get("tab").getAsInt()));
		if (json.has("subs")) {
			JsonObject subs = json.getAsJsonObject("subs");
			for (Category category : Category.values()) {
				if (subs.has(category.name())) SUBS.put(category, subs.get(category.name()).getAsString());
			}
		}
		if (!json.has("panels")) return;
		JsonObject panels = json.getAsJsonObject("panels");
		for (Category category : Category.values()) {
			if (!panels.has(category.name())) continue;
			JsonArray pos = panels.getAsJsonArray(category.name());
			POSITIONS.put(category, new float[]{pos.get(0).getAsFloat(), pos.get(1).getAsFloat()});
		}
	}
}
