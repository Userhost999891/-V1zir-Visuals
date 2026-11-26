package dev.v1zir.visuals.mixin.optimize;

import dev.v1zir.visuals.optimize.DistanceCulling;
import net.minecraft.client.render.block.entity.BannerBlockEntityRenderer;
import net.minecraft.component.type.BannerPatternsComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Distance culling: a far banner draws its cloth in the base colour without the pattern layers, one model
 * draw instead of one per layer. Shields and banner items render through here outside block entity
 * rendering, where no distance is set, and keep their patterns.
 */
@Mixin(BannerBlockEntityRenderer.class)
public abstract class BannerPatternsMixin {
	@ModifyVariable(method = "renderCanvas", at = @At("HEAD"), argsOnly = true)
	private static BannerPatternsComponent v1zir$farPatterns(BannerPatternsComponent patterns) {
		return DistanceCulling.bannerPatternsHidden() ? BannerPatternsComponent.DEFAULT : patterns;
	}
}
