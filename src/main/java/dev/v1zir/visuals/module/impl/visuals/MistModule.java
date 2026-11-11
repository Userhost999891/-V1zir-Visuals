package dev.v1zir.visuals.module.impl.visuals;

import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.module.setting.ColorSetting;
import dev.v1zir.visuals.module.setting.ModeSetting;
import dev.v1zir.visuals.module.setting.NumberSetting;
import dev.v1zir.visuals.render.sky.SkyRenderer;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.World;

/**
 * Low mist over the land (render/PostFx): it lies thickest around your feet and thins with height, so valleys fill
 * with it and hilltops stand clear, drifts slowly, and takes the colour of the sky: pale by day, warm at dawn and
 * dusk, deep blue at night, or the Shader Sky's own fog colour. It gathers in the rain. One full screen pass.
 */
public final class MistModule extends Module {
	public final NumberSetting density = add(new NumberSetting("Density", 1.0, 0.2, 4.0, 0.05).suffix("x"));
	/** The height over which the mist thins to about a third. */
	public final NumberSetting height = add(new NumberSetting("Height", 8.0, 2.0, 32.0, 0.5).suffix(" m"));
	public final BoolSetting drift = add(new BoolSetting("Drift", true));
	public final ModeSetting colors = add(new ModeSetting("Color", "Sky", "Sky", "Theme", "Custom"));
	public final ColorSetting color = add(new ColorSetting("Mist color", 0xFFBFD4EA, false).visibleIf(() -> colors.is("Custom")));

	public MistModule() {
		super("Mist", "Low drifting fog in the colours of the sky", Category.SHADERS);
		setSubCategory("World");
	}

	/** The mist's colour now; sunHeight is the sine of the sun's elevation. */
	public int color(ClientWorld world, float sunHeight) {
		if (colors.is("Theme")) return ColorUtil.lerp(Theme.accent(), 0xFFFFFFFF, 0.2f);
		if (colors.is("Custom")) return color.argb();
		if (world.getRegistryKey() == World.NETHER) return 0xFF4A1612;
		if (world.getRegistryKey() == World.END) return 0xFF1C1430;
		// The Shader Sky's horizon colour, where it sets one.
		int sky = SkyRenderer.fogOverride();
		if (sky != 0) return ColorUtil.lerp(0xFF000000 | sky, 0xFFFFFFFF, 0.1f);
		float day = smooth(sunHeight, -0.12f, 0.25f);
		float dusk = 1f - smooth(Math.abs(sunHeight), 0.02f, 0.4f);
		return ColorUtil.lerp(ColorUtil.lerp(0xFF1B2440, 0xFFC9DBEE, day), 0xFFF2B48A, dusk * 0.75f);
	}

	private static float smooth(float x, float from, float to) {
		float t = Math.max(0f, Math.min(1f, (x - from) / (to - from)));
		return t * t * (3f - 2f * t);
	}
}
