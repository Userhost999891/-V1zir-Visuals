package dev.v1zir.visuals.module.impl.effects;

import dev.v1zir.visuals.mixin.LivingEntityRendererInvoker;
import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.module.setting.ColorSetting;
import dev.v1zir.visuals.module.setting.ModeSetting;
import dev.v1zir.visuals.module.setting.NumberSetting;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.render.world.Draw3D;
import dev.v1zir.visuals.render.world.Effects;
import dev.v1zir.visuals.render.world.ModelCapture;
import dev.v1zir.visuals.render.world.Splatter;
import dev.v1zir.visuals.render.world.WorldEffect;
import dev.v1zir.visuals.render.world.WorldEffectRenderer;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntUnaryOperator;

/**
 * A finishing effect where an entity dies: a pillar of light from the sky, a branching lightning
 * strike, the body shattering into glowing cubes, a soul rising with a trailing wisp, a burst of
 * blood or a firework. It plays for your kills, or for every death nearby. Everything is client side
 * and purely visual. Paths are closed formulas of the age where they can be; only shards and blood,
 * which land and bounce, are stepped each frame. A soft screen edge flash and a sound are optional.
 */
public final class KillEffectsModule extends Module implements WorldEffect {
	private static final int PILLAR = 0;
	private static final int LIGHTNING = 1;
	private static final int SHATTER = 2;
	private static final int SOUL = 3;
	private static final int BLOOD = 4;
	private static final int FIREWORK = 5;
	/** Lifetime of each mode in seconds at Duration 1. */
	private static final float[] LIFE = {2.6f, 1.8f, 3.2f, 3.0f, 0.6f, 2.8f};
	private static final int MAX_EFFECTS = 8;
	private static final int FULL_BRIGHT = 0xF000F0;
	private static final float TAU = (float) (Math.PI * 2);
	/** Deaths of others count up to this far from the camera. */
	private static final double RANGE = 64;
	/** Height of the light pillar above the ground, in blocks. */
	private static final float BEAM = 64f;
	private static final float[] BEAM_STOPS = {0f, 2.5f, 10f, 28f, BEAM};
	private static final float[] BEAM_ALPHA = {1f, 0.85f, 0.5f, 0.2f, 0f};
	private static final int MOTES = 24;
	/** Lightning segment: two ends (x, y, z), width, order (0 main channel, 1 fork, 2 fork of a fork). */
	private static final int SEG = 8;
	private static final int MAX_SEGMENTS = 220;
	private static final int SPARKS = 16;
	/** Shard: position and velocity relative to the death spot, spin axis, angle, spin speed, half edge, tint. */
	private static final int SHARD = 13;
	private static final int MAX_SHARDS = 96;
	/** Edge length of the pieces the body breaks into, in blocks. */
	private static final float CELL = 0.24f;
	private static final int SOUL_TAIL = 18;
	private static final int FIREWORK_SPARKS = 84;
	private static final int RING_SPARKS = 24;

	public final ModeSetting mode = add(new ModeSetting("Mode", "Light Pillar", "Light Pillar", "Lightning", "Shatter", "Soul", "Blood Burst", "Firework"));
	public final ModeSetting trigger = add(new ModeSetting("Trigger", "Your kills", "Your kills", "All deaths"));
	public final NumberSetting size = add(new NumberSetting("Size", 1.0, 0.5, 2.0, 0.05).suffix("x"));
	public final NumberSetting duration = add(new NumberSetting("Duration", 1.0, 0.4, 3.0, 0.05).suffix("x"));
	public final BoolSetting themeColor = add(new BoolSetting("Theme color", true).visibleIf(() -> !mode.is("Blood Burst")));
	public final ColorSetting color = add(new ColorSetting("Color", 0xFF7FD4FF, false).visibleIf(() -> !themeColor.on() && !mode.is("Blood Burst")));
	public final ColorSetting bloodColor = add(new ColorSetting("Blood color", 0xFFA30A16, false).visibleIf(() -> mode.is("Blood Burst")));
	public final BoolSetting flash = add(new BoolSetting("Screen flash", true));
	public final BoolSetting sound = add(new BoolSetting("Sound", false));

	private final List<Kill> kills = new ArrayList<>();
	private final ModelCapture capture = new ModelCapture();
	private final Splatter blood = new Splatter();
	private final float[] basis = new float[9];
	private final double[] point = new double[3];
	private final Anim flashAmount = new Anim(0f, 5f);
	private int flashColor;
	private boolean flashSky;
	private long lastFrame = System.nanoTime();
	private int ringColor;
	private final IntUnaryOperator ringColorAt = i -> ringColor;

	public KillEffectsModule() {
		super("Kill Effects", "Light pillar, lightning, shatter, soul, blood or fireworks on kills", Category.COMBAT);
		setSubCategory("Hits");
	}

	@Override
	public String suffix() {
		return mode.get();
	}

	@Override
	protected void onDisable() {
		kills.clear();
		blood.clear();
		flashAmount.snap(0f);
	}

	public boolean allDeaths() {
		return trigger.is("All deaths");
	}

	/** Whether the entity's body is not drawn this frame because it shattered (EntityRenderManagerMixin). */
	public static boolean hides(Entity entity) {
		KillEffectsModule module = ModuleManager.get(KillEffectsModule.class);
		if (module == null || !module.isEnabled()) return false;
		List<Kill> list = module.kills;
		for (int i = 0; i < list.size(); i++) {
			Kill kill = list.get(i);
			if (kill.type == SHATTER && kill.entity == entity) return true;
		}
		return false;
	}

