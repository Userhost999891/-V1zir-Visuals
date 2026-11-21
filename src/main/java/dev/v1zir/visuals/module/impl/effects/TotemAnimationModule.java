package dev.v1zir.visuals.module.impl.effects;

import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.module.setting.NumberSetting;
import net.minecraft.client.util.Window;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Quaternionf;

/**
 * Changes vanilla's totem of undying overlay: hides it or sets its size, speed and place on the
 * screen. While on, the animation runs on this module's clock instead of vanilla's tick timer
 * (InGameOverlayRendererMixin).
 */
public final class TotemAnimationModule extends Module {
	/** Vanilla shows the totem for 40 ticks. */
	private static final float VANILLA_SECONDS = 2f;
	/** The overlay projection always uses a 70 degree vertical field of view. */
	private static final float TAN_HALF_FOV = (float) Math.tan(Math.toRadians(35));

	public final BoolSetting hide = add(new BoolSetting("Hide", false));
	public final NumberSetting scale = add(new NumberSetting("Scale", 0.8, 0.3, 2.0, 0.05).suffix("x").visibleIf(() -> !hide.on()));
	public final NumberSetting speed = add(new NumberSetting("Speed", 1.25, 0.5, 3.0, 0.05).suffix("x").visibleIf(() -> !hide.on()));
	public final NumberSetting offsetX = add(new NumberSetting("Position X", 0, -1, 1, 0.01).visibleIf(() -> !hide.on()));
	public final NumberSetting offsetY = add(new NumberSetting("Position Y", 0, -1, 1, 0.01).visibleIf(() -> !hide.on()));

	private final Quaternionf rotation = new Quaternionf();
	private long start;

	public TotemAnimationModule() {
		super("Totem Animation", "Size, speed and place of the totem pop", Category.COMBAT);
		setSubCategory("Target");
	}

	/** The module while it is on, else null. */
	public static TotemAnimationModule active() {
		TotemAnimationModule module = ModuleManager.get(TotemAnimationModule.class);
		return module != null && module.isEnabled() ? module : null;
	}

	/** A totem was used: the animation starts now. */
	public void start() {
		start = System.nanoTime();
	}

	/** 0 to 1 over the animation at the chosen speed. */
	public float progress() {
		return (System.nanoTime() - start) / 1.0e9f * speed.f() / VANILLA_SECONDS;
	}

	/** Moves and scales the totem before vanilla positions and spins it. */
	public void transform(MatrixStack matrices) {
		// Turning about the camera keeps the screen offset the same while the totem flies in and out.
		Window window = mc.getWindow();
		float aspect = window.getFramebufferWidth() / (float) Math.max(1, window.getFramebufferHeight());
		float yaw = (float) Math.atan(offsetX.f() * TAN_HALF_FOV * aspect);
		float pitch = (float) Math.atan(offsetY.f() * TAN_HALF_FOV);
		matrices.multiply(rotation.rotationY(-yaw).rotateX(pitch));
		// Only across the screen: scaling depth as well would bring it closer and keep its size on screen.
		float s = scale.f();
		matrices.scale(s, s, 1f);
	}
}
