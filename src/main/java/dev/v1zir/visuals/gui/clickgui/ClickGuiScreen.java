package dev.v1zir.visuals.gui.clickgui;

import com.google.gson.JsonObject;
import dev.v1zir.visuals.config.ConfigManager;
import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.impl.client.ClickGuiModule;
import dev.v1zir.visuals.module.impl.client.ThemeModule;
import dev.v1zir.visuals.module.impl.hud.HudModule;
import dev.v1zir.visuals.render.BackdropBlur;
import dev.v1zir.visuals.render.FrameUniforms;
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
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The click GUI: one landscape window over the blurred world. A top bar (logo, title, search), the
 * tabs on the left, and the selected tab's modules as a grid of cards; a card's settings slide in as
 * a column on the right. The Settings tab holds the theme editor and the GUI's own options, and the
 * HUD editor is reached from the Hud tab or the top bar.
 */
public final class ClickGuiScreen extends Screen {
	private static final Category[] TABS = Category.values();
	private static final float HEADER = 24f;
	/** Selected sub-category chip of every tab. */
	private static final Map<Category, String> SUBS = new EnumMap<>(Category.class);
	/** Selected tab (Category ordinal); static so it is remembered between openings. */
	private static int tab;

	private final List<ModuleCard> cards = new ArrayList<>();
	private final ModuleGrid[] grids = new ModuleGrid[TABS.length];
	private final ModuleGrid searchGrid;
	private final SettingsPage settingsPage;
	private final Sidebar sidebar = new Sidebar(tab);
	private final TopBar topBar = new TopBar();
	private final SettingsDrawer drawer = new SettingsDrawer();
	private final Anim open;
	private final Anim editor = new Anim(0f, 12f);
	private final Anim editButtonHover = new Anim(0f, 22f);
	private final Anim doneHover = new Anim(0f, 22f);
	private final Label titleWidth = new Label();
	private final Label editWidth = new Label();
	private final Label toolbarWidth = new Label();
	private String search = "";
	private boolean searchFocused;
	private boolean initialized;
	private int lastMouseX;
	private int lastMouseY;
	private boolean hudEditor;
	@Nullable
	private ModuleCard pressedCard;
	@Nullable
	private HudModule draggingHud;
	private double hudDragX;
	private double hudDragY;
	/** "n modules" under the content title, rebuilt only when the count changes. */
	private String countText = "";
	private int countFor = Integer.MIN_VALUE;
	// Layout of the last frame (window space).
	private float winX;
	private float winY;
	private float winW;
	private float winH;
	private float contentX;
	private float contentY;
	private float contentW;
	private float contentH;
	private float editX;
	private float editW;
	private float doneX;
	private float doneY;
	private float doneW;

	public ClickGuiScreen() {
		super(Text.literal("V1zir Visuals"));
		ClickGuiModule gui = ModuleManager.get(ClickGuiModule.class);
		open = new Anim(0f, 9f * (gui != null ? gui.speed() : 1f));
		List<ModuleCard> clientCards = new ArrayList<>();
		for (Module module : ModuleManager.all()) {
			ModuleCard card = new ModuleCard(module);
			cards.add(card);
			// Click GUI and Theme live on the Settings page itself; the other client modules show as cards.
			if (module.category() == Category.SETTINGS && module.canToggle()) clientCards.add(card);
		}
		for (Category category : TABS) {
			if (category == Category.SETTINGS) continue;
			List<ModuleCard> tabCards = new ArrayList<>();
			for (ModuleCard card : cards) if (card.module.category() == category) tabCards.add(card);
			grids[category.ordinal()] = new ModuleGrid(tabCards, category.subCategories(), true, SUBS.get(category));
		}
		searchGrid = new ModuleGrid(cards, List.of(), false, null);
		settingsPage = new SettingsPage(clientCards);
		boolean animate = gui == null || gui.animations.on();
		for (ModuleGrid grid : grids) if (grid != null) grid.show(animate);
	}

	/** Opens straight into the HUD editor (Hud tab underneath). */
	public static ClickGuiScreen hudEditor() {
		tab = Category.HUD.ordinal();
		ClickGuiScreen screen = new ClickGuiScreen();
		screen.hudEditor = true;
		screen.editor.snap(1f);
		return screen;
	}

	/** Slides in the settings column of a module (as a right click on its card does). */
	public void openSettings(Module module) {
		drawer.show(module);
	}

