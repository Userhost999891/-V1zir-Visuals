package dev.v1zir.visuals.mixin;

import dev.v1zir.visuals.gui.menu.ScreenTransition;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.impl.client.MainMenuModule;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.render.wallpaper.WallpaperRenderer;
import dev.v1zir.visuals.render.wallpaper.WallpaperStyle;
import dev.v1zir.visuals.render.wallpaper.Wallpapers;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Menus opened outside a world (options, world list, server list...) show the home screen's wallpaper instead of the
 * vanilla rotating panorama, and a screen opened from the main menu comes in out of a dark veil from a slight zoom
 * ({@link ScreenTransition}).
 */
@Mixin(Screen.class)
public abstract class ScreenMixin {
	@Shadow
	public int width;
	@Shadow
	public int height;

	/** Whether this frame of the screen is drawn zoomed (its entrance), and the veil left over it. */
	@Unique
	private boolean v1zir$entering;
	@Unique
	private float v1zir$veil;

	@Inject(method = "renderWithTooltip", at = @At("HEAD"))
	private void v1zir$enterBegin(DrawContext context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
		float p = ScreenTransition.progress((Screen) (Object) this);
		v1zir$entering = p < 1f;
		if (!v1zir$entering) return;
		float e = Anim.easeOutCubic(p);
		float scale = 1.04f - 0.04f * e;
		context.getMatrices().pushMatrix();
		context.getMatrices().translate(width / 2f, height / 2f);
		context.getMatrices().scale(scale, scale);
		context.getMatrices().translate(-width / 2f, -height / 2f);
		v1zir$veil = 1f - e;
	}

	@Inject(method = "renderWithTooltip", at = @At("TAIL"))
	private void v1zir$enterEnd(DrawContext context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
		if (!v1zir$entering) return;
		v1zir$entering = false;
		context.getMatrices().popMatrix();
		Render2D.rect(context, 0f, 0f, width, height, ColorUtil.withAlpha(0x000000, Math.round(200f * v1zir$veil)));
	}

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
