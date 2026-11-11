package dev.v1zir.visuals.module.impl.world;

import dev.v1zir.visuals.V1zir;
import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.module.setting.ColorSetting;
import dev.v1zir.visuals.module.setting.NumberSetting;
import dev.v1zir.visuals.render.world.Draw3D;
import dev.v1zir.visuals.render.world.WorldEffect;
import dev.v1zir.visuals.render.world.WorldEffectRenderer;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.block.BlockState;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ChargedProjectilesComponent;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.EggItem;
import net.minecraft.item.EnderPearlItem;
import net.minecraft.item.ExperienceBottleItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.LingeringPotionItem;
import net.minecraft.item.SnowballItem;
import net.minecraft.item.SplashPotionItem;
import net.minecraft.item.TridentItem;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Predicts the flight of the projectile in your hand with vanilla's physics (gravity, air and water
 * drag, the order they are applied in, your own motion), tick by tick until it meets a block or an
 * entity. The path is a glowing ribbon, the impact a ring on the face it hits, and a hit entity
 * is outlined.
 */
public final class TrajectoriesModule extends Module implements WorldEffect {
	private static final int MAX_TICKS = 300;
	private static final int MAX_SHOTS = 3;
	/** Arrow speed of a crossbow (vanilla CrossbowItem.DEFAULT_SPEED). */
	private static final float CROSSBOW_SPEED = 3.15f;
	/** Spread between multishot arrows, in degrees (vanilla CrossbowItem). */
	private static final float MULTISHOT_SPREAD = 10f;
	/** Alpha of the full-power preview while a bow or trident is not drawn yet. */
	private static final float PREVIEW_ALPHA = 0.45f;
	private static final int RING_SEGMENTS = 40;
	private static final TypeFilter<Entity, Entity> ANY_ENTITY = TypeFilter.instanceOf(Entity.class);

	public final BoolSetting themeColor = add(new BoolSetting("Theme color", true));
	public final ColorSetting color = add(new ColorSetting("Color", 0xFFFFB45C).visibleIf(() -> !themeColor.on()));
	public final NumberSetting lineWidth = add(new NumberSetting("Line width", 1.0, 0.4, 2.5, 0.05).suffix("x"));
	public final BoolSetting landingRing = add(new BoolSetting("Landing ring", true));
	public final BoolSetting highlightEntity = add(new BoolSetting("Highlight entity", true));
	public final BoolSetting preview = add(new BoolSetting("Preview undrawn", true));

	private final Path[] paths = new Path[MAX_SHOTS];
	private int pathCount;
	private final Anim shown = new Anim(0f, 9f);
	private final Draw3D.Wave wave = new Draw3D.Wave();
	private final BlockPos.Mutable cell = new BlockPos.Mutable();
	/** Entities near the last flight, collected once per tick and tested every frame. */
	private final List<Entity> candidates = new ArrayList<>();
	private final Predicate<Entity> hittable = e -> e != mc.player && e != mc.player.getVehicle() && !e.isSpectator() && e.isAlive() && e.canHit();
	// Segment being tested, block whose shape boxes are tested, nearest hit fraction along the
	// segment (2 for none) and the face it enters through.
	private int cellX;
	private int cellY;
	private int cellZ;
	private double segX;
	private double segY;
	private double segZ;
	private double segDx;
	private double segDy;
	private double segDz;
	private double hitT;
	private Direction hitSide;
	private final VoxelShapes.BoxConsumer boxTest = (x0, y0, z0, x1, y1, z1) ->
		slab(cellX + x0, cellY + y0, cellZ + z0, cellX + x1, cellY + y1, cellZ + z1);

	// Flight of the held item, set up by configure().
	private float power;
	private float roll;
	private double gravity;
	private double airDrag;
	private double waterDrag;
	/** Arrows and tridents move before drag and gravity apply; thrown items after. */
	private boolean persistent;
	private boolean inheritsMotion;
	private int shots;
	private float dim;
	// Tridents with riptide are never thrown (checked per tick, the lookup is not free).
	private boolean riptideMain;
	private boolean riptideOff;

