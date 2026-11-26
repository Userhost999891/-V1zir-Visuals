package dev.v1zir.visuals.mixin;

import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.model.json.Transformation;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.function.Supplier;

/** One layer of an item's model as it will be drawn: its display transform and the corners of its geometry. */
@Mixin(ItemRenderState.LayerRenderState.class)
public interface LayerRenderStateAccessor {
	@Accessor("transform")
	Transformation v1zir$transform();

	@Accessor("vertices")
	Supplier<Vector3fc[]> v1zir$vertices();
}
