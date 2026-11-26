package dev.v1zir.visuals.module.impl.visuals;

import dev.v1zir.visuals.V1zir;
import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.module.setting.NumberSetting;
import dev.v1zir.visuals.render.winter.SnowRenderer;
import dev.v1zir.visuals.render.world.WorldEffect;
import net.minecraft.block.AbstractFurnaceBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

/**
 * Winter: snow and the feel of Christmas. Snow falls round you, thousands of fluffy flakes of every size tumbling and
 * drifting in the wind, close ones soft as a lens sees them, lit by the sun or the moon; it stops at roofs and trees as
 * the game's own does, and takes the place of the game's rain and snow while on. All of it is worked out on the
 * graphics card (SnowRenderer): one draw, nothing per flake on the CPU. Snow lies on everything under the open sky:
 * close by as real geometry a few sixteenths deep, rounded and hanging a little over open edges (SnowLayer), further
 * off as a cover over the tops of blocks (render/Reflections, snow_cover.fsh). The sky turns festive: crisp and cold
 * by day, at night full of stars with a green and red aurora and the Christmas star (sky preset Winter). Out under
 * the sky every step crunches in snow. On screen, frost creeps in from the edges (it thaws by a fire, a campfire, lava
 * or any warmth, a little indoors, and creeps back in the cold), the world takes a cold cast, a string of Christmas
 * lights twinkles along the top, and snow falls in the menus and lies on their panels.
 */
public final class WinterModule extends Module implements WorldEffect {
	public final BoolSetting snow = add(new BoolSetting("Snow", true));
	public final NumberSetting intensity = add(new NumberSetting("Intensity", 1.0, 0.2, 2.0, 0.05).suffix("x").visibleIf(snow::on));
	public final NumberSetting flakeSize = add(new NumberSetting("Flake size", 1.0, 0.5, 2.0, 0.05).suffix("x").visibleIf(snow::on));
	public final NumberSetting wind = add(new NumberSetting("Wind", 0.7, 0.0, 2.0, 0.05).visibleIf(snow::on));
	public final BoolSetting outdoorsOnly = add(new BoolSetting("Only outdoors", true).visibleIf(snow::on));
	public final BoolSetting cover = add(new BoolSetting("Snow cover", true));
	/** How deep the snow lies close by, where it is real geometry (blocks). */
	public final NumberSetting depth = add(new NumberSetting("Snow depth", 0.16, 0.05, 0.35, 0.01).visibleIf(cover::on));
	public final BoolSetting festive = add(new BoolSetting("Festive sky", true));
	public final BoolSetting frost = add(new BoolSetting("Frost", true));
	public final BoolSetting coldTint = add(new BoolSetting("Cold tint", true));
	public final BoolSetting lights = add(new BoolSetting("Christmas lights", true));
	public final BoolSetting menuSnow = add(new BoolSetting("Menu snow", true));

	/** Flakes at full intensity: drawn as a share of the snow buffer. */
	private static final int FLAKES = 16000;
	/** How far round the player warmth is looked for (blocks, sideways; a third of it up and down). */
	private static final int WARMTH_REACH = 7;

	/** How warm it is where the player stands: 0 out in the cold, 1 right by a fire (every few ticks). */
	private float warmth;
	private int warmthTimer;
	/** Steps played in snow so far (the game test reads it). */
	public static int snowSteps;

	public WinterModule() {
		super("Winter", "Realistic snowfall, frost, lights and a Christmas mood", Category.SHADERS);
		setSubCategory("World");
	}

	/** Whether snow is falling in the world (the game's own rain and snow then stay away). */
	public static boolean snowing() {
		WinterModule m = ModuleManager.get(WinterModule.class);
		return m != null && m.isEnabled() && m.snow.on();
	}

	/** Whether the sky is Winter's festive one (render/sky: it takes the place of the Shader Sky's preset), overworld only. */
	public static boolean festiveSky() {
		WinterModule m = ModuleManager.get(WinterModule.class);
		if (m == null || !m.isEnabled() || !m.festive.on()) return false;
		MinecraftClient mc = V1zir.mc();
		return mc.world != null && mc.world.getRegistryKey() == World.OVERWORLD;
	}

	/** How deep the snow lies where it is drawn as geometry (blocks), 0 while there is no snow cover. */
	public static float depth() {
		WinterModule m = ModuleManager.get(WinterModule.class);
		return m != null && m.isEnabled() && m.cover.on() ? m.depth.f() : 0f;
	}

