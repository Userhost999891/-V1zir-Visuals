package dev.v1zir.visuals.render.cosmetic;

import dev.v1zir.visuals.render.V1zirPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.Arrays;

/**
 * Draws cosmetic models through the game's render command queue, lit like the wearer (light map, vanilla's lights,
 * fog): solid parts with the cosmetic pipeline, glowing and translucent ones blended after them. Matrices arrive in
 * the space of the body part (model space, y down); the meshes are in pixels with y up, so each draw is turned and
 * scaled into it. Each part's motion (wing beats, a halo's float, a propeller's spin) is applied here; a cape is laid
 * vertex by vertex onto its simulated cloth (CapeCloth) as it is written. Draw objects come from a pool reused every
 * frame.
 */
public final class CosmeticRenderer {
	private static final float DEG = (float) (Math.PI / 180.0);
	/** Mesh pixels (x right, y up) into model space (x left, y down): half a turn about z, 1/16 block per pixel. */
	private static final Quaternionf FLIP = new Quaternionf().rotationZ((float) Math.PI);
	private static final Quaternionf TURN = new Quaternionf();

	private static RenderLayer solid;
	private static RenderLayer glow;
	private static Draw[] draws = new Draw[16];
	private static int used;

	private CosmeticRenderer() {
	}

	/** Once per frame: the queue has drawn everything submitted, so the pool starts over. */
	public static void flip() {
		used = 0;
	}

	private static RenderLayer solid() {
		if (solid == null) solid = RenderLayer.of("v1zir_cosmetic", RenderSetup.builder(V1zirPipelines.COSMETIC).useLightmap().build());
		return solid;
	}

	private static RenderLayer glow() {
		if (glow == null) glow = RenderLayer.of("v1zir_cosmetic_glow", RenderSetup.builder(V1zirPipelines.COSMETIC_GLOW).useLightmap().translucent().build());
		return glow;
	}

	/** Submits an item worn on the body part whose space the matrices are in; a cape lies on its cloth (or hangs straight). */
	public static void submit(MatrixStack matrices, OrderedRenderCommandQueue queue, Cosmetic item, int light, CosmeticMotion motion,
							  @Nullable CapeCloth cloth) {
		CosmeticMesh mesh = item.mesh();
		matrices.push();
		matrices.multiply(FLIP);
		matrices.scale(1f / 16f, 1f / 16f, 1f / 16f);
		CosmeticMesh.Part[] parts = mesh.parts();
		boolean cape = item.motion == Cosmetic.Motion.CAPE && cloth != null;
		for (int i = 0; i < parts.length; i++) {
			CosmeticMesh.Part part = parts[i];
			matrices.push();
			move(matrices, item.motion, i, part, motion);
			queue.submitCustom(matrices, part.glow() ? glow() : solid(), draw(mesh, part, light, motion, cape ? cloth : null));
			matrices.pop();
		}
		matrices.pop();
	}

	/** A part's own motion about its pivot (mesh pixels). */
	static void move(MatrixStack matrices, Cosmetic.Motion kind, int index, CosmeticMesh.Part part, CosmeticMotion motion) {
		float t = motion.time;
		switch (kind) {
			case HALO -> {
				matrices.translate(part.pivotX(), part.pivotY() + (float) Math.sin(t * 1.7f) * 0.35f, part.pivotZ());
				matrices.multiply(TURN.rotationY(t * 22f * DEG));
				matrices.translate(-part.pivotX(), -part.pivotY(), -part.pivotZ());
			}
			case EARS -> {
				// A quick twitch every few seconds, the ears out of step.
				float cycle = (t + index * 1.3f) % 4.2f;
				float twitch = cycle < 0.3f ? (float) Math.sin(cycle / 0.3f * Math.PI) * 14f : 0f;
				float side = index == 0 ? -1f : 1f;
				matrices.translate(part.pivotX(), part.pivotY(), part.pivotZ());
				matrices.multiply(TURN.rotationZ(side * twitch * DEG));
				matrices.translate(-part.pivotX(), -part.pivotY(), -part.pivotZ());
			}
			case PROPELLER -> {
				if (index != 1) return;
				matrices.translate(part.pivotX(), part.pivotY(), part.pivotZ());
				matrices.multiply(TURN.rotationY(motion.spin * DEG));
				matrices.translate(-part.pivotX(), -part.pivotY(), -part.pivotZ());
			}
			case WINGS, FLUTTER -> {
				// Parts 0 and 1 are the wings; any others (a pack on the back) stay still.
				if (index > 1) return;
				// Open from the built sweep (28 degrees back) to the wearer's opening, plus the beat; mirrored per side.
				float beat = kind == Cosmetic.Motion.FLUTTER ? motion.wingBeat * 1.6f : motion.wingBeat;
				float open = motion.wingOpen + beat - 28f;
				float side = index == 0 ? -1f : 1f;
				matrices.translate(part.pivotX(), part.pivotY(), part.pivotZ());
				matrices.multiply(TURN.rotationY(side * open * DEG));
				matrices.translate(-part.pivotX(), -part.pivotY(), -part.pivotZ());
			}
			default -> {
			}
		}
	}

