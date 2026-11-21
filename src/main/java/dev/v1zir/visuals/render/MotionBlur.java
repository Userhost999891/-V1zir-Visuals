package dev.v1zir.visuals.render;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.systems.CommandEncoder;
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
 * Motion blur of the world (MotionBlurModule): the frame is copied, one full screen pass smears it
 * along the camera's motion and blends it with the previous result, and the result is kept for the
 * next frame. It runs right after the world is drawn, so the hand and the GUI stay sharp. While the
 * view is still nothing runs at all, and the kept result is dropped so no stale image ever shows.
 */
public final class MotionBlur {
	private static final int SIZE = new Std140SizeCalculator().putVec4().get();
	private static final Params PARAMS = new Params();
	private static DynamicUniformStorage<Params> storage;
	private static GpuTexture frame;
	private static GpuTextureView frameView;
	private static GpuTexture history;
	private static GpuTextureView historyView;
	private static int width = -1;
	private static int height = -1;
	private static boolean historyValid;

	private MotionBlur() {
	}

	public static void flip() {
		if (storage != null) storage.clear();
	}

	/** The view is still (or the blur is off): the next blur starts without a previous result. */
	public static void reset() {
		historyValid = false;
	}

	/** Blurs the frame along (motionX, motionY) in screen units per frame and blends it with the previous result by weight. */
	public static void render(float motionX, float motionY, float weight) {
		// Vanilla renders into other targets for special frames (panorama screenshots): leave those alone.
		if (RenderSystem.outputColorTextureOverride != null) return;
		Framebuffer fb = V1zir.mc().getFramebuffer();
		int w = fb.textureWidth;
		int h = fb.textureHeight;
		if (w <= 0 || h <= 0) return;
		ensureTextures(w, h);
		if (storage == null) storage = new DynamicUniformStorage<>("V1zir motion blur UBO", SIZE, 2);

		PARAMS.set(motionX, motionY, historyValid ? weight : 0f);
		GpuBufferSlice params = storage.write(PARAMS);
		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		encoder.copyTextureToTexture(fb.getColorAttachment(), frame, 0, 0, 0, 0, 0, w, h);
		try (RenderPass pass = encoder.createRenderPass(() -> "V1zir motion blur", fb.getColorAttachmentView(), OptionalInt.empty())) {
			pass.setPipeline(V1zirPipelines.MOTION_BLUR);
			pass.bindTexture("Sampler0", frameView, RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
			pass.bindTexture("Sampler1", historyView, RenderSystem.getSamplerCache().get(FilterMode.NEAREST));
			pass.setUniform("MotionParams", params);
			pass.setVertexBuffer(0, Fullscreen.buffer());
			pass.draw(0, 3);
		}
		encoder.copyTextureToTexture(fb.getColorAttachment(), history, 0, 0, 0, 0, 0, w, h);
		historyValid = true;
	}

	private static void ensureTextures(int w, int h) {
		if (w == width && h == height && frame != null) return;
		if (frameView != null) frameView.close();
		if (frame != null) frame.close();
		if (historyView != null) historyView.close();
		if (history != null) history.close();
		frame = RenderSystem.getDevice().createTexture("V1zir motion blur frame", 15, TextureFormat.RGBA8, w, h, 1, 1);
		frameView = RenderSystem.getDevice().createTextureView(frame);
		history = RenderSystem.getDevice().createTexture("V1zir motion blur history", 15, TextureFormat.RGBA8, w, h, 1, 1);
		historyView = RenderSystem.getDevice().createTextureView(history);
		width = w;
		height = h;
		historyValid = false;
	}

	/** One instance, refilled every frame: the storage copies it into the buffer on write. */
	private static final class Params implements DynamicUniformStorage.Uploadable {
		private float motionX, motionY, weight;

		void set(float motionX, float motionY, float weight) {
			this.motionX = motionX;
			this.motionY = motionY;
			this.weight = weight;
		}

		@Override
		public void write(ByteBuffer buffer) {
			Std140Builder.intoBuffer(buffer).putVec4(motionX, motionY, weight, 0f);
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
