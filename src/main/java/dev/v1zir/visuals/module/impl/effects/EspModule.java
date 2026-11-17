package dev.v1zir.visuals.module.impl.effects;

import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.module.setting.ColorSetting;
import dev.v1zir.visuals.module.setting.ModeSetting;
import dev.v1zir.visuals.module.setting.NumberSetting;
import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.font.SdfFont;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.render.world.Draw3D;
import dev.v1zir.visuals.render.world.WorldEffect;
import dev.v1zir.visuals.render.world.WorldEffectRenderer;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Boxes around players (and optionally mobs) that show through walls. Box 2D and Corners are drawn on
 * the screen around the projected bounding box (rounded box with a soft glow, or corner brackets);
 * Box 3D outlines the bounding box in the world with glowing edges. A health bar runs along the side.
 * Targets are collected once per tick; the boxes follow the interpolated positions every frame.
 */
public final class EspModule extends Module implements WorldEffect {
	private static final int BOX = 0;
	private static final int CORNERS = 1;
	private static final int LOW = 0xFFFF4D5E;
	private static final int MID = 0xFFFFD34D;
	private static final int HIGH = 0xFF5CFF8A;
	/** Room around the bounding box, in blocks. */
	private static final float PAD = 0.08f;

	public final ModeSetting mode = add(new ModeSetting("Mode", "Box 2D", "Box 2D", "Corners", "Box 3D"));
	public final ModeSetting colorMode = add(new ModeSetting("Color", "Theme", "Theme", "Health", "Custom"));
	public final ColorSetting color = add(new ColorSetting("Custom color", 0xFF5CE1FF, false).visibleIf(() -> colorMode.is("Custom")));
	public final BoolSetting healthBar = add(new BoolSetting("Health bar", true));
	/** The name and the distance above the box (Box 2D and Corners). */
	public final BoolSetting names = add(new BoolSetting("Names", true).visibleIf(() -> !mode.is("Box 3D")));
	/** Lines of Box 2D and Corners, pixels. */
	public final NumberSetting thickness = add(new NumberSetting("Thickness", 2.0, 1.0, 4.0, 0.25).suffix(" px").visibleIf(() -> !mode.is("Box 3D")));
	/** A soft glow along the lines in their colour. */
	public final BoolSetting glow = add(new BoolSetting("Glow", true));
	public final BoolSetting fill = add(new BoolSetting("Fill", true));
	public final BoolSetting throughWalls = add(new BoolSetting("Through walls", true));
	public final BoolSetting mobs = add(new BoolSetting("Mobs", false));
	/** As far as the server sends entities (many servers stop at 48 to 128 blocks); the boxes do not need vanilla to draw them. */
	public final NumberSetting range = add(new NumberSetting("Range", 256, 16, 512, 8).suffix(" m"));

	private final List<Target> targets = new ArrayList<>();
	private final Int2ObjectOpenHashMap<Target> byId = new Int2ObjectOpenHashMap<>();
	private final Matrix4f viewProjection = new Matrix4f();
	private final Vector4f corner = new Vector4f();
	private final Render2D.TextStyle label = new Render2D.TextStyle().shadow();
	/** Screen rectangle of the last projected box, in GUI pixels. */
	private float minX, minY, maxX, maxY;
	/** Entity ids start over in a new world. */
	private ClientWorld world;

	public EspModule() {
		super("ESP", "Boxes and health bars around players", Category.COMBAT);
		setSubCategory("Target");
	}

	@Override
	public String suffix() {
		return mode.get();
	}

	@Override
	protected void onDisable() {
		targets.clear();
		byId.clear();
	}

