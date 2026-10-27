package dev.v1zir.visuals.module.impl.effects;

import com.mojang.blaze3d.textures.GpuTextureView;
import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.impl.visuals.NametagsModule;
import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.module.setting.NumberSetting;
import dev.v1zir.visuals.render.world.WorldEffect;
import dev.v1zir.visuals.render.world.WorldEffectRenderer;
import dev.v1zir.visuals.render.world.WorldSprites;
import dev.v1zir.visuals.render.world.WorldText;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Atlases;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Hearts above other players, and mobs on request, showing their exact health the way the HUD shows yours: health / 2
 * hearts with halves (19 HP is 9 and a half), golden absorption hearts after the red ones and empty containers for the
 * missing health, ten to a row; past two rows a single row stands for "xN" each. Vanilla's own heart sprites, crisp at
 * any distance (WorldSprites), facing the camera above the name tag (above the Nametags plate while that is on) and
 * growing with distance so they stay readable. Health the client does not know shows nothing: on a server that hides
 * it (hits that never take anything off) the hearts stay off until the next world.
 */
public final class HealthIndicatorModule extends Module implements WorldEffect {
	private static final Identifier CONTAINER = Identifier.ofVanilla("hud/heart/container");
	private static final Identifier FULL = Identifier.ofVanilla("hud/heart/full");
	private static final Identifier HALF = Identifier.ofVanilla("hud/heart/half");
	private static final Identifier FROZEN_FULL = Identifier.ofVanilla("hud/heart/frozen_full");
	private static final Identifier FROZEN_HALF = Identifier.ofVanilla("hud/heart/frozen_half");
	private static final Identifier ABSORBING_FULL = Identifier.ofVanilla("hud/heart/absorbing_full");
	private static final Identifier ABSORBING_HALF = Identifier.ofVanilla("hud/heart/absorbing_half");
	/** Blocks per sprite pixel at scale 1 (vanilla's name tag text uses 0.025). */
	private static final float PIXEL = 0.016f;
	/** Distance up to which the hearts keep their world size; past it they grow to keep MIN_SIZE of their on-screen size. */
	private static final float NEAR = 6f;
	private static final float MIN_SIZE = 0.75f;
	private static final int PER_ROW = 10;
	private static final int MAX_ROWS = 2;
	/** Hits that took nothing off before the server counts as hiding health. */
	private static final int HIDDEN_AFTER = 2;

	public final BoolSetting mobs = add(new BoolSetting("Mobs", false));
	public final NumberSetting range = add(new NumberSetting("Range", 32, 8, 64, 2).suffix(" m"));
	public final BoolSetting throughWalls = add(new BoolSetting("Through walls", false));
	public final NumberSetting scale = add(new NumberSetting("Scale", 1.0, 0.5, 2.0, 0.05).suffix("x"));
	public final BoolSetting number = add(new BoolSetting("Number", false));

	private final Int2ObjectOpenHashMap<Entry> byId = new Int2ObjectOpenHashMap<>();
	/** The same entries as a list, kept sorted back to front while drawing. */
	private final List<Entry> entries = new ArrayList<>();
	private final WorldText.Style text = new WorldText.Style().weight(0.05f).shadow().align(WorldText.Align.LEFT);
	/** Entity ids start over in a new world, and so does the verdict on hidden health. */
	private ClientWorld world;
	private int emptyHits;
	// This frame's sprites of the GUI atlas.
	private Sprite container;
	private Sprite full;
	private Sprite half;
	private Sprite frozenFull;
	private Sprite frozenHalf;
	private Sprite absorbingFull;
	private Sprite absorbingHalf;

	public HealthIndicatorModule() {
		super("Health Indicator", "Exact health in hearts above players", Category.COMBAT);
		setSubCategory("Info");
	}

	@Override
	protected void onDisable() {
		byId.clear();
		entries.clear();
	}

