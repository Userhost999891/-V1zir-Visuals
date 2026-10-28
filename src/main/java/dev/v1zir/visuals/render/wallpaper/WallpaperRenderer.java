package dev.v1zir.visuals.render.wallpaper;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.v1zir.visuals.V1zir;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.impl.client.MainMenuModule;
import dev.v1zir.visuals.render.Fullscreen;
import dev.v1zir.visuals.render.V1zirPipelines;
import dev.v1zir.visuals.render.sky.MenuSky;
import dev.v1zir.visuals.render.sky.SkyUniforms;
import net.minecraft.client.gl.DynamicUniformStorage;
import net.minecraft.client.gl.Framebuffer;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/**
 * Renders title screen wallpapers into their targets. Screens queue what each target shows while they build the GUI;
 * the passes run right before the GUI is drawn (GuiRendererMixin), and the menu background elements then upsample
 * the targets. Every wallpaper shares one animation clock, so a card in the picker matches the screen behind it.
 */
public final class WallpaperRenderer {
	/** Full screen targets of the two screens; both render only while the lock screen slides. */
	public static final WallpaperTarget LOCK = new WallpaperTarget("V1zir lock wallpaper");
	public static final WallpaperTarget HOME = new WallpaperTarget("V1zir home wallpaper");

	private static final int SIZE = new Std140SizeCalculator().putVec4().putVec4().putVec4().putVec4().get();
	private static final int MAX_JOBS = 24;
	/** Blur sigma at 100%, as a fraction of the target height. */
	private static final float MAX_BLUR = 0.02f;

	private static final Job[] JOBS = new Job[MAX_JOBS];
	private static final Params PARAMS = new Params();
	private static final SkyUniforms.Data SKY = new SkyUniforms.Data();
	/** Textures and pictures to free once the frame that may still draw them is finished. */
	private static final List<Runnable> RETIRED = new ArrayList<>();
	private static DynamicUniformStorage<Params> storage;
	private static int jobCount;
	private static float time;
	private static long last;
	private static int frame;
	private static int advancedFrame = -1;
	@Nullable
	private static WallpaperTarget backdrop;
	private static int backdropFrame = -1;

	static {
		for (int i = 0; i < MAX_JOBS; i++) JOBS[i] = new Job();
	}

	private WallpaperRenderer() {
	}

	/** Advances the shared animation clock by the real time since the last frame; only the first call of a frame counts. */
	public static void advance(float speed) {
		if (advancedFrame == frame) return;
		advancedFrame = frame;
		long now = System.nanoTime();
		if (last != 0L) time += Math.min((now - last) / 1.0e9f, 0.25f) * speed;
		last = now;
	}

	/** Resolution scale of a wallpaper covering the screen: the menu's render scale, full for a sharp picture. */
	public static float scale(Wallpaper wallpaper, WallpaperStyle style) {
		if (wallpaper.kind == Wallpaper.Kind.IMAGE && style.blur <= 0f) return 1f;
		MainMenuModule menu = ModuleManager.get(MainMenuModule.class);
		return menu != null ? menu.renderScale.f() : 0.5f;
	}

	/** Queues a screen target to show the wallpaper over the whole framebuffer this frame. */
	public static boolean queueScreen(WallpaperTarget target, Wallpaper wallpaper, WallpaperStyle style, float parallaxX, float parallaxY) {
		Framebuffer fb = V1zir.mc().getFramebuffer();
		float scale = scale(wallpaper, style);
		return queue(target, wallpaper, style, Math.round(fb.textureWidth * scale), Math.round(fb.textureHeight * scale),
			fb.textureWidth / (float) Math.max(fb.textureHeight, 1), parallaxX, parallaxY, false);
	}

	/**
	 * Queues the target to show the wallpaper this frame at the given size in pixels and aspect ratio (that of the
	 * screen, also for cards). small picks the small copy of a picture. Returns false while there is nothing to show
	 * yet (a picture still loading, or one that could not be read).
	 */
	public static boolean queue(WallpaperTarget target, Wallpaper wallpaper, WallpaperStyle style, int width, int height,
								float aspect, float parallaxX, float parallaxY, boolean small) {
		if (jobCount == MAX_JOBS) return target.ready();
		WallpaperImages.Picture picture = null;
		if (wallpaper.kind == Wallpaper.Kind.IMAGE) {
			picture = WallpaperImages.get(wallpaper, small);
			if (picture == null || !picture.ready()) return false;
		}
		target.ensure(width, height);
		if (storage == null) storage = new DynamicUniformStorage<>("V1zir wallpaper UBO", SIZE, 8);
		Job job = JOBS[jobCount++];
		job.target = target;
		job.image = null;
		switch (wallpaper.kind) {
			case SKY -> {
				MenuSky.fill(SKY, wallpaper.preset, aspect, parallaxX, parallaxY, time);
				job.pipeline = V1zirPipelines.SKY;
				job.uniform = "SkyParams";
				job.params = SkyUniforms.write(SKY);
			}
			case SHADER -> {
				PARAMS.set(time, aspect, wallpaper.shader, 0f, parallaxX, parallaxY, width, height);
				job.pipeline = V1zirPipelines.WALLPAPER;
				job.uniform = "WallpaperParams";
				job.params = storage.write(PARAMS);
			}
			case IMAGE -> {
				PARAMS.set(time, aspect, 0f, style.fit ? 1f : 0f, parallaxX, parallaxY, width, height);
				fitPicture(picture.aspect(), aspect, style.motion);
				job.pipeline = V1zirPipelines.WALLPAPER_IMAGE;
				job.uniform = "WallpaperParams";
				job.params = storage.write(PARAMS);
				job.image = picture.view();
			}
		}
		float sigma = style.blur * MAX_BLUR * height;
		job.blurred = sigma >= 0.35f;
		if (job.blurred) {
			PARAMS.set(0f, aspect, sigma, 0f, 0f, 0f, width, height);
			PARAMS.cover(1f / width, 0f, 0f, 0f);
			job.blurX = storage.write(PARAMS);
			PARAMS.cover(0f, 1f / height, 0f, 0f);
			job.blurY = storage.write(PARAMS);
		}
		return true;
	}

