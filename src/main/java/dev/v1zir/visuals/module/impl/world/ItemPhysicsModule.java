package dev.v1zir.visuals.module.impl.world;

import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.module.setting.NumberSetting;
import dev.v1zir.visuals.util.Anim;
import net.minecraft.client.render.entity.state.ItemEntityRenderState;
import net.minecraft.client.render.entity.state.ItemStackEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.Box;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Dropped items lie on the ground instead of floating and spinning: flat items lie down, blocks
 * stand on a face, each turned its own way. In the air they tumble, and when they land a spring
 * rolls them onto the nearest resting side.
 *
 * <p>Vanilla's bobbing and spin are replaced at the start of its stack drawing
 * (ItemEntityRendererMixin): the pose the renderer started from is restored and this one applied.
 */
public final class ItemPhysicsModule extends Module {
	private static final float TAU = (float) (Math.PI * 2);
	/** Turns a flat item from standing to lying face up. */
	private static final float LIE_DOWN = (float) (-Math.PI / 2);
	/** Keeps the item off the ground surface. */
	private static final float LIFT = 0.01f;
	private static final float SPIN = 9f;

	public final BoolSetting tumble = add(new BoolSetting("Tumble", true));
	public final NumberSetting spinSpeed = add(new NumberSetting("Spin speed", 1.0, 0.2, 3.0, 0.05).suffix("x").visibleIf(tumble::on));

	private static final Matrix4f BASE = new Matrix4f();
	private static final Matrix3f BASE_NORMAL = new Matrix3f();
	private static final Quaternionf ROTATION = new Quaternionf();
	/** The item entity whose stack is about to be drawn, between begin() and its first stack draw. */
	private static ItemEntityRenderState pending;

	public ItemPhysicsModule() {
		super("Item Physics", "Dropped items lie flat, tumble and settle", Category.WORLD);
		setSubCategory("Render");
	}

	/** Tumble state of one item entity, kept on the entity (ItemEntityMixin). */
	public static final class Tumble {
		final Anim angle = new Anim(0f, 9f);
		long last = System.nanoTime();
		boolean resting = true;
	}

	/** Implemented by ItemEntity through ItemEntityMixin. */
	public interface Tumbling {
		Tumble v1zir$tumble();
	}

	/** Implemented by ItemEntityRenderState through ItemEntityRenderStateMixin: the tilt of this frame, NaN for vanilla. */
	public interface Posed {
		float v1zir$tilt();

		void v1zir$setTilt(float tilt);
	}

	/** Advances the entity's tumble and hands this frame's tilt to its render state (end of updateRenderState). */
	public static void update(ItemEntity entity, ItemEntityRenderState state) {
		ItemPhysicsModule module = ModuleManager.get(ItemPhysicsModule.class);
		if (module == null || !module.isEnabled() || state.itemRenderState.isEmpty()) {
			((Posed) state).v1zir$setTilt(Float.NaN);
			return;
		}
		Tumble t = ((Tumbling) entity).v1zir$tumble();
		long now = System.nanoTime();
		float dt = Math.min((now - t.last) / 1.0e9f, 0.1f);
		t.last = now;
		boolean resting = entity.isOnGround() || entity.isTouchingWater() || !module.tumble.on();
		if (resting) {
			if (!t.resting) {
				// Landed: settle on the nearest resting side (flat items only face up, so stacks lie on top).
				float step = flat(state.itemRenderState.getModelBoundingBox()) ? TAU : TAU / 4f;
				t.angle.target(Math.round(t.angle.get() / step) * step);
			} else if (t.angle.settled()) {
				t.angle.snap(t.angle.get() % TAU);
			}
		} else {
			float direction = state.uniqueOffset > Math.PI ? -1f : 1f;
			t.angle.target(t.angle.target() + direction * SPIN * module.spinSpeed.f() * dt);
		}
		t.resting = resting;
		((Posed) state).v1zir$setTilt(t.angle.get());
	}

	/** The renderer starts drawing the item entity (head of ItemEntityRenderer.render): remembers its starting pose. */
	public static void begin(ItemEntityRenderState state, MatrixStack matrices) {
		if (Float.isNaN(((Posed) state).v1zir$tilt())) return;
		pending = state;
		BASE.set(matrices.peek().getPositionMatrix());
		BASE_NORMAL.set(matrices.peek().getNormalMatrix());
	}

	public static void end() {
		pending = null;
	}

	/** First stack draw of the pending entity: replaces vanilla's bob and spin with the physics pose. */
	public static void apply(MatrixStack matrices, ItemStackEntityRenderState state) {
		if (state != pending) return;
		ItemEntityRenderState s = pending;
		pending = null;
		MatrixStack.Entry entry = matrices.peek();
		entry.getPositionMatrix().set(BASE);
		entry.getNormalMatrix().set(BASE_NORMAL);
		Box box = s.itemRenderState.getModelBoundingBox();
		float a = (flat(box) ? LIE_DOWN : 0f) + ((Posed) s).v1zir$tilt();
		float hy = (float) (box.maxY - box.minY) * 0.5f;
		float hz = (float) (box.maxZ - box.minZ) * 0.5f;
		// Rotated around its centre, the model reaches this far down: lift it to stand on the ground.
		float lift = hy * Math.abs((float) Math.cos(a)) + hz * Math.abs((float) Math.sin(a)) + LIFT;
		matrices.translate(0f, lift, 0f);
		matrices.multiply(ROTATION.rotationYXZ(s.uniqueOffset, a, 0f));
		matrices.translate((float) -(box.minX + box.maxX) * 0.5f, (float) -(box.minY + box.maxY) * 0.5f, (float) -(box.minZ + box.maxZ) * 0.5f);
	}

	/** Sprite items are a single pixel deep. */
	private static boolean flat(Box box) {
		return box.maxZ - box.minZ < 0.25 * Math.min(box.maxX - box.minX, box.maxY - box.minY);
	}
}
