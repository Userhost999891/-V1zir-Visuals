package dev.v1zir.visuals.optimize;

import dev.v1zir.visuals.render.world.WorldEffectRenderer;

/**
 * Drops the costly details nobody can make out far away: sign text, banner patterns (the cloth keeps its
 * base colour), items in item frames (maps stay) and unnamed armour stands. The distances grow with zoom,
 * so zooming in on something never shows it cut.
 */
public final class DistanceCulling {
	private static final double SIGN_TEXT = 40.0;
	private static final double BANNER_PATTERNS = 48.0;
	private static final double FRAMED_ITEMS = 48.0;
	private static final double ARMOR_STANDS = 48.0;
	/** Squared camera distance of the block entity being rendered now, or -1 outside block entity rendering. */
	private static double blockEntityDistanceSq = -1.0;

	private DistanceCulling() {
	}

	/** Set by BlockEntityRenderManager.render around each block entity; sign and banner renderers read it. */
	public static void blockEntity(double distanceSq) {
		blockEntityDistanceSq = distanceSq;
	}

	public static boolean signTextHidden() {
		return beyond(blockEntityDistanceSq, SIGN_TEXT);
	}

	public static boolean bannerPatternsHidden() {
		return beyond(blockEntityDistanceSq, BANNER_PATTERNS);
	}

	public static boolean framedItemHidden(double distanceSq) {
		return beyond(distanceSq, FRAMED_ITEMS);
	}

	public static boolean armorStandHidden(double distanceSq) {
		return beyond(distanceSq, ARMOR_STANDS);
	}

	private static boolean beyond(double distanceSq, double distance) {
		if (distanceSq < 0.0 || !Optimizations.distance()) return false;
		double d = distance * WorldEffectRenderer.zoom();
		return distanceSq > d * d;
	}
}
