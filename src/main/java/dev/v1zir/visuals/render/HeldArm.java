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
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.function.Supplier;

/**
 * Hand Chams' Arm with items: in first person the player's own arm, with their skin and arm width, holds the item,
 * where vanilla shows the item alone. Vanilla holds items with their handle (or foot) at the very edge of the view,
 * so the item is raised into it a little, and the arm is placed from the item where vanilla (and View Model and
 * Swing Animations) then puts it, so it follows every swing, equip and bob: the item's model is looked up as vanilla
 * draws it, the point a hand holds is found on it (the lower end of a tool's handle, the foot of a flat item, the
 * underside of a block) and vanilla's own empty hand, in its resting pose, is moved so its fist is there.
 * <p>
 * Called from HeldItemRendererMixin right before vanilla draws a held item; plain arithmetic on reused objects.
 */
public final class HeldArm {
	/** Vanilla's resting item frame (HeldItemRenderer.applyEquipOffset with nothing in motion), right hand, view space. */
	private static final float EQUIP_X = 0.56f;
	private static final float EQUIP_Y = -0.52f;
	private static final float EQUIP_Z = -0.72f;
	/** How far below a held block the fist's centre sits, blocks: a little less than half a fist, so it rests in the hand. */
	private static final float UNDER_BLOCK = 0.1f;
	private static final float DEG = (float) (Math.PI / 180.0);
	/**
	 * Vanilla's empty right hand at rest, as HeldItemRenderer.renderArmHoldingItem places it with no swing and no equip
	 * motion: the matrix it hands to PlayerEntityRenderer.renderRightArm, in view space.
	 */
	private static final Matrix4f VANILLA_ARM = new Matrix4f()
		.translate(0.64f, -0.6f, -0.72f).rotateY(45f * DEG)
		.translate(-1f, 3.6f, 3.5f).rotateZ(120f * DEG).rotateX(200f * DEG).rotateY(-135f * DEG)
		.translate(5.6f, 0f, 0f);
	/**
	 * From a fist centred on the origin, with the palm along x and the forearm along y (towards the hand), to the arm
	 * model's own space as PlayerEntityRenderer.renderArm expects it (as KnifeAnimator places the arm on a knife).
	 */
	private static final Matrix4f GRIP_TO_ARM = new Matrix4f()
		.translate(0.75f / 16f, -8.2f / 16f, 0f).rotateZ(-0.1f).translate(5f / 16f, -2.25f / 16f, 0f);
	/** The centre of that hand's fist, view space. */
	private static final Vector3fc VANILLA_FIST = VANILLA_ARM.transformPosition(new Matrix4f(GRIP_TO_ARM).invert().transformPosition(new Vector3f()));
	private static final ItemRenderState STATE = new ItemRenderState();
	private static final MatrixStack MODEL = new MatrixStack();
	/**
	 * The hand's base, as renderFirstPersonItem starts from it: the view (with its bob and hurt tilt) as the hand's
	 * matrices see it. They do not start from the view itself (renderHand turns them by the inverse of the camera's
	 * rotation and the shader turns them back), so everything placed in the view goes through this.
	 */
	private static final Matrix4f BASE = new Matrix4f();
	private static final Matrix4f TO_BASE = new Matrix4f();
	private static final Matrix3f BASE_NORMAL = new Matrix3f();
	private static final Matrix3f TURN = new Matrix3f();
	private static final Matrix4f ARM = new Matrix4f();
	private static final Matrix4f FRAME = new Matrix4f();
	private static final Vector3f GRIP = new Vector3f();
	private static final Vector3f REST = new Vector3f();
	private static final Vector3f HELD = new Vector3f();
	private static final Vector3f AXIS = new Vector3f();

	private HeldArm() {
	}

	/** How a hand holds the item. */
	private enum Hold {
		/** Tools, weapons and rods: the fist round the lower end of the handle. */
		HANDLE,
		/** Any other flat item: pinched at its foot. */
		FLAT,
		/** Block models: resting on the palm. */
		BLOCK
	}

