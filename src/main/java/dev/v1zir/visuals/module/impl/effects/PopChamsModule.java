package dev.v1zir.visuals.module.impl.effects;

import dev.v1zir.visuals.mixin.LivingEntityRendererInvoker;
import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.module.setting.ColorSetting;
import dev.v1zir.visuals.module.setting.NumberSetting;
import dev.v1zir.visuals.render.world.WorldEffect;
import dev.v1zir.visuals.render.world.WorldEffectRenderer;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A ghost of a player rises and dissolves where they pop a totem. The model is captured once, in
 * vanilla's own pose and transforms, and cut into small cells; each frame only redraws the cells,
 * brighter towards the silhouette, breaking apart from the feet up.
 */
public final class PopChamsModule extends Module implements WorldEffect {
	private static final int MAX_GHOSTS = 6;
	private static final int FULL_BRIGHT = 0xF000F0;
	/** Edge length of the dissolve cells in blocks: two skin pixels. */
	private static final float CELL = 0.125f;
	/** Floats per cell: centre, two half edges, normal, dissolve order, height on the body (0 feet, 1 head). */
	private static final int STRIDE = 14;

	public final NumberSetting duration = add(new NumberSetting("Duration", 1.6, 0.5, 4.0, 0.05).suffix("s"));
	public final NumberSetting rise = add(new NumberSetting("Rise", 1.2, 0.0, 3.0, 0.05).suffix(" m"));
	public final NumberSetting opacity = add(new NumberSetting("Opacity", 0.85, 0.1, 1.0, 0.05));
	public final BoolSetting dissolve = add(new BoolSetting("Dissolve", true));
	public final BoolSetting self = add(new BoolSetting("Self", false));
	public final BoolSetting themeColor = add(new BoolSetting("Theme color", true));
	public final ColorSetting color = add(new ColorSetting("Color", 0xFFFFD36B, false).visibleIf(() -> !themeColor.on()));

	private final List<AbstractClientPlayerEntity> pending = new ArrayList<>();
	private final List<Ghost> ghosts = new ArrayList<>();
	private final Capture capture = new Capture();

	public PopChamsModule() {
		super("Pop Chams", "Ghost of players who pop a totem", Category.EFFECTS);
	}

	@Override
	protected void onDisable() {
		pending.clear();
		ghosts.clear();
	}

	/** A player used a totem of undying (Effects). */
	public void onPop(PlayerEntity player) {
		// Your own ghost would sit around the camera in first person.
		if (player == mc.player && (!self.on() || mc.options.getPerspective().isFirstPerson())) return;
		if (player instanceof AbstractClientPlayerEntity client && !player.isInvisible() && !pending.contains(client)) pending.add(client);
	}

	@Override
	public void render3D(float tickDelta) {
		// Captured during the frame, where entity renderers have their camera as in world rendering.
		for (int i = 0; i < pending.size(); i++) {
			AbstractClientPlayerEntity player = pending.get(i);
			if (player.isRemoved()) continue;
			if (ghosts.size() == MAX_GHOSTS) ghosts.remove(0);
			ghosts.add(capture(player, tickDelta));
		}
		pending.clear();
		if (ghosts.isEmpty()) return;

		long now = System.nanoTime();
		float life = duration.f();
		int a1 = themeColor.on() ? Theme.accent() : color.argb();
		int a2 = themeColor.on() ? Theme.accent2() : ColorUtil.brighten(a1, 0.5f);
		for (int i = ghosts.size() - 1; i >= 0; i--) {
			Ghost ghost = ghosts.get(i);
			float p = (now - ghost.born) / 1.0e9f / life;
			if (p >= 1f) {
				ghosts.remove(i);
				continue;
			}
			draw(ghost, p, a1, a2);
		}
	}

