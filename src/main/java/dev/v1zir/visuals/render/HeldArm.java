package dev.v1zir.visuals.render;

import dev.v1zir.visuals.mixin.ItemRenderStateAccessor;
import dev.v1zir.visuals.mixin.LayerRenderStateAccessor;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.impl.visuals.HandChamsModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.model.json.Transformation;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerModelPart;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.consume.UseAction;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.function.Supplier;

/**
 * Hand Chams' Arm with items: in first person the player's own arm, with their skin and arm width, holds the item,
 * where vanilla shows the item alone. The item stays where vanilla (and View Model and Swing Animations) puts it, and
 * the arm is placed from it, so it follows every swing, equip and bob: the item's model is looked up as vanilla draws
 * it, the point a hand holds is found on it (the lower end of a tool's handle, the foot of a flat item, the underside
 * of a block) and the fist is set there, the forearm running down and back out of the view.
 * <p>
 * Called from HeldItemRendererMixin right before vanilla draws a held item; plain arithmetic on reused objects.
 */
public final class HeldArm {
	/** From the fist towards the elbow at rest, view space (x right, y up, z towards the eye), for a right hand. */
	private static final Vector3fc ELBOW = new Vector3f(0.3f, -0.62f, 0.72f).normalize();
	/** How far below a held block the fist's centre sits, blocks: a little less than half a fist, so it rests in the hand. */
	private static final float UNDER_BLOCK = 0.1f;
	/**
	 * From a fist centred on the origin, with the palm along x and the forearm along y (towards the hand), to the arm
	 * model's own space as PlayerEntityRenderer.renderArm expects it (as KnifeAnimator places the arm on a knife).
	 */
	private static final Matrix4f GRIP_TO_ARM = new Matrix4f()
		.translate(0.75f / 16f, -8.2f / 16f, 0f).rotateZ(-0.1f).translate(5f / 16f, -2.25f / 16f, 0f);
	private static final ItemRenderState STATE = new ItemRenderState();
	private static final MatrixStack MODEL = new MatrixStack();
	private static final Matrix4f ARM = new Matrix4f();
	private static final Vector3f GRIP = new Vector3f();
	private static final Vector3f AXIS = new Vector3f();
	private static final Vector3f FOREARM = new Vector3f();
	private static final Vector3f PALM = new Vector3f();
	private static final Vector3f SIDE = new Vector3f();

	private HeldArm() {
	}

	/** How a hand holds the item. */
	private enum Hold {
		/** Tools, weapons and rods: the fist round the lower end of the handle, along the sprite's diagonal. */
		HANDLE,
		/** Any other flat item: pinched at its foot. */
		FLAT,
		/** Block models: resting on the palm. */
		BLOCK
	}

	/**
	 * The held item about to be drawn with the matrices as they are (renderFirstPersonItem, right before renderItem):
	 * draws the arm holding it, when Hand Chams asks for it and the item is one a hand holds plainly.
	 */
	public static void render(AbstractClientPlayerEntity player, Hand hand, ItemStack stack, MatrixStack matrices, OrderedRenderCommandQueue queue,
							  int light) {
		HandChamsModule chams = ModuleManager.get(HandChamsModule.class);
		if (chams == null || !chams.armWithItems() || stack.isEmpty() || player.isInvisible()) return;
		Hold hold = hold(stack);
		if (hold == null) return;
		// While used, most items take poses of their own (drawing a bow, blocking, tooting a horn); food and drink are
		// only lifted to the mouth, and the arm goes with them.
		if (player.isUsingItem() && player.getActiveHand() == hand) {
			UseAction action = stack.getUseAction();
			if (action != UseAction.EAT && action != UseAction.DRINK && action != UseAction.NONE) return;
		}
		Arm arm = hand == Hand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
		boolean left = arm == Arm.LEFT;
		ItemDisplayContext context = left ? ItemDisplayContext.FIRST_PERSON_LEFT_HAND : ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;

		// The item's model as vanilla is about to draw it: its first layer's display transform and its corners.
		MinecraftClient mc = MinecraftClient.getInstance();
		STATE.clear();
		mc.getItemModelManager().updateForLivingEntity(STATE, stack, context, player);
		ItemRenderStateAccessor state = (ItemRenderStateAccessor) STATE;
		ItemRenderState.LayerRenderState[] layers = state.v1zir$layers();
		if (state.v1zir$layerCount() <= 0 || layers == null || layers.length == 0 || layers[0] == null) return;
		LayerRenderStateAccessor layer = (LayerRenderStateAccessor) layers[0];
		Transformation transform = layer.v1zir$transform();
		float minX = 0f, minY = 0f, minZ = 0f, maxX = 1f, maxY = 1f, maxZ = 1f;
		Supplier<Vector3fc[]> vertices = layer.v1zir$vertices();
		Vector3fc[] corners = vertices != null ? vertices.get() : null;
		if (corners != null && corners.length > 0) {
			minX = minY = minZ = Float.MAX_VALUE;
			maxX = maxY = maxZ = -Float.MAX_VALUE;
			for (Vector3fc v : corners) {
				minX = Math.min(minX, v.x());
				minY = Math.min(minY, v.y());
				minZ = Math.min(minZ, v.z());
				maxX = Math.max(maxX, v.x());
				maxY = Math.max(maxY, v.y());
				maxZ = Math.max(maxZ, v.z());
			}
		}
		// A model with no depth to speak of is flat, whatever the item.
		if (hold == Hold.BLOCK && maxZ - minZ < 0.3f) hold = Hold.FLAT;

		// The model's matrix in the hand's space, as its layer applies it.
		MODEL.push();
		MODEL.peek().getPositionMatrix().set(matrices.peek().getPositionMatrix());
		if (transform != null) transform.apply(left, MODEL.peek());
		MODEL.translate(-0.5f, -0.5f, -0.5f);
		Matrix4f model = MODEL.peek().getPositionMatrix();
		float midX = (minX + maxX) * 0.5f;
		float midZ = (minZ + maxZ) * 0.5f;
		switch (hold) {
			case HANDLE -> {
				model.transformPosition(GRIP.set(minX + (maxX - minX) * 0.2f, minY + (maxY - minY) * 0.2f, midZ));
				model.transformDirection(AXIS.set(maxX - minX, maxY - minY, 0f)).normalize();
			}
			case FLAT -> {
				model.transformPosition(GRIP.set(midX, minY + (maxY - minY) * 0.12f, midZ));
				model.transformDirection(AXIS.set(1f, 0f, 0f)).normalize();
			}
			case BLOCK -> {
				model.transformPosition(GRIP.set(midX, minY, midZ));
				// Here the axis is the palm's: up out of the block's underside.
				model.transformDirection(AXIS.set(0f, 1f, 0f)).normalize();
				GRIP.sub(AXIS.x * UNDER_BLOCK, AXIS.y * UNDER_BLOCK, AXIS.z * UNDER_BLOCK);
			}
		}
		MODEL.pop();
		if (!Float.isFinite(GRIP.x) || !Float.isFinite(AXIS.x) || AXIS.lengthSquared() < 0.5f) return;

		// Worked out for a right hand; a left one is its mirror image across the view.
		if (left) {
			GRIP.x = -GRIP.x;
			AXIS.x = -AXIS.x;
		}
		if (!place(hold == Hold.BLOCK)) return;
		if (left) ARM.scaleLocal(-1f, 1f, 1f).scale(-1f, 1f, 1f);

		matrices.push();
		MatrixStack.Entry entry = matrices.peek();
		entry.getPositionMatrix().set(ARM);
		// A rotation (a mirrored one is one too): its normals turn with it.
		entry.getNormalMatrix().set(ARM);
		PlayerEntityRenderer<AbstractClientPlayerEntity> renderer = mc.getEntityRenderDispatcher().getPlayerRenderer(player);
		Identifier skin = player.getSkin().body().texturePath();
		if (left) renderer.renderLeftArm(matrices, queue, light, skin, player.isModelPartVisible(PlayerModelPart.LEFT_SLEEVE));
		else renderer.renderRightArm(matrices, queue, light, skin, player.isModelPartVisible(PlayerModelPart.RIGHT_SLEEVE));
		matrices.pop();
	}

