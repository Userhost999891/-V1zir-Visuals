package dev.v1zir.visuals.mixin.optimize;

import dev.v1zir.visuals.optimize.Optimizations;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Particle limit: at most a few thousand new particles per tick. Normal play stays far below it; a flood
 * (particle commands, huge redstone or explosion farms) no longer piles up more particles than a frame can
 * draw. Vanilla already culls particles outside the view and caps the total per texture sheet.
 */
@Mixin(ParticleManager.class)
public abstract class ParticleLimitMixin {
	@Unique
	private static final int V1ZIR$PER_TICK = 4096;
	@Unique
	private int v1zir$added;

	@Inject(method = "tick", at = @At("HEAD"))
	private void v1zir$newTick(CallbackInfo ci) {
		v1zir$added = 0;
	}

	@Inject(method = "addParticle(Lnet/minecraft/client/particle/Particle;)V", at = @At("HEAD"), cancellable = true)
	private void v1zir$limit(Particle particle, CallbackInfo ci) {
		if (++v1zir$added > V1ZIR$PER_TICK && Optimizations.particles()) ci.cancel();
	}
}
