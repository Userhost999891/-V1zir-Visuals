package dev.v1zir.visuals.render.cosmetic;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Builds cosmetic meshes in pixels from simple solids (boxes, lathed profiles, tubes along paths, tori, ellipsoids
 * and extruded outlines) under a transform stack. Each primitive takes the current colour and material; triangles
 * are wound so they face along their normals, whatever order a primitive lists its corners in.
 */
final class MeshKit {
	private static final float TAU = (float) (Math.PI * 2.0);
	private static final int F = CosmeticMesh.FLOATS;

	private float[] data = new float[F * 2048];
	private int[] colors = new int[2048];
	private int[] materials = new int[2048];
	private int[] indices = new int[6144];
	private int vertices;
	private int indexCount;
	private final Matrix4f[] stack = new Matrix4f[24];
	private int depth;
	private final Matrix3f normalMatrix = new Matrix3f();
	private boolean normalDirty = true;
	private final Vector3f p = new Vector3f();
	private final Vector3f n = new Vector3f();
	private int color = 0xFFFFFFFF;
	private int material = CosmeticMesh.MATTE;
	private final List<CosmeticMesh.Part> parts = new ArrayList<>();
	private int partStart;

	MeshKit() {
		for (int i = 0; i < stack.length; i++) stack[i] = new Matrix4f();
	}

	// ------------------------------------------------------------------ state

	MeshKit paint(int argb, int mat) {
		color = argb;
		material = mat;
		return this;
	}

	MeshKit push() {
		stack[depth + 1].set(stack[depth]);
		depth++;
		return this;
	}

	MeshKit pop() {
		depth--;
		normalDirty = true;
		return this;
	}

	MeshKit translate(float x, float y, float z) {
		stack[depth].translate(x, y, z);
		return this;
	}

	MeshKit rotateX(float degrees) {
		stack[depth].rotateX((float) Math.toRadians(degrees));
		normalDirty = true;
		return this;
	}

	MeshKit rotateY(float degrees) {
		stack[depth].rotateY((float) Math.toRadians(degrees));
		normalDirty = true;
		return this;
	}

	MeshKit rotateZ(float degrees) {
		stack[depth].rotateZ((float) Math.toRadians(degrees));
		normalDirty = true;
		return this;
	}

	MeshKit scale(float x, float y, float z) {
		stack[depth].scale(x, y, z);
		normalDirty = true;
		return this;
	}

	/** Ends the part built since the last one: it turns about the pivot (pixels, model space); glow parts are blended. */
	MeshKit part(float pivotX, float pivotY, float pivotZ, boolean glow) {
		if (indexCount > partStart) parts.add(new CosmeticMesh.Part(partStart, indexCount - partStart, pivotX, pivotY, pivotZ, glow));
		partStart = indexCount;
		return this;
	}

	CosmeticMesh build() {
		part(0f, 0f, 0f, false);
		float[] bounds = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
		for (int v = 0; v < vertices; v++) {
			for (int a = 0; a < 3; a++) {
				bounds[a] = Math.min(bounds[a], data[v * F + a]);
				bounds[a + 3] = Math.max(bounds[a + 3], data[v * F + a]);
			}
		}
		if (vertices == 0) Arrays.fill(bounds, 0f);
		return new CosmeticMesh(Arrays.copyOf(data, vertices * F), Arrays.copyOf(colors, vertices), Arrays.copyOf(materials, vertices),
			Arrays.copyOf(indices, indexCount), parts.toArray(new CosmeticMesh.Part[0]), bounds);
	}

	// ------------------------------------------------------------------ raw geometry

