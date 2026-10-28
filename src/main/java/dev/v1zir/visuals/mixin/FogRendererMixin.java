package dev.v1zir.visuals.mixin;

import dev.v1zir.visuals.render.sky.SkyRenderer;
import dev.v1zir.visuals.util.ColorUtil;
import net.minecraft.client.render.fog.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** "Sync fog": tints the terrain fog with the horizon colour of the active sky preset. */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
	@Inject(method = "getFogColor", at = @At("RETURN"), cancellable = true)
	private void v1zir$getFogColor(CallbackInfoReturnable<Vector4f> cir) {
		int color = SkyRenderer.fogOverride();
		if (color == 0) return;
		Vector4f original = cir.getReturnValue();
		float w = original != null ? original.w : 1f;
		cir.setReturnValue(new Vector4f(ColorUtil.red(color) / 255f, ColorUtil.green(color) / 255f, ColorUtil.blue(color) / 255f, w));
	}
}
