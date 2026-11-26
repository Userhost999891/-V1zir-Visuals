package dev.v1zir.visuals.render.world;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.v1zir.visuals.render.V1zirPipelines;
import net.minecraft.client.texture.Sprite;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/**
 * Pixel-art sprites of one atlas (the GUI's hearts) on camera-facing quads in the world, crisp at any distance the
 * way the GUI draws its pixel art (world_sprite.fsh). Queue them from {@link WorldEffect#render3D} back to front;
 * WorldEffectRenderer draws them after the other effects and before WorldText, in one draw: depth tested, or over
 * everything when the frame's sprites are see-through.
 */
public final class WorldSprites {
	/** Entity vertex format: position, colour, UV0, the sprite's region in UV1 and UV2 (times 16384), an unused normal. */
	private static final int STRIDE = 36;
	private static final float REGION_SCALE = 16384f;
	private static ByteBuffer buffer = MemoryUtil.memAlloc(STRIDE * 4 * 256);
	private static int quads;
	private static GpuTextureView texture;
	private static boolean seeThrough;

	private WorldSprites() {
	}

	/**
	 * Queues a sprite w x h blocks centred (dx, dy) blocks right and up of the world position (x, y, z) on the camera
	 * plane, tinted by argb (its alpha fades it). view is the sprite's atlas texture.
	 */
	public static void draw(double x, double y, double z, float dx, float dy, float w, float h, Sprite sprite, GpuTextureView view,
							boolean seeThrough, int argb) {
		if ((argb >>> 24) == 0) return;
		texture = view;
		WorldSprites.seeThrough = seeThrough;
		if ((quads + 1) * 4 * STRIDE > buffer.capacity()) buffer = MemoryUtil.memRealloc(buffer, buffer.capacity() * 2);
		Vector3f r = WorldEffectRenderer.RIGHT;
		Vector3f u = WorldEffectRenderer.UP;
		float cx = (float) (x - WorldEffectRenderer.camX) + r.x * dx + u.x * dy;
		float cy = (float) (y - WorldEffectRenderer.camY) + r.y * dx + u.y * dy;
		float cz = (float) (z - WorldEffectRenderer.camZ) + r.z * dx + u.z * dy;
		float hw = w * 0.5f;
		float hh = h * 0.5f;
		float u0 = sprite.getMinU();
		float v0 = sprite.getMinV();
		float u1 = sprite.getMaxU();
		float v1 = sprite.getMaxV();
		short ru0 = (short) Math.round(u0 * REGION_SCALE);
		short rv0 = (short) Math.round(v0 * REGION_SCALE);
		short ru1 = (short) Math.round(u1 * REGION_SCALE);
		short rv1 = (short) Math.round(v1 * REGION_SCALE);
		long p = MemoryUtil.memAddress(buffer) + (long) quads * 4 * STRIDE;
		vertex(p, cx - r.x * hw + u.x * hh, cy - r.y * hw + u.y * hh, cz - r.z * hw + u.z * hh, u0, v0, argb, ru0, rv0, ru1, rv1);
		vertex(p + STRIDE, cx - r.x * hw - u.x * hh, cy - r.y * hw - u.y * hh, cz - r.z * hw - u.z * hh, u0, v1, argb, ru0, rv0, ru1, rv1);
		vertex(p + STRIDE * 2, cx + r.x * hw - u.x * hh, cy + r.y * hw - u.y * hh, cz + r.z * hw - u.z * hh, u1, v1, argb, ru0, rv0, ru1, rv1);
		vertex(p + STRIDE * 3, cx + r.x * hw + u.x * hh, cy + r.y * hw + u.y * hh, cz + r.z * hw + u.z * hh, u1, v0, argb, ru0, rv0, ru1, rv1);
		quads++;
	}

	private static void vertex(long p, float x, float y, float z, float u, float v, int argb, short u0, short v0, short u1, short v1) {
		MemoryUtil.memPutFloat(p, x);
		MemoryUtil.memPutFloat(p + 4, y);
		MemoryUtil.memPutFloat(p + 8, z);
		MemoryUtil.memPutByte(p + 12, (byte) (argb >> 16 & 0xFF));
		MemoryUtil.memPutByte(p + 13, (byte) (argb >> 8 & 0xFF));
		MemoryUtil.memPutByte(p + 14, (byte) (argb & 0xFF));
		MemoryUtil.memPutByte(p + 15, (byte) (argb >>> 24));
		MemoryUtil.memPutFloat(p + 16, u);
		MemoryUtil.memPutFloat(p + 20, v);
		MemoryUtil.memPutShort(p + 24, u0);
		MemoryUtil.memPutShort(p + 26, v0);
		MemoryUtil.memPutShort(p + 28, u1);
		MemoryUtil.memPutShort(p + 30, v1);
		MemoryUtil.memPutInt(p + 32, 0);
	}

	static boolean isEmpty() {
		return quads == 0;
	}

	/** Draws the frame's sprites (WorldEffectRenderer). */
	static void flush(GpuBufferSlice params) {
		if (quads == 0) return;
		buffer.position(0).limit(quads * 4 * STRIDE);
		GpuBuffer vertices = V1zirPipelines.WORLD_SPRITE.getVertexFormat().uploadImmediateVertexBuffer(buffer);
		buffer.clear();
		var indices = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS);
		GpuBuffer indexBuffer = indices.getIndexBuffer(quads * 6);
		try (RenderPass pass = WorldEffectRenderer.openPass(() -> "V1zir world sprites")) {
			pass.setPipeline(seeThrough ? V1zirPipelines.WORLD_SPRITE_SEE_THROUGH : V1zirPipelines.WORLD_SPRITE);
			pass.setUniform("WorldParams", params);
			pass.bindTexture("Sampler0", texture, RenderSystem.getSamplerCache().get(FilterMode.NEAREST));
			pass.setVertexBuffer(0, vertices);
			pass.setIndexBuffer(indexBuffer, indices.getIndexType());
			pass.drawIndexed(0, 0, quads * 6, 1);
		}
		quads = 0;
		texture = null;
	}
}
