package dev.v1zir.visuals.mixin;

import dev.v1zir.visuals.render.sky.SkyRenderer;
import net.minecraft.client.render.SkyRendering;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Swaps the vanilla sky dome for the shader sky; the rest of the vanilla sky is hidden while it is on. */
@Mixin(SkyRendering.class)
public abstract class SkyRenderingMixin {
	@Inject(method = "renderTopSky", at = @At("HEAD"), cancellable = true)
	private void v1zir$renderTopSky(CallbackInfo ci) {
		if (SkyRenderer.renderWorldSky()) ci.cancel();
	}

	@Inject(method = "renderGlowingSky", at = @At("HEAD"), cancellable = true)
	private void v1zir$renderGlowingSky(CallbackInfo ci) {
		if (SkyRenderer.active()) ci.cancel();
	}

	@Inject(method = "renderCelestialBodies", at = @At("HEAD"), cancellable = true)
	private void v1zir$renderCelestialBodies(CallbackInfo ci) {
		if (SkyRenderer.active() && SkyRenderer.hideVanillaCelestials()) ci.cancel();
	}

	@Inject(method = "renderSkyDark", at = @At("HEAD"), cancellable = true)
	private void v1zir$renderSkyDark(CallbackInfo ci) {
		if (SkyRenderer.active()) ci.cancel();
	}

	@Inject(method = "renderEndSky", at = @At("HEAD"), cancellable = true)
	private void v1zir$renderEndSky(CallbackInfo ci) {
		if (SkyRenderer.renderEndSky()) ci.cancel();
	}
}
