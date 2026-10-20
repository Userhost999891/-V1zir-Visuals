package dev.v1zir.visuals.module.impl.hud;

import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.font.SdfFont;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Enabled modules, sorted by width, sliding in and out. */
public final class ArrayListModule extends HudModule {
	public final BoolSetting suffixes = add(new BoolSetting("Suffixes", true));
	public final BoolSetting background = add(new BoolSetting("Background", true));
	public final BoolSetting bar = add(new BoolSetting("Accent bar", true));

	private final Map<Module, Anim> animations = new HashMap<>();

	public ArrayListModule() {
		super("Array List", "Lists enabled modules", 0.995f, 0.012f);
		setSubCategory("Info");
	}

	@Override
	public boolean showInList() {
		return false;
	}

	@Override
	protected void render(DrawContext ctx, float tickDelta, float x, float y) {
		SdfFont font = Fonts.medium();
		float size = 7.5f;
		float rowH = 12.5f;
		List<Module> shown = new ArrayList<>();
		for (Module module : ModuleManager.all()) {
			if (!module.showInList()) continue;
			Anim anim = animations.computeIfAbsent(module, m -> new Anim(0f, 12f));
			anim.target(module.isEnabled() ? 1f : 0f);
			if (anim.get() > 0.01f) shown.add(module);
		}
		shown.sort(Comparator.comparingDouble((Module m) -> -label(m, font, size)));
		float maxW = 40f;
		for (Module m : shown) maxW = Math.max(maxW, label(m, font, size) + 12f);
		width = maxW;
		float total = 0f;
		for (Module m : shown) total += rowH * animations.get(m).value();
		height = Math.max(rowH, total);

		boolean right = anchoredRight();
		float cy = y;
		int index = 0;
		for (Module module : shown) {
			float t = animations.get(module).value();
			float w = label(module, font, size) + 12f;
			float rx = right ? x + width - w : x;
			float slide = (1f - t) * (w + 10f) * (right ? 1f : -1f);
			Render2D.setAlpha(t);
			int accent = Theme.accentWave(index * 0.08f);
			if (background.on()) {
				Render2D.rect(ctx, rx + slide, cy, w, rowH * t, Theme.panel());
			}
			if (bar.on()) {
				float bx = right ? rx + w - 1.5f : rx;
				Render2D.glow(ctx, bx + slide, cy, 1.5f, rowH * t, 0.75f, 4f, ColorUtil.withAlpha(accent, 140), ColorUtil.withAlpha(accent, 140));
				Render2D.rect(ctx, bx + slide, cy, 1.5f, rowH * t, accent);
			}
			float tx = rx + slide + (right ? 5f : 6.5f);
			float advance = Render2D.textMiddle(ctx, font, module.name(), tx, cy + rowH * t / 2f, size, ColorUtil.lerp(Theme.text(), accent, 0.25f));
			String suffix = suffixes.on() ? module.suffix() : null;
			if (suffix != null) Render2D.textMiddle(ctx, font, suffix, tx + advance + 4f, cy + rowH * t / 2f, size, Theme.textDim());
			Render2D.setAlpha(1f);
			cy += rowH * t;
			index++;
		}
	}

	private float label(Module module, SdfFont font, float size) {
		String suffix = suffixes.on() ? module.suffix() : null;
		return font.width(module.name(), size) + (suffix != null ? font.width(suffix, size) + 4f : 0f);
	}
}
