package dev.v1zir.visuals.gui.clickgui;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.v1zir.visuals.V1zir;
import dev.v1zir.visuals.module.impl.client.ClickGuiModule;
import dev.v1zir.visuals.render.BackdropBlur;
import dev.v1zir.visuals.render.Fullscreen;
import dev.v1zir.visuals.render.GuiCapture;
import dev.v1zir.visuals.render.V1zirPipelines;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.Anim;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.DynamicUniformStorage;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.Window;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.OptionalInt;

/**
 * Open and close animations of the click GUI window. While one plays, the window is drawn into
 * {@link GuiCapture} and composited after the frame's GUI (GuiRendererMixin calls {@link #afterGui}):
 * <ul>
 *   <li>3D open: the window swings in from a backward tilt and depth, settling on a spring.</li>
 *   <li>Fade out and 3D Frost (the glass cracks from the cursor, splits into shards that tumble and fall in
 *   perspective). These play after the screen has closed, from a snapshot of its last frame, so the game
 *   takes input again at once.</li>
 * </ul>
 * Nothing runs while no animation plays; a failure disables the animations for the session instead of the GUI.
 */
public final class WindowTransition {
	private static final int OPEN = 1;
	private static final int FADE = 2;
	private static final int SHATTER = 3;
	private static final int MODE_WARP = 0;
	private static final int MODE_FADE = 1;
	private static final int MODE_BACKDROP = 2;
	/** Seconds the cracks take to run across the window before the shards let go. */
	private static final float CRACK_TIME = 0.13f;
	private static final float SHATTER_TIME = 1.6f;
	private static final int UBO_SIZE = new Std140SizeCalculator().putVec4().putVec4().putVec4().putVec4().putVec4().putVec4().get();
	private static final Params PARAMS = new Params();
	private static final ShardMesh SHARDS = new ShardMesh();
	private static final float[] RECT = new float[4];

	private static int kind;
	private static boolean broken;
	private static Anim progress;
	private static float speed = 1f;
	private static long startedAt;
	@Nullable
	private static ClickGuiScreen closing;
	private static boolean captured;
	private static boolean drawnThisFrame;
	private static int mouseX;
	private static int mouseY;
	private static float backdropDim;
	private static boolean backdropBlur;
	private static DynamicUniformStorage<Params> storage;

	private WindowTransition() {
	}

	public static void flip() {
		if (storage != null) storage.clear();
	}

	/** Starts the 3D open; the screen then draws its window through {@link #openTarget}. */
	static void open(float animationSpeed) {
		if (broken) return;
		finish();
		kind = OPEN;
		speed = animationSpeed;
		progress = new Anim(0f, 6.5f * speed);
	}

	/** Where the opening screen draws its window this frame, or null to draw it directly. */
	@Nullable
	static DrawContext openTarget(int mx, int my) {
		if (kind != OPEN || broken) return null;
		try {
			DrawContext ctx = GuiCapture.begin(mx, my);
			drawnThisFrame = true;
			return ctx;
		} catch (RuntimeException | LinkageError e) {
			fail(e);
			return null;
		}
	}

	/**
	 * Plays a close animation of the screen that is closing now. dim is the backdrop's darkening (0 when the
	 * GUI did not dim the world), blur whether the backdrop was the blurred world.
	 */
	static void close(ClickGuiScreen screen, String animation, int mx, int my, float animationSpeed, float dim, boolean blur) {
		if (broken) return;
		finish();
		kind = ClickGuiModule.CLOSE_SHATTER.equals(animation) ? SHATTER : FADE;
		closing = screen;
		captured = false;
		speed = animationSpeed;
		mouseX = mx;
		mouseY = my;
		backdropDim = dim;
		backdropBlur = blur;
		startedAt = System.nanoTime();
		progress = new Anim(0f, 9f * speed);
	}

