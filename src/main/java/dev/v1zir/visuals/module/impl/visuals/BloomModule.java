package dev.v1zir.visuals.module.impl.visuals;

import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.setting.NumberSetting;
import dev.v1zir.visuals.render.Bloom;

/**
 * A soft glow around bright things: the effects, lit windows, the sun, glowing outlines (render/Bloom).
 * The world and the hand bloom; the GUI never does.
 */
public final class BloomModule extends Module {
	public final NumberSetting intensity = add(new NumberSetting("Intensity", 0.6, 0.1, 2.0, 0.05));
	public final NumberSetting threshold = add(new NumberSetting("Threshold", 0.75, 0.3, 1.0, 0.01));
	public final NumberSetting radius = add(new NumberSetting("Radius", 1.0, 0.5, 2.0, 0.05).suffix("x"));

	public BloomModule() {
		super("Bloom", "Soft glow around bright light", Category.SHADERS);
		setSubCategory("World");
	}

	/** End of renderWorld, before colour grading (GameRendererMixin). */
	public static void renderIfEnabled() {
		BloomModule module = ModuleManager.get(BloomModule.class);
		if (module != null && module.isEnabled() && mc.world != null) {
			Bloom.render(module.threshold.f(), 0.12f, module.intensity.f(), 1f + module.radius.f());
		}
	}
}
