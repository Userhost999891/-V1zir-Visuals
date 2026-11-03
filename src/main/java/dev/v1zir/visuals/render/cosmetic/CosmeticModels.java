package dev.v1zir.visuals.render.cosmetic;

import static dev.v1zir.visuals.render.cosmetic.CosmeticMesh.*;

/**
 * The cosmetic models, built in pixels in the space of the body part they sit on (x to the wearer's right, y up,
 * -z forward). Hats: the head's space, the neck at the origin and the top of the head at y = 8 (8.5 with the hat
 * layer). Wings and capes: the body's space, the neck at the origin, the back of the torso at z = 2 and its bottom at
 * y = -12. All original designs.
 */
final class CosmeticModels {
	private static final int WHITE = 0xFFFFFFFF;

	private CosmeticModels() {
	}

	// ------------------------------------------------------------------ hats

	static CosmeticMesh topHat() {
		MeshKit k = new MeshKit();
		k.paint(0xFF1B1B20, MATTE);
		// The brim, curled up at its rim; then the crown with a slight waist and a flat top.
		k.lathe(new float[]{4.6f, 6.4f, 6.75f, 6.5f, 4.5f}, new float[]{8.3f, 8.35f, 8.6f, 8.92f, 8.88f}, 44, false);
		k.lathe(new float[]{4.35f, 4.22f, 4.42f}, new float[]{8.8f, 11.8f, 15.6f}, 44, true);
		k.lathe(new float[]{4.42f, 4.32f, 0f}, new float[]{15.6f, 15.86f, 15.92f}, 44, false);
		// A satin band with a gold buckle at the front.
		k.paint(0xFF5B1F7A, SATIN);
		k.lathe(new float[]{4.28f, 4.5f, 4.5f, 4.28f}, new float[]{8.85f, 8.85f, 10.5f, 10.5f}, 44, false);
		k.paint(WHITE, GOLD);
		k.box(-1.15f, 8.95f, -4.78f, 1.15f, 9.22f, -4.45f);
		k.box(-1.15f, 10.13f, -4.78f, 1.15f, 10.4f, -4.45f);
		k.box(-1.15f, 9.22f, -4.78f, -0.88f, 10.13f, -4.45f);
		k.box(0.88f, 9.22f, -4.78f, 1.15f, 10.13f, -4.45f);
		return k.build();
	}

	static CosmeticMesh crown() {
		MeshKit k = new MeshKit();
		// A velvet cap inside the band.
		k.paint(0xFF6E0E26, SATIN);
		k.lathe(new float[]{4.6f, 4.45f, 3.3f, 1.6f, 0f}, new float[]{8.5f, 10.3f, 11.7f, 12.3f, 12.45f}, 36, true);
		// The gold band, a closed profile with some thickness.
		k.paint(WHITE, GOLD);
		k.lathe(new float[]{4.6f, 4.98f, 5.04f, 4.64f, 4.6f}, new float[]{8.5f, 8.5f, 10.8f, 10.8f, 8.5f}, 56, false);
		// Eight points, tall and short in turn, each crowned with a gem.
		for (int i = 0; i < 8; i++) {
			boolean tall = i % 2 == 0;
			float h = tall ? 3.4f : 2.2f;
			k.push().rotateY(i * 45f).translate(0f, 10.7f, -4.82f).rotateX(-6f);
			k.paint(WHITE, GOLD);
			k.plate(new float[]{-1.25f, 0f, 1.25f, 0f, 0.5f, h * 0.55f, 0f, h, -0.5f, h * 0.55f}, 0.36f);
			k.paint(tall ? 0xFFE0115F : 0xFF2A6BE8, GEM);
			k.ellipsoid(0f, h + 0.35f, 0f, 0.44f, 0.44f, 0.44f, 12, 8);
			k.pop();
		}
		// Gems set in the band.
		for (int i = 0; i < 4; i++) {
			k.push().rotateY(i * 90f + 22.5f).translate(0f, 9.65f, -5.02f);
			k.paint(i % 2 == 0 ? 0xFF1FB86A : 0xFFE0115F, GEM);
			k.ellipsoid(0f, 0f, 0f, 0.72f, 0.56f, 0.3f, 14, 8);
			k.pop();
		}
		// An orb and cross on top.
		k.paint(WHITE, GOLD);
		k.ellipsoid(0f, 13.0f, 0f, 0.66f, 0.66f, 0.66f, 16, 10);
		k.box(-0.15f, 13.5f, -0.15f, 0.15f, 14.9f, 0.15f);
		k.box(-0.56f, 14.1f, -0.15f, 0.56f, 14.4f, 0.15f);
		return k.build();
	}