	/** The entity died: your kill (Effects), or any death nearby in All deaths mode. */
	public void spawn(LivingEntity entity) {
		// Your own death would play around the camera; armour stands do not die, they break.
		if (entity == mc.player || entity instanceof ArmorStandEntity) return;
		for (int i = 0; i < kills.size(); i++) {
			if (kills.get(i).entity == entity) return;
		}
		if (allDeaths() && mc.gameRenderer != null && entity.squaredDistanceTo(mc.gameRenderer.getCamera().getCameraPos()) > RANGE * RANGE) return;
		ThreadLocalRandom random = ThreadLocalRandom.current();
		int type = mode.index();
		float s = size.f();
		float h = entity.getHeight();
		double ground = Effects.groundBelow(entity.getX(), entity.getY() + 0.1, entity.getZ());
		if (Double.isNaN(ground)) ground = entity.getY();
		Kill kill = new Kill(type, entity, entity.getX(), entity.getY(), entity.getZ(), ground, entity.getWidth(), h, s,
			LIFE[type] * duration.f(), random.nextFloat() * 100f, System.nanoTime());
		switch (type) {
			case PILLAR -> kill.data = motes(random, s);
			case LIGHTNING -> {
				kill.data = bolt(random, s);
				kill.extra = sparks(random, s);
			}
			case FIREWORK -> {
				kill.data = fireworkSparks(random);
				int base = themeColor.on() ? Theme.accent() : color.argb();
				float[] hsv = ColorUtil.toHsv(base);
				kill.third = ColorUtil.fromHsv(hsv[0] + 0.42f, Math.max(0.55f, hsv[1]), 1f, 255);
			}
			case BLOOD -> {
				double cy = entity.getY() + h * 0.55;
				blood.spawn(entity.getX(), cy, entity.getZ(), ground, Math.round(60 * s), 4.2f * s, s, 0f, 0f);
				blood.pool(entity.getX(), ground, entity.getZ(), (0.35f + entity.getWidth() * 0.45f) * s);
			}
			default -> {
			}
		}
		if (kills.size() == MAX_EFFECTS) kills.remove(0);
		kills.add(kill);
		effectFlash(kill, type);
		if (sound.on() && mc.world != null) {
			mc.world.playSoundClient(entity.getX(), entity.getY(), entity.getZ(), sound(type), type == LIGHTNING ? SoundCategory.WEATHER : SoundCategory.PLAYERS,
				0.85f, 0.9f + random.nextFloat() * 0.2f, false);
		}
	}

	private static SoundEvent sound(int type) {
		return switch (type) {
			case PILLAR -> SoundEvents.BLOCK_BEACON_ACTIVATE;
			case LIGHTNING -> SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT;
			case SHATTER -> SoundEvents.BLOCK_AMETHYST_CLUSTER_BREAK;
			case SOUL -> SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE;
			case BLOOD -> SoundEvents.ENTITY_SLIME_SQUISH;
			default -> SoundEvents.ENTITY_FIREWORK_ROCKET_LARGE_BLAST;
		};
	}

	/** A soft flash at the screen edges in the effect's colour, weaker the farther away the death was. */
	private void effectFlash(Kill kill, int type) {
		if (!flash.on() || mc.gameRenderer == null) return;
		Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();
		double dx = kill.x - cam.x, dy = kill.y - cam.y, dz = kill.z - cam.z;
		float near = 1f - Anim.clamp01((float) Math.sqrt(dx * dx + dy * dy + dz * dz) / 32f);
		if (near <= 0f) return;
		float strength = type == PILLAR || type == LIGHTNING ? 0.55f : 0.38f;
		flashAmount.snap(Math.max(flashAmount.get(), strength * near));
		flashAmount.target(0f);
		flashColor = type == BLOOD ? bloodColor.argb() : themeColor.on() ? Theme.accent() : color.argb();
		flashSky = type == LIGHTNING;
	}

	/** Drawn with the screen overlays, below the rest of the HUD (HudRenderer). */
	public void renderFlash(DrawContext ctx) {
		float amount = flashAmount.get();
		if (amount < 0.004f) return;
		float w = ctx.getScaledWindowWidth();
		float h = ctx.getScaledWindowHeight();
		// Lightning lights the whole view for an instant.
		if (flashSky) Render2D.rect(ctx, 0f, 0f, w, h, ColorUtil.withAlpha(0xFFF4F7FF, Math.round(70 * amount)));
		int c = ColorUtil.withAlpha(flashColor, Math.round(230 * amount));
		float reach = Math.min(w, h) * (0.12f + 0.1f * amount);
		// Each glow surrounds a rectangle just outside one edge, so only its falloff reaches into the screen.
		Render2D.shadow(ctx, -reach, -reach, reach, h + reach * 2f, 0f, reach, c);
		Render2D.shadow(ctx, w, -reach, reach, h + reach * 2f, 0f, reach, c);
		Render2D.shadow(ctx, 0f, -reach, w, reach, 0f, reach, c);
		Render2D.shadow(ctx, 0f, h, w, reach, 0f, reach, c);
	}

	@Override
	public void render3D(float tickDelta) {
		long now = System.nanoTime();
		float dt = Math.min((now - lastFrame) / 1.0e9f, 0.05f);
		lastFrame = now;
		blood.render(dt, bloodColor.argb(), 6f * duration.f());
		if (kills.isEmpty()) return;
		int a1 = themeColor.on() ? Theme.accent() : color.argb();
		int a2 = themeColor.on() ? Theme.accent2() : ColorUtil.brighten(a1, 0.45f);
		for (int i = kills.size() - 1; i >= 0; i--) {
			Kill kill = kills.get(i);
			float age = (now - kill.born) / 1.0e9f;
			if (age >= kill.life) {
				kills.remove(i);
				continue;
			}
			switch (kill.type) {
				case PILLAR -> pillar(kill, age, a1, a2);
				case LIGHTNING -> lightning(kill, age, a1, a2);
				case SHATTER -> shatter(kill, age, dt, tickDelta, a1, a2);
				case SOUL -> soul(kill, age, a1, a2);
				case BLOOD -> mist(kill, age);
				default -> firework(kill, age, a1, a2);
			}
		}
	}

