// Reflections and rain wetness (render/Reflections): the surface under a pixel, rebuilt from the
// depth buffer and the block textures of render/SurfaceMap, and what it reflects.

layout(std140) uniform ReflectParams {
	mat4 ViewProj;    // camera-relative world position to clip space
	mat4 InvViewProj;
	vec4 CameraPos;   // xyz = camera position (x and z modulo 4096, for world-space noise), w = camera block Y
	vec4 CameraBlock; // xyz = camera block modulo 128 (texture wrap), w = bottom of the world
	vec4 CameraFrac;  // xyz = camera position inside its block, w = time in seconds
	vec4 Screen;      // xy = screen size, zw = trace texture size
	vec4 Trace;       // x = march steps, y = refinement steps, z = ray length, w = material grid ready
	vec4 Shading;     // x = mode (0 rain only, 1 normal, 2 metal), y = strength, z = roughness
	vec4 Weather;     // x = wetness, y = raindrop rings on water
	vec4 LightDir;    // xyz = towards the sun or moon, w = its strength
	vec4 LightColor;
	vec4 FogColor;    // horizon colour of the frame
	vec4 Forward;     // xyz = camera forward
};

uniform sampler2D Sampler0; // the frame
uniform sampler2D Sampler1; // its depth
uniform sampler2D Sampler2; // block columns
uniform sampler2D Sampler3; // material grid

const int AIR = 0;
const int SOLID = 1;
const int WATER = 2;
const int GLOSSY = 3;
const int METAL = 4;
const int FOLIAGE = 5;
const int LAVA = 6;

// Beyond these distances (blocks) water, other shiny surfaces and rain are left alone.
const float WATER_DISTANCE = 160.0;
const float SHINE_DISTANCE = 96.0;
const float RAIN_DISTANCE = 60.0;

struct SurfaceInfo {
	vec3 pos;        // camera-relative world position
	vec3 normal;     // shading normal, waves and ripples included
	vec3 tint;       // reflection weight per channel: Fresnel, metal tint and strength
	float diffuse;   // how much of the surface's own colour stays
	float wet;       // darkening by a water film
	float metallic;  // 1 where the reflection takes the surface's colour (metal blocks)
	float satin;     // how much the surface turns into polished satin metal (Metal mode)
	float roughness;
	float glint;     // weight of the sun or moon highlight
	float exposure;  // 1 under the open sky, 0 under a roof
};

float rDepth(ivec2 p) {
	return texelFetch(Sampler1, clamp(p, ivec2(0), ivec2(Screen.xy) - 1), 0).r;
}

vec3 rPosition(vec2 pixel, float depth) {
	vec4 h = InvViewProj * vec4(pixel / Screen.xy * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
	return h.xyz / h.w;
}

vec3 rPosition(ivec2 p) {
	return rPosition(vec2(p) + 0.5, rDepth(p));
}

// Clip-space w of the scene at a screen position: what marched rays are compared with.
float rSceneW(vec2 uv) {
	float depth = texelFetch(Sampler1, ivec2(uv * Screen.xy), 0).r;
	vec4 row = vec4(InvViewProj[0][3], InvViewProj[1][3], InvViewProj[2][3], InvViewProj[3][3]);
	return 1.0 / dot(row, vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0));
}

// Face normal from the four neighbours: per axis the side with the smaller step wins, so a face
// never mixes with the one behind an edge. Faces are axis aligned in Minecraft, so a normal close
// to an axis snaps to it and depth noise cannot ripple flat walls.
vec3 rNormal(ivec2 p, vec3 center) {
	ivec2 q = clamp(p, ivec2(1), ivec2(Screen.xy) - 2);
	vec3 c = q == p ? center : rPosition(q);
	vec3 l = c - rPosition(q - ivec2(1, 0));
	vec3 r = rPosition(q + ivec2(1, 0)) - c;
	vec3 d = c - rPosition(q - ivec2(0, 1));
	vec3 u = rPosition(q + ivec2(0, 1)) - c;
	vec3 n = normalize(cross(dot(r, r) < dot(l, l) ? r : l, dot(u, u) < dot(d, d) ? u : d));
	vec3 a = abs(n);
	float m = max(a.x, max(a.y, a.z));
	if (m > 0.94) n = sign(n) * step(vec3(m), a);
	return dot(n, center) > 0.0 ? -n : n;
}

int rUnpack(float v) {
	return int(v * 255.0 + 0.5);
}

