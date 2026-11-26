package dev.v1zir.visuals.module.impl.client;

import dev.v1zir.visuals.controller.Controller;
import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.module.setting.NumberSetting;

/**
 * Play with a PlayStation controller (DualShock 4 or DualSense) or another gamepad, in the world and in the menus
 * (Controller has the buttons). On by default: it does nothing until a controller is connected, and stands down when a
 * controller mod is installed.
 */
public final class ControllerModule extends Module {
	public final NumberSetting lookSpeed = add(new NumberSetting("Look speed", 1.0, 0.2, 3.0, 0.05).suffix("x"));
	public final BoolSetting invertY = add(new BoolSetting("Invert look", false));
	public final NumberSetting cursorSpeed = add(new NumberSetting("Pointer speed", 1.0, 0.3, 3.0, 0.05).suffix("x"));
	public final NumberSetting deadzone = add(new NumberSetting("Deadzone", 0.15, 0.05, 0.4, 0.01));

	public ControllerModule() {
		super("Controller", "Play with a PS4 or PS5 controller, in the world and in the menus", Category.SETTINGS);
		setEnabled(true, false);
	}

	@Override
	public String suffix() {
		String name = Controller.name();
		return name.isEmpty() ? null : name;
	}
}
