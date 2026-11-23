package dev.v1zir.visuals.render.cosmetic;

import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.impl.cosmetics.CosmeticsModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Items;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Cosmetics on players: the hat on the head, wings and the cape on the back, following the model's pose (looking
 * around, sneaking, swimming, flying) because they are drawn in the space of its head and body. A cape is cloth,
 * simulated for each wearer (CapeCloth) and kept off the back, the legs and the arms as they are posed this frame;
 * the world and the screens that show a player (the inventory, the Cosmetics tab) each have their own, as they turn
 * the player differently. Runs wherever the player model is drawn, and never in first person, where the model is not
 * drawn. Other players only when the module says so.
 */
public final class CosmeticsFeature extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {
	/** Set while the world is being drawn (WorldRendererMixin): players drawn otherwise are on a screen. */
	public static boolean worldPass;

	private static final Map<Integer, Cloth> WORLD = new HashMap<>();
	private static final Map<Integer, Cloth> SCREEN = new HashMap<>();
	private static long lastSweep;
	private static final Matrix4f TO_WORLD = new Matrix4f();
	private static final Matrix4f BODY_INVERSE = new Matrix4f();
	private static final Matrix4f LIMB = new Matrix4f();
	private static final Vector3f POINT = new Vector3f();
	/** Pixels between the cloth and what it rests on. */
	private static final float MARGIN = 0.35f;

	public CosmeticsFeature(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
		super(context);
	}

	@Override
	public void render(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, PlayerEntityRenderState state, float limbAngle,
					   float limbDistance) {
		CosmeticsModule module = ModuleManager.get(CosmeticsModule.class);
		if (module == null || !module.isEnabled() || state.invisible) return;
		MinecraftClient mc = MinecraftClient.getInstance();
		boolean self = mc.player != null && state.id == mc.player.getId();
		if (!self && !module.others.on()) return;
		Cosmetic hat = module.item(Cosmetic.Slot.HAT);
		Cosmetic wings = module.item(Cosmetic.Slot.WINGS);
		Cosmetic cape = module.item(Cosmetic.Slot.CAPE);
		if (hat != null && module.hideUnderHelmet.on() && !state.equippedHeadStack.isEmpty()) hat = null;
		// An elytra takes the back.
		if (state.equippedChestStack.isOf(Items.ELYTRA)) {
			wings = null;
			cape = null;
		}
		if (hat == null && wings == null && cape == null) return;
		CosmeticMotion motion = CosmeticMotion.of(state.id, state.x, state.y, state.z, state.bodyYaw, state.isInSneakingPose, state.isGliding,
			state.isSwimming);
		PlayerEntityModel model = getContextModel();
		// Pose the shared model for this player now: it is posed again when the queue draws it.
		model.setAngles(state);
		ModelPart root = model.getRootPart();
		if (hat != null) {
			matrices.push();
			root.applyTransform(matrices);
			model.head.applyTransform(matrices);
			CosmeticRenderer.submit(matrices, queue, hat, light, motion, null);
			matrices.pop();
		}
		if (wings != null || cape != null) {
			// Over a chestplate the back is a pixel further out.
			float armor = state.equippedChestStack.isEmpty() ? 0f : 1f;
			matrices.push();
			root.applyTransform(matrices);
			model.body.applyTransform(matrices);
			if (cape != null) {
				CapeCloth cloth = cloth(state.id);
				floors(cloth, model, armor, !state.equippedLegsStack.isEmpty(), wings != null);
				toWorld(TO_WORLD, state, model, armor);
				cloth.update(System.nanoTime(), TO_WORLD, state.x, state.y, state.z);
				matrices.push();
				if (armor > 0f) matrices.translate(0f, 0f, armor / 16f);
				CosmeticRenderer.submit(matrices, queue, cape, light, motion, cloth);
				matrices.pop();
			}
			if (wings != null) {
				// Wings sit over the armour and over a cape, which then stays under them.
				float back = armor + (cape != null ? CosmeticModels.CAPE_THICKNESS + 0.45f : 0f);
				if (back > 0f) matrices.translate(0f, 0f, back / 16f);
				CosmeticRenderer.submit(matrices, queue, wings, light, motion, null);
			}
			matrices.pop();
		}
	}

	/** This wearer's cloth in the world or on a screen, forgetting wearers not seen for a while. */
	private static CapeCloth cloth(int id) {
		Map<Integer, Cloth> map = worldPass ? WORLD : SCREEN;
		Cloth cloth = map.computeIfAbsent(id, k -> new Cloth());
		long now = System.nanoTime();
		cloth.seen = now;
		if (now - lastSweep > 10_000_000_000L) {
			lastSweep = now;
			for (Map<Integer, Cloth> m : java.util.List.of(WORLD, SCREEN)) {
				Iterator<Cloth> it = m.values().iterator();
				while (it.hasNext()) if (now - it.next().seen > 10_000_000_000L) it.remove();
			}
		}
		return cloth.cloth;
	}

