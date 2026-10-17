package dev.v1zir.visuals.module.impl.client;

import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.module.setting.ColorSetting;
import dev.v1zir.visuals.module.setting.ModeSetting;
import dev.v1zir.visuals.module.setting.NumberSetting;
import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.util.Theme;

/** Colours, glass and font of the whole UI. Always enabled. */
public final class ThemeModule extends Module {
	public final ModeSetting preset = add(new ModeSetting("Preset", Theme.Preset.V1ZIR.title(), Theme.Preset.titles()));
	public final ColorSetting accent = add(new ColorSetting("Accent", 0xFF9B7BFF, false).visibleIf(this::custom));
	public final ColorSetting accent2 = add(new ColorSetting("Accent 2", 0xFF4FD1FF, false).visibleIf(this::custom));
	public final NumberSetting panelOpacity = add(new NumberSetting("Panel opacity", 0.72, 0.3, 1.0, 0.01));
	public final BoolSetting blur = add(new BoolSetting("Blur", true));
	public final NumberSetting blurStrength = add(new NumberSetting("Blur strength", 0.6, 0.0, 1.0, 0.05).visibleIf(blur::on));
	public final ModeSetting font = add(new ModeSetting("Font", "Inter", "Inter", "Montserrat", "Rubik", "Nunito", "Comfortaa"));
	public final BoolSetting textShadow = add(new BoolSetting("Text shadow", true));

	public ThemeModule() {
		super("Theme", "Colours, glass and font of the interface", Category.CLIENT);
		setEnabled(true, false);
	}

	private boolean custom() {
		return preset.is(Theme.Preset.CUSTOM.title());
	}

	@Override
	public boolean canToggle() {
		return false;
	}

	@Override
	public boolean showInList() {
		return false;
	}

	/** Applies settings that need side effects (the font family). Called every frame by the GUI. */
	public void apply() {
		Fonts.setFamily(Fonts.Family.byTitle(font.get()));
	}
}