	private static float smooth(float t) {
		t = Anim.clamp01(t);
		return t * t * (3f - 2f * t);
	}

	/** 1 until the effect is past {@code from} of its life, then smoothly down to 0 at its end. */
	private static float fadeOut(float p, float from) {
		return 1f - smooth((p - from) / (1f - from));
	}

	// ------------------------------------------------------------------ light pillar

	/** Per mote: phase, radius, rise speed, start delay, size. */
	private static float[] motes(ThreadLocalRandom random, float s) {
		float[] d = new float[MOTES * 5];
		for (int i = 0; i < MOTES; i++) {
			d[i * 5] = random.nextFloat() * TAU;
			d[i * 5 + 1] = (0.15f + random.nextFloat() * 0.75f) * s;
			d[i * 5 + 2] = (1.1f + random.nextFloat() * 1.6f) * s;
			d[i * 5 + 3] = random.nextFloat() * 0.9f;
			d[i * 5 + 4] = (0.05f + random.nextFloat() * 0.06f) * s;
		}
		return d;
	}

	/**
	 * A beam strikes down from high in the sky in a tenth of a second, a shockwave runs over the ground,
	 * and the beam holds, thinning out, while motes of light spiral up around its foot.
	 */
	private void pillar(Kill k, float age, int a1, int a2) {
		WorldEffectRenderer.Mesh mesh = WorldEffectRenderer.glow();
		float s = k.size;
		float p = age / k.life;
		float strike = Anim.easeOutCubic(age / 0.12f);
		float hit = age < 0.12f ? 0f : (float) Math.exp(-(age - 0.12f) * 5f);
		float fade = fadeOut(p, 0.45f);
		double ground = k.ground;
		double bottom = ground + BEAM * (1f - strike);
		int core = ColorUtil.lerp(0xFFFFFFFF, a2, 0.2f);
		float glowWidth = 0.42f * s * (0.55f + 0.45f * fade) * (1f + 0.6f * hit);
		float coreWidth = 0.065f * s * (0.6f + 0.4f * fade) * (1f + hit);
		for (int i = 0; i < BEAM_STOPS.length - 1; i++) {
			double y0 = Math.max(bottom, ground + BEAM_STOPS[i]);
			double y1 = ground + BEAM_STOPS[i + 1];
			if (y1 <= y0) continue;
			float f0 = BEAM_ALPHA[i] * fade, f1 = BEAM_ALPHA[i + 1] * fade;
			Draw3D.line(mesh, k.x, y0, k.z, k.x, y1, k.z, glowWidth, ColorUtil.fade(a1, 0.7f * f0 + 0.3f * hit), ColorUtil.fade(a1, 0.7f * f1));
			Draw3D.line(mesh, k.x, y0, k.z, k.x, y1, k.z, coreWidth, ColorUtil.fade(core, f0), ColorUtil.fade(core, f1));
		}
		if (strike < 1f) mesh.orb(k.x, bottom, k.z, 0.8f * s, ColorUtil.fade(core, 0.9f));
		if (hit <= 0f) return;

		// A column of light around the foot of the beam, bright at the ground and gone at its top.
		double radius = (0.35f + k.width * 0.4f) * s;
		double top = ground + 2.4f * s;
		int foot = ColorUtil.fade(a1, 0.32f * fade + 0.25f * hit);
		int clear = ColorUtil.withAlpha(a1, 0);
		for (int i = 0; i < 20; i++) {
			double a0 = TAU * i / 20, b0 = TAU * (i + 1) / 20;
			double x0 = k.x + Math.cos(a0) * radius, z0 = k.z + Math.sin(a0) * radius;
			double x1 = k.x + Math.cos(b0) * radius, z1 = k.z + Math.sin(b0) * radius;
			mesh.vertex(x0, ground, z0, 3f, 0f, foot);
			mesh.vertex(x0, top, z0, 3f, 0f, clear);
			mesh.vertex(x1, top, z1, 3f, 0f, clear);
			mesh.vertex(x1, ground, z1, 3f, 0f, foot);
		}
		float pool = (1.3f + 0.9f * hit) * s;
		Draw3D.flatOrb(mesh, k.x, ground + 0.02, k.z, pool, 0f, 0f, pool, ColorUtil.fade(a1, 0.5f * fade + 0.4f * hit));
		mesh.orb(k.x, ground + 0.9, k.z, 2.6f * s, ColorUtil.fade(a2, 0.65f * hit * hit));

		// Two shockwaves, the second one later and slower.
		for (int r = 0; r < 2; r++) {
			float t = (age - 0.12f - r * 0.16f) / (0.7f + r * 0.35f);
			if (t <= 0f || t >= 1f) continue;
			ringColor = ColorUtil.fade(r == 0 ? a1 : a2, 0.95f * (1f - t));
			Draw3D.flatRing(mesh, k.x, ground + 0.04, k.z, (0.3f + (3.4f - r) * Anim.easeOutCubic(t)) * s, 0.13f * (1f - t * 0.7f), 56, ringColorAt);
		}

		// Motes spiral up around the beam, looping until the pillar fades.
		float[] d = k.data;
		float span = 5.5f * s;
		int light = ColorUtil.lerp(a1, 0xFFFFFFFF, 0.3f);
		for (int i = 0; i < MOTES; i++) {
			int o = i * 5;
			float t = age - 0.12f - d[o + 3];
			if (t <= 0f) continue;
			float rise = (t * d[o + 2]) % span;
			float along = rise / span;
			float alpha = fade * (float) Math.sin(Math.PI * along);
			if (alpha <= 0.01f) continue;
			double angle = d[o] + t * (i % 2 == 0 ? 1.7 : -1.7);
			double r = d[o + 1] * (1f - 0.4f * along);
			double x = k.x + Math.cos(angle) * r, y = ground + 0.1 + rise, z = k.z + Math.sin(angle) * r;
			mesh.orb(x, y, z, d[o + 4] * 2.4f, ColorUtil.fade(i % 3 == 0 ? a2 : a1, 0.55f * alpha));
			mesh.orb(x, y, z, d[o + 4] * 0.7f, ColorUtil.fade(light, 0.9f * alpha));
		}
	}

