package dev.v1zir.visuals.gui.menu;

import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.gui.screen.Screen;

/** The main menu of the style theme in use: the client's own, or the console home screen of the PS5 style. */
public final class MainMenus {
	private MainMenus() {
	}

	public static Screen create() {
		return Theme.ps5() ? new ConsoleMenu() : new V1zirMainMenu();
	}
}
