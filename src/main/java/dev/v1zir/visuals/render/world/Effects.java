package dev.v1zir.visuals.render.world;

import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.impl.effects.HitParticlesModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

/** Shared combat state for the visual effects and the target HUD. */
public final class Effects {
	private static final long TARGET_TIMEOUT = 8_000_000_000L;

	private static LivingEntity target;
	private static long lastAttack;

	private Effects() {
	}

	/** Called when the local player attacks an entity (ClientPlayerInteractionManagerMixin). */
	public static void onAttack(Entity entity) {
		if (entity instanceof LivingEntity living) {
			target = living;
			lastAttack = System.nanoTime();
		}
		HitParticlesModule particles = ModuleManager.get(HitParticlesModule.class);
		if (particles != null && particles.isEnabled()) particles.spawn(entity);
	}

	public static void tick(MinecraftClient client) {
		if (client.player == null || client.world == null) {
			target = null;
			return;
		}
		if (target != null) {
			boolean expired = System.nanoTime() - lastAttack > TARGET_TIMEOUT;
			if (!target.isAlive() || target.isRemoved() || target.distanceTo(client.player) > 16f || expired) target = null;
		}
	}

	/** The entity the player is fighting, or the one under the crosshair. */
	public static LivingEntity target() {
		if (target != null) return target;
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.targetedEntity instanceof LivingEntity living && living.isAlive()) return living;
		return null;
	}

	/** Only entities that were actually attacked recently (used by Target ESP). */
	public static LivingEntity combatTarget() {
		return target;
	}
}
