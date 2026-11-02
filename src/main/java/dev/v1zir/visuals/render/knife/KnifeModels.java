package dev.v1zir.visuals.render.knife;

import static dev.v1zir.visuals.render.knife.KnifeMesh.BLADE;
import static dev.v1zir.visuals.render.knife.KnifeMesh.BEVEL;
import static dev.v1zir.visuals.render.knife.KnifeMesh.BRASS;
import static dev.v1zir.visuals.render.knife.KnifeMesh.CORD;
import static dev.v1zir.visuals.render.knife.KnifeMesh.DARK;
import static dev.v1zir.visuals.render.knife.KnifeMesh.EDGE;
import static dev.v1zir.visuals.render.knife.KnifeMesh.LEATHER;
import static dev.v1zir.visuals.render.knife.KnifeMesh.POLYMER;
import static dev.v1zir.visuals.render.knife.KnifeMesh.STEEL;
import static dev.v1zir.visuals.render.knife.KnifeMesh.WOOD;

/**
 * The knife models, generated from profile curves in centimetres (see {@link KnifeMesh} for the axes). Blades are
 * lofted between their edge and spine curves: a cross-section with a honed edge, a ground bevel up to the grind line,
 * flats with an optional fuller and a chamfered spine that turns into a swedge towards the tip, thinning towards the
 * point (distal taper) and blunt in front of the plunge line. Handles are lofted rounded cross-sections, optionally
 * split by the band of a full tang. detail scales the sampling: 1 for the view model and previews, less for knives
 * in other players' hands.
 */
final class KnifeModels {
	// Handle colours.
	private static final int G10_BLACK = 0xFF202125;
	private static final int G10_OLIVE = 0xFF3C4132;
	private static final int G10_TAN = 0xFF8A785A;
	private static final int WALNUT = 0xFF5C3B23;
	private static final int ROSEWOOD = 0xFF4B2619;
	private static final int OAK = 0xFF86623A;
	private static final int RUBBER = 0xFF1B1C1E;
	private static final int CORD_OLIVE = 0xFF4A5136;
	private static final int CORD_BLACK = 0xFF1C1C1F;
	private static final int CORD_RED = 0xFF7C2322;
	private static final int LEATHER_TAN = 0xFF7A5030;
	private static final int LEATHER_DARK = 0xFF3F2817;
	private static final int HORN = 0xFF2E2622;
	private static final int WHITE = 0xFFFFFFFF;

	private KnifeModels() {
	}

	// ------------------------------------------------------------------ blades

	/** Cross-section and grind of a blade; fractions run from the edge (0) to the spine (1) and from the base (0) to the tip (1). */
	static final class Grind {
		float thickness = 0.42f;
		/** Thickness at the tip relative to the base. */
		float taper = 0.4f;
		/** Height of the primary bevel as a fraction of the blade's width (1 = flat grind to the spine). */
		float grind = 0.42f;
		/** Width of the honed edge, cm. */
		float hone = 0.07f;
		/** Spine chamfer, cm. */
		float chamfer = 0.035f;
		/** Along the blade: where the edge bevel starts (before it the edge is a blunt ricasso). */
		float plunge0 = 0.02f;
		float plunge1 = 0.07f;
		float fullerStart = -1f;
		float fullerEnd;
		float fullerLow;
		float fullerHigh;
		float fullerDepth;
		/** Along the blade: where the spine turns into a swedge (false edge); past 1 there is none. */
		float swedge = 2f;
		/** Width the swedge reaches at the tip, as a fraction of the blade's width. */
		float swedgeWidth = 0.35f;

		Grind thickness(float t, float taper) {
			this.thickness = t;
			this.taper = taper;
			return this;
		}

		Grind grind(float g) {
			this.grind = g;
			return this;
		}

		Grind plunge(float start, float end) {
			this.plunge0 = start;
			this.plunge1 = end;
			return this;
		}

		Grind fuller(float start, float end, float low, float high, float depth) {
			fullerStart = start;
			fullerEnd = end;
			fullerLow = low;
			fullerHigh = high;
			fullerDepth = depth;
			return this;
		}

		Grind swedge(float start, float width) {
			swedge = start;
			swedgeWidth = width;
			return this;
		}
	}

	/** A straight blade: spine and edge are curves whose x only grows, meeting at the tip. */
	static void straightBlade(MeshBuilder b, Curve spine, Curve edge, Grind g, int stations) {
		float x0 = Math.min(spine.x(0), edge.x(0));
		float x1 = Math.min(spine.endX(), edge.endX());
		float[] ex = new float[stations];
		float[] ey = new float[stations];
		float[] sx = new float[stations];
		float[] sy = new float[stations];
		for (int i = 0; i < stations; i++) {
			float t = i / (float) (stations - 1);
			// Denser towards the point, where the shape changes fastest.
			float x = x0 + (x1 - x0) * (1f - (float) Math.pow(1f - t, 1.45f));
			ex[i] = x;
			ey[i] = edge.yAt(x);
			sx[i] = x;
			sy[i] = spine.yAt(x);
		}
		blade(b, ex, ey, sx, sy, g);
	}

	/** A curved blade (a karambit's claw): spine and edge matched by their arc length. */
	static void curvedBlade(MeshBuilder b, Curve spine, Curve edge, Grind g, int stations) {
		float[] ex = new float[stations];
		float[] ey = new float[stations];
		float[] sx = new float[stations];
		float[] sy = new float[stations];
		float[] p = new float[2];
		for (int i = 0; i < stations; i++) {
			float t = i / (float) (stations - 1);
			float s = 1f - (float) Math.pow(1f - t, 1.35f);
			edge.at(s, p);
			ex[i] = p[0];
			ey[i] = p[1];
			spine.at(s, p);
			sx[i] = p[0];
			sy[i] = p[1];
		}
		blade(b, ex, ey, sx, sy, g);
	}

	private static float smooth(float e0, float e1, float x) {
		float t = Math.max(0f, Math.min(1f, (x - e0) / (e1 - e0)));
		return t * t * (3f - 2f * t);
	}

