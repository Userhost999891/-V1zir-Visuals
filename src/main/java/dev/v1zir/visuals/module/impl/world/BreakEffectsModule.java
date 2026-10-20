package dev.v1zir.visuals.module.impl.world;

import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.module.setting.ColorSetting;
import dev.v1zir.visuals.module.setting.ModeSetting;
import dev.v1zir.visuals.render.world.Draw3D;
import dev.v1zir.visuals.render.world.WorldEffect;
import dev.v1zir.visuals.render.world.WorldEffectRenderer;
import dev.v1zir.visuals.render.world.WorldText;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.block.BlockState;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.BlockBreakingInfo;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;

/**
 * Block breaking indicator: a glowing rounded box that grows, shrinks or fills up with the mining
 * progress while its colour shifts from start to end, the percentage floating in front of the
 * block, and a soft burst when the block breaks. Blocks other players mine show their stage too.
 */
public final class BreakEffectsModule extends Module implements WorldEffect {
	private static final int OTHERS = 8;
	private static final double INFLATE = 0.004;
	private static final String[] PERCENT = new String[101];

	static {
		for (int i = 0; i <= 100; i++) PERCENT[i] = i + "%";
	}

	public final ModeSetting mode = add(new ModeSetting("Mode", "Grow", "Grow", "Shrink", "Fill"));
	public final BoolSetting percentage = add(new BoolSetting("Percentage", true));
	public final BoolSetting others = add(new BoolSetting("Other players", true));
	public final BoolSetting hideCracks = add(new BoolSetting("Hide cracks", false));
	public final BoolSetting themeColor = add(new BoolSetting("Theme color", true));
	public final ColorSetting startColor = add(new ColorSetting("Start color", 0xFFFF5A6E).visibleIf(() -> !themeColor.on()));
	public final ColorSetting endColor = add(new ColorSetting("End color", 0xFF5CFF9D).visibleIf(() -> !themeColor.on()));

	private final WorldText.Style text = new WorldText.Style().size(0.2f).weight(0.06f).shadow();
	private final double[] box = new double[6];

	// The local player's block: progress of the last two ticks (interpolated per frame) and its box.
	private final double[] ownBox = new double[6];
	private final double[] burstBox = new double[6];
	private final Anim ownShown = new Anim(0f, 10f);
	private final Anim burst = new Anim(1f, 6f);
	private BlockPos ownPos;
	private BlockState ownState;
	private boolean breaking;
	private float progress;
	private float lastProgress;

	// Other miners, one slot each, fed from vanilla's breaking stages every tick.
	private final int[] otherActor = new int[OTHERS];
	private final BlockPos[] otherPos = new BlockPos[OTHERS];
	private final double[] otherBox = new double[OTHERS * 6];
	private final Anim[] otherProgress = new Anim[OTHERS];
	private final Anim[] otherShown = new Anim[OTHERS];
	private final boolean[] otherSeen = new boolean[OTHERS];

	public BreakEffectsModule() {
		super("Break Effects", "Glowing progress box on the block being mined", Category.WORLD);
		setSubCategory("Render");
		for (int i = 0; i < OTHERS; i++) {
			otherProgress[i] = new Anim(0f, 8f);
			otherShown[i] = new Anim(0f, 8f);
		}
	}

	@Override
	public String suffix() {
		return mode.get();
	}

	/** Whether vanilla's crack overlay is hidden (WorldRendererMixin). */
	public static boolean hidesCracks() {
		BreakEffectsModule module = ModuleManager.get(BreakEffectsModule.class);
		return module != null && module.isEnabled() && module.hideCracks.on();
	}

	@Override
	protected void onDisable() {
		breaking = false;
		ownPos = null;
		ownShown.snap(0f);
		burst.snap(1f);
		for (int i = 0; i < OTHERS; i++) {
			otherPos[i] = null;
			otherShown[i].snap(0f);
		}
	}

