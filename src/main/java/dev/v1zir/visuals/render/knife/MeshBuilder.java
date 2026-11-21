package dev.v1zir.visuals.render.knife;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Builds a {@link KnifeMesh} out of lofts (rings of points joined into a surface, with hard or smooth edges
 * between ring segments), bevelled plates (outlines with holes, extruded and chamfered) and caps. Winding is
 * derived from the normals, so every primitive comes out facing outwards for back-face culling.
 */
final class MeshBuilder {
	private static final float EPS = 1e-7f;
	/** Plate corners turning more than this (radians) get a hard edge. */
	private static final float CREASE_ANGLE = 0.6f;

	private float[] data = new float[KnifeMesh.FLOATS * 4096];
	private int[] material = new int[4096];
	private int[] color = new int[4096];
	private int[] indices = new int[16384];
	private int vertexCount;
	private int indexCount;
	private final List<KnifeMesh.Part> parts = new ArrayList<>();
	private int partStart;
	private float pivotX;
	private float pivotY;
	/** Rigid placement of what is added next (a handle stood upright, a ring at the butt). */
	final Matrix4f place = new Matrix4f();
	private final Vector3f p = new Vector3f();
	private final Vector3f n = new Vector3f();

	/** Starts a part that turns around (pivotX, pivotY); what was added before becomes the previous part. */
	void part(float pivotX, float pivotY) {
		closePart();
		this.pivotX = pivotX;
		this.pivotY = pivotY;
	}

	private void closePart() {
		if (indexCount > partStart) parts.add(new KnifeMesh.Part(partStart, indexCount - partStart, pivotX, pivotY));
		partStart = indexCount;
	}

	KnifeMesh build() {
		closePart();
		return new KnifeMesh(Arrays.copyOf(data, vertexCount * KnifeMesh.FLOATS), Arrays.copyOf(material, vertexCount),
			Arrays.copyOf(color, vertexCount), Arrays.copyOf(indices, indexCount), parts.toArray(new KnifeMesh.Part[0]));
	}

	int vertex(float x, float y, float z, float nx, float ny, float nz, float u, float v, int mat, int argb) {
		if (vertexCount == material.length) {
			int size = vertexCount * 2;
			data = Arrays.copyOf(data, size * KnifeMesh.FLOATS);
			material = Arrays.copyOf(material, size);
			color = Arrays.copyOf(color, size);
		}
		place.transformPosition(x, y, z, p);
		place.transformDirection(nx, ny, nz, n);
		float len = n.length();
		if (len > EPS) n.div(len);
		int o = vertexCount * KnifeMesh.FLOATS;
		data[o] = p.x;
		data[o + 1] = p.y;
		data[o + 2] = p.z;
		data[o + 3] = n.x;
		data[o + 4] = n.y;
		data[o + 5] = n.z;
		data[o + 6] = u;
		data[o + 7] = v;
		material[vertexCount] = mat;
		color[vertexCount] = argb;
		return vertexCount++;
	}

	void triangle(int a, int b, int c) {
		if (indexCount + 3 > indices.length) indices = Arrays.copyOf(indices, indices.length * 2);
		indices[indexCount++] = a;
		indices[indexCount++] = b;
		indices[indexCount++] = c;
	}

	/** Adds the triangle wound so that it faces the way its vertex normals point; slivers are dropped. */
	private void facing(int a, int b, int c) {
		int oa = a * KnifeMesh.FLOATS;
		int ob = b * KnifeMesh.FLOATS;
		int oc = c * KnifeMesh.FLOATS;
		float ux = data[ob] - data[oa];
		float uy = data[ob + 1] - data[oa + 1];
		float uz = data[ob + 2] - data[oa + 2];
		float vx = data[oc] - data[oa];
		float vy = data[oc + 1] - data[oa + 1];
		float vz = data[oc + 2] - data[oa + 2];
		float gx = uy * vz - uz * vy;
		float gy = uz * vx - ux * vz;
		float gz = ux * vy - uy * vx;
		float area = gx * gx + gy * gy + gz * gz;
		if (area < 1e-12f) return;
		float nx = data[oa + 3] + data[ob + 3] + data[oc + 3];
		float ny = data[oa + 4] + data[ob + 4] + data[oc + 4];
		float nz = data[oa + 5] + data[ob + 5] + data[oc + 5];
		if (gx * nx + gy * ny + gz * nz >= 0f) triangle(a, b, c);
		else triangle(a, c, b);
	}

