package dev.v1zir.visuals.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.v1zir.visuals.module.impl.visuals.HandChamsModule;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hand Chams on held items. The deferred item draw still knows its display context, so the tint
 * is switched on for first-person items only and applied to the colour of every quad.
 */
@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {
	private static final String RENDER_ITEM = "renderItem(Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;II[ILjava/util/List;Lnet/minecraft/client/render/RenderLayer;Lnet/minecraft/client/render/item/ItemRenderState$Glint;)V";
	private static final String QUAD = "Lnet/minecraft/client/render/VertexConsumer;quad(Lnet/minecraft/client/util/math/MatrixStack$Entry;Lnet/minecraft/client/render/model/BakedQuad;FFFFII)V";

	@Inject(method = RENDER_ITEM, at = @At("HEAD"))
	private static void v1zir$beginChams(CallbackInfo ci, @Local(argsOnly = true) ItemDisplayContext context) {
		HandChamsModule.beginItem(context.isFirstPerson());
	}

	@Inject(method = RENDER_ITEM, at = @At("RETURN"))
	private static void v1zir$endChams(CallbackInfo ci) {
		HandChamsModule.endItem();
	}

	@ModifyArg(method = "renderBakedItemQuads", at = @At(value = "INVOKE", target = QUAD), index = 2)
	private static float v1zir$red(float red) {
		return HandChamsModule.red(red);
	}

	@ModifyArg(method = "renderBakedItemQuads", at = @At(value = "INVOKE", target = QUAD), index = 3)
	private static float v1zir$green(float green) {
		return HandChamsModule.green(green);
	}

	@ModifyArg(method = "renderBakedItemQuads", at = @At(value = "INVOKE", target = QUAD), index = 4)
	private static float v1zir$blue(float blue) {
		return HandChamsModule.blue(blue);
	}

	@ModifyArg(method = "renderBakedItemQuads", at = @At(value = "INVOKE", target = QUAD), index = 5)
	private static float v1zir$alpha(float alpha) {
		return HandChamsModule.alpha(alpha);
	}

	@ModifyArg(method = "renderBakedItemQuads", at = @At(value = "INVOKE", target = QUAD), index = 6)
	private static int v1zir$light(int light) {
		return HandChamsModule.itemLight(light);
	}
}
