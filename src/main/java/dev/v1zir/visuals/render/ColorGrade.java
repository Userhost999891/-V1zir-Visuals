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
 * Full screen colour grading of the world (ColorGradingModule). The frame is copied once and a
 * single pass reads one texel of the copy per pixel and writes the graded colour back. It runs at
 * the end of renderWorld (GameRendererMixin), so the GUI drawn afterwards is never graded.
 */
public final class ColorGrade {
	private static final int SIZE = new Std140SizeCalculator().putVec4().putVec4().putVec4().putVec4().putVec4().putVec4().get();
	private static final Params PARAMS = new Params();
	private static DynamicUniformStorage<Params> storage;
	private static GpuTexture copy;
	private static GpuTextureView copyView;
	private static int width = -1;
	private static int height = -1;

	private ColorGrade() {
	}

	public static void flip() {
		if (storage != null) storage.clear();
	}

	/**
	 * Grades the frame. Gains are the per channel multipliers (white balance and brightness),
	 * vignette 0 turns the vignette off. A look recolours the picture by its brightness in a gradient
	 * from the {@code shadow} through the {@code mid} to the {@code light} colour (RGB), {@code look}
	 * of it (0 for none).
	 */
	public static void render(float red, float green, float blue, float contrast, float saturation, float vibrance, float vignette,
							  int shadow, int mid, int light, float look) {
		// Vanilla renders into other targets for special frames (panorama screenshots): leave those alone.
		if (RenderSystem.outputColorTextureOverride != null) return;
		Framebuffer fb = V1zir.mc().getFramebuffer();
		int w = fb.textureWidth;
		int h = fb.textureHeight;
		if (w <= 0 || h <= 0) return;
		ensureCopy(w, h);
		if (storage == null) storage = new DynamicUniformStorage<>("V1zir color grading UBO", SIZE, 2);

		PARAMS.set(red, green, blue, contrast, saturation, vibrance, vignette, w, h);
		PARAMS.look(shadow, mid, light, look);
		GpuBufferSlice params = storage.write(PARAMS);
		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		encoder.copyTextureToTexture(fb.getColorAttachment(), copy, 0, 0, 0, 0, 0, w, h);
		try (RenderPass pass = encoder.createRenderPass(() -> "V1zir color grading", fb.getColorAttachmentView(), OptionalInt.empty())) {
			pass.setPipeline(V1zirPipelines.COLOR_GRADE);
			pass.bindTexture("Sampler0", copyView, RenderSystem.getSamplerCache().get(FilterMode.NEAREST));
			pass.setUniform("GradeParams", params);
			pass.setVertexBuffer(0, Fullscreen.buffer());
			pass.draw(0, 3);
		}
	}

	private static void ensureCopy(int w, int h) {
		if (w == width && h == height && copy != null) return;
		if (copyView != null) copyView.close();
		if (copy != null) copy.close();
		copy = RenderSystem.getDevice().createTexture("V1zir color grading source", 15, TextureFormat.RGBA8, w, h, 1, 1);
		copyView = RenderSystem.getDevice().createTextureView(copy);
		width = w;
		height = h;
	}

	/** One instance, refilled every frame: the storage copies it into the buffer on write. */
	private static final class Params implements DynamicUniformStorage.Uploadable {
		private float red, green, blue, contrast, saturation, vibrance, vignette, look;
		private int width, height, shadow, mid, light;

		void set(float red, float green, float blue, float contrast, float saturation, float vibrance, float vignette, int width, int height) {
			this.red = red;
			this.green = green;
			this.blue = blue;
			this.contrast = contrast;
			this.saturation = saturation;
			this.vibrance = vibrance;
			this.vignette = vignette;
			this.width = width;
			this.height = height;
		}

		void look(int shadow, int mid, int light, float look) {
			this.shadow = shadow;
			this.mid = mid;
			this.light = light;
			this.look = look;
		}

		private static float channel(int rgb, int shift) {
			return (rgb >> shift & 0xFF) / 255f;
		}

		@Override
		public void write(ByteBuffer buffer) {
			float aspect = width / (float) height;
			float corner = (float) Math.sqrt(aspect * aspect * 0.25f + 0.25f);
			Std140Builder.intoBuffer(buffer)
				.putVec4(red, green, blue, 1f)
				.putVec4(contrast, saturation, vibrance, vignette)
				.putVec4(1f / width, 1f / height, aspect, 1f / corner)
				.putVec4(channel(shadow, 16), channel(shadow, 8), channel(shadow, 0), 1f)
				.putVec4(channel(mid, 16), channel(mid, 8), channel(mid, 0), 1f)
				.putVec4(channel(light, 16), channel(light, 8), channel(light, 0), look);
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
