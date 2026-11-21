package dev.v1zir.visuals.optimize;

import dev.v1zir.visuals.V1zir;
import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.api.config.option.OptionFlag;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.EnumOptionBuilder;
import net.minecraft.text.Text;

/**
 * GPU in Sodium's Video Settings, which take the place of the game's when Sodium is there: a page of V1zir Visuals with
 * the same choice as the game's settings get ({@link GpuOption}), through Sodium's config API (the
 * "sodium:config_api_user" entrypoint, which only Sodium asks for: without it this class is never loaded). Every
 * method used here is the same in Sodium 0.8.0 and 0.8.7, so any 0.8 release for 1.21.11 takes it.
 */
public final class SodiumGpuOption implements ConfigEntryPoint {
	@Override
	public void registerConfigLate(ConfigBuilder builder) {
		EnumOptionBuilder<GpuOption.Gpu> gpu = builder.createEnumOption(V1zir.id("gpu"), GpuOption.Gpu.class)
			.setName(Text.literal("GPU"))
			.setTooltip(choice -> Text.literal(GpuOption.describe(choice)))
			.setElementNameProvider(choice -> Text.literal(GpuOption.name(choice)))
			.setStorageHandler(() -> {
			})
			.setBinding(GpuOption::pick, GpuOption::current)
			.setDefaultValue(GpuOption.Gpu.AUTO)
			.setFlags(OptionFlag.REQUIRES_GAME_RESTART)
			.setEnabled(GpuPreference.supported());
		builder.registerOwnModOptions()
			.setColorTheme(builder.createColorTheme().setBaseThemeRGB(0x8F7CFF))
			.addPage(builder.createOptionPage()
				.setName(Text.literal("GPU"))
				.addOptionGroup(builder.createOptionGroup()
					.setName(Text.literal("Graphics card"))
					.addOption(gpu)));
	}
}
