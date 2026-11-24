package dev.v1zir.visuals.render;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import it.unimi.dsi.fastutil.objects.Reference2ByteOpenHashMap;
import net.minecraft.block.AbstractFurnaceBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.Heightmap;
import net.minecraft.world.LightType;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/**
 * The blocks around the camera as the reflection pass (render/Reflections) sees them, in two small
 * textures that wrap around: block x lives in texel x &amp; (size - 1), so moving never shifts any
 * data. A rolling refresh rewrites a few rows every tick and the shader only trusts the part well
 * inside the window.
 * <ul>
 *   <li>Columns, 128 x 128: the first free Y above the highest motion-blocking block (where rain
 *   stops) in R and G, that block's material in B; alpha 0 marks columns not known yet.</li>
 *   <li>Materials, 64 x 32 x 64 blocks: one material byte per block, four Y layers per RGBA texel,
 *   the eight tiles of four layers stacked vertically. Kept only while Block Reflection is on.</li>
 *   <li>Far columns, 512 x 512, like the columns but out to the edge of a 16 chunk view, so water far
 *   away reflects too: refreshed slowly (the whole map every three seconds), only while Block Reflection
 *   is on. Alpha holds bits 9 to 11 of the block's x and z besides the known flag, so a texel still
 *   holding the column from the other side of the window (the camera moved since it was refreshed) is
 *   told apart and not trusted.</li>
 *   <li>Snow, 128 x 128 like the columns, kept while Winter's snow lies (render/winter/SnowLayer): in R
 *   the height in sixteenths at which snow lies on the column's top block (0 where its top is not flat
 *   and whole, or is water), in G the block light above it, in B 1 for leaves and 2 for something hot that
 *   melts any snow (fire, a campfire, magma, lava, a lit furnace); alpha 0 where not known.</li>
 * </ul>
 */
public final class SurfaceMap {
	public static final byte AIR = 0;
	public static final byte SOLID = 1;
	public static final byte WATER = 2;
	/** Glass, ice, polished and smooth stone, quartz, concrete, gems: subtle reflections. */
	public static final byte GLOSSY = 3;
	public static final byte METAL = 4;
	/** Leaves and blocks without collision (plants, flowers, torches): never metal, no puddles. */
	public static final byte FOLIAGE = 5;
	public static final byte LAVA = 6;

	private static final int COLUMNS = 128;
	public static final int FAR = 512;
	/** Rows of far columns per tick (the whole map in 64 ticks), four times as many while it fills. */
	private static final int FAR_BAND = 8;
	private static final int FAR_BANDS = FAR / FAR_BAND;
	private static final int GRID = 64;
	private static final int LAYERS = 32;
	/** Rows of columns per tick (the whole map in 8 ticks); also what one upload covers. */
	private static final int BAND = 16;
	/** Material layers per tick: the whole grid in 16 ticks. */
	private static final int LAYERS_PER_TICK = 2;
	/** A camera jump further than this (teleport, respawn) forgets everything at once. */
	private static final int JUMP = 24;

	private static final String[] FOLIAGE_NAMES = {"leaves"};
	private static final String[] METAL_NAMES = {"iron_block", "gold_block", "netherite_block", "copper", "iron_bars", "iron_door",
		"iron_trapdoor", "anvil", "chain", "cauldron", "hopper", "lightning_rod", "heavy_core"};
	private static final String[] GLOSSY_NAMES = {"glass", "ice", "polished", "smooth", "quartz", "glazed_terracotta", "concrete",
		"obsidian", "amethyst", "diamond_block", "emerald_block", "lapis_block", "prismarine", "purpur", "honey_block", "slime_block"};

