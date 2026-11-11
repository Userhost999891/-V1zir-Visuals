package dev.v1zir.visuals.render;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import dev.v1zir.visuals.V1zir;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.impl.visuals.CinematicModule;
import dev.v1zir.visuals.module.impl.visuals.GodRaysModule;
import dev.v1zir.visuals.module.impl.visuals.MistModule;
import dev.v1zir.visuals.render.sky.SkyRenderer;
import dev.v1zir.visuals.render.world.WorldEffectRenderer;
import dev.v1zir.visuals.util.ColorUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.DynamicUniformStorage;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

import java.nio.ByteBuffer;
import java.util.OptionalInt;

/**
 * The full screen passes that set the mood of the world picture, each a copy of the frame and one pass over it, and
 * each skipped unless a module asks for it:
 * <ul>
 *   <li>the atmosphere, once the world is drawn and before the hand (whose depth replaces the world's), so it glows
 *   and is graded with the rest: the Mist, low fog whose
 *   density falls off with height, summed along every view ray in closed form, and the God Rays, shafts of the sun's
 *   or moon's glow cut out by whatever stands in the sky;</li>
 *   <li>the lens (Cinematic), last before the GUI: colour fringes, a vignette, film grain and letterbox bars.</li>
 * </ul>
 */
public final class PostFx {
	private static final int SIZE = new Std140SizeCalculator().putMat4f()
		.putVec4().putVec4().putVec4().putVec4().putVec4().putVec4().putVec4().get();
	/** How far below the eyes the mist's densest layer lies, and how fast (per second) it follows a change of height. */
	private static final float MIST_BELOW = 2.0f;
	private static final float MIST_FOLLOW = 0.35f;
	private static final Params PARAMS = new Params();
	private static final Matrix4d INVERSE = new Matrix4d();
	private static final Matrix4f VIEW_PROJ = new Matrix4f();
	private static final Vector4f CLIP = new Vector4f();
	private static DynamicUniformStorage<Params> storage;
	private static GpuTexture copy;
	private static GpuTextureView copyView;
	private static int width = -1;
	private static int height = -1;
	/** World height of the mist's base, following the camera slowly; NaN until it is first placed. */
	private static float mistBase = Float.NaN;
	private static long lastNanos;

	private PostFx() {
	}

	public static void flip() {
		if (storage != null) storage.clear();
	}

	/** Once the world is drawn, before the hand (GameRendererMixin): the mist and the light shafts, where their modules are on. */
	public static void atmosphere() {
		MinecraftClient mc = V1zir.mc();
		GodRaysModule rays = ModuleManager.get(GodRaysModule.class);
		MistModule mist = ModuleManager.get(MistModule.class);
		boolean shafts = rays != null && rays.isEnabled();
		boolean fog = mist != null && mist.isEnabled();
		if (!shafts && !fog || mc.world == null || mc.player == null) return;
		// Under water the game's own fog fills the view.
		if (mc.player.isSubmergedInWater()) return;
		ClientWorld world = mc.world;
		float tickDelta = mc.getRenderTickCounter().getTickProgress(false);
		Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();
		Params p = PARAMS;
		Matrix4fc view = WorldEffectRenderer.view();
		VIEW_PROJ.set(WorldEffectRenderer.projection()).mul(view);
		p.inverse.set(INVERSE.set(VIEW_PROJ).invert());

		// Where the sun stands, as the game turns it (the Shader Sky tilts its own a little off the zenith).
		double t = (Math.floorMod(world.getTimeOfDay(), 24000L) + tickDelta) / 24000.0 - 0.25;
		t -= Math.floor(t);
		double angle = (t * 2.0 + (0.5 - Math.cos(t * Math.PI) / 2.0)) / 3.0 * Math.PI * 2.0;
		float sx = (float) -Math.sin(angle);
		float sy = (float) Math.cos(angle);
		float sz = SkyRenderer.active() && SkyRenderer.hideVanillaCelestials() ? 0.18f : 0f;
		float length = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
		sx /= length;
		sy /= length;
		sz /= length;
		boolean day = sy > -0.05f;
		float clear = 1f - 0.85f * world.getRainGradient(tickDelta);

		p.light.set(0f, 0f, 0f, 0f);
		if (shafts && world.getRegistryKey() == World.OVERWORLD) {
			float lx = day ? sx : -sx, ly = day ? sy : -sy, lz = day ? sz : -sz;
			CLIP.set(lx, ly, lz, 0f).mul(VIEW_PROJ);
			// How far the light stands in front of the camera: the shafts fade as it leaves the view sideways.
			float facing = -view.m02() * lx - view.m12() * ly - view.m22() * lz;
			if (CLIP.w > 1.0e-4f && facing > 0.05f) {
				float up = Math.min(1f, ((day ? sy : -sy) + 0.05f) / 0.25f);
				// Strongest while the light is low, as in the morning and evening; the moon's are faint.
				float strength = rays.intensity.f() * clear * up * smooth(facing, 0.05f, 0.5f) * (day ? 0.75f + 0.75f * (1f - sy) : 0.4f);
				int color = rays.color(day, day ? sy : -sy);
				p.light.set(CLIP.x / CLIP.w * 0.5f + 0.5f, CLIP.y / CLIP.w * 0.5f + 0.5f, strength, rays.reach.f());
				p.lightColor.set(ColorUtil.red(color) / 255f, ColorUtil.green(color) / 255f, ColorUtil.blue(color) / 255f, rays.samples());
			}
		}

		p.fog.set(0f, 0f, 0f, 0f);
		if (fog) {
			long now = System.nanoTime();
			float dt = lastNanos == 0L ? 0f : Math.min((now - lastNanos) / 1.0e9f, 0.25f);
			lastNanos = now;
			float target = (float) cam.y - MIST_BELOW;
			if (Float.isNaN(mistBase) || Math.abs(target - mistBase) > 32f) mistBase = target;
			else mistBase += (target - mistBase) * (1f - (float) Math.exp(-dt * MIST_FOLLOW));
			int color = mist.color(world, sy);
			// At the default density a field 10 blocks away is a little hazy, 30 blocks away half gone in it.
			float density = mist.density.f() * 0.021f * (1f + 0.8f * world.getRainGradient(tickDelta));
			p.fog.set(ColorUtil.red(color) / 255f, ColorUtil.green(color) / 255f, ColorUtil.blue(color) / 255f, density);
			p.fogShape.set(1f / mist.height.f(), (float) cam.y - mistBase, mist.drift.on() ? 1f : 0f, 0.92f);
		} else {
			mistBase = Float.NaN;
			lastNanos = 0L;
		}
		if (p.light.z <= 0.001f && p.fog.w <= 0f) return;
		p.camera.set(Math.floorMod((long) Math.floor(cam.x), 4096L) + (float) (cam.x - Math.floor(cam.x)), (float) cam.y,
			Math.floorMod((long) Math.floor(cam.z), 4096L) + (float) (cam.z - Math.floor(cam.z)), 0f);
		p.lens.set(0f, 0f, 0f, 0f);
		pass(mc, V1zirPipelines.ATMOSPHERE, true, "V1zir atmosphere");
	}

