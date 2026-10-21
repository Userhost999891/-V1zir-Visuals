package dev.v1zir.visuals.render.font;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.TextureSetup;
import org.lwjgl.system.MemoryUtil;

/** GPU copy of a {@link GlyphAtlas}; uploaded again whenever new glyphs were rasterised. */
public final class AtlasTexture {
	private final GpuTexture texture;
	private final GpuTextureView view;
	private final TextureSetup setup;

	private AtlasTexture() {
		texture = RenderSystem.getDevice().createTexture("V1zir SDF font atlas", 15, TextureFormat.RED8, GlyphAtlas.SIZE, GlyphAtlas.SIZE, 1, 1);
		view = RenderSystem.getDevice().createTextureView(texture);
		setup = TextureSetup.of(view, RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
	}

	/** Returns the texture setup for a page, uploading pending glyphs first. Render thread only. */
	public static TextureSetup prepare(GlyphAtlas atlas) {
		return sync(atlas).setup;
	}

	/** The page's texture for render passes that bind it themselves; uploads pending glyphs first, so call it outside a pass. */
	public static GpuTextureView view(GlyphAtlas atlas) {
		return sync(atlas).view;
	}

	private static AtlasTexture sync(GlyphAtlas atlas) {
		if (atlas.texture == null) atlas.texture = new AtlasTexture();
		if (atlas.isDirty()) {
			atlas.texture.upload(atlas);
			atlas.markClean();
		}
		return atlas.texture;
	}

	private void upload(GlyphAtlas atlas) {
		// NativeImage is only a staging copy: LUMINANCE maps onto the RED8 texture byte for byte.
		NativeImage image = new NativeImage(NativeImage.Format.LUMINANCE, GlyphAtlas.SIZE, GlyphAtlas.SIZE, false);
		try {
			MemoryUtil.memCopy(MemoryUtil.memAddress(atlas.pixels()), image.imageId(), (long) GlyphAtlas.SIZE * GlyphAtlas.SIZE);
			RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture, image);
		} finally {
			image.close();
		}
	}
}
