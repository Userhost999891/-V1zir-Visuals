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
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.impl.visuals.BlockReflectionModule;
import dev.v1zir.visuals.module.impl.visuals.RainEffectsModule;
import dev.v1zir.visuals.module.impl.visuals.WetWorldModule;
import dev.v1zir.visuals.module.impl.visuals.WinterModule;
import dev.v1zir.visuals.gui.hud.Notifications;
import dev.v1zir.visuals.render.font.Icons;
import dev.v1zir.visuals.render.sky.SkyCache;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.DynamicUniformStorage;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.nio.ByteBuffer;
import java.util.OptionalInt;

/**
 * Screen-space reflections and rain wetness (Block Reflection, Rain Effects), one post pass over
 * the finished world before the hand and the GUI. The frame is copied once; a trace pass (at half
 * resolution by default) rebuilds the surface under each pixel from the depth buffer and the block
 * textures of {@link SurfaceMap}, and marches the reflected ray through the depth buffer. A full
 * resolution pass then blurs the traces by roughness and composites them with the wet look and the
 * sun glints, discarding every pixel it leaves alone. Nothing runs while both modules are off or,
 * with only Rain Effects on, while everything is dry. Winter's snow cover is a pass of its own over a
 * fresh copy of the frame, with the same surface helpers (snow_cover.fsh).
 */
public final class Reflections {
	private static final int SIZE = new Std140SizeCalculator().putMat4f().putMat4f()
		.putVec4().putVec4().putVec4().putVec4().putVec4().putVec4().putVec4().putVec4().putVec4().putVec4().putVec4().putVec4()
		.putVec4().putVec4().get();
	/** Longest reflected ray, in blocks. */
	private static final float RAY_LENGTH = 64f;
	private static final Matrix4f VIEW = new Matrix4f();
	private static final Matrix4f PROJECTION = new Matrix4f();
	// The inverse is taken in double: the float inverse of a perspective matrix is too imprecise.
	private static final Matrix4d INVERSE = new Matrix4d();
	private static final Vector4f FOG = new Vector4f();
	private static final Params PARAMS = new Params();
	private static DynamicUniformStorage<Params> storage;
	private static boolean captured;
	private static GpuTexture copy;
	private static GpuTextureView copyView;
	private static GpuTexture traced;
	private static GpuTextureView tracedView;
	private static int width = -1;
	private static int height = -1;
	private static int tracedWidth = -1;
	private static int tracedHeight = -1;
	/** Set when the snow cover failed: it stays off until the game restarts, the reflections are not touched. */
	private static boolean snowBroken;

	private Reflections() {
	}

	public static void flip() {
		if (storage != null) storage.clear();
	}

	/** Start of WorldRenderer#render: the frame's view rotation, world projection and fog colour. */
	public static void captureFrame(Matrix4f view, Matrix4f projection, Vector4f fogColor) {
		VIEW.set(view);
		PROJECTION.set(projection);
		FOG.set(fogColor);
		captured = true;
	}

	/** End of every client tick: keeps the block textures fresh while any effect may need them. */
	public static void tick(MinecraftClient client) {
		boolean reflections = ModuleManager.isEnabled(BlockReflectionModule.class);
		RainEffectsModule rain = ModuleManager.get(RainEffectsModule.class);
		boolean snow = snowCover(client.world);
		if (client.world == null
			|| !reflections && !snow && (rain == null || !rain.isEnabled() || !rain.active()) && WetWorldModule.wetness() <= 0f) {
			SurfaceMap.idle();
			return;
		}
		Vec3d cam = client.gameRenderer.getCamera().getCameraPos();
		// Snow needs the far columns (and the materials near the camera) as reflections do.
		SurfaceMap.tick(client.world, (int) Math.floor(cam.x), (int) Math.floor(cam.y), (int) Math.floor(cam.z), reflections || snow);
	}

	/** Winter's snow lies on the overworld only. */
	private static boolean snowCover(ClientWorld world) {
		return world != null && world.getRegistryKey() == World.OVERWORLD && WinterModule.covers();
	}