	/** End of renderWorld, after grading (GameRendererMixin): the lens, where Cinematic is on. */
	public static void lens() {
		MinecraftClient mc = V1zir.mc();
		CinematicModule cinematic = ModuleManager.get(CinematicModule.class);
		if (cinematic == null || !cinematic.isEnabled() || mc.world == null) return;
		Params p = PARAMS;
		p.lens.set(cinematic.fringes.f(), cinematic.grain.f(), cinematic.bars.on() ? cinematic.barSize.f() : 0f, cinematic.vignette.f());
		if (p.lens.x <= 0f && p.lens.y <= 0f && p.lens.z <= 0f && p.lens.w <= 0f) return;
		p.light.set(0f, 0f, 0f, 0f);
		p.fog.set(0f, 0f, 0f, 0f);
		pass(mc, V1zirPipelines.LENS, false, "V1zir lens");
	}

	private static float smooth(float x, float from, float to) {
		float t = Math.max(0f, Math.min(1f, (x - from) / (to - from)));
		return t * t * (3f - 2f * t);
	}

	/** Copies the frame and draws the pass over it; with depth the pass also reads the depth buffer. */
	private static void pass(MinecraftClient mc, RenderPipeline pipeline, boolean depth, String label) {
		// Vanilla renders into other targets for special frames (panorama screenshots): leave those alone.
		if (RenderSystem.outputColorTextureOverride != null) return;
		Framebuffer fb = mc.getFramebuffer();
		int w = fb.textureWidth;
		int h = fb.textureHeight;
		if (w <= 0 || h <= 0) return;
		if (w != width || h != height || copy == null) {
			if (copyView != null) copyView.close();
			if (copy != null) copy.close();
			copy = RenderSystem.getDevice().createTexture("V1zir post source", 15, TextureFormat.RGBA8, w, h, 1, 1);
			copyView = RenderSystem.getDevice().createTextureView(copy);
			width = w;
			height = h;
		}
		if (storage == null) storage = new DynamicUniformStorage<>("V1zir post UBO", SIZE, 2);
		PARAMS.screen.set(1f / w, 1f / h, w / (float) h, V1zir.time() % 3600f);
		GpuBufferSlice params = storage.write(PARAMS);
		GpuSampler linear = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
		GpuSampler nearest = RenderSystem.getSamplerCache().get(FilterMode.NEAREST);
		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		encoder.copyTextureToTexture(fb.getColorAttachment(), copy, 0, 0, 0, 0, 0, w, h);
		try (RenderPass pass = encoder.createRenderPass(() -> label, fb.getColorAttachmentView(), OptionalInt.empty())) {
			pass.setPipeline(pipeline);
			pass.setUniform("PostParams", params);
			pass.bindTexture("Sampler0", copyView, linear);
			if (depth) pass.bindTexture("Sampler1", fb.getDepthAttachmentView(), nearest);
			pass.setVertexBuffer(0, Fullscreen.buffer());
			pass.draw(0, 3);
		}
	}

	/** One instance, refilled every frame: the storage copies it into the buffer on write. */
	private static final class Params implements DynamicUniformStorage.Uploadable {
		final Matrix4f inverse = new Matrix4f();
		final Vector4f screen = new Vector4f();
		final Vector4f light = new Vector4f();
		final Vector4f lightColor = new Vector4f();
		final Vector4f fog = new Vector4f();
		final Vector4f fogShape = new Vector4f();
		final Vector4f camera = new Vector4f();
		final Vector4f lens = new Vector4f();

		@Override
		public void write(ByteBuffer buffer) {
			Std140Builder.intoBuffer(buffer)
				.putMat4f(inverse)
				.putVec4(screen)
				.putVec4(light)
				.putVec4(lightColor)
				.putVec4(fog)
				.putVec4(fogShape)
				.putVec4(camera)
				.putVec4(lens);
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
