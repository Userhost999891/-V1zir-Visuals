package dev.v1zir.visuals.render.cosmetic;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import dev.v1zir.visuals.render.V1zirPipelines;
import net.minecraft.client.gl.DynamicUniformStorage;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.client.util.math.MatrixStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Previews of single cosmetics on the Cosmetics tab: the item lit like a studio shot, moving as it would when worn,
 * rendered into a colour and depth target of its own (supersampled), then drawn in the GUI as an image. Solid parts
 * first, glowing and translucent ones blended over them. Vertex buffers are built once per item, targets reused.
 */
public final class CosmeticPreview {
	/** GpuBuffer.USAGE_VERTEX */
	private static final int USAGE_VERTEX = 32;
	private static final int USAGE_TARGET = 15;
	/** Bytes per vertex of POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL. */
	private static final int STRIDE = 36;
	private static final int UBO_SIZE = new Std140SizeCalculator().putMat4f().putMat4f().putVec4().putVec4().putVec4().putVec4().get();
	private static final int MAX_SIZE = 1024;
	private static final float DEG = (float) (Math.PI / 180.0);
	private static final float FOV = 24f * DEG;
	private static final Vector3f KEY = new Vector3f(-0.35f, 0.75f, 0.55f).normalize();
	private static final Vector3f FILL = new Vector3f(0.75f, 0.05f, 0.4f).normalize();
	private static final Cosmetic[] ITEMS = Cosmetic.values();
	private static final GpuBuffer[] BUFFERS = new GpuBuffer[ITEMS.length];
	private static final Params PARAMS = new Params();
	private static final Matrix4f BASE = new Matrix4f();
	private static final MatrixStack MOTION = new MatrixStack();
	private static final GpuBufferSlice[] SLICES = new GpuBufferSlice[8];
	private static DynamicUniformStorage<Params> storage;
	private static boolean broken;

	private final String label;
	@Nullable
	private GpuTexture color;
	@Nullable
	private GpuTexture depth;
	@Nullable
	private GpuTextureView colorView;
	@Nullable
	private GpuTextureView depthView;
	@Nullable
	private TextureSetup setup;
	private int width;
	private int height;

	public CosmeticPreview(String label) {
		this.label = label;
	}

	public static void flip() {
		if (storage != null) storage.clear();
	}

