package dev.v1zir.visuals.module.impl.client;

import dev.v1zir.visuals.gui.menu.LockScreen;
import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.module.setting.ModeSetting;
import dev.v1zir.visuals.module.setting.NumberSetting;
import dev.v1zir.visuals.render.sky.SkyPreset;

/** Options of the custom title screen. */
public final class MainMenuModule extends Module {
	/** Set by the client game test so that it can finish on the vanilla title screen. */
	public static boolean suspended;

	public final ModeSetting background = add(new ModeSetting("Background", SkyPreset.MAGMA.title(), SkyPreset.titles()));
	public final NumberSetting speed = add(new NumberSetting("Animation speed", 1.0, 0.1, 3.0, 0.05).suffix("x"));
	/** Fraction of the screen resolution the background sky is rendered at before it is upsampled. */
	public final NumberSetting renderScale = add(new NumberSetting("Render scale", 0.5, 0.25, 1.0, 0.05).suffix("x"));
	public final BoolSetting parallax = add(new BoolSetting("Mouse parallax", true));
	public final BoolSetting particles = add(new BoolSetting("Particles", true));
	public final NumberSetting particleCount = add(new NumberSetting("Particle count", 70, 10, 200, 1).visibleIf(particles::on));
	/** The title screen opens on a phone-style lock screen with a big clock that is swiped up to open. */
	public final BoolSetting lockScreen = add(new BoolSetting("Lock screen", true));
	public final BoolSetting lockAfterWorld = add(new BoolSetting("Lock after leaving a world", false).visibleIf(lockScreen::on));
	public final ModeSetting timeFormat = add(new ModeSetting("Time format", "24-hour", "24-hour", "12-hour"));
	public final BoolSetting seconds = add(new BoolSetting("Show seconds", false));
	public final BoolSetting greeting = add(new BoolSetting("Greeting", true));

	public MainMenuModule() {
		super("Main Menu", "Custom animated title screen", Category.SETTINGS);
		setEnabled(true, false);
	}

	@Override
	public void onTick() {
		// Playing a world locks the title screen again for when the player comes back to it.
		if (mc.world != null && lockScreen.on() && lockAfterWorld.on()) LockScreen.lock();
	}

	@Override
	public boolean canToggle() {
		// A left click opens the settings instead, like Theme and Click GUI.
		return false;
	}

	@Override
	public boolean showInList() {
		return false;
	}

	/** Whether the custom title screen replaces the vanilla one right now. */
	public static boolean active() {
		MainMenuModule module = ModuleManager.get(MainMenuModule.class);
		return module != null && module.isEnabled() && !suspended;
	}

	public SkyPreset preset() {
		return SkyPreset.byTitle(background.get());
	}
}
