package dev.v1zir.visuals.render.sky;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.v1zir.visuals.V1zir;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.impl.visuals.ShaderSkyModule;
import dev.v1zir.visuals.render.Fullscreen;
import dev.v1zir.visuals.render.V1zirPipelines;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.OptionalInt;

/** Draws the shader sky from inside the vanilla sky pass (see SkyRenderingMixin). */
public final class SkyRenderer {
	private static final Matrix4f VIEW = new Matrix4f();
	private static final Matrix4f PROJECTION = new Matrix4f();
	private static final SkyUniforms.Data DATA = new SkyUniforms.Data();
	private static final SkyTarget TARGET = new SkyTarget("V1zir shader sky");
	private static boolean captured;

	private SkyRenderer() {
	}

	/** Called at the start of WorldRenderer#render with the matrices vanilla uses for the frame. */
	public static void captureMatrices(Matrix4f view, Matrix4f projection) {
		VIEW.set(view);
		PROJECTION.set(projection);
		captured = true;
	}

	public static ShaderSkyModule module() {
		ShaderSkyModule sky = ModuleManager.get(ShaderSkyModule.class);
		return sky != null && sky.isEnabled() ? sky : null;
	}

	public static boolean active() {
		return module() != null && captured;
	}

	public static boolean hideVanillaCelestials() {
		ShaderSkyModule sky = module();
		return sky != null && !sky.vanillaCelestial.on();
	}

	public static boolean hideVanillaClouds() {
		ShaderSkyModule sky = module();
		return sky != null && sky.hideVanillaClouds.on();
	}

	public static int fogOverride() {
		ShaderSkyModule sky = module();
		return sky != null ? sky.fogColor() : 0;
	}

	/** Overworld sky. Returns true when vanilla's sky disc should be skipped. */
	public static boolean renderWorldSky() {
		ShaderSkyModule sky = module();
		if (sky == null || !captured) return false;
		return render(sky);
	}

	public static boolean renderEndSky() {
		ShaderSkyModule sky = module();
		if (sky == null || !captured || !sky.endSky.on()) return false;
		return render(sky);
	}

	private static boolean render(ShaderSkyModule sky) {
		MinecraftClient mc = V1zir.mc();
		if (mc.world == null) return false;
		try {
			float tickDelta = mc.getRenderTickCounter().getTickProgress(false);
			Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();
			sky.fill(DATA, mc.world, tickDelta, cam.x, cam.y, cam.z);
			DATA.invViewProj.set(PROJECTION).mul(VIEW).invert();
			GpuBufferSlice params = SkyUniforms.write(DATA);
			GpuTextureView color = RenderSystem.outputColorTextureOverride != null
				? RenderSystem.outputColorTextureOverride
				: mc.getFramebuffer().getColorAttachmentView();
			float scale = sky.renderScale.f();
			if (scale < 0.99f && RenderSystem.outputColorTextureOverride == null) {
				// Sky at a fraction of the resolution, then upsampled: the cost drops with the pixel count.
				Framebuffer fb = mc.getFramebuffer();
				TARGET.ensure(fb.textureWidth, fb.textureHeight, scale);
				TARGET.render(params);
				TARGET.upsampleTo(color);
				return true;
			}
			try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "V1zir shader sky", color, OptionalInt.empty())) {
				pass.setPipeline(V1zirPipelines.SKY);
				pass.setUniform("SkyParams", params);
				pass.setVertexBuffer(0, Fullscreen.buffer());
				pass.draw(0, 3);
			}
			return true;
		} catch (RuntimeException e) {
			V1zir.LOGGER.error("Shader sky failed, disabling it", e);
			sky.setEnabled(false, false);
			return false;
		}
	}
}
