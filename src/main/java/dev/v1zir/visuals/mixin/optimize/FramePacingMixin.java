package dev.v1zir.visuals.mixin.optimize;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.v1zir.visuals.optimize.FramePacer;
import dev.v1zir.visuals.optimize.Optimizations;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Frame pacing: the wait for the next frame at a capped frame rate keeps an even beat (FramePacer). */
@Mixin(RenderSystem.class)
public abstract class FramePacingMixin {
	@Inject(method = "limitDisplayFPS", at = @At("HEAD"), cancellable = true, require = 0)
	private static void v1zir$pace(int fps, CallbackInfo ci) {
		if (Optimizations.pacing() && FramePacer.limit(fps)) ci.cancel();
	}
}
