package dev.v1zir.visuals.module.impl.effects;

import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.module.setting.ColorSetting;
import dev.v1zir.visuals.module.setting.ModeSetting;
import dev.v1zir.visuals.module.setting.NumberSetting;
import dev.v1zir.visuals.render.world.ModelCapture;
import dev.v1zir.visuals.render.world.WorldEffect;
import dev.v1zir.visuals.render.world.WorldEffectRenderer;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.model.Model;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;

import java.util.Arrays;

/**
 * Players (and optionally mobs) as tinted translucent silhouettes. Glass keeps the body visible under
 * a tinted shell with a bright fresnel rim and softly glowing edges on every face; Glow replaces the
 * body by a bright self-lit silhouette with a halo; Flat by one solid colour. The quads of the model
 * are captured while vanilla submits it (LivingEntityRendererMixin) and drawn as a shell after the
 * world in the effect batches, depth tested or through walls, so chams add no render pass of their
 * own. Back faces are dropped on the CPU. Never for yourself in first person.
 */
public final class ChamsModule extends Module implements WorldEffect {
	private static final int GLASS = 0;
	private static final int GLOW = 1;
	/** How far the shell sits outside the model per style: Glass clears the outer skin layer. */
	private static final float[] INFLATE = {0.04f, 0.012f, 0.006f};
	private static final float HALO = 0.07f;
	/** Per captured entity: first quad, quad count, colour. */
	private static final int SPAN = 3;

	public final ModeSetting style = add(new ModeSetting("Style", "Glass", "Glass", "Glow", "Flat"));
	public final BoolSetting throughWalls = add(new BoolSetting("Through walls", true));
	public final NumberSetting opacity = add(new NumberSetting("Opacity", 0.75, 0.1, 1.0, 0.05));
	public final BoolSetting mobs = add(new BoolSetting("Mobs", false));
	public final BoolSetting self = add(new BoolSetting("Self", false));
	public final BoolSetting teamColor = add(new BoolSetting("Team color", true));
	public final BoolSetting themeColor = add(new BoolSetting("Theme color", false));
	public final ColorSetting players = add(new ColorSetting("Players", 0xFF5CE1FF, false).visibleIf(() -> !themeColor.on()));
	public final ColorSetting hostile = add(new ColorSetting("Hostile", 0xFFFF5C7A, false).visibleIf(() -> mobs.on() && !themeColor.on()));
	public final ColorSetting passive = add(new ColorSetting("Passive", 0xFF8CFF9E, false).visibleIf(() -> mobs.on() && !themeColor.on()));

	private final ModelCapture capture = new ModelCapture();
	private int[] spans = new int[SPAN * 16];
	private int spanCount;
	/** World frame the captured quads belong to. */
	private int frame = -1;

	public ChamsModule() {
		super("Chams", "Players as glass, glowing or flat silhouettes", Category.COMBAT);
		setSubCategory("Target");
	}

	@Override
	public String suffix() {
		return style.get();
	}

	@Override
	protected void onDisable() {
		capture.clear();
		spanCount = 0;
	}

	/** Chams colour of an entity this frame (EntityRenderManagerMixin), 0 for none. */
	public static int color(Entity entity) {
		ChamsModule module = ModuleManager.get(ChamsModule.class);
		return module != null && module.isEnabled() ? module.colorFor(entity) : 0;
	}

	private int colorFor(Entity entity) {
		if (!(entity instanceof LivingEntity) || entity instanceof ArmorStandEntity) return 0;
		// Your own body only shows in third person, and only if asked for.
		if (entity == mc.player && (!self.on() || mc.options.getPerspective().isFirstPerson())) return 0;
		int c;
		if (entity instanceof PlayerEntity) c = themeColor.on() ? Theme.accent() : players.argb();
		else if (!mobs.on()) return 0;
		else if (entity instanceof Monster) c = themeColor.on() ? Theme.accent2() : hostile.argb();
		else c = themeColor.on() ? ColorUtil.lerp(Theme.accent(), Theme.accent2(), 0.5f) : passive.argb();
		if (teamColor.on()) {
			// Plain white means the entity is in no coloured team.
			int team = entity.getTeamColorValue() & 0xFFFFFF;
			if (team != 0xFFFFFF) c = team;
		}
		return 0xFF000000 | c;
	}

