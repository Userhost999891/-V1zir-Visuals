package dev.v1zir.visuals.mixin;

import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.impl.client.MainMenuModule;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.render.wallpaper.WallpaperRenderer;
import dev.v1zir.visuals.render.wallpaper.WallpaperStyle;
import dev.v1zir.visuals.render.wallpaper.Wallpapers;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Menus opened outside a world (options, world list, server list...) show the home screen's wallpaper instead of the
 * vanilla rotating panorama.
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
		WallpaperRenderer.advance(module.speed.f());
		WallpaperStyle style = Wallpapers.style(Wallpapers.Screen.HOME);
		if (WallpaperRenderer.queueScreen(WallpaperRenderer.HOME, Wallpapers.selected(Wallpapers.Screen.HOME), style, 0f, 0f)) {
			WallpaperRenderer.backdrop(WallpaperRenderer.HOME);
			// Dimmer than on the main menu so vanilla widgets stay readable on top of it.
			Render2D.wallpaper(context, WallpaperRenderer.HOME.textureSetup(), 0f, 0f, width, height, 0f, 0, style.tint(0x6E));
		} else {
			Render2D.rect(context, 0f, 0f, width, height, 0xFF050505);
		}
		ci.cancel();
	}
}
