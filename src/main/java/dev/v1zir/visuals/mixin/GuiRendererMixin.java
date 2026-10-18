package dev.v1zir.visuals.mixin;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.v1zir.visuals.render.BackdropBlur;
import dev.v1zir.visuals.render.FrameUniforms;
import dev.v1zir.visuals.render.V1zirPipelines;
import dev.v1zir.visuals.render.sky.MenuSky;
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiRenderer.class)
public abstract class GuiRendererMixin {
	/** Runs before any GUI pass is opened: uniform buffers can be mapped and the blur can render. */
	@Inject(method = "render(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V", at = @At("HEAD"))
	private void v1zir$beforeGui(GpuBufferSlice fogBuffer, CallbackInfo ci) {
		FrameUniforms.gui();
		BackdropBlur.runIfRequested();
		MenuSky.renderPending();
	}

	/** Binds the extra uniform blocks our GUI pipelines declare. */
	@Inject(
		method = "render(Lnet/minecraft/client/gui/render/GuiRenderer$Draw;Lcom/mojang/blaze3d/systems/RenderPass;Lcom/mojang/blaze3d/buffers/GpuBuffer;Lcom/mojang/blaze3d/vertex/VertexFormat$IndexType;)V",
		at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderPass;drawIndexed(IIII)V")
	)
	private void v1zir$bindUniforms(GuiRenderer.Draw draw, RenderPass pass, GpuBuffer indexBuffer, VertexFormat.IndexType indexType, CallbackInfo ci) {
		RenderPipeline pipeline = draw.pipeline();
		if (pipeline == V1zirPipelines.GLASS) {
			pass.setUniform("V1zirGui", FrameUniforms.gui());
		} else if (pipeline == V1zirPipelines.MENU_BACKGROUND) {
			pass.setUniform("V1zirGui", FrameUniforms.gui());
		}
	}
}
