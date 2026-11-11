package dev.v1zir.visuals.render.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import dev.v1zir.visuals.V1zir;
import dev.v1zir.visuals.render.font.Icons;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/**
 * The client's brand mark: {@link #TEXTURE} fitted into a box (any aspect ratio), or the accent bubble with
 * the sparkle icon when the file is missing. Small marks (the HUD watermark) use {@link #TEXTURE_SMALL}, a
 * pre-downsampled copy, so they stay sharp; gui_image.fsh box-filters whatever is minified further.
 * Fully transparent pixels take the colour of their neighbours on load, so linear filtering pulls no dark fringe in.
 */
public final class Logo {
	/** 256x256 PNG with alpha. */
	public static final Identifier TEXTURE = V1zir.id("textures/gui/logo.png");
	/** 64x64 copy of the same logo for marks of about 32 physical pixels or less. */
	public static final Identifier TEXTURE_SMALL = V1zir.id("textures/gui/logo_small.png");
	private static final float SMALL_PIXELS = 32f;

	private static final Image LARGE = new Image(TEXTURE);
	private static final Image SMALL = new Image(TEXTURE_SMALL);

	private Logo() {
	}

	/** Looks for the file again (cheap; called when the click GUI opens) so an added or removed logo shows up. */
	public static void refresh() {
		boolean present = MinecraftClient.getInstance().getResourceManager().getResource(TEXTURE).isPresent();
		if (present != (LARGE.setup != null)) {
			LARGE.release();
			SMALL.release();
		}
	}

	/** Draws the mark fitted into the size x size box at (x, y). */
	public static void draw(DrawContext ctx, float x, float y, float size) {
		boolean small = size * (float) MinecraftClient.getInstance().getWindow().getScaleFactor() <= SMALL_PIXELS;
		Image image = small && SMALL.load() != null ? SMALL : LARGE;
		TextureSetup logo = image.load();
		if (logo != null) {
			float w = image.width >= image.height ? size : size * image.width / image.height;
			float h = image.height >= image.width ? size : size * image.height / image.width;
			Render2D.image(ctx, logo, x + (size - w) / 2f, y + (size - h) / 2f, w, h, 0f, 0xFFFFFFFF);
			return;
		}
		int accent = Theme.accent();
		int accent2 = Theme.accent2();
		float r = size / 2f;
		Render2D.glow(ctx, x, y, size, size, r, size * 0.5f, ColorUtil.withAlpha(accent, 110), ColorUtil.withAlpha(accent2, 110));
		Render2D.round(ctx, x, y, size, size, r, accent, accent2, accent2, accent);
		Render2D.icon(ctx, Icons.SPARKLES, x + r, y + r, size * 0.66f, 0xFFFFFFFF);
	}

	/** One logo file, uploaded on first use. */
	private static final class Image {
		private final Identifier id;
		@Nullable
		private NativeImageBackedTexture texture;
		@Nullable
		private TextureSetup setup;
		private int width;
		private int height;
		private boolean checked;

		Image(Identifier id) {
			this.id = id;
		}

		@Nullable
		TextureSetup load() {
			if (checked) return setup;
			checked = true;
			Optional<Resource> resource = MinecraftClient.getInstance().getResourceManager().getResource(id);
			if (resource.isEmpty()) return null;
			try (InputStream in = resource.get().getInputStream()) {
				NativeImage image = NativeImage.read(in);
				width = image.getWidth();
				height = image.getHeight();
				bleed(image);
				texture = new NativeImageBackedTexture(() -> "V1zir logo", image);
				setup = TextureSetup.of(texture.getGlTextureView(), RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
			} catch (IOException | RuntimeException e) {
				V1zir.LOGGER.warn("Could not load the logo {}", id, e);
				release();
				checked = true;
			}
			return setup;
		}

		void release() {
			if (texture != null) texture.close();
			texture = null;
			setup = null;
			checked = false;
		}
	}

	/** Gives every fully transparent pixel the mean colour of its visible neighbours (it stays transparent). */
	private static void bleed(NativeImage image) {
		int w = image.getWidth();
		int h = image.getHeight();
		int[] px = new int[w * h];
		for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) px[y * w + x] = image.getColorArgb(x, y);
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				if ((px[y * w + x] >>> 24) != 0) continue;
				int r = 0;
				int g = 0;
				int b = 0;
				int n = 0;
				for (int dy = Math.max(0, y - 1); dy <= Math.min(h - 1, y + 1); dy++) {
					for (int dx = Math.max(0, x - 1); dx <= Math.min(w - 1, x + 1); dx++) {
						int c = px[dy * w + dx];
						if ((c >>> 24) == 0) continue;
						r += c >> 16 & 255;
						g += c >> 8 & 255;
						b += c & 255;
						n++;
					}
				}
				if (n > 0) image.setColorArgb(x, y, ColorUtil.argb(0, r / n, g / n, b / n));
			}
		}
	}
}
