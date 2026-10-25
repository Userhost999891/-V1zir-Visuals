package dev.v1zir.visuals.module.impl.world;

import dev.v1zir.visuals.V1zir;
import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.module.setting.ColorSetting;
import dev.v1zir.visuals.module.setting.NumberSetting;
import dev.v1zir.visuals.render.world.Draw3D;
import dev.v1zir.visuals.render.world.WorldEffect;
import dev.v1zir.visuals.render.world.WorldEffectRenderer;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.block.BlockState;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;

/**
 * Replaces the vanilla block outline with a rounded glowing border and a soft fill that glide from
 * block to block and breathe gently. Vanilla still decides when an outline is shown (not with
 * the HUD hidden, not in spectator...), so the highlight follows the same rules.
 */
public final class BlockHighlightModule extends Module implements WorldEffect {
	/** Lifts the faces off the block so they never fight its surface for depth. */
	private static final double INFLATE = 0.003;

	public final BoolSetting themeColor = add(new BoolSetting("Theme color", true));
	public final ColorSetting color = add(new ColorSetting("Color", 0xFF8FD8FF).visibleIf(() -> !themeColor.on()));
	public final BoolSetting fill = add(new BoolSetting("Fill", true));
	public final NumberSetting fillOpacity = add(new NumberSetting("Fill opacity", 0.16, 0.02, 0.6, 0.01).visibleIf(fill::on));
	public final BoolSetting pulse = add(new BoolSetting("Pulse", true));
	public final BoolSetting glide = add(new BoolSetting("Glide", true));

	/** Box corners (min x, y, z, then max x, y, z) relative to the anchor block, so the float springs stay precise. */
	private final Anim[] box = new Anim[6];
	private final float[] goal = new float[6];
	private final int[] anchor = new int[3];
	private final Anim visible = new Anim(0f, 9f);
	private boolean requested;
	private BlockPos shapePos;
	private BlockState shapeState;
	private boolean hasShape;

	public BlockHighlightModule() {
		super("Block Highlight", "Rounded glowing outline on the block you look at", Category.VISUALS);
		setSubCategory("World");
		for (int i = 0; i < box.length; i++) box[i] = new Anim(0f, 16f);
	}

	/**
	 * Called where vanilla draws the outline (WorldRendererMixin): remembers that it wanted one
	 * this frame and returns whether to cancel it.
	 */
	public static boolean replacesOutline() {
		BlockHighlightModule module = ModuleManager.get(BlockHighlightModule.class);
		if (module == null || !module.isEnabled()) return false;
		module.requested = true;
		return true;
	}

	@Override
	protected void onDisable() {
		visible.snap(0f);
		shapePos = null;
		shapeState = null;
	}

	@Override
	public void render3D(float tickDelta) {
		boolean target = requested && updateGoal();
		requested = false;
		if (target) {
			// Glide between targets, but appear in place rather than flying in from the last one.
			boolean snap = !glide.on() || visible.get() < 0.05f;
			for (int i = 0; i < 6; i++) {
				if (snap) box[i].snap(goal[i]);
				else box[i].target(goal[i]);
			}
		}
		float shown = visible.target(target ? 1f : 0f).get();
		if (shown <= 0.004f) return;

		float k = pulse.on() ? 0.84f + 0.16f * (float) Math.sin(V1zir.time() * 2.6f) : 1f;
		int top = themeColor.on() ? Theme.accent() : color.argb();
		int bottom = themeColor.on() ? Theme.accent2() : ColorUtil.brighten(top, 0.3f);
		double x0 = anchor[0] + box[0].get() - INFLATE;
		double y0 = anchor[1] + box[1].get() - INFLATE;
		double z0 = anchor[2] + box[2].get() - INFLATE;
		double x1 = anchor[0] + box[3].get() + INFLATE;
		double y1 = anchor[1] + box[4].get() + INFLATE;
		double z1 = anchor[2] + box[5].get() + INFLATE;
		float a = 255f * shown;
		if (fill.on()) {
			float f = a * fillOpacity.f() * (0.85f + 0.15f * k);
			Draw3D.solidBox(WorldEffectRenderer.translucent(), x0, y0, z0, x1, y1, z1,
				ColorUtil.withAlpha(bottom, Math.round(f)), ColorUtil.withAlpha(top, Math.round(f * 0.7f)));
		}
		Draw3D.roundedBox(WorldEffectRenderer.glow(), x0, y0, z0, x1, y1, z1,
			ColorUtil.withAlpha(bottom, Math.round(a * k)), ColorUtil.withAlpha(top, Math.round(a * k)));
	}

	/** Reads the targeted block into goal; false when the crosshair is not on a block with an outline. */
	private boolean updateGoal() {
		if (mc.world == null || !(mc.crosshairTarget instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return false;
		BlockPos pos = hit.getBlockPos();
		BlockState state = mc.world.getBlockState(pos);
		// The shape only changes with the block, so it is looked up once per target.
		if (pos.equals(shapePos) && state == shapeState) return hasShape;
		shapePos = pos;
		shapeState = state;
		VoxelShape shape = state.getOutlineShape(mc.world, pos);
		hasShape = !shape.isEmpty();
		if (!hasShape) return false;
		int x = pos.getX();
		int y = pos.getY();
		int z = pos.getZ();
		if (Math.abs(x - anchor[0]) + Math.abs(y - anchor[1]) + Math.abs(z - anchor[2]) > 256) {
			// Far from the anchor (or the first target): move it here and let the box appear in place.
			anchor[0] = x;
			anchor[1] = y;
			anchor[2] = z;
			visible.snap(0f);
		}
		x -= anchor[0];
		y -= anchor[1];
		z -= anchor[2];
		goal[0] = (float) (x + shape.getMin(Direction.Axis.X));
		goal[1] = (float) (y + shape.getMin(Direction.Axis.Y));
		goal[2] = (float) (z + shape.getMin(Direction.Axis.Z));
		goal[3] = (float) (x + shape.getMax(Direction.Axis.X));
		goal[4] = (float) (y + shape.getMax(Direction.Axis.Y));
		goal[5] = (float) (z + shape.getMax(Direction.Axis.Z));
		return true;
	}
}
