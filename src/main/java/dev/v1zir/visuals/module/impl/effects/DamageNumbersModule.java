package dev.v1zir.visuals.module.impl.effects;

import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.module.setting.ColorSetting;
import dev.v1zir.visuals.module.setting.NumberSetting;
import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.world.WorldEffect;
import dev.v1zir.visuals.render.world.WorldEffectRenderer;
import dev.v1zir.visuals.render.world.WorldText;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Floating numbers for the damage living entities take (and optionally what they heal), read from
 * their health as the client sees it. A number pops up above the entity, arcs up and sideways with
 * a little gravity and fades; big hits are larger and take the big hit colour.
 */
public final class DamageNumbersModule extends Module implements WorldEffect {
	private static final int MAX_NUMBERS = 48;
	private static final int HEAL_COLOR = 0xFF5CFF8A;
	private static final float GRAVITY = 5.5f;

	public final BoolSetting healing = add(new BoolSetting("Healing", true));
	public final BoolSetting self = add(new BoolSetting("Self", false));
	public final BoolSetting hearts = add(new BoolSetting("Hearts", false));
	public final NumberSetting size = add(new NumberSetting("Size", 1.0, 0.5, 2.5, 0.05).suffix("x"));
	public final NumberSetting duration = add(new NumberSetting("Duration", 1.2, 0.5, 3.0, 0.05).suffix("s"));
	public final NumberSetting bigHit = add(new NumberSetting("Big hit", 6, 2, 20, 0.5).suffix(" HP"));
	public final ColorSetting bigColor = add(new ColorSetting("Big hit color", 0xFFFF4D5E, false));
	public final BoolSetting throughWalls = add(new BoolSetting("Through walls", false));
	public final NumberSetting range = add(new NumberSetting("Range", 32, 8, 64, 1).suffix(" m"));

	private final List<Popup> numbers = new ArrayList<>();
	// Health (plus absorption) per entity id at the last tick; rebuilt every tick so unloaded entities drop out.
	private Int2FloatOpenHashMap health = new Int2FloatOpenHashMap();
	private Int2FloatOpenHashMap seen = new Int2FloatOpenHashMap();
	private final WorldText.Style style = new WorldText.Style().weight(0.06f).shadow();
	private long lastFrame = System.nanoTime();

	public DamageNumbersModule() {
		super("Damage Numbers", "Floating damage numbers", Category.EFFECTS);
		health.defaultReturnValue(-1f);
		seen.defaultReturnValue(-1f);
	}

	@Override
	protected void onDisable() {
		numbers.clear();
		health.clear();
	}

	@Override
	public void onTick() {
		if (mc.world == null || mc.player == null) {
			health.clear();
			return;
		}
		double maxDistance = range.f() * range.f();
		// Your own numbers would sit right above the camera in first person.
		boolean showSelf = self.on() && !mc.options.getPerspective().isFirstPerson();
		for (Entity entity : mc.world.getEntities()) {
			if (!(entity instanceof LivingEntity living) || living == mc.player && !showSelf) continue;
			if (living.squaredDistanceTo(mc.player) > maxDistance) continue;
			// Absorption hearts take damage first, so they count as health.
			float hp = living.getHealth() + living.getAbsorptionAmount();
			int id = living.getId();
			seen.put(id, hp);
			float before = health.get(id);
			if (before < 0f) continue;
			if (before - hp >= 0.05f) spawn(living, before - hp, false);
			else if (hp - before >= 0.05f && healing.on()) spawn(living, hp - before, true);
		}
		Int2FloatOpenHashMap last = health;
		health = seen;
		seen = last;
		seen.clear();
	}

	private void spawn(LivingEntity entity, float amount, boolean heal) {
		int tenths = Math.round((hearts.on() ? amount / 2f : amount) * 10f);
		if (tenths == 0) return;
		String text = tenths % 10 == 0 ? Integer.toString(tenths / 10) : tenths / 10 + "." + tenths % 10;
		if (numbers.size() == MAX_NUMBERS) numbers.remove(0);
		ThreadLocalRandom random = ThreadLocalRandom.current();
		double angle = random.nextDouble() * Math.PI * 2;
		double speed = 0.5 + random.nextDouble() * 0.6;
		double spread = entity.getWidth() * 0.3;
		float big = heal ? 0f : Math.min(1f, amount / bigHit.f());
		numbers.add(new Popup(heal ? "+" + text : text, heal, big,
			entity.getX() + (random.nextDouble() - 0.5) * spread, entity.getY() + entity.getHeight() + 0.25, entity.getZ() + (random.nextDouble() - 0.5) * spread,
			Math.cos(angle) * speed, 2.6 + random.nextDouble() * 0.6, Math.sin(angle) * speed));
	}

	@Override
	public void render3D(float tickDelta) {
		long now = System.nanoTime();
		float dt = Math.min((now - lastFrame) / 1.0e9f, 0.05f);
		lastFrame = now;
		if (numbers.isEmpty()) return;
		float life = duration.f();
		float base = 0.32f * size.f();
		int accent = Theme.accent();
		int big = bigColor.argb();
		style.font(Fonts.bold()).seeThrough(throughWalls.on());
		float drag = 1f - 1.8f * dt;
		for (int i = numbers.size() - 1; i >= 0; i--) {
			Popup n = numbers.get(i);
			float age = (now - n.born) / 1.0e9f;
			if (age >= life) {
				numbers.remove(i);
				continue;
			}
			n.vy -= GRAVITY * dt;
			n.vx *= drag;
			n.vz *= drag;
			n.x += n.vx * dt;
			n.y += n.vy * dt;
			n.z += n.vz * dt;
			float fade = 1f - Anim.clamp01((age / life - 0.6f) / 0.4f);
			// Far numbers grow a little so they stay readable.
			double dx = n.x - WorldEffectRenderer.camX, dy = n.y - WorldEffectRenderer.camY, dz = n.z - WorldEffectRenderer.camZ;
			float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
			float scale = base * (1f + 0.6f * n.big) * n.pop.get() * (0.75f + 0.25f * fade) * (1f + Math.max(0f, distance - 8f) * 0.035f);
			int color = n.heal ? HEAL_COLOR : ColorUtil.lerp(0xFFFFFFFF, big, n.big);
			int glow = n.heal ? HEAL_COLOR : ColorUtil.lerp(accent, big, n.big);
			WorldText.draw(n.text, n.x, n.y, n.z, style.size(scale).color(color).glow(glow, 0.35f + 0.35f * n.big).alpha(fade));
		}
	}

	private static final class Popup {
		final String text;
		final boolean heal;
		/** 0 for small hits up to 1 at the big hit threshold. */
		final float big;
		final long born = System.nanoTime();
		/** Starts enlarged and springs back: the pop when the number appears. */
		final Anim pop;
		double x, y, z, vx, vy, vz;

		Popup(String text, boolean heal, float big, double x, double y, double z, double vx, double vy, double vz) {
			this.text = text;
			this.heal = heal;
			this.big = big;
			this.pop = new Anim(1.5f + 0.5f * big, 12f).target(1f);
			this.x = x;
			this.y = y;
			this.z = z;
			this.vx = vx;
			this.vy = vy;
			this.vz = vz;
		}
	}
}
