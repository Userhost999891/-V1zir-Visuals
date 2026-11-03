package dev.v1zir.visuals.mixin;

import dev.v1zir.visuals.module.impl.visuals.NoRenderModule;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
import net.minecraft.particle.ParticleTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * No Render: explosion clouds. The emitter and the puffs it spawns both come through here; callers
 * already handle a null particle (vanilla returns null for types without a factory).
 */
@Mixin(ParticleManager.class)
public abstract class ParticleManagerMixin {
	@Inject(method = "addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)Lnet/minecraft/client/particle/Particle;", at = @At("HEAD"), cancellable = true)
	private void v1zir$explosions(ParticleEffect parameters, double x, double y, double z, double velocityX, double velocityY, double velocityZ,
								  CallbackInfoReturnable<Particle> cir) {
		ParticleType<?> type = parameters.getType();
		if (type != ParticleTypes.EXPLOSION && type != ParticleTypes.EXPLOSION_EMITTER) return;
		NoRenderModule noRender = NoRenderModule.active();
		if (noRender != null && noRender.explosions.on()) cir.setReturnValue(null);
	}
}
