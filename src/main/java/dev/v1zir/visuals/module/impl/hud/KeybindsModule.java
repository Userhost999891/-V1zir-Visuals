package dev.v1zir.visuals.module.impl.hud;

import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.font.Icons;
import dev.v1zir.visuals.render.font.SdfFont;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.KeyNames;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** List of modules that have a keybind, with their state. */
public final class KeybindsModule extends HudModule {
	private final List<Module> bound = new ArrayList<>();
	/** The bound modules and the panel width, rebuilt only when a module is toggled or rebound or the font changes. */
	private int builtFor = -1;
	private SdfFont builtFont;
	private float panelW;

	public KeybindsModule() {
		super("Keybinds", "Shows bound modules", 0.008f, 0.25f);
		setSubCategory("Widgets");
	}

	@Override
	public boolean showInList() {
		return false;
	}

	/** Hidden in game while nothing is bound; the HUD editor still shows it with a hint. */
	@Override
	public boolean hasContent() {
		rebuild(Fonts.medium(), Fonts.semibold());
		return !bound.isEmpty();
	}

	private void rebuild(SdfFont font, SdfFont keyFont) {
		if (builtFor == ModuleManager.version() && builtFont == font) return;
		builtFor = ModuleManager.version();
		builtFont = font;
		bound.clear();
		panelW = 96f;
		for (Module module : ModuleManager.all()) {
			if (module.key() == GLFW.GLFW_KEY_UNKNOWN || !module.canToggle()) continue;
			bound.add(module);
			panelW = Math.max(panelW, font.width(module.name(), 7f) + keyFont.width(KeyNames.name(module.key()), 6.5f) + 34f);
		}
	}

	@Override
	protected void render(DrawContext ctx, float tickDelta, float x, float y) {
		SdfFont font = Fonts.medium();
		SdfFont keyFont = Fonts.semibold();
		rebuild(font, keyFont);
		float size = 7f;
		float rowH = 12f;
		float w = panelW;
		float h = 18f + Math.max(1, bound.size()) * rowH + 3f;
		width = w;
		height = h;
		float cy = headerPanel(ctx, x, y, w, h, Icons.KEYBOARD, "Keybinds");
		if (bound.isEmpty()) {
			Render2D.textMiddle(ctx, font, "Middle click a module to bind", x + 8f, cy + rowH / 2f, 6.5f, Theme.textFaint());
			return;
		}
		for (int i = 0; i < bound.size(); i++) {
			Module module = bound.get(i);
			boolean on = module.isEnabled();
			Render2D.circle(ctx, x + 9f, cy + rowH / 2f, 2f, on ? Theme.accent() : 0x40FFFFFF);
			Render2D.textMiddle(ctx, font, module.name(), x + 15f, cy + rowH / 2f, size, on ? Theme.text() : Theme.textDim());
			String key = KeyNames.name(module.key());
			float kw = keyFont.width(key, 6.5f) + 8f;
			float kx = x + w - 6f - kw;
			Render2D.round(ctx, kx, cy + 1.5f, kw, rowH - 3f, 3f, ColorUtil.withAlpha(on ? Theme.accent() : 0xFFFFFF, on ? 60 : 18));
			Render2D.textMiddle(ctx, keyFont, key, kx + 4f, cy + rowH / 2f, 6.5f, on ? Theme.text() : Theme.textDim());
			cy += rowH;
		}
	}
}