	int vertex(float x, float y, float z, float nx, float ny, float nz, float u, float v) {
		if (vertices == colors.length) {
			int size = colors.length * 2;
			data = Arrays.copyOf(data, size * F);
			colors = Arrays.copyOf(colors, size);
			materials = Arrays.copyOf(materials, size);
		}
		Matrix4f m = stack[depth];
		m.transformPosition(p.set(x, y, z));
		if (normalDirty) {
			m.normal(normalMatrix);
			normalDirty = false;
		}
		normalMatrix.transform(n.set(nx, ny, nz));
		float len = n.length();
		if (len > 1e-6f) n.div(len);
		int o = vertices * F;
		data[o] = p.x;
		data[o + 1] = p.y;
		data[o + 2] = p.z;
		data[o + 3] = n.x;
		data[o + 4] = n.y;
		data[o + 5] = n.z;
		data[o + 6] = u;
		data[o + 7] = v;
		colors[vertices] = color;
		materials[vertices] = material;
		return vertices++;
	}

	/** A triangle, turned to face along its vertices' normals; degenerate ones are dropped. */
	void tri(int a, int b, int c) {
		float ax = data[a * F], ay = data[a * F + 1], az = data[a * F + 2];
		float ux = data[b * F] - ax, uy = data[b * F + 1] - ay, uz = data[b * F + 2] - az;
		float vx = data[c * F] - ax, vy = data[c * F + 1] - ay, vz = data[c * F + 2] - az;
		float cx = uy * vz - uz * vy, cy = uz * vx - ux * vz, cz = ux * vy - uy * vx;
		if (cx * cx + cy * cy + cz * cz < 1e-12f) return;
		float nx = data[a * F + 3] + data[b * F + 3] + data[c * F + 3];
		float ny = data[a * F + 4] + data[b * F + 4] + data[c * F + 4];
		float nz = data[a * F + 5] + data[b * F + 5] + data[c * F + 5];
		if (indexCount + 3 > indices.length) indices = Arrays.copyOf(indices, indices.length * 2);
		indices[indexCount++] = a;
		if (cx * nx + cy * ny + cz * nz >= 0f) {
			indices[indexCount++] = b;
			indices[indexCount++] = c;
		} else {
			indices[indexCount++] = c;
			indices[indexCount++] = b;
		}
	}

	void quad(int a, int b, int c, int d) {
		tri(a, b, c);
		tri(a, c, d);
	}

	// ------------------------------------------------------------------ solids

	/** An axis aligned box; pattern coordinates are pixels across each face. */
	MeshKit box(float x0, float y0, float z0, float x1, float y1, float z1) {
		face(x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, 1, 0, 0, z0, y0, z1, y1);
		face(x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0, -1, 0, 0, z0, y0, z1, y1);
		face(x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, 0, 1, 0, x0, z0, x1, z1);
		face(x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1, 0, -1, 0, x0, z0, x1, z1);
		face(x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, 0, 0, 1, x0, y0, x1, y1);
		face(x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, 0, 0, -1, x0, y0, x1, y1);
		return this;
	}

	private void face(float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz,
					  float nx, float ny, float nz, float u0, float v0, float u1, float v1) {
		int a = vertex(ax, ay, az, nx, ny, nz, u0, v0);
		int b = vertex(bx, by, bz, nx, ny, nz, u0, v1);
		int c = vertex(cx, cy, cz, nx, ny, nz, u1, v1);
		int d = vertex(dx, dy, dz, nx, ny, nz, u1, v0);
		quad(a, b, c, d);
	}