	public TrajectoriesModule() {
		super("Trajectories", "Predicted flight path of bows, tridents and throwables", Category.COMBAT);
		setSubCategory("Info");
		for (int i = 0; i < MAX_SHOTS; i++) paths[i] = new Path();
	}

	@Override
	protected void onDisable() {
		pathCount = 0;
		candidates.clear();
		shown.snap(0f);
	}

	@Override
	public void onTick() {
		candidates.clear();
		ClientPlayerEntity player = mc.player;
		if (player == null || mc.world == null) return;
		riptideMain = riptide(player.getMainHandStack());
		riptideOff = riptide(player.getOffHandStack());
		if (pathCount == 0) return;
		double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
		double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
		for (int k = 0; k < pathCount; k++) {
			Path p = paths[k];
			for (int i = 0; i < p.count; i++) {
				minX = Math.min(minX, p.x[i]);
				minY = Math.min(minY, p.y[i]);
				minZ = Math.min(minZ, p.z[i]);
				maxX = Math.max(maxX, p.x[i]);
				maxY = Math.max(maxY, p.y[i]);
				maxZ = Math.max(maxZ, p.z[i]);
			}
		}
		// A margin covers aiming a little elsewhere before the next tick.
		Box area = new Box(minX - 4, minY - 4, minZ - 4, maxX + 4, maxY + 4, maxZ + 4);
		mc.world.collectEntitiesByType(ANY_ENTITY, area, hittable, candidates);
	}

	private boolean riptide(ItemStack stack) {
		return stack.getItem() instanceof TridentItem && EnchantmentHelper.getTridentSpinAttackStrength(stack, mc.player) > 0f;
	}

	@Override
	public void render3D(float tickDelta) {
		boolean aiming = mc.player != null && simulate(mc.player, tickDelta);
		// While the path fades out after switching items, the last one stays where it was.
		float alpha = shown.target(aiming ? 1f : 0f).get();
		if (alpha <= 0.004f) {
			pathCount = 0;
			return;
		}
		alpha *= dim;
		int c1 = themeColor.on() ? Theme.accent() : color.argb();
		int c2 = themeColor.on() ? Theme.accent2() : ColorUtil.brighten(c1, 0.35f);
		for (int k = 0; k < pathCount; k++) {
			Path p = paths[k];
			ribbon(p, alpha, c1, c2);
			impact(p, alpha, c1, c2, tickDelta);
		}
	}

