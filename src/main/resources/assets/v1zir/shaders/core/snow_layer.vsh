#version 150

// Snow lying on the world close to the camera as real geometry (the Winter module, render/winter/SnowLayer): a
// cushion of snow a few sixteenths deep on every column whose top is flat and whole and open to the sky, its surface
// rising and falling softly. Where a column's side is open (the neighbour lower, or water level with it) the cushion
// rounds off towards that edge, hangs a little over it and comes down to the block in a short side; between columns
// at the same height it runs on without a seam. Every vertex is placed here from a static template, nothing per frame
// on the CPU. The depth thins out towards the edge of the layer, where the flat cover (snow_cover.fsh) takes over.
#moj_import <v1zir:reflect.glsl>

// Snow per column (render/SurfaceMap): r = sixteenths at which it lies on the top block, g = block light,
// b = 1 on leaves (2 on something hot, where r is 0).
uniform sampler2D Sampler5;

// Position.xz: where in its column the vertex lies (0..1); Position.y: 0 on the snow's surface, 1 at the foot of a
// side. UV0: the column, 0..47 each way with the camera's block at 24. Color.r: the side a side's vertex belongs to
// (0 -x, 1 +x, 2 -z, 3 +z), 4 on the top.
in vec3 Position;
in vec2 UV0;
in vec4 Color;

out vec3 vWorld;
out vec3 vRel;
out vec3 vNormal;
out float vLight;
out float vShade;

const int HALF = 24;
// How far in from an open edge the surface starts to round off, and how far the snow hangs over the edge.
const float INSET = 0.32;
const float OVERHANG = 0.035;

float lHash(ivec2 c) {
	uint h = uint(c.x) * 374761393u + uint(c.y) * 668265263u;
	h = (h ^ (h >> 13u)) * 1274126177u;
	return float((h ^ (h >> 16u)) & 0xFFFFFFu) / 16777215.0;
}

float lNoise(vec2 x) {
	vec2 i = floor(x);
	vec2 f = x - i;
	f = f * f * (3.0 - 2.0 * f);
	ivec2 c = ivec2(i);
	return mix(mix(lHash(c), lHash(c + ivec2(1, 0)), f.x), mix(lHash(c + ivec2(0, 1)), lHash(c + ivec2(1, 1)), f.x), f.y);
}

// The snow of column v (from the camera's block): x = the height it lies at (absolute), y = block light (0..1),
// z = 1 on leaves; x is -1e9 where none lies. Leaves hold it in patches: some of their columns stay bare.
vec3 lSnow(ivec2 v) {
	vec2 column = rColumn(v);
	vec4 s = texelFetch(Sampler5, (ivec2(CameraBlock.xz) + v + 256) & 127, 0);
	int lies = rUnpack(s.r);
	if (column.x < -1.0e8 || lies == 0 || s.a < 0.5) return vec3(-1.0e9, 0.0, 0.0);
	bool leaves = rUnpack(s.b) == 1;
	if (leaves && lHash(ivec2(floor(CameraPos.xz)) + v + ivec2(57, 113)) < 0.15) return vec3(-1.0e9, 0.0, 0.0);
	return vec3(column.x - 1.0 + float(lies) / 16.0, s.g, leaves ? 1.0 : 0.0);
}

// Whether the side towards column n is open: lower there (with snow or without), or level with no snow on it
// (water, a path, a patch of bare leaves).
bool lOpen(ivec2 n, float own) {
	vec3 s = lSnow(n);
	if (s.x > -1.0e8) return s.x < own - 0.02;
	vec2 c = rColumn(n);
	if (c.x < -1.0e8) return false;
	return c.x < own + 0.02;
}

float lEdge(float d) {
	return 0.3 + 0.7 * smoothstep(0.0, INSET, d);
}