	// ------------------------------------------------------------------ lightning

	/** A bolt from high above to the feet, by midpoint displacement, forking on the way down. */
	private static float[] bolt(ThreadLocalRandom random, float s) {
		float[] out = new float[MAX_SEGMENTS * SEG];
		float height = 20f + 6f * s;
		float sx = (random.nextFloat() - 0.5f) * 6f * s;
		float sz = (random.nextFloat() - 0.5f) * 6f * s;
		int end = channel(out, 0, random, sx, height, sz, 0f, 0f, 0f, 1f, 0, 6);
		return Arrays.copyOf(out, end);
	}

	private static int channel(float[] out, int o, ThreadLocalRandom random, float x0, float y0, float z0, float x1, float y1, float z1,
							   float width, int order, int depth) {
		if (o + SEG > out.length) return o;
		if (depth == 0) {
			out[o] = x0;
			out[o + 1] = y0;
			out[o + 2] = z0;
			out[o + 3] = x1;
			out[o + 4] = y1;
			out[o + 5] = z1;
			out[o + 6] = width;
			out[o + 7] = order;
			return o + SEG;
		}
		float dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
		float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
		float jitter = length * 0.3f;
		float mx = (x0 + x1) * 0.5f + (random.nextFloat() - 0.5f) * jitter;
		float my = (y0 + y1) * 0.5f + (random.nextFloat() - 0.5f) * jitter * 0.3f;
		float mz = (z0 + z1) * 0.5f + (random.nextFloat() - 0.5f) * jitter;
		o = channel(out, o, random, x0, y0, z0, mx, my, mz, width, order, depth - 1);
		if (order < 2 && depth >= 2 && my > 1.5f && random.nextFloat() < (order == 0 ? 0.5f : 0.3f)) {
			// A fork: off to the side and down, shorter and thinner than the channel it leaves.
			float reach = length * (0.45f + random.nextFloat() * 0.45f);
			double angle = random.nextDouble() * Math.PI * 2;
			float bx = mx + (float) Math.cos(angle) * reach * 0.6f;
			float by = Math.max(0.3f, my - reach * (0.5f + random.nextFloat() * 0.4f));
			float bz = mz + (float) Math.sin(angle) * reach * 0.6f;
			o = channel(out, o, random, mx, my, mz, bx, by, bz, width * 0.55f, order + 1, depth - 1);
		}
		return channel(out, o, random, mx, my, mz, x1, y1, z1, width, order, depth - 1);
	}

	/** Per spark of the impact: direction (x, z), speed, upward speed. */
	private static float[] sparks(ThreadLocalRandom random, float s) {
		float[] d = new float[SPARKS * 4];
		for (int i = 0; i < SPARKS; i++) {
			double angle = TAU * (i + random.nextFloat() * 0.7f) / SPARKS;
			d[i * 4] = (float) Math.cos(angle);
			d[i * 4 + 1] = (float) Math.sin(angle);
			d[i * 4 + 2] = (3f + random.nextFloat() * 3f) * s;
			d[i * 4 + 3] = (1.5f + random.nextFloat() * 3.5f) * s;
		}
		return d;
	}

