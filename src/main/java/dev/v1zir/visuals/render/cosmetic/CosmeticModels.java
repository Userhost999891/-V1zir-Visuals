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
		// A bow on the band at the wearer's left: two loops and their knot.
		k.paint(0xFF4A1866, SATIN);
		for (int loop = -1; loop <= 1; loop += 2) {
			k.push().translate(-4.5f, 9.7f, 0.9f + loop * 0.95f).rotateX(loop * 22f);
			k.ellipsoid(0f, 0f, 0f, 0.32f, 0.62f, 0.95f, 14, 8);
			k.pop();
		}
		k.ellipsoid(-4.62f, 9.68f, 0.9f, 0.36f, 0.48f, 0.42f, 12, 8);
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
		// Pearls along both rims of the band.
		k.paint(0xFFF6F1E6, SATIN);
		for (int i = 0; i < 32; i++) {
			double a = Math.PI * 2.0 * i / 32;
			float c = (float) Math.cos(a), sn = (float) Math.sin(a);
			k.ellipsoid(c * 5.1f, 10.86f, sn * 5.1f, 0.2f, 0.2f, 0.2f, 8, 6);
			k.ellipsoid(c * 5.06f, 8.56f, sn * 5.06f, 0.2f, 0.2f, 0.2f, 8, 6);
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
			k.push().scale(1f, 1f, 0.62f);
			k.paint(0xFF2B2627, FUR);
			k.lathe(new float[]{2.0f, 1.45f, 0.65f, 0f}, new float[]{0f, 1.7f, 3.2f, 4.0f}, 20, true);
			k.pop();
			// The pink inside of the ear on its front, and a pale tuft of fur at its base.
			k.translate(0f, 0.3f, -1.18f);
			k.paint(0xFFF2A2B6, FUR);
			k.plate(new float[]{-1.15f, 0f, 1.15f, 0f, 0.22f, 2.6f, -0.22f, 2.6f}, 0.1f);
			k.paint(0xFFEDE6E0, FUR);
			k.ellipsoid(0f, 0.55f, -0.08f, 0.75f, 0.6f, 0.22f, 12, 8);
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
		k.push().translate(-2.3f, 9.4f, -4.1f).rotateY(-28f);
		k.plate(star(5, 1.0f, 0.44f), 0.2f);
		k.pop();
		// A gold buckle at the front of the band.
		k.paint(WHITE, GOLD);
		k.box(-1.0f, 8.95f, -4.86f, 1.0f, 9.18f, -4.56f);
		k.box(-1.0f, 9.82f, -4.86f, 1.0f, 10.05f, -4.56f);
		k.box(-1.0f, 9.18f, -4.86f, -0.77f, 9.82f, -4.56f);
		k.box(0.77f, 9.18f, -4.86f, 1.0f, 9.82f, -4.56f);
		// A patch sewn on the cone, at the back.
		k.paint(0xFF4E3A66, MATTE);
		k.push().translate(0.9f, 11.6f, 3.0f).rotateX(-18f).rotateZ(8f);
		k.plate(new float[]{-0.95f, -0.8f, 0.95f, -0.85f, 1.0f, 0.75f, -0.9f, 0.8f}, 0.16f);
		k.pop();
		return k.build();
	}

	/** A soft knitted beanie fitted to the blocky head: a rounded crown, a folded cuff and a fluffy pompom. */
	static CosmeticMesh beanie() {
		MeshKit k = new MeshKit();
		// Pulled down over the top half of the head (its corners stay inside, the hat layer's too), rounding off
		// a little above it.
		k.paint(0xFFC23B2E, KNIT);
		k.roundedLoft(new float[]{4.8f, 4.82f, 4.72f, 4.35f, 3.6f, 2.45f, 1.2f, 0f},
			new float[]{0.95f, 1.0f, 1.5f, 2.0f, 2.1f, 1.7f, 1.0f, 0f},
			new float[]{6.4f, 8.6f, 9.5f, 10.4f, 11.05f, 11.45f, 11.65f, 11.72f}, 6);
		// The folded cuff: a thick rolled band over the forehead, rounded at its top and bottom edges.
		k.paint(0xFFE9E3D7, KNIT);
		k.roundedLoft(new float[]{4.78f, 5.0f, 5.12f, 5.15f, 5.12f, 5.0f, 4.8f},
			new float[]{1.0f, 1.2f, 1.3f, 1.35f, 1.3f, 1.2f, 1.0f},
			new float[]{4.45f, 4.55f, 4.85f, 5.65f, 6.45f, 6.75f, 6.85f}, 6);
		// A leather tag on the cuff at the front.
		k.paint(0xFFB98A5A, LEATHER);
		k.push().translate(0f, 5.65f, -5.2f);
		k.plate(new float[]{-1.1f, -0.55f, 1.1f, -0.55f, 1.1f, 0.55f, -1.1f, 0.55f}, 0.12f);
		k.pop();
		k.paint(0xFFF4F1EA, FUR);
		k.ellipsoid(0f, 12.7f, 0f, 1.7f, 1.55f, 1.7f, 20, 14);
		return k.build();
	}

	static CosmeticMesh horns() {
		MeshKit k = new MeshKit();
		k.paint(0xFF2B2224, BONE);
		// From the temples (the top corners of the forehead) up and out, sweeping back over the head; ridged rings
		// run round them (the material).
		for (int side = -1; side <= 1; side += 2) {
			float s = side;
			k.tube(new float[]{s * 3.0f, 7.4f, -2.5f, s * 3.5f, 9.2f, -2.4f, s * 4.4f, 10.8f, -1.6f, s * 5.3f, 11.9f, -0.1f,
				s * 5.8f, 12.4f, 1.7f, s * 5.7f, 12.2f, 3.3f, s * 5.3f, 11.6f, 4.3f}, new float[]{1.2f, 1.06f, 0.88f, 0.7f, 0.5f, 0.3f, 0f},
				20, true, false);
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

	/**
	 * A Santa hat: a thick white fur band fitted round the head (its corners inside, the hat layer's too), soft red
	 * velvet rising from it and flopping over to the wearer's left and back, a fur pompom at its tip.
	 */
	static CosmeticMesh santaHat() {
		MeshKit k = new MeshKit();
		k.paint(0xFFC8102E, VELVET);
		k.tube(new float[]{0f, 8.25f, 0f, 0f, 10.0f, 0.4f, 0.4f, 11.8f, 1.0f, 0.9f, 13.3f, 1.9f, -0.6f, 14.3f, 2.9f, -2.6f, 14.25f, 3.6f, -4.4f, 13.0f,
			3.8f, -5.4f, 11.4f, 3.6f}, new float[]{4.75f, 4.1f, 3.1f, 2.1f, 1.4f, 0.95f, 0.6f, 0.35f}, 28, false, true);
		k.paint(0xFFF5F2EC, FUR);
		k.roundedLoft(new float[]{4.85f, 5.15f, 5.28f, 5.15f, 4.85f}, new float[]{1.0f, 1.3f, 1.45f, 1.3f, 1.0f},
			new float[]{6.35f, 6.5f, 7.4f, 8.3f, 8.45f}, 6);
		// Its top, closed over the head, under the velvet.
		k.disc(4.85f, 8.45f, 24, true);
		k.ellipsoid(-5.6f, 10.6f, 3.5f, 1.3f, 1.2f, 1.3f, 18, 12);
		return k.build();
	}

	/**
	 * Over-ear headphones: a padded band arching over the head clear of its corners, cups over the ears with leather
	 * cushions, a ring of light round each and a brushed metal plate in the middle; metal sliders join them.
	 */
	static CosmeticMesh headphones() {
		MeshKit k = new MeshKit();
		// The band: up from the right cup, round the head's top corner, over the top, and down to the left one.
		float[] path = new float[17 * 3];
		int n = 0;
		for (int side = 1; side >= -1; side -= 2) {
			float[][] half = new float[9][];
			int h = 0;
			for (int i = 0; i <= 2; i++) half[h++] = new float[]{5.55f, 5.6f + i};
			for (int i = 1; i <= 5; i++) {
				double a = Math.toRadians(i / 5.0 * 90.0);
				half[h++] = new float[]{3.3f + 2.25f * (float) Math.cos(a), 7.6f + 2.25f * (float) Math.sin(a)};
			}
			half[h] = new float[]{0f, 9.85f};
			// The right half runs up to the middle; the left one back down from it (without the middle again).
			for (int i = 0; i < 9; i++) {
				int at = side > 0 ? i : 7 - i;
				if (side < 0 && i > 7) break;
				path[n++] = side * half[at][0];
				path[n++] = half[at][1];
				path[n++] = 0.3f;
			}
		}
		float[] radius = new float[17];
		java.util.Arrays.fill(radius, 0.5f);
		k.paint(0xFF25262B, PLASTIC);
		k.tube(path, radius, 12, true, true);
		// A cushion under the top of the band.
		k.paint(0xFF3B3B42, LEATHER);
		k.tube(new float[]{-3.0f, 9.3f, 0.3f, 0f, 9.38f, 0.3f, 3.0f, 9.3f, 0.3f}, new float[]{0.42f, 0.45f, 0.42f}, 10, true, true);
		for (int side = -1; side <= 1; side += 2) {
			// Sliders from the band down to the cups.
			k.paint(WHITE, SILVER);
			k.box(side * 5.55f - 0.25f, 5.0f, 0.05f, side * 5.55f + 0.25f, 6.4f, 0.55f);
			// The cup: its axis out from the side of the head.
			k.push().translate(side * 5.0f, 3.7f, 0.3f).rotateZ(side * -90f);
			k.paint(0xFF2E2F35, PLASTIC);
			k.lathe(new float[]{2.35f, 2.65f, 2.7f, 2.55f, 2.2f, 0f}, new float[]{0.0f, 0.25f, 1.1f, 1.55f, 1.75f, 1.8f}, 32, true);
			k.paint(0xFF1C1C21, LEATHER);
			k.push().translate(0f, -0.15f, 0f);
			k.torus(2.0f, 0.55f, 28, 10);
			k.pop();
			k.paint(0xFF3FE6D2, GLOW);
			k.push().translate(0f, 1.6f, 0f);
			k.torus(2.25f, 0.14f, 32, 6);
			k.pop();
			k.paint(0xFFB9BEC8, METAL);
			k.disc(1.25f, 1.84f, 24, true);
			k.pop();
		}
		return k.build();
	}

	/**
	 * A leather cowboy hat: a wide brim curling up at the sides and dipping a little at the front and back, a tall
	 * crown creased along the top, a darker band with a silver concho at the front.
	 */
	static CosmeticMesh cowboyHat() {
		MeshKit k = new MeshKit();
		k.paint(0xFF8A5A33, LEATHER);
		// The brim's top and underside, a little apart, closed at its rim and round the crown.
		for (int face = 0; face < 2; face++) {
			float off = face == 0 ? 0.18f : -0.18f;
			k.surface((u, v, out) -> {
				double a = u * Math.PI * 2.0;
				float c = (float) Math.cos(a);
				float sn = (float) Math.sin(a);
				float r = 4.75f + v * 4.5f;
				float sides = Math.abs(c);
				float curl = v * v * (2.5f * (float) Math.pow(sides, 1.6) - 0.55f * (1f - sides));
				out[0] = c * r;
				out[1] = 8.75f + curl + off;
				out[2] = sn * r * 1.06f;
			}, 72, 6, face == 0);
		}
		float[] rim = new float[73 * 3];
		for (int i = 0; i <= 72; i++) {
			double a = i / 72.0 * Math.PI * 2.0;
			float c = (float) Math.cos(a);
			float sides = Math.abs(c);
			rim[i * 3] = c * 9.25f;
			rim[i * 3 + 1] = 8.75f + 2.5f * (float) Math.pow(sides, 1.6) - 0.55f * (1f - sides);
			rim[i * 3 + 2] = (float) Math.sin(a) * 9.25f * 1.06f;
		}
		float[] rimR = new float[73];
		java.util.Arrays.fill(rimR, 0.24f);
		k.tube(rim, rimR, 8, false, false);
		// The crown: oval, rising to a crease along its top from front to back.
		float[] ry = {8.6f, 10.2f, 11.6f, 12.55f, 13.0f};
		float[] rr = {4.62f, 4.55f, 4.3f, 3.6f, 0f};
		k.surface((u, v, out) -> {
			double a = u * Math.PI * 2.0;
			float c = (float) Math.cos(a);
			float sn = (float) Math.sin(a);
			float t = v * (ry.length - 1);
			int i = Math.min((int) t, ry.length - 2);
			float f = t - i;
			float r = rr[i] + (rr[i + 1] - rr[i]) * f;
			float y = ry[i] + (ry[i + 1] - ry[i]) * f;
			float x = c * r;
			// The crease: the top sinks along the middle (x near 0), pinched at the front.
			float top = Math.max(0f, (v - 0.62f) / 0.38f);
			y -= top * 1.25f * (float) Math.exp(-(x * x) / 2.2f) * (sn < 0 ? 1.15f : 1f);
			out[0] = x;
			out[1] = y;
			out[2] = sn * r * 1.08f;
		}, 64, 12, false);
		// The band, darker, with a silver concho at the front.
		k.paint(0xFF3E2716, LEATHER);
		k.push().scale(1f, 1f, 1.08f);
		k.lathe(new float[]{4.66f, 4.7f, 4.68f, 4.6f}, new float[]{8.95f, 9.0f, 9.85f, 9.9f}, 56, false);
		k.pop();
		k.push().translate(0f, 9.42f, -5.1f).rotateX(90f);
		k.paint(WHITE, SILVER);
		k.torus(0.62f, 0.16f, 20, 6);
		k.disc(0.62f, 0.05f, 20, false);
		k.paint(0xFF2A8F9E, GEM);
		k.ellipsoid(0f, -0.12f, 0f, 0.3f, 0.18f, 0.3f, 12, 6);
		k.pop();
		return k.build();
	}

	/** Antlers growing from the top of the head: ridged beams sweeping up and out, each with three tines. */
	static CosmeticMesh antlers() {
		MeshKit k = new MeshKit();
		k.paint(0xFFCDB48E, BONE);
		for (int side = -1; side <= 1; side += 2) {
			float s = side;
			k.tube(new float[]{s * 2.4f, 8.0f, 0.2f, s * 2.9f, 9.8f, 0.4f, s * 3.9f, 11.6f, 0.9f, s * 5.2f, 13.2f, 1.3f, s * 6.3f, 14.9f, 1.1f,
				s * 6.9f, 16.6f, 0.3f, s * 7.1f, 17.9f, -0.6f}, new float[]{0.66f, 0.58f, 0.52f, 0.46f, 0.38f, 0.26f, 0f}, 10, true, false);
			k.tube(new float[]{s * 3.6f, 11.2f, 0.8f, s * 3.4f, 12.9f, -0.5f, s * 3.0f, 14.3f, -1.6f}, new float[]{0.38f, 0.25f, 0f}, 8, true,
				false);
			k.tube(new float[]{s * 5.0f, 13.0f, 1.3f, s * 4.9f, 15.0f, 0.6f, s * 4.5f, 16.4f, 0.0f}, new float[]{0.34f, 0.22f, 0f}, 8, true,
				false);
			k.tube(new float[]{s * 6.1f, 14.6f, 1.1f, s * 7.5f, 15.6f, 1.7f, s * 8.4f, 16.1f, 1.9f}, new float[]{0.3f, 0.2f, 0f}, 8, true, false);
			// A rough burr where it grows from the head.
			k.paint(0xFF8E7656, BONE);
			k.push().translate(s * 2.45f, 8.55f, 0.2f);
			k.torus(0.66f, 0.24f, 14, 6);
			k.pop();
			k.paint(0xFFCDB48E, BONE);
		}
		return k.build();
	}

	/**
	 * A propeller cap: a dome of four bright panels over the top of the head with a visor at the front and a button
	 * on top. Part 1, the propeller, spins about the stem.
	 */
	static CosmeticMesh propellerCap() {
		MeshKit k = new MeshKit();
		int[] panels = {0xFFE53935, 0xFFFDD835, 0xFF1E88E5, 0xFF43A047};
		float[] py = {6.3f, 8.0f, 9.3f, 10.25f, 10.85f, 11.1f};
		float[] ph = {4.92f, 4.95f, 4.6f, 3.75f, 2.3f, 0f};
		float[] pp = {12f, 10f, 5f, 3f, 2.4f, 2f};
		for (int q = 0; q < 4; q++) {
			int panel = q;
			k.paint(panels[q], SATIN);
			k.surface((u, v, out) -> {
				double a = Math.toRadians((panel + u) * 90.0 + 45.0);
				float t = v * (py.length - 1);
				int i = Math.min((int) t, py.length - 2);
				float f = t - i;
				float h = ph[i] + (ph[i + 1] - ph[i]) * f;
				float p = pp[i] + (pp[i + 1] - pp[i]) * f;
				float c = (float) Math.cos(a);
				float sn = (float) Math.sin(a);
				// A superellipse: square at the rim (the head's corners stay inside), round at the top.
				out[0] = Math.signum(c) * (float) Math.pow(Math.abs(c), 2.0 / p) * h;
				out[1] = py[i] + (py[i + 1] - py[i]) * f;
				out[2] = Math.signum(sn) * (float) Math.pow(Math.abs(sn), 2.0 / p) * h;
			}, 10, 10, false);
		}
		// The visor: a curved plate at the front of the rim.
		k.paint(0xFF1E88E5, PLASTIC);
		for (int face = 0; face < 2; face++) {
			float off = face == 0 ? 0.14f : -0.14f;
			k.surface((u, v, out) -> {
				float x = -4.2f + u * 8.4f;
				float depth = 3.6f * (1f - (x / 4.4f) * (x / 4.4f) * 0.55f);
				out[0] = x;
				out[1] = 6.45f + off - v * v * 0.5f;
				out[2] = -4.85f - v * depth;
			}, 16, 4, face == 0);
		}
		// The stem and its button.
		k.paint(WHITE, SILVER);
		k.tube(new float[]{0f, 10.95f, 0f, 0f, 12.1f, 0f}, new float[]{0.22f, 0.22f}, 10, false, true);
		k.paint(0xFFE53935, PLASTIC);
		k.ellipsoid(0f, 11.05f, 0f, 0.6f, 0.3f, 0.6f, 12, 6);
		k.part(0f, 0f, 0f, false);
		// The propeller: a hub and three twisted blades.
		k.paint(0xFFFDD835, PLASTIC);
		k.ellipsoid(0f, 12.25f, 0f, 0.42f, 0.32f, 0.42f, 12, 8);
		int[] blades = {0xFFE53935, 0xFF1E88E5, 0xFF43A047};
		for (int i = 0; i < 3; i++) {
			k.push().translate(0f, 12.25f, 0f).rotateY(i * 120f).rotateX(90f).rotateY(14f);
			k.paint(blades[i], PLASTIC);
			k.plate(new float[]{0.25f, 0f, 0.7f, 1.2f, 0.62f, 3.1f, 0f, 3.6f, -0.45f, 3.0f, -0.4f, 1.0f}, 0.14f);
			k.pop();
		}
		k.part(0f, 12.25f, 0f, false);
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
			// A hooked claw at the wrist and a small one at each finger's tip.
			k.paint(0xFF141012, BONE);
			k.tube(new float[]{wx + 0.8f, wy + 2.3f, 0f, wx + 1.4f, wy + 3.3f, 0.1f, wx + 2.3f, wy + 3.6f, 0.15f}, new float[]{0.3f, 0.18f, 0f}, 8, true,
				false);
			for (float[] tip : tips) {
				k.tube(new float[]{tip[0], tip[1], 0f, tip[0] + 0.35f, tip[1] - 0.8f, 0.05f, tip[0] + 0.1f, tip[1] - 1.5f, 0.1f},
					new float[]{0.16f, 0.1f, 0f}, 6, true, false);
			}
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

	/** Feathers of fire: crimson coverts, red secondaries, long swept primaries burning to gold at their tips. */
	static CosmeticMesh phoenixWings() {
		MeshKit k = new MeshKit();
		for (int side = -1; side <= 1; side += 2) {
			wingSpace(k, side);
			for (int i = 0; i < 9; i++) {
				float t = i / 8f;
				float u = 7f + t * 10.5f;
				feather(k, u, armV(u), -120f + t * 74f, 14f + t * 7.5f, 1.3f, 0.1f + i * 0.04f, 0xFFC4261A, EMBER);
			}
			for (int i = 0; i < 7; i++) {
				float t = i / 6f;
				float u = 0.6f + t * 6.8f;
				feather(k, u, armV(u), -104f + t * 12f, 11f + t * 2.5f, 1.25f, 0.45f + i * 0.03f, 0xFFA51A12, EMBER);
			}
			for (int row = 0; row < 2; row++) {
				for (int i = 0; i < 9; i++) {
					float t = i / 8f;
					float u = 0.5f + t * 15.5f;
					feather(k, u, armV(u) + 0.3f, -100f + t * 50f, row == 0 ? 6.2f : 4.2f, 1.05f, -0.25f - row * 0.18f,
						row == 0 ? 0xFF8E1410 : 0xFF6E0C0A, EMBER);
				}
			}
			k.pop();
			k.part(side * WING_X, WING_Y, WING_Z, false);
		}
		return k.build();
	}

	/**
	 * Mechanical wings: a jointed metal arm on each side with a fan of blades hanging from it, a line of light along
	 * each blade's edge; a pack on the back between them with two thrusters glowing underneath (part 2, still).
	 */
	static CosmeticMesh mechaWings() {
		MeshKit k = new MeshKit();
		for (int side = -1; side <= 1; side += 2) {
			wingSpace(k, side);
			k.paint(0xFF3B414C, METAL);
			k.tube(new float[]{0f, 0f, 0f, 4.5f, 3.6f, 0f, 9.5f, 6.2f, 0f, 13.5f, 6.8f, 0f}, new float[]{0.62f, 0.52f, 0.45f, 0.3f}, 10, true, true);
			k.paint(0xFF6E7684, METAL);
			k.ellipsoid(0f, 0f, 0f, 0.9f, 0.9f, 0.9f, 14, 10);
			k.ellipsoid(9.5f, 6.2f, 0f, 0.72f, 0.72f, 0.72f, 14, 10);
			for (int i = 0; i < 6; i++) {
				float t = i / 5f;
				float u = 1.2f + t * 11.5f;
				float v = u < 9.5f ? 6.2f * u / 9.5f * 0.95f : 6.2f + (u - 9.5f) * 0.15f;
				float length = 8.5f + t * 6.5f;
				k.push().translate(u, v - 0.2f, 0.12f * i).rotateZ(-104f + t * 40f - 90f);
				k.paint(i % 2 == 0 ? 0xFF4A5260 : 0xFF404754, METAL);
				k.plate(new float[]{0.9f, 0f, 1.05f, length * 0.7f, 0.35f, length, -0.55f, length * 0.92f, -0.85f, length * 0.3f, -0.7f, 0f}, 0.32f);
				k.paint(0xFF35E0FF, GLOW);
				k.push().translate(0.95f, 0f, 0.2f);
				k.plate(new float[]{0.1f, 0.4f, 0.16f, length * 0.68f, 0f, length * 0.72f, -0.08f, length * 0.66f, -0.06f, 0.4f}, 0.05f);
				k.pop();
				k.pop();
			}
			k.pop();
			k.part(side * WING_X, WING_Y, WING_Z, false);
		}
		// The pack on the back with its thrusters.
		k.paint(0xFF2C3139, METAL);
		k.box(-1.9f, -7.2f, 2.15f, 1.9f, -1.2f, 3.5f);
		k.paint(0xFF6E7684, METAL);
		k.box(-1.5f, -6.4f, 3.5f, 1.5f, -2.0f, 3.7f);
		for (int side = -1; side <= 1; side += 2) {
			k.push().translate(side * 1.05f, -7.2f, 2.85f).rotateX(180f);
			k.paint(0xFF4A5260, METAL);
			k.lathe(new float[]{0.55f, 0.7f, 0.62f}, new float[]{0f, 0.9f, 1.3f}, 18, false);
			k.paint(0xFF35E0FF, GLOW);
			k.disc(0.5f, 0.8f, 16, true);
			k.pop();
		}
		k.part(0f, 0f, 0f, false);
		return k.build();
	}

	/** Shards of crystal fanning out from the shoulder blades, long ones along the top, shorter below. */
	static CosmeticMesh crystalWings() {
		MeshKit k = new MeshKit();
		for (int side = -1; side <= 1; side += 2) {
			wingSpace(k, side);
			for (int i = 0; i < 7; i++) {
				float angle = 32f - i * 21f;
				float length = 15.5f - Math.abs(i - 1.5f) * 1.9f;
				float width = 1.15f + (i % 3) * 0.2f;
				k.push().translate(0.5f + i * 0.25f, 0.2f - i * 0.15f, i * 0.07f).rotateZ(angle - 90f);
				k.paint(i % 2 == 0 ? 0xFFBEEBFF : 0xFFD7CCFF, CRYSTAL);
				k.plate(shard(length, width), 0.7f);
				k.pop();
			}
			// A few small shards round the root.
			for (int i = 0; i < 3; i++) {
				k.push().translate(0.8f, -0.6f - i * 1.2f, -0.3f).rotateZ(-60f - i * 28f - 90f);
				k.paint(0xFFE6F7FF, CRYSTAL);
				k.plate(shard(4.5f - i * 0.8f, 0.8f), 0.5f);
				k.pop();
			}
			k.pop();
			k.part(side * WING_X, WING_Y, WING_Z, false);
		}
		return k.build();
	}

	/** A crystal shard pointing up +y: six-sided, its point long and sharp. x across. */
	private static float[] shard(float length, float width) {
		return new float[]{0f, -0.3f, width * 0.6f, length * 0.08f, width, length * 0.55f, 0f, length, -width, length * 0.55f, -width * 0.6f,
			length * 0.08f};
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
		feather(k, u, v, angle, length, width, z, color, FEATHER);
	}

	private static void feather(MeshKit k, float u, float v, float angle, float length, float width, float z, int color, int material) {
		k.push().translate(u, v, z).rotateZ(angle - 90f);
		k.paint(color, material);
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

	/**
	 * Cape cloth, as it hangs straight from the shoulders; CapeCloth moves it every frame. A little narrower than the
	 * shoulders and arms at the top (so arms swinging back pass beside it), flaring towards the hem.
	 */
	static final float CAPE_TOP = 0f;
	static final float CAPE_LENGTH = 16f;
	static final float CAPE_HALF_TOP = 4.5f;
	static final float CAPE_HALF_BOTTOM = 5.3f;
	static final float CAPE_Z = 2.3f;
	static final float CAPE_THICKNESS = 0.5f;

	/** Columns of the cape's grid: the cloth's points across (CapeCloth.COLS). */
	private static final int CAPE_COLS = 10;

	/** The cape's x at column c (0..CAPE_COLS) of row r: it flares from the shoulders to the hem. */
	private static float capeX(float c, float r) {
		return CapeCloth.half(r) * (2f * c / CAPE_COLS - 1f);
	}

	/**
	 * A cape: cloth in rows a pixel apart (so it bends smoothly on CapeCloth's grid), its outside in a colour running
	 * from top to bottom in its material, a lining of its own inside, a raised trim along the sides and the hem, a
	 * rolled collar along the top and a clasp at each shoulder with a stone set in it. Pattern coordinates: pixels
	 * across and down the cape.
	 */
	static CosmeticMesh cape(int top, int bottom, int material, int lining, int liningMaterial, int trim, int trimMaterial, int clasp,
							 int stone) {
		return capeKit(top, bottom, material, lining, liningMaterial, trim, trimMaterial, 0.75f, clasp, stone).build();
	}

	/** The cape's kit, for capes that add to it: as cape(), with the trim's width (pixels). */
	private static MeshKit capeKit(int top, int bottom, int material, int lining, int liningMaterial, int trim, int trimMaterial, float trimWidth,
								   int clasp, int stone) {
		MeshKit k = new MeshKit();
		int rows = (int) CAPE_LENGTH;
		float z0 = CAPE_Z;
		float z1 = CAPE_Z + CAPE_THICKNESS;
		// The outside, then the lining.
		for (int face = 0; face < 2; face++) {
			float z = face == 0 ? z1 : z0;
			float nz = face == 0 ? 1f : -1f;
			int[] grid = new int[(rows + 1) * (CAPE_COLS + 1)];
			for (int r = 0; r <= rows; r++) {
				if (face == 0) k.paint(lerpColor(top, bottom, r / (float) rows), material);
				else k.paint(lining, liningMaterial);
				for (int c = 0; c <= CAPE_COLS; c++) {
					float x = capeX(c, r);
					grid[r * (CAPE_COLS + 1) + c] = k.vertex(x, CAPE_TOP - r, z, 0f, 0f, nz, x + CAPE_HALF_BOTTOM, r);
				}
			}
			for (int r = 0; r < rows; r++) {
				for (int c = 0; c < CAPE_COLS; c++) {
					int i = r * (CAPE_COLS + 1) + c;
					k.quad(grid[i], grid[i + 1], grid[i + CAPE_COLS + 2], grid[i + CAPE_COLS + 1]);
				}
			}
		}
		k.paint(trim, trimMaterial);
		// The cloth's edges: the sides and the hem.
		for (int r = 0; r < rows; r++) {
			for (int side = -1; side <= 1; side += 2) {
				float xa = capeX(side < 0 ? 0 : CAPE_COLS, r);
				float xb = capeX(side < 0 ? 0 : CAPE_COLS, r + 1);
				int a = k.vertex(xa, CAPE_TOP - r, z0, side, 0f, 0f, 0f, r);
				int b = k.vertex(xa, CAPE_TOP - r, z1, side, 0f, 0f, 1f, r);
				int c = k.vertex(xb, CAPE_TOP - r - 1, z1, side, 0f, 0f, 1f, r + 1);
				int d = k.vertex(xb, CAPE_TOP - r - 1, z0, side, 0f, 0f, 0f, r + 1);
				k.quad(a, b, c, d);
			}
		}
		for (int c = 0; c < CAPE_COLS; c++) {
			float xa = capeX(c, rows);
			float xb = capeX(c + 1, rows);
			float y = CAPE_TOP - rows;
			k.quad(k.vertex(xa, y, z0, 0f, -1f, 0f, xa, 0f), k.vertex(xb, y, z0, 0f, -1f, 0f, xb, 0f), k.vertex(xb, y, z1, 0f, -1f, 0f, xb, 1f),
				k.vertex(xa, y, z1, 0f, -1f, 0f, xa, 1f));
		}
		// A raised trim on the outside, along both sides and the hem, a pixel wide.
		float zt = z1 + 0.07f;
		for (int side = -1; side <= 1; side += 2) {
			int prevOut = -1;
			int prevIn = -1;
			for (int r = 0; r <= rows; r++) {
				float edge = capeX(side < 0 ? 0 : CAPE_COLS, r);
				int out = k.vertex(edge, CAPE_TOP - r, zt, 0f, 0f, 1f, 0f, r);
				int in = k.vertex(edge - side * trimWidth, CAPE_TOP - r, zt, 0f, 0f, 1f, trimWidth, r);
				if (r > 0) k.quad(prevOut, out, in, prevIn);
				prevOut = out;
				prevIn = in;
			}
		}
		int prevLo = -1;
		int prevHi = -1;
		for (int c = 0; c <= CAPE_COLS; c++) {
			float x = capeX(c, rows);
			int lo = k.vertex(x, CAPE_TOP - rows, zt, 0f, 0f, 1f, x, 0f);
			float up = Math.max(0.8f, trimWidth);
			int hi = k.vertex(capeX(c, rows - up), CAPE_TOP - rows + up, zt, 0f, 0f, 1f, x, up);
			if (c > 0) k.quad(prevLo, lo, hi, prevHi);
			prevLo = lo;
			prevHi = hi;
		}
		// A rolled collar along the top.
		float[] path = new float[9 * 3];
		float[] radius = new float[9];
		for (int i = 0; i < 9; i++) {
			float t = i / 8f;
			path[i * 3] = -CAPE_HALF_TOP + 2f * CAPE_HALF_TOP * t;
			path[i * 3 + 1] = CAPE_TOP - 0.1f;
			path[i * 3 + 2] = CAPE_Z + CAPE_THICKNESS * 0.5f + 0.05f;
			radius[i] = 0.42f;
		}
		k.tube(path, radius, 10, true, true);
		// A clasp at each shoulder: a round setting with a stone.
		for (int side = -1; side <= 1; side += 2) {
			k.push().translate(side * (CAPE_HALF_TOP - 0.9f), CAPE_TOP - 0.7f, z1 + 0.12f).rotateX(90f);
			k.paint(clasp, clasp == WHITE ? GOLD : SILVER);
			k.torus(0.62f, 0.2f, 18, 8);
			k.pop();
			k.paint(stone, GEM);
			k.ellipsoid(side * (CAPE_HALF_TOP - 0.9f), CAPE_TOP - 0.7f, z1 + 0.22f, 0.45f, 0.45f, 0.26f, 14, 8);
		}
		return k;
	}

	/**
	 * The royal cape: deep red velvet lined with gold satin, edged with a broad band of ermine, an ermine mantle over
	 * the shoulders, gold clasps set with rubies.
	 */
	static CosmeticMesh royalCape() {
		MeshKit k = capeKit(0xFF9E0F2A, 0xFF6A0A1C, VELVET, 0xFFD9A441, SATIN, 0xFFF7F4EE, ERMINE, 1.5f, WHITE, 0xFFE0115F);
		// The mantle: a layer of ermine over the shoulders, lower in the middle of the back.
		float z = CAPE_Z + CAPE_THICKNESS + 0.18f;
		int rows = 5;
		int[] grid = new int[(rows + 1) * (CAPE_COLS + 1)];
		k.paint(0xFFF7F4EE, ERMINE);
		for (int r = 0; r <= rows; r++) {
			for (int c = 0; c <= CAPE_COLS; c++) {
				float across = 2f * c / CAPE_COLS - 1f;
				float depth = 3.0f + 1.1f * (float) Math.cos(across * Math.PI / 2.0);
				float y = CAPE_TOP - depth * r / rows;
				float x = (CapeCloth.half(-y) + 0.25f) * across;
				grid[r * (CAPE_COLS + 1) + c] = k.vertex(x, y, z, 0f, 0f, 1f, x + CAPE_HALF_BOTTOM, -y * 1.6f);
			}
		}
		for (int r = 0; r < rows; r++) {
			for (int c = 0; c < CAPE_COLS; c++) {
				int i = r * (CAPE_COLS + 1) + c;
				k.quad(grid[i], grid[i + 1], grid[i + CAPE_COLS + 2], grid[i + CAPE_COLS + 1]);
			}
		}
		// Its lower edge, a soft rim.
		float[] edge = new float[(CAPE_COLS + 1) * 3];
		float[] er = new float[CAPE_COLS + 1];
		for (int c = 0; c <= CAPE_COLS; c++) {
			float across = 2f * c / CAPE_COLS - 1f;
			float depth = 3.0f + 1.1f * (float) Math.cos(across * Math.PI / 2.0);
			edge[c * 3] = (CapeCloth.half(depth) + 0.25f) * across;
			edge[c * 3 + 1] = CAPE_TOP - depth;
			edge[c * 3 + 2] = z - 0.05f;
			er[c] = 0.24f;
		}
		k.tube(edge, er, 8, true, true);
		return k.build();
	}

	/** The ember cape: charcoal cloth burning at the hem, lined in dark red, edged in dark metal, embers for stones. */
	static CosmeticMesh emberCape() {
		return capeKit(0xFF231C1E, 0xFF2B1B17, FLAME, 0xFF3A0E0A, SATIN, 0xFF34363E, METAL, 0.75f, 0xFFD8DCE8, 0xFFFF7A1A).build();
	}

	/** The sakura cape: pale pink silk scattered with blossoms, lined in white silk, gold edged, pink stones. */
	static CosmeticMesh sakuraCape() {
		return capeKit(0xFFFBDDE6, 0xFFF6BCCD, PETALS, 0xFFFFF6F8, SILK, WHITE, GOLD, 0.75f, WHITE, 0xFFFF8FB5).build();
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