// x = first free Y above the column's highest motion-blocking block, y = that block's material;
// x is -1e9 while the column is not known.
vec2 rColumn(ivec2 v) {
	vec4 c = texelFetch(Sampler2, (ivec2(CameraBlock.xz) + v + 256) & 127, 0);
	if (c.a < 0.5) return vec2(-1.0e9, 0.0);
	return vec2(float(rUnpack(c.r) * 256 + rUnpack(c.g)) + CameraBlock.w, float(rUnpack(c.b)));
}

// Material of the block holding a camera-relative point: from the grid near the camera, further
// out the top block of its column, air above that (entities, plants) and plain blocks below.
int rMaterial(vec3 rel) {
	ivec3 v = ivec3(floor(CameraFrac.xyz + rel));
	if (Trace.w > 0.5 && max(abs(v.x), abs(v.z)) <= 24 && abs(v.y) <= 12) {
		ivec3 t = ivec3(CameraBlock.xyz) + v + 256;
		int layer = t.y & 31;
		vec4 c = texelFetch(Sampler3, ivec2(t.x & 63, (layer >> 2) * 64 + (t.z & 63)), 0);
		return rUnpack(c[layer & 3]);
	}
	if (max(abs(v.x), abs(v.z)) > 60) return SOLID;
	vec2 column = rColumn(v.xz);
	float y = CameraPos.w + float(v.y);
	if (y >= column.x) return column.x < -1.0e8 ? SOLID : AIR;
	return y >= column.x - 1.0 ? int(column.y) : SOLID;
}

float rOpen(ivec2 v, float y) {
	if (max(abs(v.x), abs(v.y)) > 60) return 1.0;
	float top = rColumn(v).x;
	// A step one block up still counts as mostly open; two blocks of cover keep the rain off.
	return top < -1.0e8 ? 0.0 : clamp((y - top) * 0.6 + 1.2, 0.0, 1.0);
}

// How open to the sky a camera-relative point in the air is, blended between the four nearest
// columns so the edge of an overhang stays soft.
float rExposure(vec3 rel) {
	vec3 w = CameraFrac.xyz + rel;
	vec2 q = w.xz - 0.5;
	vec2 b = floor(q);
	vec2 f = q - b;
	ivec2 v = ivec2(b);
	float y = CameraPos.w + w.y;
	return mix(mix(rOpen(v, y), rOpen(v + ivec2(1, 0), y), f.x),
		mix(rOpen(v + ivec2(0, 1), y), rOpen(v + ivec2(1, 1), y), f.x), f.y);
}

float rHash(vec2 p) {
	vec3 p3 = fract(vec3(p.xyx) * 0.1031);
	p3 += dot(p3, p3.yzx + 33.33);
	return fract((p3.x + p3.y) * p3.z);
}

float rNoise(vec2 p) {
	vec2 i = floor(p);
	vec2 f = p - i;
	f = f * f * (3.0 - 2.0 * f);
	return mix(mix(rHash(i), rHash(i + vec2(1.0, 0.0)), f.x), mix(rHash(i + vec2(0.0, 1.0)), rHash(i + 1.0), f.x), f.y);
}

// Gentle wind waves on water: the slope of four travelling sines.
vec2 rWaves(vec2 xz) {
	float t = CameraFrac.w;
	vec2 s = vec2(0.8, 0.6) * cos(dot(xz, vec2(0.8, 0.6)) * 1.9 + t * 1.5);
	s += vec2(-0.5, 0.86) * (0.6 * cos(dot(xz, vec2(-0.5, 0.86)) * 3.1 + t * 2.0));
	s += vec2(0.96, -0.28) * (0.35 * cos(dot(xz, vec2(0.96, -0.28)) * 5.3 + t * 2.7));
	s += vec2(-0.24, -0.97) * (0.2 * cos(dot(xz, vec2(-0.24, -0.97)) * 8.7 + t * 3.4));
	return s * 0.035;
}