	/**
	 * A surface of revolution about the y axis through the profile points (radius, height) listed from the bottom
	 * up; smooth averages the normals across profile points, otherwise every segment is a flat band. Pattern
	 * coordinates: u around (pixels at the widest radius), v along the profile (pixels).
	 */
	MeshKit lathe(float[] radius, float[] height, int segments, boolean smooth) {
		int count = radius.length;
		float widest = 0f;
		for (float r : radius) widest = Math.max(widest, r);
		float around = TAU * widest;
		float[] segN = new float[(count - 1) * 2];
		for (int k = 0; k < count - 1; k++) {
			float dr = radius[k + 1] - radius[k];
			float dy = height[k + 1] - height[k];
			float len = (float) Math.sqrt(dr * dr + dy * dy);
			segN[k * 2] = len > 1e-6f ? dy / len : 1f;
			segN[k * 2 + 1] = len > 1e-6f ? -dr / len : 0f;
		}
		float along = 0f;
		if (smooth) {
			int[] ring = new int[count * (segments + 1)];
			for (int i = 0; i < count; i++) {
				if (i > 0) along += dist(radius[i] - radius[i - 1], height[i] - height[i - 1]);
				float nr;
				float ny;
				if (i == 0) {
					nr = segN[0];
					ny = segN[1];
				} else if (i == count - 1) {
					nr = segN[(i - 1) * 2];
					ny = segN[(i - 1) * 2 + 1];
				} else {
					nr = segN[(i - 1) * 2] + segN[i * 2];
					ny = segN[(i - 1) * 2 + 1] + segN[i * 2 + 1];
				}
				for (int j = 0; j <= segments; j++) {
					float t = j / (float) segments;
					float a = t * TAU;
					float cos = (float) Math.cos(a);
					float sin = (float) Math.sin(a);
					ring[i * (segments + 1) + j] = vertex(radius[i] * cos, height[i], radius[i] * sin, nr * cos, ny, nr * sin, t * around, along);
				}
			}
			for (int i = 0; i < count - 1; i++) {
				for (int j = 0; j < segments; j++) {
					int a = ring[i * (segments + 1) + j];
					int b = ring[i * (segments + 1) + j + 1];
					int c = ring[(i + 1) * (segments + 1) + j + 1];
					int d = ring[(i + 1) * (segments + 1) + j];
					quad(a, b, c, d);
				}
			}
		} else {
			for (int k = 0; k < count - 1; k++) {
				float nr = segN[k * 2];
				float ny = segN[k * 2 + 1];
				float next = along + dist(radius[k + 1] - radius[k], height[k + 1] - height[k]);
				int prevA = -1;
				int prevB = -1;
				for (int j = 0; j <= segments; j++) {
					float t = j / (float) segments;
					float a = t * TAU;
					float cos = (float) Math.cos(a);
					float sin = (float) Math.sin(a);
					int lo = vertex(radius[k] * cos, height[k], radius[k] * sin, nr * cos, ny, nr * sin, t * around, along);
					int hi = vertex(radius[k + 1] * cos, height[k + 1], radius[k + 1] * sin, nr * cos, ny, nr * sin, t * around, next);
					if (j > 0) quad(prevA, lo, hi, prevB);
					prevA = lo;
					prevB = hi;
				}
				along = next;
			}
		}
		return this;
	}

