package dev.v1zir.visuals.mixin;

import dev.v1zir.visuals.gui.loading.LoadingScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.SplashOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The loading screen in place of the Mojang one. The Mojang screen still runs as it always does (it follows the
 * load, finishes it and takes itself away), but everything it draws is clipped away, and the loading screen is
 * drawn after it, with the screen behind while it fades.
 */
@Mixin(SplashOverlay.class)
public abstract class SplashOverlayMixin {
	@Unique
	private boolean v1zir$replaced;

	@Inject(method = "render", at = @At("HEAD"))
	private void v1zir$hideMojang(DrawContext context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
		v1zir$replaced = LoadingScreen.enabled();
		if (v1zir$replaced) context.enableScissor(0, 0, 0, 0);
	}

	@Inject(method = "render", at = @At("TAIL"))
	private void v1zir$drawLoadingScreen(DrawContext context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
		if (!v1zir$replaced) return;
		context.disableScissor();
		SplashOverlayAccessor overlay = (SplashOverlayAccessor) this;
		LoadingScreen.render(context, this, mouseX, mouseY, deltaTicks, overlay.v1zir$reloading(), overlay.v1zir$progress(),
			overlay.v1zir$reloadCompleteTime(), overlay.v1zir$reloadStartTime());
	}
}