	/**
	 * The leader runs down in a few frames, then the bolt strobes three times like real lightning and
	 * leaves a fading afterglow of its channel; the sky flashes, a ring runs over the ground and sparks fly.
	 */
	private void lightning(Kill k, float age, int a1, int a2) {
		WorldEffectRenderer.Mesh mesh = WorldEffectRenderer.glow();
		float s = k.size;
		float leader = Anim.clamp01(age / 0.07f);
		float strobe = 0f;
		for (int i = 0; i < 3; i++) {
			float t0 = 0.07f + i * (0.1f + 0.02f * hash(k.seed + i));
			if (age >= t0) strobe = Math.max(strobe, (float) Math.exp(-(age - t0) * 14f) * (1f - i * 0.18f));
		}
		if (age < 0.07f) strobe = 0.35f;
		float after = 1f - Anim.clamp01((age - 0.3f) / (k.life - 0.3f));
		float[] seg = k.data;
		float top = seg.length > 1 ? seg[1] : 1f;
		int core = ColorUtil.lerp(0xFFFFFFFF, a2, 0.15f);
		int ember = ColorUtil.lerp(a1, a2, 0.5f);
		for (int o = 0; o < seg.length; o += SEG) {
			// Revealed from the top while the leader runs down.
			if (Math.max(seg[o + 1], seg[o + 4]) < top * (1f - leader)) continue;
			int order = (int) seg[o + 7];
			float w = seg[o + 6];
			float bright = order == 0 ? Math.max(strobe, 0.4f * after) : strobe * (order == 1 ? 0.75f : 0.5f) + (order == 1 ? 0.12f * after : 0f);
			if (bright <= 0.01f) continue;
			double x0 = k.x + seg[o], y0 = k.ground + seg[o + 1], z0 = k.z + seg[o + 2];
			double x1 = k.x + seg[o + 3], y1 = k.ground + seg[o + 4], z1 = k.z + seg[o + 5];
			int glow = ColorUtil.fade(strobe > 0.2f ? a1 : ember, 0.8f * bright);
			Draw3D.line(mesh, x0, y0, z0, x1, y1, z1, 0.4f * s * w * (0.7f + 0.3f * strobe), glow, glow);
			int white = ColorUtil.fade(core, Math.min(1f, bright * 1.2f));
			Draw3D.line(mesh, x0, y0, z0, x1, y1, z1, 0.06f * s * w, white, white);
		}
		if (leader < 1f) return;
		mesh.orb(k.x + seg[0] * 0.6, k.ground + top * 0.8, k.z + seg[2] * 0.6, 8f * s, ColorUtil.fade(a2, 0.22f * strobe));
		float pool = (1.6f + 1.2f * strobe) * s;
		Draw3D.flatOrb(mesh, k.x, k.ground + 0.02, k.z, pool, 0f, 0f, pool, ColorUtil.fade(a1, 0.6f * strobe + 0.25f * after));
		mesh.orb(k.x, k.ground + 0.2, k.z, 1.1f * s, ColorUtil.fade(core, 0.8f * strobe));
		float ring = (age - 0.07f) / 0.55f;
		if (ring < 1f) {
			ringColor = ColorUtil.fade(a1, 0.9f * (1f - ring));
			Draw3D.flatRing(mesh, k.x, k.ground + 0.04, k.z, (0.3f + 3.2f * Anim.easeOutCubic(ring)) * s, 0.1f * (1f - ring * 0.6f), 48, ringColorAt);
		}
		float[] d = k.extra;
		float t = age - 0.07f;
		if (t > 0.8f) return;
		float spark = 1f - t / 0.8f;
		float travel = (1f - (float) Math.exp(-3f * t)) / 3f;
		float before = Math.max(0f, t - 0.03f);
		float travelBefore = (1f - (float) Math.exp(-3f * before)) / 3f;
		for (int i = 0; i < SPARKS; i++) {
			int o = i * 4;
			double x = k.x + d[o] * d[o + 2] * travel, z = k.z + d[o + 1] * d[o + 2] * travel;
			double y = Math.max(k.ground + 0.05, k.ground + 0.15 + d[o + 3] * t - 7f * t * t);
			double px = k.x + d[o] * d[o + 2] * travelBefore, pz = k.z + d[o + 1] * d[o + 2] * travelBefore;
			double py = Math.max(k.ground + 0.05, k.ground + 0.15 + d[o + 3] * before - 7f * before * before);
			Draw3D.line(mesh, x, y, z, px, py, pz, 0.035f * s, ColorUtil.fade(core, spark), ColorUtil.fade(a1, 0f));
		}
	}

	// ------------------------------------------------------------------ shatter

	/**
	 * The body bursts into glowing cubes cut from its own model in its pose: they fly outwards, tumble,
	 * bounce on the ground and cool from white hot to the accent colour before they fade. The body
	 * itself is not drawn meanwhile (LivingEntityRendererMixin).
	 */
	private void shatter(Kill k, float age, float dt, float tickDelta, int a1, int a2) {
		if (k.data == null) k.data = shards(k, tickDelta);
		WorldEffectRenderer.Mesh mesh = WorldEffectRenderer.glow();
		float s = k.size;
		float p = age / k.life;
		float fade = fadeOut(p, 0.6f);
		float hot = (float) Math.exp(-age * 3.5f);
		float floor = (float) (k.ground - k.y);
		float[] d = k.data;
		for (int o = 0; o < d.length; o += SHARD) {
			float half = d[o + 11] * (0.35f + 0.65f * fade);
			d[o + 4] -= 16f * dt;
			float drag = 1f - 0.6f * dt;
			d[o + 3] *= drag;
			d[o + 5] *= drag;
			d[o] += d[o + 3] * dt;
			d[o + 1] += d[o + 4] * dt;
			d[o + 2] += d[o + 5] * dt;
			if (d[o + 1] < floor + half) {
				d[o + 1] = floor + half;
				if (d[o + 4] < 0f) {
					// Bounce: loses most of its speed and spin on the ground.
					d[o + 4] = d[o + 4] < -1.2f ? -d[o + 4] * 0.35f : 0f;
					d[o + 3] *= 0.6f;
					d[o + 5] *= 0.6f;
					d[o + 10] *= 0.55f;
				}
			}
			d[o + 9] += d[o + 10] * dt;
			double x = k.x + d[o], y = k.y + d[o + 1], z = k.z + d[o + 2];
			float near = Draw3D.cameraFade(x, y, z, 0.5f, 1.3f);
			if (near <= 0.01f) continue;
			Draw3D.rotation(basis, d[o + 6], d[o + 7], d[o + 8], d[o + 9]);
			int base = ColorUtil.lerp(a1, a2, d[o + 12]);
			int c = ColorUtil.fade(ColorUtil.lerp(base, 0xFFFFFFFF, 0.7f * hot), 0.95f * fade * near);
			Draw3D.cube(mesh, x, y, z, half, basis, c);
			if (hot > 0.12f) mesh.orb(x, y, z, half * 2.6f, ColorUtil.fade(0xFFFFFFFF, 0.55f * hot * near));
		}
		float burst = 1f - age / 0.3f;
		if (burst > 0f) mesh.orb(k.x, k.y + k.height * 0.5, k.z, (1.2f + k.width) * s, ColorUtil.fade(a2, 0.8f * burst));
		float ring = age / 0.6f;
		if (ring < 1f) {
			ringColor = ColorUtil.fade(a1, 0.85f * (1f - ring));
			Draw3D.flatRing(mesh, k.x, k.ground + 0.04, k.z, (0.3f + 2.6f * Anim.easeOutCubic(ring)) * s, 0.1f * (1f - ring * 0.6f), 48, ringColorAt);
		}
	}