	/** True while HUD elements are being placed: they then draw a preview even without content. */
	public boolean isHudEditor() {
		return hudEditor;
	}

	private Category category() {
		return TABS[Math.max(0, Math.min(TABS.length - 1, tab))];
	}

	private boolean searching() {
		return !search.isEmpty();
	}

	// ------------------------------------------------------------------ layout & transforms

	private float baseScale() {
		ClickGuiModule gui = ModuleManager.get(ClickGuiModule.class);
		return gui != null ? gui.scale.f() : 1f;
	}

	/** Window space: the screen divided by the GUI scale setting, centred. */
	private float spaceW() {
		return width / baseScale();
	}

	private float spaceH() {
		return height / baseScale();
	}

	private double toSpaceX(double mx) {
		return (mx - width / 2.0) / baseScale() + spaceW() / 2.0;
	}

	private double toSpaceY(double my) {
		return (my - height / 2.0) / baseScale() + spaceH() / 2.0;
	}

	private void layout() {
		float sw = spaceW();
		float sh = spaceH();
		winW = Math.min(sw - 16f, Math.max(420f, Math.min(780f, sw * 0.70f)));
		winH = Math.min(sh - 16f, Math.max(250f, Math.min(470f, sh * 0.72f)));
		winX = Math.round((sw - winW) / 2f);
		winY = Math.round((sh - winH) / 2f);
		float side = Sidebar.width(winW);
		contentX = winX + Ui.PAD + side + 10f;
		contentY = winY + Ui.BAR + Ui.PAD;
		contentW = winX + winW - 12f - contentX;
		contentH = winH - Ui.BAR - Ui.PAD * 2f;
	}

	/** Window rectangle in screen GUI coordinates (x, y, w, h), for the transitions. */
	void windowRect(float[] out) {
		layout();
		float s = baseScale();
		out[0] = width / 2f + (winX - spaceW() / 2f) * s;
		out[1] = height / 2f + (winY - spaceH() / 2f) * s;
		out[2] = winW * s;
		out[3] = winH * s;
	}

	// ------------------------------------------------------------------ rendering

	@Override
	protected void init() {
		if (initialized) return;
		// First showing: the 3D open draws the window through WindowTransition; anything else stops a close still playing.
		initialized = true;
		ClickGuiModule gui = ModuleManager.get(ClickGuiModule.class);
		if (!hudEditor && gui != null && gui.animations.on() && gui.openAnimation.is(ClickGuiModule.OPEN_3D)) WindowTransition.open(gui.speed());
		else WindowTransition.finish();
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
		if (!animations) open.snap(1f);
		float t = open.target(1f).get();
		float edit = editor.target(hudEditor ? 1f : 0f).get();
		lastMouseX = mouseX;
		lastMouseY = mouseY;
		if (edit < 0.999f) FrameUniforms.cursor(mouseX, mouseY);

		// The blurred world behind the window, only lightly dimmed; the HUD editor shows the plain game.
		if (gui == null || gui.dimBackground.on()) {
			Render2D.setAlpha(t * (1f - edit));
			boolean inWorld = client != null && client.world != null;
			// The close animations (WindowTransition) redraw this backdrop while they fade it.
			if (inWorld && BackdropBlur.available()) Render2D.glass(ctx, 0, 0, width, height, 0, 0x34000000);
			else Render2D.rect(ctx, 0, 0, width, height, inWorld ? 0x80000000 : 0xB0000000);
		}
		if (edit < 0.999f) {
			DrawContext swing = edit < 0.001f ? WindowTransition.openTarget(mouseX, mouseY) : null;
			if (swing != null) {
				drawWindow(swing, mouseX, mouseY, 1f, 1f, 0f);
			} else {
				// Normal open: the window grows from 94 % and rises into place while it fades in.
				float k = (0.94f + 0.06f * t) * (1f - 0.05f * edit);
				drawWindow(ctx, mouseX, mouseY, t * (1f - edit), k, (1f - t) * 10f);
			}
		}
		if (edit > 0.001f) {
			Render2D.setAlpha(edit);
			renderHudEditor(ctx, mouseX, mouseY);
		}
		Render2D.setAlpha(1f);
	}