	/** Lofts the blade through its stations (edge point and spine point of each). */
	private static void blade(MeshBuilder b, float[] ex, float[] ey, float[] sx, float[] sy, Grind g) {
		int stations = ex.length;
		boolean fuller = g.fullerStart >= 0f && g.fullerDepth > 0f;
		// Half cross-section from the edge to the spine; the loft runs it down the +z side and back up the -z side.
		int half = fuller ? 11 : 5;
		int points = half * 2;
		MeshBuilder.Loft l = new MeshBuilder.Loft(stations, points);
		float[] f = new float[half];
		float[] z = new float[half];
		int[] mats = new int[half];
		for (int i = 0; i < stations; i++) {
			float t = i / (float) (stations - 1);
			float dx = sx[i] - ex[i];
			float dy = sy[i] - ey[i];
			float w = Math.max((float) Math.hypot(dx, dy), 1e-4f);
			float tip = (float) Math.sqrt(Math.max(0f, Math.min(1f, (1f - t) / 0.07f)));
			float h = g.thickness * 0.5f * (1f + (g.taper - 1f) * t) * Math.max(tip, 0.04f);
			float plunge = smooth(g.plunge0, g.plunge1, t);
			float sw = smooth(g.swedge, Math.min(g.swedge + 0.18f, 1f), t);
			float chamfer = Math.min(g.chamfer, h * 0.5f);
			// Fractions across: honed edge, grind line, (fuller), shoulder, spine.
			float hone = Math.min(g.hone / w, g.grind * 0.35f);
			float grind = Math.min(g.grind, 0.97f);
			float shoulder = Math.max(1f - Math.max(chamfer / w, g.swedgeWidth * sw), grind + 0.02f);
			int k = 0;
			f[k] = 0f;
			z[k] = h * (1f - plunge) * 0.92f + 0.006f * plunge;
			mats[k++] = plunge > 0.5f ? EDGE : BLADE;
			f[k] = hone;
			z[k] = h * (1f - plunge) + Math.min(h, 0.03f) * plunge;
			mats[k++] = plunge > 0.5f ? BEVEL : BLADE;
			f[k] = grind;
			z[k] = h;
			mats[k++] = BLADE;
			if (fuller) {
				float depth = g.fullerDepth * smooth(g.fullerStart, g.fullerStart + 0.06f, t) * (1f - smooth(g.fullerEnd - 0.08f, g.fullerEnd, t));
				float lo = Math.max(g.fullerLow, grind + 0.02f);
				float hi = Math.min(g.fullerHigh, shoulder - 0.02f);
				if (hi < lo + 0.02f) {
					hi = lo + 0.02f;
					depth = 0f;
				}
				for (int j = 0; j < 6; j++) {
					float a = j / 5f;
					f[k] = lo + (hi - lo) * a;
					// A rounded groove: deepest in the middle, meeting the flat at its borders.
					z[k] = h - Math.min(depth, h * 0.45f) * (float) Math.sin(Math.PI * a);
					mats[k++] = BLADE;
				}
			}
			f[k] = shoulder;
			z[k] = h;
			mats[k++] = sw > 0.5f ? BEVEL : BLADE;
			f[k] = 1f;
			z[k] = Math.max(h - chamfer, 0.004f) * (1f - sw) + 0.012f * sw;
			mats[k] = BLADE;
			for (int j = 0; j < half; j++) {
				float px = ex[i] + dx * f[j];
				float py = ey[i] + dy * f[j];
				int col = packAlong(t, f[j]);
				l.set(i, j, px, py, z[j], px, py, mats[j], col);
				// Back down the -z side: the segment starting at mirrored point j runs towards the edge.
				int m = points - 1 - j;
				l.set(i, m, px, py, -z[j], px, py, j > 0 ? mats[j - 1] : EDGE, col);
			}
		}
		for (int j = 0; j < points; j++) l.crease[j] = true;
		if (fuller) {
			// Inside the groove the surface is smooth.
			for (int j = 4; j < 8; j++) {
				l.crease[j] = false;
				l.crease[points - 1 - j] = false;
			}
		}
		b.loft(l);
		b.cap(l, 0, BLADE, packAlong(0f, 0.5f));
	}

	/** Blade vertex colour: position along the blade in red and across it (edge to spine) in green, for the finishes. */
	private static int packAlong(float t, float across) {
		int r = Math.round(Math.max(0f, Math.min(1f, t)) * 255f);
		int g = Math.round(Math.max(0f, Math.min(1f, across)) * 255f);
		return 0xFF000000 | r << 16 | g << 8;
	}

	// ------------------------------------------------------------------ handles

	/** A handle lofted from x0 (butt) to x1 (front) between a top and a bottom outline. */
	static final class Handle {
		Curve top;
		Curve bottom;
		float x0;
		float x1;
		/** Half thickness (z) at the butt, the middle and the front. */
		float w0 = 1.1f;
		float w1 = 1.2f;
		float w2 = 1.1f;
		/** Superellipse exponent of the cross-section: 2 is an ellipse, larger is squarer. */
		float roundness = 2.6f;
		int mat = POLYMER;
		int color = G10_BLACK;
		/** Half thickness of a full tang showing between the scales (0 = none). */
		float tang;
		int tangMat = STEEL;
		/** Ridges around the handle: depth relative to the size, and their spacing in cm. */
		float ridges;
		float ridgeSpacing = 0.55f;
		/** Length of the rounded butt, 0 for a flat end. */
		float butt = 0.6f;
		/** Length of a rounded front end, 0 for a flat one. */
		float front;

		Handle(Curve top, Curve bottom, float x0, float x1) {
			this.top = top;
			this.bottom = bottom;
			this.x0 = x0;
			this.x1 = x1;
		}

		Handle width(float butt, float middle, float front) {
			w0 = butt;
			w1 = middle;
			w2 = front;
			return this;
		}

		Handle material(int mat, int color) {
			this.mat = mat;
			this.color = color;
			return this;
		}

		float halfWidth(float x) {
			float t = (x - x0) / (x1 - x0);
			float a = 1f - t;
			// Quadratic through butt, middle and front.
			return w0 * a * (2f * a - 1f) + w1 * 4f * t * a + w2 * t * (2f * t - 1f);
		}
	}

	private static float spow(float v, float e) {
		return Math.copySign((float) Math.pow(Math.abs(v), e), v);
	}