	/** Cuts the body's model, captured in its pose, into cubes; a box of cubes for entities without a model. */
	private float[] shards(Kill k, float tickDelta) {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		float s = k.size;
		capture.clear();
		captureBody(k.entity, tickDelta);
		float[] q = capture.quads();
		int quads = capture.count();
		int cells = 0;
		for (int i = 0; i < quads; i++) cells += cellsU(q, i * ModelCapture.STRIDE) * cellsW(q, i * ModelCapture.STRIDE);
		if (cells == 0) return boxShards(k, random);
		int step = Math.max(1, (cells + MAX_SHARDS - 1) / MAX_SHARDS);
		float[] d = new float[((cells + step - 1) / step) * SHARD];
		int n = 0;
		int index = 0;
		float cy = k.height * 0.5f;
		for (int i = 0; i < quads; i++) {
			int o = i * ModelCapture.STRIDE;
			int su = cellsU(q, o), sw = cellsW(q, o);
			float ux = q[o + 3] - q[o], uy = q[o + 4] - q[o + 1], uz = q[o + 5] - q[o + 2];
			float wx = q[o + 9] - q[o], wy = q[o + 10] - q[o + 1], wz = q[o + 11] - q[o + 2];
			float cell = Math.min(length(ux, uy, uz) / su, length(wx, wy, wz) / sw);
			for (int a = 0; a < su; a++) {
				for (int b = 0; b < sw; b++) {
					if (index++ % step != 0 || n + SHARD > d.length) continue;
					float fu = (a + 0.5f) / su, fw = (b + 0.5f) / sw;
					float half = cell * 0.42f;
					// The cube sits just inside the face it was cut from.
					float x = q[o] + ux * fu + wx * fw - q[o + 12] * half;
					float y = q[o + 1] + uy * fu + wy * fw - q[o + 13] * half;
					float z = q[o + 2] + uz * fu + wz * fw - q[o + 14] * half;
					n = shard(d, n, random, x, y, z, half, cy, k.height, s);
				}
			}
		}
		return n == d.length ? d : Arrays.copyOf(d, n);
	}

	private static int cellsU(float[] q, int o) {
		return Math.max(1, Math.round(length(q[o + 3] - q[o], q[o + 4] - q[o + 1], q[o + 5] - q[o + 2]) / CELL));
	}

	private static int cellsW(float[] q, int o) {
		return Math.max(1, Math.round(length(q[o + 9] - q[o], q[o + 10] - q[o + 1], q[o + 11] - q[o + 2]) / CELL));
	}

	private static float length(float x, float y, float z) {
		return (float) Math.sqrt(x * x + y * y + z * z);
	}

	/** Shards filling the bounding box, for bodies whose model could not be captured. */
	private static float[] boxShards(Kill k, ThreadLocalRandom random) {
		int across = Math.max(2, Math.round(k.width / CELL));
		int up = Math.max(2, Math.round(k.height / CELL));
		int total = Math.min(MAX_SHARDS, across * across * up);
		float[] d = new float[total * SHARD];
		int n = 0;
		for (int i = 0; i < total; i++) {
			float x = (random.nextFloat() - 0.5f) * k.width;
			float y = random.nextFloat() * k.height;
			float z = (random.nextFloat() - 0.5f) * k.width;
			n = shard(d, n, random, x, y, z, CELL * 0.42f, k.height * 0.5f, k.height, k.size);
		}
		return d;
	}

	/** One shard at (x, y, z) relative to the feet, flung away from the body centre at height cy. */
	private static int shard(float[] d, int n, ThreadLocalRandom random, float x, float y, float z, float half, float cy, float height, float s) {
		float dx = x + (random.nextFloat() - 0.5f) * 0.3f, dy = y - cy + (random.nextFloat() - 0.5f) * 0.3f, dz = z + (random.nextFloat() - 0.5f) * 0.3f;
		float l = Math.max(1e-3f, length(dx, dy, dz));
		float speed = (1.6f + random.nextFloat() * 2.8f) * s;
		float ax = random.nextFloat() - 0.5f, ay = random.nextFloat() - 0.5f, az = random.nextFloat() - 0.5f;
		float al = Math.max(1e-3f, length(ax, ay, az));
		d[n] = x;
		d[n + 1] = y;
		d[n + 2] = z;
		d[n + 3] = dx / l * speed;
		d[n + 4] = dy / l * speed + (1.6f + random.nextFloat() * 2.2f) * s;
		d[n + 5] = dz / l * speed;
		d[n + 6] = ax / al;
		d[n + 7] = ay / al;
		d[n + 8] = az / al;
		d[n + 9] = random.nextFloat() * TAU;
		d[n + 10] = 4f + random.nextFloat() * 9f;
		d[n + 11] = half;
		d[n + 12] = Anim.clamp01(y / Math.max(0.1f, height));
		return n + SHARD;
	}

