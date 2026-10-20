package dev.v1zir.visuals.module.impl.visuals;

import dev.v1zir.visuals.gui.hud.CameraIndicator;
import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.module.setting.KeySetting;
import dev.v1zir.visuals.module.setting.NumberSetting;
import dev.v1zir.visuals.render.font.Icons;
import dev.v1zir.visuals.util.Anim;
import net.minecraft.client.option.Perspective;
import org.lwjgl.glfw.GLFW;

/**
 * Third-person camera: passes through walls, keeps an adjustable distance that the scroll wheel
 * changes while a key is held, drops the hurt shake and glides between perspectives.
 * <p>
 * The glide is one spring on the camera distance: it grows from zero when leaving first person,
 * and when going back the camera keeps rendering in third person until it reaches the head.
 */
public final class CameraTweaksModule extends Module {
	/** Distance vanilla asks for at scale 1; the setting is applied relative to it so entity scale still counts. */
	private static final float VANILLA_DISTANCE = 4f;
	private static final float IN_HEAD = 0.05f;

	public final BoolSetting noClip = add(new BoolSetting("No clip", true));
	public final NumberSetting distance = add(new NumberSetting("Distance", 4.0, 1.0, 20.0, 0.1).suffix("m"));
	public final KeySetting scrollKey = add(new KeySetting("Scroll key", GLFW.GLFW_KEY_LEFT_ALT));
	public final BoolSetting noHurtShake = add(new BoolSetting("No hurt shake", true));
	public final BoolSetting smooth = add(new BoolSetting("Smooth perspective", true));

	private final Anim current = new Anim(0f, 10f);
	private float value;
	private boolean front;
	private boolean gliding;

	public CameraTweaksModule() {
		super("Camera Tweaks", "Third person camera options", Category.VISUALS);
	}

	/** Called once per frame before the camera is placed. */
	public void frame() {
		Perspective perspective = mc.options.getPerspective();
		boolean third = !perspective.isFirstPerson();
		if (third) front = perspective.isFrontView();
		float target = third ? distance.f() : 0f;
		if (smooth.on()) current.target(target);
		else current.snap(target);
		value = current.get();
		gliding = !third && value > IN_HEAD;
	}

	@Override
	protected void onDisable() {
		current.snap(0f);
		gliding = false;
	}

	/** Keeps the third-person camera while it glides back into the head. */
	public boolean thirdPerson(boolean vanilla) {
		return vanilla || gliding;
	}

	public boolean frontView(boolean vanilla) {
		return gliding ? front : vanilla;
	}

	/** The first-person hand would float in front of the gliding camera. */
	public boolean hidesHand() {
		return gliding;
	}

	public float distance(float vanilla) {
		return vanilla * value / VANILLA_DISTANCE;
	}

	/** Scroll with the key held changes the distance. Returns true when the wheel was used. */
	public boolean onScroll(double amount) {
		if (mc.options.getPerspective().isFirstPerson() || !scrollKey.held()) return false;
		float d = distance.f();
		float step = Math.max(0.1f, Math.abs((float) amount) * d * 0.12f);
		distance.set((double) (d - Math.signum((float) amount) * step));
		CameraIndicator.show(Icons.ORBIT, distance.display(), (float) distance.fraction());
		return true;
	}
}
