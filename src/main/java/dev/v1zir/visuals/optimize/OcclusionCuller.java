package dev.v1zir.visuals.optimize;

import dev.v1zir.visuals.V1zir;
import dev.v1zir.visuals.module.impl.visuals.EntityGlowModule;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.Leashable;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.LockSupport;

/**
 * Occlusion culling of entities and block entities, in the spirit of the Entity Culling mod. A background
 * thread casts rays from the camera to a 3x3x3 grid of points on each target's (padded) box through the
 * client world and marks the target hidden only when every ray meets an opaque full block. The render
 * thread only reads those results, and trusts a "hidden" only while the camera is still within half a
 * block of where it was tested from; anything untested, near, glowing, named or a player always renders.
 */
public final class OcclusionCuller {
	/** Closer than this (blocks, to the box) nothing is culled: the padding and a frame of lag must never show. */
	private static final double NEAR = 4.0;
	private static final double MOVED_SQ = 0.5 * 0.5;
	/** Rays longer than this are not traced (the target counts as visible). */
	private static final double MAX_DISTANCE = 160.0;
	/** Targets not asked about for this many frames are forgotten. */
	private static final int FORGET_FRAMES = 40;
	private static final long MIN_PASS_NANOS = 4_000_000L;

	private static final Int2ObjectOpenHashMap<Target> ENTITIES = new Int2ObjectOpenHashMap<>();
	private static final Long2ObjectOpenHashMap<Target> BLOCK_ENTITIES = new Long2ObjectOpenHashMap<>();
	private static final List<Target> TARGETS = new ArrayList<>();
	private static boolean targetsChanged;
	private static int frame;
	private static ClientWorld trackedWorld;

	// Handed to the worker: the target list (a new array only when targets come or go) and the camera.
	private static volatile Target[] published = new Target[0];
	private static volatile ClientWorld publishedWorld;
	private static volatile double camX;
	private static volatile double camY;
	private static volatile double camZ;
	private static Thread worker;

	private OcclusionCuller() {
	}

	/** One culled thing: its padded box (render thread writes, worker reads) and the last result. */
	private static final class Target {
		double minX;
		double minY;
		double minZ;
		double maxX;
		double maxY;
		double maxZ;
		int seen;
		volatile boolean hidden;
		volatile double testX;
		volatile double testY;
		volatile double testZ;

		void box(double x0, double y0, double z0, double x1, double y1, double z1) {
			minX = x0;
			minY = y0;
			minZ = z0;
			maxX = x1;
			maxY = y1;
			maxZ = z1;
			seen = frame;
		}

		boolean hiddenFrom(double x, double y, double z) {
			if (!hidden) return false;
			double dx = testX - x;
			double dy = testY - y;
			double dz = testZ - z;
			return dx * dx + dy * dy + dz * dz < MOVED_SQ;
		}
	}

	/** Render thread, from EntityRenderManager.shouldRender: true if the entity is known to be hidden. */
	public static boolean hidden(Entity entity, double x, double y, double z) {
		if (!Optimizations.occlusion() || exempt(entity)) return false;
		Box box = entity.getBoundingBox();
		if ((box.maxX - box.minX) * (box.maxY - box.minY) * (box.maxZ - box.minZ) > 512.0) return false;
		if (distanceSq(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, x, y, z) < NEAR * NEAR) return false;
		Target target = ENTITIES.get(entity.getId());
		if (target == null) {
			target = add();
			ENTITIES.put(entity.getId(), target);
		}
		// Padded: arms, held items and armour reach past the box, and the box moves on while the result is computed.
		target.box(box.minX - 0.5, box.minY - 0.5, box.minZ - 0.5, box.maxX + 0.5, box.maxY + 0.5, box.maxZ + 0.5);
		return target.hiddenFrom(x, y, z);
	}

