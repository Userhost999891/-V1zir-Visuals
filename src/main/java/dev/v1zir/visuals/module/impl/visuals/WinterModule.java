package dev.v1zir.visuals.module.impl.visuals;

import dev.v1zir.visuals.V1zir;
import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.module.setting.NumberSetting;
import dev.v1zir.visuals.render.winter.SnowRenderer;
import dev.v1zir.visuals.render.world.WorldEffect;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.World;

/**
 * Winter: snow and the feel of Christmas. Snow falls round you, thousands of flakes of every size tumbling and
 * drifting in the wind, close ones soft as a lens sees them, lit by the day; it stops at roofs and trees as the
 * game's own does, and takes the place of the game's rain and snow while on. All of it is worked out on the graphics
 * card (SnowRenderer): one draw, nothing per flake on the CPU, so it costs no frames to speak of. On screen, frost
 * creeps in from the edges, the world takes a cold cast, a string of Christmas lights twinkles along the top, and
 * snow falls in the menus and lies on their panels.
 */
public final class WinterModule extends Module implements WorldEffect {
	public final BoolSetting snow = add(new BoolSetting("Snow", true));
	public final NumberSetting intensity = add(new NumberSetting("Intensity", 1.0, 0.2, 2.0, 0.05).suffix("x").visibleIf(snow::on));
	public final NumberSetting flakeSize = add(new NumberSetting("Flake size", 1.0, 0.5, 2.0, 0.05).suffix("x").visibleIf(snow::on));
	public final NumberSetting wind = add(new NumberSetting("Wind", 0.7, 0.0, 2.0, 0.05).visibleIf(snow::on));
	public final BoolSetting outdoorsOnly = add(new BoolSetting("Only outdoors", true).visibleIf(snow::on));
	public final BoolSetting frost = add(new BoolSetting("Frost", true));
	public final BoolSetting coldTint = add(new BoolSetting("Cold tint", true));
	public final BoolSetting lights = add(new BoolSetting("Christmas lights", true));
	public final BoolSetting menuSnow = add(new BoolSetting("Menu snow", true));

	/** Flakes at full intensity: drawn as a share of the snow buffer. */
	private static final int FLAKES = 10000;

	public WinterModule() {
		super("Winter", "Realistic snowfall, frost, lights and a Christmas mood", Category.SHADERS);
		setSubCategory("World");
	}

	/** Whether snow is falling in the world (the game's own rain and snow then stay away). */
	public static boolean snowing() {
		WinterModule m = ModuleManager.get(WinterModule.class);
		return m != null && m.isEnabled() && m.snow.on();
	}

	@Override
	public void render3D(float tickDelta) {
		if (!snow.on()) return;
		MinecraftClient mc = V1zir.mc();
		if (mc.world == null || mc.player == null || mc.world.getRegistryKey() != World.OVERWORLD || mc.player.isSubmergedInWater()) return;
		float t = V1zir.time();
		// The wind turns slowly and comes in gusts.
		double angle = t * 0.02 + Math.sin(t * 0.07) * 0.6;
		float strength = wind.f() * (0.6f + 0.4f * (float) Math.sin(t * 0.31f)) * 1.3f;
		int count = Math.round(FLAKES * intensity.f());
		SnowRenderer.render(tickDelta, count, 0.034f * flakeSize.f(), 1.1f, (float) Math.cos(angle) * strength, (float) Math.sin(angle) * strength,
			outdoorsOnly.on());
	}
}
