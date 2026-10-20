package dev.v1zir.visuals.module.impl.visuals;

import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.setting.ModeSetting;
import dev.v1zir.visuals.module.setting.NumberSetting;
import dev.v1zir.visuals.util.Anim;
import net.minecraft.client.util.Window;
import org.joml.Matrix4f;

/**
 * Stretches the world like a stretched resolution (4:3 on a wide screen) by scaling the world
 * projection horizontally. The HUD, menus and the first-person hand keep their shape because
 * they use their own projections.
 */
public final class AspectRatioModule extends Module {
	public final ModeSetting ratio = add(new ModeSetting("Ratio", "4:3", "4:3", "5:4", "16:10", "1:1", "Custom"));
	public final NumberSetting custom = add(new NumberSetting("Custom ratio", 1.33, 0.8, 3.0, 0.01).visibleIf(() -> ratio.is("Custom")));

	// Horizontal stretch factor; it springs in and out when the module or the ratio changes.
	private final Anim stretch = new Anim(1f, 8f);

	public AspectRatioModule() {
		super("Aspect Ratio", "Stretched resolution look", Category.VISUALS);
	}

	/** True while the projection needs changing, including the glide back after disabling. */
	public boolean active() {
		return isEnabled() || !stretch.settled();
	}

	public Matrix4f apply(Matrix4f projection) {
		Window window = mc.getWindow();
		float screen = (float) window.getFramebufferWidth() / Math.max(1, window.getFramebufferHeight());
		float k = stretch.target(isEnabled() ? screen / target() : 1f).get();
		// Scaling clip-space x keeps the vertical FOV and makes everything wider on screen.
		return projection.scaleLocal(k, 1f, 1f);
	}

	private float target() {
		return switch (ratio.get()) {
			case "4:3" -> 4f / 3f;
			case "5:4" -> 1.25f;
			case "16:10" -> 1.6f;
			case "1:1" -> 1f;
			default -> custom.f();
		};
	}

	@Override
	public String suffix() {
		return ratio.get();
	}
}
