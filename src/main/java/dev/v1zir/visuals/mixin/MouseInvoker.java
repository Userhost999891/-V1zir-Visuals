package dev.v1zir.visuals.mixin;

import net.minecraft.client.Mouse;
import net.minecraft.client.input.MouseInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The mouse's own input handlers, for a controller standing in for the mouse in screens (Controller). */
@Mixin(Mouse.class)
public interface MouseInvoker {
	@Invoker("onCursorPos")
	void v1zir$cursorPos(long window, double x, double y);

	@Invoker("onMouseButton")
	void v1zir$mouseButton(long window, MouseInput input, int action);

	@Invoker("onMouseScroll")
	void v1zir$scroll(long window, double horizontal, double vertical);
}
