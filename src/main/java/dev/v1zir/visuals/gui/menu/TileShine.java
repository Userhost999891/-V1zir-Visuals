package dev.v1zir.visuals.gui.menu;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import dev.v1zir.visuals.V1zir;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.ColorUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureSetup;
import org.jetbrains.annotations.Nullable;

/**
 * The light that crosses a console's tile as it is picked: a broad, soft white band lying across the tile's diagonal,
 * passing from its top left corner to its bottom right. One texture holds the band across a square 2.5 tiles wide; the
 * tile shows a tile-sized window of it (clipped to its rounded corners), and moving the window from the bottom right of
 * the texture to its top left carries the band over the tile. Render thread only.
 */
final class TileShine {
	/** The texture's side in tiles: the band starts half a tile before the tile's corner and ends half a tile after. */
	private static final float SPAN = 2.5f;
	private static final int PIXELS = 160;
	/** The band's half width, along the diagonal (a tile's diagonal is 2 of these units). */
	private static final float BAND = 0.42f;
	@Nullable
	private static NativeImageBackedTexture texture;
	@Nullable
	private static TextureSetup setup;
	private static boolean broken;

	private TileShine() {
	}

	/** The shine p of its way across (0 to 1) over the size x size tile at (x, y) with corners of radius, at alpha. */
	static void draw(DrawContext ctx, float x, float y, float size, float radius, float p, float alpha) {
		if (p <= 0f || p >= 1f || alpha <= 0.004f) return;
		TextureSetup band = setup();
		if (band == null) return;
		// The window's corner in tiles: at 1.5 the band lies before the tile's top left, at 0 past its bottom right.
		float o = 1.5f * (1f - p);
		float u0 = o / SPAN, u1 = (o + 1f) / SPAN;
		Render2D.image(ctx, band, x, y, size, size, radius, ColorUtil.fade(0xFFFFFFFF, alpha), u0, u0, u1, u1, 0);
	}

	@Nullable
	private static TextureSetup setup() {
		if (setup != null || broken) return setup;
		try {
			NativeImage image = new NativeImage(NativeImage.Format.RGBA, PIXELS, PIXELS, false);
			for (int py = 0; py < PIXELS; py++) {
				for (int px = 0; px < PIXELS; px++) {
					float u = (px + 0.5f) / PIXELS * SPAN, v = (py + 0.5f) / PIXELS * SPAN;
					float d = (u + v - SPAN) / BAND;
					float a = (float) Math.exp(-d * d);
					image.setColorArgb(px, py, Math.round(255f * a) << 24 | 0xFFFFFF);
				}
			}
			texture = new NativeImageBackedTexture(() -> "V1zir tile shine", image);
			setup = TextureSetup.of(texture.getGlTextureView(), RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
		} catch (RuntimeException e) {
			V1zir.LOGGER.warn("Could not make the tiles' shine", e);
			broken = true;
		}
		return setup;
	}
}
