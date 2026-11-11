package dev.v1zir.visuals.module.impl.visuals;

import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.setting.ColorSetting;
import dev.v1zir.visuals.module.setting.ModeSetting;
import dev.v1zir.visuals.module.setting.NumberSetting;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;

/**
 * Shafts of light from the sun and the moon (render/PostFx): the glow around the light, cut out by whatever stands
 * in the sky before it (trees, hills, a doorway), streams through the gaps. They are strongest while
 * the light stands low, fade as it leaves the view, and dim in the rain. One full screen pass with a few depth
 * reads per pixel.
 */
public final class GodRaysModule extends Module {
	public final NumberSetting intensity = add(new NumberSetting("Intensity", 0.8, 0.1, 2.5, 0.05));
	/** How far towards the light every pixel looks: longer shafts reach further across the screen. */
	public final NumberSetting reach = add(new NumberSetting("Reach", 0.8, 0.3, 1.0, 0.05));
	public final ModeSetting quality = add(new ModeSetting("Quality", "Medium", "Low", "Medium", "High"));
	public final ModeSetting colors = add(new ModeSetting("Color", "Sunlight", "Sunlight", "Theme", "Custom"));
	public final ColorSetting color = add(new ColorSetting("Light color", 0xFFFFD9A0, false).visibleIf(() -> colors.is("Custom")));

	public GodRaysModule() {
		super("God Rays", "Shafts of light from the sun and the moon", Category.SHADERS);
		setSubCategory("World");
	}

	/** Depth reads along each shaft. */
	public int samples() {
		return switch (quality.index()) {
			case 0 -> 14;
			case 2 -> 36;
			default -> 24;
		};
	}

	/**
	 * The colour of the shafts: sunlight that reddens towards the horizon (height is the sine of the light's
	 * elevation) and cool moonlight, or the theme's or a picked colour.
	 */
	public int color(boolean day, float height) {
		if (colors.is("Theme")) return Theme.accent();
		if (colors.is("Custom")) return color.argb();
		if (!day) return 0xFF9EB8FF;
		return ColorUtil.lerp(0xFFFF9050, 0xFFFFF2DC, Math.max(0f, Math.min(1f, height / 0.6f)));
	}
}
