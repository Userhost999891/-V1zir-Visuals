package dev.v1zir.visuals.mixin;

import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Pop Chams: vanilla's own body transforms (rotation, swim and glide lean, player scale) for a copy of a model. */
@Mixin(LivingEntityRenderer.class)
public interface LivingEntityRendererInvoker {
	@Invoker("setupTransforms")
	void v1zir$setupTransforms(LivingEntityRenderState state, MatrixStack matrices, float bodyYaw, float baseHeight);

	@Invoker("scale")
	void v1zir$scale(LivingEntityRenderState state, MatrixStack matrices);
}
