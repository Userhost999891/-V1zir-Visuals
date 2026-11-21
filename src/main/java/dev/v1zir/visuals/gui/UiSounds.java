package dev.v1zir.visuals.gui;

import dev.v1zir.visuals.V1zir;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.impl.client.ThemeModule;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import org.jetbrains.annotations.Nullable;

/**
 * The PS5 style theme's sounds, the client's own (assets/v1zir/sounds/ui), made for it as one family of soft glassy
 * tones in a small airy room: a short tick as the selection moves, a breath of air sliding between tabs, a tone blooming
 * upwards to confirm and settling downwards to go back, a bubble rising as a small menu opens and sinking as it closes,
 * a light chime when a choice of it is taken, a soft chime for a message and a slow swell when the game has loaded.
 * They play only with that style theme and Theme > UI sounds on.
 */
public final class UiSounds {
	public static final String MOVE = "move";
	public static final String TAB = "tab";
	public static final String CONFIRM = "confirm";
	public static final String BACK = "back";
	public static final String NOTIFY = "notify";
	public static final String BOOT = "boot";
	/** A small menu (prompt) opening, going back from it, and a choice of it taken. */
	public static final String POPUP = "popup";
	public static final String POPUP_BACK = "popup_back";
	public static final String POPUP_ENTER = "popup_enter";
	/** The pause menu (the style's control center) opening over the game, and closing back to it. */
	public static final String CONTROL_ON = "control_on";
	public static final String CONTROL_OFF = "control_off";
	/** An advancement made, as a console's trophy. */
	public static final String TROPHY = "trophy";

	private UiSounds() {
	}

	/** Whether the PS5 style's sounds play. */
	public static boolean on() {
		if (!Theme.ps5()) return false;
		ThemeModule theme = ModuleManager.get(ThemeModule.class);
		return theme == null || theme.uiSounds.on();
	}

	public static void play(String name) {
		play(name, 1f);
	}

	public static void play(String name, float pitch) {
		if (!on()) return;
		MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvent.of(V1zir.id("ui." + name)), pitch, volume(name)));
	}

	/** How loud each plays: the ones heard all the time (moving, tabs) quietest, the start fullest. */
	private static float volume(String name) {
		return switch (name) {
			case MOVE -> 0.4f;
			case TAB, POPUP_BACK -> 0.5f;
			case BACK, POPUP, CONTROL_ON, CONTROL_OFF -> 0.55f;
			case TROPHY -> 0.7f;
			case BOOT -> 0.8f;
			default -> 0.6f;
		};
	}

	/**
	 * A screen about to change: the pause menu opening over the game plays the control center's opening, and going
	 * back from it to the game its closing (not the menus opened from it and back).
	 */
	public static void screenChanging(@Nullable Screen from, @Nullable Screen to) {
		if (to instanceof GameMenuScreen && from == null) play(CONTROL_ON);
		else if (from instanceof GameMenuScreen && to == null) play(CONTROL_OFF);
	}
}