	private static final BlockPos.Mutable POS = new BlockPos.Mutable();
	private static final Reference2ByteOpenHashMap<BlockState> MATERIALS = new Reference2ByteOpenHashMap<>();
	private static final Reference2ByteOpenHashMap<BlockState> SNOW_TOPS = new Reference2ByteOpenHashMap<>();
	private static ByteBuffer columns;
	private static ByteBuffer snow;
	private static GpuTexture snowTexture;
	private static GpuTextureView snowView;
	/** The snow rows are refreshed and sent with the columns (Winter's snow lies). */
	private static boolean snowKept;
	private static ByteBuffer far;
	private static ByteBuffer grid;
	private static GpuTexture columnTexture;
	private static GpuTextureView columnView;
	private static GpuTexture gridTexture;
	private static GpuTextureView gridView;
	private static GpuTexture farTexture;
	private static GpuTextureView farView;
	private static ClientWorld world;
	/** False after a tick without refresh: what is kept may be outdated by then. */
	private static boolean live;
	private static int lastX;
	private static int lastY;
	private static int lastZ;
	private static int nextBand;
	private static int nextLayer;
	/** Layers refreshed since the grid was last started: it is trusted once every layer is fresh. */
	private static int freshLayers;
	private static int dirtyBands;
	private static int dirtyTiles;
	private static int nextFarBand;
	/** Far bands refreshed since the far map was last cleared (it fills faster until all are). */
	private static int farFilled;
	private static long dirtyFar;
	/** The far map holds anything (it is cleared once when Block Reflection is turned off). */
	private static boolean farKept;

	static {
		MATERIALS.defaultReturnValue((byte) -1);
		SNOW_TOPS.defaultReturnValue((byte) -1);
	}

	private SurfaceMap() {
	}

	/** Refreshes the next rows around the camera block; materials only when the grid is needed, snow when it lies. */
	public static void tick(ClientWorld w, int x, int y, int z, boolean materials, boolean snowLayer) {
		if (columns == null) {
			columns = MemoryUtil.memCalloc(COLUMNS * COLUMNS * 4);
			snow = MemoryUtil.memCalloc(COLUMNS * COLUMNS * 4);
			grid = MemoryUtil.memCalloc(GRID * GRID * LAYERS);
			far = MemoryUtil.memCalloc(FAR * FAR * 4);
		}
		if (!live || w != world || Math.abs(x - lastX) > JUMP || Math.abs(y - lastY) > JUMP || Math.abs(z - lastZ) > JUMP) {
			live = true;
			world = w;
			MemoryUtil.memSet(columns, 0);
			MemoryUtil.memSet(snow, 0);
			MemoryUtil.memSet(grid, 0);
			dirtyBands = (1 << COLUMNS / BAND) - 1;
			dirtyTiles = (1 << LAYERS / 4) - 1;
			freshLayers = 0;
			clearFar();
		}
		lastX = x;
		lastY = y;
		lastZ = z;
		if (snowLayer && !snowKept) {
			// Rows refreshed before the snow was wanted hold none: all of them are rewritten from now on.
			MemoryUtil.memSet(snow, 0);
			dirtyBands = (1 << COLUMNS / BAND) - 1;
		}
		snowKept = snowLayer;
		refreshColumns(x, z);
		if (materials) {
			refreshLayers(x, y, z);
			refreshFar(x, z);
		} else {
			freshLayers = 0;
			if (farKept) clearFar();
		}
	}

	private static void clearFar() {
		MemoryUtil.memSet(far, 0);
		dirtyFar = -1L;
		farFilled = 0;
		farKept = false;
	}

	/** A tick in which nothing needs the map: it starts from scratch when it is needed again. */
	public static void idle() {
		live = false;
		world = null;
	}

	public static boolean gridReady() {
		return freshLayers >= LAYERS;
	}

	public static GpuTextureView columns() {
		return columnView;
	}

	public static GpuTextureView grid() {
		return gridView;
	}

	public static GpuTextureView far() {
		return farView;
	}

	/** The snow texture (see the class comment); null until the first upload. */
	public static GpuTextureView snow() {
		return snowView;
	}

