package dev.v1zir.visuals.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.impl.visuals.SwingAnimationsModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Swing Animations speed. The swing duration only drives the arm animation; the swing packet and
 * the attack cooldown do not depend on it, so this is purely visual.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	@ModifyReturnValue(method = "getHandSwingDuration", at = @At("RETURN"))
	private int v1zir$swingSpeed(int ticks) {
		if ((Object) this != MinecraftClient.getInstance().player) return ticks;
		SwingAnimationsModule swing = ModuleManager.get(SwingAnimationsModule.class);
		return swing != null && swing.isEnabled() ? swing.swingDuration(ticks) : ticks;
	}
}
