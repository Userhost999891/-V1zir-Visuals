package dev.v1zir.visuals.module.impl.effects;

import dev.v1zir.visuals.V1zir;
import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.module.setting.ColorSetting;
import dev.v1zir.visuals.module.setting.NumberSetting;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.util.Identifier;

/**
 * End crystals in a custom colour and light, with their own size, spin speed and bounce height
 * (EndCrystalEntityRendererMixin, EndCrystalEntityModelMixin).
 */
public final class CrystalChamsModule extends Module {
	private static final Identifier WHITE = V1zir.id("textures/misc/white.png");
	private static final int FULL_BRIGHT = 0xF000F0;

	public final BoolSetting themeColor = add(new BoolSetting("Theme color", true));
	public final ColorSetting color = add(new ColorSetting("Color", 0xFF9B7BFF, false).visibleIf(() -> !themeColor.on()));
	public final NumberSetting opacity = add(new NumberSetting("Opacity", 0.75, 0.1, 1.0, 0.05));
	public final BoolSetting texture = add(new BoolSetting("Texture", true));
	public final BoolSetting glow = add(new BoolSetting("Glow", true));
	public final NumberSetting scale = add(new NumberSetting("Scale", 1.0, 0.4, 2.0, 0.05).suffix("x"));
	public final NumberSetting spin = add(new NumberSetting("Spin speed", 1.0, 0.0, 5.0, 0.05).suffix("x"));
	public final NumberSetting bounce = add(new NumberSetting("Bounce", 1.0, 0.0, 3.0, 0.05).suffix("x"));

	private RenderLayer textured;
	private RenderLayer solid;

	public CrystalChamsModule() {
		super("Crystal Chams", "Colour, size and motion of end crystals", Category.EFFECTS);
	}

	/** The module while it is on, else null. */
	public static CrystalChamsModule active() {
		CrystalChamsModule module = ModuleManager.get(CrystalChamsModule.class);
		return module != null && module.isEnabled() ? module : null;
	}

	/** Translucent, so the colour's opacity works: the crystal's own texture under the tint, or plain white for a solid colour. */
	public RenderLayer layer(Identifier crystalTexture) {
		if (texture.on()) {
			if (textured == null) textured = RenderLayers.entityTranslucent(crystalTexture);
			return textured;
		}
		if (solid == null) solid = RenderLayers.entityTranslucent(WHITE);
		return solid;
	}

	public int tint() {
		return ColorUtil.withAlpha(themeColor.on() ? Theme.accent() : color.argb(), Math.round(255 * opacity.f()));
	}

	/** Full brightness makes the colour glow in the dark like the end crystal's beam. */
	public int light(int light) {
		return glow.on() ? FULL_BRIGHT : light;
	}

	/** Vanilla's bob curve (EndCrystalEntityRenderer.getYOffset) with its height scaled. */
	public float yOffset(float age) {
		float f = (float) Math.sin(age * 0.2f) / 2f + 0.5f;
		return (f * f + f) * 0.4f * bounce.f() - 1.4f;
	}
}