	/**
	 * Draws the window in its own transform: scale around the screen centre and a vertical offset.
	 * Mouse coordinates are screen coordinates.
	 */
	public void drawWindow(DrawContext ctx, int mouseX, int mouseY, float alpha, float scale, float rise) {
		layout();
		float s = baseScale();
		float sw = spaceW();
		float sh = spaceH();
		double mx = toSpaceX(mouseX);
		double my = toSpaceY(mouseY);
		ctx.getMatrices().pushMatrix();
		ctx.getMatrices().translate(width / 2f, height / 2f + rise);
		ctx.getMatrices().scale(s * scale, s * scale);
		ctx.getMatrices().translate(-sw / 2f, -sh / 2f);
		Render2D.setAlpha(alpha);
		if (hudEditor) {
			// Nothing in the window reacts while the HUD editor is in front of it.
			mx = -1000;
			my = -1000;
		}

		Render2D.shadow(ctx, winX, winY + 8f, winW, winH, Ui.RADIUS, 30f, 0x9C000000);
		Render2D.panel(ctx, winX, winY, winW, winH, Ui.RADIUS, 0, Ui.LIT);
		topBar.render(ctx, winX, winY, winW, mx, my, search, searchFocused);
		sidebar.render(ctx, winX + Ui.PAD, contentY, Sidebar.width(winW), contentH, tab, searching(), mx, my);

		boolean mouseFree = !drawer.over(mx, my);
		renderHeader(ctx, mx, my, mouseFree);
		float bodyY = contentY + HEADER + 4f;
		float bodyH = contentH - HEADER - 4f;
		Module drawerModule = drawer.module();
		if (searching()) searchGrid.render(ctx, contentX, bodyY, contentW, bodyH, mx, my, drawerModule, mouseFree);
		else if (category() == Category.SETTINGS) settingsPage.render(ctx, contentX, bodyY, contentW, bodyH, mx, my, drawerModule, mouseFree);
		else grids[tab].render(ctx, contentX, bodyY, contentW, bodyH, mx, my, drawerModule, mouseFree);
		if (drawer.visible()) {
			// Clipped to the window below the top bar, so the column and its shadow emerge from the window edge.
			ctx.enableScissor((int) Math.floor(winX + 1f), (int) Math.floor(winY + Ui.BAR + 1f), (int) Math.ceil(winX + winW - 1f),
				(int) Math.ceil(winY + winH - 1f));
			drawer.render(ctx, contentX, contentY, contentW + 2f, contentH, mx, my);
			ctx.disableScissor();
		}
		ctx.getMatrices().popMatrix();
	}

	/** Content title with its module count on the left; sub-category chips (and Edit layout on the Hud tab) on the right. */
	private void renderHeader(DrawContext ctx, double mx, double my, boolean mouseFree) {
		float mid = contentY + HEADER / 2f;
		SdfFont bold = Fonts.bold();
		String title = searching() ? "Search" : category().title();
		Render2D.textMiddle(ctx, bold, title, contentX + 2f, mid, 10f, Theme.text());
		float right = contentX + contentW;
		if (category() == Category.SETTINGS && !searching()) {
			drawCount(ctx, contentX + 2f + titleWidth.width(bold, title, 10f) + 6f, mid, -1);
			return;
		}
		ModuleGrid grid = searching() ? searchGrid : grids[tab];
		drawCount(ctx, contentX + 2f + titleWidth.width(bold, title, 10f) + 6f, mid, grid.count());
		if (searching()) return;
		double hx = mouseFree ? mx : -1000;
		double hy = mouseFree ? my : -1000;
		if (category() == Category.HUD) {
			// Edit layout: opens the HUD editor.
			SdfFont font = Fonts.semibold();
			editW = editWidth.width(font, "Edit layout", Ui.SMALL) + 24f;
			editX = right - editW;
			float hv = editButtonHover.target(Ui.inside(hx, hy, editX, mid - 9f, editW, 18f) ? 1f : 0f).get();
			int accent = Theme.accent();
			Render2D.shadow(ctx, editX, mid - 9f, editW, 18f, 6f, 8f, ColorUtil.withAlpha(accent, Math.round(30 + 40 * hv)));
			Render2D.matte(ctx, editX, mid - 9f, editW, 18f, 6f, ColorUtil.withAlpha(accent, Math.round(46 + 30 * hv)), Ui.LIT);
			Render2D.rim(ctx, editX, mid - 9f, editW, 18f, 6f, ColorUtil.withAlpha(ColorUtil.lerp(accent, 0xFFFFFFFF, 0.3f), 120), Ui.LIT);
			Render2D.icon(ctx, Icons.MOVE, editX + 9f, mid, 7f, 0xFFFFFFFF);
			Render2D.textMiddle(ctx, font, "Edit layout", editX + 16f, mid, Ui.SMALL, 0xFFFFFFFF);
			right = editX - 8f;
		}
		grid.renderChips(ctx, right, mid, hx, hy);
	}