	/** How warm it is where the player stands, 0 out in the cold to 1 right by a fire (the frost on screen thaws). */
	public float warmth() {
		return warmth;
	}

	/**
	 * A step of the local player (EntityMixin): out under the sky, where snow lies, it crunches in snow instead of the
	 * block's own sound; indoors and in water the step is the game's own. Returns true when the snow's step was played.
	 */
	public static boolean snowStep(Entity entity, BlockPos pos, BlockState state) {
		if (!covers()) return false;
		MinecraftClient mc = V1zir.mc();
		ClientWorld world = mc.world;
		if (world == null || world.getRegistryKey() != World.OVERWORLD || entity.isTouchingWater() || !state.getFluidState().isEmpty()) return false;
		// Anything above that keeps the snow off (a roof, leaves): no snow underfoot.
		if (world.getTopY(Heightmap.Type.MOTION_BLOCKING, pos.getX(), pos.getZ()) > pos.getY() + 1) return false;
		float pitch = 0.88f + world.random.nextFloat() * 0.24f;
		world.playSoundClient(entity.getX(), entity.getY(), entity.getZ(), SoundEvents.BLOCK_SNOW_STEP, SoundCategory.PLAYERS, 0.24f, pitch, false);
		snowSteps++;
		return true;
	}

	@Override
	public void onTick() {
		if (--warmthTimer > 0) return;
		warmthTimer = 5;
		warmth = warmthAround(V1zir.mc());
	}

	/**
	 * Warmth round the player: fire, a lit campfire or lava at full strength, magma and a lit furnace a little less,
	 * torches, lanterns and jack o'lanterns a little, each fading out with distance; burning is warm, and so, a little,
	 * is being indoors (anything overhead that keeps the snow off).
	 */
	private static float warmthAround(MinecraftClient mc) {
		ClientWorld world = mc.world;
		ClientPlayerEntity player = mc.player;
		if (world == null || player == null) return 0f;
		if (player.isOnFire()) return 1f;
		BlockPos center = player.getBlockPos();
		BlockPos.Mutable at = new BlockPos.Mutable();
		int up = WARMTH_REACH / 2;
		float best = 0f;
		for (int dy = -up; dy <= up; dy++) {
			for (int dz = -WARMTH_REACH; dz <= WARMTH_REACH; dz++) {
				for (int dx = -WARMTH_REACH; dx <= WARMTH_REACH; dx++) {
					float heat = heat(world.getBlockState(at.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz)));
					if (heat <= 0f) continue;
					float d = (float) Math.sqrt(dx * dx + dy * dy * 1.5f + dz * dz);
					best = Math.max(best, heat * (1f - Math.min(1f, d / (WARMTH_REACH * heat + 1f))));
				}
			}
		}
		if (world.getTopY(Heightmap.Type.MOTION_BLOCKING, center.getX(), center.getZ()) > player.getEyeY() + 0.5) best = Math.max(best, 0.35f);
		return Math.min(1f, best);
	}

	private static float heat(BlockState state) {
		if (state.isAir()) return 0f;
		if (state.isIn(BlockTags.FIRE) || state.getFluidState().isIn(FluidTags.LAVA)) return 1f;
		if (state.isIn(BlockTags.CAMPFIRES)) return state.getOrEmpty(CampfireBlock.LIT).orElse(false) ? 1f : 0f;
		if (state.getBlock() instanceof AbstractFurnaceBlock) return state.getOrEmpty(AbstractFurnaceBlock.LIT).orElse(false) ? 0.75f : 0f;
		if (state.isOf(Blocks.MAGMA_BLOCK)) return 0.6f;
		if (state.isOf(Blocks.TORCH) || state.isOf(Blocks.WALL_TORCH) || state.isOf(Blocks.SOUL_TORCH) || state.isOf(Blocks.SOUL_WALL_TORCH)
			|| state.isOf(Blocks.LANTERN) || state.isOf(Blocks.SOUL_LANTERN) || state.isOf(Blocks.JACK_O_LANTERN)) {
			return 0.35f;
		}
		return 0f;
	}

	/** Whether snow lies on the world (render/Reflections draws it, over the finished world). */
	public static boolean covers() {
		WinterModule m = ModuleManager.get(WinterModule.class);
		return m != null && m.isEnabled() && m.cover.on();
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