	/**
	 * Sends the rows refreshed since the last frame to the GPU (render thread, once per frame).
	 * Returns false until the map has been ticked since it was last idle.
	 */
	public static boolean upload() {
		if (!live) return false;
		if (columnTexture == null) {
			columnTexture = RenderSystem.getDevice().createTexture("V1zir surface columns", 15, TextureFormat.RGBA8, COLUMNS, COLUMNS, 1, 1);
			columnView = RenderSystem.getDevice().createTextureView(columnTexture);
			gridTexture = RenderSystem.getDevice().createTexture("V1zir surface materials", 15, TextureFormat.RGBA8, GRID, GRID * LAYERS / 4, 1, 1);
			gridView = RenderSystem.getDevice().createTextureView(gridTexture);
			farTexture = RenderSystem.getDevice().createTexture("V1zir surface far columns", 15, TextureFormat.RGBA8, FAR, FAR, 1, 1);
			farView = RenderSystem.getDevice().createTextureView(farTexture);
			snowTexture = RenderSystem.getDevice().createTexture("V1zir surface snow", 15, TextureFormat.RGBA8, COLUMNS, COLUMNS, 1, 1);
			snowView = RenderSystem.getDevice().createTextureView(snowTexture);
			dirtyBands = (1 << COLUMNS / BAND) - 1;
			dirtyTiles = (1 << LAYERS / 4) - 1;
			dirtyFar = -1L;
		}
		if ((dirtyBands | dirtyTiles) == 0 && dirtyFar == 0L) return true;
		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		// Each band and tile is contiguous in its buffer, so the buffer window is moved instead of slicing it.
		for (int band = 0; dirtyBands != 0; band++) {
			if ((dirtyBands & 1 << band) == 0) continue;
			dirtyBands &= ~(1 << band);
			int from = band * BAND * COLUMNS * 4;
			columns.limit(from + BAND * COLUMNS * 4).position(from);
			encoder.writeToTexture(columnTexture, columns, NativeImage.Format.RGBA, 0, 0, 0, band * BAND, COLUMNS, BAND);
			if (snowKept) {
				snow.limit(from + BAND * COLUMNS * 4).position(from);
				encoder.writeToTexture(snowTexture, snow, NativeImage.Format.RGBA, 0, 0, 0, band * BAND, COLUMNS, BAND);
			}
		}
		columns.clear();
		snow.clear();
		for (int tile = 0; dirtyTiles != 0; tile++) {
			if ((dirtyTiles & 1 << tile) == 0) continue;
			dirtyTiles &= ~(1 << tile);
			int from = tile * GRID * GRID * 4;
			grid.limit(from + GRID * GRID * 4).position(from);
			encoder.writeToTexture(gridTexture, grid, NativeImage.Format.RGBA, 0, 0, 0, tile * GRID, GRID, GRID);
		}
		grid.clear();
		for (int band = 0; dirtyFar != 0L; band++) {
			if ((dirtyFar & 1L << band) == 0L) continue;
			dirtyFar &= ~(1L << band);
			int from = band * FAR_BAND * FAR * 4;
			far.limit(from + FAR_BAND * FAR * 4).position(from);
			encoder.writeToTexture(farTexture, far, NativeImage.Format.RGBA, 0, 0, 0, band * FAR_BAND, FAR, FAR_BAND);
		}
		far.clear();
		return true;
	}

	private static void refreshColumns(int cx, int cz) {
		int bottom = world.getBottomY();
		int x0 = cx - COLUMNS / 2;
		int z0 = cz - COLUMNS / 2;
		for (int row = nextBand * BAND; row < (nextBand + 1) * BAND; row++) {
			// The z of the window that lives in this texel row.
			int z = z0 + Math.floorMod(row - z0, COLUMNS);
			for (int col = 0; col < COLUMNS; col++) {
				int x = x0 + Math.floorMod(col - x0, COLUMNS);
				int i = (row * COLUMNS + col) * 4;
				// getTopY answers the bottom of the world for chunks that are not loaded.
				int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
				if (top <= bottom) {
					columns.putInt(i, 0);
					if (snowKept) snow.putInt(i, 0);
					continue;
				}
				int height = Math.min(top - bottom, 0xFFFF);
				BlockState state = world.getBlockState(POS.set(x, top - 1, z));
				byte material = classify(state);
				columns.put(i, (byte) (height >> 8));
				columns.put(i + 1, (byte) height);
				columns.put(i + 2, material);
				columns.put(i + 3, (byte) 0xFF);
				if (snowKept) {
					byte top16 = snowTop(state, material);
					boolean hot = (top16 & HOT) != 0;
					int lies = top16 & 31;
					int light = hot ? 15 : world.getLightLevel(LightType.BLOCK, POS.set(x, top, z));
					snow.put(i, (byte) lies);
					snow.put(i + 1, (byte) (light * 17));
					snow.put(i + 2, (byte) (hot ? 2 : material == FOLIAGE ? 1 : 0));
					snow.put(i + 3, (byte) 0xFF);
				}
			}
		}
		dirtyBands |= 1 << nextBand;
		nextBand = (nextBand + 1) % (COLUMNS / BAND);
	}

