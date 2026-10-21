package dev.v1zir.visuals.mixin;

import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.impl.client.MainMenuModule;
import dev.v1zir.visuals.render.sky.MenuSky;
import dev.v1zir.visuals.render.ui.Render2D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Menus opened outside a world (options, world list, server list...) show our shader background
 * instead of the vanilla rotating panorama.
 */
@Mixin(Screen.class)
public abstract class ScreenMixin {
	@Shadow
	public int width;
	@Shadow
	public int height;

	@Inject(method = "renderPanoramaBackground", at = @At("HEAD"), cancellable = true)
	private void v1zir$shaderPanorama(DrawContext context, float deltaTicks, CallbackInfo ci) {
		if (!MainMenuModule.active()) return;
		MainMenuModule module = ModuleManager.get(MainMenuModule.class);
		MenuSky.prepare(module.preset(), width, height, 0f, 0f, module.speed.f());
		// Dimmer than on the main menu so vanilla widgets stay readable on top of it.
		Render2D.menuBackground(context, width, height, 0xFF6E6E6E);
		ci.cancel();
	}
}