	@Override
	public void onTick() {
		ClientPlayerInteractionManager im = mc.interactionManager;
		if (im == null || mc.world == null || mc.player == null) return;
		lastProgress = progress;
		BlockPos pos = im.currentBreakingPos;
		boolean now = im.isBreakingBlock() && pos != null;
		if (now) {
			BlockState state = mc.world.getBlockState(pos);
			if (!pos.equals(ownPos) || state != ownState) {
				now = bounds(pos, state, ownBox, 0);
				ownPos = now ? pos : null;
				ownState = state;
				lastProgress = 0f;
			}
			progress = im.currentBreakingProgress;
		} else if (breaking) {
			progress = lastProgress;
			// Broken rather than given up: a copy of the box bursts outwards while the indicator is gone.
			if (mc.world.getBlockState(ownPos).isAir()) {
				System.arraycopy(ownBox, 0, burstBox, 0, 6);
				burst.snap(0f);
				ownShown.snap(0f);
			}
		}
		breaking = now;
		if (others.on()) trackOthers();
	}

	/** Copies vanilla's breaking stages of other players into the slots. */
	private void trackOthers() {
		int self = mc.player.getId();
		for (int i = 0; i < OTHERS; i++) otherSeen[i] = false;
		for (BlockBreakingInfo info : mc.worldRenderer.blockBreakingInfos.values()) {
			int actor = info.getActorId();
			if (actor == self) continue;
			int slot = slot(actor);
			if (slot < 0) continue;
			BlockPos pos = info.getPos();
			if (!pos.equals(otherPos[slot]) || otherActor[slot] != actor) {
				if (!bounds(pos, mc.world.getBlockState(pos), otherBox, slot * 6)) continue;
				otherPos[slot] = pos;
				otherActor[slot] = actor;
				otherProgress[slot].snap(0f);
			}
			otherSeen[slot] = true;
			otherProgress[slot].target((info.getStage() + 1) / 10f);
		}
	}

	/** The slot already showing this miner, or a free one; -1 when all are busy. */
	private int slot(int actor) {
		int free = -1;
		for (int i = 0; i < OTHERS; i++) {
			if (otherPos[i] != null && otherActor[i] == actor) return i;
			if (free < 0 && !otherSeen[i] && otherShown[i].get() < 0.01f) free = i;
		}
		return free;
	}

	private boolean bounds(BlockPos pos, BlockState state, double[] out, int o) {
		VoxelShape shape = state.getOutlineShape(mc.world, pos);
		if (shape.isEmpty()) return false;
		out[o] = pos.getX() + shape.getMin(Direction.Axis.X);
		out[o + 1] = pos.getY() + shape.getMin(Direction.Axis.Y);
		out[o + 2] = pos.getZ() + shape.getMin(Direction.Axis.Z);
		out[o + 3] = pos.getX() + shape.getMax(Direction.Axis.X);
		out[o + 4] = pos.getY() + shape.getMax(Direction.Axis.Y);
		out[o + 5] = pos.getZ() + shape.getMax(Direction.Axis.Z);
		return true;
	}

	@Override
	public void render3D(float tickDelta) {
		float pop = burst.target(1f).get();
		if (pop < 1f) draw(burstBox, 0, 1f, 1f - pop, 1f + 0.22f * pop);
		float shown = ownShown.target(breaking ? 1f : 0f).get();
		if (shown > 0.004f && ownPos != null) {
			float p = Math.min(1f, lastProgress + (progress - lastProgress) * tickDelta);
			draw(ownBox, 0, p, shown, 1f);
			if (percentage.on()) label(p, shown);
		}
		if (!others.on()) return;
		for (int i = 0; i < OTHERS; i++) {
			float a = otherShown[i].target(otherSeen[i] ? 1f : 0f).get();
			if (a <= 0.004f || otherPos[i] == null) continue;
			draw(otherBox, i * 6, otherProgress[i].get(), a * 0.85f, 1f);
		}
	}

