package dev.v1zir.visuals.module.impl.visuals;

import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.module.setting.NumberSetting;

/**
 * The look of a film camera over the world, never the GUI (render/PostFx): colour fringes that grow towards the
 * edges of the picture, as a simple lens makes them, fine film grain, a vignette and letterbox bars. One full
 * screen pass after the colour grading.
 */
public final class CinematicModule extends Module {
	public final NumberSetting fringes = add(new NumberSetting("Color fringes", 0.4, 0.0, 1.5, 0.05));
	public final NumberSetting grain = add(new NumberSetting("Film grain", 0.35, 0.0, 1.0, 0.05));
	public final NumberSetting vignette = add(new NumberSetting("Vignette", 0.3, 0.0, 1.0, 0.05));
	public final BoolSetting bars = add(new BoolSetting("Letterbox bars", false));
	/** Share of the picture's height each bar covers. */
	public final NumberSetting barSize = add(new NumberSetting("Bar size", 0.1, 0.03, 0.2, 0.01).visibleIf(bars::on));

	public CinematicModule() {
		super("Cinematic", "Film grain, lens fringes, a vignette and letterbox bars", Category.SHADERS);
		setSubCategory("World");
	}
}
