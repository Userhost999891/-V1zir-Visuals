package dev.v1zir.visuals.mixin;

import net.minecraft.client.render.item.ItemRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The layers of an item's render state: Hand Chams' arm reads how the held item's model is placed in the hand. */
@Mixin(ItemRenderState.class)
public interface ItemRenderStateAccessor {
	@Accessor("layers")
	ItemRenderState.LayerRenderState[] v1zir$layers();

	@Accessor("layerCount")
	int v1zir$layerCount();
}