	@Override
	public void onTick() {
		if (mc.world != world) {
			world = mc.world;
			emptyHits = 0;
			onDisable();
		}
		for (int i = 0; i < entries.size(); i++) entries.get(i).seen = false;
		if (mc.world != null && mc.player != null && emptyHits < HIDDEN_AFTER) {
			double max = range.get() * range.get();
			if (mobs.on()) {
				for (Entity entity : mc.world.getEntities()) {
					if (entity instanceof LivingEntity living && !(entity instanceof ArmorStandEntity)) watch(living, max);
				}
			} else {
				for (PlayerEntity player : mc.world.getPlayers()) watch(player, max);
			}
		}
		// Entries that are gone fade out where they were, then go.
		for (int i = entries.size() - 1; i >= 0; i--) {
			Entry entry = entries.get(i);
			entry.appear.target(entry.seen ? 1f : 0f);
			if (!entry.seen && entry.appear.get() < 0.01f) {
				byId.remove(entry.entity.getId());
				entries.remove(i);
			}
		}
	}

	private void watch(LivingEntity e, double maxSquared) {
		if (e == mc.player || !e.isAlive() || e.isSpectator() || e.isInvisibleTo(mc.player) || e.squaredDistanceTo(mc.player) > maxSquared) return;
		float hp = e.getHealth();
		float max = e.getMaxHealth();
		// Values no real health has (none, NaN, past the maximum) are not shown.
		if (!(hp > 0f && hp <= max && max < 100000f)) return;
		Entry entry = byId.get(e.getId());
		if (entry == null) {
			entry = new Entry(e);
			byId.put(e.getId(), entry);
			entries.add(entry);
		}
		entry.entity = e;
		entry.seen = true;
		if (entry.update(e, hp, max)) emptyHits++;
	}

	@Override
	public void render3D(float tickDelta) {
		if (entries.isEmpty() || mc.options.hudHidden) return;
		SpriteAtlasTexture atlas = mc.getAtlasManager().getAtlasTexture(Atlases.GUI);
		GpuTextureView view = atlas.getGlTextureView();
		container = atlas.getSprite(CONTAINER);
		full = atlas.getSprite(FULL);
		half = atlas.getSprite(HALF);
		frozenFull = atlas.getSprite(FROZEN_FULL);
		frozenHalf = atlas.getSprite(FROZEN_HALF);
		absorbingFull = atlas.getSprite(ABSORBING_FULL);
		absorbingHalf = atlas.getSprite(ABSORBING_HALF);
		NametagsModule tags = ModuleManager.get(NametagsModule.class);
		for (int i = 0; i < entries.size(); i++) {
			Entry e = entries.get(i);
			if (e.seen) {
				LivingEntity entity = e.entity;
				e.x = MathHelper.lerp(tickDelta, entity.lastRenderX, entity.getX());
				e.z = MathHelper.lerp(tickDelta, entity.lastRenderZ, entity.getZ());
				e.y = MathHelper.lerp(tickDelta, entity.lastRenderY, entity.getY()) + entity.getHeight();
				// Above the Nametags plate, else above vanilla's name tag (players and named mobs have one).
				float tag = tags != null ? tags.tagTop(entity, distance(e)) : 0f;
				e.y += tag > 0f ? tag + 0.08f : entity instanceof PlayerEntity || entity.hasCustomName() ? 0.6f : 0.3f;
			}
			e.dist = distance(e);
		}
		// Back to front, so nearer hearts blend over farther ones; the order barely changes between frames.
		for (int i = 1; i < entries.size(); i++) {
			Entry e = entries.get(i);
			int j = i - 1;
			for (; j >= 0 && entries.get(j).dist < e.dist; j--) entries.set(j + 1, entries.get(j));
			entries.set(j + 1, e);
		}
		for (int i = 0; i < entries.size(); i++) {
			Entry e = entries.get(i);
			float fade = e.appear.get();
			if (fade > 0.01f) draw(e, view, fade);
		}
	}