	static void handle(MeshBuilder b, Handle h, float detail) {
		int body = Math.max(10, Math.round((h.x1 - h.x0) * 3.2f * detail));
		int dome = h.butt > 0f ? Math.max(3, Math.round(6 * detail)) : 0;
		int frontDome = h.front > 0f ? Math.max(3, Math.round(6 * detail)) : 0;
		int stations = dome + body + frontDome;
		boolean tang = h.tang > 0f;
		int side = Math.max(6, Math.round(9 * detail));
		// With a tang: a flat steel band on top and bottom between the two scales.
		int points = tang ? side * 2 + 2 : side * 2;
		MeshBuilder.Loft l = new MeshBuilder.Loft(stations, points);
		for (int i = 0; i < stations; i++) {
			float x;
			float scale = 1f;
			float ref;
			if (i < dome) {
				// Quarter circle closing the butt.
				float a = (float) (Math.PI * 0.5 * (dome - i) / dome);
				x = h.x0 - h.butt * (float) Math.sin(a);
				scale = Math.max((float) Math.cos(a), 0.02f);
				ref = h.x0;
			} else if (i >= dome + body) {
				float a = (float) (Math.PI * 0.5 * (i - dome - body + 1) / frontDome);
				x = h.x1 + h.front * (float) Math.sin(a);
				scale = Math.max((float) Math.cos(a), 0.02f);
				ref = h.x1;
			} else {
				x = h.x0 + (h.x1 - h.x0) * (i - dome) / (float) (body - 1);
				ref = x;
			}
			float top = h.top.yAt(ref);
			float bottom = h.bottom.yAt(ref);
			float cy = (top + bottom) * 0.5f;
			float hy = (top - bottom) * 0.5f * scale;
			float hz = h.halfWidth(ref) * scale;
			if (h.ridges > 0f && i >= dome && i < dome + body) {
				float wave = 0.5f + 0.5f * (float) Math.cos(2.0 * Math.PI * (x - h.x0) / h.ridgeSpacing);
				float k = 1f - h.ridges * wave * smooth(h.x0, h.x0 + 0.6f, x) * (1f - smooth(h.x1 - 0.6f, h.x1, x));
				hy *= k;
				hz *= k;
			}
			float e = 2f / h.roundness;
			float tz = Math.min(h.tang * scale, hz * 0.6f);
			int k = 0;
			if (tang) {
				// +z scale from the top band round to the bottom band, then the -z scale back up.
				for (int s = 0; s < 2; s++) {
					float sign = s == 0 ? 1f : -1f;
					for (int j = 0; j <= side; j++) {
						float a = (float) (Math.PI * (0.5 - j / (double) side));
						if (s == 1) a = -a;
						float py = cy + hy * spow((float) Math.sin(a), e);
						float pz = sign * (tz + (hz - tz) * (float) Math.pow(Math.abs(Math.cos(a)), e));
						boolean band = j == side;
						l.set(i, k, x, py, pz, x, py, band ? h.tangMat : h.mat, band ? WHITE : h.color);
						k++;
					}
				}
			} else {
				for (int j = 0; j < points; j++) {
					float a = (float) (Math.PI * 2.0 * j / points);
					float py = cy + hy * spow((float) Math.sin(a), e);
					float pz = hz * spow((float) Math.cos(a), e);
					l.set(i, j, x, py, pz, x, a * (hy + hz), h.mat, h.color);
				}
			}
		}
		if (tang) {
			// The scales meet the tang in hard edges.
			l.crease[0] = true;
			l.crease[side] = true;
			l.crease[side + 1] = true;
			l.crease[points - 1] = true;
		}
		b.loft(l);
		b.cap(l, 0, h.mat, h.color);
		b.cap(l, stations - 1, h.mat, h.color);
	}

	// ------------------------------------------------------------------ parts

	/** A closed ring around (cx, cy) in the knife's plane: a finger loop or a lanyard ring, rounded-rectangle tube. */
	static void ring(MeshBuilder b, float cx, float cy, float radius, float halfRadial, float halfZ, int mat, int color, float detail) {
		int stations = Math.max(16, Math.round(36 * detail));
		int points = Math.max(8, Math.round(14 * detail));
		MeshBuilder.Loft l = new MeshBuilder.Loft(stations, points);
		l.closedStations = true;
		for (int i = 0; i < stations; i++) {
			float a = (float) (Math.PI * 2.0 * i / stations);
			float ca = (float) Math.cos(a);
			float sa = (float) Math.sin(a);
			for (int j = 0; j < points; j++) {
				float t = (float) (Math.PI * 2.0 * j / points);
				float r = radius + halfRadial * spow((float) Math.cos(t), 0.7f);
				float z = halfZ * spow((float) Math.sin(t), 0.7f);
				float px = cx + ca * r;
				float py = cy + sa * r;
				l.set(i, j, px, py, z, px, py, mat, color);
			}
		}
		b.loft(l);
	}

	static float[] roundRect(float x0, float y0, float x1, float y1, float r) {
		Curve c = Curve.start(x0 + r, y0).line(x1 - r, y0).arc(x1 - r, y0 + r, (float) (Math.PI / 2)).line(x1, y1 - r)
			.arc(x1 - r, y1 - r, (float) (Math.PI / 2)).line(x0 + r, y1).arc(x0 + r, y1 - r, (float) (Math.PI / 2))
			.line(x0, y0 + r).arc(x0 + r, y0 + r, (float) (Math.PI / 2));
		return outline(c);
	}

	static float[] circle(float cx, float cy, float r, int n) {
		float[] out = new float[n * 2];
		for (int i = 0; i < n; i++) {
			double a = Math.PI * 2.0 * i / n;
			out[i * 2] = cx + r * (float) Math.cos(a);
			out[i * 2 + 1] = cy + r * (float) Math.sin(a);
		}
		return out;
	}

	/** A closed curve as an outline, without the repeated end point and without points closer than a millimetre apart. */
	static float[] outline(Curve c) {
		float[] tmp = new float[c.size() * 2];
		int n = 0;
		for (int i = 0; i < c.size(); i++) {
			float x = c.x(i);
			float y = c.y(i);
			if (n > 0 && Math.hypot(x - tmp[(n - 1) * 2], y - tmp[(n - 1) * 2 + 1]) < 0.05) continue;
			tmp[n * 2] = x;
			tmp[n * 2 + 1] = y;
			n++;
		}
		if (n > 1 && Math.hypot(tmp[0] - tmp[(n - 1) * 2], tmp[1] - tmp[(n - 1) * 2 + 1]) < 0.05) n--;
		float[] out = new float[n * 2];
		System.arraycopy(tmp, 0, out, 0, n * 2);
		return out;
	}

	/** Screws or pins through a handle at the given points, on both faces at half thickness z. */
	static void pins(MeshBuilder b, float z, float radius, int mat, float... xy) {
		for (int i = 0; i < xy.length; i += 2) {
			b.pin(xy[i], xy[i + 1], z, 0.08f, radius, mat, WHITE);
			b.pin(xy[i], xy[i + 1], -z, -0.08f, radius, mat, WHITE);
		}
	}

	private static int stations(float detail, int full) {
		return Math.max(14, Math.round(full * detail));
	}

