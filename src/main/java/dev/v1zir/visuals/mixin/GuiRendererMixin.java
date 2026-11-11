package dev.v1zir.visuals.mixin;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.v1zir.visuals.render.BackdropBlur;
import dev.v1zir.visuals.gui.clickgui.WindowTransition;
import dev.v1zir.visuals.render.FrameUniforms;
import dev.v1zir.visuals.render.GuiCapture;
import dev.v1zir.visuals.render.V1zirPipelines;
import dev.v1zir.visuals.render.wallpaper.WallpaperRenderer;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiRenderer.class)
public abstract class GuiRendererMixin {
	/** Runs before any GUI pass is opened: uniform buffers can be mapped and the blur can render. */
	@Inject(method = "render(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V", at = @At("HEAD"))
	private void v1zir$beforeGui(GpuBufferSlice fogBuffer, CallbackInfo ci) {
		if (GuiCapture.isCapturing(this)) return;
		FrameUniforms.gui();
		// The title screen wallpapers first: outside a world they are what glass panels blur.
		WallpaperRenderer.renderPending();
		if (WindowTransition.wantsBlur()) BackdropBlur.request();
		BackdropBlur.runIfRequested();
	}

	/** The click GUI's open and close animations composite over the finished GUI. */
	@Inject(method = "render(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V", at = @At("RETURN"))
	private void v1zir$afterGui(GpuBufferSlice fogBuffer, CallbackInfo ci) {
		if (!GuiCapture.isCapturing(this)) WindowTransition.afterGui(fogBuffer);
	}

	/** GuiCapture's own renderer draws into the capture framebuffer instead of the main one. */
	@ModifyVariable(
		method = "render(Ljava/util/function/Supplier;Lnet/minecraft/client/gl/Framebuffer;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lcom/mojang/blaze3d/buffers/GpuBuffer;Lcom/mojang/blaze3d/vertex/VertexFormat$IndexType;II)V",
		at = @At("HEAD"), argsOnly = true
	)
	private Framebuffer v1zir$captureTarget(Framebuffer framebuffer) {
		return GuiCapture.isCapturing(this) ? GuiCapture.target() : framebuffer;
	}

	/** Binds the extra uniform blocks our GUI pipelines declare. */
	@Inject(
		method = "render(Lnet/minecraft/client/gui/render/GuiRenderer$Draw;Lcom/mojang/blaze3d/systems/RenderPass;Lcom/mojang/blaze3d/buffers/GpuBuffer;Lcom/mojang/blaze3d/vertex/VertexFormat$IndexType;)V",
		at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderPass;drawIndexed(IIII)V")
	)
	private void v1zir$bindUniforms(GuiRenderer.Draw draw, RenderPass pass, GpuBuffer indexBuffer, VertexFormat.IndexType indexType, CallbackInfo ci) {
		RenderPipeline pipeline = draw.pipeline();
		if (pipeline == V1zirPipelines.SHAPE || pipeline == V1zirPipelines.GLASS || pipeline == V1zirPipelines.MENU_BACKGROUND) {
			pass.setUniform("V1zirGui", FrameUniforms.gui());
		}
	}
}
