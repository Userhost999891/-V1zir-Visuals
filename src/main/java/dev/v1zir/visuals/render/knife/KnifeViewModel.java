package dev.v1zir.visuals.render.knife;

import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.impl.knives.KnivesModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerModelPart;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The first-person knife (HeldItemRendererMixin hands the main hand's render over to it): the player's own arm, with
 * their skin and arm width, holding the knife, both posed by {@link KnifeAnimator}. It notices a newly drawn knife,
 * every swing (and whether it was fully charged) and how fast the camera turns; vanilla's attack timing is untouched.
 */
public final class KnifeViewModel {
	private static final KnifeAnimator ANIMATOR = new KnifeAnimator();
	private static final Matrix4f MIRROR = new Matrix4f();
	private static final Vector3f MOVE = new Vector3f();
	private static final Quaternionf TURN = new Quaternionf();

	private static long lastFrame;
	private static float lastSwing;
	private static float lastCharge = 1f;
	private static float lastYaw;
	private static float lastPitch;
	private static int shownSlot = -1;
	private static Item shownItem;
	private static KnifeType shownType;

	private KnifeViewModel() {
	}

	/** Forgets the knife in hand, so the next frame draws it in. */
	public static void reset() {
		shownSlot = -1;
		shownItem = null;
	}

	public static void inspect() {
		ANIMATOR.inspect();
	}

	/**
	 * Renders the main hand's knife and arm; returns false (and draws nothing) when the hand holds no knife, so
	 * vanilla renders it. Called for both hands from renderFirstPersonItem.
	 */
	public static boolean render(AbstractClientPlayerEntity player, float tickProgress, Hand hand, float swingProgress, ItemStack item,
								 MatrixStack matrices, OrderedRenderCommandQueue queue, int light) {
		if (hand != Hand.MAIN_HAND) return false;
		KnivesModule module = ModuleManager.get(KnivesModule.class);
		if (module == null || !module.isEnabled() || !module.replaces(item)) {
			shownItem = null;
			return false;
		}
		long now = System.nanoTime();
		float dt = lastFrame == 0L ? 0f : (now - lastFrame) / 1.0e9f;
		lastFrame = now;
		KnifeType type = module.type();
		int slot = player.getInventory().getSelectedSlot();
		// A different slot, item or knife: draw it. Durability or component updates of the same sword are not a switch.
		if (slot != shownSlot || item.getItem() != shownItem || type != shownType) {
			shownSlot = slot;
			shownItem = item.getItem();
			shownType = type;
			ANIMATOR.draw(type);
			lastSwing = swingProgress;
		}
		// A swing starts when the progress jumps back; the charge is last frame's, before the hit reset it.
		if (swingProgress > 0f && (lastSwing == 0f || swingProgress < lastSwing - 0.05f)) ANIMATOR.attack(lastCharge >= 0.95f);
		lastSwing = swingProgress;
		lastCharge = player.getAttackCooldownProgress(0f);
		float yaw = player.getYaw(tickProgress);
		float pitch = player.getPitch(tickProgress);
		float yawSpeed = dt > 0f ? (yaw - lastYaw) / dt : 0f;
		float pitchSpeed = dt > 0f ? (pitch - lastPitch) / dt : 0f;
		lastYaw = yaw;
		lastPitch = pitch;
		ANIMATOR.update(dt, module.speed.f(), clampSpeed(yawSpeed), clampSpeed(pitchSpeed), module.sway.f(),
			module.offsetX.f(), module.offsetY.f(), module.offsetZ.f());

		boolean left = player.getMainArm() == Arm.LEFT;
		if (!player.isInvisible()) {
			matrices.push();
			apply(matrices, ANIMATOR.arm, left);
			PlayerEntityRenderer<AbstractClientPlayerEntity> renderer = MinecraftClient.getInstance().getEntityRenderDispatcher().getPlayerRenderer(player);
			Identifier skin = player.getSkin().body().texturePath();
			if (left) renderer.renderLeftArm(matrices, queue, light, skin, player.isModelPartVisible(PlayerModelPart.LEFT_SLEEVE));
			else renderer.renderRightArm(matrices, queue, light, skin, player.isModelPartVisible(PlayerModelPart.RIGHT_SLEEVE));
			matrices.pop();
		}
		matrices.push();
		apply(matrices, ANIMATOR.knife, left);
		matrices.scale(KnifeAnimator.SCALE, KnifeAnimator.SCALE, KnifeAnimator.SCALE);
		KnifeRenderer.submit(matrices, queue, type.mesh(), ANIMATOR.parts, module.finish().ordinal(), light);
		matrices.pop();
		return true;
	}

	/** Teleports and snap turns are not camera motion to sway with. */
	private static float clampSpeed(float degreesPerSecond) {
		return Math.max(-900f, Math.min(900f, degreesPerSecond));
	}

	/** Multiplies the matrix stack by a rigid pose; a left-handed player gets the pose mirrored across the screen. */
	private static void apply(MatrixStack matrices, Matrix4f pose, boolean mirror) {
		Matrix4f m = mirror ? MIRROR.set(pose).scaleLocal(-1f, 1f, 1f).scale(-1f, 1f, 1f) : pose;
		m.getTranslation(MOVE);
		m.getUnnormalizedRotation(TURN).normalize();
		matrices.translate(MOVE.x, MOVE.y, MOVE.z);
		matrices.multiply(TURN);
	}
}
