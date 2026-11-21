package dev.v1zir.visuals.render;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.util.Window;

import java.util.List;

/**
 * Renders GUI elements into an offscreen colour texture instead of the screen, with a GuiRenderer of its own
 * (GuiRendererMixin points that renderer at this framebuffer). The click GUI's open and close animations draw
 * its window here and then composite the texture. Nothing is allocated until the first capture, nothing
 * runs while no animation asks for it, and the framebuffer is freed a few seconds after the last capture.
 */
public final class GuiCapture {
	private static GuiRenderState state;
	private static GuiRenderer renderer;
	private static SimpleFramebuffer target;
	private static DrawContext context;
	private static boolean rendering;
	private static final long IDLE_NANOS = 5_000_000_000L;
	private static long lastUse;

	private GuiCapture() {
	}

	/** A draw context for this frame's capture; what was drawn into it before is dropped. */
	public static DrawContext begin(int mouseX, int mouseY) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (renderer == null) {
			state = new GuiRenderState();
			renderer = new GuiRenderer(state, mc.getBufferBuilders().getEntityVertexConsumers(), mc.gameRenderer.getEntityRenderCommandQueue(),
				mc.gameRenderer.getEntityRenderDispatcher(), List.of());
			context = new DrawContext(mc, state, mouseX, mouseY);
		}
		state.clear();
		lastUse = System.nanoTime();
		return context;
	}

	/** Once per frame: frees the full-window framebuffer (several MB) when no animation used it for a while. */
	public static void flip() {
		if (target != null && System.nanoTime() - lastUse > IDLE_NANOS) {
			target.delete();
			target = null;
		}
	}

	/** Draws the captured elements into the texture; called once the frame's GUI is done (GuiRendererMixin). */
	public static void render(GpuBufferSlice fog) {
		Window window = MinecraftClient.getInstance().getWindow();
		int width = Math.max(1, window.getFramebufferWidth());
		int height = Math.max(1, window.getFramebufferHeight());
		lastUse = System.nanoTime();
		if (target == null) target = new SimpleFramebuffer("V1zir GUI capture", width, height, true);
		else if (target.textureWidth != width || target.textureHeight != height) target.resize(width, height);
		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		encoder.clearColorTexture(target.getColorAttachment(), 0);
		encoder.clearDepthTexture(target.getDepthAttachment(), 1.0);
		GpuTextureView color = RenderSystem.outputColorTextureOverride;
		GpuTextureView depth = RenderSystem.outputDepthTextureOverride;
		// Both routes lead here: the overrides (if GuiRenderer honours them) and the framebuffer swap in GuiRendererMixin.
		RenderSystem.outputColorTextureOverride = target.getColorAttachmentView();
		RenderSystem.outputDepthTextureOverride = target.getDepthAttachmentView();
		rendering = true;
		try {
			renderer.render(fog);
			renderer.incrementFrame();
		} finally {
			rendering = false;
			RenderSystem.outputColorTextureOverride = color;
			RenderSystem.outputDepthTextureOverride = depth;
		}
	}

	/** Whether this GuiRenderer is the capture's own and is drawing right now. */
	public static boolean isCapturing(Object guiRenderer) {
		return rendering && guiRenderer == renderer;
	}

	public static Framebuffer target() {
		return target;
	}

	/** The captured image: premultiplied colour, transparent where nothing was drawn. */
	public static GpuTextureView view() {
		lastUse = System.nanoTime();
		return target.getColorAttachmentView();
	}
}
