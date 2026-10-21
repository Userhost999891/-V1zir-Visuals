package dev.v1zir.visuals.render;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import dev.v1zir.visuals.V1zir;
import dev.v1zir.visuals.render.sky.MenuSky;
import dev.v1zir.visuals.render.sky.SkyCache;
import dev.v1zir.visuals.render.sky.SkyUniforms;
import dev.v1zir.visuals.render.world.WorldEffectRenderer;
import net.minecraft.client.gl.DynamicUniformStorage;
import net.minecraft.client.util.Window;

import java.nio.ByteBuffer;

/**
 * Per frame uniform buffers. Storages are rotated in RenderSystem#flipFrame (see RenderSystemMixin)
 * and slices are written lazily the first time they are needed in a frame.
 */
public final class FrameUniforms {
	private static final int GUI_SIZE = new Std140SizeCalculator().putVec4().putVec4().get();

	private static DynamicUniformStorage<GuiData> guiStorage;
	private static GpuBufferSlice guiSlice;

	private FrameUniforms() {
	}

	public static void flip() {
		if (guiStorage != null) guiStorage.clear();
		guiSlice = null;
		SkyUniforms.flip();
		MenuSky.flip();
		SkyCache.flip();
		BackdropBlur.flip();
		WorldEffectRenderer.flip();
	}

	public static GpuBufferSlice gui() {
		if (guiSlice == null) {
			if (guiStorage == null) guiStorage = new DynamicUniformStorage<>("V1zir GUI UBO", GUI_SIZE, 4);
			Window window = V1zir.mc().getWindow();
			guiSlice = guiStorage.write(new GuiData(V1zir.time(), window.getScaleFactor(),
				Math.max(window.getFramebufferWidth(), 1), Math.max(window.getFramebufferHeight(), 1)));
		}
		return guiSlice;
	}

	private record GuiData(float time, float scale, int width, int height) implements DynamicUniformStorage.Uploadable {
		@Override
		public void write(ByteBuffer buffer) {
			Std140Builder.intoBuffer(buffer)
				.putVec4(time, scale, 0.0f, 0.0f)
				.putVec4(width, height, 1.0f / width, 1.0f / height);
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