	/** End of WorldRenderer#render, before the 3D effects (WorldRendererMixin). */
	public static void render(float tickDelta) {
		BlockReflectionModule settings = ModuleManager.get(BlockReflectionModule.class);
		RainEffectsModule rain = ModuleManager.get(RainEffectsModule.class);
		if (settings == null || rain == null) return;
		boolean reflections = settings.isEnabled();
		// Rain's own wetness, or Wet World's held on: the wetter of them.
		boolean raining = rain.isEnabled() && rain.active();
		float wetness = Math.max(raining ? rain.wetness(tickDelta) : 0f, WetWorldModule.wetness());
		float drops = Math.max(raining ? rain.rain(tickDelta) : 0f, WetWorldModule.drops());
		boolean wet = wetness > 0f;
		MinecraftClient mc = V1zir.mc();
		// Not under water: the game's own fog fills the view there.
		boolean snow = !snowBroken && snowCover(mc.world) && mc.player != null && !mc.player.isSubmergedInWater();
		// Vanilla renders into other targets for special frames (panorama screenshots): leave those alone.
		if (!reflections && !wet && !snow || !captured || RenderSystem.outputColorTextureOverride != null) return;
		if (mc.world == null || !SurfaceMap.upload()) return;
		GpuBufferSlice params;
		try {
			params = prepare(mc, settings, reflections, snow, wetness, drops, tickDelta);
			if (params == null) return;
			if (reflections || wet) draw(mc, params);
		} catch (RuntimeException e) {
			V1zir.LOGGER.error("Reflections failed, disabling them", e);
			settings.setEnabled(false, false);
			rain.setEnabled(false, false);
			WetWorldModule wetWorld = ModuleManager.get(WetWorldModule.class);
			if (wetWorld != null) wetWorld.setEnabled(false, false);
			// Said where it shows, not only in the log: otherwise the modules just seem to switch themselves off.
			String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
			Notifications.push(Icons.INFO, "Reflections turned off", reason.length() > 60 ? reason.substring(0, 60) + "…" : reason, false);
			return;
		}
		if (!snow) return;
		try {
			drawSnow(mc, params);
		} catch (RuntimeException e) {
			// Only the snow goes: the reflections and the rest of Winter stay.
			V1zir.LOGGER.error("Snow cover failed, turning it off", e);
			snowBroken = true;
			String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
			Notifications.push(Icons.INFO, "Snow cover turned off", reason.length() > 60 ? reason.substring(0, 60) + "…" : reason, false);
		}
	}

	/** Makes the textures and fills the frame's parameters; null when the frame has no size. */
	private static GpuBufferSlice prepare(MinecraftClient mc, BlockReflectionModule settings, boolean reflections, boolean snow, float wetness,
										  float drops, float tickDelta) {
		Framebuffer fb = mc.getFramebuffer();
		int w = fb.textureWidth;
		int h = fb.textureHeight;
		if (w <= 0 || h <= 0) return null;
		float scale = settings.halfResolution.on() ? 0.5f : 1f;
		ensureTextures(w, h, Math.max(1, Math.round(w * scale)), Math.max(1, Math.round(h * scale)), fb.getColorAttachment().getFormat());
		if (storage == null) storage = new DynamicUniformStorage<>("V1zir reflections UBO", SIZE, 2);
		fill(mc, mc.world, mc.gameRenderer.getCamera().getCameraPos(), settings, reflections, snow, wetness, drops, tickDelta);
		// The Shader Sky's panorama, where rays that leave the screen look the sky up (else the sky on screen and a gradient).
		PARAMS.skyInfo.set(SkyCache.upperSky() != null ? 1f : 0f, 0f, 0f, 0f);
		return storage.write(PARAMS);
	}

	private static void draw(MinecraftClient mc, GpuBufferSlice params) {
		Framebuffer fb = mc.getFramebuffer();
		int w = fb.textureWidth;
		int h = fb.textureHeight;
		GpuTextureView sky = SkyCache.upperSky();
		GpuSampler nearest = RenderSystem.getSamplerCache().get(FilterMode.NEAREST);
		GpuSampler linear = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		encoder.copyTextureToTexture(fb.getColorAttachment(), copy, 0, 0, 0, 0, 0, w, h);
		try (RenderPass pass = encoder.createRenderPass(() -> "V1zir reflection trace", tracedView, OptionalInt.empty())) {
			pass.setPipeline(V1zirPipelines.REFLECT_TRACE);
			bindScene(pass, params, linear, nearest);
			pass.bindTexture("Sampler5", sky != null ? sky : copyView, linear);
			pass.setVertexBuffer(0, Fullscreen.buffer());
			pass.draw(0, 3);
		}
		try (RenderPass pass = encoder.createRenderPass(() -> "V1zir reflection composite", fb.getColorAttachmentView(), OptionalInt.empty())) {
			pass.setPipeline(V1zirPipelines.REFLECT_COMPOSITE);
			bindScene(pass, params, nearest, nearest);
			pass.bindTexture("Sampler5", tracedView, linear);
			pass.setVertexBuffer(0, Fullscreen.buffer());
			pass.draw(0, 3);
		}
	}