	/** Simulates every shot of the held item into the paths; false when nothing is held that flies. */
	private boolean simulate(ClientPlayerEntity player, float tickDelta) {
		boolean ok;
		if (player.isUsingItem()) {
			ItemStack active = player.getActiveItem();
			ok = configure(player, active, active == player.getMainHandStack() ? riptideMain : riptideOff, tickDelta);
		} else {
			ok = configure(player, player.getMainHandStack(), riptideMain, tickDelta)
				|| configure(player, player.getOffHandStack(), riptideOff, tickDelta);
		}
		if (!ok) return false;

		float yaw = player.getYaw(tickDelta) * MathHelper.RADIANS_PER_DEGREE;
		float pitch = player.getPitch(tickDelta) * MathHelper.RADIANS_PER_DEGREE;
		float shotPitch = pitch + roll * MathHelper.RADIANS_PER_DEGREE;
		// Direction as vanilla ProjectileEntity.setVelocity builds it.
		double fx = -Math.sin(yaw) * Math.cos(pitch);
		double fy = -Math.sin(shotPitch);
		double fz = Math.cos(yaw) * Math.cos(pitch);
		double len = Math.sqrt(fx * fx + fy * fy + fz * fz);
		fx /= len;
		fy /= len;
		fz /= len;
		// Camera up vector: multishot arrows turn around it.
		double ux = -Math.sin(yaw) * Math.sin(pitch);
		double uy = Math.cos(pitch);
		double uz = Math.cos(yaw) * Math.sin(pitch);
		double sx = MathHelper.lerp(tickDelta, player.lastRenderX, player.getX());
		double sy = MathHelper.lerp(tickDelta, player.lastRenderY, player.getY()) + player.getEyeY() - player.getY() - 0.1;
		double sz = MathHelper.lerp(tickDelta, player.lastRenderZ, player.getZ());
		Vec3d motion = player.getVelocity();
		double mx = inheritsMotion ? motion.x : 0;
		double my = inheritsMotion && !player.isOnGround() ? motion.y : 0;
		double mz = inheritsMotion ? motion.z : 0;

		// Vanilla RangedWeaponItem.shootAll: 0, then alternately left and right by the spread.
		float step = shots == 1 ? 0f : 2f * MULTISHOT_SPREAD / (shots - 1);
		float first = (shots - 1) % 2 * step / 2f;
		float side = 1f;
		pathCount = Math.min(shots, MAX_SHOTS);
		for (int k = 0; k < pathCount; k++) {
			double angle = (first + side * ((k + 1) / 2) * step) * MathHelper.RADIANS_PER_DEGREE;
			side = -side;
			double cos = Math.cos(angle);
			double sin = Math.sin(angle);
			// Rodrigues around the up vector (perpendicular to the direction, so one term drops).
			double dx = fx * cos + (uy * fz - uz * fy) * sin;
			double dy = fy * cos + (uz * fx - ux * fz) * sin;
			double dz = fz * cos + (ux * fy - uy * fx) * sin;
			fly(paths[k], sx, sy, sz, dx * power + mx, dy * power + my, dz * power + mz, tickDelta);
		}
		return true;
	}

	/** Reads the flight parameters of the stack; false (changing nothing that is drawn) when it does not fly. */
	private boolean configure(ClientPlayerEntity player, ItemStack stack, boolean riptide, float tickDelta) {
		Item item = stack.getItem();
		roll = 0f;
		shots = 1;
		inheritsMotion = true;
		float alpha = 1f;
		boolean drawing = player.isUsingItem() && player.getActiveItem() == stack;
		if (item instanceof BowItem) {
			if (!drawing && !preview.on()) return false;
			// Vanilla BowItem.getPullProgress, on the interpolated use time so the path grows smoothly.
			float f = drawing ? (player.getItemUseTime() + tickDelta) / 20f : 1f;
			f = Math.min((f * f + f * 2f) / 3f, 1f);
			persistent(Math.max(f, 0.1f) * 3f, 0.6);
			if (!drawing) alpha = PREVIEW_ALPHA;
		} else if (item instanceof CrossbowItem) {
			ChargedProjectilesComponent charged = stack.get(DataComponentTypes.CHARGED_PROJECTILES);
			// Rockets fly straight and burst on a timer: only arrows get a path.
			if (charged == null || charged.isEmpty() || charged.contains(Items.FIREWORK_ROCKET)) return false;
			persistent(CROSSBOW_SPEED, 0.6);
			shots = charged.getProjectiles().size();
			inheritsMotion = false;
		} else if (item instanceof TridentItem) {
			if (riptide || !drawing && !preview.on()) return false;
			persistent(2.5f, 0.99);
			// Vanilla throws only after ten ticks of drawing.
			if (!drawing || player.getItemUseTime() < 10) alpha = PREVIEW_ALPHA;
		} else if (item instanceof SnowballItem || item instanceof EggItem || item instanceof EnderPearlItem) {
			thrown(1.5f, 0f, 0.03);
		} else if (item instanceof ExperienceBottleItem) {
			thrown(0.7f, -20f, 0.07);
		} else if (item instanceof SplashPotionItem || item instanceof LingeringPotionItem) {
			thrown(0.5f, -20f, 0.05);
		} else {
			return false;
		}
		dim = alpha;
		return true;
	}