	/**
	 * The next far rows around the camera, chunk by chunk along each row (one chunk lookup per sixteen columns). A
	 * column of a chunk that is not loaded is left unknown.
	 */
	private static void refreshFar(int cx, int cz) {
		farKept = true;
		int bottom = world.getBottomY();
		int x0 = cx - FAR / 2;
		int z0 = cz - FAR / 2;
		int bands = farFilled < FAR_BANDS ? 4 : 1;
		for (int n = 0; n < bands; n++) {
			int band = nextFarBand;
			nextFarBand = (nextFarBand + 1) % FAR_BANDS;
			if (farFilled < FAR_BANDS) farFilled++;
			for (int row = band * FAR_BAND; row < (band + 1) * FAR_BAND; row++) {
				int z = z0 + Math.floorMod(row - z0, FAR);
				int chunkZ = z >> 4;
				int chunkX = Integer.MIN_VALUE;
				WorldChunk chunk = null;
				for (int col = 0; col < FAR; col++) {
					int x = x0 + Math.floorMod(col - x0, FAR);
					int i = (row * FAR + col) * 4;
					if (x >> 4 != chunkX) {
						chunkX = x >> 4;
						chunk = world.isChunkLoaded(chunkX, chunkZ) ? world.getChunk(chunkX, chunkZ) : null;
					}
					int top = chunk != null ? chunk.sampleHeightmap(Heightmap.Type.MOTION_BLOCKING, x & 15, z & 15) + 1 : bottom;
					if (top <= bottom) {
						far.putInt(i, 0);
						continue;
					}
					int height = Math.min(top - bottom, 0xFFFF);
					far.put(i, (byte) (height >> 8));
					far.put(i + 1, (byte) height);
					far.put(i + 2, classify(chunk.getBlockState(POS.set(x, top - 1, z))));
					// Known, and which 512 block tile of x and z it belongs to (bits 9 to 11).
					far.put(i + 3, (byte) (0x80 | (x >> 9 & 7) << 3 | (z >> 9 & 7)));
				}
			}
			dirtyFar |= 1L << band;
		}
	}

	private static void refreshLayers(int cx, int cy, int cz) {
		int x0 = cx - GRID / 2;
		int y0 = cy - LAYERS / 2;
		int z0 = cz - GRID / 2;
		for (int n = 0; n < LAYERS_PER_TICK; n++) {
			int layer = nextLayer;
			nextLayer = (nextLayer + 1) % LAYERS;
			int y = y0 + Math.floorMod(layer - y0, LAYERS);
			int base = (layer >> 2) * GRID * GRID * 4 + (layer & 3);
			// Chunk by chunk, so each section is looked up once and empty ones are skipped whole.
			for (int chunkZ = z0 >> 4; chunkZ <= (z0 + GRID - 1) >> 4; chunkZ++) {
				int zFrom = Math.max(z0, chunkZ << 4);
				int zTo = Math.min(z0 + GRID, (chunkZ << 4) + 16);
				for (int chunkX = x0 >> 4; chunkX <= (x0 + GRID - 1) >> 4; chunkX++) {
					int xFrom = Math.max(x0, chunkX << 4);
					int xTo = Math.min(x0 + GRID, (chunkX << 4) + 16);
					ChunkSection section = section(chunkX, chunkZ, y);
					for (int z = zFrom; z < zTo; z++) {
						int row = base + (z & GRID - 1) * GRID * 4;
						for (int x = xFrom; x < xTo; x++) {
							byte material = AIR;
							if (section != null) {
								POS.set(x, y, z);
								material = classify(section.getBlockState(x & 15, y & 15, z & 15));
							}
							grid.put(row + (x & GRID - 1) * 4, material);
						}
					}
				}
			}
			dirtyTiles |= 1 << (layer >> 2);
			if (freshLayers < LAYERS) freshLayers++;
		}
	}