// The snow's depth over the column at l (0..1), its edges rounded where open, its surface drifting softly with the
// world position (origin is the column's corner).
float lHeight(vec2 l, bvec4 open, vec2 origin, float depth) {
	float m = 1.0;
	if (open.x) m *= lEdge(l.x);
	if (open.y) m *= lEdge(1.0 - l.x);
	if (open.z) m *= lEdge(l.y);
	if (open.w) m *= lEdge(1.0 - l.y);
	vec2 w = origin + l;
	float drift = 0.82 + 0.36 * lNoise(w * 0.55) + 0.12 * (lNoise(w * 2.7 + 17.0) - 0.5);
	return depth * m * drift;
}

void cull() {
	gl_Position = vec4(0.0, 0.0, 2.0, 1.0);
	vWorld = vec3(0.0);
	vRel = vec3(0.0);
	vNormal = vec3(0.0, 1.0, 0.0);
	vLight = 0.0;
	vShade = 1.0;
}

void main() {
	ivec2 v = ivec2(UV0 + 0.5) - ivec2(HALF);
	int side = int(Color.r * 255.0 + 0.5);
	vec2 l = Position.xz;
	bool foot = Position.y > 0.5;
	float reach = length(vec2(v) + 0.5 - CameraFrac.xz);
	if (SnowTint.w <= 0.0 || reach > float(HALF) - 0.5) {
		cull();
		return;
	}
	vec3 own = lSnow(v);
	if (own.x < -1.0e8) {
		cull();
		return;
	}
	bvec4 open = bvec4(lOpen(v + ivec2(-1, 0), own.x), lOpen(v + ivec2(1, 0), own.x), lOpen(v + ivec2(0, -1), own.x), lOpen(v + ivec2(0, 1), own.x));
	bool sideOpen = side == 0 ? open.x : side == 1 ? open.y : side == 2 ? open.z : side == 3 ? open.w : false;
	// A side towards a neighbour it runs on into is not there.
	if (side < 4 && !sideOpen) {
		cull();
		return;
	}
	// Thinner on leaves, and thinning out towards the edge of the layer (never quite flat on the block, so it cannot
	// flicker against it).
	float depth = SnowTint.w * (own.z > 0.5 ? 0.6 : 1.0);
	depth = max(depth * (1.0 - smoothstep(float(HALF) - 9.0, float(HALF) - 1.5, reach)), 0.012);
	vec2 origin = floor(CameraPos.xz) + vec2(v);
	vec2 push = vec2(0.0);
	if (open.x && l.x < 0.001) push.x -= OVERHANG;
	if (open.y && l.x > 0.999) push.x += OVERHANG;
	if (open.z && l.y < 0.001) push.y -= OVERHANG;
	if (open.w && l.y > 0.999) push.y += OVERHANG;
	float y = foot ? own.x - 0.03 : own.x + lHeight(l, open, origin, depth);
	vec3 normal;
	if (side == 4) {
		float e = 0.06;
		float x0 = lHeight(clamp(l - vec2(e, 0.0), 0.0, 1.0), open, origin, depth);
		float x1 = lHeight(clamp(l + vec2(e, 0.0), 0.0, 1.0), open, origin, depth);
		float z0 = lHeight(clamp(l - vec2(0.0, e), 0.0, 1.0), open, origin, depth);
		float z1 = lHeight(clamp(l + vec2(0.0, e), 0.0, 1.0), open, origin, depth);
		normal = normalize(vec3(x0 - x1, 2.0 * e, z0 - z1));
		vShade = 1.0;
	} else {
		normal = normalize(side == 0 ? vec3(-1.0, 0.3, 0.0) : side == 1 ? vec3(1.0, 0.3, 0.0) : side == 2 ? vec3(0.0, 0.3, -1.0) : vec3(0.0, 0.3, 1.0));
		// The foot of a side is in its own shade.
		vShade = foot ? 0.62 : 0.94;
	}
	vec3 rel = vec3(float(v.x) - CameraFrac.x + l.x + push.x, y - CameraPos.y, float(v.y) - CameraFrac.z + l.y + push.y);
	vRel = rel;
	vWorld = CameraPos.xyz + rel;
	vNormal = normal;
	vLight = own.y;
	gl_Position = ViewProj * vec4(rel, 1.0);
}
