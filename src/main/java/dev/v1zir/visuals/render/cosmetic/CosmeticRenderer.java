package dev.v1zir.visuals.render.cosmetic;

import dev.v1zir.visuals.render.V1zirPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.Arrays;

/**
 * Draws cosmetic models through the game's render command queue, lit like the wearer (light map, vanilla's lights,
 * fog): solid parts with the cosmetic pipeline, glowing and translucent ones blended after them. Matrices arrive in
 * the space of the body part (model space, y down); the meshes are in pixels with y up, so each draw is turned and
 * scaled into it. Each part's motion (wing beats, a halo's float, a cape's lift and ripple) is applied here; capes
 * bend vertex by vertex as they are written. Draw objects come from a pool reused every frame.
 */
public final class CosmeticRenderer {
	private static final float DEG = (float) (Math.PI / 180.0);
	/** Mesh pixels (x right, y up) into model space (x left, y down): half a turn about z, 1/16 block per pixel. */
	private static final Quaternionf FLIP = new Quaternionf().rotationZ((float) Math.PI);
	private static final Quaternionf TURN = new Quaternionf();
	private static final int CAPE_ROWS = 17;

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

	/** Submits an item worn on the body part whose space the matrices are in. */
	public static void submit(MatrixStack matrices, OrderedRenderCommandQueue queue, Cosmetic item, int light, CosmeticMotion motion) {
		CosmeticMesh mesh = item.mesh();
		matrices.push();
		matrices.multiply(FLIP);
		matrices.scale(1f / 16f, 1f / 16f, 1f / 16f);
		CosmeticMesh.Part[] parts = mesh.parts();
		for (int i = 0; i < parts.length; i++) {
			CosmeticMesh.Part part = parts[i];
			matrices.push();
			move(matrices, item.motion, i, part, motion);
			queue.submitCustom(matrices, part.glow() ? glow() : solid(), draw(mesh, part, light, motion, item.motion == Cosmetic.Motion.CAPE));
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
			case WINGS, FLUTTER -> {
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

	private static Draw draw(CosmeticMesh mesh, CosmeticMesh.Part part, int light, CosmeticMotion motion, boolean cape) {
		if (used == draws.length) draws = Arrays.copyOf(draws, used * 2);
		Draw draw = draws[used];
		if (draw == null) draws[used] = draw = new Draw();
		used++;
		draw.mesh = mesh;
		draw.part = part;
		draw.light = light;
		draw.time = ((int) (motion.time * 50f) & 0x7FFF) << 16;
		draw.cape = cape;
		if (cape) bend(draw, motion);
		return draw;
	}

	/**
	 * The cape's centre line, row by row from the shoulders: each row turned back by the lift (more towards the hem,
	 * as cloth trails), plus a ripple running down it; integrated into positions so the cloth keeps its length.
	 */
	private static void bend(Draw draw, CosmeticMotion motion) {
		float y = CosmeticModels.CAPE_TOP;
		float z = 0f;
		for (int r = 0; r < CAPE_ROWS; r++) {
			float s = r / (float) (CAPE_ROWS - 1);
			float angle = motion.capeLift * (float) Math.pow(s, 0.7f) + motion.capeRipple * s * (float) Math.sin(motion.time * 6f - r * 0.55f);
			float a = angle * DEG;
			draw.rowY[r] = y;
			draw.rowZ[r] = z;
			draw.rowSin[r] = (float) Math.sin(a);
			draw.rowCos[r] = (float) Math.cos(a);
			// The next row is one pixel further along the cloth, in this row's direction.
			y -= draw.rowCos[r];
			z += draw.rowSin[r];
		}
		draw.sway = motion.capeSway;
	}

	/** One part of one item this frame; writes its triangles when the queue reaches it. */
	private static final class Draw implements OrderedRenderCommandQueue.Custom {
		CosmeticMesh mesh;
		CosmeticMesh.Part part;
		int light;
		int time;
		boolean cape;
		float sway;
		final float[] rowY = new float[CAPE_ROWS];
		final float[] rowZ = new float[CAPE_ROWS];
		final float[] rowSin = new float[CAPE_ROWS];
		final float[] rowCos = new float[CAPE_ROWS];

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
					// Row position along the cape and depth across the cloth, then laid along the bent centre line.
					float s = Math.max(0f, Math.min(CAPE_ROWS - 1f, CosmeticModels.CAPE_TOP - y));
					int r0 = Math.min((int) s, CAPE_ROWS - 2);
					float f = s - r0;
					float cy = rowY[r0] + (rowY[r0 + 1] - rowY[r0]) * f;
					float cz = rowZ[r0] + (rowZ[r0 + 1] - rowZ[r0]) * f;
					float sin = rowSin[r0] + (rowSin[r0 + 1] - rowSin[r0]) * f;
					float cos = rowCos[r0] + (rowCos[r0 + 1] - rowCos[r0]) * f;
					float d = z - CosmeticModels.CAPE_Z;
					x += sway * s * 16f / CAPE_ROWS * (x > 0 ? 0.9f : 1.1f);
					y = cy + sin * d;
					z = CosmeticModels.CAPE_Z + cz + cos * d;
					float rny = ny * cos + nz * sin;
					float rnz = -ny * sin + nz * cos;
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
	}
}
