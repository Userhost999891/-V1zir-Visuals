package dev.v1zir.visuals.module.impl.effects;

import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.module.setting.ColorSetting;
import dev.v1zir.visuals.module.setting.NumberSetting;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;

/**
 * Recolours the flash of hurt entities. Vanilla tints them from a tiny overlay texture whose top
 * eight rows hold the hurt colour; those rows are rewritten and re-uploaded whenever the colour
 * changes, so rendering itself costs nothing extra.
 */
public final class HitColorModule extends Module {
	private static final int HURT_ROWS = 8;
	private static final int WIDTH = 16;

	public final BoolSetting themeColor = add(new BoolSetting("Theme color", true));
	public final ColorSetting color = add(new ColorSetting("Color", 0xFF9B7BFF, false).visibleIf(() -> !themeColor.on()));
	public final NumberSetting intensity = add(new NumberSetting("Intensity", 0.45, 0.05, 0.95, 0.01));

	/** Vanilla's hurt colour, read from the texture before the first change. */
	private int vanilla;
	private boolean captured;
	private boolean applied;
	private int appliedColor;

	public HitColorModule() {
		super("Hit Color", "Custom colour of the hurt flash", Category.EFFECTS);
	}

	@Override
	public void onTick() {
		// The overlay keeps (alpha) of the entity's own colour and mixes in the rest of this colour.
		int argb = ColorUtil.withAlpha(themeColor.on() ? Theme.accent() : color.argb(), Math.round(255 * (1f - intensity.f())));
		if (applied && argb == appliedColor) return;
		if (write(argb)) {
			applied = true;
			appliedColor = argb;
		}
	}

	@Override
	protected void onDisable() {
		if (applied && captured) write(vanilla);
		applied = false;
	}

	private boolean write(int argb) {
		if (mc.gameRenderer == null) return false;
		NativeImageBackedTexture texture = mc.gameRenderer.getOverlayTexture().texture;
		NativeImage image = texture.getImage();
		if (image == null) return false;
		if (!captured) {
			vanilla = image.getColorArgb(0, 0);
			captured = true;
		}
		for (int y = 0; y < HURT_ROWS; y++) {
			for (int x = 0; x < WIDTH; x++) image.setColorArgb(x, y, argb);
		}
		texture.upload();
		return true;
	}
}