	@Override
	public void onTick() {
		if (mc.world != world) {
			world = mc.world;
			onDisable();
		}
		for (int i = 0; i < targets.size(); i++) targets.get(i).seen = false;
		if (mc.world != null && mc.player != null) {
			double max = range.get() * range.get();
			// Boxes in the world are depth tested; only boxes on the screen need a ray to hide them behind walls.
			boolean walls = throughWalls.on() || mode.is("Box 3D");
			for (Entity entity : mc.world.getEntities()) {
				if (!(entity instanceof LivingEntity living) || entity == mc.player || !living.isAlive() || entity.isInvisibleTo(mc.player)) continue;
				if (entity instanceof PlayerEntity player ? player.isSpectator() : !mobs.on() || entity instanceof ArmorStandEntity) continue;
				if (entity.squaredDistanceTo(mc.player) > max) continue;
				Target target = byId.get(entity.getId());
				if (target == null) {
					target = new Target();
					target.health.snap(health(living));
					byId.put(entity.getId(), target);
					targets.add(target);
				}
				target.entity = living;
				target.seen = true;
				target.health.target(health(living));
				// A ray per target and tick, only when boxes must not show through walls.
				target.visible = walls || mc.player.canSee(entity);
			}
		}
		for (int i = targets.size() - 1; i >= 0; i--) {
			Target target = targets.get(i);
			if (!target.seen) {
				byId.remove(target.entity.getId());
				targets.remove(i);
			}
		}
	}

	private static float health(LivingEntity entity) {
		return Anim.clamp01(entity.getHealth() / Math.max(1f, entity.getMaxHealth()));
	}

	private int boxColor(float health, boolean top) {
		return switch (colorMode.get()) {
			case "Health" -> healthColor(health);
			case "Custom" -> top ? ColorUtil.brighten(color.argb(), 0.25f) : color.argb();
			default -> top ? Theme.accent() : Theme.accent2();
		};
	}

	private static int healthColor(float health) {
		return health < 0.5f ? ColorUtil.lerp(LOW, MID, health * 2f) : ColorUtil.lerp(MID, HIGH, health * 2f - 1f);
	}

	// ------------------------------------------------------------------ 3D

	@Override
	public void render3D(float tickDelta) {
		if (!mode.is("Box 3D") || targets.isEmpty()) return;
		boolean walls = throughWalls.on();
		WorldEffectRenderer.Mesh glow = walls ? WorldEffectRenderer.glowThrough() : WorldEffectRenderer.glow();
		WorldEffectRenderer.Mesh body = walls ? WorldEffectRenderer.translucentThrough() : WorldEffectRenderer.translucent();
		Vector3f right = WorldEffectRenderer.RIGHT;
		for (int i = 0; i < targets.size(); i++) {
			Target target = targets.get(i);
			if (!target.visible) continue;
			LivingEntity e = target.entity;
			double x = MathHelper.lerp(tickDelta, e.lastRenderX, e.getX());
			double y = MathHelper.lerp(tickDelta, e.lastRenderY, e.getY());
			double z = MathHelper.lerp(tickDelta, e.lastRenderZ, e.getZ());
			double hw = e.getWidth() * 0.5 + PAD;
			double h = e.getHeight() + PAD;
			float health = target.health.get();
			int bottom = boxColor(health, false), top = boxColor(health, true);
			Draw3D.roundedBox(glow, x - hw, y, z - hw, x + hw, y + h, z + hw, bottom, top);
			if (fill.on()) Draw3D.solidBox(body, x - hw, y, z - hw, x + hw, y + h, z + hw, ColorUtil.fade(bottom, 0.16f), ColorUtil.fade(top, 0.05f));
			if (!healthBar.on()) continue;
			// A bar standing beside the box, on its left as seen from the camera.
			double side = hw * 1.42 + 0.12;
			double bx = x - right.x * side, bz = z - right.z * side;
			Draw3D.line(body, bx, y, bz, bx, y + h, bz, 0.05f, 0x99000000, 0x99000000);
			int c = healthColor(health);
			Draw3D.line(glow, bx, y, bz, bx, y + h * health, bz, 0.04f, c, c);
		}
	}

	// ------------------------------------------------------------------ 2D

	/** Box 2D and Corners, drawn with the screen overlays under the HUD (HudRenderer). */
	public void render2D(DrawContext ctx, float tickDelta) {
		if (mode.is("Box 3D") || targets.isEmpty()) return;
		viewProjection.set(WorldEffectRenderer.projection()).mul(WorldEffectRenderer.view());
		float sw = ctx.getScaledWindowWidth();
		float sh = ctx.getScaledWindowHeight();
		boolean corners = mode.index() == CORNERS;
		float t = thickness.f();
		SdfFont font = Fonts.semibold();
		for (int i = 0; i < targets.size(); i++) {
			Target target = targets.get(i);
			if (!target.visible || !project(target.entity, tickDelta, sw, sh)) continue;
			float x = minX, y = minY, w = maxX - minX, h = maxY - minY;
			if (w < 2f || h < 2f || maxX < 0f || maxY < 0f || minX > sw || minY > sh) continue;
			float health = target.health.get();
			int top = boxColor(health, true), bottom = boxColor(health, false);
			if (corners) corners(ctx, x, y, w, h, t, top, bottom);
			else box(ctx, x, y, w, h, t, top, bottom);
			if (healthBar.on()) bar(ctx, x - 4f - t * 1.5f, y, h, t * 1.25f, health);
			if (names.on()) name(ctx, font, target, x + w * 0.5f, y - 2f - t);
		}
	}

