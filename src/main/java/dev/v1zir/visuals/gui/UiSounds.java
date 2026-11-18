package dev.v1zir.visuals.gui;

import dev.v1zir.visuals.V1zir;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.impl.client.ThemeModule;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;

/**
 * The PS5 style theme's sounds, made for the client (assets/v1zir/sounds/ui): a soft tick as the selection moves, an
 * airy brush between tabs, a warm bloom to confirm, a falling tone to go back, two quiet bells for a message and a swell
 * when the game has loaded. They play only with that style theme and Theme > UI sounds on.
 */
public final class UiSounds {
	public static final String MOVE = "move";
	public static final String TAB = "tab";
	public static final String CONFIRM = "confirm";
	public static final String BACK = "back";
	public static final String NOTIFY = "notify";
	public static final String BOOT = "boot";

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
		float volume = switch (name) {
			case MOVE -> 0.35f;
			case TAB, BACK -> 0.45f;
			case BOOT -> 0.8f;
			default -> 0.6f;
		};
		MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvent.of(V1zir.id("ui." + name)), pitch, volume));
	}
}