// Raindrop rings: every cell of two offset grids gets one drop at a random spot and time.
vec2 rRipples(vec2 xz) {
	vec2 slope = vec2(0.0);
	for (int i = 0; i < 2; i++) {
		vec2 q = xz * 1.4 + float(i) * vec2(0.5, 0.37);
		vec2 cell = floor(q);
		float seed = rHash(cell + float(i) * 57.0);
		vec2 d = q - cell - 0.25 - 0.5 * vec2(seed, rHash(cell.yx + 13.0));
		float phase = fract(CameraFrac.w * 1.1 + seed * 7.0);
		float dist = length(d);
		float e = dist - 0.45 * phase;
		float fade = (1.0 - phase) * (1.0 - phase);
		slope += d / max(dist, 0.001) * (cos(e * 45.0) * exp(-e * e * 350.0) * fade);
	}
	return slope * 0.4 * Weather.y;
}

float rSchlick(float f0, float cosTheta) {
	float m = 1.0 - clamp(cosTheta, 0.0, 1.0);
	float m2 = m * m;
	return f0 + (1.0 - f0) * m2 * m2 * m;
}

// Metal reflects in its own colour: Schlick with F0 from the block's hue (saturated, so grass stays
// green under a blue sky) and some of its texture. Rough metal keeps little of the bright grazing
// sky, so the ground towards the horizon does not wash out.
vec3 rMetal(vec3 albedo, float cosTheta, float roughness) {
	float peak = max(albedo.r, max(albedo.g, albedo.b));
	vec3 hue = albedo / max(peak, 0.04) * mix(0.35, 0.9, smoothstep(0.03, 0.6, peak));
	vec3 f0 = mix(hue, sqrt(albedo) * 0.9, 0.35);
	float m = 1.0 - clamp(cosTheta, 0.0, 1.0);
	return f0 + max(vec3(0.75 - 1.5 * roughness) - f0, 0.0) * (m * m * m * m * m);
}

// Polished satin metal (Metal mode): the block's texture smoothed away by averaging the frame over
// half a block of the surface around the point (taps on its plane, so other surfaces never leak in),
// then turned into a bright pale base under the same light that only hints at the block's hue and
// takes a little of the sky's colour, as polished metal under an open sky does.
vec3 rSatin(ivec2 p, SurfaceInfo s) {
	vec3 n = s.normal;
	vec3 t1 = normalize(cross(n, abs(n.y) > 0.9 ? vec3(1.0, 0.0, 0.0) : vec3(0.0, 1.0, 0.0)));
	vec3 t2 = cross(n, t1);
	vec3 here = texelFetch(Sampler0, p, 0).rgb;
	vec3 sum = here;
	float count = 1.0;
	for (int i = 0; i < 8; i++) {
		float a = float(i) * 2.39996;
		vec3 q = s.pos + (t1 * cos(a) + t2 * sin(a)) * (0.12 + 0.05 * float(i));
		vec4 c = ViewProj * vec4(q, 1.0);
		if (c.w <= 0.1) continue;
		ivec2 t = ivec2((c.xy / c.w * 0.5 + 0.5) * Screen.xy);
		if (any(lessThan(t, ivec2(0))) || any(greaterThanEqual(t, ivec2(Screen.xy)))) continue;
		if (abs(dot(rPosition(t) - s.pos, n)) > 0.06) continue;
		sum += texelFetch(Sampler0, t, 0).rgb;
		count += 1.0;
	}
	vec3 average = sum / count;
	const vec3 LUMA = vec3(0.2126, 0.7152, 0.0722);
	float light = dot(average, LUMA);
	vec3 hint = mix(vec3(1.0), average / max(light, 0.001), 0.12);
	hint *= mix(vec3(1.0), FogColor.rgb / max(dot(FogColor.rgb, LUMA), 0.05), 0.2 * s.exposure);
	// A faint trace of the texture stays.
	float detail = mix(1.0, dot(here, LUMA) / max(light, 0.001), 0.12);
	return clamp(hint * (0.9 * pow(light / 0.45, 0.6) * detail), 0.0, 1.0);
}