	/**
	 * Renders an item turned by yaw and pitch (degrees; 0 faces its front) and returns its target for Render2D.image
	 * (draw it with v from 1 at the top to 0), or null when previews cannot render here.
	 */
	@Nullable
	public TextureSetup render(Cosmetic item, int pixelW, int pixelH, float fill, float yaw, float pitch, CosmeticMotion motion) {
		if (broken || pixelW < 4 || pixelH < 4) return null;
		try {
			int w = Math.min(pixelW, MAX_SIZE);
			int h = Math.min(pixelH, MAX_SIZE);
			ensureTarget(w, h);
			CosmeticMesh mesh = item.mesh();
			GpuBuffer buffer = buffer(item, mesh);
			float[] b = mesh.bounds;
			float cx = (b[0] + b[3]) / 2f;
			float cy = (b[1] + b[4]) / 2f;
			float cz = (b[2] + b[5]) / 2f;
			float size = Math.max(Math.max(b[3] - b[0], b[4] - b[1]), b[5] - b[2]);
			float aspect = w / (float) h;
			float tanHalf = (float) Math.tan(FOV / 2f);
			// Far enough that the item's largest extent fits `fill` of the smaller side whichever way it turns.
			float distance = 0.5f / (fill * tanHalf * Math.min(aspect, 1f)) + 0.5f;
			PARAMS.proj.setPerspective(FOV, aspect, Math.max(0.05f, distance - 1.5f), distance + 1.5f);
			BASE.translation(0f, 0f, -distance).rotateX(pitch * DEG).rotateY(yaw * DEG).scale(1f / size).translate(-cx, -cy, -cz);
			PARAMS.time = motion.time;
			if (storage == null) storage = new DynamicUniformStorage<>("V1zir cosmetic preview UBO", UBO_SIZE, 16);
			CosmeticMesh.Part[] parts = mesh.parts();
			int count = Math.min(parts.length, SLICES.length);
			for (int i = 0; i < count; i++) {
				MOTION.push();
				CosmeticRenderer.move(MOTION, item.motion, i, parts[i], motion);
				PARAMS.modelView.set(BASE).mul(MOTION.peek().getPositionMatrix());
				MOTION.pop();
				SLICES[i] = storage.write(PARAMS);
			}
			try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> label, colorView, OptionalInt.of(0),
				depthView, OptionalDouble.of(1.0))) {
				pass.setVertexBuffer(0, buffer);
				for (int glow = 0; glow < 2; glow++) {
					pass.setPipeline(glow == 0 ? V1zirPipelines.COSMETIC_PREVIEW : V1zirPipelines.COSMETIC_PREVIEW_GLOW);
					for (int i = 0; i < count; i++) {
						CosmeticMesh.Part part = parts[i];
						if (part.glow() != (glow == 1) || part.indexCount() <= 0) continue;
						pass.setUniform("CosmeticPreview", SLICES[i]);
						pass.draw(part.firstIndex(), part.indexCount());
					}
				}
			}
			return setup;
		} catch (RuntimeException e) {
			broken = true;
			dev.v1zir.visuals.V1zir.LOGGER.warn("Cosmetic previews disabled: {}", e.toString());
			return null;
		}
	}

	private void ensureTarget(int w, int h) {
		if (color != null && w == width && h == height) return;
		close();
		width = w;
		height = h;
		color = RenderSystem.getDevice().createTexture(label, USAGE_TARGET, TextureFormat.RGBA8, w, h, 1, 1);
		depth = RenderSystem.getDevice().createTexture(label + " depth", USAGE_TARGET, TextureFormat.DEPTH32, w, h, 1, 1);
		colorView = RenderSystem.getDevice().createTextureView(color);
		depthView = RenderSystem.getDevice().createTextureView(depth);
		setup = TextureSetup.of(colorView, RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
	}

	public void close() {
		if (colorView != null) colorView.close();
		if (depthView != null) depthView.close();
		if (color != null) color.close();
		if (depth != null) depth.close();
		color = null;
		depth = null;
		colorView = null;
		depthView = null;
		setup = null;
		width = 0;
		height = 0;
	}

	/** The item's triangles as a vertex buffer, written out triangle by triangle so parts keep their index ranges. */
	private static GpuBuffer buffer(Cosmetic item, CosmeticMesh mesh) {
		int t = item.ordinal();
		if (BUFFERS[t] != null) return BUFFERS[t];
		float[] data = mesh.data;
		int[] indices = mesh.indices;
		ByteBuffer bytes = MemoryUtil.memAlloc(Math.max(1, indices.length) * STRIDE);
		try {
			for (int v : indices) {
				int o = v * CosmeticMesh.FLOATS;
				int c = mesh.color[v];
				bytes.putFloat(data[o]).putFloat(data[o + 1]).putFloat(data[o + 2]);
				bytes.put((byte) (c >> 16)).put((byte) (c >> 8)).put((byte) c).put((byte) (c >>> 24));
				bytes.putFloat(data[o + 6]).putFloat(data[o + 7]);
				bytes.putShort((short) mesh.material[v]).putShort((short) 0);
				bytes.putShort((short) 240).putShort((short) 240);
				bytes.put(normalByte(data[o + 3])).put(normalByte(data[o + 4])).put(normalByte(data[o + 5])).put((byte) 0);
			}
			bytes.flip();
			BUFFERS[t] = RenderSystem.getDevice().createBuffer(() -> "V1zir cosmetic " + item.title(), USAGE_VERTEX, bytes);
		} finally {
			MemoryUtil.memFree(bytes);
		}
		return BUFFERS[t];
	}

	private static byte normalByte(float n) {
		return (byte) Math.round(Math.max(-1f, Math.min(1f, n)) * 127f);
	}

	/** CosmeticPreview (cosmetic_preview.glsl), rewritten for every part. */
	private static final class Params implements DynamicUniformStorage.Uploadable {
		final Matrix4f proj = new Matrix4f();
		final Matrix4f modelView = new Matrix4f();
		float time;

		@Override
		public void write(ByteBuffer buffer) {
			Std140Builder.intoBuffer(buffer)
				.putMat4f(proj)
				.putMat4f(modelView)
				.putVec4(KEY.x, KEY.y, KEY.z, 0f)
				.putVec4(FILL.x, FILL.y, FILL.z, 0f)
				.putVec4(time, 1f, 0f, 0f)
				.putVec4(0f, 0f, 0f, 0f);
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
