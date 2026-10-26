package dev.v1zir.visuals.module.impl.visuals;

import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.setting.NumberSetting;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;

/**
 * While it rains, surfaces under the open sky soak through: they darken, gain a glossy sheen and
 * weak reflections, and raindrops ring on water (render/Reflections). The wetness builds up over the
 * rain and dries slowly after; nothing is drawn while everything is dry.
 */
public final class RainEffectsModule extends Module {
	private static final float TICK = 0.05f;
	private static final float SOAK_SECONDS = 20f;
	private static final float DRY_SECONDS = 60f;

	public final NumberSetting wetness = add(new NumberSetting("Wetness", 1.0, 0.1, 1.5, 0.05));

	private final BlockPos.Mutable pos = new BlockPos.Mutable();
	private float wet;
	private float lastWet;
	private float rain;
	private float lastRain;

	public RainEffectsModule() {
		super("Rain Effects", "Wet surfaces and rippling puddles while it rains", Category.SHADERS);
		setSubCategory("World");
	}

	@Override
	public void onTick() {
		lastWet = wet;
		lastRain = rain;
		rain = rainHere();
		wet = rain > wet ? Math.min(rain, wet + TICK / SOAK_SECONDS) : Math.max(rain, wet - TICK / DRY_SECONDS);
	}

	@Override
	protected void onDisable() {
		wet = lastWet = rain = lastRain = 0f;
	}

	/** Rain strength where the player is: none in deserts and where it snows. */
	private float rainHere() {
		ClientWorld world = mc.world;
		if (world == null || mc.player == null) return 0f;
		float gradient = world.getRainGradient(1f);
		if (gradient <= 0f) return 0f;
		pos.set(mc.player.getX(), mc.player.getY(), mc.player.getZ());
		return world.getBiome(pos).value().getPrecipitation(pos, world.getSeaLevel()) == Biome.Precipitation.RAIN ? gradient : 0f;
	}

	/** Whether anything is wet: the reflection pass runs only then. */
	public boolean active() {
		return wet > 0f;
	}

	public float wetness(float tickDelta) {
		return (lastWet + (wet - lastWet) * tickDelta) * wetness.f();
	}

	/** Rain strength here, for the raindrop rings on water. */
	public float rain(float tickDelta) {
		return lastRain + (rain - lastRain) * tickDelta;
	}
}
