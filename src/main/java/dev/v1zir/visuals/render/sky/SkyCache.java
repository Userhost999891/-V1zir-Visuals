package dev.v1zir.visuals.render.sky;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import dev.v1zir.visuals.render.Fullscreen;
import dev.v1zir.visuals.render.V1zirPipelines;
import net.minecraft.client.gl.DynamicUniformStorage;
import net.minecraft.client.gl.GpuSampler;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.OptionalInt;

/**
 * Caches the world sky in two hemispherical octahedral panoramas, one above and one below the
 * horizon. The sky shader no longer runs for every screen pixel every frame: the panoramas are
 * rebuilt a few rows per frame, a whole build every refresh period, and the screen pass only
 * samples them, crossfading between the last two complete builds so the slow sky animation stays
 * smooth. The cost no longer depends on the screen resolution or the preset's complexity per
 * frame, and the sky cannot shimmer while the camera turns because it lives in world directions.
 */
public final class SkyCache {
	/** Panorama sizes: about 0.07 degrees per texel above the horizon, where the detail is. */
	private static final int UPPER = 1280;
	private static final int LOWER = 512;
	private static final long TEXELS = (long) UPPER * UPPER + (long) LOWER * LOWER;
	private static final int UBO_SIZE = new Std140SizeCalculator().putVec4().get();
	/**
	 * At most this much of a build per frame: at low frame rates the refresh slows down instead of
	 * each frame doing more work, so a slow frame never makes the next one slower.
	 */
	private static final float MAX_STEP = 1f / 24f;
	/** A sun or moon jump larger than this (a /time set) rebuilds the cache at once. */
	private static final float JUMP_COS = (float) Math.cos(Math.toRadians(2.0));

	private static final Panorama[] upper = new Panorama[3];
	private static final Panorama[] lower = new Panorama[3];
	private static final SkyUniforms.Data building = new SkyUniforms.Data();
	private static final float[] signature = new float[17];
	private static final float[] built = new float[17];
	private static int previous = 0;
	private static int current = 1;
	private static int next = 2;
	private static boolean valid;
	private static boolean previousValid;
	private static float progress;
	private static long lastNanos;
	private static DynamicUniformStorage<CacheData> storage;

	private SkyCache() {
	}

	public static void flip() {
		if (storage != null) storage.clear();
	}

	/**
	 * Advances the panorama build and draws the sky into the target.
	 *
	 * @param data   this frame's sky parameters (the build snapshots them at its start)
	 * @param params the same parameters already uploaded, for the screen pass
	 * @param period seconds one complete panorama build takes
	 */
	public static void render(SkyUniforms.Data data, GpuBufferSlice params, GpuTextureView target, float period) {
		ensureTextures();
		if (storage == null) storage = new DynamicUniformStorage<>("V1zir sky cache UBO", UBO_SIZE, 4);
		long now = System.nanoTime();
		float dt = lastNanos == 0 ? 0f : Math.min((now - lastNanos) / 1.0e9f, 0.25f);
		lastNanos = now;

		sign(data, signature);
		if (!valid || !Arrays.equals(signature, built) || jumped(data)) {
			// Settings changed, time skipped or nothing is cached yet: build the current panorama
			// in full right now and start the next one from here.
			GpuBufferSlice snapshot = SkyUniforms.write(data);
			buildRows(current, snapshot, 0f, 1f);
			System.arraycopy(signature, 0, built, 0, built.length);
			building.set(data);
			valid = true;
			previousValid = false;
			progress = 0f;
		} else {
			float from = progress;
			progress = Math.min(1f, progress + Math.min(dt / Math.max(period, 0.05f), MAX_STEP));
			if (progress > from) buildRows(next, SkyUniforms.write(building), from, progress);
			if (progress >= 1f) {
				int oldPrevious = previous;
				previous = current;
				current = next;
				next = oldPrevious;
				previousValid = true;
				building.set(data);
				progress = 0f;
			}
		}
		composite(target, params, previousValid ? progress : 1f);
	}

