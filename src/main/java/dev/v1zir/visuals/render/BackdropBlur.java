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
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.impl.client.ThemeModule;
import dev.v1zir.visuals.render.sky.MenuSky;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.gl.DynamicUniformStorage;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.texture.TextureSetup;

import java.nio.ByteBuffer;
import java.util.OptionalInt;

/**
 * Dual Kawase blur of the world behind the GUI. GUI code only requests it; the passes run
 * right before vanilla's GuiRenderer draws (see GuiRendererMixin), so glass panels sample the
 * freshly rendered frame.
 */
public final class BackdropBlur {
	private static final int MAX_LEVELS = 5;
	private static final int BLUR_SIZE = new Std140SizeCalculator().putVec4().get();

	private static final GpuTexture[] textures = new GpuTexture[MAX_LEVELS];
	private static final GpuTextureView[] views = new GpuTextureView[MAX_LEVELS];
	private static final int[] widths = new int[MAX_LEVELS];
	private static final int[] heights = new int[MAX_LEVELS];
	private static TextureSetup setup;
	private static int sourceWidth = -1;
	private static int sourceHeight = -1;
	private static boolean requested;
	private static DynamicUniformStorage<BlurData> storage;

	private BackdropBlur() {
	}

	/** Blur needs something behind the GUI: the world, or outside a world the menu sky. */
	public static boolean available() {
		if (Theme.style() == Theme.Style.NORMAL) return false;
		return V1zir.mc().world != null || MenuSky.renderedThisFrame();
	}

	public static void request() {
		requested = true;
	}

	public static void flip() {
		if (storage != null) storage.clear();
	}

	public static TextureSetup textureSetup() {
		Framebuffer fb = V1zir.mc().getFramebuffer();
		ensureTextures(fb.textureWidth, fb.textureHeight);
		return setup;
	}

	private static void ensureTextures(int width, int height) {
		if (width == sourceWidth && height == sourceHeight && setup != null) return;
		for (int i = 0; i < MAX_LEVELS; i++) {
			if (views[i] != null) views[i].close();
			if (textures[i] != null) textures[i].close();
			widths[i] = Math.max(1, width >> (i + 1));
			heights[i] = Math.max(1, height >> (i + 1));
			textures[i] = RenderSystem.getDevice().createTexture("V1zir blur " + i, 15, TextureFormat.RGBA8, widths[i], heights[i], 1, 1);
			views[i] = RenderSystem.getDevice().createTextureView(textures[i]);
		}
		sourceWidth = width;
		sourceHeight = height;
		setup = TextureSetup.of(views[0], RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
	}

	/** Called right before the GUI is drawn. */
	public static void runIfRequested() {
		if (!requested) return;
		requested = false;
		Framebuffer fb = V1zir.mc().getFramebuffer();
		ensureTextures(fb.textureWidth, fb.textureHeight);
		if (storage == null) storage = new DynamicUniformStorage<>("V1zir blur UBO", BLUR_SIZE, 16);

		ThemeModule theme = ModuleManager.get(ThemeModule.class);
		float strength = theme != null ? theme.blurStrength.f() : 0.6f;
		int levels = Math.max(2, Math.min(MAX_LEVELS, 2 + Math.round(strength * 3f)));
		float offset = 1.0f + strength * 1.5f;

		GpuTextureView source = fb.getColorAttachmentView();
		int sw = fb.textureWidth;
		int sh = fb.textureHeight;
		if (V1zir.mc().world == null && MenuSky.renderedThisFrame()) {
			// Outside a world the GUI draws over the menu sky, which lives in its own texture.
			source = MenuSky.view();
			sw = MenuSky.width();
			sh = MenuSky.height();
		}
		for (int i = 0; i < levels; i++) {
			pass(V1zirPipelines.BLUR_DOWN, source, sw, sh, views[i], offset);
			source = views[i];
			sw = widths[i];
			sh = heights[i];
		}
		for (int i = levels - 1; i > 0; i--) {
			pass(V1zirPipelines.BLUR_UP, views[i], widths[i], heights[i], views[i - 1], offset);
		}
	}

	private static void pass(RenderPipeline pipeline, GpuTextureView source, int sourceW, int sourceH, GpuTextureView target, float offset) {
		GpuBufferSlice params = storage.write(new BlurData(1f / sourceW, 1f / sourceH, offset));
		try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "V1zir backdrop blur", target, OptionalInt.empty())) {
			pass.setPipeline(pipeline);
			pass.bindTexture("Sampler0", source, RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
			pass.setUniform("BlurParams", params);
			pass.setVertexBuffer(0, Fullscreen.buffer());
			pass.draw(0, 3);
		}
	}

	private record BlurData(float texelX, float texelY, float offset) implements DynamicUniformStorage.Uploadable {
		@Override
		public void write(ByteBuffer buffer) {
			Std140Builder.intoBuffer(buffer).putVec4(texelX, texelY, offset, 0f);
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
