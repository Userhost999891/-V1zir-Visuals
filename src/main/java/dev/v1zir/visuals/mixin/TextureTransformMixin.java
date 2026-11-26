package dev.v1zir.visuals.mixin;

import dev.v1zir.visuals.module.impl.visuals.GlintColorModule;
import net.minecraft.client.render.TextureTransform;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Glint Color speed: every glint layer (items, entities, armour) takes its scrolling matrix from here. */
@Mixin(TextureTransform.class)
public abstract class TextureTransformMixin {
	@Inject(method = "getGlintTransformation(F)Lorg/joml/Matrix4f;", at = @At("HEAD"), cancellable = true)
	private static void v1zir$glintSpeed(float scale, CallbackInfoReturnable<Matrix4f> cir) {
		Matrix4f matrix = GlintColorModule.glintTransform(scale);
		if (matrix != null) cir.setReturnValue(matrix);
	}
}
