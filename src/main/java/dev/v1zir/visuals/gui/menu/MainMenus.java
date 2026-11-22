package dev.v1zir.visuals.gui.menu;

import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.gui.screen.Screen;

/** The main menu of the style theme in use: the client's own, or the console home screen of the PS5 style. */
public final class MainMenus {
	private MainMenus() {
	}

	/** The PS5 style's menu starts with the console's start once per start of the game (BootScreen), then the home. */
	public static Screen create() {
		if (!Theme.ps5()) return new V1zirMainMenu();
		return BootScreen.due() ? new BootScreen() : new ConsoleMenu();
	}
}