// Everything the passes need about the surface under a full-resolution pixel; false where nothing
// changes (sky, too far, air, entities, nothing shiny and dry).
bool rSurface(ivec2 p, out SurfaceInfo s) {
	float depth = rDepth(p);
	if (depth >= 1.0) return false;
	vec3 pos = rPosition(vec2(p) + 0.5, depth);
	float dist = length(pos);
	if (dist > WATER_DISTANCE) return false;
	vec3 view = pos / dist;
	// A little further along the view ray is inside the block that was hit.
	int material = rMaterial(pos + view * 0.04);
	if (material == AIR || material == LAVA) return false;
	int mode = int(Shading.x + 0.5);
	// Fabulous graphics keep water out of the depth buffer: under the water of a column the surface
	// is where the view ray crosses the water plane.
	bool plane = false;
	if (mode != 0 && material != WATER && dist < 56.0 && view.y < 0.0) {
		float eye = CameraPos.w + CameraFrac.y;
		// Just in front of the hit, so the side walls of a pool count as under its water.
		vec2 column = rColumn(ivec2(floor(CameraFrac.xz + pos.xz - view.xz * 0.05)));
		float surface = column.x - 0.111;
		if (int(column.y) == WATER && eye > surface && eye + pos.y < surface - 0.05) {
			vec3 crossing = view * ((surface - eye) / view.y);
			vec2 above = rColumn(ivec2(floor(CameraFrac.xz + crossing.xz)));
			if (int(above.y) == WATER && abs(above.x - column.x) < 0.5) {
				pos = crossing;
				dist = length(pos);
				material = WATER;
				plane = true;
			}
		}
	}
	bool water = mode != 0 && material == WATER;
	bool metal = mode == 2 && material != WATER && dist < SHINE_DISTANCE;
	bool glossy = mode == 1 && (material == GLOSSY || material == METAL) && dist < SHINE_DISTANCE;
	bool rain = Weather.x > 0.001 && material != WATER && dist < RAIN_DISTANCE;
	if (!water && !metal && !glossy && !rain) return false;
	// Measured in the air just in front of the surface.
	float exposure = rExposure(pos - view * 0.3);
	float open = exposure * (1.0 - smoothstep(RAIN_DISTANCE - 12.0, RAIN_DISTANCE, dist));
	if (!water && !metal && !glossy && open < 0.01) return false;

	vec3 n = plane ? vec3(0.0, 1.0, 0.0) : rNormal(p, pos);
	vec2 xz = CameraPos.xz + pos.xz;
	float strength = Shading.y;
	s.pos = pos;
	s.normal = n;
	s.tint = vec3(0.0);
	s.diffuse = 1.0;
	s.wet = 0.0;
	s.metallic = 0.0;
	s.satin = 0.0;
	s.roughness = 0.0;
	s.glint = 0.0;
	s.exposure = exposure;
	if (water) {
		if (n.y > 0.5) {
			vec2 slope = rWaves(xz) + rRipples(xz) * exposure;
			s.normal = normalize(vec3(-slope.x, 1.0, -slope.y));
		}
		float f = min(rSchlick(0.02, dot(-view, s.normal)) * strength, 1.0);
		s.tint = vec3(f);
		s.diffuse = 1.0 - f;
		s.glint = exposure;
		return true;
	}
	float cosV = dot(-view, n);
	if (metal) {
		// Polished satin metal: a smooth pale base (rSatin) under soft reflections that take their
		// colour from the sky and the scene, with a gentle Fresnel.
		float m = 1.0 - clamp(cosV, 0.0, 1.0);
		float f = min((0.28 + 0.5 * m * m * m) * strength, 1.0);
		s.tint = vec3(material == FOLIAGE ? 0.5 * f : f);
		s.diffuse = 1.0 - s.tint.x;
		s.satin = (material == FOLIAGE ? 0.5 : 1.0) * min(strength, 1.0);
		s.roughness = 0.3 + 0.5 * Shading.z;
		s.glint = exposure * 0.6;
	} else if (glossy) {
		// Metal blocks keep their colour in the reflection; glass, ice and polished stone a faint sheen.
		s.roughness = 0.08 + 0.4 * Shading.z;
		s.tint = min(material == METAL ? rMetal(texelFetch(Sampler0, p, 0).rgb, cosV, s.roughness) * 0.55 : vec3(rSchlick(0.04, cosV) * 0.5), 1.0) * strength;
		s.tint = min(s.tint, vec3(1.0));
		s.diffuse = 1.0 - 0.6 * dot(s.tint, vec3(0.3333));
		s.metallic = material == METAL ? 1.0 : 0.0;
		s.glint = exposure * 0.5;
	}
	if (rain) {
		// A thin film of water: darker, more saturated, glossy with fairly sharp reflections like a
		// wet road; tops soak through, walls only get damp, and the gloss varies a little over the
		// ground in large soft patches (never pools).
		float wet = clamp(Weather.x * open * (n.y > 0.5 ? 1.0 : 0.45), 0.0, 1.0) * (material == FOLIAGE ? 0.5 : 1.0);
		float gloss = 0.55 + 0.45 * rNoise(xz * 0.3);
		float f = rSchlick(0.04, cosV) * 0.65 * wet * gloss;
		if (f > max(s.tint.r, max(s.tint.g, s.tint.b))) {
			s.tint = vec3(f);
			s.roughness = metal || glossy ? min(s.roughness, 0.15) : 0.15;
		}
		s.diffuse = min(s.diffuse, 1.0 - f);
		s.wet = wet;
		s.glint = max(s.glint, open * 0.3 * wet);
	}
	return max(s.tint.r, max(s.tint.g, s.tint.b)) > 0.002 || s.wet > 0.002;
}

