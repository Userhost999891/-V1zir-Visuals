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
import net.minecraft.registry.entry.RegistryEntry;

import java.util.Arrays;

/** Active status effects with remaining time. */
public final class PotionsModule extends HudModule {
	private static final String[] ROMAN = {"", " II", " III", " IV", " V", " VI", " VII", " VIII", " IX", " X"};

	private Row[] rows = new Row[4];

	public PotionsModule() {
		super("Potions", "Shows active effects", 0.008f, 0.42f);
		setSubCategory("Widgets");
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
		SdfFont font = Fonts.medium();
		SdfFont timeFont = Fonts.semibold();
		float size = 7f;
		float rowH = 12f;
		int count = 0;
		float w = 100f;
		for (StatusEffectInstance effect : mc.player.getStatusEffects()) {
			if (count == rows.length) rows = Arrays.copyOf(rows, rows.length * 2);
			Row row = rows[count];
			if (row == null) rows[count] = row = new Row();
			row.update(effect, font, timeFont, size);
			w = Math.max(w, row.nameWidth + row.timeWidth + 30f);
			count++;
		}
		float h = 18f + Math.max(1, count) * rowH + 3f;
		width = w;
		height = h;
		float cy = headerPanel(ctx, x, y, w, h, Icons.FLASK_CONICAL, "Potions");
		for (int i = 0; i < count; i++) {
			Row row = rows[i];
			StatusEffectInstance effect = row.effect;
			int color = ColorUtil.rgb(effect.getEffectType().value().getColor());
			boolean ending = !effect.isInfinite() && effect.getDuration() < 200;
			float blink = ending ? 0.55f + 0.45f * (float) Math.sin(System.nanoTime() / 1.0e9 * 8.0) : 1f;
			Render2D.circle(ctx, x + 9f, cy + rowH / 2f, 2.2f, ColorUtil.fade(color, blink));
			Render2D.textMiddle(ctx, font, row.name, x + 15f, cy + rowH / 2f, size, Theme.text());
			Render2D.textMiddle(ctx, timeFont, row.time, x + w - 7f - row.timeWidth, cy + rowH / 2f, size, ColorUtil.fade(Theme.textDim(), blink));
			cy += rowH;
		}
		// Drop references to effects that ended, so they can be collected.
		for (int i = count; i < rows.length && rows[i] != null; i++) rows[i].effect = null;
	}

	/** One effect row; its name and time strings are rebuilt only when the effect, its level or the shown second changes. */
	private static final class Row {
		StatusEffectInstance effect;
		String name;
		String time;
		float nameWidth;
		float timeWidth;
		private RegistryEntry<StatusEffect> type;
		private int amplifier;
		private int seconds = Integer.MIN_VALUE;
		private SdfFont font;
		private SdfFont timeFont;

		void update(StatusEffectInstance effect, SdfFont font, SdfFont timeFont, float size) {
			this.effect = effect;
			boolean fontChanged = font != this.font || timeFont != this.timeFont;
			this.font = font;
			this.timeFont = timeFont;
			int amp = effect.getAmplifier();
			if (fontChanged || effect.getEffectType() != type || amp != amplifier) {
				type = effect.getEffectType();
				amplifier = amp;
				name = type.value().getName().getString() + (amp > 0 && amp < ROMAN.length ? ROMAN[amp] : amp > 0 ? " " + (amp + 1) : "");
				nameWidth = font.width(name, size);
			}
			int s = effect.isInfinite() ? -1 : effect.getDuration() / 20;
			if (fontChanged || s != seconds) {
				seconds = s;
				time = s < 0 ? "∞" : s / 60 + ":" + (s % 60 < 10 ? "0" : "") + s % 60;
				timeWidth = timeFont.width(time, size);
			}
		}
	}
}
