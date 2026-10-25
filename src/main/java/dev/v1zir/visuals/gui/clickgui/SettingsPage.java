package dev.v1zir.visuals.gui.clickgui;

import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.impl.client.ClickGuiModule;
import dev.v1zir.visuals.module.impl.client.ThemeModule;
import dev.v1zir.visuals.module.setting.Setting;
import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.font.Icons;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.gui.DrawContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The Settings tab: theme presets and their editor, the Click GUI's animations and interface
 * options, and the client modules (Main Menu, Custom UI) as cards. One softly scrolling page.
 */
final class SettingsPage {
	private static final float HEADING = 8f;
	private static final float GROUP_PAD = 12f;
	private static final float SECTION_GAP = 16f;
	private static final float PRESET_H = 32f;
	private static final float PRESET_GAP = 8f;
	private static final Theme.Preset[] PRESETS = Theme.Preset.values();

	private final List<SettingComponent<?>> theme = new ArrayList<>();
	private final List<SettingComponent<?>> animation = new ArrayList<>();
	private final List<SettingComponent<?>> ui = new ArrayList<>();
	private final List<SettingComponent<?>> all = new ArrayList<>();
	private final List<ModuleCard> cards;
	private final Anim[] presetHover = new Anim[PRESETS.length];
	/** Preset swatch layout of the last frame: x and y per preset. */
	private final float[] presetPos = new float[PRESETS.length * 2];
	private float presetW;
	private final Scroller scroller = new Scroller();
	private float viewX;
	private float viewY;
	private float viewW;
	private float viewH;
	private float contentHeight = 400f;

	SettingsPage(List<ModuleCard> clientCards) {
		this.cards = clientCards;
		for (int i = 0; i < PRESETS.length; i++) presetHover[i] = new Anim(0f, 20f);
		ThemeModule themeModule = ModuleManager.get(ThemeModule.class);
		if (themeModule != null) {
			for (Setting<?> setting : themeModule.settings()) if (setting != themeModule.preset) theme.add(SettingComponent.of(setting));
		}
		ClickGuiModule gui = ModuleManager.get(ClickGuiModule.class);
		if (gui != null) {
			animation.add(SettingComponent.of(gui.openAnimation));
			animation.add(SettingComponent.of(gui.closeAnimation));
			animation.add(SettingComponent.of(gui.animationSpeed));
			animation.add(SettingComponent.of(gui.animations));
			ui.add(SettingComponent.of(gui.scale));
			ui.add(SettingComponent.of(gui.dimBackground));
		}
		all.addAll(theme);
		all.addAll(animation);
		all.addAll(ui);
	}

	void reset() {
		scroller.reset();
	}