	/**
	 * The cape's body space (with the armour's offset) to the wearer's world space, in pixels of the model with world
	 * axes and the feet at the origin: the renderer's turn to the body's yaw, its lean when swimming or crawling, its
	 * flip of the model upright, then the model's root and body parts as posed.
	 */
	private static void toWorld(Matrix4f out, PlayerEntityRenderState state, PlayerEntityModel model, float armor) {
		out.identity().rotateY((float) Math.toRadians(180f - state.bodyYaw));
		if (state.leaningPitch > 0f) {
			out.rotateX((float) Math.toRadians(-90f * state.leaningPitch));
			if (state.isSwimming) out.translate(0f, -CapeCloth.PX_PER_BLOCK, 0.3f * CapeCloth.PX_PER_BLOCK);
		}
		out.scale(-1f, -1f, 1f).translate(0f, -24.016f, 0f);
		apply(out, model.getRootPart());
		apply(out, model.body);
		// The armour's offset, then the cosmetic's flip from the model's axes (x left, y down) to the mesh's.
		out.translate(0f, 0f, armor).scale(-1f, -1f, 1f);
	}

	/** A model part's own transform (pixels, radians), as ModelPart.applyTransform does it. */
	private static void apply(Matrix4f m, ModelPart part) {
		m.translate(part.originX, part.originY, part.originZ);
		if (part.pitch != 0f || part.yaw != 0f || part.roll != 0f) m.rotateZYX(part.roll, part.yaw, part.pitch);
		if (part.xScale != 1f || part.yScale != 1f || part.zScale != 1f) m.scale(part.xScale, part.yScale, part.zScale);
	}

	/**
	 * How far back the cape must stay, row by row down from the neck, in its own space: the torso's back, then the
	 * legs' backs as they are posed (a stride puts one far back), and at the sides also the arms'. With wings on, the
	 * cape is held close under them as well.
	 */
	private static void floors(CapeCloth cloth, PlayerEntityModel model, float armor, boolean leggings, boolean wings) {
		float[] mid = cloth.floorMid, right = cloth.floorRight, left = cloth.floorLeft;
		java.util.Arrays.fill(mid, -99f);
		java.util.Arrays.fill(right, -99f);
		java.util.Arrays.fill(left, -99f);
		// In the cape's space the back of the torso is at z = 2 plus the armour, and the cape is moved out by the armour.
		for (int row = 0; row <= 12 && row < CapeCloth.FLOOR_ROWS; row++) {
			float back = 2f + MARGIN;
			mid[row] = back;
			right[row] = back;
			left[row] = back;
		}
		BODY_INVERSE.identity();
		apply(BODY_INVERSE, model.body);
		BODY_INVERSE.invert();
		float legBack = 2f + (leggings ? 0.5f : 0f);
		limb(mid, model.rightLeg, -2f, 2f, 0f, 12f, legBack, armor);
		limb(mid, model.leftLeg, -2f, 2f, 0f, 12f, legBack, armor);
		limb(right, model.rightLeg, -2f, 2f, 0f, 12f, legBack, armor);
		limb(left, model.leftLeg, -2f, 2f, 0f, 12f, legBack, armor);
		float sleeve = 2f + (armor > 0f ? 1f : 0f);
		limb(right, model.rightArm, -3f, 1f, -2f, 10f, sleeve, armor);
		limb(left, model.leftArm, -1f, 3f, -2f, 10f, sleeve, armor);
		cloth.ceiling = wings ? 2.3f + 1.0f : Float.NaN;
	}

	/** Marks the back face of a limb (its box x0..x1, y0..y1 at z = back, in its own space) in a floor profile. */
	private static void limb(float[] floor, ModelPart part, float x0, float x1, float y0, float y1, float back, float armor) {
		LIMB.set(BODY_INVERSE);
		apply(LIMB, part);
		for (float y = y0; y <= y1 + 0.001f; y += 1f) {
			for (int k = 0; k < 3; k++) {
				float x = x0 + (x1 - x0) * k * 0.5f;
				LIMB.transformPosition(POINT.set(x, y, back));
				// The body's space has y down from the neck: the row; z is back, less the armour's offset.
				int row = Math.round(POINT.y);
				if (row < 0 || row >= CapeCloth.FLOOR_ROWS) continue;
				floor[row] = Math.max(floor[row], POINT.z - armor + MARGIN);
			}
		}
	}

	/** Whether this player wears a cosmetic cape, which then hides the vanilla one (CapeFeatureRendererMixin). */
	public static boolean wearsCape(PlayerEntityRenderState state) {
		CosmeticsModule module = ModuleManager.get(CosmeticsModule.class);
		if (module == null || !module.isEnabled() || module.item(Cosmetic.Slot.CAPE) == null) return false;
		MinecraftClient mc = MinecraftClient.getInstance();
		boolean self = mc.player != null && state.id == mc.player.getId();
		return (self || module.others.on()) && !state.equippedChestStack.isOf(Items.ELYTRA);
	}

	private static final class Cloth {
		final CapeCloth cloth = new CapeCloth();
		long seen;
	}
}