	/**
	 * The right arm's matrix into ARM, its fist on GRIP: round the handle along AXIS, or (onPalm) with AXIS as the
	 * palm's direction. The forearm runs as close to the resting elbow direction as the grip allows.
	 */
	private static boolean place(boolean onPalm) {
		// The forearm, from the fist towards the elbow, square to the axis.
		float along = ELBOW.dot(AXIS);
		FOREARM.set(ELBOW).sub(AXIS.x * along, AXIS.y * along, AXIS.z * along);
		if (FOREARM.lengthSquared() < 0.04f) FOREARM.set(0f, -1f, 0f).sub(AXIS.x * -AXIS.y, AXIS.y * -AXIS.y, AXIS.z * -AXIS.y);
		if (FOREARM.lengthSquared() < 1.0e-4f) return false;
		FOREARM.normalize();
		// The arm's y runs from the shoulder to the hand: from the elbow towards the fist.
		float yx = -FOREARM.x, yy = -FOREARM.y, yz = -FOREARM.z;
		if (onPalm) {
			PALM.set(AXIS);
		} else {
			// Round a handle the palm faces across it, in towards the middle of the view.
			PALM.set(yy * AXIS.z - yz * AXIS.y, yz * AXIS.x - yx * AXIS.z, yx * AXIS.y - yy * AXIS.x).normalize();
			if (PALM.x > 0f) PALM.negate();
		}
		// x (palm) cross y completes the frame.
		SIDE.set(PALM.y * yz - PALM.z * yy, PALM.z * yx - PALM.x * yz, PALM.x * yy - PALM.y * yx);
		ARM.set(PALM.x, PALM.y, PALM.z, 0f, yx, yy, yz, 0f, SIDE.x, SIDE.y, SIDE.z, 0f, GRIP.x, GRIP.y, GRIP.z, 1f).mul(GRIP_TO_ARM);
		return true;
	}

	/** How a hand holds this item, or null for items vanilla poses itself or a hand cannot plainly hold. */
	private static Hold hold(ItemStack stack) {
		if (stack.isOf(Items.FILLED_MAP) || stack.isOf(Items.CROSSBOW) || stack.isOf(Items.BOW) || stack.isOf(Items.SHIELD)
			|| stack.isOf(Items.TRIDENT) || stack.isOf(Items.SPYGLASS)) return null;
		if (stack.isIn(ItemTags.SWORDS) || stack.isIn(ItemTags.AXES) || stack.isIn(ItemTags.PICKAXES) || stack.isIn(ItemTags.SHOVELS)
			|| stack.isIn(ItemTags.HOES) || stack.isOf(Items.STICK) || stack.isOf(Items.BLAZE_ROD) || stack.isOf(Items.BREEZE_ROD)
			|| stack.isOf(Items.BONE) || stack.isOf(Items.FISHING_ROD) || stack.isOf(Items.CARROT_ON_A_STICK)
			|| stack.isOf(Items.WARPED_FUNGUS_ON_A_STICK) || stack.isOf(Items.MACE) || stack.isOf(Items.BRUSH)
			|| stack.isOf(Items.DEBUG_STICK)) return Hold.HANDLE;
		if (stack.getItem() instanceof BlockItem) return Hold.BLOCK;
		return Hold.FLAT;
	}
}