	// ------------------------------------------------------------------ the knives

	static KnifeMesh karambit(float detail) {
		MeshBuilder b = new MeshBuilder();
		// Handle: a gentle downward curve, finger grooves underneath, black G10 on a full tang.
		Curve top = Curve.start(-4.2f, 1.05f).cubic(-2f, 1.25f, 1.5f, 1.3f, 3.6f, 0.95f);
		Curve bottom = Curve.start(-4.2f, -0.95f).cubic(-3.2f, -1.25f, -2.2f, -1.25f, -1.4f, -0.9f)
			.cubic(-0.7f, -0.6f, 0.3f, -1.35f, 1.4f, -1.3f).cubic(2.4f, -1.25f, 3f, -1f, 3.6f, -0.95f);
		Handle h = new Handle(top, bottom, -4.2f, 3.6f).width(0.62f, 0.72f, 0.6f).material(POLYMER, G10_BLACK);
		h.tang = 0.17f;
		h.butt = 0f;
		handle(b, h, detail);
		pins(b, 0.69f, 0.2f, STEEL, -2.6f, 0.15f, 0.6f, 0.25f, 2.7f, 0f);
		// Finger ring at the butt, grown out of the tang.
		ring(b, -5.45f, 0.1f, 1.55f, 0.42f, 0.24f, STEEL, WHITE, detail);
		b.plate(new float[]{-4.3f, 1f, -4.3f, -0.9f, -3.9f, -0.9f, -3.9f, 1f}, null, -0.24f, 0.24f, 0.05f, STEEL, WHITE);
		// The claw: spine on the outside of the curve, the edge on the inside.
		Curve spine = Curve.start(3.4f, 1.0f).cubic(6.2f, 1.35f, 8.6f, 0.2f, 9.3f, -2.1f).cubic(9.6f, -3.1f, 9.4f, -4.1f, 8.9f, -5.0f);
		Curve edge = Curve.start(3.4f, -1.0f).cubic(4.9f, -1.0f, 6.3f, -1.3f, 7.0f, -2.2f).cubic(7.6f, -3f, 8.3f, -3.9f, 8.9f, -5.0f);
		Grind g = new Grind().thickness(0.46f, 0.35f).grind(0.5f).plunge(0.03f, 0.1f).swedge(0.7f, 0.3f);
		curvedBlade(b, spine, edge, g, stations(detail, 46));
		return b.build();
	}

	static KnifeMesh talon(float detail) {
		MeshBuilder b = new MeshBuilder();
		// Angular, skeletal handle: a steel frame with olive G10 inlays and a faceted ring.
		Curve top = Curve.start(-4.4f, 1.1f).line(-1.2f, 1.25f).line(3.8f, 1.05f);
		Curve bottom = Curve.start(-4.4f, -0.8f).line(-3.4f, -1.25f).line(-1.6f, -0.85f).line(0.6f, -1.45f).line(3.8f, -1.05f);
		Handle h = new Handle(top, bottom, -4.4f, 3.8f).width(0.6f, 0.7f, 0.62f).material(POLYMER, G10_OLIVE);
		h.tang = 0.2f;
		h.tangMat = DARK;
		h.roundness = 3.4f;
		h.butt = 0f;
		handle(b, h, detail);
		pins(b, 0.68f, 0.17f, DARK, -2.9f, 0.2f, -0.3f, 0.2f, 2.4f, 0f);
		float[] ring = circle(-5.7f, 0.15f, 1.95f, 8);
		float[] hole = circle(-5.7f, 0.15f, 1.15f, 8);
		b.plate(ring, new float[][]{hole}, -0.22f, 0.22f, 0.07f, DARK, WHITE);
		Curve spine = Curve.start(3.6f, 1.05f).cubic(6.8f, 1.6f, 9.6f, 0.4f, 10.4f, -2.4f).cubic(10.7f, -3.6f, 10.3f, -4.8f, 9.5f, -5.9f);
		Curve edge = Curve.start(3.6f, -1.05f).cubic(5.4f, -1.1f, 6.9f, -1.6f, 7.7f, -2.6f).cubic(8.4f, -3.6f, 9f, -4.8f, 9.5f, -5.9f);
		Grind g = new Grind().thickness(0.5f, 0.32f).grind(0.38f).plunge(0.02f, 0.08f).fuller(0.08f, 0.6f, 0.52f, 0.78f, 0.1f).swedge(0.68f, 0.32f);
		curvedBlade(b, spine, edge, g, stations(detail, 50));
		return b.build();
	}

	static KnifeMesh bayonet(float detail) {
		MeshBuilder b = new MeshBuilder();
		// Grooved black polymer grip between a steel pommel and the cross guard with its muzzle ring.
		Curve top = Curve.start(-5.6f, 1.15f).cubic(-3f, 1.3f, 2f, 1.3f, 5.6f, 1.2f);
		Curve bottom = Curve.start(-5.6f, -1.25f).cubic(-3f, -1.45f, 2f, -1.4f, 5.6f, -1.3f);
		Handle h = new Handle(top, bottom, -5.6f, 5.6f).width(1.0f, 1.12f, 1.02f).material(POLYMER, RUBBER);
		h.ridges = 0.05f;
		h.ridgeSpacing = 0.62f;
		h.roundness = 2.3f;
		h.butt = 0f;
		handle(b, h, detail);
		b.plate(roundRect(-6.6f, -1.35f, -5.5f, 1.3f, 0.35f), null, -1.02f, 1.02f, 0.18f, STEEL, WHITE);
		b.pin(-6.05f, 0.75f, 1.02f, 0.1f, 0.22f, STEEL, WHITE);
		b.plate(roundRect(5.5f, -2.3f, 6.45f, 1.9f, 0.3f), null, -0.62f, 0.62f, 0.16f, STEEL, WHITE);
		b.place.translation(5.98f, 2.55f, 0f).rotateY((float) (Math.PI / 2));
		ring(b, 0f, 0f, 0.78f, 0.22f, 0.32f, STEEL, WHITE, detail);
		b.place.identity();
		b.plate(roundRect(5.65f, 1.7f, 6.3f, 2.2f, 0.12f), null, -0.4f, 0.4f, 0.08f, STEEL, WHITE);
		Curve spine = Curve.start(6.4f, 0.95f).line(17.5f, 0.95f).cubic(20.5f, 0.95f, 22.6f, 0.55f, 23.6f, 0.0f);
		Curve edge = Curve.start(6.4f, -1.55f).line(15.5f, -1.55f).cubic(19.5f, -1.55f, 22.4f, -0.8f, 23.6f, 0.0f);
		Grind g = new Grind().thickness(0.56f, 0.4f).grind(0.4f).plunge(0.04f, 0.09f).fuller(0.05f, 0.6f, 0.52f, 0.74f, 0.13f).swedge(0.7f, 0.42f);
		straightBlade(b, spine, edge, g, stations(detail, 54));
		return b.build();
	}

