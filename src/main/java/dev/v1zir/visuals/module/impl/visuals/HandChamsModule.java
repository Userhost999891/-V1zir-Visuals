package dev.v1zir.visuals.module.impl.visuals;

import dev.v1zir.visuals.V1zir;
import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.module.setting.ColorSetting;
import dev.v1zir.visuals.module.setting.ModeSetting;
import dev.v1zir.visuals.module.setting.NumberSetting;
import dev.v1zir.visuals.render.sky.SkyPreset;
import dev.v1zir.visuals.render.sky.SkyUniforms;
import dev.v1zir.visuals.render.world.ModelCapture;
import dev.v1zir.visuals.render.world.WorldEffectRenderer;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * The first-person arm and held items, tinted, outlined, turned to crystal or made of the sky, and optionally the arm
 * holding whatever is in hand.
 * <p>
 * Tint colours them through vanilla's own draw: items through the vertex colour of their quads while
 * a first-person item is drawn (ItemRendererMixin), the arm through the model part tint
 * (PlayerEntityRendererMixin), so it costs nothing extra. Glow makes them emissive: full brightness,
 * and for the arm the emissive layer without shading, which reads as a soft self-lit glow.
 * <p>
 * Crystal draws the arm as tinted see-through crystal with glowing edges instead of its skin.
 * <p>
 * Outline and Sky are screen looks (render/HandFx): the whole hand is drawn into a texture of its own and laid over
 * the frame. Outline keeps its colours and puts a crisp line round its true silhouette with a soft glow beyond; Sky
 * makes it a window into one of the Shader Sky's presets (Nebula's stars, Ink, Plasma, Aurora...), fixed in the
 * world as the view turns, in the preset's colours or any others, with a rim of light along its edges.
 * <p>
 * Arm with items (render/HeldArm): the player's arm holds the item in first person, as the Knives view model holds
 * a knife, so every look shows on the arm as well.
 */
public final class HandChamsModule extends Module {
	/** Packed light of a fully lit vertex (block and sky light 15). */
	private static final int FULL_BRIGHT = 0xF000F0;
	private static final Identifier WHITE = V1zir.id("textures/misc/white.png");

	public final ModeSetting style = add(new ModeSetting("Style", "Sky", "Tint", "Outline", "Crystal", "Sky"));
	public final ModeSetting sky = add(new ModeSetting("Sky", SkyPreset.NEBULA.title(), SkyPreset.titles()).visibleIf(this::skyStyle));
	public final ModeSetting skyColors = add(new ModeSetting("Sky colors", "Preset", "Preset", "Custom", "Theme", "Rainbow").visibleIf(this::skyStyle));
	public final ColorSetting skyColorA = add(new ColorSetting("Sky primary", 0xFFB45CFF, false).visibleIf(this::skyPicked));
	public final ColorSetting skyColorB = add(new ColorSetting("Sky secondary", 0xFF4FD8FF, false).visibleIf(this::skyPicked));
	public final NumberSetting rainbowSpeed = add(new NumberSetting("Rainbow speed", 1.0, 0.1, 5.0, 0.05).suffix("x")
		.visibleIf(() -> skyStyle() && skyColors.is("Rainbow")));
	/** How much of the hand's own light and shade stays on the sky, so its shape still reads. */
	public final NumberSetting shading = add(new NumberSetting("Shading", 0.45, 0.0, 1.0, 0.05).visibleIf(this::skyStyle));
	public final NumberSetting skySpeed = add(new NumberSetting("Sky speed", 1.0, 0.0, 4.0, 0.05).suffix("x").visibleIf(this::skyStyle));
	public final BoolSetting items = add(new BoolSetting("Items", true).visibleIf(() -> !screenStyle()));
	public final BoolSetting hand = add(new BoolSetting("Hand", true).visibleIf(() -> !screenStyle()));
	public final BoolSetting armItems = add(new BoolSetting("Arm with items", true));
	public final BoolSetting themeColor = add(new BoolSetting("Theme color", true).visibleIf(() -> !skyStyle()));
	public final ColorSetting color = add(new ColorSetting("Color", 0xFF8FB4FF, false).visibleIf(() -> !skyStyle() && !themeColor.on()));
	public final NumberSetting opacity = add(new NumberSetting("Opacity", 0.8, 0.15, 1.0, 0.05).visibleIf(() -> !style.is("Outline")));
	public final BoolSetting glow = add(new BoolSetting("Glow", true).visibleIf(() -> style.is("Tint")));
	/** The outline's line, pixels at a window 1080 pixels tall. */
	public final NumberSetting lineWidth = add(new NumberSetting("Line width", 2.5, 1.0, 6.0, 0.5).suffix(" px").visibleIf(() -> style.is("Outline")));
	/** The glow round the silhouette and the rim along its inside edge. */
	public final NumberSetting edgeGlow = add(new NumberSetting("Edge glow", 1.0, 0.0, 2.0, 0.05).visibleIf(this::screenStyle));