	private void persistent(float power, double waterDrag) {
		this.power = power;
		this.gravity = 0.05;
		this.airDrag = 0.99;
		this.waterDrag = waterDrag;
		this.persistent = true;
	}

	private void thrown(float power, float roll, double gravity) {
		this.power = power;
		this.roll = roll;
		this.gravity = gravity;
		this.airDrag = 0.99;
		this.waterDrag = 0.8;
		this.persistent = false;
	}

	/** Steps one projectile through the world into the path. */
	private void fly(Path p, double x, double y, double z, double vx, double vy, double vz, float tickDelta) {
		p.count = 0;
		p.entity = null;
		p.hitBlock = false;
		p.add(x, y, z);
		int bottom = mc.world.getBottomY();
		for (int age = 1; age <= MAX_TICKS; age++) {
			double drag = inWater(x, y, z) ? waterDrag : airDrag;
			if (!persistent) {
				vy -= gravity;
				vx *= drag;
				vy *= drag;
				vz *= drag;
			}
			double t = raycastBlocks(x, y, z, vx, vy, vz);
			Direction side = hitSide;
			Entity entity = raycastEntities(x, y, z, vx, vy, vz, Math.min(t, 1.0), age, tickDelta);
			if (entity != null) t = hitT;
			if (t <= 1.0) {
				p.add(x + vx * t, y + vy * t, z + vz * t);
				p.entity = entity;
				p.hitBlock = entity == null;
				p.side = side;
				return;
			}
			x += vx;
			y += vy;
			z += vz;
			p.add(x, y, z);
			if (persistent) {
				vx *= drag;
				vy *= drag;
				vz *= drag;
				vy -= gravity;
			}
			if (y < bottom || !mc.world.isChunkLoaded(MathHelper.floor(x) >> 4, MathHelper.floor(z) >> 4)) return;
		}
	}

	private boolean inWater(double x, double y, double z) {
		cell.set(x, y, z);
		FluidState fluid = mc.world.getFluidState(cell);
		return fluid.isIn(FluidTags.WATER) && y < cell.getY() + fluid.getHeight(mc.world, cell);
	}

	/**
	 * Walks the blocks along the segment from (x, y, z) by (dx, dy, dz) and returns the fraction of
	 * it where it first enters a collision shape (2 when it enters none), with the face in hitSide.
	 */
	private double raycastBlocks(double x, double y, double z, double dx, double dy, double dz) {
		segX = x;
		segY = y;
		segZ = z;
		segDx = dx;
		segDy = dy;
		segDz = dz;
		int ix = MathHelper.floor(x), iy = MathHelper.floor(y), iz = MathHelper.floor(z);
		int ex = MathHelper.floor(x + dx), ey = MathHelper.floor(y + dy), ez = MathHelper.floor(z + dz);
		int stepX = Integer.signum(ex - ix), stepY = Integer.signum(ey - iy), stepZ = Integer.signum(ez - iz);
		// Fraction of the segment to the next cell boundary on each axis, and per cell.
		double nextX = stepX == 0 ? Double.MAX_VALUE : ((stepX > 0 ? ix + 1 : ix) - x) / dx;
		double nextY = stepY == 0 ? Double.MAX_VALUE : ((stepY > 0 ? iy + 1 : iy) - y) / dy;
		double nextZ = stepZ == 0 ? Double.MAX_VALUE : ((stepZ > 0 ? iz + 1 : iz) - z) / dz;
		double cellDx = stepX == 0 ? 0 : Math.abs(1 / dx);
		double cellDy = stepY == 0 ? 0 : Math.abs(1 / dy);
		double cellDz = stepZ == 0 ? 0 : Math.abs(1 / dz);
		// A fixed number of cells, so rounding at a corner can never walk past the end.
		int cells = Math.abs(ex - ix) + Math.abs(ey - iy) + Math.abs(ez - iz);
		for (int i = 0; ; i++) {
			double t = hitCell(ix, iy, iz);
			if (t <= 1.0) return t;
			if (i == cells) return 2;
			if (nextX <= nextY && nextX <= nextZ) {
				ix += stepX;
				nextX += cellDx;
			} else if (nextY <= nextZ) {
				iy += stepY;
				nextY += cellDy;
			} else {
				iz += stepZ;
				nextZ += cellDz;
			}
		}
	}

