package dev.v1zir.visuals.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.v1zir.visuals.module.impl.effects.ChamsModule;
import dev.v1zir.visuals.render.world.MarkedRenderState;
import net.minecraft.client.model.Model;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.command.ModelCommandRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Kill Effects: a body that shattered is not drawn at all (no model, armour or held items). Chams: the
 * body model is captured as it is submitted, and the styles that replace the body skip vanilla's draw.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
	private static final String RENDER = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V";
	private static final String SUBMIT_MODEL = "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/RenderLayer;IIILnet/minecraft/client/texture/Sprite;ILnet/minecraft/client/render/command/ModelCommandRenderer$CrumblingOverlayCommand;)V";

	@Inject(method = RENDER, at = @At("HEAD"), cancellable = true)
	private void v1zir$hideShattered(LivingEntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState camera,
									 CallbackInfo ci) {
		if (((MarkedRenderState) state).v1zir$hidden()) ci.cancel();
	}

	@WrapOperation(method = RENDER, at = @At(value = "INVOKE", target = SUBMIT_MODEL))
	private <S> void v1zir$chams(OrderedRenderCommandQueue queue, Model<? super S> model, S state, MatrixStack matrices, RenderLayer layer, int light,
								 int overlay, int tint, Sprite sprite, int outlineColor, ModelCommandRenderer.CrumblingOverlayCommand crumbling,
								 Operation<Void> original) {
		int chams = state instanceof MarkedRenderState marked ? marked.v1zir$chams() : 0;
		if (chams == 0 || ChamsModule.capture(model, state, matrices, light, overlay, chams)) {
			original.call(queue, model, state, matrices, layer, light, overlay, tint, sprite, outlineColor, crumbling);
		}
	}
}
