package dev.v1zir.visuals.module.impl.hud;

import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.font.Icons;
import dev.v1zir.visuals.render.font.SdfFont;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;

import java.util.Arrays;

/** Active status effects with their vanilla icon, level and remaining time. */
public final class PotionsModule extends HudModule {
	private static final String[] ROMAN = {"", " II", " III", " IV", " V", " VI", " VII", " VIII", " IX", " X"};
	/** Icon size in GUI pixels: half the 18 px sprite, so it maps onto whole screen pixels at the usual GUI scales. */
	private static final int ICON = 9;

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
		if (mc.player == null) return false;
		for (StatusEffectInstance effect : mc.player.getStatusEffects()) if (effect.shouldShowIcon()) return true;
		return false;
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
			// Hidden effects stay off the HUD, like vanilla's.
			if (!effect.shouldShowIcon()) continue;
			if (count == rows.length) rows = Arrays.copyOf(rows, rows.length * 2);
			Row row = rows[count];
			if (row == null) rows[count] = row = new Row();
			row.update(effect, font, timeFont, size);
			w = Math.max(w, row.nameWidth + row.timeWidth + 34f);
			count++;
		}
		float h = 18f + Math.max(1, count) * rowH + 3f;
		width = w;
		height = h;
		float cy = headerPanel(ctx, x, y, w, h, Icons.FLASK_CONICAL, "Potions");
		for (int i = 0; i < count; i++) {
			Row row = rows[i];
			float blink = blink(row.effect, tickDelta);
			// Sprites are pixel art: whole GUI pixels keep them crisp.
			ctx.drawGuiTexture(RenderPipelines.GUI_TEXTURED, row.icon, Math.round(x + 5f), Math.round(cy + (rowH - ICON) / 2f), ICON, ICON,
				blink * Render2D.alpha());
			Render2D.textMiddle(ctx, font, row.name, x + 8f + ICON, cy + rowH / 2f, size, Theme.text());
			Render2D.textMiddle(ctx, timeFont, row.time, x + w - 7f - row.timeWidth, cy + rowH / 2f, size,
				ColorUtil.fade(Theme.textDim(), 0.4f + 0.6f * blink));
			cy += rowH;
		}
		// Drop references to effects that ended, so they can be collected.
		for (int i = count; i < rows.length && rows[i] != null; i++) rows[i].effect = null;
	}

	/**
	 * Vanilla's icon opacity: full, then over the last 10 seconds about half with a blink that deepens towards the
	 * end (smoothed between ticks). Ambient (beacon) and infinite effects never blink.
	 */
	private static float blink(StatusEffectInstance effect, float tickDelta) {
		int duration = effect.getDuration();
		if (effect.isAmbient() || effect.isInfinite() || duration >= 200) return 1f;
		float ticks = Math.max(0f, duration - tickDelta);
		float f = Math.min(ticks / 100f, 0.5f)
			+ (float) Math.cos(ticks * Math.PI / 5.0) * Math.min((10 - duration / 20) / 40f, 0.25f);
		return Math.max(0f, Math.min(1f, f));
	}

	/** One effect row; its icon, name and time strings are rebuilt only when the effect, its level or the shown second changes. */
	private static final class Row {
		StatusEffectInstance effect;
		Identifier icon;
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
				icon = InGameHud.getEffectTexture(type);
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