	/** Render thread, from BlockEntityRenderManager.render: true if the block entity at pos is known to be hidden. */
	public static boolean hidden(BlockPos pos, double x, double y, double z) {
		if (!Optimizations.occlusion() || cameraExempt()) return false;
		// Models reach out of their block: banners two blocks up or one down, signs and pots a little sideways.
		double x0 = pos.getX() - 0.5;
		double y0 = pos.getY() - 1.0;
		double z0 = pos.getZ() - 0.5;
		double x1 = pos.getX() + 1.5;
		double y1 = pos.getY() + 2.0;
		double z1 = pos.getZ() + 1.5;
		if (distanceSq(x0, y0, z0, x1, y1, z1, x, y, z) < NEAR * NEAR) return false;
		long key = pos.asLong();
		Target target = BLOCK_ENTITIES.get(key);
		if (target == null) {
			target = add();
			BLOCK_ENTITIES.put(key, target);
		}
		target.box(x0, y0, z0, x1, y1, z1);
		return target.hiddenFrom(x, y, z);
	}

	private static Target add() {
		Target target = new Target();
		TARGETS.add(target);
		targetsChanged = true;
		return target;
	}

	private static boolean exempt(Entity entity) {
		MinecraftClient mc = V1zir.mc();
		if (cameraExempt() || entity == mc.getCameraEntity() || entity == mc.player) return true;
		// Players and named entities carry labels that show through walls; glowing ones draw outlines through them.
		if (entity instanceof PlayerEntity || entity.hasCustomName() || entity.isGlowing() || mc.hasOutline(entity)) return true;
		// A leash is drawn by the leashed entity and can be seen while the entity is not.
		if (entity instanceof Leashable leashable && leashable.isLeashed()) return true;
		EntityGlowModule glow = EntityGlowModule.active();
		return glow != null && glow.color(entity) != 0;
	}

	/** A spectator's camera can sit inside blocks and look through them. */
	private static boolean cameraExempt() {
		Entity camera = V1zir.mc().getCameraEntity();
		return camera == null || camera.isSpectator();
	}

	private static double distanceSq(double x0, double y0, double z0, double x1, double y1, double z1, double x, double y, double z) {
		double dx = Math.max(0.0, Math.max(x0 - x, x - x1));
		double dy = Math.max(0.0, Math.max(y0 - y, y - y1));
		double dz = Math.max(0.0, Math.max(z0 - z, z - z1));
		return dx * dx + dy * dy + dz * dz;
	}

	/** Render thread, once per frame: hands the targets and the camera to the worker and wakes it. */
	public static void endFrame() {
		frame++;
		MinecraftClient mc = V1zir.mc();
		if (!Optimizations.occlusion() || mc.world == null) {
			if (!TARGETS.isEmpty() || trackedWorld != null) clear();
			return;
		}
		if (mc.world != trackedWorld) {
			clear();
			trackedWorld = mc.world;
		}
		if (frame % FORGET_FRAMES == 0) forgetUnseen();
		if (targetsChanged) {
			targetsChanged = false;
			published = TARGETS.toArray(new Target[0]);
		}
		Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();
		camX = cam.x;
		camY = cam.y;
		camZ = cam.z;
		publishedWorld = mc.world;
		if (worker == null) {
			worker = new Thread(OcclusionCuller::work, "V1zir occlusion culling");
			worker.setDaemon(true);
			worker.setPriority(Thread.NORM_PRIORITY - 1);
			worker.start();
		}
		LockSupport.unpark(worker);
	}

	private static void forgetUnseen() {
		int before = TARGETS.size();
		TARGETS.removeIf(target -> frame - target.seen > FORGET_FRAMES);
		if (TARGETS.size() == before) return;
		ENTITIES.values().removeIf(target -> frame - target.seen > FORGET_FRAMES);
		BLOCK_ENTITIES.values().removeIf(target -> frame - target.seen > FORGET_FRAMES);
		targetsChanged = true;
	}

	private static void clear() {
		TARGETS.clear();
		ENTITIES.clear();
		BLOCK_ENTITIES.clear();
		published = new Target[0];
		publishedWorld = null;
		trackedWorld = null;
	}

	// ------------------------------------------------------------------ worker thread