	static KnifeMesh butterfly(float detail) {
		MeshBuilder b = new MeshBuilder();
		float pivot = 6.0f;
		// Safe handle (+z), fixed in the hand: a skeletonised channel with three windows.
		float[][] windows = {roundRect(-5.2f, -0.3f, -2.6f, 0.3f, 0.3f), roundRect(-2.1f, -0.3f, 0.6f, 0.3f, 0.3f), roundRect(1.1f, -0.3f, 3.8f, 0.3f, 0.3f)};
		float[] body = roundRect(-6.6f, -0.68f, pivot + 0.55f, 0.68f, 0.5f);
		b.plate(body, windows, 0.2f, 0.84f, 0.12f, STEEL, WHITE);
		b.pin(pivot, 0f, 0.84f, 0.1f, 0.3f, DARK, WHITE);
		b.pin(-6.0f, 0f, 0.84f, 0.08f, 0.22f, DARK, WHITE);
		// The latch at the butt of the safe handle.
		b.plate(roundRect(-7.2f, -0.18f, -5.9f, 0.18f, 0.12f), null, 0.22f, 0.5f, 0.05f, DARK, WHITE);
		// Blade (turns around the pivot relative to the safe handle).
		b.part(pivot, 0f);
		b.plate(roundRect(pivot - 0.55f, -1.05f, pivot + 0.9f, 1.0f, 0.45f), null, -0.17f, 0.17f, 0.04f, STEEL, WHITE);
		Curve spine = Curve.start(pivot + 0.6f, 0.92f).line(pivot + 6.6f, 0.92f).cubic(pivot + 7.8f, 0.92f, pivot + 9.0f, 0.55f, pivot + 10.0f, 0.05f);
		Curve edge = Curve.start(pivot + 0.6f, -1.05f).line(pivot + 5.0f, -1.05f).cubic(pivot + 7.6f, -1.05f, pivot + 9.2f, -0.6f, pivot + 10.0f, 0.05f);
		Grind g = new Grind().thickness(0.34f, 0.45f).grind(0.48f).plunge(0.06f, 0.12f).swedge(0.62f, 0.4f);
		straightBlade(b, spine, edge, g, stations(detail, 46));
		// Bite handle (-z): attached to the blade at the same pivot, turns relative to it.
		b.part(pivot, 0f);
		b.plate(body, windows, -0.84f, -0.2f, 0.12f, STEEL, WHITE);
		b.pin(pivot, 0f, -0.84f, -0.1f, 0.3f, DARK, WHITE);
		b.pin(-6.0f, 0f, -0.84f, -0.08f, 0.22f, DARK, WHITE);
		return b.build();
	}

	static KnifeMesh flip(float detail) {
		MeshBuilder b = new MeshBuilder();
		float pivot = 4.75f;
		// Tan G10 scales over steel liners, a big pivot screw and a pocket clip.
		Curve top = Curve.start(-6.0f, 0.95f).cubic(-3.5f, 1.15f, 1.5f, 1.2f, 5.4f, 1.05f);
		Curve bottom = Curve.start(-6.0f, -0.95f).cubic(-4.5f, -1.3f, -2.5f, -1.05f, -1.0f, -1.2f).cubic(1f, -1.4f, 3.5f, -1.25f, 5.4f, -1.05f);
		Handle h = new Handle(top, bottom, -6.0f, 5.4f).width(0.55f, 0.62f, 0.6f).material(POLYMER, G10_TAN);
		h.tang = 0.2f;
		h.roundness = 3.2f;
		h.butt = 0.35f;
		handle(b, h, detail);
		b.pin(pivot, 0.05f, 0.6f, 0.12f, 0.42f, STEEL, WHITE);
		b.pin(pivot, 0.05f, -0.6f, -0.12f, 0.42f, STEEL, WHITE);
		pins(b, 0.6f, 0.13f, DARK, -4.9f, 0.5f, -4.9f, -0.45f, 0.5f, 0.75f);
		b.plate(roundRect(-5.5f, -0.25f, 1.6f, 0.25f, 0.24f), null, -0.84f, -0.66f, 0.06f, DARK, WHITE);
		b.part(pivot, 0.05f);
		b.plate(new float[]{pivot - 0.7f, -0.4f, pivot + 0.7f, -0.95f, pivot + 0.9f, 1.0f, pivot - 0.2f, 1.05f, pivot - 0.9f, 0.45f}, null,
			-0.18f, 0.18f, 0.04f, STEEL, WHITE);
		// Flipper tab on the spine side.
		b.plate(new float[]{pivot - 0.9f, 0.8f, pivot + 0.3f, 0.9f, pivot - 0.2f, 1.75f, pivot - 0.7f, 1.7f}, null, -0.16f, 0.16f, 0.05f, STEEL, WHITE);
		Curve spine = Curve.start(pivot + 0.4f, 0.95f).cubic(pivot + 3f, 1.1f, pivot + 5.6f, 1.0f, pivot + 7.0f, 0.55f).cubic(pivot + 8f, 0.25f, pivot + 8.8f, -0.15f, pivot + 9.2f, -0.35f);
		Curve edge = Curve.start(pivot + 0.4f, -1.05f).line(pivot + 4.6f, -1.15f).cubic(pivot + 7f, -1.2f, pivot + 8.5f, -0.9f, pivot + 9.2f, -0.35f);
		Grind g = new Grind().thickness(0.38f, 0.4f).grind(0.92f).plunge(0.05f, 0.12f).swedge(0.74f, 0.3f);
		straightBlade(b, spine, edge, g, stations(detail, 48));
		b.pin(pivot + 1.55f, 0.55f, 0.15f, 0.12f, 0.16f, DARK, WHITE);
		b.pin(pivot + 1.55f, 0.55f, -0.15f, -0.12f, 0.16f, DARK, WHITE);
		return b.build();
	}