	/**
	 * A soft solid around the y axis whose cross-sections are rounded squares: level i has half size half[i] and
	 * corner radius corner[i] at height y[i], listed from the bottom up (half 0 closes it to a point). Hats that sit
	 * on the blocky head without its corners poking through. Smooth normals; u runs round (pixels), v up.
	 */
	MeshKit roundedLoft(float[] half, float[] corner, float[] y, int perCorner) {
		int levels = half.length;
		int ring = perCorner * 4;
		int[] idx = new int[levels * (ring + 1)];
		float[] px = new float[ring + 1];
		float[] pz = new float[ring + 1];
		float[] nx = new float[ring + 1];
		float[] nz = new float[ring + 1];
		for (int i = 0; i < levels; i++) {
			float h = half[i];
			float r = Math.min(corner[i], h);
			// Slope of the profile here (central differences), for the normals' tilt.
			int a = Math.max(0, i - 1);
			int b = Math.min(levels - 1, i + 1);
			float dh = half[b] - half[a];
			float dy = y[b] - y[a];
			float len = (float) Math.sqrt(dh * dh + dy * dy);
			float out = len > 1e-6f ? dy / len : 0f;
			float up = len > 1e-6f ? -dh / len : 1f;
			float perimeter = 0f;
			for (int j = 0; j <= ring; j++) {
				int c = (j / perCorner) % 4;
				float t = (j % perCorner) / (float) perCorner;
				double angle = Math.PI / 2.0 * (c + t);
				float cx = (c == 0 || c == 3) ? h - r : -(h - r);
				float cz = (c < 2) ? h - r : -(h - r);
				float ca = (float) Math.cos(angle);
				float sa = (float) Math.sin(angle);
				px[j] = cx + ca * r;
				pz[j] = cz + sa * r;
				nx[j] = ca;
				nz[j] = sa;
				if (j > 0) perimeter += dist(px[j] - px[j - 1], pz[j] - pz[j - 1]);
				idx[i * (ring + 1) + j] = vertex(px[j], y[i], pz[j], nx[j] * out, up, nz[j] * out, perimeter, y[i]);
			}
		}
		for (int i = 0; i < levels - 1; i++) {
			for (int j = 0; j < ring; j++) {
				quad(idx[i * (ring + 1) + j], idx[i * (ring + 1) + j + 1], idx[(i + 1) * (ring + 1) + j + 1], idx[(i + 1) * (ring + 1) + j]);
			}
		}
		return this;
	}

	/** A disc in the xz plane at height y facing up (or down): caps for lathed solids. */
	MeshKit disc(float radius, float y, int segments, boolean up) {
		float ny = up ? 1f : -1f;
		int centre = vertex(0f, y, 0f, 0f, ny, 0f, 0f, 0f);
		int prev = -1;
		for (int j = 0; j <= segments; j++) {
			float a = j / (float) segments * TAU;
			float x = (float) Math.cos(a) * radius;
			float z = (float) Math.sin(a) * radius;
			int v = vertex(x, y, z, 0f, ny, 0f, x, z);
			if (prev >= 0) tri(centre, prev, v);
			prev = v;
		}
		return this;
	}

	/** An ellipsoid centred at (cx, cy, cz). */
	MeshKit ellipsoid(float cx, float cy, float cz, float rx, float ry, float rz, int segU, int segV) {
		int[] grid = new int[(segU + 1) * (segV + 1)];
		for (int i = 0; i <= segV; i++) {
			float phi = (float) Math.PI * i / segV;
			float sy = (float) Math.cos(phi);
			float sr = (float) Math.sin(phi);
			for (int j = 0; j <= segU; j++) {
				float theta = TAU * j / segU;
				float sx = sr * (float) Math.cos(theta);
				float sz = sr * (float) Math.sin(theta);
				grid[i * (segU + 1) + j] = vertex(cx + sx * rx, cy + sy * ry, cz + sz * rz, sx / rx, sy / ry, sz / rz,
					j / (float) segU * TAU * Math.max(rx, rz), i / (float) segV * (float) Math.PI * ry);
			}
		}
		for (int i = 0; i < segV; i++) {
			for (int j = 0; j < segU; j++) {
				quad(grid[i * (segU + 1) + j], grid[i * (segU + 1) + j + 1], grid[(i + 1) * (segU + 1) + j + 1], grid[(i + 1) * (segU + 1) + j]);
			}
		}
		return this;
	}

	/** A torus in the xz plane about the y axis: ring radius R, tube radius r. */
	MeshKit torus(float ringRadius, float tubeRadius, int segU, int segV) {
		int[] grid = new int[(segU + 1) * (segV + 1)];
		for (int i = 0; i <= segU; i++) {
			float a = TAU * i / segU;
			float ca = (float) Math.cos(a);
			float sa = (float) Math.sin(a);
			for (int j = 0; j <= segV; j++) {
				float b = TAU * j / segV;
				float cb = (float) Math.cos(b);
				float sb = (float) Math.sin(b);
				float r = ringRadius + tubeRadius * cb;
				grid[i * (segV + 1) + j] = vertex(r * ca, tubeRadius * sb, r * sa, cb * ca, sb, cb * sa,
					i / (float) segU * TAU * ringRadius, j / (float) segV * TAU * tubeRadius);
			}
		}
		for (int i = 0; i < segU; i++) {
			for (int j = 0; j < segV; j++) {
				quad(grid[i * (segV + 1) + j], grid[(i + 1) * (segV + 1) + j], grid[(i + 1) * (segV + 1) + j + 1], grid[i * (segV + 1) + j + 1]);
			}
		}
		return this;
	}