	/** Stops whatever plays (a new GUI opening, or the end of an animation). */
	static void finish() {
		kind = 0;
		closing = null;
		drawnThisFrame = false;
		SHARDS.close();
	}

	/** Close animations show the blurred world behind the window, so the blur keeps running while they play. */
	public static boolean wantsBlur() {
		return kind > OPEN && (backdropBlur || !captured);
	}

	/** Composites the running animation over the frame's GUI. */
	public static void afterGui(GpuBufferSlice fog) {
		if (kind == 0) return;
		try {
			if (kind == OPEN) afterOpen(fog);
			else afterClose(fog);
		} catch (RuntimeException | LinkageError e) {
			fail(e);
		}
	}

	private static void fail(Throwable e) {
		V1zir.LOGGER.error("Click GUI animation failed, animations are off until restart", e);
		broken = true;
		finish();
	}

	private static void afterOpen(GpuBufferSlice fog) {
		if (!drawnThisFrame) {
			// The screen went away (or was replaced) without drawing.
			finish();
			return;
		}
		drawnThisFrame = false;
		GuiCapture.render(fog);
		float p = progress.target(1f).get();
		ClickGuiScreen screen = MinecraftClient.getInstance().currentScreen instanceof ClickGuiScreen s ? s : null;
		if (screen == null) {
			finish();
			return;
		}
		setRect(screen);
		Window window = MinecraftClient.getInstance().getWindow();
		float height = window.getFramebufferHeight();
		PARAMS.anim(p, Math.min(1f, p * 1.8f), MODE_WARP, 1f);
		PARAMS.shape(Ui.RADIUS * scale(), height * 1.6f, 0.38f * (1f - p), height * 0.45f * (1f - p));
		PARAMS.backdrop(0f, false, 0f);
		composite(false);
		if (p >= 0.999f) finish();
	}

	private static void afterClose(GpuBufferSlice fog) {
		if (!captured) {
			// The snapshot: the closed screen draws its window one last time, into the capture.
			captured = true;
			ClickGuiScreen screen = closing;
			closing = null;
			DrawContext ctx = GuiCapture.begin(mouseX, mouseY);
			screen.drawWindow(ctx, mouseX, mouseY, 1f, 1f, 0f);
			Render2D.setAlpha(1f);
			GuiCapture.render(fog);
			setRect(screen);
			if (kind == SHATTER) {
				float s = scale();
				float ox = mouseX * s;
				float oy = mouseY * s;
				if (!(ox > PARAMS.rx && oy > PARAMS.ry && ox < PARAMS.rx + PARAMS.rw && oy < PARAMS.ry + PARAMS.rh)) {
					ox = PARAMS.rx + PARAMS.rw / 2f;
					oy = PARAMS.ry + PARAMS.rh / 2f;
				}
				PARAMS.origin(ox, oy);
				SHARDS.build(PARAMS.rx, PARAMS.ry, PARAMS.rw, PARAMS.rh, ox, oy, startedAt);
			}
		}
		float height = MinecraftClient.getInstance().getWindow().getFramebufferHeight();
		PARAMS.shape(Ui.RADIUS * scale(), height * 1.6f, 0f, 0f);
		boolean done;
		switch (kind) {
			case FADE -> {
				float p = progress.target(1f).get();
				PARAMS.anim(p, 1f - p, MODE_FADE, 1f - 0.035f * p);
				PARAMS.backdrop(1f - p, backdropBlur, backdropDim);
				done = p >= 0.98f;
			}
			default -> {
				float t = (System.nanoTime() - startedAt) / 1.0e9f * speed;
				PARAMS.anim(t, 1f, MODE_BACKDROP, 1f);
				PARAMS.backdrop(1f - smoothstep(0.1f, 0.9f, t), backdropBlur, backdropDim);
				PARAMS.gravity = height * 2.6f;
				done = t >= SHATTER_TIME;
			}
		}
		composite(kind == SHATTER);
		if (done) finish();
	}