	/** Part 0: the shining ring; part 1: its soft glow, blended. Both float and turn about (0, 11.6, 0). */
	static CosmeticMesh halo() {
		MeshKit k = new MeshKit();
		k.push().translate(0f, 11.6f, 0f).rotateX(-10f);
		k.paint(0xFFFFE6A0, GLOW);
		k.torus(4.1f, 0.36f, 56, 10);
		k.pop();
		k.part(0f, 11.6f, 0f, false);
		k.push().translate(0f, 11.6f, 0f).rotateX(-10f);
		k.paint(0x46FFE3A0, GLOW);
		k.torus(4.1f, 1.05f, 56, 12);
		k.pop();
		k.part(0f, 11.6f, 0f, true);
		return k.build();
	}

	/** Parts 0 and 1: the left and the right ear, each twitching about its base. */
	static CosmeticMesh catEars() {
		MeshKit k = new MeshKit();
		for (int side = -1; side <= 1; side += 2) {
			k.push().translate(side * 2.55f, 8.25f, 0.4f).rotateZ(-side * 14f).rotateX(-8f);
			k.push().scale(1f, 1f, 0.5f);
			k.paint(0xFF2B2627, FUR);
			k.lathe(new float[]{1.85f, 1.25f, 0.5f, 0f}, new float[]{0f, 1.6f, 3.0f, 3.75f}, 18, true);
			k.pop();
			// The pink inside of the ear, on its front.
			k.translate(0f, 0.3f, -0.93f);
			k.paint(0xFFF2A2B6, MATTE);
			k.plate(new float[]{-1.05f, 0f, 1.05f, 0f, 0.2f, 2.45f, -0.2f, 2.45f}, 0.1f);
			k.pop();
			k.part(side * 2.55f, 8.25f, 0.4f, false);
		}
		return k.build();
	}

	static CosmeticMesh witchHat() {
		MeshKit k = new MeshKit();
		k.paint(0xFF2A1A3C, MATTE);
		// A wide brim drooping a little at the rim, and a cone that bends back to a crooked tip.
		k.lathe(new float[]{4.6f, 7.6f, 8.45f, 8.05f, 4.5f}, new float[]{8.4f, 8.25f, 7.95f, 8.3f, 8.75f}, 52, false);
		k.tube(new float[]{0f, 8.6f, 0f, 0f, 11.5f, 0.2f, 0.2f, 14.2f, 0.9f, 0.9f, 16.6f, 2.0f, 2.2f, 18.3f, 3.4f, 3.6f, 18.8f, 4.6f},
			new float[]{4.45f, 3.6f, 2.6f, 1.6f, 0.75f, 0f}, 36, true, false);
		// A satin band and a silver star.
		k.paint(0xFF7B4FA8, SATIN);
		k.lathe(new float[]{4.28f, 4.62f, 4.42f, 4.0f}, new float[]{8.7f, 8.76f, 10.0f, 10.06f}, 44, false);
		k.paint(WHITE, SILVER);
		k.push().translate(0f, 9.4f, -4.64f);
		k.plate(star(5, 1.0f, 0.44f), 0.2f);
		k.pop();
		return k.build();
	}

	/** A soft knitted beanie fitted to the blocky head: a rounded crown, a folded cuff and a fluffy pompom. */
	static CosmeticMesh beanie() {
		MeshKit k = new MeshKit();
		k.paint(0xFFC23B2E, KNIT);
		k.roundedLoft(new float[]{4.66f, 4.68f, 4.6f, 4.32f, 3.75f, 2.8f, 1.6f, 0f},
			new float[]{1.0f, 1.3f, 1.7f, 2.0f, 2.1f, 1.8f, 1.2f, 0f},
			new float[]{8.3f, 9.4f, 10.5f, 11.5f, 12.35f, 12.95f, 13.3f, 13.45f}, 6);
		// The folded cuff: a thick rolled band, rounded at its top and bottom edges.
		k.paint(0xFFE9E3D7, KNIT);
		k.roundedLoft(new float[]{4.6f, 4.86f, 4.98f, 5.0f, 4.98f, 4.86f, 4.62f},
			new float[]{1.0f, 1.15f, 1.25f, 1.3f, 1.25f, 1.15f, 1.0f},
			new float[]{6.35f, 6.45f, 6.75f, 7.5f, 8.25f, 8.55f, 8.65f}, 6);
		k.paint(0xFFF4F1EA, FUR);
		k.ellipsoid(0f, 14.4f, 0f, 1.85f, 1.7f, 1.85f, 20, 14);
		return k.build();
	}