	private double hitCell(int x, int y, int z) {
		BlockState state = mc.world.getBlockState(cell.set(x, y, z));
		if (state.isAir()) return 2;
		VoxelShape shape = state.getCollisionShape(mc.world, cell);
		if (shape.isEmpty()) return 2;
		hitT = 2;
		if (shape == VoxelShapes.fullCube()) {
			slab(x, y, z, x + 1, y + 1, z + 1);
		} else {
			cellX = x;
			cellY = y;
			cellZ = z;
			shape.forEachBox(boxTest);
		}
		return hitT;
	}

	/** Keeps the nearest entry of the segment into the box in hitT and hitSide. */
	private void slab(double x0, double y0, double z0, double x1, double y1, double z1) {
		double enter = 0;
		double exit = 1;
		Direction face = null;
		if (segDx == 0) {
			if (segX < x0 || segX > x1) return;
		} else {
			double a = (x0 - segX) / segDx, b = (x1 - segX) / segDx;
			if (a > b) {
				double s = a;
				a = b;
				b = s;
			}
			if (a > enter) {
				enter = a;
				face = segDx > 0 ? Direction.WEST : Direction.EAST;
			}
			exit = Math.min(exit, b);
		}
		if (segDy == 0) {
			if (segY < y0 || segY > y1) return;
		} else {
			double a = (y0 - segY) / segDy, b = (y1 - segY) / segDy;
			if (a > b) {
				double s = a;
				a = b;
				b = s;
			}
			if (a > enter) {
				enter = a;
				face = segDy > 0 ? Direction.DOWN : Direction.UP;
			}
			exit = Math.min(exit, b);
		}
		if (segDz == 0) {
			if (segZ < z0 || segZ > z1) return;
		} else {
			double a = (z0 - segZ) / segDz, b = (z1 - segZ) / segDz;
			if (a > b) {
				double s = a;
				a = b;
				b = s;
			}
			if (a > enter) {
				enter = a;
				face = segDz > 0 ? Direction.NORTH : Direction.SOUTH;
			}
			exit = Math.min(exit, b);
		}
		if (enter > exit || enter >= hitT) return;
		hitT = enter;
		// Starting inside the box: the face it is moving against.
		hitSide = face != null ? face : Direction.UP;
	}

	/**
	 * The nearest candidate the segment meets before the fraction limit (its fraction in hitT), as
	 * vanilla ProjectileUtil does: boxes grow by a margin as the projectile ages.
	 */
	private Entity raycastEntities(double x, double y, double z, double dx, double dy, double dz, double limit, int age, float tickDelta) {
		segX = x;
		segY = y;
		segZ = z;
		segDx = dx;
		segDy = dy;
		segDz = dz;
		double margin = Math.max(0.0, Math.min(0.3, (age - 2) / 20.0));
		hitT = limit + 1e-9;
		Entity nearest = null;
		for (int i = 0, n = candidates.size(); i < n; i++) {
			Entity e = candidates.get(i);
			if (e.isRemoved()) continue;
			Box b = e.getBoundingBox();
			// Tested where it is drawn this frame.
			double ox = MathHelper.lerp(tickDelta, e.lastRenderX, e.getX()) - e.getX() - margin;
			double oy = MathHelper.lerp(tickDelta, e.lastRenderY, e.getY()) - e.getY() - margin;
			double oz = MathHelper.lerp(tickDelta, e.lastRenderZ, e.getZ()) - e.getZ() - margin;
			double before = hitT;
			double m = margin * 2;
			slab(b.minX + ox, b.minY + oy, b.minZ + oz, b.maxX + ox + m, b.maxY + oy + m, b.maxZ + oz + m);
			if (hitT < before) nearest = e;
		}
		return nearest;
	}

