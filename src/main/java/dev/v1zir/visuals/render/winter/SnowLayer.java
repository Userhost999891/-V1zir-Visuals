package dev.v1zir.visuals.render.winter;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.v1zir.visuals.V1zir;
import dev.v1zir.visuals.render.SurfaceMap;
import dev.v1zir.visuals.render.V1zirPipelines;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GpuSampler;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Winter's snow lying close to the camera as real geometry (snow_layer.vsh): a static buffer holds a template for
 * every column within 24 blocks (its top as 3 by 3 quads, three quads of side along each edge), made once; the vertex
 * shader places all of it every frame from the block columns and the snow texture of {@link SurfaceMap}. One draw call,
 * depth tested and written, so whatever walks in the snow sinks into it a little. Render thread only, drawn by
 * render/Reflections right after the flat snow cover with its parameters.
 */
public final class SnowLayer {
	private static final int CELLS = 48;
	/** Where the template's top is cut: rounded edges need the rows close to each side. */
	private static final float[] GRID = {0f, 0.22f, 0.78f, 1f};
	private static final int QUADS_PER_CELL = 9 + 12;
	private static final int QUADS = CELLS * CELLS * QUADS_PER_CELL;
	private static final int STRIDE = 24;
	private static final int USAGE_VERTEX = 32;
	@Nullable
	private static GpuBuffer mesh;

	private SnowLayer() {
	}

	/** Draws the snow over the world's colour and depth with the reflections' parameters (ReflectParams). */
	public static void draw(GpuBufferSlice params) {
		GpuTextureView snow = SurfaceMap.snow();
		if (snow == null || SurfaceMap.columns() == null || SurfaceMap.far() == null) return;
		Framebuffer fb = V1zir.mc().getFramebuffer();
		GpuBuffer vertices = mesh();
		var indices = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS);
		int indexCount = QUADS * 6;
		GpuBuffer indexBuffer = indices.getIndexBuffer(indexCount);
		GpuSampler nearest = RenderSystem.getSamplerCache().get(FilterMode.NEAREST);
		try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "V1zir snow layer",
			fb.getColorAttachmentView(), OptionalInt.empty(), fb.getDepthAttachmentView(), OptionalDouble.empty())) {
			pass.setPipeline(V1zirPipelines.SNOW_LAYER);
			pass.setUniform("ReflectParams", params);
			pass.bindTexture("Sampler2", SurfaceMap.columns(), nearest);
			pass.bindTexture("Sampler4", SurfaceMap.far(), nearest);
			pass.bindTexture("Sampler5", snow, nearest);
			pass.setVertexBuffer(0, vertices);
			pass.setIndexBuffer(indexBuffer, indices.getIndexType());
			pass.drawIndexed(0, 0, indexCount, 1);
		}
	}

	/** The template, made the first time: per column its top, then its four sides. */
	private static GpuBuffer mesh() {
		if (mesh != null) return mesh;
		ByteBuffer data = MemoryUtil.memAlloc(QUADS * 4 * STRIDE);
		try {
			for (int cz = 0; cz < CELLS; cz++) {
				for (int cx = 0; cx < CELLS; cx++) {
					for (int j = 0; j < 3; j++) {
						for (int i = 0; i < 3; i++) {
							vertex(data, GRID[i], 0f, GRID[j], cx, cz, 4);
							vertex(data, GRID[i], 0f, GRID[j + 1], cx, cz, 4);
							vertex(data, GRID[i + 1], 0f, GRID[j + 1], cx, cz, 4);
							vertex(data, GRID[i + 1], 0f, GRID[j], cx, cz, 4);
						}
					}
					for (int j = 0; j < 3; j++) {
						side(data, 0f, GRID[j], 0f, GRID[j + 1], cx, cz, 0);
						side(data, 1f, GRID[j], 1f, GRID[j + 1], cx, cz, 1);
						side(data, GRID[j], 0f, GRID[j + 1], 0f, cx, cz, 2);
						side(data, GRID[j], 1f, GRID[j + 1], 1f, cx, cz, 3);
					}
				}
			}
			data.flip();
			mesh = RenderSystem.getDevice().createBuffer(() -> "V1zir snow layer", USAGE_VERTEX, data);
		} finally {
			MemoryUtil.memFree(data);
		}
		return mesh;
	}

	/** One quad of a side: from the surface (kind 0) down to its foot (kind 1) between two points of the edge. */
	private static void side(ByteBuffer data, float u0, float v0, float u1, float v1, int cx, int cz, int side) {
		vertex(data, u0, 0f, v0, cx, cz, side);
		vertex(data, u1, 0f, v1, cx, cz, side);
		vertex(data, u1, 1f, v1, cx, cz, side);
		vertex(data, u0, 1f, v0, cx, cz, side);
	}

	private static void vertex(ByteBuffer data, float u, float kind, float v, int cx, int cz, int side) {
		data.putFloat(u).putFloat(kind).putFloat(v);
		data.putFloat(cx).putFloat(cz);
		data.put((byte) side).put((byte) 0).put((byte) 0).put((byte) 0xFF);
	}
}
