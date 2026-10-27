package dev.v1zir.visuals.render.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.v1zir.visuals.V1zir;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.ClientPlayerLikeEntity;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Heads for the GUI and the HUD, drawn through the rounded image path so they fade, scale and layer with the
 * panel around them, as crisp pixel art: a skin's face and hat, or any other entity's face cut from its own
 * texture the same way.
 */
public final class Heads {
	/** Face and hat regions of a 64x64 skin. */
	private static final float FACE_U = 8f / 64f;
	private static final float HAT_U = 40f / 64f;
	private static final float REGION = 8f / 64f;

	@Nullable
	private static GpuTextureView skinView;
	private static TextureSetup skinSetup;
	private static final Face FACE = new Face();

	private Heads() {
	}

	/** The skin of a player-like entity (players, with or without a tab list entry, and mannequins), or null. */
	@Nullable
	public static SkinTextures skinOf(Entity entity) {
		return entity instanceof ClientPlayerLikeEntity player ? player.getSkin() : null;
	}

	/** A skin's face with its hat layer over it, in a rounded square; color tints it and carries its opacity. */
	public static void skin(DrawContext ctx, SkinTextures skin, float x, float y, float size, float radius, int color) {
		GpuTextureView view = MinecraftClient.getInstance().getTextureManager().getTexture(skin.body().texturePath()).getGlTextureView();
		if (view != skinView) {
			skinView = view;
			skinSetup = TextureSetup.of(view, RenderSystem.getSamplerCache().get(FilterMode.NEAREST));
		}
		Render2D.image(ctx, skinSetup, x, y, size, size, radius, color, FACE_U, FACE_U, FACE_U + REGION, FACE_U + REGION, ImageElement.PIXEL_ART);
		Render2D.image(ctx, skinSetup, x, y, size, size, radius, color, HAT_U, FACE_U, HAT_U + REGION, FACE_U + REGION, ImageElement.PIXEL_ART);
	}

	/**
	 * Any other entity's face cut from its own texture, drawn like a skin's face in the rounded square (x, y, size).
	 * False when it has no head to cut it from (or its renderer failed): show an icon instead.
	 */
	public static boolean face(DrawContext ctx, LivingEntity entity, float tickDelta, float x, float y, float size, float radius, int color) {
		return FACE.draw(ctx, entity, tickDelta, x, y, size, radius, color);
	}

	/**
	 * The face of the current target as its model shows it from the front: the front (north) faces of the cuboids of
	 * the model's "head" part, which is where a skin keeps its face too, back to front, so hat layers, snouts, beaks
	 * and noses sit on the skull, cropped to a square around the skull's face. Parts turned away from the front (ears,
	 * hat brims) are left out. Laid out once per model; drawing it costs one image per layer.
	 */
	private static final class Face {
		private static final int MAX_LAYERS = 12;
		private static final float EDGE = 1e-3f;
		/** Per layer the rect (x0, y0, x1, y1) and its texture region (u0, v0, u1, v1): model pixels until laid out, then the square's 0 to 1. */
		private final float[] layer = new float[MAX_LAYERS * 8];
		/** How far back each layer's front lies (model pixels), and the corners of the square it shares (those are rounded). */
		private final float[] depth = new float[MAX_LAYERS];
		private final int[] corners = new int[MAX_LAYERS];
		private int layers;
		@Nullable
		private EntityModel<?> model;
		@Nullable
		private LivingEntity entity;
		@Nullable
		private LivingEntity failed;
		private int age;
		@Nullable
		private EntityRenderer<?, ?> renderer;
		@Nullable
		private LivingEntityRenderState state;
		@Nullable
		private Identifier texture;
		@Nullable
		private GpuTextureView view;
		@Nullable
		private TextureSetup setup;

		boolean draw(DrawContext ctx, LivingEntity e, float tickDelta, float x, float y, float size, float radius, int color) {
			if (e == failed) return false;
			// Once a tick: the texture follows the entity's state (a wolf turning angry, a baby growing up).
			if ((e != entity || e.age != age) && !update(e, tickDelta)) {
				failed = e;
				entity = null;
				return false;
			}
			GpuTextureView v = MinecraftClient.getInstance().getTextureManager().getTexture(texture).getGlTextureView();
			if (v != view) {
				view = v;
				setup = TextureSetup.of(v, RenderSystem.getSamplerCache().get(FilterMode.NEAREST));
			}
			float[] l = layer;
			for (int i = 0, o = 0; i < layers; i++, o += 8) {
				Render2D.image(ctx, setup, x + l[o] * size, y + l[o + 1] * size, (l[o + 2] - l[o]) * size, (l[o + 3] - l[o + 1]) * size,
					corners[i] != 0 ? radius : 0f, color, l[o + 4], l[o + 5], l[o + 6], l[o + 7], corners[i] | ImageElement.PIXEL_ART);
			}
			return true;
		}

		/** Fresh render state, texture and (for a new model) layout; false if the entity has no face to show. */
		private boolean update(LivingEntity e, float tickDelta) {
			try {
				EntityRenderer<?, ?> current = MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(e);
				if (!(current instanceof LivingEntityRenderer<?, ?, ?> living)) return false;
				if (e != entity || current != renderer) {
					entity = e;
					renderer = current;
					state = living.createRenderState();
				}
				age = e.age;
				fill(living, e, state, tickDelta);
				texture = texture(living, state);
				// Checked every time: some renderers swap models with the state (adult and baby).
				EntityModel<?> m = living.getModel();
				if (m != model) {
					model = m;
					layout(find(m.getRootPart()));
				}
			} catch (RuntimeException ex) {
				V1zir.LOGGER.warn("Cannot cut the face of {} from its texture", e, ex);
				model = null;
				return false;
			}
			return layers > 0 && texture != null;
		}

