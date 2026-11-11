package dev.v1zir.visuals.render.sky;

import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.impl.visuals.ShaderSkyModule;
import org.joml.Matrix4d;
import org.joml.Matrix4f;

/**
 * Sky presets as title screen wallpapers: a slowly drifting virtual camera per preset. WallpaperRenderer renders them
 * at reduced resolution like every other wallpaper.
 */
public final class MenuSky {
	private static final Matrix4f PROJECTION = new Matrix4f();
	private static final Matrix4f VIEW = new Matrix4f();
	private static final Matrix4d INVERSE = new Matrix4d();

	private MenuSky() {
	}

	/** Writes the sky parameters of the preset at this animation time; parallaxX/Y are -1..1 offsets from the mouse. */
	public static void fill(SkyUniforms.Data data, SkyPreset preset, float aspect, float parallaxX, float parallaxY, float time) {
		long timeOfDay = switch (preset) {
			case REALISTIC -> 12620L;
			case SUNSET, SYNTHWAVE -> 11800L;
			case BLOOD_MOON -> 15500L;
			default -> 18000L;
		};
		ShaderSkyModule.fillSun(data, timeOfDay, 0f);
		data.timing.set(time, 0f, 0f, 1f);

		ShaderSkyModule sky = ModuleManager.get(ShaderSkyModule.class);
		if (sky != null) {
			sky.fillColors(data, preset);
			data.settings.set(1.0f, 0.42f, Math.max(sky.stars.f(), 0.6f), 1f);
		} else {
			data.colorA.set(1f, 1f, 1f, 0f);
			data.colorB.set(1f, 1f, 1f, 0f);
			data.settings.set(1.0f, 0.42f, 1f, 1f);
		}
		data.camera.set(time * 18f, 80f, time * 6f, preset.index());
		data.extra.set(1f, 1f, 0f, 0f);

		float yaw;
		float pitch;
		float fov = 72f;
		switch (preset) {
			case REALISTIC, SUNSET -> {
				yaw = faceYaw(data.sunDir.x, data.sunDir.z);
				pitch = 0.17f;
			}
			case SYNTHWAVE -> {
				yaw = faceYaw(data.sunDir.x, data.sunDir.z);
				pitch = 0.07f;
			}
			case BLOOD_MOON -> {
				yaw = faceYaw(data.moonDir.x, data.moonDir.z);
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

		PROJECTION.identity().perspective((float) Math.toRadians(fov), aspect, 0.05f, 100f);
		VIEW.identity().rotateX(-pitch).rotateY(-yaw);
		data.invViewProj.set(INVERSE.set(PROJECTION).mul(VIEW).invert());
	}

	/** Yaw that makes the camera look towards the given horizontal direction. */
	private static float faceYaw(float x, float z) {
		return (float) Math.atan2(-x, -z);
	}
}
