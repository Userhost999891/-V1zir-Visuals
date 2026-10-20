package dev.v1zir.visuals.mixin;

import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.impl.hud.KeystrokesModule;
import net.minecraft.client.Mouse;
import net.minecraft.client.input.MouseInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Counts mouse clicks for the Keystrokes CPS. Every press is seen here, even two within one frame,
 * which polling the key bindings would merge into one.
 */
@Mixin(Mouse.class)
public abstract class MouseClickMixin {
	@Inject(method = "onMouseButton", at = @At("HEAD"))
	private void v1zir$onMouseButton(long window, MouseInput input, int action, CallbackInfo ci) {
		KeystrokesModule keystrokes = ModuleManager.get(KeystrokesModule.class);
		if (keystrokes != null && keystrokes.isEnabled()) keystrokes.onMouseButton(input.button(), action);
	}
}
