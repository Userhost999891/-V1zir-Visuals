package dev.v1zir.visuals.module.impl.visuals;

import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.module.setting.ModeSetting;
import dev.v1zir.visuals.module.setting.NumberSetting;
import dev.v1zir.visuals.util.Anim;
import net.minecraft.client.util.Window;
import org.joml.Matrix4f;

/**
 * Stretches the world like a stretched resolution (4:3 on a wide screen) by scaling the world
 * projection horizontally. The HUD and menus keep their shape because they use their own
 * projections; the first-person hand follows the world unless Stretch hand is off.
 */
public final class AspectRatioModule extends Module {
	public final ModeSetting ratio = add(new ModeSetting("Ratio", "4:3", "4:3", "5:4", "16:10", "1:1", "Custom"));
	public final NumberSetting custom = add(new NumberSetting("Custom ratio", 1.33, 0.8, 3.0, 0.01).visibleIf(() -> ratio.is("Custom")));
	public final BoolSetting stretchHand = add(new BoolSetting("Stretch hand", true));

	// Horizontal stretch factor; it springs in and out when the module or the ratio changes.
	private final Anim stretch = new Anim(1f, 8f);
	// Culling gets its own copy: vanilla may keep the matrix it returns, so it is never scaled in place.
	private final Matrix4f culling = new Matrix4f();
	private float factor = 1f;

	public AspectRatioModule() {
		super("Aspect Ratio", "Stretched resolution look", Category.VISUALS);
		setSubCategory("Camera");
	}

	/** True while the projection needs changing, including the glide back after disabling. */
	public boolean active() {
		return isEnabled() || !stretch.settled();
	}

	/** The world projection, built first in the frame, so the spring advances here once per frame. */
	public Matrix4f stretchWorld(Matrix4f projection) {
		Window window = mc.getWindow();
		float screen = (float) window.getFramebufferWidth() / Math.max(1, window.getFramebufferHeight());
		factor = stretch.target(isEnabled() ? screen / target() : 1f).get();
		// Scaling clip-space x keeps the vertical FOV and makes everything wider on screen.
		return projection.scaleLocal(factor, 1f, 1f);
	}

	/** The culling frustum matches what is on screen, also when a wide ratio shows more at the sides. */
	public Matrix4f stretchCulling(Matrix4f projection) {
		return culling.set(projection).scaleLocal(factor, 1f, 1f);
	}

	public int handWidth(int width) {
		return stretchHand.on() ? Math.max(1, Math.round(width / factor)) : width;
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
