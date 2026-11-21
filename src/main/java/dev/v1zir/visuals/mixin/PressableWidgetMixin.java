package dev.v1zir.visuals.mixin;

import dev.v1zir.visuals.gui.custom.CustomUi;
import net.minecraft.client.font.DrawnTextConsumer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.PressableWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Custom UI: vanilla buttons get the rounded Theme-style look with an SDF label. */
@Mixin(PressableWidget.class)
public abstract class PressableWidgetMixin {
	@Unique
	private boolean v1zir$styled;

	@Inject(method = "drawButton", at = @At("HEAD"), cancellable = true)
	private void v1zir$drawButton(DrawContext context, CallbackInfo ci) {
		Object self = this;
		v1zir$styled = CustomUi.buttons() && (self instanceof ButtonWidget || self instanceof CyclingButtonWidget);
		if (!v1zir$styled) return;
		ClickableWidget widget = (ClickableWidget) self;
		CustomUi.button(context, widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight(), widget.getMessage(),
			widget.isSelected(), widget.active);
		ci.cancel();
	}

	/** The label was already drawn with the SDF font by drawButton. */
	@Inject(method = "drawLabel", at = @At("HEAD"), cancellable = true)
	private void v1zir$drawLabel(DrawnTextConsumer consumer, CallbackInfo ci) {
		if (v1zir$styled) ci.cancel();
	}
}