	private static ChunkSection section(int chunkX, int chunkZ, int y) {
		if (!world.isChunkLoaded(chunkX, chunkZ)) return null;
		WorldChunk chunk = world.getChunk(chunkX, chunkZ);
		ChunkSection[] sections = chunk.getSectionArray();
		int index = chunk.getSectionIndex(y);
		if (index < 0 || index >= sections.length) return null;
		ChunkSection section = sections[index];
		return section == null || section.isEmpty() ? null : section;
	}

	/** Material of a block state, worked out once per state (POS must hold a position of it). */
	private static byte classify(BlockState state) {
		byte material = MATERIALS.getByte(state);
		if (material < 0) {
			material = material(state);
			MATERIALS.put(state, material);
		}
		return material;
	}

	/** In snowTop's answer: the state is hot and melts any snow on it. */
	private static final int HOT = 32;

	/**
	 * Sixteenths of a block at which snow lies on top of the state (1 to 16): 0 where its top is not flat and whole
	 * (stairs, fences, plants, torches) or where there is water or lava; HOT added for fire, campfires, magma, lava and
	 * a lit furnace, on which no snow lies at all. Worked out once per state (POS must hold a position of it).
	 */
	private static byte snowTop(BlockState state, byte material) {
		byte top = SNOW_TOPS.getByte(state);
		if (top < 0) {
			top = flatTop(state, material);
			SNOW_TOPS.put(state, top);
		}
		return top;
	}

	private static byte flatTop(BlockState state, byte material) {
		if (material == LAVA || state.isIn(BlockTags.FIRE) || state.isIn(BlockTags.CAMPFIRES) || state.isOf(Blocks.MAGMA_BLOCK)
			|| state.getBlock() instanceof AbstractFurnaceBlock && state.getOrEmpty(AbstractFurnaceBlock.LIT).orElse(false)) {
			return (byte) HOT;
		}
		if (material == AIR || material == WATER) return 0;
		VoxelShape shape = state.getOutlineShape(world, POS);
		if (shape.isEmpty()) return 0;
		double max = shape.getMax(Direction.Axis.Y);
		if (max > 1.0001) return 0;
		// The whole square at the top, or the snow would hang over nothing.
		double area = 0.0;
		for (Box box : shape.getBoundingBoxes()) {
			if (box.maxY < max - 1.0e-4) continue;
			area += Math.max(0.0, Math.min(box.maxX, 1.0) - Math.max(box.minX, 0.0)) * Math.max(0.0, Math.min(box.maxZ, 1.0) - Math.max(box.minZ, 0.0));
		}
		if (area < 0.97) return 0;
		return (byte) Math.max(1, Math.min(16, Math.round(max * 16.0)));
	}

	private static byte material(BlockState state) {
		if (state.isAir()) return AIR;
		FluidState fluid = state.getFluidState();
		if (!fluid.isEmpty()) {
			if (fluid.isIn(FluidTags.LAVA)) return LAVA;
			// Waterlogged plants and slabs show the water around them.
			if (fluid.isIn(FluidTags.WATER) && !state.isOpaqueFullCube()) return WATER;
		}
		String name = Registries.BLOCK.getId(state.getBlock()).getPath();
		if (contains(name, FOLIAGE_NAMES) || state.getCollisionShape(world, POS).isEmpty()) return FOLIAGE;
		if (contains(name, METAL_NAMES)) return METAL;
		if (contains(name, GLOSSY_NAMES) && !name.endsWith("_ore") && !name.endsWith("powder")) return GLOSSY;
		return SOLID;
	}

	private static boolean contains(String name, String[] parts) {
		for (String part : parts) if (name.contains(part)) return true;
		return false;
	}
}