	/** Camera-facing glowing strip through the points, seamless at the joints, fading in front of the eyes. */
	private void ribbon(Path p, float alpha, int c1, int c2) {
		int n = p.count;
		if (n < 2) return;
		WorldEffectRenderer.Mesh mesh = WorldEffectRenderer.glow();
		float base = 0.045f * lineWidth.f();
		double camX = WorldEffectRenderer.camX;
		double camY = WorldEffectRenderer.camY;
		double camZ = WorldEffectRenderer.camZ;
		double lastSx = 0, lastSy = 0, lastSz = 0;
		int lastColor = 0;
		float last = n - 1;
		for (int i = 0; i < n; i++) {
			int i0 = Math.max(i - 1, 0);
			int i1 = Math.min(i + 1, n - 1);
			double tx = p.x[i1] - p.x[i0], ty = p.y[i1] - p.y[i0], tz = p.z[i1] - p.z[i0];
			double vx = p.x[i] - camX, vy = p.y[i] - camY, vz = p.z[i] - camZ;
			double sx = ty * vz - tz * vy, sy = tz * vx - tx * vz, sz = tx * vy - ty * vx;
			double len = Math.sqrt(sx * sx + sy * sy + sz * sz);
			if (len < 1e-9) {
				sx = lastSx;
				sy = lastSy;
				sz = lastSz;
			} else {
				// Slightly wider with distance so the far end stays readable.
				double dist = Math.sqrt(vx * vx + vy * vy + vz * vz);
				double w = base * (1 + dist * 0.012) / len;
				sx *= w;
				sy *= w;
				sz *= w;
				// Keep the side consistent, or the strip would twist where the path turns past the view.
				if (i > 0 && sx * lastSx + sy * lastSy + sz * lastSz < 0) {
					sx = -sx;
					sy = -sy;
					sz = -sz;
				}
			}
			float fade = Draw3D.cameraFade(p.x[i], p.y[i], p.z[i], 1.2f, 4f);
			int c = ColorUtil.withAlpha(ColorUtil.lerp(c1, c2, i / last), Math.round(235 * alpha * fade));
			if (i > 0 && (c >>> 24 != 0 || lastColor >>> 24 != 0)) {
				mesh.vertex(p.x[i - 1] - lastSx, p.y[i - 1] - lastSy, p.z[i - 1] - lastSz, -1f, 0f, lastColor);
				mesh.vertex(p.x[i - 1] + lastSx, p.y[i - 1] + lastSy, p.z[i - 1] + lastSz, 1f, 0f, lastColor);
				mesh.vertex(p.x[i] + sx, p.y[i] + sy, p.z[i] + sz, 1f, 1f, c);
				mesh.vertex(p.x[i] - sx, p.y[i] - sy, p.z[i] - sz, -1f, 1f, c);
			}
			lastSx = sx;
			lastSy = sy;
			lastSz = sz;
			lastColor = c;
		}
	}