	/**
	 * A tube along a path of points (x, y, z triples) with a radius at each point, carried by parallel transported
	 * frames so it never twists; a radius of 0 closes it to a point, and capped ends are closed with a fan.
	 * Pattern coordinates: u around (pixels), v along the path (pixels).
	 */
	MeshKit tube(float[] path, float[] radius, int segments, boolean capStart, boolean capEnd) {
		int count = radius.length;
		Vector3f tangent = new Vector3f();
		Vector3f normal = new Vector3f();
		Vector3f binormal = new Vector3f();
		Vector3f prevTangent = new Vector3f();
		int[] ring = new int[count * (segments + 1)];
		float along = 0f;
		for (int i = 0; i < count; i++) {
			int a = Math.max(0, i - 1);
			int b = Math.min(count - 1, i + 1);
			tangent.set(path[b * 3] - path[a * 3], path[b * 3 + 1] - path[a * 3 + 1], path[b * 3 + 2] - path[a * 3 + 2]).normalize();
			if (i == 0) {
				// Any normal across the first tangent.
				normal.set(Math.abs(tangent.y) < 0.9f ? 0f : 1f, Math.abs(tangent.y) < 0.9f ? 1f : 0f, 0f);
				normal.sub(new Vector3f(tangent).mul(normal.dot(tangent))).normalize();
			} else {
				// Parallel transport: turn the last normal by the rotation from the last tangent to this one.
				Vector3f axis = new Vector3f(prevTangent).cross(tangent);
				float sin = axis.length();
				if (sin > 1e-6f) {
					float angle = (float) Math.atan2(sin, prevTangent.dot(tangent));
					normal.rotateAxis(angle, axis.x / sin, axis.y / sin, axis.z / sin);
				}
				along += dist3(path, i - 1, i);
			}
			prevTangent.set(tangent);
			tangent.cross(normal, binormal).normalize();
			float r = radius[i];
			for (int j = 0; j <= segments; j++) {
				float t = j / (float) segments;
				float angle = t * TAU;
				float c = (float) Math.cos(angle);
				float s = (float) Math.sin(angle);
				float dx = normal.x * c + binormal.x * s;
				float dy = normal.y * c + binormal.y * s;
				float dz = normal.z * c + binormal.z * s;
				ring[i * (segments + 1) + j] = vertex(path[i * 3] + dx * r, path[i * 3 + 1] + dy * r, path[i * 3 + 2] + dz * r, dx, dy, dz,
					t * TAU * Math.max(r, 0.5f), along);
			}
		}
		for (int i = 0; i < count - 1; i++) {
			for (int j = 0; j < segments; j++) {
				quad(ring[i * (segments + 1) + j], ring[i * (segments + 1) + j + 1], ring[(i + 1) * (segments + 1) + j + 1], ring[(i + 1) * (segments + 1) + j]);
			}
		}
		if (capStart && radius[0] > 0f) cap(path, 0, 1, ring, 0, segments);
		if (capEnd && radius[count - 1] > 0f) cap(path, count - 1, count - 2, ring, count - 1, segments);
		return this;
	}

