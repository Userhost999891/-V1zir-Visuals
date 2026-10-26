package dev.v1zir.visuals.module.impl.visuals;

import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.module.setting.NumberSetting;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;

/**
 * While it rains, surfaces under the open sky soak through: they darken, gain a glossy sheen and
 * weak reflections, and puddles shaped by world-space noise fill up and mirror the scene, rippled
 * by raindrops (render/Reflections). Everything builds up over the rain and dries slowly after;
 * nothing is drawn while all of it is dry.
 */
public final class RainEffectsModule extends Module {
	private static final float TICK = 0.05f;
	private static final float SOAK_SECONDS = 20f;
	private static final float DRY_SECONDS = 60f;
	private static final float FILL_SECONDS = 45f;
	private static final float DRAIN_SECONDS = 120f;

	public final NumberSetting wetness = add(new NumberSetting("Wetness", 1.0, 0.1, 1.5, 0.05));
	public final BoolSetting puddles = add(new BoolSetting("Puddles", true));
	public final NumberSetting puddleAmount = add(new NumberSetting("Puddle amount", 0.6, 0.1, 1.0, 0.05).visibleIf(puddles::on));
	public final BoolSetting ripples = add(new BoolSetting("Ripples", true));

	private final BlockPos.Mutable pos = new BlockPos.Mutable();
	private float wet;
	private float lastWet;
	private float filled;
	private float lastFilled;
	private float rain;
	private float lastRain;

	public RainEffectsModule() {
		super("Rain Effects", "Wet surfaces and rippling puddles while it rains", Category.SHADERS);
		setSubCategory("World");
	}

	@Override
	public void onTick() {
		lastWet = wet;
		lastFilled = filled;
		lastRain = rain;
		rain = rainHere();
		wet = rain > wet ? Math.min(rain, wet + TICK / SOAK_SECONDS) : Math.max(rain, wet - TICK / DRY_SECONDS);
		filled = rain > 0.2f ? Math.min(1f, filled + rain * TICK / FILL_SECONDS) : Math.max(0f, filled - TICK / DRAIN_SECONDS);
	}

	@Override
	protected void onDisable() {
		wet = lastWet = filled = lastFilled = rain = lastRain = 0f;
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
		return wet > 0f || filled > 0f;
	}

	public float wetness(float tickDelta) {
		return (lastWet + (wet - lastWet) * tickDelta) * wetness.f();
	}

	/** How far the puddles have spread, 0..1, already scaled by the puddle amount. */
	public float puddles(float tickDelta) {
		return puddles.on() ? (lastFilled + (filled - lastFilled) * tickDelta) * puddleAmount.f() : 0f;
	}

	/** Strength of the raindrop rings on puddles and water. */
	public float ripples(float tickDelta) {
		return ripples.on() ? lastRain + (rain - lastRain) * tickDelta : 0f;
	}
}