	static KnifeMesh gut(float detail) {
		MeshBuilder b = new MeshBuilder();
		// Rubber grip with finger grooves and an integral steel finger guard.
		Curve top = Curve.start(-5.2f, 1.0f).cubic(-2.5f, 1.25f, 2f, 1.25f, 4.6f, 1.05f);
		Curve bottom = Curve.start(-5.2f, -1.05f).cubic(-4f, -1.45f, -2.9f, -1.0f, -2.2f, -1.05f).cubic(-1.4f, -1.5f, -0.6f, -1.45f, 0.1f, -1.05f)
			.cubic(0.9f, -1.5f, 1.8f, -1.45f, 2.5f, -1.1f).cubic(3.2f, -1.3f, 4f, -1.2f, 4.6f, -1.05f);
		Handle h = new Handle(top, bottom, -5.2f, 4.6f).width(0.9f, 1.0f, 0.88f).material(POLYMER, RUBBER);
		h.roundness = 2.4f;
		h.butt = 0.5f;
		handle(b, h, detail);
		b.plate(new float[]{4.5f, -2.3f, 5.3f, -2.1f, 5.4f, 1.3f, 4.5f, 1.3f}, null, -0.62f, 0.62f, 0.15f, STEEL, WHITE);
		// The hook: a notch in the spine near the point, its inner curve sharpened by the swedge.
		Curve spine = Curve.start(5.3f, 1.0f).line(9.6f, 1.0f).cubic(10.1f, 1.0f, 10.4f, 0.45f, 10.8f, 0.4f).cubic(11.2f, 0.35f, 11.4f, 0.9f, 11.9f, 0.85f)
			.cubic(13.3f, 0.7f, 14.5f, 0.3f, 15.2f, -0.2f);
		Curve edge = Curve.start(5.3f, -1.35f).line(10f, -1.4f).cubic(12.8f, -1.45f, 14.6f, -0.9f, 15.2f, -0.2f);
		Grind g = new Grind().thickness(0.44f, 0.42f).grind(0.45f).plunge(0.05f, 0.11f).swedge(0.42f, 0.22f);
		straightBlade(b, spine, edge, g, stations(detail, 64));
		return b.build();
	}

	static KnifeMesh huntsman(float detail) {
		MeshBuilder b = new MeshBuilder();
		// Ridged black rubber over a full tang, a steel guard and a pommel with a lanyard hole.
		Curve top = Curve.start(-5.8f, 1.15f).cubic(-3f, 1.4f, 2.5f, 1.35f, 5.6f, 1.2f);
		Curve bottom = Curve.start(-5.8f, -1.2f).cubic(-3.8f, -1.6f, -1.5f, -1.2f, 0f, -1.35f).cubic(2f, -1.55f, 4f, -1.35f, 5.6f, -1.25f);
		Handle h = new Handle(top, bottom, -5.8f, 5.6f).width(1.0f, 1.12f, 1.0f).material(POLYMER, RUBBER);
		h.ridges = 0.045f;
		h.ridgeSpacing = 0.7f;
		h.butt = 0f;
		handle(b, h, detail);
		float[] pommel = roundRect(-7.0f, -1.3f, -5.7f, 1.25f, 0.45f);
		b.plate(pommel, new float[][]{circle(-6.45f, 0f, 0.32f, 12)}, -0.85f, 0.85f, 0.16f, DARK, WHITE);
		b.plate(new float[]{5.5f, -2.5f, 6.5f, -2.3f, 6.5f, 1.9f, 5.5f, 2.0f}, null, -0.7f, 0.7f, 0.18f, DARK, WHITE);
		// Clip point, with saw teeth standing on the spine behind the clip.
		for (int i = 0; i < 7; i++) {
			float x = 8.6f + i * 0.72f;
			b.plate(new float[]{x, 1.1f, x + 0.72f, 1.1f, x + 0.5f, 1.52f}, null, -0.24f, 0.24f, 0.06f, STEEL, WHITE);
		}
		Curve spine = Curve.start(6.5f, 1.2f).line(14.6f, 1.2f).cubic(16.4f, 1.15f, 18.6f, 0.75f, 21.4f, 0.15f);
		Curve edge = Curve.start(6.5f, -1.85f).line(15.4f, -1.95f).cubic(18.8f, -2.0f, 20.6f, -1.1f, 21.4f, 0.15f);
		Grind g = new Grind().thickness(0.6f, 0.38f).grind(0.36f).plunge(0.04f, 0.1f).fuller(0.32f, 0.62f, 0.5f, 0.7f, 0.12f).swedge(0.6f, 0.36f);
		straightBlade(b, spine, edge, g, stations(detail, 52));
		return b.build();
	}

	static KnifeMesh bowie(float detail) {
		MeshBuilder b = new MeshBuilder();
		// Stacked leather washers between brass spacers, a brass guard and pommel.
		Curve top = Curve.start(-6.2f, 1.25f).cubic(-3f, 1.45f, 2.5f, 1.4f, 5.6f, 1.2f);
		Curve bottom = Curve.start(-6.2f, -1.35f).cubic(-3f, -1.6f, 2.5f, -1.5f, 5.6f, -1.25f);
		for (int i = 0; i < 2; i++) {
			float x0 = i == 0 ? -6.2f : 0.1f;
			float x1 = i == 0 ? -0.1f : 5.6f;
			Handle h = new Handle(top, bottom, x0, x1).width(1.08f, 1.15f, 1.05f).material(LEATHER, i == 0 ? LEATHER_TAN : LEATHER_DARK);
			h.ridges = 0.035f;
			h.ridgeSpacing = 0.42f;
			h.butt = 0f;
			handle(b, h, detail);
		}
		b.plate(roundRect(-0.15f, -1.5f, 0.15f, 1.45f, 0.1f), null, -1.18f, 1.18f, 0.05f, BRASS, WHITE);
		b.plate(roundRect(-7.2f, -1.45f, -6.1f, 1.35f, 0.55f), null, -1.15f, 1.15f, 0.25f, BRASS, WHITE);
		Curve guard = Curve.start(5.5f, -2.9f).cubic(5.4f, -2.2f, 5.6f, -1.6f, 5.6f, 0.4f).cubic(5.6f, 1.8f, 5.3f, 2.4f, 5.6f, 3.0f)
			.line(6.3f, 3.0f).cubic(6.6f, 2.4f, 6.4f, 1.8f, 6.4f, 0.4f).cubic(6.4f, -1.6f, 6.6f, -2.2f, 6.3f, -2.9f);
		b.plate(outline(guard), null, -0.65f, 0.65f, 0.17f, BRASS, WHITE);
		// Big clip point: the clip is sharpened (swedge) back from the tip.
		Curve spine = Curve.start(6.4f, 1.1f).line(15.2f, 1.1f).cubic(16.2f, 1.1f, 17.0f, 0.55f, 18.2f, 0.15f).cubic(20.2f, -0.45f, 22.6f, -0.5f, 24.4f, -0.35f);
		Curve edge = Curve.start(6.4f, -1.95f).line(17.0f, -2.05f).cubic(21.0f, -2.1f, 23.4f, -1.4f, 24.4f, -0.35f);
		Grind g = new Grind().thickness(0.58f, 0.36f).grind(0.4f).plunge(0.03f, 0.08f).swedge(0.5f, 0.5f);
		straightBlade(b, spine, edge, g, stations(detail, 56));
		return b.build();
	}