	private Ghost capture(AbstractClientPlayerEntity player, float tickDelta) {
		PlayerEntityRenderer<AbstractClientPlayerEntity> renderer = mc.getEntityRenderDispatcher().getPlayerRenderer(player);
		PlayerEntityRenderState state = renderer.createRenderState();
		renderer.updateRenderState(player, state, tickDelta);
		// The outer skin layers are slightly larger copies of the body; untextured they only double it.
		state.hatVisible = false;
		state.jacketVisible = false;
		state.leftSleeveVisible = false;
		state.rightSleeveVisible = false;
		state.leftPantsLegVisible = false;
		state.rightPantsLegVisible = false;

		// What LivingEntityRenderer.render does before it draws the model.
		MatrixStack matrices = new MatrixStack();
		float scale = state.baseScale;
		matrices.scale(scale, scale, scale);
		LivingEntityRendererInvoker transforms = (LivingEntityRendererInvoker) renderer;
		transforms.v1zir$setupTransforms(state, matrices, state.bodyYaw, scale);
		matrices.scale(-1f, -1f, 1f);
		transforms.v1zir$scale(state, matrices);
		matrices.translate(0f, -1.501f, 0f);

		PlayerEntityModel model = renderer.getModel();
		model.setAngles(state);
		capture.begin();
		model.render(matrices, capture, FULL_BRIGHT, OverlayTexture.DEFAULT_UV);
		Vec3d offset = renderer.getPositionOffset(state);
		return new Ghost(state.x + offset.x, state.y + offset.y, state.z + offset.z, System.nanoTime(), capture.cells());
	}

	private void draw(Ghost ghost, float p, int a1, int a2) {
		WorldEffectRenderer.Mesh fill = WorldEffectRenderer.translucent();
		WorldEffectRenderer.Mesh glow = WorldEffectRenderer.glow();
		boolean breakApart = dissolve.on();
		float fade = (breakApart ? 1f : 1f - p) * opacity.f() * (1f - Anim.clamp01((p - 0.85f) / 0.15f));
		double lift = rise.f() * Anim.easeInOutSine(p);
		float[] c = ghost.cells;
		for (int o = 0; o < c.length; o += STRIDE) {
			// 0 while intact, 1 once the cell has broken off and faded.
			float d = breakApart ? Anim.clamp01((p - 0.1f - c[o + 12] * 0.62f) / 0.26f) : 0f;
			float alpha = fade * (1f - d);
			if (alpha < 0.01f) continue;
			float nx = c[o + 9], ny = c[o + 10], nz = c[o + 11];
			float drift = d * d * 0.45f;
			double x = ghost.x + c[o] + nx * drift;
			double y = ghost.y + c[o + 1] + lift + ny * drift + drift * 1.3f;
			double z = ghost.z + c[o + 2] + nz * drift;
			// Faces seen edge-on are the silhouette: they glow, faces turned to the camera stay faint.
			double vx = x - WorldEffectRenderer.camX, vy = y - WorldEffectRenderer.camY, vz = z - WorldEffectRenderer.camZ;
			float facing = (float) (Math.abs(vx * nx + vy * ny + vz * nz) / Math.max(1e-4, Math.sqrt(vx * vx + vy * vy + vz * vz)));
			float rim = (1f - facing) * (1f - facing);
			int base = ColorUtil.lerp(a1, a2, c[o + 13]);
			float k = 1f - 0.75f * d;
			float ax = c[o + 3] * k, ay = c[o + 4] * k, az = c[o + 5] * k;
			float bx = c[o + 6] * k, by = c[o + 7] * k, bz = c[o + 8] * k;
			quad(fill, x, y, z, ax, ay, az, bx, by, bz, ColorUtil.fade(base, alpha * (0.14f + 0.4f * rim)));
			if (rim > 0.05f) quad(glow, x, y, z, ax, ay, az, bx, by, bz, ColorUtil.fade(base, alpha * 0.8f * rim));
		}
	}

	private static void quad(WorldEffectRenderer.Mesh mesh, double x, double y, double z, float ax, float ay, float az,
							 float bx, float by, float bz, int argb) {
		// u > 2: solid colour, no falloff.
		mesh.vertex(x - ax - bx, y - ay - by, z - az - bz, 3f, 0f, argb);
		mesh.vertex(x + ax - bx, y + ay - by, z + az - bz, 3f, 0f, argb);
		mesh.vertex(x + ax + bx, y + ay + by, z + az + bz, 3f, 0f, argb);
		mesh.vertex(x - ax + bx, y - ay + by, z - az + bz, 3f, 0f, argb);
	}

