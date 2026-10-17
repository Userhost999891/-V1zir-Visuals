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

// Coverage for the given mode: 0 fill, 1 outline, 2 soft shadow / glow, 3 ring arc (thickness = width)
float v1zirShapeCoverage(float d, vec4 shape, vec3 style) {
	float fw = max(fwidth(d), 1e-4);
	int mode = int(style.y);
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
