package dev.v1zir.visuals.mixin;

import dev.v1zir.visuals.controller.Targets;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every widget of the game a screen draws, wherever it sits (a recipe book's buttons too), is a place for the D-pad. */
@Mixin(ClickableWidget.class)
public abstract class ClickableWidgetMixin {
	@Inject(method = "render", at = @At("HEAD"))
	private void v1zir$target(DrawContext context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
		if (!Targets.collecting()) return;
		ClickableWidget self = (ClickableWidget) (Object) this;
		if (self.visible && self.active) Targets.addDrawn(context, self.getX(), self.getY(), self.getWidth(), self.getHeight());
	}
}
