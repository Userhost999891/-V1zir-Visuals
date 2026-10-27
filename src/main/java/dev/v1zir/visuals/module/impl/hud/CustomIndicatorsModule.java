package dev.v1zir.visuals.module.impl.hud;

import dev.v1zir.visuals.V1zir;
import dev.v1zir.visuals.gui.clickgui.Label;
import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.module.setting.ModeSetting;
import dev.v1zir.visuals.module.setting.NumberSetting;
import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.font.Icons;
import dev.v1zir.visuals.render.font.SdfFont;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.bar.ExperienceBar;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * Health, food and the other status rows as sleek bars with numbers, in place of vanilla's rows of icons above the
 * hotbar: health with a damage trail, absorption in gold, a regeneration pulse and the colours of poison, wither and
 * frost; food with its saturation; armour, air, the mount's health and experience on request. HudRenderer hides each
 * vanilla row this draws while it is on ({@link #replaces}); creative and spectator show none, as in vanilla.
 */
public final class CustomIndicatorsModule extends HudModule {
	/** Vanilla's rows this can draw instead. */
	public static final Identifier[] ROWS = {VanillaHudElements.HEALTH_BAR, VanillaHudElements.FOOD_BAR, VanillaHudElements.ARMOR_BAR,
		VanillaHudElements.AIR_BAR, VanillaHudElements.MOUNT_HEALTH, VanillaHudElements.INFO_BAR, VanillaHudElements.EXPERIENCE_LEVEL};
	/** How long the damage trail waits before it catches up, in seconds. */
	private static final float TRAIL_HOLD = 0.35f;
	private static final float ROW_GAP = 2f;
	private static final int ABSORPTION = 0xFFFFD34D;

	public final ModeSetting style = add(new ModeSetting("Style", "Bars", "Bars", "Compact"));
	public final BoolSetting numbers = add(new BoolSetting("Numbers", true));
	public final BoolSetting armor = add(new BoolSetting("Armor", true));
	public final BoolSetting air = add(new BoolSetting("Air", true));
	public final BoolSetting experience = add(new BoolSetting("Experience", false));
	public final BoolSetting mount = add(new BoolSetting("Mount", true));
	public final NumberSetting barWidth = add(new NumberSetting("Width", 182, 120, 300, 2).suffix("px"));
	public final ModeSetting position = add(new ModeSetting("Position", "Above hotbar", "Above hotbar", "Free"));

	private final Gauge health = new Gauge(0xFFD63A4A, 0xFFFF6E6A, new Count("", ""), true);
	private final Gauge food = new Gauge(0xFFC9772E, 0xFFF2AD4E, new Count("", ""), false);
	private final Gauge armorBar = new Gauge(0xFF8393A8, 0xFFC4CFDD, new Count("", ""), false);
	private final Gauge airBar = new Gauge(0xFF3A9FD2, 0xFF8FDBFA, new Count("", "s"), false);
	private final Gauge mountBar = new Gauge(0xFFC2803F, 0xFFEAB57C, new Count("", ""), true);
	private final Gauge xp = new Gauge(0, 0, new Count("", ""), false);
	private final Count absorption = new Count("+", "");
	private final Render2D.TextStyle label = Render2D.TextStyle.of(0xFFFFFFFF).shadow(0x70000000);

	public CustomIndicatorsModule() {
		super("Custom Indicators", "Sleek health and food bars with numbers", 0.5f, 0.85f);
		setSubCategory("Info");
		width = 182f;
		height = 9f;
	}

	@Override
	public boolean showInList() {
		return false;
	}

	@Override
	protected boolean centered() {
		return true;
	}

	@Override
	public boolean hasContent() {
		return mc.player != null && mc.interactionManager != null && mc.interactionManager.hasStatusBars();
	}

	/** Whether the vanilla HUD row is drawn by this module instead right now (HudRenderer). */
	public boolean replaces(Identifier row) {
		if (!isEnabled()) return false;
		if (row == VanillaHudElements.ARMOR_BAR) return armor.on();
		if (row == VanillaHudElements.AIR_BAR) return air.on();
		if (row == VanillaHudElements.MOUNT_HEALTH) return mount.on();
		if (row == VanillaHudElements.EXPERIENCE_LEVEL) return experience.on();
		// The same strip also shows the locator and the jump bar: only the experience bar is replaced.
		if (row == VanillaHudElements.INFO_BAR) return experience.on() && vanillaExperience();
		return true;
	}

	private static boolean vanillaExperience() {
		return mc.inGameHud.currentBar != null && mc.inGameHud.currentBar.getRight() instanceof ExperienceBar;
	}

	private boolean docked() {
		return position.is("Above hotbar");
	}

	/** Docked, it sits where vanilla's rows are; with experience on, its own bar takes the experience strip too. */
	@Override
	public float x(int screenWidth) {
		return docked() ? (screenWidth - width) / 2f : super.x(screenWidth);
	}

	@Override
	public float y(int screenHeight) {
		return docked() ? screenHeight - (experience.on() ? 24f : 30f) - height : super.y(screenHeight);
	}

	@Override
	public void moveTo(float x, float y, int screenWidth, int screenHeight) {
		// Dragged in the HUD editor, it leaves the hotbar.
		position.set("Free");
		super.moveTo(x, y, screenWidth, screenHeight);
	}

	@Override
	protected void render(DrawContext ctx, float tickDelta, float x, float y) {
		PlayerEntity p = mc.player;
		if (p == null) return;
		float now = V1zir.time();
		boolean compact = style.is("Compact");

		// Values and states.
		float max = Math.max(1f, p.getMaxHealth());
		float hp = Math.max(0f, p.getHealth());
		float abs = p.getAbsorptionAmount();
		health.set(hp / max, now);
		health.extra.target(Math.min(1f, abs / max));
		health.label.set(MathHelper.ceil(hp), MathHelper.ceil(max));
		absorption.set(MathHelper.ceil(abs), -1);
		if (p.hasStatusEffect(StatusEffects.WITHER)) health.tone(0xFF34343A, 0xFF707079);
		else if (p.hasStatusEffect(StatusEffects.POISON)) health.tone(0xFF6A9A2C, 0xFFA8D64E);
		else if (p.isFrozen()) health.tone(0xFF6FAEE3, 0xFFCDEBFF);
		else health.tone(0xFFD63A4A, 0xFFFF6E6A);
		health.pulse = p.hasStatusEffect(StatusEffects.REGENERATION) ? 0.5f + 0.5f * MathHelper.sin(now * 6f) : 0f;

		int foodLevel = p.getHungerManager().getFoodLevel();
		food.set(foodLevel / 20f, now);
		food.extra.target(Math.min(1f, p.getHungerManager().getSaturationLevel() / 20f));
		food.label.set(foodLevel, 20);
		if (p.hasStatusEffect(StatusEffects.HUNGER)) food.tone(0xFF738A35, 0xFFAABD5C);
		else food.tone(0xFFC9772E, 0xFFF2AD4E);

		armorBar.shown.target(armor.on() && p.getArmor() > 0 ? 1f : 0f);
		armorBar.set(p.getArmor() / 20f, now);
		armorBar.label.set(p.getArmor(), -1);
		int maxAir = Math.max(1, p.getMaxAir());
		airBar.shown.target(air.on() && (p.isSubmergedIn(FluidTags.WATER) || p.getAir() < maxAir) ? 1f : 0f);
		airBar.set(Math.max(0, p.getAir()) / (float) maxAir, now);
		airBar.label.set(MathHelper.ceil(Math.max(0, p.getAir()) / 20f), -1);
		LivingEntity vehicle = mount.on() && p.getVehicle() instanceof LivingEntity living ? living : null;
		mountBar.shown.target(vehicle != null ? 1f : 0f);
		if (vehicle != null) {
			float vmax = Math.max(1f, vehicle.getMaxHealth());
			mountBar.set(vehicle.getHealth() / vmax, now);
			mountBar.label.set(MathHelper.ceil(vehicle.getHealth()), MathHelper.ceil(vmax));
		}
		// Docked, the experience strip shows vanilla's locator or jump bar when that is up instead.
		boolean xpRow = experience.on() && mc.interactionManager != null && mc.interactionManager.hasExperienceBar();
		xp.shown.target(xpRow && (!docked() || vanillaExperience()) ? 1f : 0f);
		xp.set(p.experienceProgress, now);
		xp.label.set(p.experienceLevel, -1);
		xp.tone(Theme.accent(), Theme.accent2());

		// Layout, bottom up: experience across, then health under armour on the left, food under mount and air on the right.
		float row = compact ? 7f : 9f;
		float pad = compact ? 4f : 0f;
		float w = barWidth.f();
		float gapX = docked() && !experience.on() ? 20f : 6f;
		float column = (w - pad * 2f - gapX) / 2f;
		float armorS = armorBar.shown.get();
		float mountS = mountBar.shown.get();
		float airS = airBar.shown.get();
		float xpS = xpRow && docked() ? 1f : xp.shown.get();
		float left = row + armorS * (row + ROW_GAP);
		float right = row + (mountS + airS) * (row + ROW_GAP);
		width = w;
		height = Math.max(left, right) + xpS * (row + ROW_GAP) + pad * 2f;
		if (compact) panel(ctx, x, y, w, height, 6f);

		float lx = x + pad;
		float rx = lx + column + gapX;
		float bottom = y + height - pad;
		float base = Render2D.alpha();
		if (xpS > 0.01f) bar(ctx, xp, lx, bottom - row, w - pad * 2f, row, compact, false, Icons.SPARKLE, base * xp.shown.get());
		bottom -= xpS * (row + ROW_GAP);
		bar(ctx, health, lx, bottom - row, column, row, compact, false, Icons.HEART, base);
		bar(ctx, food, rx, bottom - row, column, row, compact, true, Icons.CHERRY, base);
		float top = bottom - row - ROW_GAP;
		if (armorS > 0.01f) bar(ctx, armorBar, lx, top - row, column, row, compact, false, Icons.SHIELD, base * armorS);
		if (mountS > 0.01f) bar(ctx, mountBar, rx, top - row, column, row, compact, true, Icons.HEART, base * mountS);
		top -= mountS * (row + ROW_GAP);
		if (airS > 0.01f) bar(ctx, airBar, rx, top - row, column, row, compact, true, Icons.DROPLETS, base * airS);
		Render2D.setAlpha(base);
	}

	/**
	 * One bar at (x, y): Bars draws a pill with the icon and number inside, Compact a thin bar beside them. A mirrored
	 * bar (the right column) fills from its right end, as vanilla's food row empties towards the right.
	 */
	private void bar(DrawContext ctx, Gauge g, float x, float y, float w, float h, boolean compact, boolean mirror, int icon, float alpha) {
		Render2D.setAlpha(alpha);
		SdfFont font = Fonts.semibold();
		float size = compact ? 6f : 6.2f;
		String text = numbers.on() ? g.label.text() : "";
		float textW = text.isEmpty() ? 0f : g.label.width(font, size);
		boolean extension = g == health && absorption.value() > 0;
		float suffixW = extension && numbers.on() ? absorption.width(font, size) : 0f;
		float labelW = h + (textW > 0f ? textW + 2f : 0f) + (suffixW > 0f ? suffixW + 2f : 0f);

		// The track: a pill of its own, or a thin groove beside the label.
		float bx, by, bw, bh;
		if (compact) {
			bw = Math.max(0f, w - labelW - 3f);
			bh = 3f;
			bx = mirror ? x : x + w - bw;
			by = y + (h - bh) * 0.5f;
			Render2D.round(ctx, bx, by, bw, bh, bh * 0.5f, ColorUtil.withAlpha(0xFFFFFF, 22));
		} else {
			panel(ctx, x, y, w, h, h * 0.5f);
			bx = x + 1.5f;
			by = y + 1.5f;
			bw = w - 3f;
			bh = h - 3f;
		}
		float r = bh * 0.5f;
		float fill = g.fill.get();
		float trail = g.trail.get();
		float extra = g.extra.get();
		int c1 = g.c1();
		int c2 = g.c2();
		if (g.pulse > 0f) {
			c1 = ColorUtil.brighten(c1, 0.22f * g.pulse);
			c2 = ColorUtil.brighten(c2, 0.22f * g.pulse);
		}
		// Absorption runs on past the health in gold, underneath it.
		if (extension && extra > 0.001f) segment(ctx, bx, by, bw, bh, r, Math.min(1f, fill + extra), mirror, 0xFFE0A526, ABSORPTION);
		if (trail > fill + 0.002f) segment(ctx, bx, by, bw, bh, r, trail, mirror, ColorUtil.withAlpha(c2, 120), ColorUtil.withAlpha(0xFFFFFF, 110));
		if (fill > 0.001f) {
			float fw = bw * fill;
			float fx = mirror ? bx + bw - fw : bx;
			Render2D.glow(ctx, fx, by, fw, bh, r, 4f, ColorUtil.withAlpha(c1, Math.round(55 + 90 * g.pulse)), ColorUtil.withAlpha(c2, Math.round(55 + 90 * g.pulse)));
			segment(ctx, bx, by, bw, bh, r, fill, mirror, c1, c2);
			// Saturation: a light band inside the food.
			if (g == food && extra > 0.001f) {
				float sw = bw * Math.min(fill, extra);
				Render2D.round(ctx, mirror ? bx + bw - sw : bx, by + bh * 0.55f, sw, bh * 0.45f, bh * 0.22f, ColorUtil.withAlpha(0xFFF7D9, 85));
			}
		}

		// Icon and number at the anchored end.
		int text1 = Theme.text();
		float cy = y + h * 0.5f;
		float ix = mirror ? x + w - h * 0.5f - (compact ? 0f : 1f) : x + h * 0.5f + (compact ? 0f : 1f);
		Render2D.icon(ctx, icon, ix, cy, compact ? 6f : 5.6f, compact ? ColorUtil.lerp(c1, c2, 0.5f) : text1);
		if (textW > 0f) {
			float tx = mirror ? ix - h * 0.5f - 1f - textW : ix + h * 0.5f + 1f;
			label.color = text1;
			Render2D.textMiddle(ctx, font, text, tx, cy, size, label);
			if (suffixW > 0f) {
				label.color = ABSORPTION;
				Render2D.textMiddle(ctx, font, absorption.text(), tx + textW + 2f, cy, size, label);
			}
		}
	}

	/** The part 0 to t of a bar, from its anchored end, in a left to right gradient. */
	private static void segment(DrawContext ctx, float x, float y, float w, float h, float r, float t, boolean mirror, int c1, int c2) {
		float sw = w * Math.min(1f, t);
		if (mirror) Render2D.gradientH(ctx, x + w - sw, y, sw, h, r, c2, c1);
		else Render2D.gradientH(ctx, x, y, sw, h, r, c1, c2);
	}

	/** "a / b", or "a" when b is negative, with a prefix and suffix; rebuilt only when the numbers change. */
	private static final class Count {
		private final String prefix;
		private final String suffix;
		private final Label width = new Label();
		private int a = Integer.MIN_VALUE;
		private int b;
		private String text = "";

		Count(String prefix, String suffix) {
			this.prefix = prefix;
			this.suffix = suffix;
		}

		void set(int a, int b) {
			if (a == this.a && b == this.b) return;
			this.a = a;
			this.b = b;
			text = b < 0 ? prefix + NumberText.of(a) + suffix : prefix + a + " / " + b + suffix;
		}

		int value() {
			return a;
		}

		String text() {
			return text;
		}

		float width(SdfFont font, float size) {
			return width.width(font, text, size);
		}
	}

	/** A bar's springs, colours and number. */
	private static final class Gauge {
		final Anim fill = new Anim(0f, 9f);
		final Anim trail = new Anim(0f, 2.4f);
		/** Absorption (past the health) or saturation (inside the food). */
		final Anim extra = new Anim(0f, 8f);
		/** Optional rows fade and slide in and out. */
		final Anim shown = new Anim(0f, 10f);
		private final Anim tone = new Anim(1f, 6f);
		final Count label;
		/** Health bars leave a damage trail; the others just follow their value. */
		private final boolean trails;
		/** Regeneration: 0 to 1, brightens the fill. */
		float pulse;
		private int from1;
		private int from2;
		private int to1;
		private int to2;
		private float hold;

		Gauge(int c1, int c2, Count label, boolean trails) {
			from1 = to1 = c1;
			from2 = to2 = c2;
			this.label = label;
			this.trails = trails;
		}

		/** A drop leaves the trail where it was for a moment, then it catches up; a rise takes it along. */
		void set(float value, float now) {
			value = Anim.clamp01(value);
			if (value < fill.target()) hold = now + TRAIL_HOLD;
			fill.target(value);
			float f = fill.get();
			if (!trails || f >= trail.get()) trail.snap(f);
			else if (now >= hold) trail.target(value);
		}

		/** Blends to a new pair of fill colours (poison, wither, frost and back). */
		void tone(int c1, int c2) {
			if (c1 == to1 && c2 == to2) return;
			float t = tone.get();
			from1 = ColorUtil.lerp(from1, to1, t);
			from2 = ColorUtil.lerp(from2, to2, t);
			to1 = c1;
			to2 = c2;
			tone.snap(0f);
			tone.target(1f);
		}

		int c1() {
			return ColorUtil.lerp(from1, to1, tone.get());
		}

		int c2() {
			return ColorUtil.lerp(from2, to2, tone.get());
		}
	}
}