	void render(DrawContext ctx, float x, float y, float w, float h, double mx, double my, @Nullable Module drawerModule, boolean mouseFree) {
		viewX = x;
		viewY = y;
		viewW = w;
		viewH = h;
		float scroll = scroller.update(contentHeight, h);
		boolean inView = mouseFree && Ui.inside(mx, my, x, y, w, h);
		double cmx = inView ? mx : -1000;
		double cmy = inView ? my : -1000;
		ctx.enableScissor((int) Math.floor(x - 6f), (int) Math.floor(y), (int) Math.ceil(x + w + 6f), (int) Math.ceil(y + h));
		float top = y + 2f - scroll;
		float cy = top;
		float gw = w;
		float gx = x;

		cy = section(ctx, gx, cy, gw, y, h, Icons.PALETTE, "Theme");
		cy = presets(ctx, gx, cy, gw, y, h, cmx, cmy);
		cy = group(ctx, theme, gx, cy + 8f, gw, y, h, cmx, cmy) + SECTION_GAP;
		cy = section(ctx, gx, cy, gw, y, h, Icons.SPARKLES, "Animations");
		cy = group(ctx, animation, gx, cy, gw, y, h, cmx, cmy) + SECTION_GAP;
		cy = section(ctx, gx, cy, gw, y, h, Icons.APP_WINDOW, "Interface");
		cy = group(ctx, ui, gx, cy, gw, y, h, cmx, cmy) + SECTION_GAP;
		if (!cards.isEmpty()) {
			cy = section(ctx, gx, cy, gw, y, h, Icons.MONITOR, "Client");
			int cols = Math.max(2, ModuleGrid.columns(gw));
			float cw = (gw - Ui.CARD_GAP * (cols - 1)) / cols;
			float alpha = Render2D.alpha();
			for (int i = 0; i < cards.size(); i++) {
				ModuleCard card = cards.get(i);
				float cx = gx + (i % cols) * (cw + Ui.CARD_GAP);
				float ccy = cy + (i / cols) * (Ui.CARD_HEIGHT + Ui.CARD_GAP);
				Render2D.setAlpha(alpha * Ui.edgeFade(ccy, ccy + Ui.CARD_HEIGHT, y, y + h));
				card.render(ctx, cx, ccy, cw, cmx, cmy, card.module == drawerModule);
			}
			Render2D.setAlpha(alpha);
			int rows = (cards.size() + cols - 1) / cols;
			cy += rows * (Ui.CARD_HEIGHT + Ui.CARD_GAP);
		}
		contentHeight = cy - top + 4f;
		ctx.disableScissor();
		scroller.drawBar(ctx, x + w + 4f, y, h, contentHeight, inView);
	}

	private static float section(DrawContext ctx, float x, float y, float w, float viewY, float viewH, int icon, String title) {
		float alpha = Render2D.alpha();
		Render2D.setAlpha(alpha * Ui.edgeFade(y, y + HEADING, viewY, viewY + viewH));
		Ui.heading(ctx, x, y, icon, title);
		Render2D.setAlpha(alpha);
		return y + HEADING + 8f;
	}

	/** Preset swatches: the preset's base with its two accents; the selected one wears an accent rim and a check. */
	private float presets(DrawContext ctx, float x, float y, float w, float viewY, float viewH, double mx, double my) {
		ThemeModule module = ModuleManager.get(ThemeModule.class);
		if (module == null) return y;
		int cols = w >= 420f ? 5 : 4;
		presetW = (w - PRESET_GAP * (cols - 1)) / cols;
		float alpha = Render2D.alpha();
		for (int i = 0; i < PRESETS.length; i++) {
			Theme.Preset p = PRESETS[i];
			float px = x + (i % cols) * (presetW + PRESET_GAP);
			float py = y + (i / cols) * (PRESET_H + PRESET_GAP);
			presetPos[i * 2] = px;
			presetPos[i * 2 + 1] = py;
			boolean selected = module.preset.is(p.title());
			float hv = presetHover[i].target(Ui.inside(mx, my, px, py, presetW, PRESET_H) ? 1f : 0f).get();
			int a1 = p == Theme.Preset.CUSTOM ? module.accent.argb() : p.accent();
			int a2 = p == Theme.Preset.CUSTOM ? module.accent2.argb() : p.accent2();
			Render2D.setAlpha(alpha * Ui.edgeFade(py, py + PRESET_H, viewY, viewY + viewH));
			Render2D.matte(ctx, px, py - hv, presetW, PRESET_H, Ui.CARD_RADIUS, ColorUtil.withAlpha(ColorUtil.brighten(p.base(), 0.05f + 0.04f * hv), 235), Ui.LIT);
			Render2D.rim(ctx, px, py - hv, presetW, PRESET_H, Ui.CARD_RADIUS, selected ? a1 : ColorUtil.withAlpha(0xFFFFFF, Math.round(20 + 24 * hv)), Ui.LIT);
			Render2D.textMiddle(ctx, Fonts.medium(), p.title(), px + 8f, py - hv + 11f, 6.5f, selected ? Theme.text() : Theme.textDim());
			// The preset's own two colours, as a preview.
			Render2D.gradientH(ctx, px + 8f, py - hv + PRESET_H - 10f, presetW - 16f, 2f, 1f, a1, a2);
			if (selected) Render2D.icon(ctx, Icons.CHECK, px + presetW - 10f, py - hv + 11f, 6.5f, a1);
		}
		Render2D.setAlpha(alpha);
		int rows = (PRESETS.length + cols - 1) / cols;
		return y + rows * (PRESET_H + PRESET_GAP) - PRESET_GAP;
	}

