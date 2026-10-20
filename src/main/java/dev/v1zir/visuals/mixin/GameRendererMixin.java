package dev.v1zir.visuals.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.impl.visuals.AspectRatioModule;
import dev.v1zir.visuals.module.impl.visuals.CameraTweaksModule;
import dev.v1zir.visuals.module.impl.visuals.ZoomModule;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** World FOV and projection (Zoom, Aspect Ratio), the hurt shake and the first-person hand. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	/** Only the world FOV zooms (changingFov); the hand keeps its own FOV. */
	@ModifyReturnValue(method = "getFov", at = @At("RETURN"))
	private float v1zir$zoom(float fov, @Local(argsOnly = true) boolean changingFov) {
		if (!changingFov) return fov;
		ZoomModule zoom = ModuleManager.get(ZoomModule.class);
		return zoom != null && zoom.isEnabled() ? zoom.fov(fov) : fov;
	}

	@ModifyReturnValue(method = "getBasicProjectionMatrix", at = @At("RETURN"))
	private Matrix4f v1zir$aspectRatio(Matrix4f projection) {
		AspectRatioModule module = ModuleManager.get(AspectRatioModule.class);
		return module != null && module.active() ? module.apply(projection) : projection;
	}

	@Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true)
	private void v1zir$noHurtShake(MatrixStack matrices, float tickProgress, CallbackInfo ci) {
		CameraTweaksModule tweaks = ModuleManager.get(CameraTweaksModule.class);
		if (tweaks != null && tweaks.isEnabled() && tweaks.noHurtShake.on()) ci.cancel();
	}

	/** No hand while it is fully zoomed away or while the camera glides back into the head. */
	@Inject(method = "renderHand", at = @At("HEAD"), cancellable = true)
	private void v1zir$hideHand(float tickProgress, boolean sleeping, Matrix4f positionMatrix, CallbackInfo ci) {
		ZoomModule zoom = ModuleManager.get(ZoomModule.class);
		CameraTweaksModule tweaks = ModuleManager.get(CameraTweaksModule.class);
		if (zoom != null && zoom.isEnabled() && zoom.handHidden() || tweaks != null && tweaks.isEnabled() && tweaks.hidesHand()) ci.cancel();
	}
}
