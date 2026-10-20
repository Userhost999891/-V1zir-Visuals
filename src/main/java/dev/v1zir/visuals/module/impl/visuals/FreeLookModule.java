package dev.v1zir.visuals.module.impl.visuals;

import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.ModuleManager;
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
 * player and the glide back still lands correctly while the player turns. Started from first
 * person, the camera also pulls out of the head and back into it, unless Camera Tweaks already
 * glides between perspectives.
 */
public final class FreeLookModule extends Module {
	/** Degrees per mouse unit, the same factor vanilla uses to turn the player. */
	private static final float MOUSE_TO_DEGREES = 0.15f;
	/** Share of the camera distance at which the camera is just outside the head. */
	private static final float HEAD_CLEARANCE = 0.125f;
	/** The glide back counts as done this close to home; the rest is too small to see. */
	private static final float HOME_DEGREES = 0.4f;

	public final KeySetting key = add(new KeySetting("Key", GLFW.GLFW_KEY_LEFT_ALT));
	public final NumberSetting sensitivity = add(new NumberSetting("Sensitivity", 1.0, 0.2, 3.0, 0.05).suffix("x"));

	private final Anim returnYaw = new Anim(0f, 9f);
	private final Anim returnPitch = new Anim(0f, 9f);
	private final Anim reach = new Anim(1f, 9f);
	private Perspective restore = Perspective.FIRST_PERSON;
	private boolean holding;
	private boolean active;
	private boolean ownGlide;
	private float yawOffset;
	private float pitchOffset;
	private float distanceScale = 1f;

	public FreeLookModule() {
		super("Free Look", "Hold a key to look around yourself", Category.VISUALS);
		setSubCategory("Camera");
	}

	/** Called once per frame before the camera is placed. */
	public void frame() {
		if (mc.player == null) return;
		// Free look drives the camera from behind. Another perspective means the user switched it, or
		// that finish() restored it last frame (the camera only sees a new perspective a frame later).
		if (active && mc.options.getPerspective() != Perspective.THIRD_PERSON_BACK) reset();
		boolean held = key.held();
		if (held && !holding) start();
		else if (!held && holding && active) release();
		holding = held;
		if (!active) return;
		float r = reach.target(holding || !ownGlide ? 1f : 0f).get();
		distanceScale = Math.max(r, HEAD_CLEARANCE);
		if (holding) return;
		yawOffset = returnYaw.target(homeYaw()).get();
		pitchOffset = returnPitch.target(homePitch()).get();
		if (Math.abs(yawOffset - homeYaw()) < HOME_DEGREES && Math.abs(pitchOffset - homePitch()) < HOME_DEGREES
			&& (!ownGlide || r <= HEAD_CLEARANCE)) {
			finish();
		}
	}

	@Override
	protected void onDisable() {
		if (active) {
			restorePerspective();
			reset();
		}
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

	/** Third-person camera distance: pulled in towards the head while gliding to or from first person. */
	public float distance(float distance) {
		return active && ownGlide ? distance * distanceScale : distance;
	}

	private void start() {
		if (active) return; // pressed again while gliding back: keep orbiting from where the camera is
		restore = mc.options.getPerspective();
		yawOffset = homeYaw();
		pitchOffset = homePitch();
		CameraTweaksModule tweaks = ModuleManager.get(CameraTweaksModule.class);
		ownGlide = restore.isFirstPerson() && (tweaks == null || !tweaks.glidesPerspective());
		if (ownGlide) reach.snap(HEAD_CLEARANCE);
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

	/**
	 * The camera is home. Restoring another perspective is picked up by frame() next frame, so this
	 * frame still renders free look at home, which already looks like the restored view.
	 */
	private void finish() {
		if (!restorePerspective()) reset();
	}

	private boolean restorePerspective() {
		if (restore == Perspective.THIRD_PERSON_BACK || mc.options.getPerspective() != Perspective.THIRD_PERSON_BACK) return false;
		mc.options.setPerspective(restore);
		return true;
	}

	private void reset() {
		active = false;
		yawOffset = 0f;
		pitchOffset = 0f;
		distanceScale = 1f;
	}

	/** Offset at which the camera from behind matches the perspective free look started in. */
	private float homeYaw() {
		return restore == Perspective.THIRD_PERSON_FRONT ? 180f : 0f;
	}

	private float homePitch() {
		return restore == Perspective.THIRD_PERSON_FRONT ? -2f * mc.player.getPitch() : 0f;
	}
}
