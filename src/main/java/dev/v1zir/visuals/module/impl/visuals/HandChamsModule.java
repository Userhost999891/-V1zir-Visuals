package dev.v1zir.visuals.module.impl.visuals;

import dev.v1zir.visuals.V1zir;
import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.module.setting.ColorSetting;
import dev.v1zir.visuals.module.setting.ModeSetting;
import dev.v1zir.visuals.module.setting.NumberSetting;
import dev.v1zir.visuals.render.world.ModelCapture;
import dev.v1zir.visuals.render.world.WorldEffectRenderer;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * The first-person arm and held items, tinted or outlined.
 * <p>
 * Tint colours them through vanilla's own draw: items through the vertex colour of their quads while
 * a first-person item is drawn (ItemRendererMixin), the arm through the model part tint
 * (PlayerEntityRendererMixin), so it costs nothing extra. Glow makes them emissive: full brightness,
 * and for the arm the emissive layer without shading, which reads as a soft self-lit glow.
 * <p>
 * Outline keeps their own colours and wraps them in a glowing rim: their quads, pushed out a little
 * along the normals, are drawn on vanilla's emissive translucent layer with an alpha that only rises
 * where a face is seen edge on, plus a wider, fainter halo. It goes through the hand's own render
 * queue, so it uses the hand's projection and depth; no extra pass, no blur.
 */
public final class HandChamsModule extends Module {
	/** Packed light of a fully lit vertex (block and sky light 15). */
	private static final int FULL_BRIGHT = 0xF000F0;
	private static final Identifier WHITE = V1zir.id("textures/misc/white.png");
	/** How far the rim and the halo sit outside the surface at Outline width 1, in blocks of the hand's space. */
	private static final float RIM = 0.006f;
	private static final float HALO = 0.02f;

	public final ModeSetting style = add(new ModeSetting("Style", "Tint", "Tint", "Outline", "Crystal"));
	public final BoolSetting items = add(new BoolSetting("Items", true));
	public final BoolSetting hand = add(new BoolSetting("Hand", true));
	public final BoolSetting themeColor = add(new BoolSetting("Theme color", true));
	public final ColorSetting color = add(new ColorSetting("Color", 0xFF8FB4FF, false).visibleIf(() -> !themeColor.on()));
	public final NumberSetting opacity = add(new NumberSetting("Opacity", 0.8, 0.15, 1.0, 0.05).visibleIf(() -> !style.is("Outline")));
	public final BoolSetting glow = add(new BoolSetting("Glow", true).visibleIf(() -> style.is("Tint")));
	public final NumberSetting outlineWidth = add(new NumberSetting("Outline width", 1.0, 0.4, 3.0, 0.05).suffix("x").visibleIf(() -> style.is("Outline")));

	// Read for every quad of a first-person item, so they are plain static fields set once per item.
	private static boolean tinting;
	private static float red;
	private static float green;
	private static float blue;
	private static float alpha;
	private static boolean fullBright;

	private final ModelCapture armCapture = new ModelCapture();
	private final ModelCapture itemCapture = new ModelCapture();
	/** World frame the captured arm belongs to; a frame whose hand was never drawn leaves nothing behind. */
	private int armFrame = -1;
	private final OrderedRenderCommandQueue.Custom armOutline = this::drawArmOutline;
	private final ModelCapture crystalCapture = new ModelCapture();
	private int crystalFrame = -1;
	private final OrderedRenderCommandQueue.Custom crystalFill = this::drawCrystalFill;
	private final OrderedRenderCommandQueue.Custom crystalEdges = this::drawCrystalEdges;
	private RenderLayer outlineLayer;
	private RenderLayer fillLayer;

	public HandChamsModule() {
		super("Hand Chams", "Tint, outline or crystal your hand and held items", Category.VISUALS);
		setSubCategory("Render");
	}

	@Override
	public String suffix() {
		return style.get();
	}

	private boolean outline() {
		return style.is("Outline");
	}