	private static void work() {
		BlockPos.Mutable cell = new BlockPos.Mutable();
		long lastPass = 0L;
		while (true) {
			// Woken every frame (the timeout only matters while no frames are drawn), at most every 4 ms.
			LockSupport.parkNanos(100_000_000L);
			long rest = lastPass + MIN_PASS_NANOS - System.nanoTime();
			if (rest > 0L) LockSupport.parkNanos(rest);
			lastPass = System.nanoTime();
			ClientWorld world = publishedWorld;
			Target[] targets = published;
			if (world == null) continue;
			double x = camX;
			double y = camY;
			double z = camZ;
			for (Target target : targets) {
				boolean hidden;
				try {
					hidden = occluded(world, cell, x, y, z, target);
				} catch (Throwable t) {
					// The world changes under us (chunks unloading); never hide on a failed test.
					hidden = false;
				}
				target.testX = x;
				target.testY = y;
				target.testZ = z;
				target.hidden = hidden;
			}
		}
	}

	/** True if no ray from the camera reaches any of the 27 grid points of the target's box. */
	private static boolean occluded(ClientWorld world, BlockPos.Mutable cell, double x, double y, double z, Target t) {
		double cx = Math.max(t.minX, Math.min(x, t.maxX));
		double cy = Math.max(t.minY, Math.min(y, t.maxY));
		double cz = Math.max(t.minZ, Math.min(z, t.maxZ));
		double dx = cx - x;
		double dy = cy - y;
		double dz = cz - z;
		if (dx * dx + dy * dy + dz * dz > MAX_DISTANCE * MAX_DISTANCE) return false;
		for (int i = 0; i < 3; i++) {
			double px = t.minX + (t.maxX - t.minX) * i * 0.5;
			for (int j = 0; j < 3; j++) {
				double py = t.minY + (t.maxY - t.minY) * j * 0.5;
				for (int k = 0; k < 3; k++) {
					double pz = t.minZ + (t.maxZ - t.minZ) * k * 0.5;
					if (clear(world, cell, x, y, z, px, py, pz)) return false;
				}
			}
		}
		return true;
	}

	/**
	 * Walks the blocks between the camera and the point (3D DDA). The camera's own block and the point's block
	 * do not count, so a target touching a wall or a camera clipping into one never hides anything.
	 */
	private static boolean clear(ClientWorld world, BlockPos.Mutable cell, double x0, double y0, double z0, double x1, double y1, double z1) {
		int bx = floor(x0);
		int by = floor(y0);
		int bz = floor(z0);
		int ex = floor(x1);
		int ey = floor(y1);
		int ez = floor(z1);
		double dx = x1 - x0;
		double dy = y1 - y0;
		double dz = z1 - z0;
		int sx = dx > 0 ? 1 : dx < 0 ? -1 : 0;
		int sy = dy > 0 ? 1 : dy < 0 ? -1 : 0;
		int sz = dz > 0 ? 1 : dz < 0 ? -1 : 0;
		double tdx = sx == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dx);
		double tdy = sy == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dy);
		double tdz = sz == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dz);
		double tx = sx == 0 ? Double.MAX_VALUE : (sx > 0 ? bx + 1 - x0 : x0 - bx) * tdx;
		double ty = sy == 0 ? Double.MAX_VALUE : (sy > 0 ? by + 1 - y0 : y0 - by) * tdy;
		double tz = sz == 0 ? Double.MAX_VALUE : (sz > 0 ? bz + 1 - z0 : z0 - bz) * tdz;
		int steps = Math.abs(ex - bx) + Math.abs(ey - by) + Math.abs(ez - bz);
		for (int n = 0; n < steps; n++) {
			if (tx < ty && tx < tz) {
				bx += sx;
				tx += tdx;
			} else if (ty < tz) {
				by += sy;
				ty += tdy;
			} else {
				bz += sz;
				tz += tdz;
			}
			if (bx == ex && by == ey && bz == ez) return true;
			if (world.getBlockState(cell.set(bx, by, bz)).isOpaqueFullCube()) return false;
		}
		return true;
	}

	private static int floor(double value) {
		int i = (int) value;
		return value < i ? i - 1 : i;
	}
}