	/**
	 * Cover and contain transforms of a picture with the given aspect on a target with another one, plus the Ken
	 * Burns drift: a zoom breathing between 6% and 12% and a pan that never reaches the picture's edges.
	 */
	private static void fitPicture(float picture, float screen, boolean motion) {
		float coverX = picture > screen ? screen / picture : 1f;
		float coverY = picture > screen ? 1f : picture / screen;
		float containX = picture > screen ? 1f : screen / picture;
		float containY = picture > screen ? picture / screen : 1f;
		float zoom = 1f;
		float panX = 0f;
		float panY = 0f;
		if (motion) {
			zoom = 1f / (1.09f + 0.03f * (float) Math.sin(time * 0.045f));
			float room = (1f - zoom) * 0.5f * 0.85f;
			panX = room * (float) Math.sin(time * 0.031f + 1.3f);
			panY = room * (float) Math.cos(time * 0.023f);
		}
		PARAMS.cover(coverX * zoom, coverY * zoom, (1f - coverX * zoom) * 0.5f + panX * coverX, (1f - coverY * zoom) * 0.5f + panY * coverY);
		PARAMS.contain(containX * zoom, containY * zoom, (1f - containX * zoom) * 0.5f + panX * containX, (1f - containY * zoom) * 0.5f + panY * containY);
	}

	/** Glass panels drawn this frame blur this target (the screen behind them). */
	public static void backdrop(WallpaperTarget target) {
		backdrop = target;
		backdropFrame = frame;
	}

	/** Whether a backdrop for glass panels was rendered this frame. */
	public static boolean backdropReady() {
		return backdropFrame == frame && backdrop != null && backdrop.ready();
	}

	@Nullable
	public static WallpaperTarget backdropTarget() {
		return backdropReady() ? backdrop : null;
	}

	/** Frees something (a texture) after the current frame was drawn. */
	public static void retire(Runnable release) {
		RETIRED.add(release);
	}

	/** Renders every wallpaper queued this frame. */
	public static void renderPending() {
		for (int i = 0; i < jobCount; i++) {
			Job job = JOBS[i];
			WallpaperTarget target = job.target;
			try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(target.label, target.view(), OptionalInt.empty())) {
				pass.setPipeline(job.pipeline);
				pass.setUniform(job.uniform, job.params);
				if (job.image != null) pass.bindTexture("Sampler0", job.image, RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
				pass.setVertexBuffer(0, Fullscreen.buffer());
				pass.draw(0, 3);
			}
			if (job.blurred) {
				GpuTextureView scratch = target.scratch();
				blur(target, target.view(), scratch, job.blurX);
				blur(target, scratch, target.view(), job.blurY);
			} else {
				target.dropScratch();
			}
			job.target = null;
			job.image = null;
		}
		jobCount = 0;
	}

	private static void blur(WallpaperTarget target, GpuTextureView source, GpuTextureView destination, GpuBufferSlice params) {
		try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(target.label, destination, OptionalInt.empty())) {
			pass.setPipeline(V1zirPipelines.WALLPAPER_BLUR);
			pass.setUniform("WallpaperParams", params);
			pass.bindTexture("Sampler0", source, RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
			pass.setVertexBuffer(0, Fullscreen.buffer());
			pass.draw(0, 3);
		}
	}

	/** Called once per frame (RenderSystem.flipFrame), after everything was drawn. */
	public static void flip() {
		frame++;
		if (storage != null) storage.clear();
		for (int i = 0; i < RETIRED.size(); i++) RETIRED.get(i).run();
		RETIRED.clear();
		WallpaperImages.tick();
	}

	private static final class Job {
		WallpaperTarget target;
		RenderPipeline pipeline;
		String uniform;
		GpuBufferSlice params;
		@Nullable
		GpuTextureView image;
		boolean blurred;
		GpuBufferSlice blurX;
		GpuBufferSlice blurY;
	}

	/** The WallpaperParams block (wallpaper_params.glsl); write() copies it into the buffer right away. */
	private static final class Params implements DynamicUniformStorage.Uploadable {
		private final float[] values = new float[16];

		void set(float time, float aspect, float index, float fit, float parallaxX, float parallaxY, float width, float height) {
			values[0] = time;
			values[1] = aspect;
			values[2] = index;
			values[3] = fit;
			values[4] = parallaxX;
			values[5] = parallaxY;
			values[6] = width;
			values[7] = height;
			cover(0f, 0f, 0f, 0f);
			contain(0f, 0f, 0f, 0f);
		}

		void cover(float x, float y, float z, float w) {
			values[8] = x;
			values[9] = y;
			values[10] = z;
			values[11] = w;
		}

		void contain(float x, float y, float z, float w) {
			values[12] = x;
			values[13] = y;
			values[14] = z;
			values[15] = w;
		}

		@Override
		public void write(ByteBuffer buffer) {
			Std140Builder.intoBuffer(buffer)
				.putVec4(values[0], values[1], values[2], values[3])
				.putVec4(values[4], values[5], values[6], values[7])
				.putVec4(values[8], values[9], values[10], values[11])
				.putVec4(values[12], values[13], values[14], values[15]);
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
