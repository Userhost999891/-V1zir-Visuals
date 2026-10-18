package dev.v1zir.visuals.render.sky;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import dev.v1zir.visuals.V1zir;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.impl.client.MainMenuModule;
import dev.v1zir.visuals.module.impl.visuals.ShaderSkyModule;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.texture.TextureSetup;
import org.joml.Matrix4f;

/**
 * Menu background sky: a slowly drifting virtual camera. The screen prepares it while it builds
 * the GUI; the sky is then rendered at reduced resolution right before the GUI is drawn
 * (GuiRendererMixin) and the background element upsamples it.
 */
public final class MenuSky {
	private static final SkyUniforms.Data DATA = new SkyUniforms.Data();
	private static final SkyTarget TARGET = new SkyTarget("V1zir menu sky");
	private static boolean pending;
	private static final Matrix4f PROJECTION = new Matrix4f();
	private static final Matrix4f VIEW = new Matrix4f();
	private static GpuBufferSlice slice;
	private static float time;
	private static long last;

	private MenuSky() {
	}

	public static TextureSetup textureSetup() {
		return TARGET.textureSetup();
	}

	/** Renders the background prepared this frame into its texture. */
	public static void renderPending() {
		if (!pending) return;
		pending = false;
		if (slice != null) TARGET.render(slice);
	}

	/**
	 * Writes the uniform block for this frame. parallaxX/Y are -1..1 offsets from the mouse.
	 */
	public static void prepare(SkyPreset preset, float width, float height, float parallaxX, float parallaxY, float speed) {
		long now = System.nanoTime();
		if (last != 0) time += Math.min((now - last) / 1.0e9f, 0.25f) * speed;
		last = now;

		long timeOfDay = switch (preset) {
			case REALISTIC -> 12620L;
			case SUNSET, SYNTHWAVE -> 11800L;
			case BLOOD_MOON -> 15500L;
			default -> 18000L;
		};
		ShaderSkyModule.fillSun(DATA, timeOfDay, 0f);
		DATA.timing.set(time, 0f, 0f, 1f);

		ShaderSkyModule sky = ModuleManager.get(ShaderSkyModule.class);
		if (sky != null) {
			sky.fillColors(DATA, preset);
			DATA.settings.set(1.0f, 0.42f, Math.max(sky.stars.f(), 0.6f), 1f);
		} else {
			DATA.colorA.set(1f, 1f, 1f, 0f);
			DATA.colorB.set(1f, 1f, 1f, 0f);
			DATA.settings.set(1.0f, 0.42f, 1f, 1f);
		}
		DATA.camera.set(time * 18f, 80f, time * 6f, preset.index());
		DATA.extra.set(1f, 1f, 0f, (float) (now % 1000L));

		float yaw;
		float pitch;
		float fov = 72f;
		switch (preset) {
			case REALISTIC, SUNSET -> {
				yaw = faceYaw(DATA.sunDir.x, DATA.sunDir.z);
				pitch = 0.17f;
			}
			case SYNTHWAVE -> {
				yaw = faceYaw(DATA.sunDir.x, DATA.sunDir.z);
				pitch = 0.07f;
			}
			case BLOOD_MOON -> {
				yaw = faceYaw(DATA.moonDir.x, DATA.moonDir.z);
				pitch = 0.62f;
			}
			case MAGMA -> {
				yaw = time * 0.018f;
				pitch = 0.62f;
				fov = 80f;
			}
			case AURORA -> {
				yaw = time * 0.012f;
				pitch = 0.3f;
			}
			default -> {
				yaw = time * 0.015f;
				pitch = 0.45f;
			}
		}
		yaw += parallaxX * 0.10f;
		pitch += parallaxY * 0.06f;

		PROJECTION.identity().perspective((float) Math.toRadians(fov), Math.max(width, 1f) / Math.max(height, 1f), 0.05f, 100f);
		VIEW.identity().rotateX(-pitch).rotateY(-yaw);
		DATA.invViewProj.set(PROJECTION).mul(VIEW).invert();
		slice = SkyUniforms.write(DATA);

		MainMenuModule menu = ModuleManager.get(MainMenuModule.class);
		float scale = menu != null ? menu.renderScale.f() : 0.5f;
		Framebuffer fb = V1zir.mc().getFramebuffer();
		TARGET.ensure(fb.textureWidth, fb.textureHeight, scale);
		pending = true;
	}

	/** Yaw that makes the camera look towards the given horizontal direction. */
	private static float faceYaw(float x, float z) {
		return (float) Math.atan2(-x, -z);
	}
}
