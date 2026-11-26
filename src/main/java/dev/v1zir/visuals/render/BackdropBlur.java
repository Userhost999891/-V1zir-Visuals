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
 * Gaussian blur of the world behind the GUI: the frame is taken down to a quarter of its size in two smooth steps,
 * blurred there by a separable Gaussian (across, then down) whose width follows the theme's blur strength, and
 * brought back up to half size with a tent filter for the glass to sample. No level smaller than a quarter is used,
 * so nothing goes blocky or crawls as the view moves. GUI code only requests it; the passes run right before
 * vanilla's GuiRenderer draws (see GuiRendererMixin), so glass panels sample the freshly rendered frame. Glass
 * panels request the area they cover: every pass is then scissored to those areas plus the reach of the passes, so
 * small HUD panels do not blur the whole frame (texels outside the areas keep stale values no panel ever samples).
 */
public final class BackdropBlur {
	/** Half and quarter size; the Gaussian runs at the quarter, through the spare texture. */
	private static final int MAX_LEVELS = 2;
	private static final int BLUR_SIZE = new Std140SizeCalculator().putVec4().get();

	private static final GpuTexture[] textures = new GpuTexture[MAX_LEVELS];
	private static final GpuTextureView[] views = new GpuTextureView[MAX_LEVELS];
	private static final int[] widths = new int[MAX_LEVELS];
	private static final int[] heights = new int[MAX_LEVELS];
	private static GpuTexture spare;
	private static GpuTextureView spareView;
	private static TextureSetup setup;
	private static int sourceWidth = -1;
	private static int sourceHeight = -1;
	private static boolean requested;
	private static final int MAX_REGIONS = 16;
	/** Two regions become one when the box around both (with the passes' reach) is at most this much larger than the two. */
	private static final float MERGE_SLACK = 1.2f;
	/** Requested areas in framebuffer pixels with the origin at the bottom (x0, y0, x1, y1), unless fullFrame. */
	private static final int[] regions = new int[MAX_REGIONS * 4];
	private static int regionCount;
	private static boolean fullFrame;
	/** Margin in framebuffer pixels the passes must cover around a region (the reach of all of them). */
	private static int margin;
	private static DynamicUniformStorage<BlurData> storage;
	private static final BlurData BLUR = new BlurData();
	/** The frame as it is before the GUI, sharp and full size: what clear glass (the Glass style) shows through. */
	private static GpuTexture sharp;
	private static GpuTextureView sharpView;
	private static TextureSetup sharpSetup;
	private static int sharpWidth = -1;
	private static int sharpHeight = -1;
	private static boolean sharpRequested;
	/** Outside a world, the title screen's wallpaper, which clear glass reads as it is. */
	private static GpuTextureView wallpaperView;
	private static TextureSetup wallpaperSetup;

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
		int x0 = bounds.getLeft() * scale;
		int y0 = height - bounds.getBottom() * scale;
		int x1 = bounds.getRight() * scale;
		int y1 = height - bounds.getTop() * scale;
		if (regionCount == MAX_REGIONS) {
			// Full: the panel joins the region that grows least by it (never the whole frame for one panel more).
			int best = 0;
			long least = Long.MAX_VALUE;
			for (int i = 0; i < regionCount * 4; i += 4) {
				long grown = area(Math.min(regions[i], x0), Math.min(regions[i + 1], y0), Math.max(regions[i + 2], x1), Math.max(regions[i + 3], y1), 0)
					- area(regions[i], regions[i + 1], regions[i + 2], regions[i + 3], 0);
				if (grown < least) {
					least = grown;
					best = i;
				}
			}
			include(best, x0, y0, x1, y1);
			return;
		}
		int r = regionCount++ * 4;
		regions[r] = x0;
		regions[r + 1] = y0;
		regions[r + 2] = x1;
		regions[r + 3] = y1;
	}

	/** Grows the region at index r (into regions) to take the box in too. */
	private static void include(int r, int x0, int y0, int x1, int y1) {
		regions[r] = Math.min(regions[r], x0);
		regions[r + 1] = Math.min(regions[r + 1], y0);
		regions[r + 2] = Math.max(regions[r + 2], x1);
		regions[r + 3] = Math.max(regions[r + 3], y1);
	}

	/** The area of a box grown by margin on every side. */
	private static long area(int x0, int y0, int x1, int y1, int margin) {
		return (long) Math.max(0, x1 - x0 + 2 * margin) * Math.max(0, y1 - y0 + 2 * margin);
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
		if (spareView != null) spareView.close();
		if (spare != null) spare.close();
		spare = RenderSystem.getDevice().createTexture("V1zir blur spare", 15, TextureFormat.RGBA8, widths[1], heights[1], 1, 1);
		spareView = RenderSystem.getDevice().createTextureView(spare);
		sourceWidth = width;
		sourceHeight = height;
		setup = TextureSetup.of(views[0], RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
	}

	/** Asks for this frame's copy of what lies behind the GUI: a clear glass panel is being drawn. */
	public static void requestSharp() {
		sharpRequested = true;
	}

	/** What clear glass samples: the frame behind the GUI, sharp (outside a world, the title screen's wallpaper). */
	public static TextureSetup sharpSetup() {
		if (V1zir.mc().world == null) {
			WallpaperTarget wallpaper = WallpaperRenderer.backdropTarget();
			if (wallpaper != null) {
				GpuTextureView view = wallpaper.view();
				if (view != wallpaperView || wallpaperSetup == null) {
					wallpaperView = view;
					wallpaperSetup = TextureSetup.of(view, RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
				}
				return wallpaperSetup;
			}
		}
		Framebuffer fb = V1zir.mc().getFramebuffer();
		ensureSharp(fb.textureWidth, fb.textureHeight);
		return sharpSetup;
	}

	private static void ensureSharp(int width, int height) {
		width = Math.max(1, width);
		height = Math.max(1, height);
		if (width == sharpWidth && height == sharpHeight && sharpSetup != null) return;
		if (sharpView != null) sharpView.close();
		if (sharp != null) sharp.close();
		sharp = RenderSystem.getDevice().createTexture("V1zir glass backdrop", 15, TextureFormat.RGBA8, width, height, 1, 1);
		sharpView = RenderSystem.getDevice().createTextureView(sharp);
		sharpWidth = width;
		sharpHeight = height;
		sharpSetup = TextureSetup.of(sharpView, RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
	}

	/**
	 * Called right before the GUI is drawn: copies the frame for the clear glass drawn in it (one copy of the frame,
	 * cheaper than the blur). Outside a world the glass reads the wallpaper instead, which needs no copy.
	 */
	public static void copySharpIfRequested() {
		if (!sharpRequested) return;
		sharpRequested = false;
		if (V1zir.mc().world == null || RenderSystem.outputColorTextureOverride != null) return;
		Framebuffer fb = V1zir.mc().getFramebuffer();
		if (fb.textureWidth <= 0 || fb.textureHeight <= 0) return;
		ensureSharp(fb.textureWidth, fb.textureHeight);
		RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(fb.getColorAttachment(), sharp, 0, 0, 0, 0, 0,
			fb.textureWidth, fb.textureHeight);
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
		// The Gaussian's width in quarter size texels: about 6 to 34 pixels of the frame.
		float sigma = 1.5f + strength * 7f;

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
		if (scissored) prepareRegions(sigma);
		// Down to half, then to a quarter, each a smooth five tap step.
		pass(V1zirPipelines.BLUR_DOWN, source, 1f / sw, 1f / sh, 1f, views[0], widths[0], heights[0], 2, scissored);
		pass(V1zirPipelines.BLUR_DOWN, views[0], 1f / widths[0], 1f / heights[0], 1f, views[1], widths[1], heights[1], 4, scissored);
		// The Gaussian at a quarter: across into the spare texture, then down back into the quarter.
		pass(V1zirPipelines.BLUR_GAUSS, views[1], 1f / widths[1], 0f, sigma, spareView, widths[1], heights[1], 4, scissored);
		pass(V1zirPipelines.BLUR_GAUSS, spareView, 0f, 1f / heights[1], sigma, views[1], widths[1], heights[1], 4, scissored);
		// Up to half size for the glass, with a tent filter.
		pass(V1zirPipelines.BLUR_UP, views[1], 1f / widths[1], 1f / heights[1], 1f, views[0], widths[0], heights[0], 2, scissored);
		fullFrame = false;
		regionCount = 0;
	}

	/**
	 * The passes' reach and merged regions. Around each panel every pass must be valid as far as all the later passes
	 * read: the Gaussian three sigmas of a quarter texel each way, the steps down and up a few texels. Regions whose
	 * reach overlaps become one (no area is blurred twice), and so do regions close enough that the box around both
	 * is hardly larger; panels far apart (a HUD's corners) stay apart.
	 */
	private static void prepareRegions(float sigma) {
		margin = (int) Math.ceil((sigma * 3f + 4f) * 4f) + 16;
		int reach = margin;
		for (boolean merged = true; merged; ) {
			merged = false;
			for (int a = 0; a < regionCount && !merged; a++) {
				for (int b = a + 1; b < regionCount; b++) {
					int ra = a * 4;
					int rb = b * 4;
					boolean overlap = !(regions[ra] - reach > regions[rb + 2] + reach || regions[rb] - reach > regions[ra + 2] + reach
						|| regions[ra + 1] - reach > regions[rb + 3] + reach || regions[rb + 1] - reach > regions[ra + 3] + reach);
					if (!overlap) {
						long both = area(Math.min(regions[ra], regions[rb]), Math.min(regions[ra + 1], regions[rb + 1]),
							Math.max(regions[ra + 2], regions[rb + 2]), Math.max(regions[ra + 3], regions[rb + 3]), reach);
						long apart = area(regions[ra], regions[ra + 1], regions[ra + 2], regions[ra + 3], reach)
							+ area(regions[rb], regions[rb + 1], regions[rb + 2], regions[rb + 3], reach);
						if (both > apart * MERGE_SLACK) continue;
					}
					include(ra, regions[rb], regions[rb + 1], regions[rb + 2], regions[rb + 3]);
					regionCount--;
					System.arraycopy(regions, regionCount * 4, regions, rb, 4);
					merged = true;
					break;
				}
			}
		}
	}

	/**
	 * One blur pass from source into target (texel: frame pixels per target texel), over the whole target or (scissored)
	 * only around the requested regions: one render pass, one draw per region.
	 */
	private static void pass(RenderPipeline pipeline, GpuTextureView source, float stepX, float stepY, float param, GpuTextureView target,
							 int targetW, int targetH, int texel, boolean scissored) {
		BLUR.set(stepX, stepY, param);
		GpuBufferSlice params = storage.write(BLUR);
		try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "V1zir backdrop blur", target, OptionalInt.empty())) {
			pass.setPipeline(pipeline);
			pass.bindTexture("Sampler0", source, RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
			pass.setUniform("BlurParams", params);
			pass.setVertexBuffer(0, Fullscreen.buffer());
			if (!scissored) {
				pass.draw(0, 3);
				return;
			}
			for (int r = 0; r < regionCount * 4; r += 4) {
				int x0 = Math.max(0, Math.floorDiv(regions[r] - margin, texel));
				int y0 = Math.max(0, Math.floorDiv(regions[r + 1] - margin, texel));
				int x1 = Math.min(targetW, -Math.floorDiv(-(regions[r + 2] + margin), texel));
				int y1 = Math.min(targetH, -Math.floorDiv(-(regions[r + 3] + margin), texel));
				if (x1 <= x0 || y1 <= y0) continue;
				pass.enableScissor(x0, y0, x1 - x0, y1 - y0);
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
