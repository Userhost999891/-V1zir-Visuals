package dev.v1zir.visuals.module.impl.visuals;

import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.module.setting.NumberSetting;
import dev.v1zir.visuals.render.ColorGrade;

/**
 * Cinematic colour grading of the world and the hand, never the GUI: saturation, vibrance,
 * contrast, brightness, white balance (temperature and tint) and an optional vignette, all in one
 * full screen pass with one texture read per pixel (render/ColorGrade).
 */
public final class ColorGradingModule extends Module {
	public final NumberSetting saturation = add(new NumberSetting("Saturation", 1.1, 0.0, 2.0, 0.05));
	public final NumberSetting vibrance = add(new NumberSetting("Vibrance", 0.25, -1.0, 1.0, 0.05));
	public final NumberSetting contrast = add(new NumberSetting("Contrast", 1.06, 0.5, 1.5, 0.01));
	public final NumberSetting brightness = add(new NumberSetting("Brightness", 1.0, 0.5, 1.5, 0.01));
	public final NumberSetting temperature = add(new NumberSetting("Temperature", 0.1, -1.0, 1.0, 0.05));
	public final NumberSetting tint = add(new NumberSetting("Tint", 0.0, -1.0, 1.0, 0.05));
	public final BoolSetting vignette = add(new BoolSetting("Vignette", true));
	public final NumberSetting vignetteStrength = add(new NumberSetting("Vignette strength", 0.35, 0.05, 1.0, 0.05).visibleIf(vignette::on));

	public ColorGradingModule() {
		super("Color Grading", "Cinematic colour grading of the world", Category.VISUALS);
		setSubCategory("Render");
	}

	/** End of renderWorld (GameRendererMixin). */
	public static void renderIfEnabled() {
		ColorGradingModule module = ModuleManager.get(ColorGradingModule.class);
		if (module != null && module.isEnabled() && mc.world != null) module.render();
	}

	private void render() {
		float warm = temperature.f();
		float magenta = tint.f();
		float vig = vignette.on() ? vignetteStrength.f() : 0f;
		if (warm == 0f && magenta == 0f && vig == 0f && saturation.f() == 1f && vibrance.f() == 0f && contrast.f() == 1f && brightness.f() == 1f) return;
		// White balance, then scaled back to the same luminance so only the hue of the light changes.
		float r = 1f + 0.12f * warm;
		float g = 1f - 0.1f * magenta;
		float b = 1f - 0.12f * warm;
		float k = brightness.f() / (0.2126f * r + 0.7152f * g + 0.0722f * b);
		ColorGrade.render(r * k, g * k, b * k, contrast.f(), saturation.f(), vibrance.f(), vig);
	}
}
