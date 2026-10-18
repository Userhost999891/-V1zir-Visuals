package dev.v1zir.visuals.render.sky;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import dev.v1zir.visuals.render.Fullscreen;
import dev.v1zir.visuals.render.V1zirPipelines;
import net.minecraft.client.texture.TextureSetup;

import java.util.OptionalInt;

/**
 * Off-screen texture the sky shader renders into at a fraction of the screen resolution. The sky
 * is smooth, so rendering a quarter of the pixels and upsampling looks the same and costs a
 * quarter as much.
 */
public final class SkyTarget {
	private final String label;
	private GpuTexture texture;
	private GpuTextureView view;
	private TextureSetup setup;
	private int width;
	private int height;

	public SkyTarget(String label) {
		this.label = label;
	}

	/** Makes sure the texture matches the given screen size and scale; returns its view. */
	public GpuTextureView ensure(int screenWidth, int screenHeight, float scale) {
		int w = Math.max(1, Math.round(screenWidth * scale));
		int h = Math.max(1, Math.round(screenHeight * scale));
		if (view == null || w != width || h != height) {
			if (view != null) view.close();
			if (texture != null) texture.close();
			texture = RenderSystem.getDevice().createTexture(label, 15, TextureFormat.RGBA8, w, h, 1, 1);
			view = RenderSystem.getDevice().createTextureView(texture);
			setup = TextureSetup.of(view, RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
			width = w;
			height = h;
		}
		return view;
	}

	public GpuTextureView view() {
		return view;
	}

	public TextureSetup textureSetup() {
		return setup;
	}

	/** Runs the sky shader over the whole texture. */
	public void render(GpuBufferSlice skyParams) {
		try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> label, view, OptionalInt.empty())) {
			pass.setPipeline(V1zirPipelines.SKY);
			pass.setUniform("SkyParams", skyParams);
			pass.setVertexBuffer(0, Fullscreen.buffer());
			pass.draw(0, 3);
		}
	}

	/** Draws this texture, upsampled, over the whole of the given colour target. */
	public void upsampleTo(GpuTextureView target) {
		try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> label + " upsample", target, OptionalInt.empty())) {
			pass.setPipeline(V1zirPipelines.SKY_UPSAMPLE);
			pass.bindTexture("Sampler0", view, RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
			pass.setVertexBuffer(0, Fullscreen.buffer());
			pass.draw(0, 3);
		}
	}
}
