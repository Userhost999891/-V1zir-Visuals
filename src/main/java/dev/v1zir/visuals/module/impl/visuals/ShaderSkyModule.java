package dev.v1zir.visuals.module.impl.visuals;

import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.module.setting.ColorSetting;
import dev.v1zir.visuals.module.setting.ModeSetting;
import dev.v1zir.visuals.module.setting.NumberSetting;
import dev.v1zir.visuals.render.sky.SkyPreset;
import dev.v1zir.visuals.render.sky.SkyUniforms;
import dev.v1zir.visuals.util.ColorUtil;
import net.minecraft.client.world.ClientWorld;

public final class ShaderSkyModule extends Module {
	public final ModeSetting preset = add(new ModeSetting("Preset", SkyPreset.MAGMA.title(), SkyPreset.titles()));
	public final NumberSetting brightness = add(new NumberSetting("Brightness", 1.0, 0.2, 2.0, 0.05));
	public final NumberSetting speed = add(new NumberSetting("Speed", 1.0, 0.0, 4.0, 0.05).suffix("x"));
	public final ModeSetting quality = add(new ModeSetting("Quality", "Medium", "Low", "Medium", "High"));
	/** Fraction of the screen resolution the sky is rendered at before it is upsampled. */
	public final NumberSetting renderScale = add(new NumberSetting("Render scale", 0.5, 0.25, 1.0, 0.05).suffix("x"));
	public final BoolSetting clouds = add(new BoolSetting("Clouds", true));
	public final NumberSetting coverage = add(new NumberSetting("Cloud coverage", 0.45, 0.0, 1.0, 0.01).visibleIf(clouds::on));
	public final BoolSetting hideVanillaClouds = add(new BoolSetting("Hide vanilla clouds", true));
	public final NumberSetting stars = add(new NumberSetting("Stars", 1.0, 0.0, 3.0, 0.05));
	public final BoolSetting customColors = add(new BoolSetting("Custom colors", false));
	public final ColorSetting colorA = add(new ColorSetting("Primary color", 0xFFFFFFFF, false).visibleIf(this::colorsVisible));
	public final ColorSetting colorB = add(new ColorSetting("Secondary color", 0xFF8A5CFF, false).visibleIf(this::colorsVisible));
	public final BoolSetting vanillaCelestial = add(new BoolSetting("Vanilla sun & moon", false));
	public final BoolSetting syncFog = add(new BoolSetting("Sync fog", true));
	public final BoolSetting endSky = add(new BoolSetting("Replace End sky", false));

	private float animationTime;
	private long lastFrameNanos;

	public ShaderSkyModule() {
		super("Shader Sky", "Replaces the sky with animated, fully shaded presets", Category.VISUALS);
	}

	private boolean colorsVisible() {
		return customColors.on() || preset.is(SkyPreset.CUSTOM.title());
	}

	public SkyPreset currentPreset() {
		return SkyPreset.byTitle(preset.get());
	}

	@Override
	public String suffix() {
		return preset.get();
	}

	/** Advances the animation clock once per rendered frame (speed changes never cause jumps). */
	public float advanceTime() {
		long now = System.nanoTime();
		if (lastFrameNanos != 0) {
			float dt = Math.min((now - lastFrameNanos) / 1.0e9f, 0.25f);
			animationTime += dt * speed.f();
		}
		lastFrameNanos = now;
		return animationTime;
	}

	/** Horizon colour for "Sync fog", 0 when vanilla fog should stay. */
	public int fogColor() {
		if (!syncFog.on()) return 0;
		SkyPreset p = currentPreset();
		if (p == SkyPreset.CUSTOM) return colorB.argb();
		if (customColors.on() && (p == SkyPreset.MAGMA || p == SkyPreset.INK)) return ColorUtil.darken(colorA.argb(), 0.97f);
		return p.fogColor();
	}

	/** Fills everything but the view matrix. */
	public void fill(SkyUniforms.Data data, ClientWorld world, float tickDelta, double camX, double camY, double camZ) {
		SkyPreset p = currentPreset();
		long timeOfDay = world != null ? world.getTimeOfDay() : 6000L;
		fillSun(data, timeOfDay, tickDelta);
		float rain = world != null ? world.getRainGradient(tickDelta) : 0f;
		float thunder = world != null ? world.getThunderGradient(tickDelta) : 0f;
		data.timing.set(advanceTime(), thunder, rain, 1f);
		fillColors(data, p);
		data.settings.set(brightness.f(), coverage.f(), stars.f(), quality.index());
		data.camera.set((float) camX, (float) camY, (float) camZ, p.index());
		data.extra.set(1f, clouds.on() ? 1f : 0f, vanillaCelestial.on() ? 1f : 0f, (float) (System.nanoTime() % 1000L));
	}

	public void fillColors(SkyUniforms.Data data, SkyPreset p) {
		boolean custom = customColors.on() || p == SkyPreset.CUSTOM;
		float[] a = ColorUtil.rgbFloats(colorA.argb());
		float[] b = ColorUtil.rgbFloats(colorB.argb());
		data.colorA.set(a[0], a[1], a[2], custom ? 1f : 0f);
		data.colorB.set(b[0], b[1], b[2], custom ? 1f : 0f);
	}

	/** Same sky angle formula vanilla uses for the overworld (0 = noon). */
	public static void fillSun(SkyUniforms.Data data, long timeOfDay, float tickDelta) {
		double t = (Math.floorMod(timeOfDay, 24000L) + tickDelta) / 24000.0;
		double d = t - 0.25;
		d -= Math.floor(d);
		double e = 0.5 - Math.cos(d * Math.PI) / 2.0;
		double angle = (d * 2.0 + e) / 3.0 * Math.PI * 2.0;
		float sx = (float) -Math.sin(angle);
		float sy = (float) Math.cos(angle);
		// A slight tilt keeps the sun from passing exactly through the zenith.
		float sz = 0.18f;
		float len = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
		data.sunDir.set(sx / len, sy / len, sz / len, 0f);
		float moonPhase = Math.floorMod(timeOfDay / 24000L, 8L) / 8f;
		data.moonDir.set(-sx / len, -sy / len, -sz / len, moonPhase);
	}
}
