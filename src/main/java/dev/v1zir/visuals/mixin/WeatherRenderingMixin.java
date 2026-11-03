package dev.v1zir.visuals.mixin;

import dev.v1zir.visuals.module.impl.visuals.NoRenderModule;
import net.minecraft.client.render.WeatherRendering;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No Render: falling rain and snow, and the splashes and sounds they make around you. The sky keeps its weather. */
@Mixin(WeatherRendering.class)
public abstract class WeatherRenderingMixin {
	@Inject(method = "renderPrecipitation", at = @At("HEAD"), cancellable = true)
	private void v1zir$precipitation(CallbackInfo ci) {
		NoRenderModule noRender = NoRenderModule.active();
		if (noRender != null && noRender.weather.on()) ci.cancel();
	}

	@Inject(method = "addParticlesAndSound", at = @At("HEAD"), cancellable = true)
	private void v1zir$splashes(CallbackInfo ci) {
		NoRenderModule noRender = NoRenderModule.active();
		if (noRender != null && noRender.weather.on()) ci.cancel();
	}
}
