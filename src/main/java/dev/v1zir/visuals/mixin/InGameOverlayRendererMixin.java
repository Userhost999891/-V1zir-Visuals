package dev.v1zir.visuals.mixin;

import dev.v1zir.visuals.module.impl.effects.TotemAnimationModule;
import dev.v1zir.visuals.module.impl.visuals.NoRenderModule;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.texture.Sprite;
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
 * No Render: the fire overlay is lowered or hidden, the underwater and in-wall overlays hidden.
 */
@Mixin(InGameOverlayRenderer.class)
public abstract class InGameOverlayRendererMixin {
	@Shadow
	private ItemStack floatingItem;
	@Shadow
	private int floatingItemTimer;

	@Unique
	private boolean v1zir$transformed;
	@Unique
	private static boolean v1zir$fireLowered;

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

	@Inject(method = "renderFireOverlay", at = @At("HEAD"), cancellable = true)
	private static void v1zir$beginFire(MatrixStack matrices, VertexConsumerProvider vertexConsumers, Sprite sprite, CallbackInfo ci) {
		NoRenderModule noRender = NoRenderModule.active();
		if (noRender == null || noRender.fire.is("Vanilla")) return;
		if (noRender.fire.is("Hide")) {
			ci.cancel();
			return;
		}
		// Vanilla pushes its own transforms for both flames on top of this one.
		matrices.push();
		matrices.translate(0f, -noRender.fireDrop(), 0f);
		v1zir$fireLowered = true;
	}

	@Inject(method = "renderFireOverlay", at = @At("RETURN"))
	private static void v1zir$endFire(MatrixStack matrices, VertexConsumerProvider vertexConsumers, Sprite sprite, CallbackInfo ci) {
		if (!v1zir$fireLowered) return;
		matrices.pop();
		v1zir$fireLowered = false;
	}

	@Inject(method = "renderUnderwaterOverlay", at = @At("HEAD"), cancellable = true)
	private static void v1zir$underwater(CallbackInfo ci) {
		NoRenderModule noRender = NoRenderModule.active();
		if (noRender != null && noRender.underwater.on()) ci.cancel();
	}

	@Inject(method = "renderInWallOverlay", at = @At("HEAD"), cancellable = true)
	private static void v1zir$inWall(CallbackInfo ci) {
		NoRenderModule noRender = NoRenderModule.active();
		if (noRender != null && noRender.inWall.on()) ci.cancel();
	}
}
