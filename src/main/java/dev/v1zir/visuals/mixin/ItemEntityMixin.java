package dev.v1zir.visuals.mixin;

import dev.v1zir.visuals.module.impl.world.ItemPhysicsModule;
import net.minecraft.entity.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Item Physics keeps each dropped item's tumble on the entity, created the first time it is drawn with the module on. */
@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin implements ItemPhysicsModule.Tumbling {
	@Unique
	private ItemPhysicsModule.Tumble v1zir$tumble;

	@Override
	public ItemPhysicsModule.Tumble v1zir$tumble() {
		if (v1zir$tumble == null) v1zir$tumble = new ItemPhysicsModule.Tumble();
		return v1zir$tumble;
	}
}
