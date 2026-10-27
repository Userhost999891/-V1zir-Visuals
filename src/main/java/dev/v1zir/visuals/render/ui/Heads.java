package dev.v1zir.visuals.render.ui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.v1zir.visuals.V1zir;
import dev.v1zir.visuals.render.V1zirPipelines;
import dev.v1zir.visuals.util.ColorUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.gui.render.state.special.EntityGuiElementRenderState;
import net.minecraft.client.gui.render.state.special.SpecialGuiElementRenderState;
import net.minecraft.client.network.ClientPlayerLikeEntity;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.SkinTextures;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.util.Objects;

/**
 * Heads for the GUI and the HUD, drawn through the rounded image path so they fade, scale and layer with the
 * panel around them: a skin's face and hat as crisp pixel art, or a live 3D portrait of any other entity's
 * head (vanilla's GUI entity renderer draws it offscreen, SpecialGuiElementRendererMixin hands the result here).
 */
public final class Heads {
	/** Face and hat regions of a 64x64 skin. */
	private static final float FACE_U = 8f / 64f;
	private static final float HAT_U = 40f / 64f;
	private static final float REGION = 8f / 64f;

	@Nullable
	private static GpuTextureView skinView;
	private static TextureSetup skinSetup;
	private static final Portrait PORTRAIT = new Portrait();

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
	 * A live 3D portrait of the entity's head filling the rounded square (x, y, size), scaled about its centre by
	 * squash (the hurt bounce). False when the entity cannot be drawn (invisible, or its renderer failed): show an
	 * icon instead.
	 */
	public static boolean portrait(DrawContext ctx, LivingEntity entity, float tickDelta, float x, float y, float size, float radius,
								   float squash, int color) {
		return PORTRAIT.draw(ctx, entity, tickDelta, x, y, size, radius, squash, color);
	}

	/** The offscreen render of a GUI element is done (SpecialGuiElementRendererMixin); true if a portrait shows it instead of vanilla's blit. */
	public static boolean portraitRendered(SpecialGuiElementRenderState element, @Nullable GpuTextureView view) {
		return PORTRAIT.rendered(element, view);
	}

	/**
	 * The portrait of the current target. Vanilla renders the entity into a texture of whole GUI pixels around the
	 * box; this element then shows that texture rounded, tinted and in the panel's transform, one layer above it.
	 * Everything is reused from frame to frame: only a new target or a moved box allocates.
	 */
	private static final class Portrait implements SimpleGuiElementRenderState {
		/** Vanilla's GUI entities are turned upside down into the y-down GUI space; bodyYaw 180 then faces the viewer. */
		private static final Quaternionf UPRIGHT = new Quaternionf().rotateZ((float) Math.PI);
		private static final float HEAD_TURN = 20f;
		private static final int FULL_BRIGHT = 0xF000F0;

		private final Vector3f translation = new Vector3f();
		private final Vector2f corner = new Vector2f();
		private final Matrix3x2f pose = new Matrix3x2f();
		@Nullable
		private LivingEntity entity;
		@Nullable
		private LivingEntity failed;
		@Nullable
		private EntityRenderer<?, ?> renderer;
		@Nullable
		private EntityRenderState state;
		/** The offscreen element and what it was made for (box in GUI pixels, scale, scissor). */
		@Nullable
		private EntityGuiElementRenderState offscreen;
		private int x0;
		private int y0;
		private int x1;
		private int y1;
		private float scale;
		@Nullable
		private ScreenRect scissor;
		@Nullable
		private ScreenRect bounds;
		/** The image: a rect under the pose and the region of the offscreen texture it shows. */
		private float x;
		private float y;
		private float size;
		private float radius;
		private int color;
		private float u0;
		private float v0;
		private float u1;
		private float v1;
		@Nullable
		private GpuTextureView view;
		@Nullable
		private TextureSetup texture;
		/** A 1x1 blank texture bound while this frame's offscreen render has not arrived (nothing is drawn then). */
		@Nullable
		private TextureSetup blank;
		private boolean ready;

