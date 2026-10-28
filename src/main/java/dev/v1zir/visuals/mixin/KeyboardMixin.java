package dev.v1zir.visuals.mixin;

import dev.v1zir.visuals.V1zirClient;
import net.minecraft.client.Keyboard;
import net.minecraft.client.input.KeyInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public abstract class KeyboardMixin {
	@Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
	private void v1zir$onKey(long window, int action, KeyInput input, CallbackInfo ci) {
		if (V1zirClient.onKey(input.key(), action)) ci.cancel();
	}
}
