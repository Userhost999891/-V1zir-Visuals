package dev.v1zir.visuals.mixin;

import dev.v1zir.visuals.module.impl.visuals.NoRenderModule;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No Render: screen overlays, boss bars and the scoreboard of the vanilla HUD. */
@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
	@Shadow
	@Final
	private static Identifier POWDER_SNOW_OUTLINE;

	@Inject(method = "renderVignetteOverlay", at = @At("HEAD"), cancellable = true)
	private void v1zir$vignette(CallbackInfo ci) {
		NoRenderModule noRender = NoRenderModule.active();
		if (noRender != null && noRender.vignette.on()) ci.cancel();
	}

	@Inject(method = "renderSpyglassOverlay", at = @At("HEAD"), cancellable = true)
	private void v1zir$spyglass(CallbackInfo ci) {
		NoRenderModule noRender = NoRenderModule.active();
		if (noRender != null && noRender.spyglass.on()) ci.cancel();
	}

	@Inject(method = "renderPortalOverlay", at = @At("HEAD"), cancellable = true)
	private void v1zir$portal(CallbackInfo ci) {
		NoRenderModule noRender = NoRenderModule.active();
		if (noRender != null && noRender.portal.on()) ci.cancel();
	}

	/** The green nausea tint; the wobble itself is in GameRendererMixin. */
	@Inject(method = "renderNauseaOverlay", at = @At("HEAD"), cancellable = true)
	private void v1zir$nausea(CallbackInfo ci) {
		NoRenderModule noRender = NoRenderModule.active();
		if (noRender != null && noRender.nausea.on()) ci.cancel();
	}

	/**
	 * Head items with a camera overlay (the carved pumpkin, or any item with an equippable camera
	 * overlay) and the powder snow frost share this method; only the frost passes its own texture.
	 */
	@Inject(method = "renderOverlay", at = @At("HEAD"), cancellable = true)
	private void v1zir$cameraOverlay(DrawContext context, Identifier texture, float opacity, CallbackInfo ci) {
		NoRenderModule noRender = NoRenderModule.active();
		if (noRender != null && noRender.pumpkin.on() && !POWDER_SNOW_OUTLINE.equals(texture)) ci.cancel();
	}

	@Inject(method = "renderBossBarHud", at = @At("HEAD"), cancellable = true)
	private void v1zir$bossBars(CallbackInfo ci) {
		NoRenderModule noRender = NoRenderModule.active();
		if (noRender != null && noRender.bossBars.on()) ci.cancel();
	}

	@Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V", at = @At("HEAD"), cancellable = true)
	private void v1zir$scoreboard(CallbackInfo ci) {
		NoRenderModule noRender = NoRenderModule.active();
		if (noRender != null && noRender.scoreboard.on()) ci.cancel();
	}
}
