package dev.v1zir.visuals.render;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import dev.v1zir.visuals.V1zir;
import net.minecraft.client.gl.DynamicUniformStorage;
import net.minecraft.client.gl.Framebuffer;

import java.nio.ByteBuffer;
import java.util.OptionalInt;

/**
 * Bloom of the world and the hand (BloomModule): a bright pass into half resolution, a short dual
 * Kawase blur down to a sixteenth and back up (the backdrop blur's own passes), and the result added
 * onto the frame. Every pass but the last runs at a quarter of the pixels or fewer, so it stays cheap;
 * it runs before the GUI is drawn, which never blooms.
 */
public final class Bloom {
	private static final int LEVELS = 4;
	private static final int SIZE = new Std140SizeCalculator().putVec4().get();
	private static final GpuTexture[] textures = new GpuTexture[LEVELS];
	private static final GpuTextureView[] views = new GpuTextureView[LEVELS];
	private static final int[] widths = new int[LEVELS];
	private static final int[] heights = new int[LEVELS];
	private static final Data DATA = new Data();
	private static DynamicUniformStorage<Data> storage;
	private static int sourceWidth = -1;
	private static int sourceHeight = -1;

	private Bloom() {
	}

	public static void flip() {
		if (storage != null) storage.clear();
	}

	/** threshold and knee are luminances (0 to 1); radius spreads the blur taps. */
	public static void render(float threshold, float knee, float intensity, float radius) {
		// Vanilla renders into other targets for special frames (panorama screenshots): leave those alone.
		if (RenderSystem.outputColorTextureOverride != null) return;
		Framebuffer fb = V1zir.mc().getFramebuffer();
		int w = fb.textureWidth;
		int h = fb.textureHeight;
		if (w <= 0 || h <= 0) return;
		ensureTextures(w, h);
		if (storage == null) storage = new DynamicUniformStorage<>("V1zir bloom UBO", SIZE, 8);

		pass(V1zirPipelines.BLOOM_BRIGHT, "BloomParams", fb.getColorAttachmentView(), views[0], widths[0], heights[0],
			0.5f / w, 0.5f / h, threshold, knee);
		for (int i = 1; i < LEVELS; i++) {
			pass(V1zirPipelines.BLUR_DOWN, "BlurParams", views[i - 1], views[i], widths[i], heights[i],
				1f / widths[i - 1], 1f / heights[i - 1], radius, 0f);
		}
		for (int i = LEVELS - 1; i > 0; i--) {
			pass(V1zirPipelines.BLUR_UP, "BlurParams", views[i], views[i - 1], widths[i - 1], heights[i - 1],
				1f / widths[i], 1f / heights[i], radius, 0f);
		}
		pass(V1zirPipelines.BLOOM_COMPOSITE, "BloomParams", views[0], fb.getColorAttachmentView(), w, h, intensity, 0f, 0f, 0f);
	}

	/** One full screen pass from source into target (whole target), its parameters as one vec4. */
	private static void pass(RenderPipeline pipeline, String uniform, GpuTextureView source, GpuTextureView target, int width, int height,
							 float x, float y, float z, float w) {
		DATA.set(x, y, z, w);
		GpuBufferSlice params = storage.write(DATA);
		try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "V1zir bloom", target, OptionalInt.empty())) {
			pass.setPipeline(pipeline);
			pass.bindTexture("Sampler0", source, RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
			pass.setUniform(uniform, params);
			pass.setVertexBuffer(0, Fullscreen.buffer());
			pass.draw(0, 3);
		}
	}

	private static void ensureTextures(int width, int height) {
		if (width == sourceWidth && height == sourceHeight && textures[0] != null) return;
		for (int i = 0; i < LEVELS; i++) {
			if (views[i] != null) views[i].close();
			if (textures[i] != null) textures[i].close();
			widths[i] = Math.max(1, width >> (i + 1));
			heights[i] = Math.max(1, height >> (i + 1));
			textures[i] = RenderSystem.getDevice().createTexture("V1zir bloom " + i, 15, TextureFormat.RGBA8, widths[i], heights[i], 1, 1);
			views[i] = RenderSystem.getDevice().createTextureView(textures[i]);
		}
		sourceWidth = width;
		sourceHeight = height;
	}

	/** One reused block: write() copies it into the buffer right away. */
	private static final class Data implements DynamicUniformStorage.Uploadable {
		private float x, y, z, w;

		void set(float x, float y, float z, float w) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.w = w;
		}

		@Override
		public void write(ByteBuffer buffer) {
			Std140Builder.intoBuffer(buffer).putVec4(x, y, z, w);
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