	// ------------------------------------------------------------------ lofts

	/** Rings of points (stations) joined into a surface; set() fills one point, the arrays are station-major. */
	static final class Loft {
		final int stations;
		final int points;
		final float[] x;
		final float[] y;
		final float[] z;
		final float[] u;
		final float[] v;
		/** Material of the segment that starts at the point. */
		final int[] mat;
		final int[] col;
		/** Hard edge where the two segments meeting at a point join; smooth otherwise. */
		final boolean[] crease;
		/** The last point joins back to the first (a closed cross-section). */
		boolean closedRing = true;
		/** The last station joins back to the first (a ring such as a karambit's finger loop). */
		boolean closedStations;

		Loft(int stations, int points) {
			this.stations = stations;
			this.points = points;
			int size = stations * points;
			x = new float[size];
			y = new float[size];
			z = new float[size];
			u = new float[size];
			v = new float[size];
			mat = new int[size];
			col = new int[size];
			crease = new boolean[points];
		}

		void set(int station, int point, float px, float py, float pz, float pu, float pv, int material, int argb) {
			int i = station * points + point;
			x[i] = px;
			y[i] = py;
			z[i] = pz;
			u[i] = pu;
			v[i] = pv;
			mat[i] = material;
			col[i] = argb;
		}
	}

	void loft(Loft l) {
		int s = l.stations;
		int pts = l.points;
		int segs = l.closedRing ? pts : pts - 1;
		float[] sn = new float[s * segs * 3];
		boolean[] valid = new boolean[s * segs];
		float[] cx = new float[s];
		float[] cy = new float[s];
		float[] cz = new float[s];
		for (int i = 0; i < s; i++) {
			for (int k = 0; k < pts; k++) {
				cx[i] += l.x[i * pts + k];
				cy[i] += l.y[i * pts + k];
				cz[i] += l.z[i * pts + k];
			}
			cx[i] /= pts;
			cy[i] /= pts;
			cz[i] /= pts;
		}
		// Segment normals from the tangent along the loft and the segment itself; outward is settled by a vote.
		float vote = 0f;
		for (int i = 0; i < s; i++) {
			int i0 = l.closedStations ? (i - 1 + s) % s : Math.max(i - 1, 0);
			int i1 = l.closedStations ? (i + 1) % s : Math.min(i + 1, s - 1);
			for (int k = 0; k < segs; k++) {
				int k1 = (k + 1) % pts;
				float lx = l.x[i1 * pts + k] + l.x[i1 * pts + k1] - l.x[i0 * pts + k] - l.x[i0 * pts + k1];
				float ly = l.y[i1 * pts + k] + l.y[i1 * pts + k1] - l.y[i0 * pts + k] - l.y[i0 * pts + k1];
				float lz = l.z[i1 * pts + k] + l.z[i1 * pts + k1] - l.z[i0 * pts + k] - l.z[i0 * pts + k1];
				float ax = l.x[i * pts + k1] - l.x[i * pts + k];
				float ay = l.y[i * pts + k1] - l.y[i * pts + k];
				float az = l.z[i * pts + k1] - l.z[i * pts + k];
				float nx = ly * az - lz * ay;
				float ny = lz * ax - lx * az;
				float nz = lx * ay - ly * ax;
				float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
				int o = (i * segs + k) * 3;
				if (len > 1e-6f) {
					sn[o] = nx / len;
					sn[o + 1] = ny / len;
					sn[o + 2] = nz / len;
					valid[i * segs + k] = true;
					float mx = (l.x[i * pts + k] + l.x[i * pts + k1]) * 0.5f - cx[i];
					float my = (l.y[i * pts + k] + l.y[i * pts + k1]) * 0.5f - cy[i];
					float mz = (l.z[i * pts + k] + l.z[i * pts + k1]) * 0.5f - cz[i];
					vote += Math.signum(sn[o] * mx + sn[o + 1] * my + sn[o + 2] * mz);
				}
			}
		}
		if (vote < 0f) for (int i = 0; i < sn.length; i++) sn[i] = -sn[i];
		// Degenerate segments (a blade's tip) borrow the normal of the nearest station that has one.
		for (int k = 0; k < segs; k++) {
			for (int i = 0; i < s; i++) {
				if (valid[i * segs + k]) continue;
				for (int d = 1; d < s; d++) {
					int j = i - d >= 0 && valid[(i - d) * segs + k] ? i - d : i + d < s && valid[(i + d) * segs + k] ? i + d : -1;
					if (j < 0) continue;
					System.arraycopy(sn, (j * segs + k) * 3, sn, (i * segs + k) * 3, 3);
					break;
				}
			}
		}

		int[] start = new int[s];
		int[] end = new int[s];
		for (int k = 0; k < segs; k++) {
			int k1 = (k + 1) % pts;
			int prev = l.closedRing ? (k - 1 + segs) % segs : k - 1;
			int next = l.closedRing ? (k + 1) % segs : k + 1 < segs ? k + 1 : -1;
			boolean smoothStart = !l.crease[k] && prev >= 0;
			boolean smoothEnd = !l.crease[k1] && next >= 0;
			for (int i = 0; i < s; i++) {
				int o = (i * segs + k) * 3;
				float nx = sn[o];
				float ny = sn[o + 1];
				float nz = sn[o + 2];
				int a = i * pts + k;
				int b = i * pts + k1;
				int m = l.mat[a];
				if (smoothStart) {
					int q = (i * segs + prev) * 3;
					start[i] = vertex(l.x[a], l.y[a], l.z[a], nx + sn[q], ny + sn[q + 1], nz + sn[q + 2], l.u[a], l.v[a], m, l.col[a]);
				} else {
					start[i] = vertex(l.x[a], l.y[a], l.z[a], nx, ny, nz, l.u[a], l.v[a], m, l.col[a]);
				}
				if (smoothEnd) {
					int q = (i * segs + next) * 3;
					end[i] = vertex(l.x[b], l.y[b], l.z[b], nx + sn[q], ny + sn[q + 1], nz + sn[q + 2], l.u[b], l.v[b], m, l.col[b]);
				} else {
					end[i] = vertex(l.x[b], l.y[b], l.z[b], nx, ny, nz, l.u[b], l.v[b], m, l.col[b]);
				}
			}
			int quads = l.closedStations ? s : s - 1;
			for (int i = 0; i < quads; i++) {
				int j = (i + 1) % s;
				facing(start[i], end[i], end[j]);
				facing(start[i], end[j], start[j]);
			}
		}
	}

