package dev.v1zir.visuals.mixin;

import dev.v1zir.visuals.gui.custom.CustomUi;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.cursor.StandardCursors;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Custom UI: sliders show their value as an accent fill with a slim handle. */
@Mixin(SliderWidget.class)
public abstract class SliderWidgetMixin {
	@Shadow
	protected double value;
	@Shadow
	private boolean dragging;

	@Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true)
	private void v1zir$renderWidget(DrawContext context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
		if (!CustomUi.sliders()) return;
		ClickableWidget widget = (ClickableWidget) (Object) this;
		CustomUi.slider(context, widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight(), value, widget.getMessage(),
			widget.isSelected(), widget.active);
		if (widget.isHovered()) context.setCursor(dragging ? StandardCursors.RESIZE_EW : StandardCursors.POINTING_HAND);
		ci.cancel();
	}
}
