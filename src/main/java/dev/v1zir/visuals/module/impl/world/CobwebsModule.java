package dev.v1zir.visuals.module.impl.world;

import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.module.setting.ColorSetting;
import dev.v1zir.visuals.module.setting.NumberSetting;
import dev.v1zir.visuals.render.world.Draw3D;
import dev.v1zir.visuals.render.world.WorldEffect;
import dev.v1zir.visuals.render.world.WorldEffectRenderer;
import dev.v1zir.visuals.util.ColorUtil;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.function.Predicate;

/**
 * Cobwebs you can see through, outlined. The web is drawn translucent at the chosen opacity (CobwebModel: its quads go
 * to the translucent layer, with vanilla's renderer and Sodium alike), and every cobweb near the player gets a rounded
 * outline running from the outline colour at the top to its shade at the bottom, purple to black at first, of the same
 * width on the screen near and far. Turning it on or off, or a new opacity, rebuilds only the chunk sections that have
 * cobwebs in them.
 */
public final class CobwebsModule extends Module implements WorldEffect {
	private static final Predicate<BlockState> IS_WEB = state -> state.isOf(Blocks.COBWEB);
	/** At most this many outlines (a whole trap of webs is still far fewer). */
	private static final int MAX_OUTLINES = 768;
	/** Lifts the outline's faces off the block so they never fight the blocks beside it for depth. */
	private static final double INFLATE = 0.004;

	public final NumberSetting opacity = add(new NumberSetting("Opacity", 0.6, 0.15, 1.0, 0.05));
	public final BoolSetting outline = add(new BoolSetting("Outline", true));
	public final ColorSetting color = add(new ColorSetting("Outline color", 0xFFA64DFF).visibleIf(outline::on));
	public final ColorSetting shade = add(new ColorSetting("Outline shade", 0xFF000000).visibleIf(outline::on));
	public final BoolSetting fill = add(new BoolSetting("Fill", false).visibleIf(outline::on));
	public final NumberSetting range = add(new NumberSetting("Range", 24, 8, 64, 4).suffix(" m").visibleIf(outline::on));

	/** The alpha (0 to 255) the web's quads are built with while the module is on, else -1. Chunk builders read it. */
	private static volatile int webAlpha = -1;

	/** Cobwebs within the outline's range, as packed positions; looked for a few times a second. */
	private final LongArrayList webs = new LongArrayList();
	private final BlockPos.Mutable pos = new BlockPos.Mutable();
	private int scanIn;
	/** Ticks the opacity slider has rested on a new value; the chunks are rebuilt once it settles, not at every step. */
	private int settle;

	public CobwebsModule() {
		super("Cobwebs", "See-through cobwebs with a purple and black outline", Category.VISUALS);
		setSubCategory("World");
	}

	/** The alpha of the web's quads, or -1 when the module is off and the web is vanilla's (CobwebModel). */
	public static int webAlpha() {
		return webAlpha;
	}

	/** How many cobwebs are outlined (found within the range at the last look round). */
	public int outlines() {
		return webs.size();
	}

	@Override
	protected void onEnable() {
		webAlpha = Math.round(opacity.f() * 255f);
		settle = 0;
		scanIn = 0;
		rebuildWebs();
	}

	@Override
	protected void onDisable() {
		webAlpha = -1;
		webs.clear();
		rebuildWebs();
	}

	@Override
	public void onTick() {
		int wanted = Math.round(opacity.f() * 255f);
		if (wanted != webAlpha) {
			if (++settle >= 4) {
				settle = 0;
				webAlpha = wanted;
				rebuildWebs();
			}
		} else {
			settle = 0;
		}
		if (!outline.on()) {
			webs.clear();
		} else if (--scanIn <= 0) {
			scanIn = 5;
			scan();
		}
	}