	/** "n modules" next to the title (n < 0 draws the Settings subtitle). */
	private void drawCount(DrawContext ctx, float x, float mid, int n) {
		if (n != countFor) {
			countFor = n;
			countText = n < 0 ? "Theme, interface and animations" : n == 1 ? "1 module" : n + " modules";
		}
		Render2D.textMiddle(ctx, Fonts.medium(), countText, x, mid + 1f, Ui.TINY, Theme.textFaint());
	}

	// ------------------------------------------------------------------ HUD editor

	private void renderHudEditor(DrawContext ctx, int mouseX, int mouseY) {
		SdfFont font = Fonts.semibold();
		for (Module module : ModuleManager.all()) {
			if (!(module instanceof HudModule hud)) continue;
			float x = hud.x(width);
			float y = hud.y(height);
			boolean hover = Ui.inside(mouseX, mouseY, x - 2, y - 2, hud.width() + 4, hud.height() + 4) || draggingHud == hud;
			int color = hud.isEnabled() ? Theme.accent() : 0xFF6B7080;
			Render2D.round(ctx, x - 2, y - 2, hud.width() + 4, hud.height() + 4, 5f, ColorUtil.withAlpha(color, hover ? 40 : 16));
			Render2D.outline(ctx, x - 2, y - 2, hud.width() + 4, hud.height() + 4, 5f, 1f, ColorUtil.withAlpha(color, hover ? 220 : 110));
			if (hover || !hud.isEnabled()) {
				// Name tag above the element (below it near the top edge), with an "off" mark when disabled.
				float nw = font.width(hud.name(), 6.5f);
				float lw = nw + 8f + (hud.isEnabled() ? 0f : 14f);
				float ly = y - 14f < 40f ? y + hud.height() + 4f : y - 14f;
				Render2D.round(ctx, x - 2, ly, lw, 11f, 3.5f, ColorUtil.withAlpha(color, 210));
				Render2D.textMiddle(ctx, font, hud.name(), x + 2, ly + 5.5f, 6.5f, 0xFFFFFFFF);
				if (!hud.isEnabled()) Render2D.textMiddle(ctx, font, "off", x + 2 + nw + 3f, ly + 5.5f, 6.5f, 0x99FFFFFF);
			}
		}
		renderToolbar(ctx, mouseX, mouseY);
	}

	/** Floating pill at the top: what the editor does, and a Done button back to the window. */
	private void renderToolbar(DrawContext ctx, int mouseX, int mouseY) {
		SdfFont font = Fonts.semibold();
		String hint = "Drag to move  ·  Right click to toggle";
		float hw = toolbarWidth.width(Fonts.regular(), hint, Ui.TINY);
		float h = 22f;
		doneW = 40f;
		float w = 12f + 10f + 6f + font.width("HUD layout", 7f) + 10f + hw + 10f + doneW + 4f;
		float x = width / 2f - w / 2f;
		// Low in the screen, above the hotbar: the top centre usually holds the compass.
		float y = height - 66f;
		float mid = y + h / 2f;
		Render2D.shadow(ctx, x, y + 3f, w, h, h / 2f, 14f, Theme.shadow());
		Render2D.panel(ctx, x, y, w, h, h / 2f);
		Render2D.icon(ctx, Icons.MOVE, x + 15f, mid, 8f, Theme.accent());
		float tx = Render2D.textMiddle(ctx, font, "HUD layout", x + 25f, mid, 7f, Theme.text()) + x + 25f + 10f;
		Render2D.textMiddle(ctx, Fonts.regular(), hint, tx, mid, Ui.TINY, Theme.textDim());
		doneX = x + w - 4f - doneW;
		doneY = y + 3f;
		float hv = doneHover.target(Ui.inside(mouseX, mouseY, doneX, doneY, doneW, h - 6f) ? 1f : 0f).get();
		Render2D.gradientH(ctx, doneX, doneY, doneW, h - 6f, (h - 6f) / 2f, ColorUtil.brighten(Theme.accent(), 0.1f * hv),
			ColorUtil.brighten(Theme.accent2(), 0.1f * hv));
		Render2D.textCentered(ctx, font, "Done", doneX + doneW / 2f, Render2D.middle(font, mid, 7f), 7f, 0xFFFFFFFF);
	}

