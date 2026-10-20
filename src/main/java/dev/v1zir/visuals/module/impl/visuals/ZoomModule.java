package dev.v1zir.visuals.module.impl.visuals;

import dev.v1zir.visuals.gui.hud.CameraIndicator;
import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.module.setting.KeySetting;
import dev.v1zir.visuals.module.setting.NumberSetting;
import dev.v1zir.visuals.render.font.Icons;
import dev.v1zir.visuals.util.Anim;
import org.lwjgl.glfw.GLFW;

/** Hold a key to zoom in with a spring; the scroll wheel changes the zoom while it is held. */
public final class ZoomModule extends Module {
	private static final float MIN_FACTOR = 1.25f;
	private static final float MAX_FACTOR = 50f;
	private static final float HAND_DROP = 0.9f;

	public final KeySetting key = add(new KeySetting("Key", GLFW.GLFW_KEY_C));
	public final NumberSetting factor = add(new NumberSetting("Zoom", 4.0, 1.5, 15.0, 0.5).suffix("x"));
	public final BoolSetting scroll = add(new BoolSetting("Scroll to adjust", true));
	public final BoolSetting slowMouse = add(new BoolSetting("Slower mouse", true));
	public final BoolSetting cinematic = add(new BoolSetting("Cinematic camera", false));
	public final BoolSetting hideHand = add(new BoolSetting("Hide hand", true));

	// The spring runs on ln(zoom), so doubling the zoom always takes the same time.
	private final Anim level = new Anim(0f, 10f);
	private boolean zooming;
	private boolean zoomed;
	private boolean savedSmoothCamera;
	private float holdFactor = 4f;
	private float divisor = 1f;
	private float progress;

	public ZoomModule() {
		super("Zoom", "Hold a key to zoom smoothly", Category.VISUALS);
	}

	/** Called once per frame before the camera is placed. */
	public void frame() {
		boolean held = key.held() && mc.player != null;
		if (held != zooming) {
			zooming = held;
			if (held) {
				holdFactor = factor.f();
				savedSmoothCamera = mc.options.smoothCameraEnabled;
				if (cinematic.on()) mc.options.smoothCameraEnabled = true;
			} else {
				mc.options.smoothCameraEnabled = savedSmoothCamera;
			}
		}
		float now = level.target(zooming ? (float) Math.log(holdFactor) : 0f).get();
		divisor = (float) Math.exp(now);
		progress = Anim.clamp01(now / (float) Math.log(holdFactor));
		if (now > 0f) {
			zoomed = true;
		} else if (zoomed) {
			// Back at normal FOV: rebuild the visible chunk list once, in case it was culled to the narrow view.
			zoomed = false;
			mc.worldRenderer.scheduleTerrainUpdate();
		}
	}

	@Override
	protected void onDisable() {
		if (zooming) mc.options.smoothCameraEnabled = savedSmoothCamera;
		if (zoomed && mc.worldRenderer != null) mc.worldRenderer.scheduleTerrainUpdate();
		zooming = false;
		zoomed = false;
		level.snap(0f);
		divisor = 1f;
		progress = 0f;
	}

	public float fov(float fov) {
		return fov / divisor;
	}

	/** Mouse turning slows down with the zoom so aiming feels the same at every zoom level. */
	public double mouseScale() {
		return slowMouse.on() ? 1.0 / divisor : 1.0;
	}

	/** How far the hands have slid down out of view, in view-space units. */
	public float handDrop() {
		return hideHand.on() ? progress * HAND_DROP : 0f;
	}

	public boolean handHidden() {
		return hideHand.on() && progress > 0.99f;
	}

	/** Scroll while zooming changes the zoom of this hold. Returns true when the wheel was used. */
	public boolean onScroll(double amount) {
		if (!zooming || !scroll.on()) return false;
		holdFactor = Math.max(MIN_FACTOR, Math.min(MAX_FACTOR, holdFactor * (float) Math.pow(1.15, amount)));
		float fraction = (float) (Math.log(holdFactor / MIN_FACTOR) / Math.log(MAX_FACTOR / MIN_FACTOR));
		CameraIndicator.show(Icons.SEARCH, Math.round(holdFactor * 10f) / 10f + "x", fraction);
		return true;
	}
}
