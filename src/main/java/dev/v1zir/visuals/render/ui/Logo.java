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
 * The client's brand mark. Draws {@link #TEXTURE} when the resource exists (any aspect ratio, fitted
 * into the box) and otherwise the accent bubble with the sparkle icon.
 * <p>
 * The PNG is prepared once for crisp, fringe-free drawing: images larger than 256 px are box-filtered
 * down with premultiplied alpha, and fully transparent pixels take the colour of their neighbours so
 * linear filtering never pulls dark edges in. gui_image.fsh samples the pixel footprint for clean minification.
 */
public final class Logo {
	/** Square PNG with alpha recommended, 128x128 (64x64 at least). */
	public static final Identifier TEXTURE = V1zir.id("textures/gui/logo.png");
	private static final int MAX_SIZE = 256;

	@Nullable
	private static NativeImageBackedTexture texture;
	@Nullable
	private static TextureSetup setup;
	private static int width;
	private static int height;
	private static boolean checked;

	private Logo() {
	}

	/** Looks for the file again (cheap; called when the click GUI opens) so an added or removed logo shows up. */
	public static void refresh() {
		boolean present = MinecraftClient.getInstance().getResourceManager().getResource(TEXTURE).isPresent();
		if (present != (setup != null)) {
			release();
			checked = false;
		}
	}

	/** Draws the mark fitted into the size x size box at (x, y). */
	public static void draw(DrawContext ctx, float x, float y, float size) {
		TextureSetup logo = texture();
		if (logo != null) {
			float w = width >= height ? size : size * width / height;
			float h = height >= width ? size : size * height / width;
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

	@Nullable
	private static TextureSetup texture() {
		if (checked) return setup;
		checked = true;
		MinecraftClient mc = MinecraftClient.getInstance();
		Optional<Resource> resource = mc.getResourceManager().getResource(TEXTURE);
		if (resource.isEmpty()) return null;
		try (InputStream in = resource.get().getInputStream(); NativeImage source = NativeImage.read(in)) {
			NativeImage image = prepare(source);
			width = image.getWidth();
			height = image.getHeight();
			texture = new NativeImageBackedTexture(() -> "V1zir logo", image);
			setup = TextureSetup.of(texture.getGlTextureView(), RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
		} catch (IOException | RuntimeException e) {
			V1zir.LOGGER.warn("Could not load the logo {}", TEXTURE, e);
			release();
		}
		return setup;
	}

	/** Downscales (box filter, premultiplied) to at most MAX_SIZE and bleeds colour into transparent pixels. */
	private static NativeImage prepare(NativeImage source) {
		int sw = source.getWidth();
		int sh = source.getHeight();
		int factor = Math.max(1, (Math.max(sw, sh) + MAX_SIZE - 1) / MAX_SIZE);
		int w = Math.max(1, sw / factor);
		int h = Math.max(1, sh / factor);
		int[] px = new int[w * h];
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				float a = 0f;
				float r = 0f;
				float g = 0f;
				float b = 0f;
				for (int dy = 0; dy < factor; dy++) {
					for (int dx = 0; dx < factor; dx++) {
						int c = source.getColorArgb(x * factor + dx, y * factor + dy);
						float ca = (c >>> 24) / 255f;
						a += ca;
						r += (c >> 16 & 255) * ca;
						g += (c >> 8 & 255) * ca;
						b += (c & 255) * ca;
					}
				}
				float n = factor * factor;
				px[y * w + x] = a <= 0f ? 0 : ColorUtil.argb(Math.round(a / n * 255f), Math.round(r / a), Math.round(g / a), Math.round(b / a));
			}
		}
		NativeImage out = new NativeImage(NativeImage.Format.RGBA, w, h, false);
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int c = px[y * w + x];
				out.setColorArgb(x, y, (c >>> 24) == 0 ? bleed(px, w, h, x, y) : c);
			}
		}
		return out;
	}

	/** Mean colour of the visible neighbours of a transparent pixel, kept transparent. */
	private static int bleed(int[] px, int w, int h, int x, int y) {
		int r = 0;
		int g = 0;
		int b = 0;
		int n = 0;
		for (int dy = -1; dy <= 1; dy++) {
			for (int dx = -1; dx <= 1; dx++) {
				int nx = x + dx;
				int ny = y + dy;
				if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
				int c = px[ny * w + nx];
				if ((c >>> 24) == 0) continue;
				r += c >> 16 & 255;
				g += c >> 8 & 255;
				b += c & 255;
				n++;
			}
		}
		return n == 0 ? 0 : ColorUtil.argb(0, r / n, g / n, b / n);
	}

	private static void release() {
		if (texture != null) texture.close();
		texture = null;
		setup = null;
	}
}