	/** Closes one station of a loft with a flat fan around its centre (a blade's base, the end of a handle). */
	void cap(Loft l, int station, int mat, int argb) {
		int pts = l.points;
		int base = station * pts;
		float cx = 0f;
		float cy = 0f;
		float cz = 0f;
		float nx = 0f;
		float ny = 0f;
		float nz = 0f;
		for (int k = 0; k < pts; k++) {
			int a = base + k;
			int b = base + (k + 1) % pts;
			cx += l.x[a];
			cy += l.y[a];
			cz += l.z[a];
			// Newell's method: the polygon's normal, whatever its shape.
			nx += (l.y[a] - l.y[b]) * (l.z[a] + l.z[b]);
			ny += (l.z[a] - l.z[b]) * (l.x[a] + l.x[b]);
			nz += (l.x[a] - l.x[b]) * (l.y[a] + l.y[b]);
		}
		cx /= pts;
		cy /= pts;
		cz /= pts;
		// Outward is away from the neighbouring station.
		int other = (station == 0 ? 1 : station - 1) * pts;
		float ox = 0f;
		float oy = 0f;
		float oz = 0f;
		for (int k = 0; k < pts; k++) {
			ox += l.x[other + k];
			oy += l.y[other + k];
			oz += l.z[other + k];
		}
		ox = cx - ox / pts;
		oy = cy - oy / pts;
		oz = cz - oz / pts;
		if (nx * ox + ny * oy + nz * oz < 0f) {
			nx = -nx;
			ny = -ny;
			nz = -nz;
		}
		int centre = vertex(cx, cy, cz, nx, ny, nz, l.u[base], l.v[base], mat, argb);
		int first = vertex(l.x[base], l.y[base], l.z[base], nx, ny, nz, l.u[base], l.v[base], mat, argb);
		int prev = first;
		for (int k = 1; k <= pts; k++) {
			int a = base + k % pts;
			int cur = k == pts ? first : vertex(l.x[a], l.y[a], l.z[a], nx, ny, nz, l.u[a], l.v[a], mat, argb);
			facing(centre, prev, cur);
			prev = cur;
		}
	}