	static CosmeticMesh horns() {
		MeshKit k = new MeshKit();
		k.paint(0xFF2B2224, BONE);
		for (int side = -1; side <= 1; side += 2) {
			float s = side;
			k.tube(new float[]{s * 2.7f, 8.0f, -1.6f, s * 3.2f, 9.9f, -1.5f, s * 3.9f, 11.5f, -0.6f, s * 4.5f, 12.6f, 0.9f,
				s * 4.7f, 13.1f, 2.6f, s * 4.5f, 13.0f, 3.9f}, new float[]{1.15f, 1.0f, 0.82f, 0.62f, 0.38f, 0f}, 20, true, false);
		}
		return k.build();
	}

	/** A ring of twigs hugging the head with leaves and five-petalled flowers along it. */
	static CosmeticMesh flowerCrown() {
		MeshKit k = new MeshKit();
		float half = 4.78f;
		float corner = 1.3f;
		float y = 8.45f;
		// A rounded square loop around the head, closed by repeating its first point.
		int perCorner = 5;
		float[] path = new float[(4 * (perCorner + 1) + 1) * 3];
		int n = 0;
		for (int c = 0; c < 4; c++) {
			float cx = (c == 0 || c == 3) ? half - corner : -(half - corner);
			float cz = (c < 2) ? half - corner : -(half - corner);
			float start = c * 90f;
			for (int i = 0; i <= perCorner; i++) {
				double a = Math.toRadians(start + 90.0 * i / perCorner);
				path[n++] = cx + (float) Math.cos(a) * corner;
				path[n++] = y + (float) Math.sin(a * 3.0 + c) * 0.12f;
				path[n++] = cz + (float) Math.sin(a) * corner;
			}
		}
		path[n++] = path[0];
		path[n++] = path[1];
		path[n] = path[2];
		float[] radius = new float[path.length / 3];
		java.util.Arrays.fill(radius, 0.32f);
		k.paint(0xFF4E6B2F, LEAF);
		k.tube(path, radius, 8, false, false);
		// Leaves and flowers in turn along the loop.
		int[] petals = {0xFFF6A9C8, 0xFFFFFFFF, 0xFFFFD45A, 0xFFC3A6F2};
		for (int i = 0; i < 16; i++) {
			int at = (i * (path.length / 3 - 1)) / 16;
			float px = path[at * 3];
			float py = path[at * 3 + 1];
			float pz = path[at * 3 + 2];
			float facing = (float) Math.toDegrees(Math.atan2(px, -pz));
			k.push().translate(px, py, pz).rotateY(-facing).translate(0f, 0f, -0.25f);
			if (i % 2 == 0) {
				// A flower facing out: petals round a golden middle.
				k.paint(petals[(i / 2) % petals.length], MATTE);
				for (int p = 0; p < 5; p++) {
					k.push().rotateZ(p * 72f).translate(0f, 0.62f, 0f);
					k.ellipsoid(0f, 0f, 0f, 0.42f, 0.62f, 0.16f, 10, 6);
					k.pop();
				}
				k.paint(0xFFFFC53D, MATTE);
				k.ellipsoid(0f, 0f, -0.12f, 0.32f, 0.32f, 0.2f, 10, 6);
			} else {
				k.paint(0xFF5E8A36, LEAF);
				k.rotateZ(i % 4 == 1 ? 35f : -35f);
				k.plate(leaf(1.9f, 0.62f), 0.1f);
			}
			k.pop();
		}
		return k.build();
	}

	// ------------------------------------------------------------------ wings

	/** Where a wing's root sits on the back (x for the right wing; the left one is mirrored). */
	static final float WING_X = 1.6f;
	static final float WING_Y = -3.2f;
	static final float WING_Z = 2.5f;