	/** Renders the entity's model in vanilla's pose and transforms into the capture, relative to its feet. */
	@SuppressWarnings({"unchecked", "rawtypes"})
	private void captureBody(LivingEntity entity, float tickDelta) {
		EntityRenderer<?, ?> base = mc.getEntityRenderDispatcher().getRenderer(entity);
		if (!(base instanceof LivingEntityRenderer renderer)) return;
		try {
			LivingEntityRenderState state = (LivingEntityRenderState) renderer.createRenderState();
			renderer.updateRenderState(entity, state, tickDelta);
			if (state instanceof PlayerEntityRenderState player) {
				// The outer skin layers are slightly larger copies of the body; they would only double the pieces.
				player.hatVisible = false;
				player.jacketVisible = false;
				player.leftSleeveVisible = false;
				player.rightSleeveVisible = false;
				player.leftPantsLegVisible = false;
				player.rightPantsLegVisible = false;
			}
			// What LivingEntityRenderer.render does before it draws the model.
			MatrixStack matrices = new MatrixStack();
			Vec3d offset = renderer.getPositionOffset(state);
			matrices.translate((float) offset.x, (float) offset.y, (float) offset.z);
			float scale = state.baseScale;
			matrices.scale(scale, scale, scale);
			LivingEntityRendererInvoker transforms = (LivingEntityRendererInvoker) renderer;
			transforms.v1zir$setupTransforms(state, matrices, state.bodyYaw, scale);
			matrices.scale(-1f, -1f, 1f);
			transforms.v1zir$scale(state, matrices);
			matrices.translate(0f, -1.501f, 0f);
			EntityModel model = renderer.getModel();
			model.setAngles(state);
			model.render(matrices, capture, FULL_BRIGHT, OverlayTexture.DEFAULT_UV);
		} catch (RuntimeException e) {
			// Some modded renderers need more than a fresh state: fall back to a box of shards.
			capture.clear();
		}
	}

	// ------------------------------------------------------------------ soul

	/** Where the soul is t seconds after the death: rising with a slow start and end, weaving gently from side to side. */
	private void soulPath(Kill k, float t, double[] out) {
		float s = k.size;
		float u = Anim.clamp01(t / (k.life * 0.9f));
		float sway = 0.42f * s * Math.min(1f, t * 1.4f);
		out[0] = k.x + Math.sin(t * 2.1f + k.seed) * sway;
		out[1] = k.y + k.height * 0.6f + 3.6f * s * Anim.easeInOutSine(u) + 0.2f * t;
		out[2] = k.z + Math.cos(t * 1.6f + k.seed * 1.3f) * sway;
	}

	/** A ghost leaves the body and rises: a bright head over a softer body, a tapering wisp trailing below it and sparkles circling it. */
	private void soul(Kill k, float age, int a1, int a2) {
		WorldEffectRenderer.Mesh mesh = WorldEffectRenderer.glow();
		float s = k.size;
		float p = age / k.life;
		float alpha = Anim.clamp01(age / 0.25f) * fadeOut(p, 0.55f);
		float release = 1f - age / 0.6f;
		if (release > 0f) mesh.orb(k.x, k.y + k.height * 0.5, k.z, (0.7f + k.width) * s, ColorUtil.fade(a1, 0.5f * release));
		if (alpha <= 0.01f) return;
		double[] at = point;
		// The wisp: the path the soul just took, thinning and fading away from it.
		soulPath(k, age, at);
		double px = at[0], py = at[1] - 0.3f * s, pz = at[2];
		for (int j = 1; j <= SOUL_TAIL; j++) {
			soulPath(k, Math.max(0f, age - j * 0.045f), at);
			double x = at[0], y = at[1] - 0.3f * s, z = at[2];
			float f0 = 1f - (j - 1) / (float) SOUL_TAIL, f1 = 1f - j / (float) SOUL_TAIL;
			Draw3D.line(mesh, px, py, pz, x, y, z, 0.3f * s * f0, ColorUtil.fade(ColorUtil.lerp(a1, a2, 1f - f0), 0.75f * alpha * f0 * f0),
				ColorUtil.fade(ColorUtil.lerp(a1, a2, 1f - f1), 0.75f * alpha * f1 * f1));
			px = x;
			py = y;
			pz = z;
		}
		soulPath(k, age, at);
		double x = at[0], y = at[1], z = at[2];
		mesh.orb(x, y - 0.3f * s, z, 0.55f * s, ColorUtil.fade(a1, 0.5f * alpha));
		mesh.orb(x, y, z, 0.38f * s, ColorUtil.fade(a2, 0.85f * alpha));
		mesh.orb(x, y, z, 0.13f * s, ColorUtil.fade(0xFFFFFFFF, alpha));
		for (int m = 0; m < 5; m++) {
			double angle = k.seed + age * 2.6f + m * TAU / 5;
			double r = (0.45f + 0.12f * (float) Math.sin(age * 3f + m)) * s;
			double sy = y - 0.15f * s + Math.sin(age * 2f + m * 1.7f) * 0.25f * s;
			float twinkle = 0.55f + 0.45f * (float) Math.sin(age * 9f + m * 2.3f);
			double sx = x + Math.cos(angle) * r, sz = z + Math.sin(angle) * r;
			mesh.orb(sx, sy, sz, 0.12f * s, ColorUtil.fade(a2, 0.6f * alpha * twinkle));
			mesh.orb(sx, sy, sz, 0.04f * s, ColorUtil.fade(0xFFFFFFFF, 0.9f * alpha * twinkle));
		}
	}

	// ------------------------------------------------------------------ blood burst

	/** A short red mist where the body burst; the droplets and stains are the Splatter's. */
	private void mist(Kill k, float age) {
		float u = age / k.life;
		int c = bloodColor.argb();
		WorldEffectRenderer.translucent().orb(k.x, k.y + k.height * 0.55, k.z, (0.8f + 0.9f * u) * k.size, ColorUtil.fade(c, 0.5f * (1f - u) * (1f - u)));
	}

	// ------------------------------------------------------------------ firework