	private void exitHudEditor() {
		hudEditor = false;
		draggingHud = null;
	}

	// ------------------------------------------------------------------ input

	private void selectTab(int index) {
		search = "";
		searchFocused = false;
		drawer.close();
		if (index == tab) return;
		tab = index;
		if (grids[tab] != null) grids[tab].show(true);
		else settingsPage.reset();
	}

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		int button = click.button();
		if (hudEditor) return hudEditorClicked(click.x(), click.y(), button);
		double mx = toSpaceX(click.x());
		double my = toSpaceY(click.y());
		searchFocused = false;
		if (!Ui.inside(mx, my, winX, winY, winW, winH)) {
			drawer.close();
			return false;
		}
		if (topBar.overSearch(mx, my)) {
			searchFocused = true;
			return true;
		}
		if (topBar.overHudButton(mx, my)) {
			topBar.pressHud(true);
			return true;
		}
		int tabAt = sidebar.tabAt(mx, my);
		if (tabAt >= 0) {
			selectTab(tabAt);
			return true;
		}
		if (drawer.mouseClicked(mx, my, button)) return true;
		if (drawer.module() != null && Ui.inside(mx, my, contentX, contentY, contentW, contentH)) {
			// A click beside the open settings column closes it instead of reaching the grid.
			drawer.close();
			return true;
		}
		float mid = contentY + HEADER / 2f;
		if (!searching()) {
			if (category() == Category.HUD && Ui.inside(mx, my, editX, mid - 9f, editW, 18f)) {
				hudEditor = true;
				return true;
			}
			if (grids[tab] != null && grids[tab].clickChip(mx, my)) return true;
			if (category() == Category.SETTINGS && settingsPage.mouseClicked(mx, my, button)) return true;
		}
		ModuleCard card = searching() ? searchGrid.cardAt(mx, my)
			: category() == Category.SETTINGS ? settingsPage.cardAt(mx, my) : grids[tab].cardAt(mx, my);
		if (card != null) {
			switch (button) {
				case GLFW.GLFW_MOUSE_BUTTON_LEFT -> {
					card.press();
					pressedCard = card;
				}
				case GLFW.GLFW_MOUSE_BUTTON_RIGHT -> {
					if (card.hasSettings()) drawer.show(card.module);
				}
				case GLFW.GLFW_MOUSE_BUTTON_MIDDLE -> card.toggleBinding();
				default -> {
				}
			}
			return true;
		}
		return super.mouseClicked(click, doubled);
	}

	private boolean hudEditorClicked(double mx, double my, int button) {
		if (Ui.inside(mx, my, doneX, doneY, doneW, 16f)) {
			exitHudEditor();
			return true;
		}
		for (Module module : ModuleManager.all()) {
			if (!(module instanceof HudModule hud)) continue;
			float x = hud.x(width);
			float y = hud.y(height);
			if (Ui.inside(mx, my, x - 2, y - 2, hud.width() + 4, hud.height() + 4)) {
				// Buttons inside an element (Spotify controls) work here too.
				if (hud.isEnabled() && hud.mouseClicked(mx, my, button)) return true;
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

	@Override
	public boolean mouseDragged(Click click, double offsetX, double offsetY) {
		if (draggingHud != null) {
			draggingHud.moveTo((float) (click.x() - hudDragX), (float) (click.y() - hudDragY), width, height);
			return true;
		}
		double mx = toSpaceX(click.x());
		double my = toSpaceY(click.y());
		drawer.mouseDragged(mx, my);
		if (category() == Category.SETTINGS) settingsPage.mouseDragged(mx, my);
		return true;
	}

	@Override
	public boolean mouseReleased(Click click) {
		draggingHud = null;
		double mx = toSpaceX(click.x());
		double my = toSpaceY(click.y());
		topBar.pressHud(false);
		if (!hudEditor && topBar.overHudButton(mx, my) && click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			drawer.close();
			hudEditor = true;
		}
		if (pressedCard != null) {
			ModuleCard card = pressedCard;
			pressedCard = null;
			if (card.release(mx, my)) {
				if (card.module.canToggle()) card.module.toggle();
				else drawer.show(card.module);
			}
		}
		drawer.mouseReleased();
		settingsPage.mouseReleased();
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (hudEditor) return false;
		double mx = toSpaceX(mouseX);
		double my = toSpaceY(mouseY);
		if (drawer.mouseScrolled(mx, my, verticalAmount)) return true;
		if (searching()) searchGrid.scroll(verticalAmount);
		else if (category() == Category.SETTINGS) settingsPage.scroll(verticalAmount);
		else grids[tab].scroll(verticalAmount);
		return true;
	}

	private boolean anyBinding() {
		if (drawer.isBinding() || settingsPage.isBinding()) return true;
		for (ModuleCard card : cards) if (card.isBinding()) return true;
		return false;
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		int key = input.key();
		if (hudEditor) {
			if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER) {
				exitHudEditor();
				return true;
			}
			return super.keyPressed(input);
		}
		for (ModuleCard card : cards) if (card.keyPressed(key)) return true;
		if (drawer.keyPressed(key) || settingsPage.keyPressed(key)) return true;
		if (key == GLFW.GLFW_KEY_F && (input.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0) {
			searchFocused = true;
			return true;
		}
		if (searchFocused) {
			if (key == GLFW.GLFW_KEY_BACKSPACE && !search.isEmpty()) {
				setSearch(search.substring(0, search.length() - 1));
				return true;
			}
			if (key == GLFW.GLFW_KEY_ENTER) {
				searchFocused = false;
				return true;
			}
			if (key == GLFW.GLFW_KEY_ESCAPE) {
				searchFocused = false;
				setSearch("");
				return true;
			}
		}
		if (key == GLFW.GLFW_KEY_ESCAPE && drawer.module() != null) {
			drawer.close();
			return true;
		}
		if (key == GLFW.GLFW_KEY_ESCAPE && searching()) {
			setSearch("");
			return true;
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
		if (!input.isValidChar() || hudEditor) return false;
		if (anyBinding()) return true;
		searchFocused = true;
		if (search.length() < 32) setSearch(search + input.asString());
		return true;
	}

	private void setSearch(String text) {
		if (text.isEmpty() == search.isEmpty() && text.equals(search)) return;
		search = text;
		drawer.close();
		searchGrid.search(text);
	}

	/** Closes at once (the game takes input again); the close animation plays from a snapshot of the window. */
	@Override
	public void close() {
		remember();
		ConfigManager.save();
		ClickGuiModule gui = ModuleManager.get(ClickGuiModule.class);
		if (gui != null && gui.animations.on() && !hudEditor) {
			boolean inWorld = client != null && client.world != null;
			boolean dim = gui.dimBackground.on();
			boolean blur = dim && inWorld && BackdropBlur.available();
			float dimAmount = !dim ? 0f : blur ? 0x34 / 255f : inWorld ? 0x80 / 255f : 0xB0 / 255f;
			WindowTransition.close(this, gui.closeAnimation.get(), lastMouseX, lastMouseY, gui.speed(), dimAmount, blur);
		}
		super.close();
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	// ------------------------------------------------------------------ persistence

	/** Keeps the selected chips for the next time the GUI opens. */
	private void remember() {
		for (Category category : TABS) {
			ModuleGrid grid = grids[category.ordinal()];
			String sub = grid != null ? grid.selectedSub() : null;
			if (sub != null) SUBS.put(category, sub);
		}
	}

	public static JsonObject saveLayout() {
		JsonObject json = new JsonObject();
		json.addProperty("page", TABS[tab].name());
		JsonObject subs = new JsonObject();
		for (Map.Entry<Category, String> entry : SUBS.entrySet()) subs.addProperty(entry.getKey().name(), entry.getValue());
		json.add("subs", subs);
		return json;
	}

	/** Reads the saved tab and chips; layouts of the old panel GUI (numeric "tab", "panels") are ignored. */
	public static void loadLayout(JsonObject json) {
		if (json.has("page")) {
			String page = json.get("page").getAsString();
			for (Category category : TABS) if (category.name().equals(page)) tab = category.ordinal();
		}
		if (json.has("subs")) {
			JsonObject subs = json.getAsJsonObject("subs");
			for (Category category : TABS) {
				if (subs.has(category.name())) SUBS.put(category, subs.get(category.name()).getAsString());
			}
		}
	}
}
