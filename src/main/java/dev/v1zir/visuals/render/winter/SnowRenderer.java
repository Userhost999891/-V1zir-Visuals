package dev.v1zir.visuals.render.winter;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.v1zir.visuals.V1zir;
import dev.v1zir.visuals.render.V1zirPipelines;
import dev.v1zir.visuals.render.world.WorldEffectRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.DynamicUniformStorage;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.Heightmap;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.Random;

/**
 * Snowfall round the camera, worked out on the graphics card: a static buffer holds every flake as four corners
 * and a handful of random numbers, uploaded once; each frame only a few numbers change (the time, the camera, the
 * wind and the light), and the vertex shader moves every flake, wraps it round inside a box that travels with the
 * camera, and turns it to face the eye. One draw call however many flakes there are, nothing per flake on the CPU.
 * Snow stops where the game's own does: a small map of the highest block in every column round the camera (read
 * every half second) keeps it from falling under roofs and trees. Render thread only.
 */
public final class SnowRenderer {
	/** Most flakes drawn: the buffer holds this many, the intensity draws a share of them. */
	public static final int MAX_FLAKES = 32000;
	private static final int STRIDE = 24;
	private static final int USAGE_VERTEX = 32;
	private static final int MAP = 128;
	private static final float BOX_X = 48f;
	private static final float BOX_Y = 36f;
	private static final float BOX_Z = 48f;
	/** The near box, dense snow close to the camera: a third of the large one each way, so both wrap alike. */
	private static final float NEAR_X = BOX_X / 3f;
	private static final float NEAR_Y = BOX_Y / 3f;
	private static final float NEAR_Z = BOX_Z / 3f;
	private static final int UBO_SIZE = new Std140SizeCalculator().putMat4f().putMat4f().putVec4().putVec4().putVec4().putVec4().putVec4().putVec4().putVec4()
		.get();

	@Nullable
	private static GpuBuffer flakes;
	@Nullable
	private static NativeImageBackedTexture heights;
	private static DynamicUniformStorage<Params> storage;
	private static final Params PARAMS = new Params();
	private static long mapAt;
	private static int mapX = Integer.MIN_VALUE;
	private static int mapZ;
	private static int mapBase;
	private static boolean broken;
	/** How far the wind has carried the snow, added up frame by frame and wrapped round the box. */
	private static double driftX;
	private static double driftZ;
	private static long lastFrame;

	private SnowRenderer() {
	}

	public static void flip() {
		if (storage != null) storage.clear();
	}

