package dev.v1zir.visuals.mixin;

import dev.v1zir.visuals.module.impl.effects.TotemAnimationModule;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Totem Animation: while the module is on, vanilla's 40 tick timer is held at its start and the
 * floating totem follows the module's clock instead, moved and scaled before vanilla draws it.
 */
@Mixin(InGameOverlayRenderer.class)
public abstract class InGameOverlayRendererMixin {
	@Shadow
	private ItemStack floatingItem;
	@Shadow
	private int floatingItemTimer;

	@Unique
	private boolean v1zir$transformed;

	@Inject(method = "setFloatingItem", at = @At("HEAD"), cancellable = true)
	private void v1zir$setFloatingItem(ItemStack stack, Random random, CallbackInfo ci) {
		TotemAnimationModule totem = TotemAnimationModule.active();
		if (totem == null) return;
		if (totem.hide.on()) ci.cancel();
		else totem.start();
	}

	@Inject(method = "tickFloatingItemTimer", at = @At("HEAD"), cancellable = true)
	private void v1zir$holdTimer(CallbackInfo ci) {
		if (TotemAnimationModule.active() != null) ci.cancel();
	}

	/** Vanilla's progress is (40 - timer + tickProgress) / 40; with the timer held at 40 this sets it directly. */
	@ModifyVariable(method = "renderFloatingItem", at = @At("HEAD"), argsOnly = true)
	private float v1zir$progress(float tickProgress) {
		TotemAnimationModule totem = TotemAnimationModule.active();
		return totem == null ? tickProgress : Math.min(totem.progress(), 1f) * 40f;
	}

	@Inject(method = "renderFloatingItem", at = @At("HEAD"), cancellable = true)
	private void v1zir$beginFloatingItem(MatrixStack matrices, float tickProgress, OrderedRenderCommandQueue queue, CallbackInfo ci) {
		TotemAnimationModule totem = TotemAnimationModule.active();
		if (totem == null || floatingItem == null) return;
		if (totem.progress() >= 1f) {
			floatingItem = null;
			floatingItemTimer = 0;
			ci.cancel();
			return;
		}
		matrices.push();
		totem.transform(matrices);
		v1zir$transformed = true;
	}

	@Inject(method = "renderFloatingItem", at = @At("RETURN"))
	private void v1zir$endFloatingItem(MatrixStack matrices, float tickProgress, OrderedRenderCommandQueue queue, CallbackInfo ci) {
		if (!v1zir$transformed) return;
		matrices.pop();
		v1zir$transformed = false;
	}
}
