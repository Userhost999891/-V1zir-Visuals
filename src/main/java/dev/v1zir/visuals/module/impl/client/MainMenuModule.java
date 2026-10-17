package dev.v1zir.visuals.module.impl.client;

import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.module.setting.ModeSetting;
import dev.v1zir.visuals.module.setting.NumberSetting;
import dev.v1zir.visuals.render.sky.SkyPreset;

/** Options of the custom title screen. */
public final class MainMenuModule extends Module {
	public final ModeSetting background = add(new ModeSetting("Background", SkyPreset.MAGMA.title(), SkyPreset.titles()));
	public final NumberSetting speed = add(new NumberSetting("Animation speed", 1.0, 0.1, 3.0, 0.05).suffix("x"));
	public final BoolSetting parallax = add(new BoolSetting("Mouse parallax", true));
	public final BoolSetting particles = add(new BoolSetting("Particles", true));
	public final NumberSetting particleCount = add(new NumberSetting("Particle count", 70, 10, 200, 1).visibleIf(particles::on));

	public MainMenuModule() {
		super("Main Menu", "Custom animated title screen", Category.CLIENT);
		setEnabled(true, false);
	}

	@Override
	public boolean showInList() {
		return false;
	}

	public SkyPreset preset() {
		return SkyPreset.byTitle(background.get());
	}
}
