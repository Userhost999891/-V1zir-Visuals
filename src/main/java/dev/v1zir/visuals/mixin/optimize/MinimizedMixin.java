package dev.v1zir.visuals.mixin.optimize;

import dev.v1zir.visuals.optimize.Optimizations;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Background FPS limit: nothing of a minimised window can be seen, so the world is not drawn at all (vanilla
 * still draws it ten times a second). The game keeps ticking, and the pause-on-focus-loss logic around
 * renderWorld still runs.
 */
@Mixin(GameRenderer.class)
public abstract class MinimizedMixin {
	@Inject(method = "renderWorld", at = @At("HEAD"), cancellable = true)
	private void v1zir$minimized(RenderTickCounter tickCounter, CallbackInfo ci) {
		if (Optimizations.background() && MinecraftClient.getInstance().getWindow().isMinimized()) ci.cancel();
	}
}