	/**
	 * Vanilla is about to submit the model of an entity with chams: its quads are captured for the shell.
	 * Returns whether vanilla should still draw the body (Glass keeps it under the shell).
	 */
	public static <S> boolean capture(Model<? super S> model, S state, MatrixStack matrices, int light, int overlay, int color) {
		ChamsModule module = ModuleManager.get(ChamsModule.class);
		if (module == null || !module.isEnabled()) return true;
		module.add(model, state, matrices, light, overlay, color);
		return module.style.index() == GLASS;
	}

	private <S> void add(Model<? super S> model, S state, MatrixStack matrices, int light, int overlay, int color) {
		int now = WorldEffectRenderer.frame();
		if (frame != now) {
			frame = now;
			capture.clear();
			spanCount = 0;
		}
		int start = capture.count();
		PlayerEntityRenderState player = state instanceof PlayerEntityRenderState p ? p : null;
		// The outer skin layers are slightly larger copies of the body: one shell around the body is enough.
		boolean hat = false, jacket = false, leftSleeve = false, rightSleeve = false, leftPants = false, rightPants = false;
		if (player != null) {
			hat = player.hatVisible;
			jacket = player.jacketVisible;
			leftSleeve = player.leftSleeveVisible;
			rightSleeve = player.rightSleeveVisible;
			leftPants = player.leftPantsLegVisible;
			rightPants = player.rightPantsLegVisible;
			player.hatVisible = player.jacketVisible = player.leftSleeveVisible = player.rightSleeveVisible = false;
			player.leftPantsLegVisible = player.rightPantsLegVisible = false;
		}
		model.setAngles(state);
		model.render(matrices, capture, light, overlay);
		if (player != null) {
			// Vanilla poses the model again from the state when it draws, so the state is put back as it was.
			player.hatVisible = hat;
			player.jacketVisible = jacket;
			player.leftSleeveVisible = leftSleeve;
			player.rightSleeveVisible = rightSleeve;
			player.leftPantsLegVisible = leftPants;
			player.rightPantsLegVisible = rightPants;
		}
		if (spans.length < (spanCount + 1) * SPAN) spans = Arrays.copyOf(spans, spans.length * 2);
		int o = spanCount++ * SPAN;
		spans[o] = start;
		spans[o + 1] = capture.count() - start;
		spans[o + 2] = color;
	}

	@Override
	public void render3D(float tickDelta) {
		if (frame != WorldEffectRenderer.frame() || spanCount == 0) return;
		boolean through = throughWalls.on();
		WorldEffectRenderer.Mesh fill = through ? WorldEffectRenderer.translucentThrough() : WorldEffectRenderer.translucent();
		WorldEffectRenderer.Mesh glow = through ? WorldEffectRenderer.glowThrough() : WorldEffectRenderer.glow();
		int type = style.index();
		float a = opacity.f();
		float[] q = capture.quads();
		for (int s = 0; s < spanCount; s++) {
			int start = spans[s * SPAN], end = start + spans[s * SPAN + 1];
			int c = spans[s * SPAN + 2];
			// Shades of the entity's colour for the layers of its style.
			int light = ColorUtil.brighten(c, type == GLASS ? 0.25f : 0.2f);
			int lighter = ColorUtil.brighten(c, 0.55f);
			int dark = ColorUtil.darken(c, 0.35f);
			for (int i = start; i < end; i++) {
				int o = i * ModelCapture.STRIDE;
				switch (type) {
					case GLASS -> {
						shell(fill, q, o, INFLATE[GLASS], c, a * 0.1f, a * 0.55f, 0.15f, false);
						shell(glow, q, o, INFLATE[GLASS], light, a * 0.55f, a * 0.45f, 0f, true);
					}
					case GLOW -> {
						shell(fill, q, o, INFLATE[GLOW], dark, a * 0.55f, 0f, 0f, false);
						shell(glow, q, o, INFLATE[GLOW], light, a * 0.35f, a * 0.55f, 0.1f, false);
						shell(glow, q, o, INFLATE[GLOW], lighter, a * 0.5f, 0f, 0f, true);
						halo(glow, q, o, c, a * 0.5f);
					}
					default -> flat(fill, q, o, INFLATE[2], c, a);
				}
			}
		}
		// Drawn once: the next frame captures afresh.
		spanCount = 0;
		capture.clear();
	}