	// ------------------------------------------------------------------ plates

	/**
	 * A flat part (guard, bolster, handle plate, open frame): a counter-clockwise outline (x, y pairs in cm) with
	 * optional holes, extruded from z0 to z1 with every edge chamfered by bevel. Pattern coordinates are x and y.
	 */
	void plate(float[] outline, float[][] holes, float z0, float z1, float bevel, int mat, int argb) {
		float[][] loops = new float[1 + (holes == null ? 0 : holes.length)][];
		loops[0] = orient(outline, true);
		for (int i = 1; i < loops.length; i++) loops[i] = orient(holes[i - 1], false);
		float b = Math.max(0f, Math.min(bevel, (z1 - z0) * 0.45f));
		float[][] inset = new float[loops.length][];
		for (int i = 0; i < loops.length; i++) inset[i] = b > 0f ? offset(loops[i], b) : loops[i];
		capFace(inset, z1, 1f, mat, argb);
		capFace(inset, z0, -1f, mat, argb);
		for (int i = 0; i < loops.length; i++) {
			float[] outer = loops[i];
			if (b > 0f) {
				band(outer, z0 + b, outer, z1 - b, 0f, mat, argb);
				band(outer, z1 - b, inset[i], z1, 1f, mat, argb);
				band(inset[i], z0, outer, z0 + b, -1f, mat, argb);
			} else {
				band(outer, z0, outer, z1, 0f, mat, argb);
			}
		}
	}

	/** A pin, screw head or rivet on a face at height z, standing h out of it (h < 0 goes down, for faces at -z). */
	void pin(float cx, float cy, float z, float h, float radius, int mat, int argb) {
		float[] circle = new float[32];
		for (int i = 0; i < 16; i++) {
			double a = Math.PI * 2.0 * i / 16;
			circle[i * 2] = cx + radius * (float) Math.cos(a);
			circle[i * 2 + 1] = cy + radius * (float) Math.sin(a);
		}
		// Sunk a little into the face so it sits on curved scales too.
		float sink = Math.abs(h) * 0.6f;
		if (h > 0f) plate(circle, null, z - sink, z + h, Math.min(radius * 0.35f, h * 0.6f), mat, argb);
		else plate(circle, null, z + h, z + sink, Math.min(radius * 0.35f, -h * 0.6f), mat, argb);
	}

	/**
	 * Wall between loop a at height za and loop b at height zb (same point count): its normal leans by lean
	 * (0 for a wall, 1 for a 45 degree chamfer towards +z, -1 towards -z). Corners sharper than CREASE_ANGLE stay hard.
	 */
	private void band(float[] a, float za, float[] b, float zb, float lean, int mat, int argb) {
		int count = a.length / 2;
		for (int i = 0; i < count; i++) {
			int j = (i + 1) % count;
			float ex = a[j * 2] - a[i * 2];
			float ey = a[j * 2 + 1] - a[i * 2 + 1];
			float len = (float) Math.hypot(ex, ey);
			if (len < 1e-6f) continue;
			// Material is on the left of every loop, so outward is to the right of the edge.
			float nx = ey / len;
			float ny = -ex / len;
			float[] ni = cornerNormal(a, i, nx, ny, true);
			float[] nj = cornerNormal(a, j, nx, ny, false);
			int v0 = vertex(a[i * 2], a[i * 2 + 1], za, ni[0], ni[1], lean, a[i * 2], a[i * 2 + 1], mat, argb);
			int v1 = vertex(a[j * 2], a[j * 2 + 1], za, nj[0], nj[1], lean, a[j * 2], a[j * 2 + 1], mat, argb);
			int v2 = vertex(b[j * 2], b[j * 2 + 1], zb, nj[0], nj[1], lean, b[j * 2], b[j * 2 + 1], mat, argb);
			int v3 = vertex(b[i * 2], b[i * 2 + 1], zb, ni[0], ni[1], lean, b[i * 2], b[i * 2 + 1], mat, argb);
			facing(v0, v1, v2);
			facing(v0, v2, v3);
		}
	}

