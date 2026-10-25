package dev.v1zir.visuals.module.impl.visuals;

import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.module.setting.ColorSetting;
import dev.v1zir.visuals.module.setting.NumberSetting;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Soft red vignette while health is low: stronger and beating faster the less health is left, with
 * a short flash whenever you are hurt. It is four soft edge glows drawn under the HUD (each only
 * covers its own screen edge), and nothing at all is drawn while there is nothing to show.
 */
public final class DamageTintModule extends Module {
	private static final float TAU = (float) (Math.PI * 2);

	public final ColorSetting color = add(new ColorSetting("Color", 0xFFFF2B3D, false));
	public final NumberSetting threshold = add(new NumberSetting("Low health", 50, 10, 90, 5).suffix("%"));
	public final NumberSetting intensity = add(new NumberSetting("Intensity", 0.7, 0.1, 1.0, 0.05));
	public final BoolSetting pulse = add(new BoolSetting("Pulse", true));
	public final BoolSetting hurtFlash = add(new BoolSetting("Hurt flash", true));

	private final Anim low = new Anim(0f, 4f);
	private final Anim flash = new Anim(0f, 4.5f);
	private int lastHurtTime;
	/** Heartbeat phase in radians; it advances faster the closer you are to dying. */
	private float beat;
	private long lastFrame = System.nanoTime();

	public DamageTintModule() {
		super("Damage Tint", "Red vignette at low health and when hurt", Category.COMBAT);
		setSubCategory("Hits");
	}

	@Override
	protected void onDisable() {
		low.snap(0f);
		flash.snap(0f);
	}

	@Override
	public void onTick() {
		ClientPlayerEntity player = mc.player;
		if (player == null) return;
		// Vanilla sets hurtTime to its maximum on every hit and counts it down each tick.
		if (hurtFlash.on() && player.hurtTime > lastHurtTime) {
			flash.snap(1f);
			flash.target(0f);
		}
		lastHurtTime = player.hurtTime;
	}

	/** Drawn after vanilla's screen overlays, below the rest of the HUD (HudRenderer). */
	public void render(DrawContext ctx) {
		long now = System.nanoTime();
		float dt = Math.min((now - lastFrame) / 1.0e9f, 0.1f);
		lastFrame = now;
		ClientPlayerEntity player = mc.player;
		if (player == null || player.isSpectator()) return;

		float t = threshold.f() / 100f;
		float health = player.getHealth() / Math.max(1f, player.getMaxHealth());
		float danger = Anim.clamp01((t - health) / t);
		float lowAmount = low.target(danger).get();
		float flashAmount = flash.get();
		// About 50 beats a minute at the threshold, up to about 130 near death.
		beat = (beat + dt * TAU * (0.8f + 1.4f * lowAmount)) % TAU;
		float heartbeat = pulse.on() ? 0.72f + 0.28f * (float) Math.sin(beat) : 1f;
		float strength = intensity.f() * Anim.clamp01(lowAmount * heartbeat + flashAmount * 0.75f);
		if (strength < 0.004f) return;

		int c = ColorUtil.withAlpha(color.argb(), Math.round(235 * strength));
		float w = ctx.getScaledWindowWidth();
		float h = ctx.getScaledWindowHeight();
		float reach = Math.min(w, h) * (0.14f + 0.12f * lowAmount * heartbeat + 0.06f * flashAmount);
		// Each glow surrounds a rectangle just outside one edge, so only its falloff reaches into the screen.
		Render2D.shadow(ctx, -reach, -reach, reach, h + reach * 2f, 0f, reach, c);
		Render2D.shadow(ctx, w, -reach, reach, h + reach * 2f, 0f, reach, c);
		Render2D.shadow(ctx, 0f, -reach, w, reach, 0f, reach, c);
		Render2D.shadow(ctx, 0f, h, w, reach, 0f, reach, c);
	}
}
