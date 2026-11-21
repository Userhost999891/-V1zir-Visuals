package dev.v1zir.visuals.render.knife;

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
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Knife previews of the Knives tab: a knife lit like a studio shot, rendered into a colour and depth target of its own
 * at the pixel size it is shown at (times a supersampling factor, so the GUI's linear filter smooths the edges), then
 * drawn in the GUI as an image. Every knife's vertices go to the GPU once; a target is only re-created when its size
 * changes, and nothing renders unless the tab asks for it.
 */
public final class KnifePreview {
	/** GpuBuffer.USAGE_VERTEX */
	private static final int USAGE_VERTEX = 32;
	/** GpuTexture usage: copy source and destination, texture binding, render attachment. */
	private static final int USAGE_TARGET = 15;
	/** Bytes per vertex of POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL: position, colour, UV0, UV1, UV2, normal, pad. */
	private static final int STRIDE = 36;
	private static final int UBO_SIZE = new Std140SizeCalculator().putMat4f().putMat4f().putVec4().putVec4().putVec4().get();
	/** Largest target edge, pixels. */
	private static final int MAX_SIZE = 1024;
	private static final float DEG = (float) (Math.PI / 180.0);
	/** Vertical field of view: long enough a lens that the knife keeps its proportions. */
	private static final float FOV = 22f * DEG;
	/** Studio lights in view space: a key from the upper left and in front, a softer fill from the right. */
	private static final Vector3f KEY = new Vector3f(-0.35f, 0.75f, 0.55f).normalize();
	private static final Vector3f FILL = new Vector3f(0.75f, 0.05f, 0.4f).normalize();
	private static final KnifeType[] TYPES = KnifeType.values();
	private static final GpuBuffer[] BUFFERS = new GpuBuffer[TYPES.length];
	/** Per type: the centre of the model (x, y, z), its length along x and its height along y, cm. */
	private static final float[][] FRAMES = new float[TYPES.length][];
	private static final Params PARAMS = new Params();
	private static final Matrix4f BASE = new Matrix4f();
	private static final GpuBufferSlice[] SLICES = new GpuBufferSlice[8];
	private static DynamicUniformStorage<Params> storage;
	/** A preview failed on this GPU: the tab shows names only from then on. */
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

	public KnifePreview(String label) {
		this.label = label;
	}

	/** Once per frame: the uniform blocks written for the previews of the last frame are free again. */
	public static void flip() {
		if (storage != null) storage.clear();
	}

	/**
	 * Renders a knife and returns its target for Render2D.image, or null when previews cannot render here. The target
	 * holds its rows bottom-up, so draw it with v running from 1 at the top to 0 at the bottom.
	 *
	 * @param fill   how much of the target's width the knife spans when seen from the side
	 * @param yaw    turn about the vertical axis, degrees (0 shows the side with the edge down and the tip to the right)
	 * @param pitch  tilt towards the viewer, degrees
	 * @param motion the knife's own motion (KnifeAnimator, cm space relative to its rest pose), or null
	 * @param parts  angles of its moving parts (KnifeAnimator.parts), or null for closed/rest
	 */
	@Nullable
	public TextureSetup render(KnifeType type, KnifeFinish finish, int pixelW, int pixelH, float fill, float yaw, float pitch,
							   @Nullable Matrix4f motion, @Nullable float[] parts) {
		if (broken || pixelW < 4 || pixelH < 4) return null;
		try {
			int w = Math.min(pixelW, MAX_SIZE);
			int h = Math.min(pixelH, MAX_SIZE);
			ensureTarget(w, h);
			KnifeMesh mesh = type.mesh();
			GpuBuffer buffer = buffer(type, mesh);
			float[] frame = FRAMES[type.ordinal()];
			KnifeMesh.Part[] meshParts = mesh.parts();
			int count = Math.min(meshParts.length, SLICES.length);

			float aspect = w / (float) h;
			float tanHalf = (float) Math.tan(FOV / 2f);
			// Far enough that the knife spans `fill` of the width side on, and its height fits too.
			float length = Math.max(frame[3], 1e-3f);
			float tall = frame[4] / length;
			float distance = Math.max(0.5f / (fill * tanHalf * aspect), tall * 0.5f / (fill * 0.9f * tanHalf)) + 0.0f;
			PARAMS.proj.setPerspective(FOV, aspect, Math.max(0.02f, distance - 1.2f), distance + 1.2f);
			BASE.translation(0f, 0f, -distance)
				.rotateX(pitch * DEG)
				.rotateY(yaw * DEG)
				.scale(1f / length)
				.translate(-frame[0], -frame[1], -frame[2]);
			if (motion != null) BASE.mul(motion);
			PARAMS.finish = finish.ordinal();
			if (storage == null) storage = new DynamicUniformStorage<>("V1zir knife preview UBO", UBO_SIZE, 16);
			// Uniform blocks first: nothing else may run on the encoder while the pass is open.
			for (int i = 0; i < count; i++) {
				KnifeMesh.Part part = meshParts[i];
				float angle = parts != null && i < parts.length ? parts[i] : 0f;
				PARAMS.modelView.set(BASE);
				if (angle != 0f) {
					PARAMS.modelView.translate(part.pivotX(), part.pivotY(), 0f)
						.rotateZ(angle * DEG)
						.translate(-part.pivotX(), -part.pivotY(), 0f);
				}
				SLICES[i] = storage.write(PARAMS);
			}
			try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> label, colorView, OptionalInt.of(0),
				depthView, OptionalDouble.of(1.0))) {
				pass.setPipeline(V1zirPipelines.KNIFE_PREVIEW);
				pass.setVertexBuffer(0, buffer);
				for (int i = 0; i < count; i++) {
					KnifeMesh.Part part = meshParts[i];
					if (part.indexCount() <= 0) continue;
					pass.setUniform("KnifePreview", SLICES[i]);
					pass.draw(part.firstIndex(), part.indexCount());
				}
			}
			return setup;
		} catch (RuntimeException e) {
			broken = true;
			dev.v1zir.visuals.V1zir.LOGGER.warn("Knife previews disabled: {}", e.toString());
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

	/** Frees the target (the tab was closed); the next render makes a new one. */
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

	/**
	 * The knife's triangles as a vertex buffer, built the first time: the indexed mesh written out triangle by
	 * triangle, so every part is the same range of vertices as of indices. UV1 carries the material; the finish
	 * comes with the uniform block, so one buffer serves every finish.
	 */
	private static GpuBuffer buffer(KnifeType type, KnifeMesh mesh) {
		int t = type.ordinal();
		if (BUFFERS[t] != null) return BUFFERS[t];
		float[] data = mesh.data;
		int[] indices = mesh.indices;
		float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
		float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
		ByteBuffer bytes = MemoryUtil.memAlloc(Math.max(1, indices.length) * STRIDE);
		try {
			for (int v : indices) {
				int o = v * KnifeMesh.FLOATS;
				float x = data[o];
				float y = data[o + 1];
				float z = data[o + 2];
				minX = Math.min(minX, x);
				minY = Math.min(minY, y);
				minZ = Math.min(minZ, z);
				maxX = Math.max(maxX, x);
				maxY = Math.max(maxY, y);
				maxZ = Math.max(maxZ, z);
				int c = mesh.color[v];
				bytes.putFloat(x).putFloat(y).putFloat(z);
				bytes.put((byte) (c >> 16)).put((byte) (c >> 8)).put((byte) c).put((byte) (c >>> 24));
				bytes.putFloat(data[o + 6]).putFloat(data[o + 7]);
				bytes.putShort((short) mesh.material[v]).putShort((short) 0);
				bytes.putShort((short) 240).putShort((short) 240);
				bytes.put(normalByte(data[o + 3])).put(normalByte(data[o + 4])).put(normalByte(data[o + 5])).put((byte) 0);
			}
			bytes.flip();
			BUFFERS[t] = RenderSystem.getDevice().createBuffer(() -> "V1zir knife " + type.title(), USAGE_VERTEX, bytes);
		} finally {
			MemoryUtil.memFree(bytes);
		}
		if (indices.length == 0) {
			minX = minY = minZ = maxX = maxY = maxZ = 0f;
		}
		FRAMES[t] = new float[] {(minX + maxX) / 2f, (minY + maxY) / 2f, (minZ + maxZ) / 2f, maxX - minX, maxY - minY};
		return BUFFERS[t];
	}

	private static byte normalByte(float n) {
		return (byte) Math.round(Math.max(-1f, Math.min(1f, n)) * 127f);
	}

	/** KnifePreview (knife_preview.glsl), rewritten for every part of every preview. */
	private static final class Params implements DynamicUniformStorage.Uploadable {
		final Matrix4f proj = new Matrix4f();
		final Matrix4f modelView = new Matrix4f();
		float finish;

		@Override
		public void write(ByteBuffer buffer) {
			Std140Builder.intoBuffer(buffer)
				.putMat4f(proj)
				.putMat4f(modelView)
				.putVec4(KEY.x, KEY.y, KEY.z, 0f)
				.putVec4(FILL.x, FILL.y, FILL.z, 0f)
				.putVec4(finish, 1f, 0f, 0f);
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
