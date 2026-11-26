package dev.v1zir.visuals.module.impl.visuals;

import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;

/** Maximum brightness everywhere (the lightmap is filled with white). */
public final class FullBrightModule extends Module {
	public FullBrightModule() {
		super("Full Bright", "See clearly in the dark", Category.SHADERS);
		setSubCategory("World");
	}
}
