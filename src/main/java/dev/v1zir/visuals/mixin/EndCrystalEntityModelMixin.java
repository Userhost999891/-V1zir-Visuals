package dev.v1zir.visuals.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.v1zir.visuals.module.impl.effects.CrystalChamsModule;
import net.minecraft.client.render.entity.model.EndCrystalEntityModel;
import net.minecraft.client.render.entity.state.EndCrystalEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Crystal Chams: spin speed and bounce height of end crystals. */
@Mixin(EndCrystalEntityModel.class)
public abstract class EndCrystalEntityModelMixin {
	@ModifyExpressionValue(method = "setAngles(Lnet/minecraft/client/render/entity/state/EndCrystalEntityRenderState;)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/EndCrystalEntityRenderer;getYOffset(F)F"))
	private float v1zir$bounce(float offset, EndCrystalEntityRenderState state) {
		CrystalChamsModule chams = CrystalChamsModule.active();
		return chams == null ? offset : chams.yOffset(state.age);
	}

	/** The first read of the age is the one that turns the glass cubes. */
	@ModifyExpressionValue(method = "setAngles(Lnet/minecraft/client/render/entity/state/EndCrystalEntityRenderState;)V",
		at = @At(value = "FIELD", target = "Lnet/minecraft/client/render/entity/state/EndCrystalEntityRenderState;age:F", ordinal = 0))
	private float v1zir$spin(float age) {
		CrystalChamsModule chams = CrystalChamsModule.active();
		return chams == null ? age : age * chams.spin.f();
	}
}