	/** Normal of a loop's wall at point i: the edge's own at a sharp corner, else averaged with the neighbouring edge. */
	private static float[] cornerNormal(float[] loop, int i, float nx, float ny, boolean atStart) {
		int count = loop.length / 2;
		int other = atStart ? (i - 1 + count) % count : (i + 1) % count;
		float ex = atStart ? loop[i * 2] - loop[other * 2] : loop[other * 2] - loop[i * 2];
		float ey = atStart ? loop[i * 2 + 1] - loop[other * 2 + 1] : loop[other * 2 + 1] - loop[i * 2 + 1];
		float len = (float) Math.hypot(ex, ey);
		if (len < 1e-6f) return new float[]{nx, ny};
		float ox = ey / len;
		float oy = -ex / len;
		float dot = ox * nx + oy * ny;
		if (dot < (float) Math.cos(CREASE_ANGLE)) return new float[]{nx, ny};
		float sx = ox + nx;
		float sy = oy + ny;
		float sl = (float) Math.hypot(sx, sy);
		return new float[]{sx / sl, sy / sl};
	}

	/** A flat face at height z facing +z (side 1) or -z (side -1), triangulated from the loops (outline, then holes). */
	private void capFace(float[][] loops, float z, float side, int mat, int argb) {
		float[] merged = bridge(loops);
		int count = merged.length / 2;
		int[] ids = new int[count];
		for (int i = 0; i < count; i++) ids[i] = vertex(merged[i * 2], merged[i * 2 + 1], z, 0f, 0f, side, merged[i * 2], merged[i * 2 + 1], mat, argb);
		int[] tris = earClip(merged);
		for (int i = 0; i < tris.length; i += 3) facing(ids[tris[i]], ids[tris[i + 1]], ids[tris[i + 2]]);
	}

	// ------------------------------------------------------------------ polygon helpers

	static float area(float[] loop) {
		int count = loop.length / 2;
		float a = 0f;
		for (int i = 0; i < count; i++) {
			int j = (i + 1) % count;
			a += loop[i * 2] * loop[j * 2 + 1] - loop[j * 2] * loop[i * 2 + 1];
		}
		return a * 0.5f;
	}

	/** The loop counter-clockwise (outline) or clockwise (hole), reversed if needed. */
	private static float[] orient(float[] loop, boolean ccw) {
		if (area(loop) > 0f == ccw) return loop;
		int count = loop.length / 2;
		float[] r = new float[loop.length];
		for (int i = 0; i < count; i++) {
			r[i * 2] = loop[(count - 1 - i) * 2];
			r[i * 2 + 1] = loop[(count - 1 - i) * 2 + 1];
		}
		return r;
	}

	/** Moves every edge of the loop d to its left (into the material) with mitred corners, capped at 3 d. */
	static float[] offset(float[] loop, float d) {
		int count = loop.length / 2;
		float[] out = new float[loop.length];
		for (int i = 0; i < count; i++) {
			int h = (i - 1 + count) % count;
			int j = (i + 1) % count;
			float ax = loop[i * 2] - loop[h * 2];
			float ay = loop[i * 2 + 1] - loop[h * 2 + 1];
			float bx = loop[j * 2] - loop[i * 2];
			float by = loop[j * 2 + 1] - loop[i * 2 + 1];
			float la = Math.max((float) Math.hypot(ax, ay), 1e-6f);
			float lb = Math.max((float) Math.hypot(bx, by), 1e-6f);
			// Left normals of both edges, and their bisector scaled so both edges move by d.
			float n1x = -ay / la;
			float n1y = ax / la;
			float n2x = -by / lb;
			float n2y = bx / lb;
			float mx = n1x + n2x;
			float my = n1y + n2y;
			float ml = (float) Math.hypot(mx, my);
			if (ml < 1e-4f) {
				mx = n1x;
				my = n1y;
				ml = 1f;
			}
			mx /= ml;
			my /= ml;
			float scale = Math.min(3f, 1f / Math.max(mx * n1x + my * n1y, 0.2f));
			out[i * 2] = loop[i * 2] + mx * d * scale;
			out[i * 2 + 1] = loop[i * 2 + 1] + my * d * scale;
		}
		return out;
	}

