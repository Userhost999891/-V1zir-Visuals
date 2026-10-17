package dev.v1zir.visuals.mixin;

import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.impl.visuals.AmbienceModule;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Ambience: keeps the custom time instead of the server's (client side only). */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {
	@Inject(method = "onWorldTimeUpdate", at = @At("HEAD"), cancellable = true)
	private void v1zir$onWorldTimeUpdate(WorldTimeUpdateS2CPacket packet, CallbackInfo ci) {
		AmbienceModule ambience = ModuleManager.get(AmbienceModule.class);
		if (ambience != null && ambience.isEnabled() && ambience.onServerTime(packet.timeOfDay())) ci.cancel();
	}
}