	/** Winter's snow on the ground, over a fresh copy of the frame (the reflections may have changed it). */
	private static void drawSnow(MinecraftClient mc, GpuBufferSlice params) {
		Framebuffer fb = mc.getFramebuffer();
		GpuSampler nearest = RenderSystem.getSamplerCache().get(FilterMode.NEAREST);
		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		encoder.copyTextureToTexture(fb.getColorAttachment(), copy, 0, 0, 0, 0, 0, fb.textureWidth, fb.textureHeight);
		try (RenderPass pass = encoder.createRenderPass(() -> "V1zir snow cover", fb.getColorAttachmentView(), OptionalInt.empty())) {
			pass.setPipeline(V1zirPipelines.SNOW_COVER);
			bindScene(pass, params, nearest, nearest);
			pass.setVertexBuffer(0, Fullscreen.buffer());
			pass.draw(0, 3);
		}
	}

	private static void bindScene(RenderPass pass, GpuBufferSlice params, GpuSampler colorSampler, GpuSampler nearest) {
		pass.setUniform("ReflectParams", params);
		pass.bindTexture("Sampler0", copyView, colorSampler);
		pass.bindTexture("Sampler1", V1zir.mc().getFramebuffer().getDepthAttachmentView(), nearest);
		pass.bindTexture("Sampler2", SurfaceMap.columns(), nearest);
		pass.bindTexture("Sampler3", SurfaceMap.grid(), nearest);
		pass.bindTexture("Sampler4", SurfaceMap.far(), nearest);
	}

	private static void fill(MinecraftClient mc, ClientWorld world, Vec3d cam, BlockReflectionModule settings, boolean reflections, boolean snow,
							 float wetness, float drops, float tickDelta) {
		Params p = PARAMS;
		p.viewProj.set(PROJECTION).mul(VIEW);
		p.inverse.set(INVERSE.set(PROJECTION).mul(VIEW).invert());
		int bx = (int) Math.floor(cam.x);
		int by = (int) Math.floor(cam.y);
		int bz = (int) Math.floor(cam.z);
		float fx = (float) (cam.x - bx);
		float fy = (float) (cam.y - by);
		float fz = (float) (cam.z - bz);
		// Small numbers only: world-space noise repeats every 4096 blocks, the textures every 512 (x and z) and 128 (y).
		p.cameraPos.set(Math.floorMod(bx, 4096) + fx, by + fy, Math.floorMod(bz, 4096) + fz, by);
		p.cameraBlock.set(Math.floorMod(bx, SurfaceMap.FAR), Math.floorMod(by, 128), Math.floorMod(bz, SurfaceMap.FAR), world.getBottomY());
		p.cameraFrac.set(fx, fy, fz, V1zir.time() % 3600f);
		p.screen.set(width, height, tracedWidth, tracedHeight);
		// The material grid is kept fresh while reflections or the snow need it.
		p.trace.set(settings.steps(), settings.refinements(), RAY_LENGTH, SurfaceMap.gridReady() && (reflections || snow) ? 1f : 0f);
		p.surface.set(reflections ? settings.metal() ? 2f : 1f : 0f, settings.strength.f(), settings.roughness.f(), 0f);
		p.weather.set(wetness, drops, 0f, 0f);
		light(p, world, tickDelta);
		p.fog.set(FOG.x, FOG.y, FOG.z, 1f);
		// The camera looks down -Z: its forward axis is the negated third row of the view rotation.
		p.forward.set(-VIEW.m02(), -VIEW.m12(), -VIEW.m22(), 0f);
		// Snow's light: bright and faintly blue by day, dim blue at night, greyer in a storm.
		double t = (Math.floorMod(world.getTimeOfDay(), 24000L) + tickDelta) / 24000.0 - 0.25;
		float day = (float) Math.max(0.0, Math.min(1.0, 0.5 + Math.cos(t * Math.PI * 2.0) * 1.3));
		float storm = world.getRainGradient(tickDelta);
		p.snowCover.set(snow ? 1f : 0f, mc.options.getClampedViewDistance() * 16f, (0.2f + 0.8f * day) * (1f - 0.25f * storm), 1f - 0.8f * storm);
		p.snowTint.set(0.78f + 0.2f * day, 0.83f + 0.16f * day, 1f, 0f);
	}