	// Read for every quad of a first-person item, so they are plain static fields set once per item.
	private static boolean tinting;
	private static float red;
	private static float green;
	private static float blue;
	private static float alpha;
	private static boolean fullBright;

	/** One captured arm per submission this frame (both arms can be drawn), each drawn by commands of its own. */
	private final List<CrystalArm> crystalArms = new ArrayList<>();
	private int crystalCount;
	private int crystalFrame = -1;
	private RenderLayer edgeLayer;
	private RenderLayer fillLayer;
	/** The sky's animation clock, seconds, advanced by Sky speed once per frame. */
	private float skyTime;
	private long skyFrameNanos;

	public HandChamsModule() {
		super("Hand Chams", "Tint, outline, crystal or sky on your hand and held items", Category.VISUALS);
		setSubCategory("Render");
	}

	@Override
	public String suffix() {
		return style.get();
	}

	private boolean crystal() {
		return style.is("Crystal");
	}

	private boolean skyStyle() {
		return style.is("Sky");
	}

	private boolean screenStyle() {
		return style.is("Outline") || style.is("Sky");
	}

	private boolean skyPicked() {
		return skyStyle() && (skyColors.is("Custom") || skyColors.is("Preset") && sky.is(SkyPreset.CUSTOM.title()));
	}

	/** Outline or Sky: the hand is drawn into a texture of its own and laid over the frame (HandFx). */
	public boolean screenLook() {
		return isEnabled() && screenStyle();
	}

	public boolean skyLook() {
		return isEnabled() && skyStyle();
	}

	/** The player's arm holds the item in first person (HeldArm). */
	public boolean armWithItems() {
		return isEnabled() && armItems.on();
	}

	public float skyOpacity() {
		return opacity.f();
	}

	public float edgeGlow() {
		return edgeGlow.f();
	}

	public float outlinePixels() {
		return skyStyle() ? 1.5f : lineWidth.f();
	}

	/** The sky has no hard line round it: its rim and glow are enough. */
	public float skyLine() {
		return 0f;
	}

	/** The colour of the outline, the glow and the rim. */
	public int edgeColor() {
		if (!skyStyle()) return themeColor.on() ? Theme.accent() : color.argb();
		if (skyColors.is("Theme")) return Theme.accent();
		if (skyColors.is("Rainbow")) return ColorUtil.fromHsv((skyTime * 0.05f * rainbowSpeed.f()) % 1f, 0.75f, 1f, 255);
		SkyPreset preset = SkyPreset.byTitle(sky.get());
		if (skyColors.is("Custom") || preset == SkyPreset.CUSTOM) return skyColorA.argb();
		return switch (preset) {
			case REALISTIC -> 0xFF8FC8FF;
			case MAGMA -> 0xFFFF7A2E;
			case AURORA -> 0xFF5CFFB4;
			case NEBULA -> 0xFFB98CFF;
			case SUNSET -> 0xFFFFA25C;
			case INK -> 0xFFFFFFFF;
			case SYNTHWAVE -> 0xFFFF5CC8;
			case BLOOD_MOON -> 0xFFFF3B3B;
			case PLASMA -> 0xFFFF6BE0;
			case LAGOON -> 0xFF5CE8FF;
			case CUSTOM -> skyColorA.argb();
		};
	}

	/**
	 * The sky seen through the hand (HandFx, once per frame): the preset and its colours, the sun where the world's
	 * is, and none of the parts that belong to a real sky (clouds, the fade below the horizon, the sun's disc).
	 */
	public void fillSky(SkyUniforms.Data data, ClientWorld world, float tickDelta) {
		long now = System.nanoTime();
		if (skyFrameNanos != 0L) skyTime += Math.min((now - skyFrameNanos) / 1.0e9f, 0.25f) * skySpeed.f();
		skyFrameNanos = now;
		SkyPreset preset = SkyPreset.byTitle(sky.get());
		ShaderSkyModule.fillSun(data, world != null ? world.getTimeOfDay() : 6000L, tickDelta);
		data.timing.set(skyTime % 3600f, 0f, 0f, 1.3f);
		int mode = skyColors.index();
		if (mode == 0 && preset == SkyPreset.CUSTOM) mode = 1;
		float[] a = ColorUtil.rgbFloats(mode == 2 ? Theme.accent() : skyColorA.argb());
		float[] b = ColorUtil.rgbFloats(mode == 2 ? Theme.accent2() : skyColorB.argb());
		data.colorA.set(a[0], a[1], a[2], mode != 0 ? 1f : 0f);
		data.colorB.set(b[0], b[1], b[2], mode == 3 ? rainbowSpeed.f() : 0f);
		data.settings.set(1.15f, 0f, 1.4f, 1f);
		data.camera.set(0f, 64f, 0f, preset.index());
		data.extra.set(0f, 0f, 1f, 0f);
	}

