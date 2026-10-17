package dev.v1zir.visuals.module.impl.hud;

import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;

/** Shows a toast whenever a module is toggled. */
public final class NotificationsModule extends Module {
	public NotificationsModule() {
		super("Notifications", "Toasts when modules are toggled", Category.HUD);
	}

	@Override
	public boolean showInList() {
		return false;
	}
}