	/** Direction and colour of the sun, or of the moon at night, for the glints; none outside the overworld. */
	private static void light(Params p, ClientWorld world, float tickDelta) {
		if (world.getRegistryKey() != World.OVERWORLD) {
			p.lightDir.set(0f, 1f, 0f, 0f);
			return;
		}
		// Vanilla's celestial angle: the sun turns about the Z axis, rising in the east (+X).
		double t = (Math.floorMod(world.getTimeOfDay(), 24000L) + tickDelta) / 24000.0 - 0.25;
		t -= Math.floor(t);
		double angle = (t * 2.0 + (0.5 - Math.cos(t * Math.PI) / 2.0)) / 3.0 * Math.PI * 2.0;
		float sx = (float) -Math.sin(angle);
		float sy = (float) Math.cos(angle);
		float clear = 1f - 0.85f * world.getRainGradient(tickDelta);
		if (sy > -0.05f) {
			float up = Math.min(1f, (sy + 0.05f) / 0.25f);
			// Warmer and dimmer near the horizon.
			p.lightDir.set(sx, sy, 0f, up * clear);
			p.lightColor.set(1f, 0.78f + 0.17f * up, 0.55f + 0.33f * up, 1f);
		} else {
			float up = Math.min(1f, (-sy - 0.05f) / 0.25f);
			p.lightDir.set(-sx, -sy, 0f, 0.3f * up * clear);
			p.lightColor.set(0.62f, 0.72f, 1f, 1f);
		}
	}

	/** The copy of the frame is made in the frame's own format: a copy between two formats fails. */
	private static void ensureTextures(int w, int h, int tw, int th, TextureFormat format) {
		if (w != width || h != height || copy == null || copy.getFormat() != format) {
			if (copyView != null) copyView.close();
			if (copy != null) copy.close();
			copy = RenderSystem.getDevice().createTexture("V1zir reflections source", 15, format, w, h, 1, 1);
			copyView = RenderSystem.getDevice().createTextureView(copy);
			width = w;
			height = h;
		}
		if (tw != tracedWidth || th != tracedHeight || traced == null) {
			if (tracedView != null) tracedView.close();
			if (traced != null) traced.close();
			traced = RenderSystem.getDevice().createTexture("V1zir reflections traced", 15, TextureFormat.RGBA8, tw, th, 1, 1);
			tracedView = RenderSystem.getDevice().createTextureView(traced);
			tracedWidth = tw;
			tracedHeight = th;
		}
	}

	/** One instance, refilled every frame: the storage copies it into the buffer on write. */
	private static final class Params implements DynamicUniformStorage.Uploadable {
		final Matrix4f viewProj = new Matrix4f();
		final Matrix4f inverse = new Matrix4f();
		final Vector4f cameraPos = new Vector4f();
		final Vector4f cameraBlock = new Vector4f();
		final Vector4f cameraFrac = new Vector4f();
		final Vector4f screen = new Vector4f();
		final Vector4f trace = new Vector4f();
		final Vector4f surface = new Vector4f();
		final Vector4f weather = new Vector4f();
		final Vector4f lightDir = new Vector4f();
		final Vector4f lightColor = new Vector4f();
		final Vector4f fog = new Vector4f();
		final Vector4f forward = new Vector4f();
		/** x = 1 while the trace pass's Sampler5 holds the Shader Sky's panorama. */
		final Vector4f skyInfo = new Vector4f();
		/** Winter's snow on the ground: x = amount, y = render distance (blocks), z = the day's light, w = glitter. */
		final Vector4f snowCover = new Vector4f();
		final Vector4f snowTint = new Vector4f();

		@Override
		public void write(ByteBuffer buffer) {
			Std140Builder.intoBuffer(buffer)
				.putMat4f(viewProj)
				.putMat4f(inverse)
				.putVec4(cameraPos)
				.putVec4(cameraBlock)
				.putVec4(cameraFrac)
				.putVec4(screen)
				.putVec4(trace)
				.putVec4(surface)
				.putVec4(weather)
				.putVec4(lightDir)
				.putVec4(lightColor)
				.putVec4(fog)
				.putVec4(forward)
				.putVec4(skyInfo)
				.putVec4(snowCover)
				.putVec4(snowTint);
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
