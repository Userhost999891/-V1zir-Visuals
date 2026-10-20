package dev.v1zir.visuals.mixin;

import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.impl.visuals.AmbienceModule;
import dev.v1zir.visuals.render.world.Effects;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Ambience keeps its custom time; the combat effects hear about totem pops and deaths. Client side only. */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {
	@Inject(method = "onWorldTimeUpdate", at = @At("HEAD"), cancellable = true)
	private void v1zir$onWorldTimeUpdate(WorldTimeUpdateS2CPacket packet, CallbackInfo ci) {
		AmbienceModule ambience = ModuleManager.get(AmbienceModule.class);
		if (ambience != null && ambience.isEnabled() && ambience.onServerTime(packet.timeOfDay())) ci.cancel();
	}

	@Inject(method = "onEntityStatus", at = @At("HEAD"))
	private void v1zir$onEntityStatus(EntityStatusS2CPacket packet, CallbackInfo ci) {
		byte status = packet.getStatus();
		boolean totem = status == EntityStatuses.USE_TOTEM_OF_UNDYING;
		if (!totem && status != EntityStatuses.PLAY_DEATH_SOUND_OR_ADD_PROJECTILE_HIT_PARTICLES) return;
		// The handler first runs on the network thread, which hands the packet over to the client thread.
		MinecraftClient mc = MinecraftClient.getInstance();
		if (!mc.isOnThread() || mc.world == null) return;
		Entity entity = packet.getEntity(mc.world);
		if (totem) Effects.onTotemPop(entity);
		else Effects.onDeath(entity);
	}
}
