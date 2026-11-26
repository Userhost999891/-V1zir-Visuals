package dev.v1zir.visuals.module.impl.visuals;

import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.module.setting.NumberSetting;

/**
 * Wet World: the world as it is right after a downpour, whatever the weather. Surfaces under the open sky are soaked
 * (darker, glossy, with faint reflections) and puddles lie on open flat ground, each a dark mirror of the sky; with
 * Drizzle a light rain keeps ringing on them. It is Rain Effects' wet look (render/Reflections) held on, so the two
 * share the puddles: in real rain the wetter of them wins.
 */
public final class WetWorldModule extends Module {
	public final NumberSetting wetness = add(new NumberSetting("Wetness", 1.0, 0.2, 1.5, 0.05));
	/** Raindrop rings on the puddles, as in a light rain. */
	public final BoolSetting drizzle = add(new BoolSetting("Drizzle", false));

	public WetWorldModule() {
		super("Wet World", "The world soaked and glossy with puddles, rain or not", Category.SHADERS);
		setSubCategory("World");
	}

	/** How wet the world is held (Reflections), 0 while this is off. */
	public static float wetness() {
		WetWorldModule module = ModuleManager.get(WetWorldModule.class);
		return module != null && module.isEnabled() ? module.wetness.f() : 0f;
	}

	/** The rain the raindrop rings on the puddles take (Reflections), 0 while this is off or without Drizzle. */
	public static float drops() {
		WetWorldModule module = ModuleManager.get(WetWorldModule.class);
		return module != null && module.isEnabled() && module.drizzle.on() ? 0.3f : 0f;
	}
}
