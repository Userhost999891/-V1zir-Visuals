package dev.v1zir.visuals.render.world;

import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.impl.effects.HitMarkerModule;
import dev.v1zir.visuals.module.impl.effects.HitParticlesModule;
import dev.v1zir.visuals.module.impl.effects.KillEffectsModule;
import dev.v1zir.visuals.module.impl.effects.PopChamsModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;

import java.util.Arrays;

/** Shared combat state for the visual effects and the target HUD. */
public final class Effects {
	private static final long TARGET_TIMEOUT = 8_000_000_000L;
	/** A death this long after your last hit on the entity still counts as your kill. */
	private static final long KILL_WINDOW = 5_000_000_000L;

	private static LivingEntity target;
	private static long lastAttack;
	// Entities hit recently, watched for their death; a few slots are plenty, fights rarely involve more.
	private static final LivingEntity[] hit = new LivingEntity[8];
	private static final long[] hitTime = new long[8];

	private Effects() {
	}

	/** Called when the local player attacks an entity (ClientPlayerInteractionManagerMixin). */
	public static void onAttack(Entity entity) {
		long now = System.nanoTime();
		if (entity instanceof LivingEntity living) {
			target = living;
			lastAttack = now;
			if (living.getHealth() > 0f) remember(living, now);
		}
		HitParticlesModule particles = ModuleManager.get(HitParticlesModule.class);
		if (particles != null && particles.isEnabled()) particles.spawn(entity);
		HitMarkerModule marker = ModuleManager.get(HitMarkerModule.class);
		if (marker != null && marker.isEnabled()) marker.onHit();
	}

	/** A totem of undying was used (ClientPlayNetworkHandlerMixin, entity status 35). */
	public static void onTotemPop(Entity entity) {
		PopChamsModule chams = ModuleManager.get(PopChamsModule.class);
		if (chams != null && chams.isEnabled() && entity instanceof PlayerEntity player) chams.onPop(player);
	}

	/** Its own slot if it is already watched, else a free one, else the one hit longest ago. */
	private static void remember(LivingEntity entity, long now) {
		int slot = -1;
		for (int i = 0; i < hit.length; i++) {
			if (hit[i] == entity) {
				slot = i;
				break;
			}
			if (slot < 0 || hit[slot] != null && (hit[i] == null || hitTime[i] < hitTime[slot])) slot = i;
		}
		hit[slot] = entity;
		hitTime[slot] = now;
	}

	private static void onKill(LivingEntity entity) {
		KillEffectsModule effects = ModuleManager.get(KillEffectsModule.class);
		if (effects != null && effects.isEnabled()) effects.spawn(entity);
		HitMarkerModule marker = ModuleManager.get(HitMarkerModule.class);
		if (marker != null && marker.isEnabled()) marker.onKill();
	}

	public static void tick(MinecraftClient client) {
		if (client.player == null || client.world == null) {
			target = null;
			Arrays.fill(hit, null);
			return;
		}
		long now = System.nanoTime();
		for (int i = 0; i < hit.length; i++) {
			LivingEntity entity = hit[i];
			if (entity == null) continue;
			// Health reaches zero before the entity is removed, also when both arrive in the same tick.
			if (entity.getHealth() <= 0f) {
				hit[i] = null;
				onKill(entity);
			} else if (entity.isRemoved() || now - hitTime[i] > KILL_WINDOW) {
				hit[i] = null;
			}
		}
		if (target != null) {
			boolean expired = now - lastAttack > TARGET_TIMEOUT;
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