	private void cap(float[] path, int at, int from, int[] ring, int row, int segments) {
		float nx = path[at * 3] - path[from * 3];
		float ny = path[at * 3 + 1] - path[from * 3 + 1];
		float nz = path[at * 3 + 2] - path[from * 3 + 2];
		int centre = vertex(path[at * 3], path[at * 3 + 1], path[at * 3 + 2], nx, ny, nz, 0f, 0f);
		// The ring is already in model space: copy its corners with the centre's (transformed) normal.
		int[] edge = new int[segments + 1];
		for (int j = 0; j <= segments; j++) {
			int src = ring[row * (segments + 1) + j];
			edge[j] = copy(src, data[centre * F + 3], data[centre * F + 4], data[centre * F + 5]);
		}
		for (int j = 0; j < segments; j++) tri(centre, edge[j], edge[j + 1]);
	}

	/** A copy of a vertex already in model space with another normal. */
	private int copy(int src, float nx, float ny, float nz) {
		if (vertices == colors.length) {
			int size = colors.length * 2;
			data = Arrays.copyOf(data, size * F);
			colors = Arrays.copyOf(colors, size);
			materials = Arrays.copyOf(materials, size);
		}
		System.arraycopy(data, src * F, data, vertices * F, F);
		data[vertices * F + 3] = nx;
		data[vertices * F + 4] = ny;
		data[vertices * F + 5] = nz;
		colors[vertices] = color;
		materials[vertices] = material;
		return vertices++;
	}

	/**
	 * An outline in the xy plane (x, y pairs, star-shaped about its centroid) extruded symmetrically along z to the
	 * given thickness: two faces and the walls between them. Pattern coordinates: the outline's own x and y.
	 */
	MeshKit plate(float[] outline, float thickness) {
		int count = outline.length / 2;
		float cx = 0f;
		float cy = 0f;
		for (int i = 0; i < count; i++) {
			cx += outline[i * 2];
			cy += outline[i * 2 + 1];
		}
		cx /= count;
		cy /= count;
		float h = thickness / 2f;
		for (int side = 0; side < 2; side++) {
			float z = side == 0 ? h : -h;
			float nz = side == 0 ? 1f : -1f;
			int centre = vertex(cx, cy, z, 0f, 0f, nz, cx, cy);
			int first = -1;
			int prev = -1;
			for (int i = 0; i < count; i++) {
				int v = vertex(outline[i * 2], outline[i * 2 + 1], z, 0f, 0f, nz, outline[i * 2], outline[i * 2 + 1]);
				if (prev >= 0) tri(centre, prev, v);
				else first = v;
				prev = v;
			}
			tri(centre, prev, first);
		}
		if (thickness > 0.01f) {
			for (int i = 0; i < count; i++) {
				int k = (i + 1) % count;
				float x0 = outline[i * 2], y0 = outline[i * 2 + 1], x1 = outline[k * 2], y1 = outline[k * 2 + 1];
				float ex = x1 - x0;
				float ey = y1 - y0;
				float len = (float) Math.sqrt(ex * ex + ey * ey);
				if (len < 1e-5f) continue;
				// Outward: away from the centroid.
				float nx = ey / len;
				float ny = -ex / len;
				if (nx * ((x0 + x1) / 2f - cx) + ny * ((y0 + y1) / 2f - cy) < 0f) {
					nx = -nx;
					ny = -ny;
				}
				int a = vertex(x0, y0, h, nx, ny, 0f, x0, y0);
				int b = vertex(x1, y1, h, nx, ny, 0f, x1, y1);
				int c = vertex(x1, y1, -h, nx, ny, 0f, x1, y1);
				int d = vertex(x0, y0, -h, nx, ny, 0f, x0, y0);
				quad(a, b, c, d);
			}
		}
		return this;
	}

	private static float dist(float a, float b) {
		return (float) Math.sqrt(a * a + b * b);
	}

	private static float dist3(float[] path, int i, int j) {
		float dx = path[j * 3] - path[i * 3];
		float dy = path[j * 3 + 1] - path[i * 3 + 1];
		float dz = path[j * 3 + 2] - path[i * 3 + 2];
		return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
	}
}