// What a ray that finds nothing on screen sees: the sky itself where its direction is on screen
// and uncovered (the Shader Sky included), otherwise a gradient from the horizon colour.
vec3 rSky(vec3 dir) {
	vec4 c = ViewProj * vec4(dir, 0.0);
	if (c.w > 0.0) {
		vec2 uv = c.xy / c.w * 0.5 + 0.5;
		if (all(greaterThan(uv, vec2(0.0))) && all(lessThan(uv, vec2(1.0))) && texelFetch(Sampler1, ivec2(uv * Screen.xy), 0).r >= 1.0) {
			return texture(Sampler0, uv).rgb;
		}
	}
	vec3 horizon = FogColor.rgb;
	vec3 zenith = horizon * vec3(0.62, 0.78, 1.0);
	// Below the horizon: the ground, darker than the sky.
	return dir.y >= 0.0 ? mix(horizon, zenith, sqrt(dir.y)) : horizon * mix(0.7, 0.3, sqrt(-dir.y));
}

// Marches a camera-relative ray through the depth buffer in screen space: even steps over the part
// of the ray that is on screen, then a binary search between the last two. Returns the hit's screen
// position in xy and its confidence in z (0 = no hit).
vec3 rMarch(vec3 origin, vec3 dir, float jitter) {
	const float NEAR = 0.1;
	vec4 c0 = ViewProj * vec4(origin, 1.0);
	if (c0.w < NEAR) return vec3(0.0);
	vec4 c1 = ViewProj * vec4(origin + dir * Trace.z, 1.0);
	// Keep the end in front of the camera: clip-space w is linear along the ray.
	if (c1.w < NEAR) c1 = mix(c0, c1, (c0.w - NEAR) / (c0.w - c1.w));
	vec2 s0 = c0.xy / c0.w * 0.5 + 0.5;
	vec2 s1 = c1.xy / c1.w * 0.5 + 0.5;
	// 1/w is linear in screen space.
	float k0 = 1.0 / c0.w;
	float k1 = 1.0 / c1.w;
	vec2 delta = s1 - s0;
	delta = mix(max(abs(delta), vec2(1.0e-5)), -max(abs(delta), vec2(1.0e-5)), lessThan(delta, vec2(0.0)));
	vec2 exit = mix((1.0 - s0) / delta, -s0 / delta, lessThan(delta, vec2(0.0)));
	float end = min(1.0, min(exit.x, exit.y));
	int steps = int(Trace.x);
	float stepT = end / float(steps);
	float t = stepT * jitter;
	float last = 0.0;
	float lastW = c0.w;
	for (int i = 0; i < steps; i++) {
		t += stepT;
		vec2 uv = s0 + delta * t;
		float rayW = 1.0 / mix(k0, k1, t);
		float behind = rayW - rSceneW(uv);
		// Behind the scene, but by less than a block plus the step: the ray went into it.
		if (behind > 0.0 && behind < 1.0 + abs(rayW - lastW) * 1.5 + rayW * 0.02) {
			float a = last;
			float b = t;
			int refinements = int(Trace.y);
			for (int j = 0; j < refinements; j++) {
				float m = 0.5 * (a + b);
				if (1.0 / mix(k0, k1, m) > rSceneW(s0 + delta * m)) b = m;
				else a = m;
			}
			vec2 hit = s0 + delta * b;
			// Still far behind the surface found: the ray passed behind an object, keep marching.
			float hitW = 1.0 / mix(k0, k1, b);
			if (hitW - rSceneW(hit) < 0.35 + hitW * 0.01) {
				vec2 edge = min(hit, 1.0 - hit);
				return vec3(hit, smoothstep(0.0, 0.06, min(edge.x, edge.y)));
			}
		}
		last = t;
		lastW = rayW;
	}
	return vec3(0.0);
}
