package dev.v1zir.visuals.module.impl.effects;

import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.module.setting.ColorSetting;
import dev.v1zir.visuals.module.setting.NumberSetting;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.gui.DrawContext;
import org.joml.Matrix3x2fStack;

/** Crosshair hit indicator: four diagonal ticks that pop outwards on a hit and turn red on a kill. */
public final class HitMarkerModule extends Module {
	private static final float QUARTER_TURN = (float) (Math.PI / 2);
	private static final float EIGHTH_TURN = (float) (Math.PI / 4);

	public final NumberSetting size = add(new NumberSetting("Size", 4.5, 2.0, 10.0, 0.5));
	public final NumberSetting gap = add(new NumberSetting("Gap", 3.5, 1.0, 10.0, 0.5));
	public final NumberSetting thickness = add(new NumberSetting("Thickness", 1.5, 0.5, 3.0, 0.25));
	public final NumberSetting duration = add(new NumberSetting("Duration", 0.4, 0.1, 1.5, 0.05).suffix("s"));
	public final BoolSetting themeColor = add(new BoolSetting("Theme color", false));
	public final ColorSetting color = add(new ColorSetting("Color", 0xFFFFFFFF).visibleIf(() -> !themeColor.on()));
	public final ColorSetting killColor = add(new ColorSetting("Kill color", 0xFFFF3B4F));
	public final BoolSetting glow = add(new BoolSetting("Glow", true));

	private final Anim alpha = new Anim(0f, 9f);
	/** Pushes the ticks outwards on a hit; the spring pulls them back in. */
	private final Anim kick = new Anim(0f, 14f);
	private long shownAt;
	private float hold;
	private boolean kill;

	public HitMarkerModule() {
		super("Hit Marker", "Crosshair marker when you hit or kill", Category.EFFECTS);
		setSubCategory("Combat");
	}

	public void onHit() {
		// A hit right after a kill (the next swing) would hide the red marker too early.
		if (kill && visible()) return;
		// Weak hits from spam clicking get a weaker marker.
		float strength = mc.player != null ? 0.45f + 0.55f * mc.player.getAttackCooldownProgress(0.5f) : 1f;
		show(false, strength, strength);
	}

	public void onKill() {
		show(true, 1f, 1.6f);
	}

	private void show(boolean kill, float opacity, float push) {
		this.kill = kill;
		hold = duration.f() * (kill ? 1.8f : 1f);
		shownAt = System.nanoTime();
		alpha.snap(opacity);
		kick.snap(push);
		kick.target(0f);
	}

	private boolean visible() {
		return (System.nanoTime() - shownAt) / 1.0e9f < hold;
	}

	/** Drawn on top of the crosshair (HudRenderer). */
	public void render(DrawContext ctx) {
		if (!visible()) alpha.target(0f);
		float a = alpha.get();
		if (a <= 0.01f || mc.player == null || !mc.options.getPerspective().isFirstPerson()) return;
		float k = kick.get();
		float scale = kill ? 1.35f : 1f;
		float length = size.f() * scale * (1f + 0.35f * k);
		float from = gap.f() + k * 3.5f * scale;
		float t = thickness.f() * (kill ? 1.4f : 1f);
		int c = ColorUtil.fade(kill ? killColor.argb() : themeColor.on() ? Theme.accent() : color.argb(), a);
		int outline = ColorUtil.withAlpha(0x000000, Math.round(ColorUtil.alpha(c) * 0.45f));
		int halo = ColorUtil.fade(c, kill ? 0.7f : 0.45f);

		Matrix3x2fStack matrices = ctx.getMatrices();
		matrices.pushMatrix();
		matrices.translate(ctx.getScaledWindowWidth() / 2f, ctx.getScaledWindowHeight() / 2f);
		// Four arms of a plus sign, turned by 45 degrees.
		matrices.rotate(EIGHTH_TURN);
		for (int i = 0; i < 4; i++) {
			if (glow.on()) Render2D.shadow(ctx, from, -t / 2f, length, t, t / 2f, kill ? 6f : 4f, halo);
			Render2D.round(ctx, from - 0.6f, -t / 2f - 0.6f, length + 1.2f, t + 1.2f, t / 2f + 0.6f, outline);
			Render2D.round(ctx, from, -t / 2f, length, t, t / 2f, c);
			matrices.rotate(QUARTER_TURN);
		}
		matrices.popMatrix();
	}
}
