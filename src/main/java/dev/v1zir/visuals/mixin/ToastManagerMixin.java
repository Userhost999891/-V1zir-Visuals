package dev.v1zir.visuals.mixin;

import dev.v1zir.visuals.gui.UiSounds;
import net.minecraft.client.toast.AdvancementToast;
import net.minecraft.client.toast.Toast;
import net.minecraft.client.toast.ToastManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The PS5 style's trophy: an advancement's toast plays it as it comes. */
@Mixin(ToastManager.class)
public abstract class ToastManagerMixin {
	@Inject(method = "add", at = @At("HEAD"))
	private void v1zir$trophy(Toast toast, CallbackInfo ci) {
		if (toast instanceof AdvancementToast) UiSounds.play(UiSounds.TROPHY);
	}
}
