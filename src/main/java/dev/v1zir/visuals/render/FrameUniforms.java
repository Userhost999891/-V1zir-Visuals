package dev.v1zir.visuals.render;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import dev.v1zir.visuals.V1zir;
import dev.v1zir.visuals.gui.clickgui.WindowTransition;
import dev.v1zir.visuals.optimize.Optimizations;
import dev.v1zir.visuals.render.wallpaper.WallpaperRenderer;
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
	/** Cursor in framebuffer pixels (bottom-left origin) for lit GUI shapes; negative while no screen sets it. */
	private static float cursorX = -1f;
	private static float cursorY = -1f;

	private FrameUniforms() {
	}

	public static void flip() {
		if (guiStorage != null) guiStorage.clear();
		guiSlice = null;
		cursorX = -1f;
		cursorY = -1f;
		SkyUniforms.flip();
		WallpaperRenderer.flip();
		SkyCache.flip();
		BackdropBlur.flip();
		ColorGrade.flip();
		Reflections.flip();
		WindowTransition.flip();
		WorldEffectRenderer.flip();
		GuiCapture.flip();
		Optimizations.endFrame();
	}

	/** Lights LIT shapes around this point (GUI coordinates) for the current frame. */
	public static void cursor(double guiX, double guiY) {
		Window window = V1zir.mc().getWindow();
		float scale = (float) window.getScaleFactor();
		cursorX = (float) guiX * scale;
		cursorY = window.getFramebufferHeight() - (float) guiY * scale;
	}

	public static GpuBufferSlice gui() {
		if (guiSlice == null) {
			if (guiStorage == null) guiStorage = new DynamicUniformStorage<>("V1zir GUI UBO", GUI_SIZE, 4);
			Window window = V1zir.mc().getWindow();
			guiSlice = guiStorage.write(new GuiData(V1zir.time(), window.getScaleFactor(), cursorX, cursorY,
				Math.max(window.getFramebufferWidth(), 1), Math.max(window.getFramebufferHeight(), 1)));
		}
		return guiSlice;
	}

	private record GuiData(float time, float scale, float cursorX, float cursorY, int width, int height) implements DynamicUniformStorage.Uploadable {
		@Override
		public void write(ByteBuffer buffer) {
			Std140Builder.intoBuffer(buffer)
				.putVec4(time, scale, cursorX, cursorY)
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