	@Override
	public void render3D(float tickDelta) {
		ClientWorld world = mc.world;
		if (world == null || webs.isEmpty() || !outline.on()) return;
		int top = color.argb();
		int bottom = shade.argb();
		float reach = range.f();
		for (int i = 0; i < webs.size(); i++) {
			long packed = webs.getLong(i);
			pos.set(BlockPos.unpackLongX(packed), BlockPos.unpackLongY(packed), BlockPos.unpackLongZ(packed));
			// Broken since the last look round: no outline left hanging in the air.
			if (!world.getBlockState(pos).isOf(Blocks.COBWEB)) continue;
			double x = pos.getX();
			double y = pos.getY();
			double z = pos.getZ();
			// Fades in over the last blocks of the range, and away when the camera is inside the web.
			float far = 1f - Draw3D.cameraFade(x + 0.5, y + 0.5, z + 0.5, reach - 4f, reach);
			float shown = far * Draw3D.cameraFade(x + 0.5, y + 0.5, z + 0.5, 0.7f, 1.6f);
			if (shown <= 0.01f) continue;
			int topColor = ColorUtil.withAlpha(top, Math.round(ColorUtil.alpha(top) * shown));
			int bottomColor = ColorUtil.withAlpha(bottom, Math.round(ColorUtil.alpha(bottom) * shown));
			if (fill.on()) {
				Draw3D.solidBox(WorldEffectRenderer.translucent(), x, y, z, x + 1, y + 1, z + 1,
					ColorUtil.withAlpha(bottom, Math.round(40 * shown)), ColorUtil.withAlpha(top, Math.round(34 * shown)));
			}
			Draw3D.roundedBox(WorldEffectRenderer.translucent(), x - INFLATE, y - INFLATE, z - INFLATE,
				x + 1 + INFLATE, y + 1 + INFLATE, z + 1 + INFLATE, bottomColor, topColor);
		}
	}

	/** Collects the cobwebs within the outline's range, skipping every chunk section that has none. */
	private void scan() {
		webs.clear();
		ClientWorld world = mc.world;
		if (world == null || mc.player == null) return;
		int r = range.i();
		BlockPos at = mc.player.getBlockPos();
		int px = at.getX();
		int py = at.getY();
		int pz = at.getZ();
		int minY = Math.max(world.getBottomY(), py - r);
		int maxY = Math.min(world.getTopYInclusive(), py + r);
		for (int cx = px - r >> 4; cx <= px + r >> 4; cx++) {
			for (int cz = pz - r >> 4; cz <= pz + r >> 4; cz++) {
				if (!world.isChunkLoaded(cx, cz)) continue;
				WorldChunk chunk = world.getChunk(cx, cz);
				ChunkSection[] sections = chunk.getSectionArray();
				for (int sy = minY >> 4; sy <= maxY >> 4; sy++) {
					int index = chunk.sectionCoordToIndex(sy);
					if (index < 0 || index >= sections.length) continue;
					ChunkSection section = sections[index];
					if (section == null || section.isEmpty() || !section.hasAny(IS_WEB)) continue;
					for (int y = 0; y < 16; y++) {
						int by = sy << 4 | y;
						if (by < minY || by > maxY) continue;
						for (int z = 0; z < 16; z++) {
							for (int x = 0; x < 16; x++) {
								if (!section.getBlockState(x, y, z).isOf(Blocks.COBWEB)) continue;
								int bx = cx << 4 | x;
								int bz = cz << 4 | z;
								long dx = bx - px;
								long dy = by - py;
								long dz = bz - pz;
								if (dx * dx + dy * dy + dz * dz > (long) r * r) continue;
								webs.add(BlockPos.asLong(bx, by, bz));
								if (webs.size() >= MAX_OUTLINES) return;
							}
						}
					}
				}
			}
		}
	}

	/**
	 * Rebuilds the chunk sections with cobwebs in them within the view distance, so the web's model is built again with
	 * the module's look (or vanilla's).
	 */
	private static void rebuildWebs() {
		ClientWorld world = mc.world;
		if (world == null || mc.player == null || mc.worldRenderer == null) return;
		int view = mc.options.getClampedViewDistance() + 1;
		ChunkPos center = mc.player.getChunkPos();
		for (int cx = center.x - view; cx <= center.x + view; cx++) {
			for (int cz = center.z - view; cz <= center.z + view; cz++) {
				if (!world.isChunkLoaded(cx, cz)) continue;
				WorldChunk chunk = world.getChunk(cx, cz);
				ChunkSection[] sections = chunk.getSectionArray();
				for (int i = 0; i < sections.length; i++) {
					ChunkSection section = sections[i];
					if (section == null || section.isEmpty() || !section.hasAny(IS_WEB)) continue;
					mc.worldRenderer.scheduleChunkRender(cx, chunk.sectionIndexToCoord(i), cz);
				}
			}
		}
	}
}