	/** Landing ring on the face the flight ends on, or the outline of the entity it hits; both fade in and out. */
	private void impact(Path p, float alpha, int c1, int c2, float tickDelta) {
		int end = p.count - 1;
		float t = V1zir.time();
		float ring = p.ring.target(landingRing.on() && p.hitBlock ? 1f : 0f).get() * alpha;
		if (p.hitBlock) {
			p.ringX = p.x[end];
			p.ringY = p.y[end];
			p.ringZ = p.z[end];
			p.ringSide = p.side;
		}
		if (ring > 0.004f && p.ringSide != null) {
			Direction s = p.ringSide;
			// Lifted off the face, in the plane of the face: spanned by the two other axes.
			double cx = p.ringX + s.getOffsetX() * 0.02;
			double cy = p.ringY + s.getOffsetY() * 0.02;
			double cz = p.ringZ + s.getOffsetZ() * 0.02;
			Direction.Axis axis = s.getAxis();
			double ax = axis == Direction.Axis.X ? 0 : 1, ay = 1 - ax;
			double by = axis == Direction.Axis.Z ? 1 : 0, bz = 1 - by;
			double r = 0.3 + 0.035 * Math.sin(t * 3.2f);
			WorldEffectRenderer.Mesh mesh = WorldEffectRenderer.glow();
			float spin = t * 1.6f;
			Draw3D.ring(mesh, cx, cy, cz, ax, ay, 0, 0, by, bz, r, 0.15, RING_SEGMENTS, wave.set(c1, c2, spin, 0.3f * ring, RING_SEGMENTS));
			Draw3D.ring(mesh, cx, cy, cz, ax, ay, 0, 0, by, bz, r, 0.035, RING_SEGMENTS, wave.set(c1, c2, spin, 0.95f * ring, RING_SEGMENTS));
			mesh.orb(cx, cy, cz, 0.11f, ColorUtil.withAlpha(ColorUtil.brighten(c1, 0.5f), Math.round(200 * ring)));
		}

		if (p.entity != p.shownEntity && p.entity != null) {
			// A new target fades in where it is rather than taking over the old one's fade.
			p.shownEntity = p.entity;
			p.box.snap(0f);
		}
		Entity e = p.shownEntity;
		float box = p.box.target(highlightEntity.on() && p.entity != null ? 1f : 0f).get() * alpha;
		if (e == null || box <= 0.004f || e.isRemoved()) return;
		Box b = e.getBoundingBox();
		double ox = MathHelper.lerp(tickDelta, e.lastRenderX, e.getX()) - e.getX();
		double oy = MathHelper.lerp(tickDelta, e.lastRenderY, e.getY()) - e.getY();
		double oz = MathHelper.lerp(tickDelta, e.lastRenderZ, e.getZ()) - e.getZ();
		double g = 0.06;
		double x0 = b.minX + ox - g, y0 = b.minY + oy - g, z0 = b.minZ + oz - g;
		double x1 = b.maxX + ox + g, y1 = b.maxY + oy + g, z1 = b.maxZ + oz + g;
		float pulse = 0.85f + 0.15f * (float) Math.sin(t * 4f);
		Draw3D.solidBox(WorldEffectRenderer.translucent(), x0, y0, z0, x1, y1, z1,
			ColorUtil.withAlpha(c2, Math.round(46 * box)), ColorUtil.withAlpha(c1, Math.round(22 * box)));
		Draw3D.roundedBox(WorldEffectRenderer.glow(), x0, y0, z0, x1, y1, z1,
			ColorUtil.withAlpha(c2, Math.round(230 * box * pulse)), ColorUtil.withAlpha(c1, Math.round(230 * box * pulse)));
	}

	/** One simulated flight: its points, how it ends, and the fades of its impact marks. */
	private static final class Path {
		final double[] x = new double[MAX_TICKS + 2];
		final double[] y = new double[MAX_TICKS + 2];
		final double[] z = new double[MAX_TICKS + 2];
		int count;
		boolean hitBlock;
		Direction side;
		Entity entity;
		// Where the ring was last placed (it stays there while fading out) and the outlined entity.
		double ringX;
		double ringY;
		double ringZ;
		Direction ringSide;
		Entity shownEntity;
		final Anim ring = new Anim(0f, 10f);
		final Anim box = new Anim(0f, 10f);

		void add(double px, double py, double pz) {
			x[count] = px;
			y[count] = py;
			z[count] = pz;
			count++;
		}
	}
}
