package dev.v1zir.visuals.mixin;

import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.impl.visuals.CameraTweaksModule;
import dev.v1zir.visuals.module.impl.visuals.FreeLookModule;
import dev.v1zir.visuals.module.impl.visuals.ZoomModule;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Free Look angles, Camera Tweaks distance, clipping and perspective glide. */
@Mixin(Camera.class)
public abstract class CameraMixin {
	/** Runs once per frame before the camera is placed: polls the hold keys and advances the springs. */
	@Inject(method = "update", at = @At("HEAD"))
	private void v1zir$frame(CallbackInfo ci) {
		FreeLookModule freeLook = ModuleManager.get(FreeLookModule.class);
		if (freeLook != null && freeLook.isEnabled()) freeLook.frame();
		CameraTweaksModule tweaks = ModuleManager.get(CameraTweaksModule.class);
		if (tweaks != null && tweaks.isEnabled()) tweaks.frame();
		ZoomModule zoom = ModuleManager.get(ZoomModule.class);
		if (zoom != null && zoom.isEnabled()) zoom.frame();
	}

	@ModifyVariable(method = "update", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private boolean v1zir$thirdPerson(boolean thirdPerson) {
		CameraTweaksModule tweaks = ModuleManager.get(CameraTweaksModule.class);
		return tweaks != null && tweaks.isEnabled() ? tweaks.thirdPerson(thirdPerson) : thirdPerson;
	}

	@ModifyVariable(method = "update", at = @At("HEAD"), argsOnly = true, ordinal = 1)
	private boolean v1zir$frontView(boolean inverseView) {
		CameraTweaksModule tweaks = ModuleManager.get(CameraTweaksModule.class);
		return tweaks != null && tweaks.isEnabled() ? tweaks.frontView(inverseView) : inverseView;
	}

	@ModifyVariable(method = "setRotation", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private float v1zir$freeLookYaw(float yaw) {
		FreeLookModule freeLook = ModuleManager.get(FreeLookModule.class);
		return freeLook != null && freeLook.isEnabled() ? freeLook.yaw(yaw) : yaw;
	}

	@ModifyVariable(method = "setRotation", at = @At("HEAD"), argsOnly = true, ordinal = 1)
	private float v1zir$freeLookPitch(float pitch) {
		FreeLookModule freeLook = ModuleManager.get(FreeLookModule.class);
		return freeLook != null && freeLook.isEnabled() ? freeLook.pitch(pitch) : pitch;
	}

	/**
	 * Distance with clipping. The two handlers below act in exclusive cases, so their order at HEAD
	 * does not matter: this one when clipping stays on, the next one when it is off.
	 */
	@ModifyVariable(method = "clipToSpace", at = @At("HEAD"), argsOnly = true)
	private float v1zir$distance(float distance) {
		CameraTweaksModule tweaks = ModuleManager.get(CameraTweaksModule.class);
		return tweaks != null && tweaks.isEnabled() && !tweaks.noClip.on() ? tweaks.distance(distance) : distance;
	}

	@Inject(method = "clipToSpace", at = @At("HEAD"), cancellable = true)
	private void v1zir$noClip(float distance, CallbackInfoReturnable<Float> cir) {
		CameraTweaksModule tweaks = ModuleManager.get(CameraTweaksModule.class);
		if (tweaks != null && tweaks.isEnabled() && tweaks.noClip.on()) cir.setReturnValue(tweaks.distance(distance));
	}
}