	private record Ghost(double x, double y, double z, long born, float[] cells) {
	}

	/** Receives the model's quads and cuts every face into cells of about {@link #CELL} blocks; only positions and normals are kept. */
	private static final class Capture implements VertexConsumer {
		private final float[] corners = new float[12];
		private float normalX, normalY, normalZ;
		private int corner;
		private float[] cells = new float[STRIDE * 512];
		private int count;

		void begin() {
			corner = 0;
			count = 0;
		}

		float[] cells() {
			return Arrays.copyOf(cells, count * STRIDE);
		}

		@Override
		public void vertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float nx, float ny, float nz) {
			normal(nx, ny, nz);
			vertex(x, y, z);
		}

		@Override
		public VertexConsumer vertex(float x, float y, float z) {
			int o = corner * 3;
			corners[o] = x;
			corners[o + 1] = y;
			corners[o + 2] = z;
			if (++corner == 4) {
				corner = 0;
				face();
			}
			return this;
		}

		@Override
		public VertexConsumer normal(float x, float y, float z) {
			normalX = x;
			normalY = y;
			normalZ = z;
			return this;
		}

		/** The face is v0 + s (v1 - v0) + t (v3 - v0) for s, t in 0..1. */
		private void face() {
			float[] v = corners;
			float ux = v[3] - v[0], uy = v[4] - v[1], uz = v[5] - v[2];
			float wx = v[9] - v[0], wy = v[10] - v[1], wz = v[11] - v[2];
			float lu = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
			float lw = (float) Math.sqrt(wx * wx + wy * wy + wz * wz);
			float ln = (float) Math.sqrt(normalX * normalX + normalY * normalY + normalZ * normalZ);
			if (lu < 1e-4f || lw < 1e-4f || ln < 1e-4f) return;
			int su = Math.max(1, Math.round(lu / CELL));
			int sw = Math.max(1, Math.round(lw / CELL));
			if (cells.length < (count + su * sw) * STRIDE) cells = Arrays.copyOf(cells, Math.max(cells.length * 2, (count + su * sw) * STRIDE));
			ThreadLocalRandom random = ThreadLocalRandom.current();
			for (int i = 0; i < su; i++) {
				for (int j = 0; j < sw; j++) {
					float fu = (i + 0.5f) / su;
					float fw = (j + 0.5f) / sw;
					int o = count++ * STRIDE;
					cells[o] = v[0] + ux * fu + wx * fw;
					cells[o + 1] = v[1] + uy * fu + wy * fw;
					cells[o + 2] = v[2] + uz * fu + wz * fw;
					cells[o + 3] = ux / (2 * su);
					cells[o + 4] = uy / (2 * su);
					cells[o + 5] = uz / (2 * su);
					cells[o + 6] = wx / (2 * sw);
					cells[o + 7] = wy / (2 * sw);
					cells[o + 8] = wz / (2 * sw);
					cells[o + 9] = normalX / ln;
					cells[o + 10] = normalY / ln;
					cells[o + 11] = normalZ / ln;
					float height = Anim.clamp01(cells[o + 1] / 1.9f);
					// Mostly from the feet up, with some noise so the edge is ragged.
					cells[o + 12] = 0.55f * height + 0.45f * random.nextFloat();
					cells[o + 13] = height;
				}
			}
		}

		// Colour, texture and lighting are not needed for the ghost.

		@Override
		public VertexConsumer color(int red, int green, int blue, int alpha) {
			return this;
		}

		@Override
		public VertexConsumer color(int argb) {
			return this;
		}

		@Override
		public VertexConsumer texture(float u, float v) {
			return this;
		}

		@Override
		public VertexConsumer overlay(int u, int v) {
			return this;
		}

		@Override
		public VertexConsumer light(int u, int v) {
			return this;
		}

		@Override
		public VertexConsumer lineWidth(float width) {
			return this;
		}
	}
}
