package dev.v1zir.visuals.render.font;

import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/**
 * CPU side of one single channel SDF atlas page with a simple shelf packer.
 * The GPU copy lives in {@link AtlasTexture}; {@link #dirty} tells it to re-upload.
 */
public final class GlyphAtlas {
	public static final int SIZE = 1024;
	private static final int GAP = 1;

	private final ByteBuffer pixels = MemoryUtil.memCalloc(SIZE * SIZE);
	private int shelfX;
	private int shelfY;
	private int shelfHeight;
	boolean dirty = true;
	AtlasTexture texture;

	/** Returns {x, y} of the reserved area or null when the page is full. */
	public int[] allocate(int width, int height) {
		if (width > SIZE || height > SIZE) return null;
		if (shelfX + width > SIZE) {
			shelfY += shelfHeight + GAP;
			shelfX = 0;
			shelfHeight = 0;
		}
		if (shelfY + height > SIZE) return null;
		int[] pos = {shelfX, shelfY};
		shelfX += width + GAP;
		shelfHeight = Math.max(shelfHeight, height);
		return pos;
	}

	public void blit(ByteBuffer source, int x, int y, int width, int height) {
		for (int row = 0; row < height; row++) {
			MemoryUtil.memCopy(MemoryUtil.memAddress(source) + (long) row * width,
				MemoryUtil.memAddress(pixels) + (long) (y + row) * SIZE + x, width);
		}
		dirty = true;
	}

	public ByteBuffer pixels() {
		return pixels;
	}

	public boolean isDirty() {
		return dirty;
	}

	public void markClean() {
		dirty = false;
	}
}
