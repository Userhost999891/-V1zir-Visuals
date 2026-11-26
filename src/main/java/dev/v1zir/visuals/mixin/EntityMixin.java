package dev.v1zir.visuals.mixin;

import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.impl.visuals.FreeLookModule;
import dev.v1zir.visuals.module.impl.visuals.WinterModule;
import dev.v1zir.visuals.module.impl.visuals.ZoomModule;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mouse turning of the local player: slower while zoomed, and redirected to the camera by Free Look. Its steps crunch in
 * snow out under the sky while Winter's snow lies.
 */
@Mixin(Entity.class)
public abstract class EntityMixin {
	@ModifyVariable(method = "changeLookDirection", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private double v1zir$zoomTurnX(double cursorDeltaX) {
		return cursorDeltaX * v1zir$zoomMouseScale();
	}

	@ModifyVariable(method = "changeLookDirection", at = @At("HEAD"), argsOnly = true, ordinal = 1)
	private double v1zir$zoomTurnY(double cursorDeltaY) {
		return cursorDeltaY * v1zir$zoomMouseScale();
	}

	@Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
	private void v1zir$freeLook(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
		if ((Object) this != MinecraftClient.getInstance().player) return;
		FreeLookModule freeLook = ModuleManager.get(FreeLookModule.class);
		if (freeLook != null && freeLook.isEnabled() && freeLook.turn(cursorDeltaX, cursorDeltaY)) ci.cancel();
	}

	@Inject(method = "playStepSound", at = @At("HEAD"), cancellable = true, require = 0)
	private void v1zir$snowStep(BlockPos pos, BlockState state, CallbackInfo ci) {
		if ((Object) this != MinecraftClient.getInstance().player) return;
		if (WinterModule.snowStep((Entity) (Object) this, pos, state)) ci.cancel();
	}

	@Unique
	private double v1zir$zoomMouseScale() {
		if ((Object) this != MinecraftClient.getInstance().player) return 1.0;
		ZoomModule zoom = ModuleManager.get(ZoomModule.class);
		return zoom != null && zoom.isEnabled() ? zoom.mouseScale() : 1.0;
	}
}