	/** Feathered wings: layered primaries, secondaries and coverts. Parts 0 and 1: the left and the right wing. */
	static CosmeticMesh angelWings() {
		MeshKit k = new MeshKit();
		for (int side = -1; side <= 1; side += 2) {
			wingSpace(k, side);
			// Primaries fan down from the outer arm, secondaries hang from the inner arm, coverts overlap their roots.
			for (int i = 0; i < 8; i++) {
				float t = i / 7f;
				float u = 7.5f + t * 9.5f;
				feather(k, u, armV(u), -112f + t * 62f, 13f + t * 6f, 1.35f, 0.1f + i * 0.04f, 0xFFF6F8FC);
			}
			for (int i = 0; i < 7; i++) {
				float t = i / 6f;
				float u = 0.6f + t * 7f;
				feather(k, u, armV(u), -100f + t * 10f, 11f + t * 2.5f, 1.3f, 0.45f + i * 0.03f, 0xFFF1F3F8);
			}
			for (int row = 0; row < 2; row++) {
				for (int i = 0; i < 9; i++) {
					float t = i / 8f;
					float u = 0.5f + t * 15.5f;
					float length = row == 0 ? 6.2f : 4.2f;
					feather(k, u, armV(u) + 0.3f, -100f + t * 50f, length, 1.05f, -0.25f - row * 0.18f, row == 0 ? 0xFFFAFBFE : WHITE);
				}
			}
			k.pop();
			k.part(side * WING_X, WING_Y, WING_Z, false);
		}
		return k.build();
	}

	/** Bat-like wings: a bony arm and fingers with leathery skin stretched between them. */
	static CosmeticMesh demonWings() {
		MeshKit k = new MeshKit();
		float[][] tips = {{21.5f, 9.5f}, {21.0f, 0.5f}, {16.0f, -6.5f}, {8.5f, -8.5f}};
		float wx = 8.5f;
		float wy = 6.0f;
		for (int side = -1; side <= 1; side += 2) {
			wingSpace(k, side);
			k.paint(0xFF2A1A1D, BONE);
			k.tube(new float[]{0f, 0f, 0f, 4.5f, 3.6f, 0f, wx, wy, 0f}, new float[]{0.62f, 0.5f, 0.45f}, 10, true, false);
			k.tube(new float[]{wx, wy, 0f, wx + 0.8f, wy + 2.4f, 0f}, new float[]{0.3f, 0f}, 8, false, false);
			for (float[] tip : tips) {
				float mx = wx + (tip[0] - wx) * 0.55f;
				float my = wy + (tip[1] - wy) * 0.55f + 0.6f;
				k.tube(new float[]{wx, wy, 0f, mx, my, 0f, tip[0], tip[1], 0f}, new float[]{0.36f, 0.24f, 0.06f}, 8, false, false);
			}
			// Skin between the fingers, its trailing edge scalloped towards the wrist.
			k.paint(0xFF5C1426, MEMBRANE);
			for (int i = 0; i < tips.length - 1; i++) {
				float[] a = tips[i];
				float[] b = tips[i + 1];
				float sx = wx + ((a[0] + b[0]) / 2f - wx) * 0.72f;
				float sy = wy + ((a[1] + b[1]) / 2f - wy) * 0.72f;
				k.plate(new float[]{wx, wy, a[0], a[1], sx, sy}, 0.14f);
				k.plate(new float[]{wx, wy, sx, sy, b[0], b[1]}, 0.14f);
			}
			// The last panel runs back to the root on the body.
			float[] last = tips[tips.length - 1];
			k.plate(new float[]{0f, 0f, wx, wy, last[0], last[1]}, 0.14f);
			k.plate(new float[]{0f, 0f, last[0], last[1], 2.5f, -4.5f}, 0.14f);
			k.pop();
			k.part(side * WING_X, WING_Y, WING_Z, false);
		}
		return k.build();
	}

	/** Translucent, iridescent butterfly wings: an upper and a lower lobe on each side. Blended. */
	static CosmeticMesh butterflyWings() {
		MeshKit k = new MeshKit();
		for (int side = -1; side <= 1; side += 2) {
			wingSpace(k, side);
			k.paint(0xE06B5CFF, IRIDESCENT);
			k.plate(lobe(0f, 0.5f, 14.5f, 9.5f, 0.9f), 0.12f);
			k.paint(0xE0A15CFF, IRIDESCENT);
			k.plate(lobe(0f, -0.8f, 10.5f, -9.5f, 0.75f), 0.12f);
			k.pop();
			k.part(side * WING_X, WING_Y, WING_Z, true);
		}
		return k.build();
	}