	/** Screen rectangle (GUI pixels) around the entity's interpolated bounding box; false when part of it is behind the camera. */
	private boolean project(LivingEntity e, float tickDelta, float sw, float sh) {
		double x = MathHelper.lerp(tickDelta, e.lastRenderX, e.getX()) - WorldEffectRenderer.camX;
		double y = MathHelper.lerp(tickDelta, e.lastRenderY, e.getY()) - WorldEffectRenderer.camY;
		double z = MathHelper.lerp(tickDelta, e.lastRenderZ, e.getZ()) - WorldEffectRenderer.camZ;
		float hw = e.getWidth() * 0.5f + PAD;
		float h = e.getHeight() + PAD;
		minX = minY = Float.MAX_VALUE;
		maxX = maxY = -Float.MAX_VALUE;
		for (int i = 0; i < 8; i++) {
			corner.set((float) x + ((i & 1) == 0 ? -hw : hw), (float) y + ((i & 2) == 0 ? 0f : h), (float) z + ((i & 4) == 0 ? -hw : hw), 1f);
			viewProjection.transform(corner);
			if (corner.w < 0.05f) return false;
			float sx = (corner.x / corner.w * 0.5f + 0.5f) * sw;
			float sy = (0.5f - corner.y / corner.w * 0.5f) * sh;
			minX = Math.min(minX, sx);
			maxX = Math.max(maxX, sx);
			minY = Math.min(minY, sy);
			maxY = Math.max(maxY, sy);
		}
		return true;
	}

	/** A rounded box: a soft fill, a glow along its line, a dark contrast line and the coloured line on top. */
	private void box(DrawContext ctx, float x, float y, float w, float h, float t, int top, int bottom) {
		float radius = Math.min(4f, Math.min(w, h) * 0.12f);
		if (fill.on()) Render2D.gradientV(ctx, x, y, w, h, radius, ColorUtil.fade(top, 0.16f), ColorUtil.fade(bottom, 0.05f));
		if (glow.on()) {
			// Two wide faint bands along the line read as a soft glow.
			Render2D.outline(ctx, x - t * 2.5f, y - t * 2.5f, w + t * 5f, h + t * 5f, radius + t * 2.5f, t * 5f,
				ColorUtil.fade(top, 0.1f), ColorUtil.fade(top, 0.1f), ColorUtil.fade(bottom, 0.1f), ColorUtil.fade(bottom, 0.1f));
			Render2D.outline(ctx, x - t * 1.25f, y - t * 1.25f, w + t * 2.5f, h + t * 2.5f, radius + t * 1.25f, t * 2.5f,
				ColorUtil.fade(top, 0.18f), ColorUtil.fade(top, 0.18f), ColorUtil.fade(bottom, 0.18f), ColorUtil.fade(bottom, 0.18f));
		}
		Render2D.outline(ctx, x - 0.75f, y - 0.75f, w + 1.5f, h + 1.5f, radius + 0.75f, t + 1.5f, 0x73000000);
		Render2D.outline(ctx, x, y, w, h, radius, t, top, top, bottom, bottom);
	}