	/** A matte group box with the components flowing into two balanced columns; returns its bottom. */
	private static float group(DrawContext ctx, List<SettingComponent<?>> components, float x, float y, float w, float viewY, float viewH,
							   double mx, double my) {
		// Components alternate between the columns, so one that unfolds (a colour picker) never moves the others across.
		float colW = (w - GROUP_PAD * 3f) / 2f;
		float left = 0f;
		float right = 0f;
		int index = 0;
		for (SettingComponent<?> c : components) {
			if (!c.visible()) continue;
			if (index++ % 2 == 0) left += c.height() + 4f;
			else right += c.height() + 4f;
		}
		float h = Math.max(left, right) - 4f + GROUP_PAD * 2f;
		float alpha = Render2D.alpha();
		Render2D.setAlpha(alpha * Ui.edgeFade(y, y + Math.min(h, 30f), viewY, viewY + viewH) * Ui.edgeFade(y + h - 30f, y + h, viewY, viewY + viewH));
		Render2D.matte(ctx, x, y, w, h, Ui.PANEL_RADIUS, Theme.raised(), Ui.LIT);
		Render2D.rim(ctx, x, y, w, h, Ui.PANEL_RADIUS, Ui.HAIRLINE, Ui.LIT);
		float ly = y + GROUP_PAD;
		float ry = y + GROUP_PAD;
		index = 0;
		for (SettingComponent<?> c : components) {
			if (!c.visible()) continue;
			boolean toLeft = index++ % 2 == 0;
			float cy = toLeft ? ly : ry;
			Render2D.setAlpha(alpha * Ui.edgeFade(cy, cy + Math.min(c.height(), 22f), viewY, viewY + viewH));
			float used = c.render(ctx, toLeft ? x + GROUP_PAD : x + GROUP_PAD * 2f + colW, cy, colW, mx, my) + 4f;
			if (toLeft) ly += used;
			else ry += used;
		}
		Render2D.setAlpha(alpha);
		return y + h;
	}

	boolean over(double mx, double my) {
		return Ui.inside(mx, my, viewX, viewY, viewW, viewH);
	}

	@Nullable
	ModuleCard cardAt(double mx, double my) {
		if (!over(mx, my)) return null;
		for (ModuleCard card : cards) if (card.over(mx, my)) return card;
		return null;
	}

	/** Presets and setting components; module cards are handled by the screen (cardAt). */
	boolean mouseClicked(double mx, double my, int button) {
		if (!over(mx, my)) return false;
		ThemeModule module = ModuleManager.get(ThemeModule.class);
		for (int i = 0; i < PRESETS.length; i++) {
			if (module != null && Ui.inside(mx, my, presetPos[i * 2], presetPos[i * 2 + 1], presetW, PRESET_H)) {
				module.preset.set(PRESETS[i].title());
				return true;
			}
		}
		for (SettingComponent<?> c : all) if (c.visible() && c.mouseClicked(mx, my, button)) return true;
		return false;
	}

	void mouseDragged(double mx, double my) {
		for (SettingComponent<?> c : all) c.mouseDragged(mx, my);
	}

	void mouseReleased() {
		for (SettingComponent<?> c : all) c.mouseReleased();
	}

	void scroll(double amount) {
		scroller.scroll(amount);
	}

	boolean isBinding() {
		for (SettingComponent<?> c : all) if (c instanceof KeyComponent key && key.listening()) return true;
		return false;
	}

	boolean keyPressed(int key) {
		for (SettingComponent<?> c : all) if (c.keyPressed(key)) return true;
		return false;
	}
}
