package dev.v1zir.visuals.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.impl.visuals.SwingAnimationsModule;
import dev.v1zir.visuals.module.impl.visuals.ViewModelModule;
import dev.v1zir.visuals.module.impl.visuals.ZoomModule;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** First-person hands: View Model offsets, Swing Animations styles and Zoom's hand slide. */
@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {
	@Unique
	private boolean v1zir$handsLowered;

	/** Zoom: both hands slide down out of view as the zoom grows. */
	@Inject(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/network/ClientPlayerEntity;I)V", at = @At("HEAD"))
	private void v1zir$lowerHands(float tickProgress, MatrixStack matrices, OrderedRenderCommandQueue queue, ClientPlayerEntity player, int light, CallbackInfo ci) {
		ZoomModule zoom = ModuleManager.get(ZoomModule.class);
		float drop = zoom != null && zoom.isEnabled() ? zoom.handDrop() : 0f;
		v1zir$handsLowered = drop > 0f;
		if (!v1zir$handsLowered) return;
		matrices.push();
		matrices.translate(0f, -drop, 0f);
	}

	@Inject(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/network/ClientPlayerEntity;I)V", at = @At("RETURN"))
	private void v1zir$restoreHands(float tickProgress, MatrixStack matrices, OrderedRenderCommandQueue queue, ClientPlayerEntity player, int light, CallbackInfo ci) {
		if (v1zir$handsLowered) matrices.pop();
		v1zir$handsLowered = false;
	}

	@Inject(method = "renderFirstPersonItem", at = @At("HEAD"))
	private void v1zir$swingBegin(AbstractClientPlayerEntity player, float tickProgress, float pitch, Hand hand, float swingProgress, ItemStack item,
								  float equipProgress, MatrixStack matrices, OrderedRenderCommandQueue queue, int light, CallbackInfo ci) {
		SwingAnimationsModule swing = ModuleManager.get(SwingAnimationsModule.class);
		if (swing != null && swing.isEnabled()) swing.begin(player, hand, swingProgress, item);
	}

	/** Swing Animations: vanilla's swing translation is replaced by the style (applied before renderItem). */
	@Inject(method = "swingArm", at = @At("HEAD"), cancellable = true)
	private void v1zir$swingTranslation(float swingProgress, MatrixStack matrices, int armX, Arm arm, CallbackInfo ci) {
		if (v1zir$swingTakesOver()) ci.cancel();
	}

	/** Swing Animations: vanilla's swing rotation is replaced by the style as well. */
	@Inject(method = "applySwingOffset", at = @At("HEAD"), cancellable = true)
	private void v1zir$swingRotation(MatrixStack matrices, Arm arm, float swingProgress, CallbackInfo ci) {
		if (v1zir$swingTakesOver()) ci.cancel();
	}

	/** Swing style, then View Model, right before vanilla renders the held item. */
	@Inject(method = "renderFirstPersonItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/item/HeldItemRenderer;renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V", shift = At.Shift.BEFORE))
	private void v1zir$viewModel(AbstractClientPlayerEntity player, float tickProgress, float pitch, Hand hand, float swingProgress, ItemStack item,
								 float equipProgress, MatrixStack matrices, OrderedRenderCommandQueue queue, int light, CallbackInfo ci) {
		SwingAnimationsModule swing = ModuleManager.get(SwingAnimationsModule.class);
		if (swing != null && swing.isEnabled()) swing.apply(matrices, player);
		ViewModelModule module = ModuleManager.get(ViewModelModule.class);
		if (module == null || !module.isEnabled()) return;
		Arm arm = hand == Hand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
		module.apply(matrices, arm);
	}

	/** Swing Animations: the held item no longer dips after every hit while the attack recharges. */
	@ModifyExpressionValue(method = "updateHeldItems", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getHandEquippingProgress(F)F"))
	private float v1zir$noCooldownDip(float progress) {
		SwingAnimationsModule swing = ModuleManager.get(SwingAnimationsModule.class);
		return swing != null && swing.isEnabled() && swing.noCooldownDip() ? 1f : progress;
	}

	@Unique
	private static boolean v1zir$swingTakesOver() {
		SwingAnimationsModule swing = ModuleManager.get(SwingAnimationsModule.class);
		return swing != null && swing.isEnabled() && swing.takeOver();
	}
}