	/** Corner brackets a quarter of the box long, each over a dark contrast underlay (and a glow under that). */
	private void corners(DrawContext ctx, float x, float y, float w, float h, float t, int top, int bottom) {
		float lw = Math.max(4f, w * 0.28f);
		float lh = Math.max(4f, h * 0.22f);
		if (glow.on()) {
			float g = t * 2f;
			int ct = ColorUtil.fade(top, 0.16f);
			int cb = ColorUtil.fade(bottom, 0.16f);
			Render2D.round(ctx, x - g, y - g, lw + g * 2f, t + g * 2f, t + g, ct);
			Render2D.round(ctx, x - g, y - g, t + g * 2f, lh + g * 2f, t + g, ct);
			Render2D.round(ctx, x + w - lw - g, y - g, lw + g * 2f, t + g * 2f, t + g, ct);
			Render2D.round(ctx, x + w - t - g, y - g, t + g * 2f, lh + g * 2f, t + g, ct);
			Render2D.round(ctx, x - g, y + h - t - g, lw + g * 2f, t + g * 2f, t + g, cb);
			Render2D.round(ctx, x - g, y + h - lh - g, t + g * 2f, lh + g * 2f, t + g, cb);
			Render2D.round(ctx, x + w - lw - g, y + h - t - g, lw + g * 2f, t + g * 2f, t + g, cb);
			Render2D.round(ctx, x + w - t - g, y + h - lh - g, t + g * 2f, lh + g * 2f, t + g, cb);
		}
		for (int pass = 0; pass < 2; pass++) {
			// The dark pass is one pixel larger all around.
			float g = pass == 0 ? 0.75f : 0f;
			int ct = pass == 0 ? 0x73000000 : top;
			int cb = pass == 0 ? 0x73000000 : bottom;
			float tt = t + g * 2f;
			Render2D.round(ctx, x - g, y - g, lw + g * 2f, tt, tt * 0.5f, ct);
			Render2D.round(ctx, x - g, y - g, tt, lh + g * 2f, tt * 0.5f, ct);
			Render2D.round(ctx, x + w - lw - g, y - g, lw + g * 2f, tt, tt * 0.5f, ct);
			Render2D.round(ctx, x + w - t - g, y - g, tt, lh + g * 2f, tt * 0.5f, ct);
			Render2D.round(ctx, x - g, y + h - t - g, lw + g * 2f, tt, tt * 0.5f, cb);
			Render2D.round(ctx, x - g, y + h - lh - g, tt, lh + g * 2f, tt * 0.5f, cb);
			Render2D.round(ctx, x + w - lw - g, y + h - t - g, lw + g * 2f, tt, tt * 0.5f, cb);
			Render2D.round(ctx, x + w - t - g, y + h - lh - g, tt, lh + g * 2f, tt * 0.5f, cb);
		}
	}

	/** The name, and how far away, centred above the box over a small dark pill. */
	private void name(DrawContext ctx, SdfFont font, Target target, float cx, float bottom) {
		LivingEntity e = target.entity;
		int meters = Math.round((float) Math.sqrt(e.squaredDistanceTo(mc.player)));
		if (target.label == null || meters != target.meters) {
			target.meters = meters;
			target.label = e.getName().getString() + "  " + meters + "m";
		}
		float size = 7f;
		float tw = font.width(target.label, size);
		float ph = size + 3f;
		Render2D.round(ctx, cx - tw * 0.5f - 3f, bottom - ph, tw + 6f, ph, ph * 0.5f, 0x8C0B0C10);
		label.color = Theme.text();
		Render2D.textCentered(ctx, font, target.label, cx, bottom - ph + 1.5f, size, label);
	}

	/** Vertical health bar: a dark track filled from the bottom in the health colour, with a soft glow. */
	private static void bar(DrawContext ctx, float x, float y, float h, float w, float health) {
		Render2D.round(ctx, x - 0.75f, y - 0.75f, w + 1.5f, h + 1.5f, 1.75f, 0x8C000000);
		float fillH = h * health;
		if (fillH < 0.5f) return;
		int c = healthColor(health);
		Render2D.shadow(ctx, x, y + h - fillH, w, fillH, 1.25f, 3f, ColorUtil.fade(c, 0.35f));
		Render2D.round(ctx, x, y + h - fillH, w, fillH, 1.25f, ColorUtil.brighten(c, 0.15f), ColorUtil.brighten(c, 0.15f), c, c);
	}

	private static final class Target {
		LivingEntity entity;
		boolean seen;
		boolean visible = true;
		/** Health fraction, eased so the bar drains smoothly. */
		final Anim health = new Anim(1f, 7f);
		/** "Name  12m", rebuilt when the distance changes. */
		String label;
		int meters = -1;
	}
}
