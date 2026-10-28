package dev.v1zir.visuals.render.sky;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import net.minecraft.client.gl.DynamicUniformStorage;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.nio.ByteBuffer;

/** Writes the SkyParams uniform block (layout in assets/v1zir/shaders/include/sky_params.glsl). */
public final class SkyUniforms {
	private static final int SIZE = new Std140SizeCalculator()
		.putMat4f()
		.putVec4().putVec4().putVec4().putVec4()
		.putVec4().putVec4().putVec4().putVec4()
		.get();

	private static DynamicUniformStorage<Data> storage;

	private SkyUniforms() {
	}

	public static void flip() {
		if (storage != null) storage.clear();
	}

	public static GpuBufferSlice write(Data data) {
		if (storage == null) storage = new DynamicUniformStorage<>("V1zir sky UBO", SIZE, 4);
		return storage.write(data);
	}

	/** All vectors follow the comment block at the top of sky_lib.glsl. */
	public static final class Data implements DynamicUniformStorage.Uploadable {
		public final Matrix4f invViewProj = new Matrix4f();
		public final Vector4f sunDir = new Vector4f(0, 1, 0, 0);
		public final Vector4f moonDir = new Vector4f(0, -1, 0, 0);
		public final Vector4f timing = new Vector4f();
		public final Vector4f colorA = new Vector4f(1, 1, 1, 0);
		public final Vector4f colorB = new Vector4f(1, 1, 1, 0);
		public final Vector4f settings = new Vector4f(1, 0.45f, 1, 1);
		public final Vector4f camera = new Vector4f();
		public final Vector4f extra = new Vector4f(1, 1, 0, 0);

		/** Copies every field of another instance. */
		public Data set(Data other) {
			invViewProj.set(other.invViewProj);
			sunDir.set(other.sunDir);
			moonDir.set(other.moonDir);
			timing.set(other.timing);
			colorA.set(other.colorA);
			colorB.set(other.colorB);
			settings.set(other.settings);
			camera.set(other.camera);
			extra.set(other.extra);
			return this;
		}

		@Override
		public void write(ByteBuffer buffer) {
			Std140Builder.intoBuffer(buffer)
				.putMat4f(invViewProj)
				.putVec4(sunDir)
				.putVec4(moonDir)
				.putVec4(timing)
				.putVec4(colorA)
				.putVec4(colorB)
				.putVec4(settings)
				.putVec4(camera)
				.putVec4(extra);
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
