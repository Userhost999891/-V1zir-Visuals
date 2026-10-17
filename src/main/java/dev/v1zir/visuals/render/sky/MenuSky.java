package dev.v1zir.visuals.render.sky;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.impl.visuals.ShaderSkyModule;
import org.joml.Matrix4f;

/** SkyParams for the main menu background: a slowly drifting virtual camera. */
public final class MenuSky {
	private static final SkyUniforms.Data DATA = new SkyUniforms.Data();
	private static final Matrix4f PROJECTION = new Matrix4f();
	private static final Matrix4f VIEW = new Matrix4f();
	private static GpuBufferSlice slice;
	private static float time;
	private static long last;

	private MenuSky() {
	}

	public static GpuBufferSlice slice() {
		return slice;
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
	}

	/** Yaw that makes the camera look towards the given horizontal direction. */
	private static float faceYaw(float x, float z) {
		return (float) Math.atan2(-x, -z);
	}
}
