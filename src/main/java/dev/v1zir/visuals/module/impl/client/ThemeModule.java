package dev.v1zir.visuals.module.impl.client;

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

import java.io.IOException;
import java.nio.file.Files;

/** Colours, glass and font of the whole UI. Always enabled. */
public final class ThemeModule extends Module {
	/** The look of the menus and screens: the client's own, or that of a console's home screen (PS5). The game itself stays as it is. */
	public final ModeSetting styleTheme = add(new ModeSetting("Style theme", "Normal", "Normal", "PS5"));
	/** The PS5 style's sounds in its menus (UiSounds), the client's own. */
	public final BoolSetting uiSounds = add(new BoolSetting("UI sounds", true).visibleIf(() -> styleTheme.is("PS5")));
	/** Pictures of the worlds and servers you play, taken now and then while playing, behind their tiles in the PS5 style menu. */
	public final BoolSetting menuPictures = add(new BoolSetting("Menu pictures", true).visibleIf(() -> styleTheme.is("PS5")));
	/** Turned on, it opens the folder of the menu's pictures and turns itself off again. */
	public final BoolSetting openPictures = add(new BoolSetting("Open pictures folder", false).visibleIf(() -> styleTheme.is("PS5")));
	public final ModeSetting preset = add(new ModeSetting("Preset", Theme.Preset.V1ZIR.title(), Theme.Preset.titles()));
	public final ColorSetting accent = add(new ColorSetting("Accent", 0xFFF6A64B, false).visibleIf(this::custom));
	public final ColorSetting accent2 = add(new ColorSetting("Accent 2", 0xFFE8693F, false).visibleIf(this::custom));
	public final NumberSetting panelOpacity = add(new NumberSetting("Panel opacity", 0.85, 0.3, 1.0, 0.01));
	/** Panel style of the GUI and the HUD: Normal (solid black) or Glass (frosted glass panels and clear glass buttons). */
	public final ModeSetting style = add(new ModeSetting("Style", Theme.Style.GLASS.title(), Theme.Style.titles()));
	public final NumberSetting blurStrength = add(new NumberSetting("Blur strength", 0.75, 0.0, 1.0, 0.05).visibleIf(() -> !style.is(Theme.Style.NORMAL.title())));
	/** Set once Glass, which took the place of Blur, Matte glass and Glassmorphism, was turned on for this config; Normal can be chosen again after. */
	private final BoolSetting glassGiven = add(new BoolSetting("Glass given", false).visibleIf(() -> false));
	public final ModeSetting font = add(new ModeSetting("Font", "Inter", "Inter", "Montserrat", "Rubik", "Nunito", "Comfortaa"));
	public final BoolSetting textShadow = add(new BoolSetting("Text shadow", true));
	/** The game's loading screen (at start and when the resource packs reload) in the client's style instead of Mojang's. */
	public final BoolSetting loadingScreen = add(new BoolSetting("Loading screen", true));
	private String appliedFont;

	public ThemeModule() {
		super("Theme", "Colours, glass and font of the interface", Category.SETTINGS);
		setEnabled(true, false);
	}

	private boolean custom() {
		return preset.is(Theme.Preset.CUSTOM.title());
	}

	@Override
	public void onTick() {
		if (!glassGiven.on()) {
			glassGiven.set(true);
			style.set(Theme.Style.GLASS.title());
		}
		// The "button": done once, then off again.
		if (openPictures.on()) {
			openPictures.set(false);
			try {
				Files.createDirectories(MenuShots.folder());
			} catch (IOException ignored) {
				// The file manager says it is missing.
			}
			Util.getOperatingSystem().open(MenuShots.folder());
		}
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