	private static float distance(Entry e) {
		double dx = e.x - WorldEffectRenderer.camX;
		double dy = e.y - WorldEffectRenderer.camY;
		double dz = e.z - WorldEffectRenderer.camZ;
		return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	/** One entity's hearts, rows growing upwards from (x, y, z), and the number to the right of the first row. */
	private void draw(Entry e, GpuTextureView view, float fade) {
		float px = PIXEL * scale.f() * (1f + MIN_SIZE * Math.max(0f, e.dist / NEAR - 1f));
		int maxHalves = e.maxHalves;
		int health = Math.min(e.healthHalves, maxHalves);
		int absorption = e.absorptionHalves;
		int containers = (maxHalves + 1) / 2;
		int golden = (absorption + 1) / 2;
		// Past two rows every heart stands for `factor` of them, in a single row.
		int factor = containers + golden > PER_ROW * MAX_ROWS ? MathHelper.ceil((containers + golden) / (float) PER_ROW) : 1;
		if (factor > 1) {
			health = MathHelper.ceil(health / (float) factor);
			containers = (MathHelper.ceil(maxHalves / (float) factor) + 1) / 2;
			golden = Math.min((MathHelper.ceil(absorption / (float) factor) + 1) / 2, Math.max(0, PER_ROW - containers));
			absorption = Math.min(MathHelper.ceil(absorption / (float) factor), golden * 2);
		}
		int hearts = containers + golden;
		if (hearts == 0) return;
		float width = ((Math.min(hearts, PER_ROW) - 1) * 8 + 9) * px;
		float size = 9f * px;
		float left = (size - width) * 0.5f;
		int tint = ColorUtil.withAlpha(0xFFFFFF, Math.round(255f * fade));
		boolean through = throughWalls.on();
		for (int i = 0; i < hearts; i++) {
			float dx = left + (i % PER_ROW) * 8f * px;
			float dy = (i / PER_ROW) * 10f * px + size * 0.5f;
			WorldSprites.draw(e.x, e.y, e.z, dx, dy, size, size, container, view, through, tint);
			// Red halves first, then the golden ones of absorption.
			int halves = i < containers ? health - i * 2 : absorption - (i - containers) * 2;
			if (halves <= 0) continue;
			Sprite fill = i < containers
				? (e.frozen ? (halves > 1 ? frozenFull : frozenHalf) : (halves > 1 ? full : half))
				: (halves > 1 ? absorbingFull : absorbingHalf);
			WorldSprites.draw(e.x, e.y, e.z, dx, dy, size, size, fill, view, through, tint);
		}
		String label = e.label(number.on(), factor);
		if (label != null) {
			WorldText.draw(label, e.x, e.y, e.z, width * 0.5f + 2.5f * px, size * 0.5f,
				text.size(8f * px).color(0xFFFFFFFF).alpha(fade).seeThrough(through));
		}
	}

	/** One entity's numbers, its place this frame and the check for health that a hit never takes off. */
	private static final class Entry {
		LivingEntity entity;
		boolean seen;
		final Anim appear = new Anim(0f, 8f);
		double x;
		double y;
		double z;
		float dist;
		int healthHalves;
		int maxHalves;
		int absorptionHalves;
		boolean frozen;
		private int lastHurt = Integer.MAX_VALUE;
		private float lastTotal;
		/** Health and absorption before the hit being watched (-1 when none) and the lowest they went since. */
		private float before = -1f;
		private float lowest;
		private String label;
		private int labelHalves = -1;
		private int labelFactor;
		private boolean labelNumber;

		Entry(LivingEntity entity) {
			this.entity = entity;
		}

		/** Takes the new numbers; true when a hit has just ended without taking any health or absorption off. */
		boolean update(LivingEntity e, float hp, float max) {
			float absorption = e.getAbsorptionAmount();
			healthHalves = MathHelper.ceil(hp);
			maxHalves = MathHelper.ceil(max);
			absorptionHalves = MathHelper.ceil(absorption);
			frozen = e.isFrozen();
			float total = hp + absorption;
			boolean empty = false;
			if (e.hurtTime > lastHurt) {
				before = lastTotal;
				lowest = total;
			} else if (before >= 0f) {
				lowest = Math.min(lowest, total);
				if (e.hurtTime == 0) {
					empty = lowest >= before;
					before = -1f;
				}
			}
			lastHurt = e.hurtTime;
			lastTotal = total;
			return empty;
		}

		/** "9.5" hearts with Number on, "xN" when one heart stands for several; null for neither. Rebuilt on change only. */
		String label(boolean number, int factor) {
			if (!number && factor == 1) return null;
			if (healthHalves != labelHalves || factor != labelFactor || number != labelNumber) {
				labelHalves = healthHalves;
				labelFactor = factor;
				labelNumber = number;
				String hearts = healthHalves / 2 + (healthHalves % 2 == 1 ? ".5" : "");
				label = !number ? "x" + factor : factor > 1 ? hearts + "  x" + factor : hearts;
			}
			return label;
		}
	}
}
