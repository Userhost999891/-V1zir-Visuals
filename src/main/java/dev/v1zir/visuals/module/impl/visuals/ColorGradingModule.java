package dev.v1zir.visuals.module.impl.visuals;

import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.module.setting.ModeSetting;
import dev.v1zir.visuals.module.setting.NumberSetting;
import dev.v1zir.visuals.render.ColorGrade;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;

/**
 * Cinematic colour grading of the world and the hand, never the GUI: saturation, vibrance,
 * contrast, brightness, white balance (temperature and tint), an optional vignette and a look, all
 * in one full screen pass with one texture read per pixel (render/ColorGrade). A look recolours the
 * picture by brightness, keeping its light: Amethyst in violet with pink highlights, Frost in icy
 * blue, Sunset in plum, coral and gold, Theme in the theme's own colours.
 */
public final class ColorGradingModule extends Module {
	private static final String NATURAL = "Natural";
	/** Shadow, midtone and highlight colour of each look after Natural. */
	private static final int[][] LOOKS = {
		{0x24083F, 0x9B4DFF, 0xFFD9FB},
		{0x0A1C3A, 0x3FA9FF, 0xE3FAFF},
		{0x2E0B2A, 0xFF6A4D, 0xFFE6B3},
	};

	public final ModeSetting look = add(new ModeSetting("Look", NATURAL, NATURAL, "Amethyst", "Frost", "Sunset", "Theme"));
	public final NumberSetting lookStrength = add(new NumberSetting("Look strength", 0.45, 0.05, 1.0, 0.05).visibleIf(() -> !look.is(NATURAL)));
	public final NumberSetting saturation = add(new NumberSetting("Saturation", 1.1, 0.0, 2.0, 0.05));
	public final NumberSetting vibrance = add(new NumberSetting("Vibrance", 0.25, -1.0, 1.0, 0.05));
	public final NumberSetting contrast = add(new NumberSetting("Contrast", 1.06, 0.5, 1.5, 0.01));
	public final NumberSetting brightness = add(new NumberSetting("Brightness", 1.0, 0.5, 1.5, 0.01));
	public final NumberSetting temperature = add(new NumberSetting("Temperature", 0.1, -1.0, 1.0, 0.05));
	public final NumberSetting tint = add(new NumberSetting("Tint", 0.0, -1.0, 1.0, 0.05));
	public final BoolSetting vignette = add(new BoolSetting("Vignette", true));
	public final NumberSetting vignetteStrength = add(new NumberSetting("Vignette strength", 0.35, 0.05, 1.0, 0.05).visibleIf(vignette::on));

	public ColorGradingModule() {
		super("Color Grading", "Cinematic colour grading of the world", Category.SHADERS);
		setSubCategory("World");
	}

	@Override
	public String suffix() {
		return look.is(NATURAL) ? null : look.get();
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
		int index = look.index();
		float amount = index == 0 ? 0f : lookStrength.f();
		if (amount == 0f && warm == 0f && magenta == 0f && vig == 0f && saturation.f() == 1f && vibrance.f() == 0f && contrast.f() == 1f
			&& brightness.f() == 1f) return;
		int shadow = 0;
		int mid = 0x808080;
		int light = 0xFFFFFF;
		if (index > 0 && index <= LOOKS.length) {
			shadow = LOOKS[index - 1][0];
			mid = LOOKS[index - 1][1];
			light = LOOKS[index - 1][2];
		} else if (index > LOOKS.length) {
			// The theme's colours: its first accent in the midtones, deepened for the shadows, the second paled for the highlights.
			shadow = ColorUtil.darken(Theme.accent(), 0.75f) & 0xFFFFFF;
			mid = Theme.accent() & 0xFFFFFF;
			light = ColorUtil.lerp(Theme.accent2(), 0xFFFFFFFF, 0.7f) & 0xFFFFFF;
		}
		// White balance, then scaled back to the same luminance so only the hue of the light changes.
		float r = 1f + 0.12f * warm;
		float g = 1f - 0.1f * magenta;
		float b = 1f - 0.12f * warm;
		float k = brightness.f() / (0.2126f * r + 0.7152f * g + 0.0722f * b);
		ColorGrade.render(r * k, g * k, b * k, contrast.f(), saturation.f(), vibrance.f(), vig, shadow, mid, light, amount);
	}
}