	/** Start of an item draw; only first-person items are tinted. */
	public static void beginItem(boolean firstPerson) {
		tinting = false;
		if (!firstPerson) return;
		HandChamsModule module = ModuleManager.get(HandChamsModule.class);
		if (module == null || !module.isEnabled() || !module.items.on() || !module.style.is("Tint")) return;
		int c = module.argb();
		red = ColorUtil.red(c) / 255f;
		green = ColorUtil.green(c) / 255f;
		blue = ColorUtil.blue(c) / 255f;
		alpha = ColorUtil.alpha(c) / 255f;
		fullBright = module.glow.on();
		tinting = true;
	}

	public static void endItem() {
		tinting = false;
	}

	public static float red(float r) {
		return tinting ? r * red : r;
	}

	public static float green(float g) {
		return tinting ? g * green : g;
	}

	public static float blue(float b) {
		return tinting ? b * blue : b;
	}

	public static float alpha(float a) {
		return tinting ? a * alpha : a;
	}

	public static int itemLight(int light) {
		return tinting && fullBright ? FULL_BRIGHT : light;
	}

	public boolean tintsHand() {
		return isEnabled() && hand.on() && style.is("Tint");
	}

	/** The arm drawn as tinted see-through crystal with glowing edges instead of its skin. */
	public boolean crystalHand() {
		return isEnabled() && hand.on() && crystal();
	}

	public int handLight(int light) {
		return glow.on() ? FULL_BRIGHT : light;
	}

	public boolean glowingHand() {
		return tintsHand() && glow.on();
	}

	/** Tint colour with the opacity as alpha; glow lifts it towards white so it reads as lit. */
	public int argb() {
		int c = themeColor.on() ? Theme.accent() : color.argb();
		if (glow.on()) c = ColorUtil.brighten(c, 0.2f);
		return ColorUtil.withAlpha(c, Math.round(255 * opacity.f()));
	}

	private RenderLayer edgeLayer() {
		if (edgeLayer == null) edgeLayer = RenderLayers.eyes(WHITE);
		return edgeLayer;
	}

	private RenderLayer fillLayer() {
		if (fillLayer == null) fillLayer = RenderLayers.entityTranslucentEmissive(WHITE);
		return fillLayer;
	}

	/**
	 * The arm as vanilla submits it, in Crystal: captured instead of drawn, then drawn as crystal with the hand. The
	 * body and the edges are commands of their own, run in whatever order the queue takes them, so each submission
	 * keeps its capture until the next frame.
	 */
	public void crystalArm(ModelPart part, MatrixStack matrices, OrderedRenderCommandQueue queue) {
		int frame = WorldEffectRenderer.frame();
		if (crystalFrame != frame) {
			crystalFrame = frame;
			crystalCount = 0;
		}
		// Two arms at most in a frame; a few more slots in case a frame's counter is late.
		if (crystalCount >= 4) return;
		if (crystalCount == crystalArms.size()) crystalArms.add(new CrystalArm());
		CrystalArm arm = crystalArms.get(crystalCount++);
		arm.capture.clear();
		part.render(matrices, arm.capture, FULL_BRIGHT, OverlayTexture.DEFAULT_UV);
		queue.submitCustom(matrices, fillLayer(), arm.fill);
		queue.submitCustom(matrices, edgeLayer(), arm.edges);
	}

	private final class CrystalArm {
		final ModelCapture capture = new ModelCapture();
		final OrderedRenderCommandQueue.Custom fill = (entry, consumer) -> drawCrystalFill(capture, consumer);
		final OrderedRenderCommandQueue.Custom edges = (entry, consumer) -> drawCrystalEdges(capture, consumer);
	}

