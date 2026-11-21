package dev.v1zir.visuals.render.sky;

import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import dev.v1zir.visuals.render.Fullscreen;
import dev.v1zir.visuals.render.V1zirPipelines;

import java.util.OptionalInt;

/**
 * The noise the volumetric clouds are carved from (sky_lib.glsl): a tileable 64^3 volume of
 * Perlin-Worley and Worley octaves, stored as an atlas of 2D slices and baked once on the GPU by
 * core/cloud_noise.fsh. Two texture lookups give the sky shader four octaves of 3D noise that would
 * otherwise cost it dozens of hash evaluations per cloud sample.
 */
public final class CloudNoise {
	/** 8 x 8 slices of 64 x 64 texels, each with a one texel wrapped border. */
	private static final int SIZE = 528;

	private static GpuTextureView view;

	private CloudNoise() {
	}

	/** The noise atlas, baked the first time: call it before opening the pass that binds it (the bake is a pass of its own). */
	public static GpuTextureView view() {
		if (view == null) {
			GpuTexture texture = RenderSystem.getDevice().createTexture("V1zir cloud noise", 15, TextureFormat.RGBA8, SIZE, SIZE, 1, 1);
			view = RenderSystem.getDevice().createTextureView(texture);
			try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "V1zir cloud noise bake", view, OptionalInt.empty())) {
				pass.setPipeline(V1zirPipelines.CLOUD_NOISE);
				pass.setVertexBuffer(0, Fullscreen.buffer());
				pass.draw(0, 3);
			}
		}
		return view;
	}

	/** Binds the atlas from {@link #view()} as the sky shader's CloudNoise sampler. */
	public static void bind(RenderPass pass, GpuTextureView noise) {
		pass.bindTexture("CloudNoise", noise, RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
	}
}
