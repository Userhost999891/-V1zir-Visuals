package dev.v1zir.visuals.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.impl.visuals.FullBrightModule;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.util.profiler.Profiler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Full Bright: the lightmap is cleared to white instead of being computed. */
@Mixin(LightmapTextureManager.class)
public abstract class LightmapTextureManagerMixin {
	@Shadow
	@Final
	private GpuTexture glTexture;

	@Inject(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/profiler/Profiler;push(Ljava/lang/String;)V", shift = At.Shift.AFTER), cancellable = true)
	private void v1zir$fullBright(float tickProgress, CallbackInfo ci, @Local Profiler profiler) {
		if (!ModuleManager.isEnabled(FullBrightModule.class)) return;
		RenderSystem.getDevice().createCommandEncoder().clearColorTexture(glTexture, 0xFFFFFFFF);
		profiler.pop();
		ci.cancel();
	}
}
