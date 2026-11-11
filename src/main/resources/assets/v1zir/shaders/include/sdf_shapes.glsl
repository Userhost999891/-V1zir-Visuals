// Signed distance helpers for the GUI shape pipelines (GUI space, y points down).

// rad: top-left, top-right, bottom-right, bottom-left
float v1zirCornerRadius(vec2 p, vec4 rad) {
	if (p.x < 0.0) return p.y < 0.0 ? rad.x : rad.w;
	return p.y < 0.0 ? rad.y : rad.z;
}

float v1zirRoundBox(vec2 p, vec2 halfSize, float r) {
	vec2 q = abs(p) - halfSize + r;
	return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

// Distance to the shape described by the vertex data; corner mask bits: 1 TL, 2 TR, 4 BR, 8 BL (0 = all).
float v1zirShapeDistance(vec2 p, vec4 shape, float mask) {
	vec2 halfSize = shape.xy;
	float radius = min(shape.z, min(halfSize.x, halfSize.y));
	int m = int(mask);
	vec4 rad = vec4(radius);
	if (m != 0) {
		rad = vec4(
			(m & 1) != 0 ? radius : 0.0,
			(m & 2) != 0 ? radius : 0.0,
			(m & 4) != 0 ? radius : 0.0,
			(m & 8) != 0 ? radius : 0.0);
	}
	return v1zirRoundBox(p, halfSize, v1zirCornerRadius(p, rad));
}

// Mode flags on top of the shape kind in the low 3 bits (see ShapeElement).
const int V1ZIR_MATTE = 8;
const int V1ZIR_LIT = 16;

// Coverage for the given mode: 0 fill, 1 outline, 2 soft shadow / glow
float v1zirShapeCoverage(float d, vec4 shape, vec3 style) {
	float fw = max(fwidth(d), 1e-4);
	int mode = int(style.y) & 7;
	if (mode == 1) {
		float t = max(style.x, 0.0);
		float o = abs(d + t * 0.5) - t * 0.5;
		return clamp(0.5 - o / fw, 0.0, 1.0);
	}
	if (mode == 2) {
		float soft = max(shape.w, 0.5);
		float a = 1.0 - smoothstep(-soft * 0.35, soft, d);
		return a * a * (3.0 - 2.0 * a);
	}
	return clamp(0.5 - d / fw, 0.0, 1.0);
}

// Static grain of one framebuffer pixel in [-0.5, 0.5]: an integer hash, so it never crawls or bands
// and stays one device pixel fine at every GUI scale. It gives flat surfaces their matte feel.
float v1zirGrain(vec2 fragCoord) {
	uvec2 p = uvec2(fragCoord);
	uint h = p.x * 1597334677u ^ p.y * 3812015801u;
	h = (h ^ (h >> 16u)) * 2246822519u;
	h ^= h >> 13u;
	return float(h & 65535u) / 65535.0 - 0.5;
}
