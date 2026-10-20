package dev.v1zir.visuals.module.impl.visuals;

import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.setting.KeySetting;
import dev.v1zir.visuals.module.setting.NumberSetting;
import dev.v1zir.visuals.util.Anim;
import net.minecraft.client.option.Perspective;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

/**
 * Hold a key to orbit the camera around yourself in third person while the player keeps looking
 * ahead; on release the camera glides back and the previous perspective returns.
 * <p>
 * The camera angle is kept as an offset from the player's own view, so the orbit follows the
 * player and the glide back still lands correctly while the player turns.
 */
public final class FreeLookModule extends Module {
	/** Degrees per mouse unit, the same factor vanilla uses to turn the player. */
	private static final float MOUSE_TO_DEGREES = 0.15f;

	public final KeySetting key = add(new KeySetting("Key", GLFW.GLFW_KEY_LEFT_ALT));
	public final NumberSetting sensitivity = add(new NumberSetting("Sensitivity", 1.0, 0.2, 3.0, 0.05).suffix("x"));

	private final Anim returnYaw = new Anim(0f, 9f);
	private final Anim returnPitch = new Anim(0f, 9f);
	private Perspective restore = Perspective.FIRST_PERSON;
	private boolean holding;
	private boolean active;
	private float yawOffset;
	private float pitchOffset;

	public FreeLookModule() {
		super("Free Look", "Hold a key to look around yourself", Category.VISUALS);
		setSubCategory("Camera");
	}

	/** Called once per frame before the camera is placed. */
	public void frame() {
		if (mc.player == null) return;
		// Free look drives the camera from behind; if the user switches the perspective, let go.
		if (active && mc.options.getPerspective() != Perspective.THIRD_PERSON_BACK) reset();
		boolean held = key.held();
		if (held && !holding) start();
		else if (!held && holding && active) release();
		holding = held;
		if (active && !holding) {
			yawOffset = returnYaw.target(homeYaw()).get();
			pitchOffset = returnPitch.target(homePitch()).get();
			if (returnYaw.settled() && returnPitch.settled()) finish();
		}
	}

	@Override
	protected void onDisable() {
		if (active) finish();
		holding = false;
	}

	/** Mouse movement while the key is held turns the camera instead of the player. */
	public boolean turn(double cursorDeltaX, double cursorDeltaY) {
		if (!active || !holding) return false;
		float pitch = mc.player.getPitch();
		float k = MOUSE_TO_DEGREES * sensitivity.f();
		yawOffset += (float) cursorDeltaX * k;
		pitchOffset = MathHelper.clamp(pitchOffset + (float) cursorDeltaY * k, -90f - pitch, 90f - pitch);
		return true;
	}

	public float yaw(float yaw) {
		return active ? yaw + yawOffset : yaw;
	}

	public float pitch(float pitch) {
		return active ? MathHelper.clamp(pitch + pitchOffset, -90f, 90f) : pitch;
	}

	private void start() {
		if (active) return; // pressed again while gliding back: keep orbiting from where the camera is
		restore = mc.options.getPerspective();
		yawOffset = homeYaw();
		pitchOffset = homePitch();
		if (restore != Perspective.THIRD_PERSON_BACK) mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
		active = true;
	}

	private void release() {
		// Take the short way home after orbiting several times around.
		float home = homeYaw();
		yawOffset = home + MathHelper.wrapDegrees(yawOffset - home);
		returnYaw.snap(yawOffset);
		returnPitch.snap(pitchOffset);
	}

	private void finish() {
		if (restore != Perspective.THIRD_PERSON_BACK && mc.options.getPerspective() == Perspective.THIRD_PERSON_BACK) {
			mc.options.setPerspective(restore);
		}
		reset();
	}

	private void reset() {
		active = false;
		yawOffset = 0f;
		pitchOffset = 0f;
	}

	/** Offset at which the camera from behind matches the perspective free look started in. */
	private float homeYaw() {
		return restore == Perspective.THIRD_PERSON_FRONT ? 180f : 0f;
	}

	private float homePitch() {
		return restore == Perspective.THIRD_PERSON_FRONT ? -2f * mc.player.getPitch() : 0f;
	}
}
