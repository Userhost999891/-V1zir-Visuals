package dev.v1zir.visuals.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.v1zir.visuals.render.FrameUniforms;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderSystem.class)
public abstract class RenderSystemMixin {
	@Inject(method = "flipFrame", at = @At("TAIL"))
	private static void v1zir$flipFrame(CallbackInfo ci) {
		FrameUniforms.flip();
	}
}