	private static Draw draw(CosmeticMesh mesh, CosmeticMesh.Part part, int light, CosmeticMotion motion, @Nullable CapeCloth cloth) {
		if (used == draws.length) draws = Arrays.copyOf(draws, used * 2);
		Draw draw = draws[used];
		if (draw == null) draws[used] = draw = new Draw();
		used++;
		draw.mesh = mesh;
		draw.part = part;
		draw.light = light;
		draw.time = ((int) (motion.time * 50f) & 0x7FFF) << 16;
		draw.cape = cloth != null;
		// The cloth as it is now: the queue draws later, after other wearers may have moved theirs.
		if (cloth != null) cloth.frames(draw.cloth);
		return draw;
	}

	/** One part of one item this frame; writes its triangles when the queue reaches it. */
	private static final class Draw implements OrderedRenderCommandQueue.Custom {
		CosmeticMesh mesh;
		CosmeticMesh.Part part;
		int light;
		int time;
		boolean cape;
		/** The cloth's frames (CapeCloth.frames) when this is a cape. */
		final float[] cloth = new float[CapeCloth.COUNT * 12];
		private final float[] at = new float[12];

		@Override
		public void render(MatrixStack.Entry entry, VertexConsumer consumer) {
			Matrix4f pose = entry.getPositionMatrix();
			Matrix3f normal = entry.getNormalMatrix();
			float[] data = mesh.data;
			int[] indices = mesh.indices;
			int end = part.firstIndex() + part.indexCount();
			for (int i = part.firstIndex(); i < end; i++) {
				int v = indices[i];
				int o = v * CosmeticMesh.FLOATS;
				float x = data[o];
				float y = data[o + 1];
				float z = data[o + 2];
				float nx = data[o + 3];
				float ny = data[o + 4];
				float nz = data[o + 5];
				if (cape) {
					// Where the vertex sits on the straight cape (down the rows, across the columns), then the same place
					// on the cloth: its point there, off it by the vertex's depth along the cloth's normal, its normal
					// turned with the cloth. Past the cloth's edges (a clasp, a collar) along its tangents.
					float s = CosmeticModels.CAPE_TOP - y;
					float sc = Math.max(0f, Math.min(CapeCloth.ROWS - 1f, s));
					int r0 = Math.min((int) sc, CapeCloth.ROWS - 2);
					float f = sc - r0;
					float half = CapeCloth.half(sc);
					float u = (x / half + 1f) * 0.5f * (CapeCloth.COLS - 1);
					float uc = Math.max(0f, Math.min(CapeCloth.COLS - 1f, u));
					int c0 = Math.min((int) uc, CapeCloth.COLS - 2);
					float g = uc - c0;
					sample(r0, c0, f, g);
					float across = (u - uc) * 2f * half / (CapeCloth.COLS - 1);
					float along = sc - s;
					float d = z - CosmeticModels.CAPE_Z;
					x = at[0] + at[3] * across + at[6] * along + at[9] * d;
					y = at[1] + at[4] * across + at[7] * along + at[10] * d;
					z = at[2] + at[5] * across + at[8] * along + at[11] * d;
					float rnx = at[3] * nx + at[6] * ny + at[9] * nz;
					float rny = at[4] * nx + at[7] * ny + at[10] * nz;
					float rnz = at[5] * nx + at[8] * ny + at[11] * nz;
					nx = rnx;
					ny = rny;
					nz = rnz;
				}
				float tx = normal.m00() * nx + normal.m10() * ny + normal.m20() * nz;
				float ty = normal.m01() * nx + normal.m11() * ny + normal.m21() * nz;
				float tz = normal.m02() * nx + normal.m12() * ny + normal.m22() * nz;
				float len = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
				if (len > 1e-6f) {
					tx /= len;
					ty /= len;
					tz /= len;
				}
				consumer.vertex(
					pose.m00() * x + pose.m10() * y + pose.m20() * z + pose.m30(),
					pose.m01() * x + pose.m11() * y + pose.m21() * z + pose.m31(),
					pose.m02() * x + pose.m12() * y + pose.m22() * z + pose.m32(),
					mesh.color[v], data[o + 6], data[o + 7], mesh.material[v] | time, light, tx, ty, tz);
			}
		}

		/** The cloth's frame bilinearly between its grid points (r0, c0) and (r0 + 1, c0 + 1), into at. */
		private void sample(int r0, int c0, float f, float g) {
			int a = (r0 * CapeCloth.COLS + c0) * 12;
			int b = a + 12;
			int c = a + CapeCloth.COLS * 12;
			int d = c + 12;
			float wa = (1f - f) * (1f - g), wb = (1f - f) * g, wc = f * (1f - g), wd = f * g;
			for (int k = 0; k < 12; k++) at[k] = cloth[a + k] * wa + cloth[b + k] * wb + cloth[c + k] * wc + cloth[d + k] * wd;
		}
	}
}
