package dev.v1zir.visuals.module.impl.visuals;

import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.module.setting.NumberSetting;
import dev.v1zir.visuals.render.world.Draw3D;
import dev.v1zir.visuals.render.world.WorldEffect;
import dev.v1zir.visuals.render.world.WorldEffectRenderer;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.fluid.FluidState;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;

/**
 * While it rains, surfaces under the open sky soak through: they darken, gain a glossy sheen and
 * weak reflections, and raindrops ring on water (render/Reflections). The wetness builds up over the
 * rain and dries slowly after; nothing is drawn while everything is dry.
 * <p>
 * Jumping or landing on wet ground in the open (or in shallow water) splashes: a ripple ring with
 * a bright rim spreads on the ground and droplets fly up and out, bigger after a fall.
 */
public final class RainEffectsModule extends Module implements WorldEffect {
	private static final float TICK = 0.05f;
	private static final float SOAK_SECONDS = 20f;
	private static final float DRY_SECONDS = 60f;
	private static final float RING_SECONDS = 0.6f;
	private static final float SPLASH_SECONDS = 0.75f;
	private static final float GRAVITY = 14f;
	private static final int SEGMENTS = 40;
	private static final int WATER_COLOR = 0xFFA8CCE0;
	private static final int DROP_COLOR = 0xFFE4F2FA;

	public final NumberSetting wetness = add(new NumberSetting("Wetness", 1.0, 0.1, 1.5, 0.05));
	public final BoolSetting splashes = add(new BoolSetting("Splashes", true));
	public final NumberSetting splashSize = add(new NumberSetting("Splash size", 1.0, 0.5, 2.0, 0.05).visibleIf(splashes::on));
	public final BoolSetting splashSound = add(new BoolSetting("Splash sound", true).visibleIf(splashes::on));

	private final BlockPos.Mutable pos = new BlockPos.Mutable();
	/** Reused slots, oldest overwritten first: splashing allocates nothing. */
	private final Splash[] splashList = {new Splash(), new Splash(), new Splash(), new Splash(), new Splash(), new Splash()};
	private final Draw3D.Wave ringColor = new Draw3D.Wave();
	private final Draw3D.Wave rimColor = new Draw3D.Wave();
	private int nextSplash;
	private boolean wasOnGround = true;
	private double airTop;
	private float wet;
	private float lastWet;
	private float rain;
	private float lastRain;

	public RainEffectsModule() {
		super("Rain Effects", "Wet surfaces and splashes underfoot while it rains", Category.SHADERS);
		setSubCategory("World");
	}

	@Override
	public void onTick() {
		lastWet = wet;
		lastRain = rain;
		rain = rainHere();
		wet = rain > wet ? Math.min(rain, wet + TICK / SOAK_SECONDS) : Math.max(rain, wet - TICK / DRY_SECONDS);
		ClientPlayerEntity player = mc.player;
		if (player == null) return;
		// Landings: the higher the fall since leaving the ground, the bigger the splash.
		boolean onGround = player.isOnGround();
		if (!onGround) airTop = wasOnGround ? player.getY() : Math.max(airTop, player.getY());
		else if (!wasOnGround) splash(0.6f + (float) Math.min(airTop - player.getY(), 6.0) * 0.15f);
		wasOnGround = onGround;
	}

	@Override
	protected void onDisable() {
		wet = lastWet = rain = lastRain = 0f;
		for (Splash s : splashList) s.live = false;
	}

	/** A real jump of the local player (LivingEntityMixin): a small take-off splash. */
	public void onJump() {
		splash(0.45f);
	}