	/**
	 * One face of the shell, pushed out along its normal, skipped when it faces away. Alpha is base plus rim
	 * times the squared fresnel term (0 facing the camera, 1 edge on); white lifts the rim towards white.
	 * Edges draws the face as a glowing rounded border instead of a fill.
	 */
	private static void shell(WorldEffectRenderer.Mesh mesh, float[] q, int o, float inflate, int color, float base, float rim, float white,
							  boolean edges) {
		float nx = q[o + 12], ny = q[o + 13], nz = q[o + 14];
		float cx = (q[o] + q[o + 3] + q[o + 6] + q[o + 9]) * 0.25f;
		float cy = (q[o + 1] + q[o + 4] + q[o + 7] + q[o + 10]) * 0.25f;
		float cz = (q[o + 2] + q[o + 5] + q[o + 8] + q[o + 11]) * 0.25f;
		// The camera is at the origin of the captured space.
		if (nx * cx + ny * cy + nz * cz >= 0f) return;
		float u0 = 3f, u1 = 3f, v0 = 0f, v1 = 0f;
		if (edges) {
			// The rounded border encoding of Draw3D.roundedBox: half sizes packed next to the position.
			float hu = 0.5f * length(q[o + 3] - q[o], q[o + 4] - q[o + 1], q[o + 5] - q[o + 2]);
			float hv = 0.5f * length(q[o + 9] - q[o], q[o + 10] - q[o + 1], q[o + 11] - q[o + 2]);
			float qu = 16f * Math.max(1, Math.min(480, Math.round(hu * 64f)));
			float qv = 16f * Math.max(1, Math.min(480, Math.round(hv * 64f))) + 8f;
			u0 = qu - hu;
			u1 = qu + hu;
			v0 = -hv - qv;
			v1 = hv - qv;
		}
		vertex(mesh, q, o, 0, nx, ny, nz, inflate, u0, v0, color, base, rim, white);
		vertex(mesh, q, o, 3, nx, ny, nz, inflate, u1, v0, color, base, rim, white);
		vertex(mesh, q, o, 6, nx, ny, nz, inflate, u1, v1, color, base, rim, white);
		vertex(mesh, q, o, 9, nx, ny, nz, inflate, u0, v1, color, base, rim, white);
	}

	private static void vertex(WorldEffectRenderer.Mesh mesh, float[] q, int o, int corner, float nx, float ny, float nz, float inflate,
							   float u, float v, int color, float base, float rim, float white) {
		float x = q[o + corner] + nx * inflate, y = q[o + corner + 1] + ny * inflate, z = q[o + corner + 2] + nz * inflate;
		float facing = -(nx * x + ny * y + nz * z) / Math.max(1e-4f, length(x, y, z));
		float f = 1f - Math.max(0f, facing);
		float k = f * f;
		int c = white > 0f ? ColorUtil.lerp(color, 0xFFFFFFFF, white * k) : color;
		mesh.relative(x, y, z, u, v, ColorUtil.withAlpha(c, Math.round(255f * Math.min(1f, base + rim * k))));
	}

	/** A larger, faint copy of the face whose alpha only shows edge on, so the silhouette seems to glow past its outline. */
	private static void halo(WorldEffectRenderer.Mesh mesh, float[] q, int o, int color, float alpha) {
		float nx = q[o + 12], ny = q[o + 13], nz = q[o + 14];
		float cx = (q[o] + q[o + 6]) * 0.5f, cy = (q[o + 1] + q[o + 7]) * 0.5f, cz = (q[o + 2] + q[o + 8]) * 0.5f;
		if (nx * cx + ny * cy + nz * cz >= 0f) return;
		for (int corner = 0; corner < 12; corner += 3) {
			float x = q[o + corner] + nx * HALO, y = q[o + corner + 1] + ny * HALO, z = q[o + corner + 2] + nz * HALO;
			float facing = -(nx * x + ny * y + nz * z) / Math.max(1e-4f, length(x, y, z));
			float f = 1f - Math.max(0f, facing);
			mesh.relative(x, y, z, 3f, 0f, ColorUtil.withAlpha(color, Math.round(255f * alpha * f * f * f)));
		}
	}

	/** Solid colour, shaded a little by the face direction so the shape still reads. */
	private static void flat(WorldEffectRenderer.Mesh mesh, float[] q, int o, float inflate, int color, float alpha) {
		float ny = q[o + 13];
		float shade = 0.8f + 0.2f * ny;
		shell(mesh, q, o, inflate, ColorUtil.darken(color, 1f - shade), alpha, 0f, 0f, false);
	}

	private static float length(float x, float y, float z) {
		return (float) Math.sqrt(x * x + y * y + z * z);
	}
}