	/** Joins the holes into the outline through bridge edges, giving one polygon that ear clipping can fill. */
	private static float[] bridge(float[][] loops) {
		float[] poly = loops[0];
		for (int h = 1; h < loops.length; h++) {
			float[] hole = loops[h];
			int hc = hole.length / 2;
			int hi = 0;
			for (int i = 1; i < hc; i++) if (hole[i * 2] > hole[hi * 2]) hi = i;
			float hx = hole[hi * 2];
			float hy = hole[hi * 2 + 1];
			// Nearest outline edge crossed by a ray from the hole's rightmost point towards +x.
			int pc = poly.length / 2;
			int best = -1;
			float bestX = Float.MAX_VALUE;
			for (int i = 0; i < pc; i++) {
				int j = (i + 1) % pc;
				float y0 = poly[i * 2 + 1];
				float y1 = poly[j * 2 + 1];
				if ((y0 > hy) == (y1 > hy)) continue;
				float t = (hy - y0) / (y1 - y0);
				float x = poly[i * 2] + (poly[j * 2] - poly[i * 2]) * t;
				if (x > hx && x < bestX) {
					bestX = x;
					best = poly[i * 2] > poly[j * 2] ? i : j;
				}
			}
			if (best < 0) continue;
			float[] merged = new float[(pc + hc + 2) * 2];
			int o = 0;
			for (int i = 0; i <= best; i++) {
				merged[o++] = poly[i * 2];
				merged[o++] = poly[i * 2 + 1];
			}
			for (int i = 0; i <= hc; i++) {
				int k = (hi + i) % hc;
				merged[o++] = hole[k * 2];
				merged[o++] = hole[k * 2 + 1];
			}
			merged[o++] = poly[best * 2];
			merged[o++] = poly[best * 2 + 1];
			for (int i = best + 1; i < pc; i++) {
				merged[o++] = poly[i * 2];
				merged[o++] = poly[i * 2 + 1];
			}
			poly = merged;
		}
		return poly;
	}

	/** Ear clipping of a counter-clockwise polygon (bridged holes allowed); triangles as index triples. */
	private static int[] earClip(float[] poly) {
		int count = poly.length / 2;
		int[] next = new int[count];
		int[] prev = new int[count];
		for (int i = 0; i < count; i++) {
			next[i] = (i + 1) % count;
			prev[i] = (i - 1 + count) % count;
		}
		int[] out = new int[Math.max(0, count - 2) * 3];
		int o = 0;
		int cur = 0;
		for (int left = count; left > 2; left--) {
			// A proper ear if there is one; a polygon left degenerate by rounding gives up its first convex corner.
			int ear = -1;
			int v = cur;
			for (int pass = 0; pass < 2 && ear < 0; pass++) {
				for (int step = 0; step < left; step++, v = next[v]) {
					if (isEar(poly, prev[v], v, next[v], next, pass == 1)) {
						ear = v;
						break;
					}
				}
			}
			if (ear < 0) ear = cur;
			out[o++] = prev[ear];
			out[o++] = ear;
			out[o++] = next[ear];
			next[prev[ear]] = next[ear];
			prev[next[ear]] = prev[ear];
			cur = next[ear];
		}
		return Arrays.copyOf(out, o);
	}

	private static boolean isEar(float[] poly, int a, int b, int c, int[] next, boolean anyConvex) {
		float ax = poly[a * 2];
		float ay = poly[a * 2 + 1];
		float bx = poly[b * 2];
		float by = poly[b * 2 + 1];
		float cx = poly[c * 2];
		float cy = poly[c * 2 + 1];
		if ((bx - ax) * (cy - ay) - (by - ay) * (cx - ax) <= 0f) return false;
		if (anyConvex) return true;
		for (int i = next[c]; i != a; i = next[i]) {
			float px = poly[i * 2];
			float py = poly[i * 2 + 1];
			// Bridge seams repeat points: a copy of a corner does not block its ear.
			if (px == ax && py == ay || px == bx && py == by || px == cx && py == cy) continue;
			if (inside(px, py, ax, ay, bx, by, cx, cy)) return false;
		}
		return true;
	}

	private static boolean inside(float px, float py, float ax, float ay, float bx, float by, float cx, float cy) {
		float d1 = (px - bx) * (ay - by) - (ax - bx) * (py - by);
		float d2 = (px - cx) * (by - cy) - (bx - cx) * (py - cy);
		float d3 = (px - ax) * (cy - ay) - (cx - ax) * (py - ay);
		return d1 < 0f && d2 < 0f && d3 < 0f || d1 > 0f && d2 > 0f && d3 > 0f;
	}
}
