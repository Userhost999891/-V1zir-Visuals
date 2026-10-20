package dev.v1zir.visuals.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.impl.visuals.HandChamsModule;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Hand Chams on the first-person arm: it is submitted through the overload that carries a tint colour. */
@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererMixin {
	/** Glow: the same skin on the emissive variant of the arm's layer, so it is drawn unshaded. */
	@ModifyExpressionValue(method = "renderArm", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/RenderLayers;entityTranslucent(Lnet/minecraft/util/Identifier;)Lnet/minecraft/client/render/RenderLayer;"))
	private RenderLayer v1zir$glowArm(RenderLayer layer, @Local(argsOnly = true) Identifier skin) {
		HandChamsModule chams = ModuleManager.get(HandChamsModule.class);
		return chams != null && chams.glowingHand() ? RenderLayers.entityTranslucentEmissive(skin) : layer;
	}

	@WrapOperation(method = "renderArm", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;submitModelPart(Lnet/minecraft/client/model/ModelPart;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/RenderLayer;IILnet/minecraft/client/texture/Sprite;)V"))
	private void v1zir$tintArm(OrderedRenderCommandQueue queue, ModelPart part, MatrixStack matrices, RenderLayer layer, int light, int overlay, Sprite sprite,
							   Operation<Void> original) {
		HandChamsModule chams = ModuleManager.get(HandChamsModule.class);
		if (chams == null || !chams.tintsHand()) {
			original.call(queue, part, matrices, layer, light, overlay, sprite);
			return;
		}
		queue.submitModelPart(part, matrices, layer, chams.handLight(light), overlay, sprite, chams.argb(), null);
	}
}
