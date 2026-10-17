package dev.v1zir.visuals.module.impl.hud;

import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.font.Icons;
import dev.v1zir.visuals.render.font.SdfFont;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;

import java.util.ArrayList;
import java.util.List;

/** Active status effects with remaining time. */
public final class PotionsModule extends HudModule {
	private static final String[] ROMAN = {"", " II", " III", " IV", " V", " VI", " VII", " VIII", " IX", " X"};

	public PotionsModule() {
		super("Potions", "Shows active effects", 0.008f, 0.42f);
	}

	@Override
	public boolean showInList() {
		return false;
	}

	@Override
	public boolean hasContent() {
		return mc.player != null && !mc.player.getStatusEffects().isEmpty();
	}

	@Override
	protected void render(DrawContext ctx, float tickDelta, float x, float y) {
		if (mc.player == null) return;
		List<StatusEffectInstance> effects = new ArrayList<>(mc.player.getStatusEffects());
		SdfFont font = Fonts.medium();
		SdfFont timeFont = Fonts.semibold();
		float size = 7f;
		float rowH = 12f;
		List<String> names = new ArrayList<>();
		List<String> times = new ArrayList<>();
		float w = 100f;
		for (StatusEffectInstance effect : effects) {
			int amp = effect.getAmplifier();
			String name = effect.getEffectType().value().getName().getString() + (amp > 0 && amp < ROMAN.length ? ROMAN[amp] : amp > 0 ? " " + (amp + 1) : "");
			String time = effect.isInfinite() ? "∞" : format(effect.getDuration());
			names.add(name);
			times.add(time);
			w = Math.max(w, font.width(name, size) + timeFont.width(time, size) + 30f);
		}
		float h = 18f + Math.max(1, effects.size()) * rowH + 3f;
		width = w;
		height = h;
		float cy = headerPanel(ctx, x, y, w, h, Icons.FLASK_CONICAL, "Potions");
		for (int i = 0; i < effects.size(); i++) {
			StatusEffect type = effects.get(i).getEffectType().value();
			int color = ColorUtil.rgb(type.getColor());
			boolean ending = !effects.get(i).isInfinite() && effects.get(i).getDuration() < 200;
			float blink = ending ? 0.55f + 0.45f * (float) Math.sin(System.nanoTime() / 1.0e9 * 8.0) : 1f;
			Render2D.circle(ctx, x + 9f, cy + rowH / 2f, 2.2f, ColorUtil.fade(color, blink));
			Render2D.textMiddle(ctx, font, names.get(i), x + 15f, cy + rowH / 2f, size, Theme.text());
			String time = times.get(i);
			float tw = timeFont.width(time, size);
			Render2D.textMiddle(ctx, timeFont, time, x + w - 7f - tw, cy + rowH / 2f, size, ColorUtil.fade(Theme.textDim(), blink));
			cy += rowH;
		}
	}

	private static String format(int ticks) {
		int seconds = ticks / 20;
		return String.format("%d:%02d", seconds / 60, seconds % 60);
	}
}