		boolean draw(DrawContext ctx, LivingEntity e, float tickDelta, float x, float y, float size, float radius, float squash, int color) {
			if (e.isInvisible() || e == failed) return false;
			color = ColorUtil.fade(color, Render2D.alpha());
			if ((color >>> 24) == 0) return true;
			LivingEntityRenderState living = update(e, tickDelta);
			if (living == null) return false;

			// The box on screen (the offscreen element ignores the pose), widened to whole GUI pixels.
			Matrix3x2f m = ctx.getMatrices();
			m.transformPosition(x, y, corner);
			float sx0 = corner.x;
			float sy0 = corner.y;
			m.transformPosition(x + size, y + size, corner);
			float sx1 = corner.x;
			float sy1 = corner.y;
			int bx0 = (int) Math.floor(Math.min(sx0, sx1));
			int by0 = (int) Math.floor(Math.min(sy0, sy1));
			int bx1 = (int) Math.ceil(Math.max(sx0, sx1));
			int by1 = (int) Math.ceil(Math.max(sy0, sy1));
			if (bx1 <= bx0 || by1 <= by0) return true;
			// A square of `side` blocks around the eyes: the head with a little of the neck and shoulders.
			float side = Math.max(0.6f, Math.min(living.width * 1.25f, living.height * 0.6f));
			float pixelsPerBlock = Math.abs(sx1 - sx0) / side;
			// Vanilla centres the entity on the pixel box; move the eyes onto the exact centre of the head box.
			translation.set(((sx0 + sx1) - (bx0 + bx1)) * 0.5f / pixelsPerBlock,
				living.standingEyeHeight + ((sy0 + sy1) - (by0 + by1)) * 0.5f / pixelsPerBlock, 0f);
			ScreenRect clip = ctx.scissorStack.peekLast();
			if (offscreen == null || bx0 != x0 || by0 != y0 || bx1 != x1 || by1 != y1 || pixelsPerBlock != scale
				|| !Objects.equals(clip, scissor)) {
				x0 = bx0;
				y0 = by0;
				x1 = bx1;
				y1 = by1;
				scale = pixelsPerBlock;
				scissor = clip;
				offscreen = new EntityGuiElementRenderState(living, translation, UPRIGHT, null, bx0, by0, bx1, by1, pixelsPerBlock, clip);
				ScreenRect rect = new ScreenRect(bx0 - 2, by0 - 2, bx1 - bx0 + 4, by1 - by0 + 4);
				bounds = clip != null ? clip.intersection(rect) : rect;
			}
			if (bounds == null) return true;
			ctx.state.addSpecialElement(offscreen);

			// The image covers the head box (squashed about its centre) and shows the part of the texture under it;
			// render targets are stored bottom row first.
			float w = bx1 - bx0;
			float h = by1 - by0;
			this.u0 = (sx0 - bx0) / w;
			this.u1 = (sx1 - bx0) / w;
			this.v0 = 1f - (sy0 - by0) / h;
			this.v1 = 1f - (sy1 - by0) / h;
			this.size = size * squash;
			this.x = x + (size - this.size) * 0.5f;
			this.y = y + (size - this.size) * 0.5f;
			this.radius = radius * squash;
			this.color = color;
			pose.set(m);
			ready = false;
			if (blank == null) {
				NativeImageBackedTexture pixel = new NativeImageBackedTexture(() -> "V1zir portrait placeholder", new NativeImage(NativeImage.Format.RGBA, 1, 1, true));
				blank = TextureSetup.of(pixel.getGlTextureView(), RenderSystem.getSamplerCache().get(FilterMode.NEAREST));
			}
			ctx.state.addSimpleElement(this);
			return true;
		}

		/** The entity's render state for this frame, posed for a portrait; null if it cannot be drawn. */
		@Nullable
		private LivingEntityRenderState update(LivingEntity e, float tickDelta) {
			try {
				EntityRenderer<?, ?> current = MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(e);
				if (e != entity || current != renderer) {
					entity = e;
					renderer = current;
					state = current.createRenderState();
					offscreen = null;
				}
				fill(current, e, state, tickDelta);
			} catch (RuntimeException ex) {
				V1zir.LOGGER.warn("Cannot draw a portrait of {}", e, ex);
				failed = e;
				entity = null;
				renderer = null;
				state = null;
				offscreen = null;
				return null;
			}
			if (!(state instanceof LivingEntityRenderState living) || living.invisible) return null;
			// Lit like the inventory, no name tag, outline or ground shadow; the body faces the viewer, the head turned a little.
			living.light = FULL_BRIGHT;
			living.outlineColor = 0;
			living.displayName = null;
			living.shadowPieces.clear();
			living.bodyYaw = 180f;
			living.relativeHeadYaw = HEAD_TURN;
			living.pitch = 0f;
			return living;
		}

		@SuppressWarnings({"unchecked", "rawtypes"})
		private static void fill(EntityRenderer renderer, LivingEntity entity, EntityRenderState state, float tickDelta) {
			renderer.updateRenderState(entity, state, tickDelta);
		}

		boolean rendered(SpecialGuiElementRenderState element, @Nullable GpuTextureView texture) {
			if (element != offscreen || offscreen == null || texture == null) return false;
			if (texture != view) {
				view = texture;
				this.texture = TextureSetup.of(texture, RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
			}
			ready = true;
			return true;
		}

		@Override
		public void setupVertices(VertexConsumer vertices) {
			ImageElement.emit(vertices, pose, x, y, size, size, radius, ready ? color : 0, u0, v0, u1, v1, ImageElement.PREMULTIPLIED);
		}

		@Override
		public RenderPipeline pipeline() {
			return V1zirPipelines.IMAGE;
		}

		@Override
		public TextureSetup textureSetup() {
			return ready ? texture : blank;
		}

		@Nullable
		@Override
		public ScreenRect scissorArea() {
			return scissor;
		}

		@Nullable
		@Override
		public ScreenRect bounds() {
			return bounds;
		}
	}
}
