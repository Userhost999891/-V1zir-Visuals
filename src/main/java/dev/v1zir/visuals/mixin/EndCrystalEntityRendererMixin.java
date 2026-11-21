package dev.v1zir.visuals.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.v1zir.visuals.module.impl.effects.CrystalChamsModule;
import net.minecraft.client.model.Model;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.command.ModelCommandRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EndCrystalEntityRenderer;
import net.minecraft.client.render.entity.state.EndCrystalEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Crystal Chams: size, colour and light of end crystals. */
@Mixin(EndCrystalEntityRenderer.class)
public abstract class EndCrystalEntityRendererMixin {
	@Shadow
	@Final
	private static Identifier TEXTURE;

	/** Before vanilla's own scale, so the crystal grows around its base. */
	@Inject(method = "render(Lnet/minecraft/client/render/entity/state/EndCrystalEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;scale(FFF)V", ordinal = 0))
	private void v1zir$scale(EndCrystalEntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState camera, CallbackInfo ci) {
		CrystalChamsModule chams = CrystalChamsModule.active();
		if (chams == null) return;
		float s = chams.scale.f();
		matrices.scale(s, s, s);
	}

	@WrapOperation(method = "render(Lnet/minecraft/client/render/entity/state/EndCrystalEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/RenderLayer;IIILnet/minecraft/client/render/command/ModelCommandRenderer$CrumblingOverlayCommand;)V"))
	private <S> void v1zir$submitModel(OrderedRenderCommandQueue queue, Model<? super S> model, S state, MatrixStack matrices, RenderLayer layer,
									   int light, int overlay, int outlineColor, ModelCommandRenderer.CrumblingOverlayCommand crumbling, Operation<Void> original) {
		CrystalChamsModule chams = CrystalChamsModule.active();
		if (chams == null) {
			original.call(queue, model, state, matrices, layer, light, overlay, outlineColor, crumbling);
			return;
		}
		// The overload with a tint colour; no sprite, the layer carries the texture.
		queue.submitModel(model, state, matrices, chams.layer(TEXTURE), chams.light(light), overlay, chams.tint(), null, outlineColor, crumbling);
	}
}
