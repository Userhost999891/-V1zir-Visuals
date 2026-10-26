package dev.v1zir.visuals.render;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import it.unimi.dsi.fastutil.objects.Reference2ByteOpenHashMap;
import net.minecraft.block.BlockState;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
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
	private static ByteBuffer columns;
	private static ByteBuffer grid;
	private static GpuTexture columnTexture;
	private static GpuTextureView columnView;
	private static GpuTexture gridTexture;
	private static GpuTextureView gridView;
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

	static {
		MATERIALS.defaultReturnValue((byte) -1);
	}

	private SurfaceMap() {
	}

	/** Refreshes the next rows around the camera block; materials only when the grid is needed. */
	public static void tick(ClientWorld w, int x, int y, int z, boolean materials) {
		if (columns == null) {
			columns = MemoryUtil.memCalloc(COLUMNS * COLUMNS * 4);
			grid = MemoryUtil.memCalloc(GRID * GRID * LAYERS);
		}
		if (!live || w != world || Math.abs(x - lastX) > JUMP || Math.abs(y - lastY) > JUMP || Math.abs(z - lastZ) > JUMP) {
			live = true;
			world = w;
			MemoryUtil.memSet(columns, 0);
			MemoryUtil.memSet(grid, 0);
			dirtyBands = (1 << COLUMNS / BAND) - 1;
			dirtyTiles = (1 << LAYERS / 4) - 1;
			freshLayers = 0;
		}
		lastX = x;
		lastY = y;
		lastZ = z;
		refreshColumns(x, z);
		if (materials) refreshLayers(x, y, z);
		else freshLayers = 0;
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

	/**
	 * Sends the rows refreshed since the last frame to the GPU (render thread, once per frame).
	 * Returns false while nothing has been ticked yet.
	 */
	public static boolean upload() {
		if (columns == null) return false;
		if (columnTexture == null) {
			columnTexture = RenderSystem.getDevice().createTexture("V1zir surface columns", 15, TextureFormat.RGBA8, COLUMNS, COLUMNS, 1, 1);
			columnView = RenderSystem.getDevice().createTextureView(columnTexture);
			gridTexture = RenderSystem.getDevice().createTexture("V1zir surface materials", 15, TextureFormat.RGBA8, GRID, GRID * LAYERS / 4, 1, 1);
			gridView = RenderSystem.getDevice().createTextureView(gridTexture);
			dirtyBands = (1 << COLUMNS / BAND) - 1;
			dirtyTiles = (1 << LAYERS / 4) - 1;
		}
		if ((dirtyBands | dirtyTiles) == 0) return true;
		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		// Each band and tile is contiguous in its buffer, so the buffer window is moved instead of slicing it.
		for (int band = 0; dirtyBands != 0; band++) {
			if ((dirtyBands & 1 << band) == 0) continue;
			dirtyBands &= ~(1 << band);
			int from = band * BAND * COLUMNS * 4;
			columns.limit(from + BAND * COLUMNS * 4).position(from);
			encoder.writeToTexture(columnTexture, columns, NativeImage.Format.RGBA, 0, 0, 0, band * BAND, COLUMNS, BAND);
		}
		columns.clear();
		for (int tile = 0; dirtyTiles != 0; tile++) {
			if ((dirtyTiles & 1 << tile) == 0) continue;
			dirtyTiles &= ~(1 << tile);
			int from = tile * GRID * GRID * 4;
			grid.limit(from + GRID * GRID * 4).position(from);
			encoder.writeToTexture(gridTexture, grid, NativeImage.Format.RGBA, 0, 0, 0, tile * GRID, GRID, GRID);
		}
		grid.clear();
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
					continue;
				}
				int height = Math.min(top - bottom, 0xFFFF);
				columns.put(i, (byte) (height >> 8));
				columns.put(i + 1, (byte) height);
				columns.put(i + 2, classify(world.getBlockState(POS.set(x, top - 1, z))));
				columns.put(i + 3, (byte) 0xFF);
			}
		}
		dirtyBands |= 1 << nextBand;
		nextBand = (nextBand + 1) % (COLUMNS / BAND);
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