	/** Blades of light fanning out from the shoulders, energy flowing along them. Blended. */
	static CosmeticMesh energyWings() {
		MeshKit k = new MeshKit();
		for (int side = -1; side <= 1; side += 2) {
			wingSpace(k, side);
			for (int i = 0; i < 5; i++) {
				float angle = 52f - i * 24f;
				float length = 17f - Math.abs(i - 1.5f) * 2.2f;
				k.push().translate(0.5f, 0f, i * 0.08f).rotateZ(angle - 90f);
				k.paint(0xD05CE1FF, ENERGY);
				k.plate(vane(length, 1.15f), 0.18f);
				k.pop();
			}
			k.pop();
			k.part(side * WING_X, WING_Y, WING_Z, true);
		}
		return k.build();
	}

	/** Enters a wing's own space: u out from the root, v up, swept back from the shoulder blade; the left one mirrored. */
	private static void wingSpace(MeshKit k, int side) {
		k.push().translate(side * WING_X, WING_Y, WING_Z).scale(side, 1f, 1f).rotateY(-28f).rotateZ(8f);
	}

	/** Height of the wing's leading edge (the arm) at u: up to the wrist, then down to the tip. */
	private static float armV(float u) {
		return u < 8f ? 9f * (float) Math.sin(u / 8f * Math.PI / 2f) : 9f - (u - 8f) * 0.55f;
	}

	/** One feather at (u, v) of the wing pointing at angle (degrees, 0 = outwards, -90 = down), layered at depth z. */
	private static void feather(MeshKit k, float u, float v, float angle, float length, float width, float z, int color) {
		k.push().translate(u, v, z).rotateZ(angle - 90f);
		k.paint(color, FEATHER);
		k.plate(featherOutline(length, width), 0.22f);
		k.pop();
	}

	/** A feather pointing up +y from its quill at the origin: a narrow leading vane, a wider trailing one. x across. */
	private static float[] featherOutline(float length, float width) {
		return new float[]{0.18f, 0f, width * 0.7f, length * 0.18f, width * 0.95f, length * 0.55f, width * 0.6f, length * 0.86f, 0f, length,
			-width * 0.45f, length * 0.86f, -width * 0.75f, length * 0.55f, -width * 0.55f, length * 0.18f, -0.18f, 0f};
	}

	/** A rounded wing lobe from its root (x0, y0) to its far corner (x1, y1); round sets how full it is. */
	private static float[] lobe(float x0, float y0, float x1, float y1, float round) {
		int n = 22;
		float[] out = new float[n * 2];
		float cx = (x0 + x1) / 2f;
		float cy = (y0 + y1) / 2f;
		float rx = Math.abs(x1 - x0) / 2f;
		float ry = Math.abs(y1 - y0) / 2f;
		for (int i = 0; i < n; i++) {
			double a = Math.PI * 2.0 * i / n;
			float c = (float) Math.cos(a);
			float s = (float) Math.sin(a);
			// A superellipse, pinched towards the root.
			float e = 2f / (1f + round);
			float x = Math.signum(c) * (float) Math.pow(Math.abs(c), e) * rx;
			float y = Math.signum(s) * (float) Math.pow(Math.abs(s), e) * ry;
			float pinch = 0.55f + 0.45f * (x + rx) / (2f * rx);
			out[i * 2] = cx + x;
			out[i * 2 + 1] = cy + y * pinch;
		}
		return out;
	}

	/** A long tapering vane pointing up +y, x across. */
	private static float[] vane(float length, float width) {
		return new float[]{0f, 0f, width * 0.8f, length * 0.25f, width, length * 0.6f, width * 0.5f, length * 0.9f, 0f, length,
			-width * 0.25f, length * 0.85f, -width * 0.45f, length * 0.5f, -width * 0.3f, length * 0.2f};
	}

	/** A leaf pointing up +y, x across. */
	private static float[] leaf(float length, float width) {
		return new float[]{0f, 0f, width * 0.8f, length * 0.3f, width, length * 0.55f, width * 0.55f, length * 0.85f, 0f, length,
			-width * 0.55f, length * 0.85f, -width, length * 0.55f, -width * 0.8f, length * 0.3f};
	}

