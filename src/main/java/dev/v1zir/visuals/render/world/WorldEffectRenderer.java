package dev.v1zir.visuals.render.world;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.v1zir.visuals.V1zir;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.render.V1zirPipelines;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.DynamicUniformStorage;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Immediate renderer for the 3D effects. Geometry is built camera-relative into two batches
 * (additive glow and alpha blended) and drawn right after the world, depth tested against it.
 */
public final class WorldEffectRenderer {
	private static final int UBO_SIZE = new Std140SizeCalculator().putMat4f().putMat4f().putVec4().get();
	private static final Matrix4f VIEW = new Matrix4f();
	private static final Matrix4f PROJECTION = new Matrix4f();
	private static final Mesh GLOW = new Mesh();
	private static final Mesh TRANSLUCENT = new Mesh();
	private static DynamicUniformStorage<WorldData> storage;

	/** Camera position of the frame being rendered; vertices are relative to it. */
	public static double camX;
	public static double camY;
	public static double camZ;
	/** World-space camera right and up vectors, for billboards. */
	public static final Vector3f RIGHT = new Vector3f();
	public static final Vector3f UP = new Vector3f();

	private WorldEffectRenderer() {
	}

	public static void flip() {
		if (storage != null) storage.clear();
	}

	public static void captureMatrices(Matrix4f view, Matrix4f projection) {
		VIEW.set(view);
		PROJECTION.set(projection);
	}

	public static Mesh glow() {
		return GLOW;
	}

	public static Mesh translucent() {
		return TRANSLUCENT;
	}

	/** Called after the world has been rendered (WorldRendererMixin). */
	public static void render(float tickDelta) {
		MinecraftClient mc = V1zir.mc();
		if (mc.world == null || mc.player == null) return;
		boolean any = false;
		for (Module module : ModuleManager.all()) {
			if (module instanceof WorldEffect && module.isEnabled()) {
				any = true;
				break;
			}
		}
		if (!any) return;

		Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();
		camX = cam.x;
		camY = cam.y;
		camZ = cam.z;
		RIGHT.set(VIEW.m00(), VIEW.m10(), VIEW.m20()).normalize();
		UP.set(VIEW.m01(), VIEW.m11(), VIEW.m21()).normalize();

		GLOW.reset();
		TRANSLUCENT.reset();
		for (Module module : ModuleManager.all()) {
			if (module instanceof WorldEffect effect && module.isEnabled()) {
				try {
					effect.render3D(tickDelta);
				} catch (RuntimeException e) {
					V1zir.LOGGER.error("Effect {} failed", module.name(), e);
					module.setEnabled(false, false);
				}
			}
		}
		if (GLOW.quads == 0 && TRANSLUCENT.quads == 0) return;

		if (storage == null) storage = new DynamicUniformStorage<>("V1zir world effects UBO", UBO_SIZE, 4);
		GpuBufferSlice params = storage.write(new WorldData(PROJECTION, VIEW, V1zir.time()));
		draw(V1zirPipelines.WORLD_TRANSLUCENT, TRANSLUCENT, params);
		draw(V1zirPipelines.WORLD_GLOW, GLOW, params);
	}

	private static void draw(RenderPipeline pipeline, Mesh mesh, GpuBufferSlice params) {
		if (mesh.quads == 0) return;
		MinecraftClient mc = V1zir.mc();
		ByteBuffer data = mesh.buffer.duplicate();
		data.position(0).limit(mesh.quads * 4 * Mesh.STRIDE);
		GpuBuffer vertices = pipeline.getVertexFormat().uploadImmediateVertexBuffer(data);
		var indices = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS);
		int indexCount = mesh.quads * 6;
		GpuBuffer indexBuffer = indices.getIndexBuffer(indexCount);
		GpuTextureView color = RenderSystem.outputColorTextureOverride != null ? RenderSystem.outputColorTextureOverride : mc.getFramebuffer().getColorAttachmentView();
		GpuTextureView depth = RenderSystem.outputDepthTextureOverride != null ? RenderSystem.outputDepthTextureOverride : mc.getFramebuffer().getDepthAttachmentView();
		try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "V1zir world effects", color, OptionalInt.empty(), depth, OptionalDouble.empty())) {
			pass.setPipeline(pipeline);
			pass.setUniform("WorldParams", params);
			pass.setVertexBuffer(0, vertices);
			pass.setIndexBuffer(indexBuffer, indices.getIndexType());
			pass.drawIndexed(0, 0, indexCount, 1);
		}
	}

	/** Camera-relative quad builder (POSITION_TEXTURE_COLOR, 24 bytes per vertex). */
	public static final class Mesh {
		static final int STRIDE = 24;
		ByteBuffer buffer = MemoryUtil.memAlloc(STRIDE * 4 * 1024);
		int quads;
		private int vertices;

		void reset() {
			quads = 0;
			vertices = 0;
		}

		/** Adds one vertex at world position (x, y, z). Four vertices make a quad. */
		public Mesh vertex(double x, double y, double z, float u, float v, int argb) {
			int needed = (vertices + 1) * STRIDE;
			if (needed > buffer.capacity()) {
				ByteBuffer bigger = MemoryUtil.memAlloc(buffer.capacity() * 2);
				MemoryUtil.memCopy(MemoryUtil.memAddress(buffer), MemoryUtil.memAddress(bigger), (long) vertices * STRIDE);
				MemoryUtil.memFree(buffer);
				buffer = bigger;
			}
			long p = MemoryUtil.memAddress(buffer) + (long) vertices * STRIDE;
			MemoryUtil.memPutFloat(p, (float) (x - camX));
			MemoryUtil.memPutFloat(p + 4, (float) (y - camY));
			MemoryUtil.memPutFloat(p + 8, (float) (z - camZ));
			MemoryUtil.memPutFloat(p + 12, u);
			MemoryUtil.memPutFloat(p + 16, v);
			MemoryUtil.memPutByte(p + 20, (byte) (argb >> 16 & 0xFF));
			MemoryUtil.memPutByte(p + 21, (byte) (argb >> 8 & 0xFF));
			MemoryUtil.memPutByte(p + 22, (byte) (argb & 0xFF));
			MemoryUtil.memPutByte(p + 23, (byte) (argb >>> 24));
			vertices++;
			if (vertices % 4 == 0) quads = vertices / 4;
			return this;
		}

		/** Camera facing glowing orb (radial falloff in the shader). */
		public void orb(double x, double y, double z, float size, int argb) {
			float rx = RIGHT.x * size;
			float ry = RIGHT.y * size;
			float rz = RIGHT.z * size;
			float ux = UP.x * size;
			float uy = UP.y * size;
			float uz = UP.z * size;
			vertex(x - rx + ux, y - ry + uy, z - rz + uz, -1f, 2f, argb);
			vertex(x - rx - ux, y - ry - uy, z - rz - uz, -1f, 4f, argb);
			vertex(x + rx - ux, y + ry - uy, z + rz - uz, 1f, 4f, argb);
			vertex(x + rx + ux, y + ry + uy, z + rz + uz, 1f, 2f, argb);
		}
	}

	private record WorldData(Matrix4f projection, Matrix4f view, float time) implements DynamicUniformStorage.Uploadable {
		@Override
		public void write(ByteBuffer buffer) {
			Std140Builder.intoBuffer(buffer).putMat4f(projection).putMat4f(view).putVec4(time, 0f, 0f, 0f);
		}

		@Override
		public boolean equals(Object o) {
			return false;
		}

		@Override
		public int hashCode() {
			return System.identityHashCode(this);
		}
	}
}
