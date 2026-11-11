package dev.v1zir.visuals.render.knife;

import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.impl.knives.KnivesModule;
import dev.v1zir.visuals.render.V1zirPipelines;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.state.ArmedEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.Arrays;

/**
 * Draws knife models through the game's render command queue with the knife pipeline, so a knife is sorted, depth
 * tested and lit (light map, vanilla's directional lights, fog) like the item it replaces: in the first-person hand
 * and in players' hands in the world. Vertices are written straight from the cached mesh; each submitted part needs a
 * draw object until the queue runs later in the frame, so they come from a pool that is reused every frame.
 */
public final class KnifeRenderer {
	private static final float DEG = (float) (Math.PI / 180.0);
	/** Centimetres to blocks for knives held by players in the world, sized to the player model's hand. */
	private static final float WORLD_SCALE = 0.024f;
	/** Third person: the knife's axes in the hand's item frame (x right, y forward, z up the arm). */
	private static final Quaternionf FORWARD_HOLD = new Quaternionf().setFromNormalized(new Matrix3f(0f, 1f, 0f, 0f, 0f, 1f, 1f, 0f, 0f))
		.rotateZ(18f * DEG);
	/** Reverse grip: the blade below the fist, its curve towards the front. */
	private static final Quaternionf REVERSE_HOLD = new Quaternionf().setFromNormalized(new Matrix3f(0f, 0f, -1f, 0f, -1f, 0f, -1f, 0f, 0f));
	private static final float[] AT_REST = new float[3];
	private static final Quaternionf TURN = new Quaternionf();

	private static RenderLayer layer;
	private static Draw[] draws = new Draw[8];
	private static int used;

	private KnifeRenderer() {
	}

	/** Once per frame: the queue has drawn everything submitted, so the pool starts over. */
	public static void flip() {
		used = 0;
	}

	private static RenderLayer layer() {
		if (layer == null) layer = RenderLayer.of("v1zir_knife", RenderSetup.builder(V1zirPipelines.KNIFE).useLightmap().build());
		return layer;
	}

	/** Submits the knife at the current matrices (centimetres), each part turned by its angle about its pivot. */
	static void submit(MatrixStack matrices, OrderedRenderCommandQueue queue, KnifeMesh mesh, float[] angles, int finish, int light) {
		KnifeMesh.Part[] parts = mesh.parts();
		for (int i = 0; i < parts.length; i++) {
			KnifeMesh.Part part = parts[i];
			float angle = i < angles.length ? angles[i] : 0f;
			boolean turned = angle != 0f;
			if (turned) {
				matrices.push();
				matrices.translate(part.pivotX(), part.pivotY(), 0f);
				matrices.multiply(TURN.rotationZ(angle * DEG));
				matrices.translate(-part.pivotX(), -part.pivotY(), 0f);
			}
			queue.submitCustom(matrices, layer(), draw(mesh, part, finish, light));
			if (turned) matrices.pop();
		}
	}

	private static Draw draw(KnifeMesh mesh, KnifeMesh.Part part, int finish, int light) {
		if (used == draws.length) draws = Arrays.copyOf(draws, used * 2);
		Draw draw = draws[used];
		if (draw == null) draws[used] = draw = new Draw();
		used++;
		draw.mesh = mesh;
		draw.part = part;
		draw.finish = finish;
		draw.light = light;
		return draw;
	}

	/**
	 * A knife in a player's hand in the world, replacing HeldItemFeatureRenderer's item draw: matrices are at the
	 * hand as the item would be drawn. Returns false to leave the item as it is.
	 */
	public static boolean thirdPerson(ArmedEntityRenderState state, ItemStack stack, MatrixStack matrices, OrderedRenderCommandQueue queue, int light) {
		KnivesModule module = ModuleManager.get(KnivesModule.class);
		if (module == null || !module.isEnabled() || !module.replaces(stack)) return false;
		if (state.entityType != EntityType.PLAYER || !(state instanceof PlayerEntityRenderState player)) return false;
		MinecraftClient mc = MinecraftClient.getInstance();
		if (!module.others.on() && (mc.player == null || player.id != mc.player.getId())) return false;
		KnifeType type = module.type();
		matrices.push();
		// The grip point a little into the fist; the knife forward and tipped up, or below the fist in a reverse grip.
		matrices.translate(0f, -0.02f, 0.05f);
		matrices.multiply(type.grip == KnifeType.Grip.REVERSE ? REVERSE_HOLD : FORWARD_HOLD);
		matrices.scale(WORLD_SCALE, WORLD_SCALE, WORLD_SCALE);
		submit(matrices, queue, type.worldMesh(), AT_REST, module.finish().ordinal(), light);
		matrices.pop();
		return true;
	}

	/** One part of one knife this frame; writes its triangles when the queue reaches it. */
	private static final class Draw implements OrderedRenderCommandQueue.Custom {
		KnifeMesh mesh;
		KnifeMesh.Part part;
		int finish;
		int light;

		@Override
		public void render(MatrixStack.Entry entry, VertexConsumer consumer) {
			Matrix4f pose = entry.getPositionMatrix();
			Matrix3f normal = entry.getNormalMatrix();
			float[] data = mesh.data;
			int[] indices = mesh.indices;
			int[] material = mesh.material;
			int[] color = mesh.color;
			// The overlay coordinates carry the material and the finish to the shader (UV1).
			int surface = finish << 16;
			int end = part.firstIndex() + part.indexCount();
			for (int i = part.firstIndex(); i < end; i++) {
				int v = indices[i];
				int o = v * KnifeMesh.FLOATS;
				float x = data[o];
				float y = data[o + 1];
				float z = data[o + 2];
				float nx = data[o + 3];
				float ny = data[o + 4];
				float nz = data[o + 5];
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
					color[v], data[o + 6], data[o + 7], material[v] | surface, light, tx, ty, tz);
			}
		}
	}
}