	private boolean crystal() {
		return style.is("Crystal");
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

	public boolean outlinesHand() {
		return isEnabled() && hand.on() && outline();
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

	private RenderLayer outlineLayer() {
		if (outlineLayer == null) outlineLayer = RenderLayers.eyes(WHITE);
		return outlineLayer;
	}

	/** The arm as vanilla submits it (PlayerEntityRendererMixin): its quads now, its outline when the hand is drawn. */
	public void outlineArm(ModelPart part, MatrixStack matrices, OrderedRenderCommandQueue queue) {
		int frame = WorldEffectRenderer.frame();
		if (armFrame != frame) {
			armFrame = frame;
			armCapture.clear();
		}
		part.render(matrices, armCapture, FULL_BRIGHT, OverlayTexture.DEFAULT_UV);
		queue.submitCustom(matrices, outlineLayer(), armOutline);
	}

	private RenderLayer fillLayer() {
		if (fillLayer == null) fillLayer = RenderLayers.entityTranslucentEmissive(WHITE);
		return fillLayer;
	}

	/** The arm as vanilla submits it, in Crystal: captured instead of drawn, then drawn as crystal with the hand. */
	public void crystalArm(ModelPart part, MatrixStack matrices, OrderedRenderCommandQueue queue) {
		int frame = WorldEffectRenderer.frame();
		if (crystalFrame != frame) {
			crystalFrame = frame;
			crystalCapture.clear();
		}
		part.render(matrices, crystalCapture, FULL_BRIGHT, OverlayTexture.DEFAULT_UV);
		queue.submitCustom(matrices, fillLayer(), crystalFill);
		queue.submitCustom(matrices, outlineLayer(), crystalEdges);
	}

	/**
	 * The crystal's body: every face, the far ones too and fainter, in the tint (lighter at the top), more opaque
	 * towards the rim where glass is seen edge on.
	 */
	private void drawCrystalFill(MatrixStack.Entry entry, VertexConsumer consumer) {
		int c = themeColor.on() ? Theme.accent() : color.argb();
		float a = opacity.f();
		float[] q = crystalCapture.quads();
		for (int i = 0; i < crystalCapture.count(); i++) {
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
				float alpha = front ? a * (0.34f + 0.3f * rim * rim) : a * 0.16f;
				consumer.vertex(x, y, z, ColorUtil.withAlpha(body, Math.round(255f * Math.min(1f, alpha))), 0.5f, 0.5f, OverlayTexture.DEFAULT_UV,
					FULL_BRIGHT, nx, ny, nz);
			}
		}
	}

	/** The crystal's edges: a bright thin border round every face and a soft glow beside it, the far borders fainter. */
	private void drawCrystalEdges(MatrixStack.Entry entry, VertexConsumer consumer) {
		int c = themeColor.on() ? Theme.accent() : color.argb();
		int line = ColorUtil.lerp(c, 0xFFFFFFFF, 0.8f);
		float a = opacity.f();
		float[] q = crystalCapture.quads();
		for (int i = 0; i < crystalCapture.count(); i++) {
			int o = i * ModelCapture.STRIDE;
			float nx = q[o + 12], ny = q[o + 13], nz = q[o + 14];
			boolean front = nx * (q[o] + q[o + 6]) + ny * (q[o + 1] + q[o + 7]) + nz * (q[o + 2] + q[o + 8]) < 0f;
			float strength = front ? Math.min(1f, a * 1.15f) : a * 0.5f;
			border(consumer, q, o, front, 0.0045f, line, strength);
			border(consumer, q, o, front, 0.013f, c, strength * 0.35f);
		}
		crystalCapture.clear();
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

	/** Every arm captured this frame, drawn once (both arms may be submitted). */
	private void drawArmOutline(MatrixStack.Entry entry, VertexConsumer consumer) {
		drawOutline(armCapture, consumer);
		armCapture.clear();
	}

	/** A first-person item being drawn (ItemRendererMixin): its quads go straight into the outline layer. */
	public static void outlineItem(ItemDisplayContext context, MatrixStack matrices, VertexConsumerProvider consumers, List<BakedQuad> quads) {
		if (!context.isFirstPerson()) return;
		HandChamsModule module = ModuleManager.get(HandChamsModule.class);
		if (module == null || !module.isEnabled() || !module.items.on() || !module.outline()) return;
		ModelCapture capture = module.itemCapture;
		capture.clear();
		MatrixStack.Entry entry = matrices.peek();
		for (int i = 0; i < quads.size(); i++) capture.quad(entry, quads.get(i), 1f, 1f, 1f, 1f, FULL_BRIGHT, OverlayTexture.DEFAULT_UV);
		module.drawOutline(capture, consumers.getBuffer(module.outlineLayer()));
	}

	/**
	 * The rim and the halo of every face that looks towards the camera (the origin of the hand's space): alpha
	 * grows with the squared and cubed fresnel term, so faces seen edge on light up and faces turned to the
	 * camera stay clear.
	 */
	private void drawOutline(ModelCapture capture, VertexConsumer consumer) {
		int c = themeColor.on() ? Theme.accent() : color.argb();
		int halo = ColorUtil.brighten(c, 0.3f);
		float width = outlineWidth.f();
		float[] q = capture.quads();
		for (int i = 0; i < capture.count(); i++) {
			int o = i * ModelCapture.STRIDE;
			float nx = q[o + 12], ny = q[o + 13], nz = q[o + 14];
			if (nx * (q[o] + q[o + 6]) + ny * (q[o + 1] + q[o + 7]) + nz * (q[o + 2] + q[o + 8]) >= 0f) continue;
			shell(consumer, q, o, RIM * width, c, 0.9f, 2);
			shell(consumer, q, o, HALO * width, halo, 0.45f, 3);
		}
	}

	private static void shell(VertexConsumer consumer, float[] q, int o, float inflate, int color, float strength, int power) {
		float nx = q[o + 12], ny = q[o + 13], nz = q[o + 14];
		for (int corner = 0; corner < 12; corner += 3) {
			float x = q[o + corner] + nx * inflate, y = q[o + corner + 1] + ny * inflate, z = q[o + corner + 2] + nz * inflate;
			float facing = -(nx * x + ny * y + nz * z) / Math.max(1e-4f, (float) Math.sqrt(x * x + y * y + z * z));
			float rim = 1f - Math.max(0f, facing);
			float a = power == 2 ? rim * rim : rim * rim * rim;
			consumer.vertex(x, y, z, ColorUtil.withAlpha(color, Math.round(255f * strength * a)), 0.5f, 0.5f, OverlayTexture.DEFAULT_UV, FULL_BRIGHT,
				nx, ny, nz);
		}
	}
}