	/** Renders the rows of the build that fall between two progress values. */
	private static void buildRows(int slot, GpuBufferSlice snapshot, float from, float to) {
		long t0 = (long) (from * TEXELS);
		long t1 = to >= 1f ? TEXELS : (long) (to * TEXELS);
		long upperTexels = (long) UPPER * UPPER;
		int u0 = (int) Math.min(UPPER, t0 / UPPER);
		int u1 = (int) Math.min(UPPER, t1 / UPPER);
		if (u1 > u0) upper[slot].render(snapshot, 1f, u0, u1);
		int l0 = (int) Math.max(0, Math.min(LOWER, (t0 - upperTexels) / LOWER));
		int l1 = (int) Math.max(0, Math.min(LOWER, (t1 - upperTexels) / LOWER));
		if (l1 > l0) lower[slot].render(snapshot, -1f, l0, l1);
	}

	private static void composite(GpuTextureView target, GpuBufferSlice params, float blend) {
		GpuBufferSlice cache = storage.write(new CacheData(blend, 0f));
		GpuSampler sampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
		Panorama prevUpper = previousValid ? upper[previous] : upper[current];
		Panorama prevLower = previousValid ? lower[previous] : lower[current];
		try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "V1zir cached sky", target, OptionalInt.empty())) {
			pass.setPipeline(V1zirPipelines.SKY_CACHED);
			pass.setUniform("SkyParams", params);
			pass.setUniform("SkyCache", cache);
			pass.bindTexture("Sampler0", upper[current].view, sampler);
			pass.bindTexture("Sampler1", prevUpper.view, sampler);
			pass.bindTexture("Sampler2", lower[current].view, sampler);
			pass.bindTexture("Sampler3", prevLower.view, sampler);
			pass.setVertexBuffer(0, Fullscreen.buffer());
			pass.draw(0, 3);
		}
	}

	/** Everything that changes the look of the sky apart from the slow animation and the camera. */
	private static void sign(SkyUniforms.Data d, float[] out) {
		out[0] = d.camera.w;
		out[1] = d.settings.x;
		out[2] = d.settings.y;
		out[3] = d.settings.z;
		out[4] = d.settings.w;
		out[5] = d.colorA.x;
		out[6] = d.colorA.y;
		out[7] = d.colorA.z;
		out[8] = d.colorA.w;
		out[9] = d.colorB.x;
		out[10] = d.colorB.y;
		out[11] = d.colorB.z;
		out[12] = d.colorB.w;
		out[13] = d.extra.x;
		out[14] = d.extra.y;
		out[15] = d.extra.z;
		out[16] = d.timing.w;
	}

	private static boolean jumped(SkyUniforms.Data d) {
		float sun = d.sunDir.x * building.sunDir.x + d.sunDir.y * building.sunDir.y + d.sunDir.z * building.sunDir.z;
		return sun < JUMP_COS || d.moonDir.w != building.moonDir.w;
	}

	private static void ensureTextures() {
		if (upper[0] != null) return;
		for (int i = 0; i < 3; i++) {
			upper[i] = new Panorama("V1zir sky cache upper " + i, UPPER);
			lower[i] = new Panorama("V1zir sky cache lower " + i, LOWER);
		}
	}

	private static final class Panorama {
		private final GpuTexture texture;
		private final GpuTextureView view;
		private final int size;

		Panorama(String label, int size) {
			this.size = size;
			texture = RenderSystem.getDevice().createTexture(label, 15, TextureFormat.RGBA8, size, size, 1, 1);
			view = RenderSystem.getDevice().createTextureView(texture);
		}

		/** Runs the sky shader over rows [from, to) of this panorama. */
		void render(GpuBufferSlice snapshot, float hemisphere, int from, int to) {
			GpuBufferSlice cache = storage.write(new CacheData(1f, hemisphere));
			try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "V1zir sky cache build", view, OptionalInt.empty())) {
				pass.setPipeline(V1zirPipelines.SKY_PANORAMA);
				pass.setUniform("SkyParams", snapshot);
				pass.setUniform("SkyCache", cache);
				pass.enableScissor(0, from, size, to - from);
				pass.setVertexBuffer(0, Fullscreen.buffer());
				pass.draw(0, 3);
			}
		}
	}

	private record CacheData(float blend, float hemisphere) implements DynamicUniformStorage.Uploadable {
		@Override
		public void write(ByteBuffer buffer) {
			Std140Builder.intoBuffer(buffer).putVec4(blend, hemisphere, 0f, 0f);
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