	/**
	 * Draws the snow over the world just rendered: count flakes (up to MAX_FLAKES) of size (blocks), falling at fall
	 * blocks a second and carried by the wind (windX, windZ in blocks a second, free to change every frame), lit by
	 * the day and the weather; outdoors only when roofs is set.
	 */
	public static void render(float tickDelta, int count, float size, float fall, float windX, float windZ, boolean roofs) {
		if (broken || count <= 0) return;
		MinecraftClient mc = V1zir.mc();
		ClientWorld world = mc.world;
		if (world == null) return;
		try {
			GpuBuffer buffer = flakes();
			if (roofs) updateMap(world);
			double cx = WorldEffectRenderer.camX;
			double cy = WorldEffectRenderer.camY;
			double cz = WorldEffectRenderer.camZ;
			Params p = PARAMS;
			p.camera[0] = (float) (cx - Math.floor(cx / BOX_X) * BOX_X);
			p.camera[1] = (float) (cy - Math.floor(cy / BOX_Y) * BOX_Y);
			p.camera[2] = (float) (cz - Math.floor(cz / BOX_Z) * BOX_Z);
			p.camera[3] = V1zir.time() % 7200f;
			// The wind changes with every gust, so its drift is added up here rather than worked out from the time.
			long now = System.nanoTime();
			double dt = lastFrame == 0L ? 0.0 : Math.min(0.1, Math.max(0.0, (now - lastFrame) / 1.0e9));
			lastFrame = now;
			driftX = wrap(driftX + windX * dt, BOX_X);
			driftZ = wrap(driftZ + windZ * dt, BOX_Z);
			p.wind[0] = (float) driftX;
			p.wind[1] = fall;
			p.wind[2] = (float) driftZ;
			p.wind[3] = size;
			// Light: the same as the snow on the ground has (so flakes never show darker than it), a little brighter as
			// they catch the light from every side; faintly blue by day, bluer at night.
			float day = daylight(world, tickDelta);
			float bright = Math.min(1f, skyLight(world, tickDelta) * 1.08f);
			p.light[0] = bright * (0.9f + 0.05f * day);
			p.light[1] = bright * (0.93f + 0.04f * day);
			p.light[2] = bright * 1.0f;
			p.light[3] = 0.95f;
			p.height[0] = (float) (cy - mapBase);
			p.height[1] = roofs && heights != null ? 1f : 0f;
			p.height[2] = (float) (cx - Math.floor(cx));
			p.height[3] = (float) (cz - Math.floor(cz));
			if (roofs && heights != null) {
				// The map is laid out round the column the camera was in when it was read.
				p.height[2] += (float) (Math.floor(cx) - mapX);
				p.height[3] += (float) (Math.floor(cz) - mapZ);
			}
			p.screen = mc.getFramebuffer().textureHeight;
			celestial(world, tickDelta, p.sun);
			if (storage == null) storage = new DynamicUniformStorage<>("V1zir snow UBO", UBO_SIZE, 2);
			GpuBufferSlice params = storage.write(p);
			int flakesDrawn = Math.min(count, MAX_FLAKES);
			var indices = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS);
			int indexCount = flakesDrawn * 6;
			GpuBuffer indexBuffer = indices.getIndexBuffer(indexCount);
			try (RenderPass pass = WorldEffectRenderer.openPass(() -> "V1zir snow")) {
				pass.setPipeline(V1zirPipelines.SNOW);
				pass.setUniform("SnowParams", params);
				pass.bindTexture("Sampler0", heightsView(), RenderSystem.getSamplerCache().get(FilterMode.NEAREST));
				pass.setVertexBuffer(0, buffer);
				pass.setIndexBuffer(indexBuffer, indices.getIndexType());
				pass.drawIndexed(0, 0, indexCount, 1);
			}
		} catch (RuntimeException e) {
			broken = true;
			V1zir.LOGGER.warn("Snow turned off: {}", e.toString());
		}
	}

	/**
	 * The sky's light on snow out in the open (0 to 1): full by day, by night as bright as the game's own light is set
	 * (Brightness, from Moody to Bright), greyer in a storm. The snow on the ground and the flakes in the air share it.
	 */
	public static float skyLight(ClientWorld world, float tickDelta) {
		double gamma = V1zir.mc().options.getGamma().getValue();
		float night = 0.16f + 0.22f * (float) Math.max(0.0, Math.min(1.0, gamma));
		float day = daylight(world, tickDelta);
		return (night + (1f - night) * day) * (1f - 0.25f * world.getRainGradient(tickDelta));
	}

	/** How much of the day's light there is: 1 at noon, 0 through the night, between them at dawn and dusk. */
	public static float daylight(ClientWorld world, float tickDelta) {
		double t = (Math.floorMod(world.getTimeOfDay(), 24000L) + tickDelta) / 24000.0 - 0.25;
		return (float) Math.max(0.0, Math.min(1.0, 0.5 + Math.cos(t * Math.PI * 2.0) * 1.3));
	}

	/**
	 * Towards the sun, or the moon at night, in out[0..2] (world axes, the sun turning about the z axis and rising in
	 * the east as the game's does) and its strength in out[3]: lower near the horizon, a third for the moon, less in
	 * rain.
	 */
	public static void celestial(ClientWorld world, float tickDelta, float[] out) {
		double t = (Math.floorMod(world.getTimeOfDay(), 24000L) + tickDelta) / 24000.0 - 0.25;
		t -= Math.floor(t);
		double angle = (t * 2.0 + (0.5 - Math.cos(t * Math.PI) / 2.0)) / 3.0 * Math.PI * 2.0;
		float sx = (float) -Math.sin(angle);
		float sy = (float) Math.cos(angle);
		float clear = 1f - 0.85f * world.getRainGradient(tickDelta);
		if (sy > -0.05f) {
			out[0] = sx;
			out[1] = sy;
			out[3] = Math.min(1f, (sy + 0.05f) / 0.25f) * clear;
		} else {
			out[0] = -sx;
			out[1] = -sy;
			out[3] = 0.35f * Math.min(1f, (-sy - 0.05f) / 0.25f) * clear;
		}
		out[2] = 0f;
	}

	private static double wrap(double v, double box) {
		return v - Math.floor(v / box) * box;
	}

	/**
	 * The flakes' static buffer: four corners each with the flake's random numbers, made the first time. Two flakes in
	 * five belong to the near box (their first number is 1 more), spread evenly so any share drawn keeps the mix.
	 */
	private static GpuBuffer flakes() {
		if (flakes != null) return flakes;
		ByteBuffer data = MemoryUtil.memAlloc(MAX_FLAKES * 4 * STRIDE);
		try {
			Random random = new Random(0x5A0F1AE5L);
			float[] cu = {-1f, -1f, 1f, 1f};
			float[] cv = {1f, -1f, -1f, 1f};
			for (int i = 0; i < MAX_FLAKES; i++) {
				float a = random.nextFloat() + (i % 5 < 2 ? 1f : 0f);
				float b = random.nextFloat();
				float c = random.nextFloat();
				byte size = (byte) random.nextInt(256);
				byte phase = (byte) random.nextInt(256);
				byte drift = (byte) random.nextInt(256);
				byte spin = (byte) random.nextInt(256);
				for (int k = 0; k < 4; k++) {
					data.putFloat(a).putFloat(b).putFloat(c);
					data.putFloat(cu[k]).putFloat(cv[k]);
					data.put(size).put(phase).put(drift).put(spin);
				}
			}
			data.flip();
			flakes = RenderSystem.getDevice().createBuffer(() -> "V1zir snowflakes", USAGE_VERTEX, data);
		} finally {
			MemoryUtil.memFree(data);
		}
		return flakes;
	}

	private static com.mojang.blaze3d.textures.GpuTextureView heightsView() {
		if (heights == null) {
			NativeImage image = new NativeImage(NativeImage.Format.RGBA, MAP, MAP, false);
			for (int y = 0; y < MAP; y++) for (int x = 0; x < MAP; x++) image.setColorArgb(x, y, 0xFF000000);
			heights = new NativeImageBackedTexture(() -> "V1zir snow heights", image);
			heights.upload();
		}
		return heights.getGlTextureView();
	}

	/** Reads the highest block snow stops on in every column round the camera, twice a second or after a move. */
	private static void updateMap(ClientWorld world) {
		int x = (int) Math.floor(WorldEffectRenderer.camX);
		int z = (int) Math.floor(WorldEffectRenderer.camZ);
		long now = System.nanoTime();
		boolean moved = Math.abs(x - mapX) > 6 || Math.abs(z - mapZ) > 6;
		if (!moved && now - mapAt < 500_000_000L) return;
		mapAt = now;
		mapX = x;
		mapZ = z;
		mapBase = (int) Math.floor(WorldEffectRenderer.camY) - 128;
		heightsView();
		NativeImage image = heights.getImage();
		if (image == null) return;
		for (int dz = 0; dz < MAP; dz++) {
			for (int dx = 0; dx < MAP; dx++) {
				int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING, x - 64 + dx, z - 64 + dz);
				int h = Math.max(0, Math.min(255, top - mapBase));
				image.setColorArgb(dx, dz, 0xFF000000 | h << 16);
			}
		}
		heights.upload();
	}

	private static final org.joml.Matrix4f PROJ = new org.joml.Matrix4f();
	private static final org.joml.Matrix4f VIEW = new org.joml.Matrix4f();

	/** SnowParams (snow_params.glsl). */
	private static final class Params implements DynamicUniformStorage.Uploadable {
		final float[] camera = new float[4];
		final float[] wind = new float[4];
		final float[] light = new float[4];
		final float[] height = new float[4];
		final float[] sun = new float[4];
		float screen;

		@Override
		public void write(ByteBuffer buffer) {
			Std140Builder.intoBuffer(buffer)
				.putMat4f(PROJ.set(WorldEffectRenderer.projection()))
				.putMat4f(VIEW.set(WorldEffectRenderer.view()))
				.putVec4(camera[0], camera[1], camera[2], camera[3])
				.putVec4(wind[0], wind[1], wind[2], wind[3])
				.putVec4(BOX_X, BOX_Y, BOX_Z, 4f)
				.putVec4(light[0], light[1], light[2], light[3])
				.putVec4(height[0], height[1], height[2], height[3])
				.putVec4(NEAR_X, NEAR_Y, NEAR_Z, screen)
				.putVec4(sun[0], sun[1], sun[2], sun[3]);
		}

		@Override
		public boolean equals(Object o) {
			return false;
		}

		@Override
		public int hashCode() {
			return System.identityHashCode(this);
		}
	}
}
