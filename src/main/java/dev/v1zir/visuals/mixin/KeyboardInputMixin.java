package dev.v1zir.visuals.mixin;

import dev.v1zir.visuals.controller.Controller;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A controller's left stick and buttons move the player alongside the keys: what the keys asked for stays, the
 * controller's adds to it, and a tilted stick walks as far as it is tilted (Controller).
 */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends Input {
	@Inject(method = "tick", at = @At("TAIL"))
	private void v1zir$controller(CallbackInfo ci) {
		Controller.Movement m = Controller.movement();
		if (m == null) return;
		PlayerInput keys = this.playerInput;
		this.playerInput = new PlayerInput(keys.forward() || m.forward(), keys.backward() || m.backward(), keys.left() || m.left(),
			keys.right() || m.right(), keys.jump() || m.jump(), keys.sneak() || m.sneak(), keys.sprint() || m.sprint());
		if (m.moving()) this.movementVector = new Vec2f(m.x(), m.y());
	}
}
