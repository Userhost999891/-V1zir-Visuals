package dev.v1zir.visuals.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.ProfileKeys;
import net.minecraft.client.session.Session;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.CompletableFuture;

/** The account the game plays as (Accounts): its session, chat signing keys and profile. */
@Mixin(MinecraftClient.class)
public interface MinecraftClientAccessor {
	@Mutable
	@Accessor("session")
	void v1zir$setSession(Session session);

	@Accessor("profileKeys")
	ProfileKeys v1zir$getProfileKeys();

	@Mutable
	@Accessor("profileKeys")
	void v1zir$setProfileKeys(ProfileKeys keys);

	@Accessor("gameProfileFuture")
	CompletableFuture<?> v1zir$getGameProfileFuture();

	@Mutable
	@Accessor("gameProfileFuture")
	void v1zir$setGameProfileFuture(CompletableFuture<?> future);
}
