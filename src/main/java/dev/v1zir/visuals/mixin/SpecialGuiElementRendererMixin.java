package dev.v1zir.visuals.mixin;

import com.mojang.blaze3d.textures.GpuTextureView;
import dev.v1zir.visuals.render.ui.Heads;
import net.minecraft.client.gui.render.SpecialGuiElementRenderer;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.gui.render.state.special.SpecialGuiElementRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Head portraits (Heads) show the offscreen entity render themselves, rounded and faded with their panel, instead of vanilla's square blit. */
@Mixin(SpecialGuiElementRenderer.class)
public abstract class SpecialGuiElementRendererMixin {
	@Shadow
	private GpuTextureView textureView;

	@Inject(
		method = "renderElement(Lnet/minecraft/client/gui/render/state/special/SpecialGuiElementRenderState;Lnet/minecraft/client/gui/render/state/GuiRenderState;)V",
		at = @At("HEAD"), cancellable = true
	)
	private void v1zir$portrait(SpecialGuiElementRenderState element, GuiRenderState state, CallbackInfo ci) {
		if (Heads.portraitRendered(element, textureView)) ci.cancel();
	}
}
