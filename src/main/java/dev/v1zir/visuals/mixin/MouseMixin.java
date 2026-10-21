package dev.v1zir.visuals.mixin;

import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.impl.visuals.CameraTweaksModule;
import dev.v1zir.visuals.module.impl.visuals.ZoomModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While zooming, or with the camera scroll key held, the wheel adjusts the camera instead of the hotbar. */
@Mixin(Mouse.class)
public abstract class MouseMixin {
	@Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
	private void v1zir$cameraScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (vertical == 0 || mc.currentScreen != null || mc.player == null) return;
		ZoomModule zoom = ModuleManager.get(ZoomModule.class);
		CameraTweaksModule tweaks = ModuleManager.get(CameraTweaksModule.class);
		if (zoom != null && zoom.isEnabled() && zoom.onScroll(vertical)
			|| tweaks != null && tweaks.isEnabled() && tweaks.onScroll(vertical)) {
			ci.cancel();
		}
	}
}