	/** Per spark: direction (x, y, z), speed factor, colour group (3: ring). Spherical shell plus a tilted ring. */
	private static float[] fireworkSparks(ThreadLocalRandom random) {
		float[] d = new float[(FIREWORK_SPARKS + RING_SPARKS) * 5];
		float golden = (float) (Math.PI * (3 - Math.sqrt(5)));
		for (int i = 0; i < FIREWORK_SPARKS; i++) {
			// Fibonacci sphere: evenly spread directions, slightly jittered.
			float y = 1f - 2f * (i + 0.5f) / FIREWORK_SPARKS;
			float r = (float) Math.sqrt(1f - y * y);
			float a = i * golden + random.nextFloat() * 0.3f;
			int o = i * 5;
			d[o] = (float) Math.cos(a) * r;
			d[o + 1] = y;
			d[o + 2] = (float) Math.sin(a) * r;
			d[o + 3] = 0.85f + random.nextFloat() * 0.3f;
			d[o + 4] = random.nextInt(3);
		}
		float tilt = (random.nextFloat() - 0.5f) * 1.4f;
		for (int i = 0; i < RING_SPARKS; i++) {
			float a = TAU * i / RING_SPARKS;
			int o = (FIREWORK_SPARKS + i) * 5;
			d[o] = (float) Math.cos(a);
			d[o + 1] = (float) (Math.sin(a) * Math.sin(tilt));
			d[o + 2] = (float) (Math.sin(a) * Math.cos(tilt));
			d[o + 3] = 1.15f;
			d[o + 4] = 3;
		}
		return d;
	}

	/** A rocket climbs from the body and bursts into a shell of coloured sparks with a ring around it; the sparks streak, sag and crackle out. */
	private void firework(Kill k, float age, int a1, int a2) {
		WorldEffectRenderer.Mesh mesh = WorldEffectRenderer.glow();
		float s = k.size;
		float climb = 0.42f;
		double baseY = k.y + k.height * 0.5;
		float apex = 3.4f * s;
		if (age < climb) {
			float u = age / climb;
			double y = baseY + apex * Anim.easeOutCubic(u);
			for (int j = 1; j <= 8; j++) {
				float uj = Math.max(0f, u - j * 0.05f);
				double ty = baseY + apex * Anim.easeOutCubic(uj);
				mesh.orb(k.x, ty, k.z, 0.1f * s * (1f - j / 9f), ColorUtil.fade(a1, 0.7f * (1f - j / 9f)));
			}
			mesh.orb(k.x, y, k.z, 0.22f * s, ColorUtil.fade(0xFFFFFFFF, 0.95f));
			return;
		}
		float t = age - climb;
		float life = k.life - climb;
		double cy = baseY + apex;
		float flashLeft = 1f - t / 0.18f;
		if (flashLeft > 0f) mesh.orb(k.x, cy, k.z, 2.6f * s, ColorUtil.fade(0xFFFFFFFF, 0.7f * flashLeft));
		float fade = fadeOut(t / life, 0.5f);
		float drag = 2.4f;
		float travel = (1f - (float) Math.exp(-drag * t)) / drag;
		float fall = 6f / drag * (t - travel);
		float back = Math.max(0f, t - 0.1f);
		float travelBack = (1f - (float) Math.exp(-drag * back)) / drag;
		float fallBack = 6f / drag * (back - travelBack);
		float[] d = k.data;
		for (int o = 0; o < d.length; o += 5) {
			int group = (int) d[o + 4];
			float v = 6.5f * s * d[o + 3];
			float alpha = fade;
			// Crackles out at the end: each spark flickers on its own.
			if (t > life * 0.45f) alpha *= 0.35f + 0.65f * hash(k.seed + o + (float) Math.floor(t * 18f));
			if (alpha <= 0.01f) continue;
			double x = k.x + d[o] * v * travel, y = cy + d[o + 1] * v * travel - fall, z = k.z + d[o + 2] * v * travel;
			double bx = k.x + d[o] * v * travelBack, by = cy + d[o + 1] * v * travelBack - fallBack, bz = k.z + d[o + 2] * v * travelBack;
			int c = group == 0 ? a1 : group == 1 ? a2 : group == 2 ? k.third : ColorUtil.lerp(a2, 0xFFFFFFFF, 0.5f);
			Draw3D.line(mesh, x, y, z, bx, by, bz, 0.045f * s, ColorUtil.fade(c, 0.9f * alpha), ColorUtil.fade(c, 0f));
			mesh.orb(x, y, z, 0.1f * s, ColorUtil.fade(c, 0.7f * alpha));
			mesh.orb(x, y, z, 0.035f * s, ColorUtil.fade(0xFFFFFFFF, 0.9f * alpha));
		}
	}

	/** Pseudo random 0..1 from a float, for strobes and crackles. */
	private static float hash(float v) {
		float n = (float) (Math.sin(v * 12.9898) * 43758.5453);
		return n - (float) Math.floor(n);
	}

	private static final class Kill {
		final int type;
		final LivingEntity entity;
		/** Feet of the entity when it died, and the ground below them. */
		final double x, y, z, ground;
		final float width, height, size, life, seed;
		final long born;
		/** Per mode layout (motes, bolt segments, shards, sparks); shards are filled on the first frame. */
		float[] data;
		/** Lightning: impact sparks. */
		float[] extra;
		/** Firework: the third colour of the shell. */
		int third;

		Kill(int type, LivingEntity entity, double x, double y, double z, double ground, float width, float height, float size,
			 float life, float seed, long born) {
			this.type = type;
			this.entity = entity;
			this.x = x;
			this.y = y;
			this.z = z;
			this.ground = ground;
			this.width = width;
			this.height = height;
			this.size = size;
			this.life = life;
			this.seed = seed;
			this.born = born;
		}
	}
}
