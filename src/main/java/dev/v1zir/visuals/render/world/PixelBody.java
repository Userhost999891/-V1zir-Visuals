package dev.v1zir.visuals.render.world;

import dev.v1zir.visuals.V1zir;
import dev.v1zir.visuals.mixin.LivingEntityRendererInvoker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * An entity's body as coloured cells: its model is rendered once, in vanilla's own pose and transforms, into a
 * capture, and every face is cut into one cell per texel of its texture (coarser when that would be too many), each
 * taking the texel's colour; see-through texels (the empty parts of a skin's outer layer) leave no cell. What draws
 * the cells can then move, fade and scatter them freely: a player's ghost that still wears their skin and falls
 * apart pixel by pixel. Positions are relative to the entity's feet. Render thread only.
 */
public final class PixelBody {
	/** Floats per cell: centre, two half edges, unit normal (3 each), break order (0 first to 1 last), height (0 feet to 1 top). */
	public static final int STRIDE = 14;
	private static final int FULL_BRIGHT = 0xF000F0;
	/** Cell edge in blocks where there is no texture to count texels on: a skin pixel. */
	private static final float CELL = 0.0625f;
	private static final int MAX_PER_EDGE = 48;
	private static final Capture CAPTURE = new Capture();
	private static final Pixels MISSING = new Pixels(new int[0], 0, 0);
	/** The textures read last, most recently used last. */
	private static final Map<Identifier, Pixels> TEXTURES = new LinkedHashMap<>(16, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Identifier, Pixels> eldest) {
			return size() > 24;
		}
	};

	public final float[] cells;
	/** Opaque ARGB of every cell; white when the texture could not be read. */
	public final int[] colors;
	public final int count;
	public final boolean textured;
	/** Height of the captured body in blocks (its highest cell above its lowest). */
	public final float height;

	private PixelBody(float[] cells, int[] colors, int count, boolean textured, float height) {
		this.cells = cells;
		this.colors = colors;
		this.count = count;
		this.textured = textured;
		this.height = height;
	}

	private record Pixels(int[] argb, int width, int height) {
	}

	/**
	 * Captures the entity as it stands now (upright even while it dies), in at most about maxCells cells; null when
	 * its renderer has no model to capture.
	 */
	@Nullable
	@SuppressWarnings({"unchecked", "rawtypes"})
	public static PixelBody capture(LivingEntity entity, float tickDelta, int maxCells) {
		MinecraftClient mc = MinecraftClient.getInstance();
		EntityRenderer<?, ?> base = mc.getEntityRenderDispatcher().getRenderer(entity);
		if (!(base instanceof LivingEntityRenderer renderer)) return null;
		Identifier texture;
		try {
			LivingEntityRenderState state = (LivingEntityRenderState) renderer.createRenderState();
			renderer.updateRenderState(entity, state, tickDelta);
			// As it stood: the death animation tips the body over.
			state.deathTime = 0f;
			// What LivingEntityRenderer.render does before it draws the model.
			MatrixStack matrices = new MatrixStack();
			Vec3d offset = renderer.getPositionOffset(state);
			matrices.translate((float) offset.x, (float) offset.y, (float) offset.z);
			float scale = state.baseScale;
			matrices.scale(scale, scale, scale);
			LivingEntityRendererInvoker transforms = (LivingEntityRendererInvoker) renderer;
			transforms.v1zir$setupTransforms(state, matrices, state.bodyYaw, scale);
			matrices.scale(-1f, -1f, 1f);
			transforms.v1zir$scale(state, matrices);
			matrices.translate(0f, -1.501f, 0f);
			EntityModel model = renderer.getModel();
			model.setAngles(state);
			CAPTURE.begin();
			model.render(matrices, CAPTURE, FULL_BRIGHT, OverlayTexture.DEFAULT_UV);
			texture = renderer.getTexture(state);
		} catch (RuntimeException e) {
			// Some modded renderers need more than a fresh state.
			return null;
		}
		return CAPTURE.quads() == 0 ? null : build(texture != null ? pixels(texture) : null, Math.max(64, maxCells));
	}

	/** The texture's pixels: from the image a downloaded skin keeps, else read from the resource packs; null when neither works. */
	@Nullable
	private static Pixels pixels(Identifier id) {
		Pixels known = TEXTURES.get(id);
		if (known == null) {
			known = read(id);
			TEXTURES.put(id, known != null ? known : MISSING);
		}
		return known == MISSING ? null : known;
	}

	@Nullable
	private static Pixels read(Identifier id) {
		MinecraftClient mc = MinecraftClient.getInstance();
		try {
			AbstractTexture texture = mc.getTextureManager().getTexture(id);
			if (texture instanceof NativeImageBackedTexture backed && backed.getImage() != null) {
				NativeImage image = backed.getImage();
				return new Pixels(image.copyPixelsArgb(), image.getWidth(), image.getHeight());
			}
			Optional<Resource> resource = mc.getResourceManager().getResource(id);
			if (resource.isEmpty()) return null;
			try (InputStream in = resource.get().getInputStream(); NativeImage image = NativeImage.read(in)) {
				return new Pixels(image.copyPixelsArgb(), image.getWidth(), image.getHeight());
			}
		} catch (IOException | RuntimeException e) {
			V1zir.LOGGER.debug("Could not read the pixels of {}", id, e);
			return null;
		}
	}

	/** Cuts the captured quads into cells. */
	private static PixelBody build(@Nullable Pixels pixels, int maxCells) {
		Capture c = CAPTURE;
		int quads = c.quads();
		boolean textured = pixels != null && pixels.width > 0 && pixels.height > 0;
		// How many cells the faces ask for, to coarsen them all alike when that is too many.
		long wanted = 0;
		for (int q = 0; q < quads; q++) wanted += (long) across(c, q, 1, pixels) * across(c, q, 3, pixels);
		int step = wanted <= maxCells ? 1 : (int) Math.ceil(Math.sqrt(wanted / (double) maxCells));
		int capacity = (int) Math.min(wanted, (long) maxCells * 2L + 64L);
		float[] cells = new float[Math.max(1, capacity) * STRIDE];
		int[] colors = new int[Math.max(1, capacity)];
		int count = 0;
		float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
		ThreadLocalRandom random = ThreadLocalRandom.current();
		for (int q = 0; q < quads; q++) {
			int v = q * 4;
			float x0 = c.pos[v * 3], y0 = c.pos[v * 3 + 1], z0 = c.pos[v * 3 + 2];
			float ux = c.pos[(v + 1) * 3] - x0, uy = c.pos[(v + 1) * 3 + 1] - y0, uz = c.pos[(v + 1) * 3 + 2] - z0;
			float wx = c.pos[(v + 3) * 3] - x0, wy = c.pos[(v + 3) * 3 + 1] - y0, wz = c.pos[(v + 3) * 3 + 2] - z0;
			float nx = c.normal[v * 3], ny = c.normal[v * 3 + 1], nz = c.normal[v * 3 + 2];
			float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
			if (nl < 1e-4f) {
				// No normal given: the face's own.
				nx = uy * wz - uz * wy;
				ny = uz * wx - ux * wz;
				nz = ux * wy - uy * wx;
				nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
				if (nl < 1e-6f) continue;
			}
			nx /= nl;
			ny /= nl;
			nz /= nl;
			int su = Math.max(1, (across(c, q, 1, pixels) + step - 1) / step);
			int sw = Math.max(1, (across(c, q, 3, pixels) + step - 1) / step);
			float u0 = c.uv[v * 2], v0 = c.uv[v * 2 + 1];
			float uu = c.uv[(v + 1) * 2] - u0, uv = c.uv[(v + 1) * 2 + 1] - v0;
			float wu = c.uv[(v + 3) * 2] - u0, wv = c.uv[(v + 3) * 2 + 1] - v0;
			for (int i = 0; i < su; i++) {
				for (int j = 0; j < sw; j++) {
					float fu = (i + 0.5f) / su, fw = (j + 0.5f) / sw;
					int color = 0xFFFFFFFF;
					if (textured) {
						int px = Math.max(0, Math.min(pixels.width - 1, (int) ((u0 + uu * fu + wu * fw) * pixels.width)));
						int py = Math.max(0, Math.min(pixels.height - 1, (int) ((v0 + uv * fu + wv * fw) * pixels.height)));
						color = pixels.argb[py * pixels.width + px];
						// See-through: nothing there (the unused parts of a skin's outer layer).
						if ((color >>> 24) < 40) continue;
						color |= 0xFF000000;
					}
					if (count == colors.length) {
						cells = Arrays.copyOf(cells, cells.length * 2);
						colors = Arrays.copyOf(colors, colors.length * 2);
					}
					int o = count * STRIDE;
					cells[o] = x0 + ux * fu + wx * fw;
					cells[o + 1] = y0 + uy * fu + wy * fw;
					cells[o + 2] = z0 + uz * fu + wz * fw;
					cells[o + 3] = ux / (2 * su);
					cells[o + 4] = uy / (2 * su);
					cells[o + 5] = uz / (2 * su);
					cells[o + 6] = wx / (2 * sw);
					cells[o + 7] = wy / (2 * sw);
					cells[o + 8] = wz / (2 * sw);
					cells[o + 9] = nx;
					cells[o + 10] = ny;
					cells[o + 11] = nz;
					cells[o + 12] = random.nextFloat();
					minY = Math.min(minY, cells[o + 1]);
					maxY = Math.max(maxY, cells[o + 1]);
					colors[count++] = color;
				}
			}
		}
		if (count == 0) return null;
		float span = Math.max(0.1f, maxY - minY);
		for (int i = 0; i < count; i++) {
			int o = i * STRIDE;
			float h = (cells[o + 1] - minY) / span;
			cells[o + 13] = h;
			// Mostly from the feet up, ragged by the random part.
			cells[o + 12] = 0.62f * h + 0.38f * cells[o + 12];
		}
		return new PixelBody(cells, colors, count, textured, span);
	}

	/**
	 * How many cells a quad gets along the edge from its first corner to corner 1 or 3: the texels that edge crosses,
	 * or its length in skin pixels without a texture.
	 */
	private static int across(Capture c, int quad, int corner, @Nullable Pixels pixels) {
		int a = quad * 4, b = a + corner;
		if (pixels != null && pixels.width > 0) {
			float du = Math.abs(c.uv[b * 2] - c.uv[a * 2]) * pixels.width;
			float dv = Math.abs(c.uv[b * 2 + 1] - c.uv[a * 2 + 1]) * pixels.height;
			int texels = Math.round(Math.max(du, dv));
			if (texels >= 1) return Math.min(MAX_PER_EDGE, texels);
		}
		float dx = c.pos[b * 3] - c.pos[a * 3], dy = c.pos[b * 3 + 1] - c.pos[a * 3 + 1], dz = c.pos[b * 3 + 2] - c.pos[a * 3 + 2];
		return Math.max(1, Math.min(MAX_PER_EDGE, Math.round((float) Math.sqrt(dx * dx + dy * dy + dz * dz) / CELL)));
	}

	/** Receives the model's vertices: position, texture coordinate and normal of each; four make a quad. */
	private static final class Capture implements VertexConsumer {
		float[] pos = new float[3 * 4 * 256];
		float[] uv = new float[2 * 4 * 256];
		float[] normal = new float[3 * 4 * 256];
		private int vertices;

		void begin() {
			vertices = 0;
		}

		int quads() {
			return vertices / 4;
		}

		@Override
		public void vertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float nx, float ny, float nz) {
			vertex(x, y, z);
			texture(u, v);
			normal(nx, ny, nz);
		}

		@Override
		public VertexConsumer vertex(float x, float y, float z) {
			if ((vertices + 1) * 3 > pos.length) {
				pos = Arrays.copyOf(pos, pos.length * 2);
				uv = Arrays.copyOf(uv, uv.length * 2);
				normal = Arrays.copyOf(normal, normal.length * 2);
			}
			int o = vertices * 3;
			pos[o] = x;
			pos[o + 1] = y;
			pos[o + 2] = z;
			normal[o] = normal[o + 1] = normal[o + 2] = 0f;
			uv[vertices * 2] = uv[vertices * 2 + 1] = 0f;
			vertices++;
			return this;
		}

		@Override
		public VertexConsumer texture(float u, float v) {
			if (vertices > 0) {
				uv[(vertices - 1) * 2] = u;
				uv[(vertices - 1) * 2 + 1] = v;
			}
			return this;
		}

		@Override
		public VertexConsumer normal(float x, float y, float z) {
			if (vertices > 0) {
				int o = (vertices - 1) * 3;
				normal[o] = x;
				normal[o + 1] = y;
				normal[o + 2] = z;
			}
			return this;
		}

		@Override
		public VertexConsumer color(int red, int green, int blue, int alpha) {
			return this;
		}

		@Override
		public VertexConsumer color(int argb) {
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