		@SuppressWarnings({"unchecked", "rawtypes"})
		private static void fill(EntityRenderer renderer, LivingEntity entity, LivingEntityRenderState state, float tickDelta) {
			renderer.updateRenderState(entity, state, tickDelta);
		}

		@SuppressWarnings({"unchecked", "rawtypes"})
		private static Identifier texture(LivingEntityRenderer renderer, LivingEntityRenderState state) {
			return renderer.getTexture(state);
		}

		/** The part called "head": a child of the root in most models, deeper in some (a horse's, an axolotl's). */
		@Nullable
		private static ModelPart find(ModelPart part) {
			ModelPart head = part.children.get("head");
			if (head != null) return head;
			for (ModelPart child : part.children.values()) {
				head = find(child);
				if (head != null) return head;
			}
			return null;
		}

		private void layout(@Nullable ModelPart head) {
			layers = 0;
			if (head == null) return;
			collect(head, 0f, 0f, 0f);
			float[] l = layer;
			// The skull is the first cuboid; its front, cropped to a centred square, frames the face.
			float side = Math.min(l[2] - l[0], l[3] - l[1]);
			if (layers == 0 || side <= 0f) {
				layers = 0;
				return;
			}
			float fx = (l[0] + l[2] - side) * 0.5f;
			float fy = (l[1] + l[3] - side) * 0.5f;
			int kept = 0;
			for (int i = 0; i < layers; i++) {
				int o = i * 8;
				float x0 = l[o], y0 = l[o + 1], x1 = l[o + 2], y1 = l[o + 3];
				float cx0 = Math.max(x0, fx), cy0 = Math.max(y0, fy);
				float cx1 = Math.min(x1, fx + side), cy1 = Math.min(y1, fy + side);
				// Outside the square: horns, ears and the parts of a long face beyond it.
				if (cx1 - cx0 < EDGE || cy1 - cy0 < EDGE) continue;
				float du = (l[o + 6] - l[o + 4]) / (x1 - x0);
				float dv = (l[o + 7] - l[o + 5]) / (y1 - y0);
				int k = kept * 8;
				float u0 = l[o + 4], v0 = l[o + 5];
				l[k + 4] = u0 + (cx0 - x0) * du;
				l[k + 5] = v0 + (cy0 - y0) * dv;
				l[k + 6] = u0 + (cx1 - x0) * du;
				l[k + 7] = v0 + (cy1 - y0) * dv;
				l[k] = (cx0 - fx) / side;
				l[k + 1] = (cy0 - fy) / side;
				l[k + 2] = (cx1 - fx) / side;
				l[k + 3] = (cy1 - fy) / side;
				boolean left = l[k] < EDGE, top = l[k + 1] < EDGE, right = l[k + 2] > 1f - EDGE, bottom = l[k + 3] > 1f - EDGE;
				corners[kept] = (top && left ? Render2D.CORNER_TL : 0) | (top && right ? Render2D.CORNER_TR : 0)
					| (bottom && right ? Render2D.CORNER_BR : 0) | (bottom && left ? Render2D.CORNER_BL : 0);
				depth[kept] = depth[i];
				kept++;
			}
			layers = kept;
			// Back to front; equal depths keep the model's order (a hat layer comes after the skull it covers).
			for (int i = 1; i < layers; i++) {
				for (int j = i; j > 0 && depth[j - 1] < depth[j]; j--) swap(j - 1, j);
			}
		}

		/** Adds the front face of every cuboid of part, and of the children sitting straight on it; (ox, oy, oz) is part's pivot in the head. */
		private void collect(ModelPart part, float ox, float oy, float oz) {
			if (!part.hidden) {
				for (ModelPart.Cuboid cuboid : part.cuboids) {
					for (ModelPart.Quad quad : cuboid.sides) {
						if (quad.direction().z() > -0.5f || layers == MAX_LAYERS) continue;
						int o = layers * 8;
						layer[o] = cuboid.minX + ox;
						layer[o + 1] = cuboid.minY + oy;
						layer[o + 2] = cuboid.maxX + ox;
						layer[o + 3] = cuboid.maxY + oy;
						float u0 = Float.MAX_VALUE, v0 = Float.MAX_VALUE, u1 = -Float.MAX_VALUE, v1 = -Float.MAX_VALUE;
						for (ModelPart.Vertex vertex : quad.vertices()) {
							u0 = Math.min(u0, vertex.u());
							v0 = Math.min(v0, vertex.v());
							u1 = Math.max(u1, vertex.u());
							v1 = Math.max(v1, vertex.v());
						}
						layer[o + 4] = u0;
						layer[o + 5] = v0;
						layer[o + 6] = u1;
						layer[o + 7] = v1;
						// Where the face is drawn, inflation included: a hat layer lies in front of the skull, a shrunken inner layer behind it.
						depth[layers] = quad.vertices()[0].worldZ() * 16f + oz;
						layers++;
					}
				}
			}
			for (ModelPart child : part.children.values()) {
				// The resting pose: rendering poses the model anew every frame, so this changes nothing on screen.
				child.resetTransform();
				if (child.pitch == 0f && child.yaw == 0f && child.roll == 0f && child.xScale == 1f && child.yScale == 1f && child.zScale == 1f) {
					collect(child, ox + child.originX, oy + child.originY, oz + child.originZ);
				}
			}
		}

		private void swap(int a, int b) {
			for (int i = 0; i < 8; i++) {
				float t = layer[a * 8 + i];
				layer[a * 8 + i] = layer[b * 8 + i];
				layer[b * 8 + i] = t;
			}
			float d = depth[a];
			depth[a] = depth[b];
			depth[b] = d;
			int c = corners[a];
			corners[a] = corners[b];
			corners[b] = c;
		}
	}
}