	/** Start of renderFirstPersonItem (HeldItemRendererMixin): the matrices as they are there are the hand's base. */
	public static void begin(MatrixStack matrices) {
		BASE.set(matrices.peek().getPositionMatrix());
		BASE_NORMAL.set(matrices.peek().getNormalMatrix());
		TO_BASE.set(BASE).invert();
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

		// The point a hand holds, in the model's own coordinates.
		float midX = (minX + maxX) * 0.5f;
		float midZ = (minZ + maxZ) * 0.5f;
		switch (hold) {
			case HANDLE -> HELD.set(minX + (maxX - minX) * 0.2f, minY + (maxY - minY) * 0.2f, midZ);
			case FLAT -> HELD.set(midX, minY + (maxY - minY) * 0.12f, midZ);
			case BLOCK -> HELD.set(midX, minY, midZ);
		}

		// Where vanilla would hold that point at rest, and the item moved by as much as puts it in the fist of vanilla's
		// own empty hand: the arm keeps its natural size and pose and the item lies in it, a little further off and so
		// a little smaller, as the Knives view model holds a knife. Vanilla's matrices are these, so the item it draws
		// next moves with it; swings and the equip motion still move both.
		grip(transform, left, EQUIP_X, EQUIP_Y, EQUIP_Z, hold == Hold.BLOCK, REST);
		float fistX = left ? -VANILLA_FIST.x() : VANILLA_FIST.x();
		Matrix4f item = matrices.peek().getPositionMatrix();
		FRAME.set(TO_BASE).mul(item).translateLocal(fistX - REST.x, VANILLA_FIST.y() - REST.y, VANILLA_FIST.z() - REST.z);
		item.set(BASE).mul(FRAME);

		// The grip where the item is now (the hand's matrices, then the base): rest plus whatever moves it.
		MODEL.push();
		MODEL.peek().getPositionMatrix().set(item);
		if (transform != null) transform.apply(left, MODEL.peek());
		MODEL.translate(-0.5f, -0.5f, -0.5f);
		Matrix4f model = MODEL.peek().getPositionMatrix();
		model.transformPosition(GRIP.set(HELD));
		if (hold == Hold.BLOCK) {
			// Under the block, along its own up.
			model.transformDirection(AXIS.set(0f, 1f, 0f)).normalize();
			GRIP.sub(AXIS.x * UNDER_BLOCK, AXIS.y * UNDER_BLOCK, AXIS.z * UNDER_BLOCK);
		}
		MODEL.pop();
		if (!Float.isFinite(GRIP.x) || !Float.isFinite(GRIP.y) || !Float.isFinite(GRIP.z)) return;

		// Vanilla's resting hand, moved along with the grip (only by swings and the equip motion now), in the base as
		// vanilla places its own. Worked out for a right hand; a left one is its mirror image across the view.
		TO_BASE.transformPosition(GRIP);
		if (left) GRIP.x = -GRIP.x;
		ARM.translation(GRIP.x - VANILLA_FIST.x(), GRIP.y - VANILLA_FIST.y(), GRIP.z - VANILLA_FIST.z()).mul(VANILLA_ARM);
		if (left) ARM.scaleLocal(-1f, 1f, 1f).scale(-1f, 1f, 1f);

		matrices.push();
		MatrixStack.Entry entry = matrices.peek();
		// The arm's rotation (a mirrored one is one too) turns its normals, after the base's own.
		entry.getNormalMatrix().set(BASE_NORMAL).mul(TURN.set(ARM));
		entry.getPositionMatrix().set(BASE).mul(ARM);
		PlayerEntityRenderer<AbstractClientPlayerEntity> renderer = mc.getEntityRenderDispatcher().getPlayerRenderer(player);
		Identifier skin = player.getSkin().body().texturePath();
		if (left) renderer.renderLeftArm(matrices, queue, light, skin, player.isModelPartVisible(PlayerModelPart.LEFT_SLEEVE));
		else renderer.renderRightArm(matrices, queue, light, skin, player.isModelPartVisible(PlayerModelPart.RIGHT_SLEEVE));
		matrices.pop();
	}

	/**
	 * Where the held point HELD (model coordinates) is, in the hand's base, with the item frame at (x, y, z) (mirrored
	 * for a left hand) and the model placed by its display transform; under a block, the fist's centre below it.
	 */
	private static void grip(Transformation transform, boolean left, float x, float y, float z, boolean block, Vector3f out) {
		MODEL.push();
		MODEL.peek().getPositionMatrix().translation(left ? -x : x, y, z);
		if (transform != null) transform.apply(left, MODEL.peek());
		MODEL.translate(-0.5f, -0.5f, -0.5f);
		Matrix4f model = MODEL.peek().getPositionMatrix();
		model.transformPosition(out.set(HELD));
		if (block) {
			model.transformDirection(AXIS.set(0f, 1f, 0f)).normalize();
			out.sub(AXIS.x * UNDER_BLOCK, AXIS.y * UNDER_BLOCK, AXIS.z * UNDER_BLOCK);
		}
		MODEL.pop();
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
