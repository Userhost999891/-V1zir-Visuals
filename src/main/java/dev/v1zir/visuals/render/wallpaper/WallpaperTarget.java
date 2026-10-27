package dev.v1zir.visuals.render.wallpaper;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.texture.TextureSetup;

import java.util.function.Supplier;

/**
 * Off-screen texture one wallpaper renders into (a screen's, or a card's in the picker), plus a scratch texture of the
 * same size that only exists while the wallpaper is blurred.
 */
public final class WallpaperTarget {
	final Supplier<String> label;
	private GpuTexture texture;
	private GpuTextureView view;
	private GpuTexture scratch;
	private GpuTextureView scratchView;
	private TextureSetup setup;
	private int width;
	private int height;

	public WallpaperTarget(String label) {
		this.label = () -> label;
	}

	/** Makes sure the texture has this size in pixels. */
	void ensure(int w, int h) {
		w = Math.max(1, w);
		h = Math.max(1, h);
		if (view != null && w == width && h == height) return;
		close();
		texture = RenderSystem.getDevice().createTexture(label.get(), 15, TextureFormat.RGBA8, w, h, 1, 1);
		view = RenderSystem.getDevice().createTextureView(texture);
		setup = TextureSetup.of(view, RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
		width = w;
		height = h;
	}

	GpuTextureView scratch() {
		if (scratchView == null) {
			scratch = RenderSystem.getDevice().createTexture(label.get() + " blur", 15, TextureFormat.RGBA8, width, height, 1, 1);
			scratchView = RenderSystem.getDevice().createTextureView(scratch);
		}
		return scratchView;
	}

	/** Frees the scratch texture of a wallpaper that is no longer blurred. */
	void dropScratch() {
		if (scratchView == null) return;
		scratchView.close();
		scratch.close();
		scratchView = null;
		scratch = null;
	}

	public boolean ready() {
		return view != null;
	}

	public GpuTextureView view() {
		return view;
	}

	public TextureSetup textureSetup() {
		return setup;
	}

	public int width() {
		return width;
	}

	public int height() {
		return height;
	}

	/** Frees both textures; the next ensure() creates them again. */
	public void close() {
		dropScratch();
		if (view != null) view.close();
		if (texture != null) texture.close();
		view = null;
		texture = null;
		setup = null;
		width = 0;
		height = 0;
	}
}
