package dev.v1zir.visuals.mixin.optimize;

import dev.v1zir.visuals.optimize.Optimizations;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.MessageScreen;
import net.minecraft.client.gui.screen.ProgressScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.world.LevelLoadingScreen;
import net.minecraft.client.option.InactivityFpsLimiter;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Background FPS limit: in a world with the window in the background the frame rate is capped at 30
 * (vanilla only lowers it when minimised or AFK). Never while anything loads, so joining a world or
 * reloading resources in the background stays as fast as before.
 */
@Mixin(InactivityFpsLimiter.class)
public abstract class BackgroundFpsMixin {
	@Shadow
	@Final
	private MinecraftClient client;

	@Inject(method = "update", at = @At("RETURN"), cancellable = true)
	private void v1zir$background(CallbackInfoReturnable<Integer> cir) {
		if (!Optimizations.background() || client.isWindowFocused() || client.world == null || client.getOverlay() != null) return;
		if (client.currentScreen instanceof LevelLoadingScreen || client.currentScreen instanceof ProgressScreen
			|| client.currentScreen instanceof MessageScreen || client.currentScreen instanceof ConnectScreen) return;
		cir.setReturnValue(Math.min(cir.getReturnValue(), 30));
	}
}
