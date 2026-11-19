package dev.v1zir.visuals.module.impl.client;

import dev.v1zir.visuals.gui.UiSounds;
import dev.v1zir.visuals.gui.menu.MenuShots;
import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.module.setting.ColorSetting;
import dev.v1zir.visuals.module.setting.ModeSetting;
import dev.v1zir.visuals.module.setting.NumberSetting;
import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Colours, glass and font of the whole UI. Always enabled. */
public final class ThemeModule extends Module {
	/** The look of the menus and screens: the client's own, or that of a console's home screen (PS5). The game itself stays as it is. */
	public final ModeSetting styleTheme = add(new ModeSetting("Style theme", "Normal", "Normal", "PS5"));
	/** The PS5 style's own sounds in its menus (UiSounds). */
	public final BoolSetting uiSounds = add(new BoolSetting("UI sounds", true).visibleIf(() -> styleTheme.is("PS5")));
	/** Your own files from config/v1zir/ui-sounds in place of the PS5 style's sounds, each picked below (Auto: by its name). */
	public final BoolSetting ownSounds = add(new BoolSetting("Own sounds", true).visibleIf(() -> styleTheme.is("PS5") && uiSounds.on()));
	public final ModeSetting moveSound = add(soundChoice("Move sound"));
	public final ModeSetting tabSound = add(soundChoice("Tab sound"));
	public final ModeSetting confirmSound = add(soundChoice("Confirm sound"));
	public final ModeSetting backSound = add(soundChoice("Back sound"));
	public final ModeSetting messageSound = add(soundChoice("Message sound"));
	public final ModeSetting startSound = add(soundChoice("Start sound"));
	public final NumberSetting ownVolume = add(new NumberSetting("Own sounds volume", 0.7, 0.05, 1.0, 0.05).visibleIf(this::ownShown));
	/** Turned on, it opens the folder of your own sounds in the file manager and turns itself off again. */
	public final BoolSetting openSounds = add(new BoolSetting("Open sounds folder", false).visibleIf(() -> styleTheme.is("PS5") && uiSounds.on()));
	/** Pictures of the worlds and servers you play, taken now and then while playing, behind their tiles in the PS5 style menu. */
	public final BoolSetting menuPictures = add(new BoolSetting("Menu pictures", true).visibleIf(() -> styleTheme.is("PS5")));
	/** Turned on, it opens the folder of the menu's pictures and turns itself off again. */
	public final BoolSetting openPictures = add(new BoolSetting("Open pictures folder", false).visibleIf(() -> styleTheme.is("PS5")));
	public final ModeSetting preset = add(new ModeSetting("Preset", Theme.Preset.V1ZIR.title(), Theme.Preset.titles()));
	public final ColorSetting accent = add(new ColorSetting("Accent", 0xFFF6A64B, false).visibleIf(this::custom));
	public final ColorSetting accent2 = add(new ColorSetting("Accent 2", 0xFFE8693F, false).visibleIf(this::custom));
	public final NumberSetting panelOpacity = add(new NumberSetting("Panel opacity", 0.62, 0.3, 1.0, 0.01));
	/** Panel style: Normal (solid grey) or Blur (tinted blur). */
	public final ModeSetting style = add(new ModeSetting("Style", Theme.Style.BLUR.title(), Theme.Style.titles()));
	public final NumberSetting blurStrength = add(new NumberSetting("Blur strength", 0.75, 0.0, 1.0, 0.05).visibleIf(() -> !style.is(Theme.Style.NORMAL.title())));
	public final ModeSetting font = add(new ModeSetting("Font", "Inter", "Inter", "Montserrat", "Rubik", "Nunito", "Comfortaa"));
	public final BoolSetting textShadow = add(new BoolSetting("Text shadow", true));
	/** The game's loading screen (at start and when the resource packs reload) in the client's style instead of Mojang's. */
	public final BoolSetting loadingScreen = add(new BoolSetting("Loading screen", true));
	private String appliedFont;
	private int ticks;

	public ThemeModule() {
		super("Theme", "Colours, glass and font of the interface", Category.SETTINGS);
		setEnabled(true, false);
	}

	private boolean custom() {
		return preset.is(Theme.Preset.CUSTOM.title());
	}

	private boolean ownShown() {
		return styleTheme.is("PS5") && uiSounds.on() && ownSounds.on();
	}

	private ModeSetting soundChoice(String name) {
		UiSounds.refresh();
		return new ModeSetting(name, UiSounds.AUTO, UiSounds.choices().toArray(new String[0])).visibleIf(this::ownShown);
	}

	@Override
	public void onTick() {
		// The two folder "buttons": opened once, then off again.
		if (openSounds.on()) {
			openSounds.set(false);
			UiSounds.refresh();
			Util.getOperatingSystem().open(UiSounds.folder());
		}
		if (openPictures.on()) {
			openPictures.set(false);
			try {
				Files.createDirectories(MenuShots.folder());
			} catch (IOException ignored) {
				// The file manager says it is missing.
			}
			Util.getOperatingSystem().open(MenuShots.folder());
		}
		// Files added to the folder show up in the choices.
		if (++ticks % 40 != 0 || !styleTheme.is("PS5")) return;
		if (!UiSounds.refresh()) return;
		var choices = UiSounds.choices();
		for (ModeSetting setting : new ModeSetting[]{moveSound, tabSound, confirmSound, backSound, messageSound, startSound}) setting.setModes(choices);
	}

	/** The player's own file for one of UiSounds' sounds, null for the client's own. */
	@Nullable
	public Path ownSound(String sound) {
		if (!ownSounds.on()) return null;
		ModeSetting choice = switch (sound) {
			case UiSounds.MOVE -> moveSound;
			case UiSounds.TAB -> tabSound;
			case UiSounds.CONFIRM -> confirmSound;
			case UiSounds.BACK -> backSound;
			case UiSounds.NOTIFY -> messageSound;
			case UiSounds.BOOT -> startSound;
			default -> null;
		};
		if (choice == null || choice.is(UiSounds.BUILT_IN)) return null;
		if (choice.is(UiSounds.AUTO)) return UiSounds.guessed(sound);
		Path file = UiSounds.folder().resolve(choice.get());
		return Files.isRegularFile(file) ? file : null;
	}

	@Override
	public boolean canToggle() {
		return false;
	}

	@Override
	public boolean showInList() {
		return false;
	}

	/** Applies settings that need side effects (the font family). Called every frame by the GUI, so it only resolves a change. */
	public void apply() {
		String family = font.get();
		if (family == appliedFont) return;
		appliedFont = family;
		Fonts.setFamily(Fonts.Family.byTitle(family));
	}
}