	/** A star outline in the xy plane. */
	private static float[] star(int points, float outer, float inner) {
		float[] out = new float[points * 4];
		for (int i = 0; i < points * 2; i++) {
			double a = Math.PI / 2.0 + Math.PI * i / points;
			float r = i % 2 == 0 ? outer : inner;
			out[i * 2] = (float) Math.cos(a) * r;
			out[i * 2 + 1] = (float) Math.sin(a) * r;
		}
		return out;
	}

	// ------------------------------------------------------------------ capes

	/** Cape cloth: CapeRenderer bends it along its length every frame; here it hangs straight from the shoulders. */
	static final float CAPE_TOP = 0f;
	static final float CAPE_LENGTH = 16f;
	static final float CAPE_HALF_WIDTH = 5f;
	static final float CAPE_Z = 2.3f;
	static final float CAPE_THICKNESS = 0.6f;

	/**
	 * A sheet of cloth in rows a pixel apart (so it can bend smoothly) with a thin border; the colour runs from top
	 * to bottom (top, bottom). Pattern coordinates: pixels across and down the cape.
	 */
	static CosmeticMesh cape(int top, int bottom, int material, int trim) {
		MeshKit k = new MeshKit();
		int rows = (int) CAPE_LENGTH;
		int cols = 10;
		float z0 = CAPE_Z;
		float z1 = CAPE_Z + CAPE_THICKNESS;
		for (int face = 0; face < 2; face++) {
			float z = face == 0 ? z1 : z0;
			float nz = face == 0 ? 1f : -1f;
			int[] grid = new int[(rows + 1) * (cols + 1)];
			for (int r = 0; r <= rows; r++) {
				float y = CAPE_TOP - r;
				k.paint(lerpColor(top, bottom, r / (float) rows), material);
				for (int c = 0; c <= cols; c++) {
					float x = -CAPE_HALF_WIDTH + c;
					grid[r * (cols + 1) + c] = k.vertex(x, y, z, 0f, 0f, nz, x + CAPE_HALF_WIDTH, r);
				}
			}
			for (int r = 0; r < rows; r++) {
				for (int c = 0; c < cols; c++) {
					k.quad(grid[r * (cols + 1) + c], grid[r * (cols + 1) + c + 1], grid[(r + 1) * (cols + 1) + c + 1], grid[(r + 1) * (cols + 1) + c]);
				}
			}
		}
		// Edges: sides and the hem, in the trim colour.
		k.paint(trim, trim == WHITE ? GOLD : SATIN);
		for (int r = 0; r < rows; r++) {
			float ya = CAPE_TOP - r;
			float yb = ya - 1f;
			for (int side = -1; side <= 1; side += 2) {
				float x = side * CAPE_HALF_WIDTH;
				int a = k.vertex(x, ya, z0, side, 0f, 0f, 0f, r);
				int b = k.vertex(x, ya, z1, side, 0f, 0f, 1f, r);
				int c = k.vertex(x, yb, z1, side, 0f, 0f, 1f, r + 1);
				int d = k.vertex(x, yb, z0, side, 0f, 0f, 0f, r + 1);
				k.quad(a, b, c, d);
			}
		}
		float yb = CAPE_TOP - CAPE_LENGTH;
		for (int c = 0; c < cols; c++) {
			float xa = -CAPE_HALF_WIDTH + c;
			int a = k.vertex(xa, yb, z0, 0f, -1f, 0f, xa, 0f);
			int b = k.vertex(xa + 1f, yb, z0, 0f, -1f, 0f, xa + 1f, 0f);
			int cc = k.vertex(xa + 1f, yb, z1, 0f, -1f, 0f, xa + 1f, 1f);
			int d = k.vertex(xa, yb, z1, 0f, -1f, 0f, xa, 1f);
			k.quad(a, b, cc, d);
		}
		return k.build();
	}

	private static int lerpColor(int a, int b, float t) {
		int r = 0;
		for (int shift = 0; shift <= 24; shift += 8) {
			int ca = (a >>> shift) & 255;
			int cb = (b >>> shift) & 255;
			r |= Math.round(ca + (cb - ca) * t) << shift;
		}
		return r;
	}
}