	/**
	 * The crystal's body: every face, the far ones too and fainter, in the tint (lighter at the top), more opaque
	 * towards the rim where glass is seen edge on.
	 */
	private void drawCrystalFill(ModelCapture capture, VertexConsumer consumer) {
		int c = themeColor.on() ? Theme.accent() : color.argb();
		float a = opacity.f();
		float[] q = capture.quads();
		for (int i = 0; i < capture.count(); i++) {
			int o = i * ModelCapture.STRIDE;
			float nx = q[o + 12], ny = q[o + 13], nz = q[o + 14];
			boolean front = nx * (q[o] + q[o + 6]) + ny * (q[o + 1] + q[o + 7]) + nz * (q[o + 2] + q[o + 8]) < 0f;
			int body = ColorUtil.lerp(c, 0xFFFFFFFF, 0.18f + 0.12f * Math.max(0f, ny));
			for (int k = 0; k < 4; k++) {
				// Far faces are wound the other way so they face the camera too.
				int corner = (front ? k : 3 - k) * 3;
				float x = q[o + corner] + nx * 0.002f, y = q[o + corner + 1] + ny * 0.002f, z = q[o + corner + 2] + nz * 0.002f;
				float facing = -(nx * x + ny * y + nz * z) / Math.max(1e-4f, (float) Math.sqrt(x * x + y * y + z * z));
				float rim = 1f - Math.max(0f, facing);
				float alpha = front ? a * (0.44f + 0.36f * rim * rim) : a * 0.2f;
				consumer.vertex(x, y, z, ColorUtil.withAlpha(body, Math.round(255f * Math.min(1f, alpha))), 0.5f, 0.5f, OverlayTexture.DEFAULT_UV,
					FULL_BRIGHT, nx, ny, nz);
			}
		}
	}

	/** The crystal's edges: a bright thin border round every face and a soft glow beside it, the far borders fainter. */
	private void drawCrystalEdges(ModelCapture capture, VertexConsumer consumer) {
		int c = themeColor.on() ? Theme.accent() : color.argb();
		int line = ColorUtil.lerp(c, 0xFFFFFFFF, 0.8f);
		float a = opacity.f();
		float[] q = capture.quads();
		for (int i = 0; i < capture.count(); i++) {
			int o = i * ModelCapture.STRIDE;
			float nx = q[o + 12], ny = q[o + 13], nz = q[o + 14];
			boolean front = nx * (q[o] + q[o + 6]) + ny * (q[o + 1] + q[o + 7]) + nz * (q[o + 2] + q[o + 8]) < 0f;
			float strength = front ? Math.min(1f, a * 1.15f) : a * 0.5f;
			border(consumer, q, o, front, 0.0045f, line, strength);
			border(consumer, q, o, front, 0.013f, c, strength * 0.35f);
		}
	}

	/** A border of the given width inside a face: a thin quad along each of its four edges. */
	private static void border(VertexConsumer consumer, float[] q, int o, boolean front, float width, int color, float alpha) {
		float nx = q[o + 12], ny = q[o + 13], nz = q[o + 14];
		float cx = (q[o] + q[o + 3] + q[o + 6] + q[o + 9]) * 0.25f;
		float cy = (q[o + 1] + q[o + 4] + q[o + 7] + q[o + 10]) * 0.25f;
		float cz = (q[o + 2] + q[o + 5] + q[o + 8] + q[o + 11]) * 0.25f;
		int argb = ColorUtil.withAlpha(color, Math.round(255f * Math.min(1f, alpha)));
		float lift = 0.004f;
		for (int e = 0; e < 4; e++) {
			int a0 = e * 3, a1 = ((e + 1) % 4) * 3;
			float ax = q[o + a0] + nx * lift, ay = q[o + a0 + 1] + ny * lift, az = q[o + a0 + 2] + nz * lift;
			float bx = q[o + a1] + nx * lift, by = q[o + a1 + 1] + ny * lift, bz = q[o + a1 + 2] + nz * lift;
			// Towards the face's centre from the middle of this edge.
			float mx = (ax + bx) * 0.5f, my = (ay + by) * 0.5f, mz = (az + bz) * 0.5f;
			float ix = cx + nx * lift - mx, iy = cy + ny * lift - my, iz = cz + nz * lift - mz;
			float il = (float) Math.sqrt(ix * ix + iy * iy + iz * iz);
			if (il < 1e-5f) continue;
			float w = Math.min(width, il * 0.5f) / il;
			ix *= w;
			iy *= w;
			iz *= w;
			float[] p = front ? new float[]{ax, ay, az, bx, by, bz, bx + ix, by + iy, bz + iz, ax + ix, ay + iy, az + iz}
				: new float[]{ax + ix, ay + iy, az + iz, bx + ix, by + iy, bz + iz, bx, by, bz, ax, ay, az};
			for (int k = 0; k < 12; k += 3) {
				consumer.vertex(p[k], p[k + 1], p[k + 2], argb, 0.5f, 0.5f, OverlayTexture.DEFAULT_UV, FULL_BRIGHT, nx, ny, nz);
			}
		}
	}
}
