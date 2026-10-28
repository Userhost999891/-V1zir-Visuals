package dev.v1zir.visuals.module.impl.visuals;

import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.module.setting.ModeSetting;
import dev.v1zir.visuals.module.setting.NumberSetting;

/**
 * Screen-space reflections (render/Reflections). Normal reflects like a shader pack: water with
 * Fresnel and gentle waves, a subtle sheen on glass, ice, polished stone, quartz and metal blocks.
 * Metal turns every block into polished metal tinted by its own colour, with sun and moon glints.
 * Rain Effects traces its puddles with the quality settings of this module.
 */
public final class BlockReflectionModule extends Module {
	public static final String NORMAL = "Normal";
	public static final String METAL = "Metal";

	public final ModeSetting mode = add(new ModeSetting("Mode", NORMAL, NORMAL, METAL));
	public final NumberSetting strength = add(new NumberSetting("Strength", 1.0, 0.1, 1.5, 0.05));
	/** Blur of metal and glossy reflections; water and puddles always stay sharp. */
	public final NumberSetting roughness = add(new NumberSetting("Roughness", 0.25, 0.0, 1.0, 0.05));
	public final ModeSetting quality = add(new ModeSetting("Quality", "Medium", "Low", "Medium", "High"));
	/** Traces a quarter of the pixels; the result is upsampled with the roughness blur. */
	public final BoolSetting halfResolution = add(new BoolSetting("Half resolution", true));

	public BlockReflectionModule() {
		super("Block Reflection", "Reflections on water and smooth blocks, or every block as metal", Category.SHADERS);
		setSubCategory("World");
	}

	public boolean metal() {
		return mode.is(METAL);
	}

	/** Ray march steps of the quality setting. */
	public int steps() {
		return switch (quality.index()) {
			case 0 -> 12;
			case 2 -> 32;
			default -> 20;
		};
	}

	/** Binary search steps that pin a hit down between two march steps. */
	public int refinements() {
		return 3 + quality.index();
	}

	@Override
	public String suffix() {
		return mode.get();
	}
}
