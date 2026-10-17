package dev.v1zir.visuals.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.v1zir.visuals.render.sky.SkyRenderer;
import dev.v1zir.visuals.render.world.WorldEffectRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {
	/** Captures the view and projection matrices of the frame (shader sky and 3D effects). */
	@Inject(method = "render", at = @At("HEAD"))
	private void v1zir$captureMatrices(CallbackInfo ci,
									   @Local(argsOnly = true, ordinal = 0) Matrix4f positionMatrix,
									   @Local(argsOnly = true, ordinal = 2) Matrix4f projectionMatrix) {
		SkyRenderer.captureMatrices(positionMatrix, projectionMatrix);
		WorldEffectRenderer.captureMatrices(positionMatrix, projectionMatrix);
	}

	/** The world is fully rendered: draw the 3D effects on top, depth tested. */
	@Inject(method = "render", at = @At("TAIL"))
	private void v1zir$renderEffects(CallbackInfo ci, @Local(argsOnly = true) RenderTickCounter tickCounter) {
		WorldEffectRenderer.render(tickCounter.getTickProgress(false));
	}

	@Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
	private void v1zir$renderClouds(CallbackInfo ci) {
		if (SkyRenderer.active() && SkyRenderer.hideVanillaClouds()) ci.cancel();
	}
}
