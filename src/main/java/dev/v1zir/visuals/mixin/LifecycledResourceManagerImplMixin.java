package dev.v1zir.visuals.mixin;

import dev.v1zir.visuals.pack.PackMixer;
import net.minecraft.resource.LifecycledResourceManagerImpl;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Predicate;

/**
 * The Pack Mixer's choices: where the game asks for a texture, the chosen pack's file stands in for the top pack's,
 * both for single lookups (entity and GUI textures) and for the directory listings the atlases are stitched from.
 */
@Mixin(LifecycledResourceManagerImpl.class)
public abstract class LifecycledResourceManagerImplMixin {
	@Inject(method = "getResource(Lnet/minecraft/util/Identifier;)Ljava/util/Optional;", at = @At("RETURN"), cancellable = true)
	private void v1zir$chosenPack(Identifier id, CallbackInfoReturnable<Optional<Resource>> cir) {
		String pack = PackMixer.choice(id);
		if (pack == null) return;
		Resource chosen = PackMixer.pick((ResourceManager) (Object) this, id, pack);
		if (chosen != null) cir.setReturnValue(Optional.of(chosen));
	}

	@Inject(method = "findResources(Ljava/lang/String;Ljava/util/function/Predicate;)Ljava/util/Map;", at = @At("RETURN"), cancellable = true)
	private void v1zir$chosenPacks(String startingPath, Predicate<Identifier> allowedPathPredicate, CallbackInfoReturnable<Map<Identifier, Resource>> cir) {
		Map<Identifier, String> choices = PackMixer.choices();
		if (choices.isEmpty()) return;
		Map<Identifier, Resource> found = cir.getReturnValue();
		Map<Identifier, Resource> changed = null;
		for (Map.Entry<Identifier, String> e : choices.entrySet()) {
			if (!found.containsKey(e.getKey())) continue;
			Resource chosen = PackMixer.pick((ResourceManager) (Object) this, e.getKey(), e.getValue());
			if (chosen == null) continue;
			if (changed == null) changed = new TreeMap<>(found);
			changed.put(e.getKey(), chosen);
		}
		if (changed != null) cir.setReturnValue(changed);
	}
}