	/** Window rectangle in framebuffer pixels. */
	private static void setRect(ClickGuiScreen screen) {
		screen.windowRect(RECT);
		float s = scale();
		PARAMS.rect(RECT[0] * s, RECT[1] * s, RECT[2] * s, RECT[3] * s);
	}

	private static float scale() {
		return (float) MinecraftClient.getInstance().getWindow().getScaleFactor();
	}

	private static float smoothstep(float a, float b, float x) {
		float t = Anim.clamp01((x - a) / (b - a));
		return t * t * (3f - 2f * t);
	}

	/** The fullscreen transition pass, then (shatter) the shards over it. */
	private static void composite(boolean shards) {
		MinecraftClient mc = MinecraftClient.getInstance();
		Window window = mc.getWindow();
		PARAMS.screen(window.getFramebufferWidth(), window.getFramebufferHeight());
		if (storage == null) storage = new DynamicUniformStorage<>("V1zir GUI transition UBO", UBO_SIZE, 4);
		GpuBufferSlice params = storage.write(PARAMS);
		GpuTextureView capture = GuiCapture.view();
		GpuTextureView blur = PARAMS.blur && BackdropBlur.view() != null ? BackdropBlur.view() : capture;
		GpuSampler sampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
		try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "V1zir GUI transition",
			mc.getFramebuffer().getColorAttachmentView(), OptionalInt.empty())) {
			pass.setPipeline(V1zirPipelines.GUI_TRANSITION);
			pass.bindTexture("Sampler0", capture, sampler);
			pass.bindTexture("Sampler1", blur, sampler);
			pass.setUniform("TransitionParams", params);
			pass.setVertexBuffer(0, Fullscreen.buffer());
			pass.draw(0, 3);
			if (shards && SHARDS.vertices() > 0) {
				pass.setPipeline(V1zirPipelines.GUI_SHARDS);
				pass.bindTexture("Sampler0", capture, sampler);
				pass.setUniform("TransitionParams", params);
				pass.setVertexBuffer(0, SHARDS.buffer());
				pass.draw(0, SHARDS.vertices());
			}
		}
	}

	/** TransitionParams (transition.glsl), rewritten in place every frame of an animation. */
	private static final class Params implements DynamicUniformStorage.Uploadable {
		float rx;
		float ry;
		float rw;
		float rh;
		float width;
		float height;
		float progress;
		float alpha;
		float mode;
		float scale;
		float radius;
		float focal;
		float tilt;
		float depth;
		float backdrop;
		boolean blur;
		float dim;
		float ox;
		float oy;
		float gravity;

		void rect(float x, float y, float w, float h) {
			rx = x;
			ry = y;
			rw = w;
			rh = h;
		}

		void screen(float w, float h) {
			width = Math.max(1f, w);
			height = Math.max(1f, h);
		}

		void anim(float progress, float alpha, int mode, float scale) {
			this.progress = progress;
			this.alpha = alpha;
			this.mode = mode;
			this.scale = scale;
		}

		void shape(float radius, float focal, float tilt, float depth) {
			this.radius = radius;
			this.focal = focal;
			this.tilt = tilt;
			this.depth = depth;
		}

		void backdrop(float opacity, boolean blur, float dim) {
			this.backdrop = opacity;
			this.blur = blur;
			this.dim = dim;
		}

		void origin(float x, float y) {
			ox = x;
			oy = y;
		}

		@Override
		public void write(ByteBuffer buffer) {
			Std140Builder.intoBuffer(buffer)
				.putVec4(rx, ry, rw, rh)
				.putVec4(width, height, 1f / width, 1f / height)
				.putVec4(progress, alpha, mode, scale)
				.putVec4(radius, focal, tilt, depth)
				.putVec4(backdrop, blur ? 1f : 0f, dim, 0f)
				.putVec4(ox, oy, gravity, CRACK_TIME);
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
