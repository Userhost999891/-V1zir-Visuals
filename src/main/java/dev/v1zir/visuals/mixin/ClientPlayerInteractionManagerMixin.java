package dev.v1zir.visuals.mixin;

import dev.v1zir.visuals.render.world.Effects;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tells the effects which entity you hit (target ESP, target HUD, hit particles). */
@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionManagerMixin {
	@Inject(method = "attackEntity", at = @At("HEAD"))
	private void v1zir$attackEntity(PlayerEntity player, Entity target, CallbackInfo ci) {
		Effects.onAttack(target);
	}
}
