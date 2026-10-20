package dev.v1zir.visuals.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
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
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * World FOV and projection (Zoom, Aspect Ratio), the hurt shake and the first-person hand.
 * <p>
 * renderWorld builds three projections: the one the world is drawn with (getBasicProjectionMatrix),
 * a plain one for frustum culling (getProjectionMatrix) and the hand's (ProjectionMatrix3.set). They
 * are changed at those call sites only, so nothing else that asks for a projection is affected.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	private static final String RENDER_WORLD = "renderWorld";
	private static final String BASIC_PROJECTION = "Lnet/minecraft/client/render/GameRenderer;getBasicProjectionMatrix(F)Lorg/joml/Matrix4f;";
	private static final String CULLING_PROJECTION = "Lnet/minecraft/client/render/GameRenderer;getProjectionMatrix(F)Lorg/joml/Matrix4f;";
	private static final String HAND_PROJECTION = "Lnet/minecraft/client/render/ProjectionMatrix3;set(IIF)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;";

	/** Only the world FOV zooms (changingFov); the hand keeps its own FOV. */
	@ModifyReturnValue(method = "getFov", at = @At("RETURN"))
	private float v1zir$zoom(float fov, @Local(argsOnly = true) boolean changingFov) {
		if (!changingFov) return fov;
		ZoomModule zoom = ModuleManager.get(ZoomModule.class);
		return zoom != null && zoom.isEnabled() ? zoom.fov(fov) : fov;
	}

	/**
	 * Culling keeps the unzoomed FOV: a frustum narrowed by the zoom would leave chunks out of the
	 * visible set when the zoom is released or the view turns while zoomed.
	 */
	@ModifyArg(method = RENDER_WORLD, at = @At(value = "INVOKE", target = CULLING_PROJECTION))
	private float v1zir$cullingFov(float fov) {
		ZoomModule zoom = ModuleManager.get(ZoomModule.class);
		return zoom != null && zoom.isEnabled() ? zoom.unzoomedFov(fov) : fov;
	}

	@ModifyExpressionValue(method = RENDER_WORLD, at = @At(value = "INVOKE", target = BASIC_PROJECTION))
	private Matrix4f v1zir$stretchWorld(Matrix4f projection) {
		AspectRatioModule module = ModuleManager.get(AspectRatioModule.class);
		return module != null && module.active() ? module.stretchWorld(projection) : projection;
	}

	@ModifyExpressionValue(method = RENDER_WORLD, at = @At(value = "INVOKE", target = CULLING_PROJECTION))
	private Matrix4f v1zir$stretchCulling(Matrix4f projection) {
		AspectRatioModule module = ModuleManager.get(AspectRatioModule.class);
		return module != null && module.active() ? module.stretchCulling(projection) : projection;
	}

	/** The hand projection is built from the framebuffer size: a narrower width stretches the hand the same way. */
	@ModifyArg(method = RENDER_WORLD, at = @At(value = "INVOKE", target = HAND_PROJECTION), index = 0)
	private int v1zir$stretchHand(int width) {
		AspectRatioModule module = ModuleManager.get(AspectRatioModule.class);
		return module != null && module.active() ? module.handWidth(width) : width;
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