	/** Draws the progress box for the block bounds at b[o..o+5]. */
	private void draw(double[] b, int o, float p, float alpha, float scale) {
		int start = themeColor.on() ? Theme.accent2() : startColor.argb();
		int end = themeColor.on() ? Theme.accent() : endColor.argb();
		int color = ColorUtil.lerp(start, end, p);
		double cx = (b[o] + b[o + 3]) * 0.5;
		double cy = (b[o + 1] + b[o + 4]) * 0.5;
		double cz = (b[o + 2] + b[o + 5]) * 0.5;
		double hx = (b[o + 3] - b[o]) * 0.5 * scale + INFLATE;
		double hy = (b[o + 4] - b[o + 1]) * 0.5 * scale + INFLATE;
		double hz = (b[o + 5] - b[o + 2]) * 0.5 * scale + INFLATE;
		WorldEffectRenderer.Mesh glow = WorldEffectRenderer.glow();
		// Faint full-size frame, so the progress always reads against the whole block.
		int ghost = ColorUtil.withAlpha(color, Math.round(70 * alpha));
		Draw3D.roundedBox(glow, cx - hx, cy - hy, cz - hz, cx + hx, cy + hy, cz + hz, ghost, ghost);

		box[0] = cx - hx;
		box[1] = cy - hy;
		box[2] = cz - hz;
		box[3] = cx + hx;
		box[4] = cy + hy;
		box[5] = cz + hz;
		switch (mode.get()) {
			case "Shrink" -> scaleBox(1f - 0.8f * p, cx, cy, cz, hx, hy, hz);
			case "Fill" -> box[4] = box[1] + 2 * hy * Math.max(p, 0.04f);
			default -> scaleBox(0.2f + 0.8f * p, cx, cy, cz, hx, hy, hz);
		}
		Draw3D.solidBox(WorldEffectRenderer.translucent(), box[0], box[1], box[2], box[3], box[4], box[5],
			ColorUtil.withAlpha(color, Math.round(80 * alpha)), ColorUtil.withAlpha(color, Math.round(45 * alpha)));
		int edge = ColorUtil.withAlpha(color, Math.round(235 * alpha));
		Draw3D.roundedBox(glow, box[0], box[1], box[2], box[3], box[4], box[5], edge, edge);
	}

	private void scaleBox(float s, double cx, double cy, double cz, double hx, double hy, double hz) {
		box[0] = cx - hx * s;
		box[1] = cy - hy * s;
		box[2] = cz - hz * s;
		box[3] = cx + hx * s;
		box[4] = cy + hy * s;
		box[5] = cz + hz * s;
	}

	/** Percentage in front of the face you are mining (on top when the crosshair has left it). */
	private void label(float p, float alpha) {
		Direction side = Direction.UP;
		if (mc.crosshairTarget instanceof BlockHitResult hit && ownPos.equals(hit.getBlockPos())) side = hit.getSide();
		double cx = (ownBox[0] + ownBox[3]) * 0.5;
		double cy = (ownBox[1] + ownBox[4]) * 0.5;
		double cz = (ownBox[2] + ownBox[5]) * 0.5;
		double x = cx + side.getOffsetX() * ((ownBox[3] - ownBox[0]) * 0.5 + 0.28);
		double y = cy + side.getOffsetY() * ((ownBox[4] - ownBox[1]) * 0.5 + 0.28);
		double z = cz + side.getOffsetZ() * ((ownBox[5] - ownBox[2]) * 0.5 + 0.28);
		int start = themeColor.on() ? Theme.accent2() : startColor.argb();
		int end = themeColor.on() ? Theme.accent() : endColor.argb();
		text.color(0xFFFFFFFF).glow(ColorUtil.lerp(start, end, p), 0.55f).alpha(alpha);
		WorldText.draw(PERCENT[Math.round(p * 100f)], x, y, z, text);
	}
}
