package dev.v1zir.visuals.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/** A single triangle covering the whole viewport, shared by every full screen pass. */
public final class Fullscreen {
	/** GpuBuffer.USAGE_VERTEX */
	private static final int USAGE_VERTEX = 32;

	private static GpuBuffer buffer;

	private Fullscreen() {
	}

	public static GpuBuffer buffer() {
		if (buffer == null) {
			ByteBuffer data = MemoryUtil.memAlloc(3 * 3 * Float.BYTES);
			try {
				data.putFloat(-1f).putFloat(-1f).putFloat(0f);
				data.putFloat(3f).putFloat(-1f).putFloat(0f);
				data.putFloat(-1f).putFloat(3f).putFloat(0f);
				data.flip();
				buffer = RenderSystem.getDevice().createBuffer(() -> "V1zir fullscreen triangle", USAGE_VERTEX, data);
			} finally {
				MemoryUtil.memFree(data);
			}
		}
		return buffer;
	}
}
