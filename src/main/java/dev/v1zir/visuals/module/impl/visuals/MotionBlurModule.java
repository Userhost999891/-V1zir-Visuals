package dev.v1zir.visuals.module.impl.visuals;

import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.setting.NumberSetting;
import dev.v1zir.visuals.render.MotionBlur;
import dev.v1zir.visuals.render.world.WorldEffectRenderer;
import dev.v1zir.visuals.util.Anim;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4fc;

/**
 * Motion blur of the world, never of the hand or the GUI (render/MotionBlur). It follows the camera:
 * turning smears the frame along the turn and leaves soft trails, running blurs a little, and a still
 * view costs nothing because the pass does not run at all.
 */
public final class MotionBlurModule extends Module {
	/** Largest smear across the screen per frame, as a fraction of it. */
	private static final float MAX_SMEAR = 0.06f;

	public final NumberSetting strength = add(new NumberSetting("Strength", 0.6, 0.1, 1.0, 0.05));

	private float lastYaw;
	private float lastPitch;
	private double lastX;
	private double lastY;
	private double lastZ;
	private long lastFrame;
	private boolean tracking;

	public MotionBlurModule() {
		super("Motion Blur", "Blur the world while the view moves", Category.SHADERS);
		setSubCategory("World");
	}

	@Override
	protected void onDisable() {
		tracking = false;
		MotionBlur.reset();
	}

	/** Right after the world is drawn, before the hand (GameRendererMixin). */
	public static void renderIfEnabled() {
		MotionBlurModule module = ModuleManager.get(MotionBlurModule.class);
		if (module != null && module.isEnabled() && mc.world != null) module.render();
	}

	private void render() {
		Camera camera = mc.gameRenderer.getCamera();
		float yaw = camera.getYaw();
		float pitch = camera.getPitch();
		Vec3d pos = camera.getCameraPos();
		long now = System.nanoTime();
		float dt = (now - lastFrame) / 1.0e9f;
		float turnYaw = MathHelper.wrapDegrees(yaw - lastYaw);
		float turnPitch = pitch - lastPitch;
		double mx = pos.x - lastX, my = pos.y - lastY, mz = pos.z - lastZ;
		boolean first = !tracking || dt <= 0f || dt > 0.25f;
		lastYaw = yaw;
		lastPitch = pitch;
		lastX = pos.x;
		lastY = pos.y;
		lastZ = pos.z;
		lastFrame = now;
		tracking = true;
		if (first) {
			MotionBlur.reset();
			return;
		}
		float turn = (float) Math.sqrt(turnYaw * turnYaw + turnPitch * turnPitch) / dt;
		float speed = (float) Math.sqrt(mx * mx + my * my + mz * mz) / dt;
		// Degrees and blocks per second: a slow look around barely blurs, a flick or a sprint does.
		float motion = Math.min(1f, smooth((turn - 4f) / 220f) + 0.35f * smooth((speed - 5f) / 25f));
		if (motion <= 0.01f) {
			MotionBlur.reset();
			return;
		}
		float s = strength.f();
		// The turn on the screen: degrees over the field of view (from the frame's projection).
		Matrix4fc projection = WorldEffectRenderer.projection();
		float fovX = (float) Math.toDegrees(2 * Math.atan(1 / Math.max(1e-3f, projection.m00())));
		float fovY = (float) Math.toDegrees(2 * Math.atan(1 / Math.max(1e-3f, projection.m11())));
		float sx = clamp(turnYaw / fovX * s);
		float sy = clamp(turnPitch / fovY * s);
		// The trail fades by the same amount per second at any frame rate.
		MotionBlur.render(sx, sy, (float) Math.pow(0.88f * s * motion, dt * 60f));
	}

	private static float clamp(float v) {
		return Math.max(-MAX_SMEAR, Math.min(MAX_SMEAR, v));
	}

	private static float smooth(float t) {
		t = Anim.clamp01(t);
		return t * t * (3f - 2f * t);
	}
}