	static KnifeMesh falchion(float detail) {
		MeshBuilder b = new MeshBuilder();
		float pivot = 4.9f;
		// Curved grip of black polymer with a steel bolster, the pivot behind the blade's heel.
		Curve top = Curve.start(-6.0f, 0.55f).cubic(-3.5f, 1.15f, 1.5f, 1.25f, 5.5f, 1.05f);
		Curve bottom = Curve.start(-6.0f, -1.45f).cubic(-3.5f, -1.0f, 1.5f, -1.15f, 5.5f, -1.1f);
		Handle h = new Handle(top, bottom, -6.0f, 5.5f).width(0.62f, 0.7f, 0.66f).material(POLYMER, G10_BLACK);
		h.roundness = 3.0f;
		h.butt = 0.4f;
		handle(b, h, detail);
		b.plate(roundRect(3.8f, -1.15f, 5.6f, 1.1f, 0.35f), null, -0.72f, 0.72f, 0.14f, STEEL, WHITE);
		b.pin(pivot, 0f, 0.72f, 0.1f, 0.34f, DARK, WHITE);
		b.pin(pivot, 0f, -0.72f, -0.1f, 0.34f, DARK, WHITE);
		b.part(pivot, 0f);
		// A curved blade with a convex edge and a clipped point.
		Curve spine = Curve.start(pivot + 0.5f, 1.0f).cubic(pivot + 3.5f, 1.25f, pivot + 6f, 1.0f, pivot + 7.4f, 0.6f).cubic(pivot + 8.6f, 0.25f, pivot + 9.3f, 0.6f, pivot + 10.2f, 1.25f);
		Curve edge = Curve.start(pivot + 0.5f, -1.05f).cubic(pivot + 4f, -1.4f, pivot + 7.6f, -1.2f, pivot + 9.2f, -0.2f).cubic(pivot + 9.7f, 0.2f, pivot + 10f, 0.75f, pivot + 10.2f, 1.25f);
		Grind g = new Grind().thickness(0.4f, 0.42f).grind(0.5f).plunge(0.04f, 0.1f).swedge(0.66f, 0.3f);
		straightBlade(b, spine, edge, g, stations(detail, 50));
		return b.build();
	}

	static KnifeMesh pushDagger(float detail) {
		MeshBuilder b = new MeshBuilder();
		// T-handle across the palm, wrapped in red cord; the blade comes out between the fingers.
		b.place.rotationZ((float) (Math.PI / 2));
		Curve side = Curve.start(-2.7f, 0.8f).cubic(-1.0f, 0.95f, 1.0f, 0.95f, 2.7f, 0.8f);
		Curve under = Curve.start(-2.7f, -0.8f).cubic(-1.0f, -0.95f, 1.0f, -0.95f, 2.7f, -0.8f);
		Handle h = new Handle(side, under, -2.7f, 2.7f).width(0.8f, 0.9f, 0.8f).material(KnifeMesh.CORD, CORD_RED);
		h.ridges = 0.06f;
		h.ridgeSpacing = 0.3f;
		h.roundness = 2.2f;
		h.butt = 0.45f;
		h.front = 0.45f;
		handle(b, h, detail);
		b.place.identity();
		// Neck and a double-edged spear blade.
		b.plate(new float[]{0.8f, -0.75f, 2.0f, -0.6f, 2.0f, 0.6f, 0.8f, 0.75f}, null, -0.32f, 0.32f, 0.08f, DARK, WHITE);
		Curve spine = Curve.start(1.9f, 1.15f).cubic(4.5f, 1.25f, 8f, 0.7f, 10.6f, 0f);
		Curve edge = Curve.start(1.9f, -1.15f).cubic(4.5f, -1.25f, 8f, -0.7f, 10.6f, 0f);
		Grind g = new Grind().thickness(0.5f, 0.35f).grind(0.5f).plunge(0.05f, 0.1f).swedge(0.0f, 0.5f);
		straightBlade(b, spine, edge, g, stations(detail, 40));
		return b.build();
	}

	static KnifeMesh stiletto(float detail) {
		MeshBuilder b = new MeshBuilder();
		float pivot = 5.1f;
		// Horn scales between steel bolsters, small quillons at the front.
		Curve top = Curve.start(-6.0f, 0.8f).cubic(-3f, 0.95f, 2f, 0.95f, 5.6f, 0.8f);
		Curve bottom = Curve.start(-6.0f, -0.8f).cubic(-3f, -0.95f, 2f, -0.95f, 5.6f, -0.8f);
		Handle h = new Handle(top, bottom, -5.2f, 4.4f).width(0.55f, 0.6f, 0.55f).material(WOOD, HORN);
		h.roundness = 2.8f;
		h.butt = 0f;
		handle(b, h, detail);
		b.plate(roundRect(-6.4f, -0.82f, -5.1f, 0.82f, 0.6f), null, -0.6f, 0.6f, 0.16f, STEEL, WHITE);
		b.plate(roundRect(4.3f, -0.85f, 5.8f, 0.85f, 0.25f), null, -0.6f, 0.6f, 0.14f, STEEL, WHITE);
		b.plate(new float[]{5.4f, -1.55f, 5.85f, -1.5f, 5.85f, 1.5f, 5.4f, 1.55f}, null, -0.32f, 0.32f, 0.1f, STEEL, WHITE);
		b.pin(pivot, 0f, 0.6f, 0.08f, 0.22f, STEEL, WHITE);
		b.pin(pivot, 0f, -0.6f, -0.08f, 0.22f, STEEL, WHITE);
		b.part(pivot, 0f);
		// Long slim bayonet-ground blade: a wide swedge from mid-blade and a central fuller.
		Curve spine = Curve.start(5.85f, 0.62f).line(12.0f, 0.62f).cubic(14f, 0.6f, 15.6f, 0.3f, 17.0f, -0.05f);
		Curve edge = Curve.start(5.85f, -0.78f).line(11.5f, -0.8f).cubic(14f, -0.8f, 15.8f, -0.5f, 17.0f, -0.05f);
		Grind g = new Grind().thickness(0.42f, 0.45f).grind(0.44f).plunge(0.14f, 0.2f).fuller(0.03f, 0.42f, 0.5f, 0.74f, 0.07f).swedge(0.38f, 0.42f);
		straightBlade(b, spine, edge, g, stations(detail, 50));
		return b.build();
	}

