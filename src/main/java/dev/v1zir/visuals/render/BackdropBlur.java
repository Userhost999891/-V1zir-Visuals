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
import dev.v1zir.visuals.render.wallpaper.WallpaperRenderer;
import dev.v1zir.visuals.render.wallpaper.WallpaperTarget;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.gl.DynamicUniformStorage;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.util.Window;
import net.minecraft.client.texture.TextureSetup;

import java.nio.ByteBuffer;
import java.util.OptionalInt;

/**
 * Dual Kawase blur of the world behind the GUI. GUI code only requests it; the passes run
 * right before vanilla's GuiRenderer draws (see GuiRendererMixin), so glass panels sample the
 * freshly rendered frame. Glass panels request the area they cover: every pass is then scissored
 * to those areas plus the reach of the remaining passes, so small HUD panels do not blur the whole
 * frame (texels outside the areas keep stale values no panel ever samples).
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
	private static final int MAX_REGIONS = 8;
	/** Requested areas in framebuffer pixels with the origin at the bottom (x0, y0, x1, y1), unless fullFrame. */
	private static final int[] regions = new int[MAX_REGIONS * 4];
	private static int regionCount;
	private static boolean fullFrame;
	/** Margin in framebuffer pixels each level's passes must cover around a region. */
	private static final int[] margins = new int[MAX_LEVELS];
	private static DynamicUniformStorage<BlurData> storage;
	private static final BlurData BLUR = new BlurData();

	private BackdropBlur() {
	}

	/** Blur needs something behind the GUI: the world, or outside a world the title screen's wallpaper. */
	public static boolean available() {
		if (Theme.style() == Theme.Style.NORMAL) return false;
		return V1zir.mc().world != null || WallpaperRenderer.backdropReady();
	}

	/** Requests the blur of the whole frame (the click GUI's transitions). */
	public static void request() {
		requested = true;
		fullFrame = true;
	}

	/** Requests the blur behind a glass panel with these GUI bounds. */
	public static void request(ScreenRect bounds) {
		requested = true;
		if (fullFrame) return;
		if (regionCount == MAX_REGIONS) {
			fullFrame = true;
			return;
		}
		Window window = V1zir.mc().getWindow();
		int scale = window.getScaleFactor();
		int height = window.getFramebufferHeight();
		int r = regionCount++ * 4;
		regions[r] = bounds.getLeft() * scale;
		regions[r + 1] = height - bounds.getBottom() * scale;
		regions[r + 2] = bounds.getRight() * scale;
		regions[r + 3] = height - bounds.getTop() * scale;
	}

	public static void flip() {
		if (storage != null) storage.clear();
	}

	/** The blurred world of this frame (largest level), or null before the blur ever ran. */
	public static GpuTextureView view() {
		return views[0];
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
		WallpaperTarget wallpaper = V1zir.mc().world == null ? WallpaperRenderer.backdropTarget() : null;
		if (wallpaper != null) {
			// Outside a world the GUI draws over a wallpaper, which lives in its own texture.
			source = wallpaper.view();
			sw = wallpaper.width();
			sh = wallpaper.height();
		}
		boolean scissored = !fullFrame && regionCount > 0;
		if (scissored) prepareRegions(levels, offset);
		for (int i = 0; i < levels; i++) {
			pass(V1zirPipelines.BLUR_DOWN, source, sw, sh, i, offset, scissored);
			source = views[i];
			sw = widths[i];
			sh = heights[i];
		}
		for (int i = levels - 1; i > 0; i--) {
			pass(V1zirPipelines.BLUR_UP, views[i], widths[i], heights[i], i - 1, offset, scissored);
		}
		fullFrame = false;
		regionCount = 0;
	}

	/**
	 * Margins per level and merged regions. An up pass reads up to offset + 1 texels of its source level
	 * around each target texel, a down pass 0.5 * offset + 1; walking back from the final level gives how far
	 * around a panel every level must be valid.
	 */
	private static void prepareRegions(int levels, float offset) {
		int up = (int) Math.ceil(offset + 1f);
		int down = (int) Math.ceil(offset * 0.5f + 1f);
		int margin = 2;
		for (int j = 1; j < levels; j++) margin += up << (j + 1);
		margins[levels - 1] = margin;
		for (int i = levels - 2; i >= 0; i--) margins[i] = margins[i + 1] + (down << (i + 1));
		// Merge regions whose widest reach overlaps, so no area is blurred twice.
		int reach = margins[0];
		for (boolean merged = true; merged; ) {
			merged = false;
			for (int a = 0; a < regionCount && !merged; a++) {
				for (int b = a + 1; b < regionCount; b++) {
					int ra = a * 4;
					int rb = b * 4;
					if (regions[ra] - reach > regions[rb + 2] + reach || regions[rb] - reach > regions[ra + 2] + reach
						|| regions[ra + 1] - reach > regions[rb + 3] + reach || regions[rb + 1] - reach > regions[ra + 3] + reach) continue;
					regions[ra] = Math.min(regions[ra], regions[rb]);
					regions[ra + 1] = Math.min(regions[ra + 1], regions[rb + 1]);
					regions[ra + 2] = Math.max(regions[ra + 2], regions[rb + 2]);
					regions[ra + 3] = Math.max(regions[ra + 3], regions[rb + 3]);
					regionCount--;
					System.arraycopy(regions, regionCount * 4, regions, rb, 4);
					merged = true;
					break;
				}
			}
		}
	}

	/** One blur pass into the given level, over the whole level or (scissored) only around the requested regions. */
	private static void pass(RenderPipeline pipeline, GpuTextureView source, int sourceW, int sourceH, int level, float offset, boolean scissored) {
		BLUR.set(1f / sourceW, 1f / sourceH, offset);
		GpuBufferSlice params = storage.write(BLUR);
		int texel = 2 << level;
		int margin = margins[level];
		for (int r = 0; r < (scissored ? regionCount * 4 : 1); r += 4) {
			int x0 = 0;
			int y0 = 0;
			int x1 = widths[level];
			int y1 = heights[level];
			if (scissored) {
				x0 = Math.max(x0, Math.floorDiv(regions[r] - margin, texel));
				y0 = Math.max(y0, Math.floorDiv(regions[r + 1] - margin, texel));
				x1 = Math.min(x1, -Math.floorDiv(-(regions[r + 2] + margin), texel));
				y1 = Math.min(y1, -Math.floorDiv(-(regions[r + 3] + margin), texel));
				if (x1 <= x0 || y1 <= y0) continue;
			}
			try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "V1zir backdrop blur", views[level], OptionalInt.empty())) {
				if (scissored) pass.enableScissor(x0, y0, x1 - x0, y1 - y0);
				pass.setPipeline(pipeline);
				pass.bindTexture("Sampler0", source, RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
				pass.setUniform("BlurParams", params);
				pass.setVertexBuffer(0, Fullscreen.buffer());
				pass.draw(0, 3);
			}
		}
	}

	/** One reused block: write() copies it into the buffer right away. */
	private static final class BlurData implements DynamicUniformStorage.Uploadable {
		private float texelX;
		private float texelY;
		private float offset;

		void set(float texelX, float texelY, float offset) {
			this.texelX = texelX;
			this.texelY = texelY;
			this.offset = offset;
		}

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