	private void splash(float size) {
		ClientPlayerEntity player = mc.player;
		ClientWorld world = mc.world;
		if (!splashes.on() || player == null || world == null) return;
		double x = player.getX();
		double y = player.getY();
		double z = player.getZ();
		boolean water = player.isTouchingWater();
		// Wet ground in the open while it rains: nothing above the block the player stands on.
		if (!water && (rain < 0.2f || wet < 0.25f || world.getTopY(Heightmap.Type.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z)) > y + 0.51)) return;
		if (water) {
			// On the water surface rather than the bottom of a shallow pool.
			pos.set(x, y, z);
			FluidState fluid = world.getFluidState(pos);
			if (!fluid.isEmpty()) y = pos.getY() + fluid.getHeight(world, pos);
		}
		size *= splashSize.f();
		Splash s = splashList[nextSplash];
		nextSplash = (nextSplash + 1) % splashList.length;
		s.set(x, y + 0.02, z, size, System.nanoTime());
		if (splashSound.on()) {
			world.playSoundClient(x, y, z, SoundEvents.ENTITY_PLAYER_SPLASH, SoundCategory.PLAYERS, 0.05f + 0.05f * size, 1.35f + hash(s.seed, 0, 0) * 0.3f, false);
		}
	}

	@Override
	public void render3D(float tickDelta) {
		long now = System.nanoTime();
		WorldEffectRenderer.Mesh water = WorldEffectRenderer.translucent();
		WorldEffectRenderer.Mesh glow = WorldEffectRenderer.glow();
		for (Splash s : splashList) {
			if (!s.live) continue;
			float age = (now - s.born) / 1.0e9f;
			if (age > SPLASH_SECONDS) {
				s.live = false;
				continue;
			}
			ring(water, glow, s, age, 1f);
			ring(water, glow, s, age - 0.12f, 0.6f);
			drops(water, s, age);
		}
	}

	/** A ripple spreading on the ground: a water-coloured band with a bright, thin rim. */
	private void ring(WorldEffectRenderer.Mesh water, WorldEffectRenderer.Mesh glow, Splash s, float age, float scale) {
		if (age <= 0f || age >= RING_SECONDS) return;
		float p = age / RING_SECONDS;
		float fade = (1f - p) * (1f - p);
		double r = (0.2 + Anim.easeOutCubic(p) * 0.9) * s.size * scale;
		Draw3D.flatRing(water, s.x, s.y, s.z, r, 0.1 * s.size * scale, SEGMENTS, ringColor.set(WATER_COLOR, WATER_COLOR, 0f, 0.5f * fade, SEGMENTS));
		Draw3D.flatRing(glow, s.x, s.y + 0.005, s.z, r + 0.03 * s.size, 0.025, SEGMENTS, rimColor.set(DROP_COLOR, DROP_COLOR, 0f, 0.55f * fade, SEGMENTS));
	}

	/** Droplets thrown up and out, each a short streak along its flight, gone once back on the ground. */
	private static void drops(WorldEffectRenderer.Mesh mesh, Splash s, float age) {
		int count = 6 + Math.round(6f * s.size);
		int alpha = Math.round(217f * (1f - age / SPLASH_SECONDS));
		int head = ColorUtil.withAlpha(DROP_COLOR, alpha);
		int tail = ColorUtil.withAlpha(DROP_COLOR, alpha / 5);
		for (int i = 0; i < count; i++) {
			double angle = (i + hash(s.seed, i, 1) * 0.7) / count * Math.PI * 2.0;
			double out = (0.8 + 1.4 * hash(s.seed, i, 2)) * s.size;
			double up = (1.4 + 1.6 * hash(s.seed, i, 3)) * Math.sqrt(s.size);
			double height = up * age - 0.5 * GRAVITY * age * age;
			if (height < 0.0) continue;
			double vx = Math.cos(angle) * out;
			double vz = Math.sin(angle) * out;
			double vy = up - GRAVITY * age;
			double x = s.x + vx * age;
			double y = s.y + height;
			double z = s.z + vz * age;
			Draw3D.line(mesh, x, y, z, x - vx * 0.035, y - vy * 0.035, z - vz * 0.035, 0.016f * (float) Math.sqrt(s.size), head, tail);
		}
	}

	/** Stable pseudo-random 0..1 per splash, droplet and property. */
	private static float hash(int seed, int i, int k) {
		int h = seed * 0x9E3779B1 + i * 0x85EBCA77 + k * 0xC2B2AE3D;
		h ^= h >>> 15;
		h *= 0x2C1B3C6D;
		h ^= h >>> 12;
		return (h >>> 8) / 16777216f;
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

	private static final class Splash {
		double x;
		double y;
		double z;
		float size;
		long born;
		int seed;
		boolean live;

		void set(double x, double y, double z, float size, long born) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.size = size;
			this.born = born;
			this.seed = (int) (born ^ born >>> 32);
			this.live = true;
		}
	}
}