	static KnifeMesh kukri(float detail) {
		MeshBuilder b = new MeshBuilder();
		// Walnut grip with turned rings, a steel bolster and butt cap.
		Curve top = Curve.start(-6.0f, 1.1f).cubic(-3f, 1.45f, 2f, 1.35f, 5.2f, 1.25f);
		Curve bottom = Curve.start(-6.0f, -1.55f).cubic(-3.5f, -1.2f, 1f, -1.6f, 5.2f, -1.3f);
		Handle h = new Handle(top, bottom, -6.0f, 5.2f).width(1.1f, 1.2f, 1.1f).material(WOOD, WALNUT);
		h.roundness = 2.2f;
		h.butt = 0f;
		handle(b, h, detail);
		for (float x : new float[]{-2.2f, 1.6f}) b.plate(roundRect(x - 0.12f, -1.55f, x + 0.12f, 1.5f, 0.1f), null, -1.26f, 1.26f, 0.05f, BRASS, WHITE);
		b.plate(roundRect(-6.9f, -1.7f, -5.9f, 1.25f, 0.55f), null, -1.2f, 1.2f, 0.22f, STEEL, WHITE);
		b.plate(roundRect(5.1f, -1.5f, 6.4f, 1.4f, 0.4f), null, -0.98f, 0.98f, 0.18f, STEEL, WHITE);
		// Forward-bent heavy blade: the spine bends down, the belly swells, and the notch sits by the handle.
		Curve spine = Curve.start(6.3f, 1.3f).cubic(10f, 1.55f, 13f, 0.9f, 16f, -0.6f).cubic(19f, -2.1f, 22f, -3.6f, 25.8f, -5.6f);
		Curve edge = Curve.start(6.3f, -1.6f).cubic(6.7f, -2.2f, 7.1f, -2.0f, 7.6f, -2.3f).cubic(11f, -4.4f, 17f, -6.4f, 21.5f, -6.9f)
			.cubic(23.6f, -7.1f, 25.0f, -6.6f, 25.8f, -5.6f);
		Grind g = new Grind().thickness(0.8f, 0.32f).grind(0.34f).plunge(0.05f, 0.12f).fuller(0.06f, 0.4f, 0.7f, 0.86f, 0.1f).swedge(0.84f, 0.3f);
		curvedBlade(b, spine, edge, g, stations(detail, 58));
		return b.build();
	}

	static KnifeMesh skeleton(float detail) {
		MeshBuilder b = new MeshBuilder();
		// Open steel frame with a ring at the butt, black cord wrapped round its middle.
		Curve frame = Curve.start(-4.6f, -1.05f).line(5.2f, -1.1f).line(5.2f, 1.1f).line(-4.6f, 1.05f)
			.cubic(-6.3f, 1.05f, -7.9f, 1.4f, -7.9f, 0f).cubic(-7.9f, -1.4f, -6.3f, -1.05f, -4.6f, -1.05f);
		float[] slot = roundRect(-3.9f, -0.5f, 4.4f, 0.5f, 0.45f);
		float[] loop = circle(-6.5f, 0f, 0.62f, 18);
		b.plate(outline(frame), new float[][]{slot, loop}, -0.22f, 0.22f, 0.07f, STEEL, WHITE);
		Curve top = Curve.start(-3.0f, 1.18f).line(3.0f, 1.18f);
		Curve bottom = Curve.start(-3.0f, -1.2f).line(3.0f, -1.2f);
		Handle wrap = new Handle(top, bottom, -3.0f, 3.0f).width(0.45f, 0.47f, 0.45f).material(CORD, CORD_BLACK);
		wrap.ridges = 0.07f;
		wrap.ridgeSpacing = 0.32f;
		wrap.roundness = 2.2f;
		wrap.butt = 0f;
		handle(b, wrap, detail);
		Curve spine = Curve.start(5.1f, 1.08f).line(11.2f, 1.08f).cubic(13f, 1.05f, 14.3f, 0.6f, 15.4f, 0.0f);
		Curve edge = Curve.start(5.1f, -1.25f).line(10.6f, -1.3f).cubic(13.2f, -1.3f, 14.8f, -0.75f, 15.4f, 0.0f);
		Grind g = new Grind().thickness(0.44f, 0.42f).grind(0.5f).plunge(0.05f, 0.11f).swedge(0.72f, 0.34f);
		straightBlade(b, spine, edge, g, stations(detail, 46));
		return b.build();
	}

	static KnifeMesh classic(float detail) {
		MeshBuilder b = new MeshBuilder();
		// Oak scales on a full tang with brass pins, a steel guard: a plain hunter.
		Curve top = Curve.start(-5.6f, 1.1f).cubic(-3f, 1.3f, 2f, 1.3f, 4.9f, 1.15f);
		Curve bottom = Curve.start(-5.6f, -1.2f).cubic(-3.5f, -1.5f, 1f, -1.25f, 4.9f, -1.2f);
		Handle h = new Handle(top, bottom, -5.6f, 4.9f).width(0.78f, 0.86f, 0.8f).material(WOOD, OAK);
		h.tang = 0.2f;
		h.roundness = 2.7f;
		h.butt = 0.4f;
		handle(b, h, detail);
		pins(b, 0.85f, 0.2f, BRASS, -3.6f, 0f, 0f, 0f, 3.2f, 0f);
		b.plate(new float[]{4.8f, -2.1f, 5.6f, -2.0f, 5.6f, 1.8f, 4.8f, 1.9f}, null, -0.62f, 0.62f, 0.14f, STEEL, WHITE);
		Curve spine = Curve.start(5.6f, 1.05f).line(12.6f, 1.05f).cubic(14.5f, 1.0f, 16.1f, 0.5f, 17.2f, -0.1f);
		Curve edge = Curve.start(5.6f, -1.6f).line(12.2f, -1.65f).cubic(15f, -1.65f, 16.6f, -0.95f, 17.2f, -0.1f);
		Grind g = new Grind().thickness(0.48f, 0.4f).grind(0.38f).plunge(0.04f, 0.09f).swedge(0.78f, 0.3f);
		straightBlade(b, spine, edge, g, stations(detail, 48));
		return b.build();
	}
}
