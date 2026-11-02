#version 150

// Bakes the cloud noise once (render/sky/CloudNoise): a tileable 64^3 volume stored as an 8 x 8
// atlas of 64 x 64 slices, each with a one texel border copied from the opposite edge, so linear
// filtering wraps inside a slice and never bleeds into the next one (528 x 528 texels).
// R = Perlin-Worley: billowy Perlin noise dilated by Worley noise, the round, connected masses of
//     the clouds. G, B, A = Worley fbm (inverted cells: round puffs) at 4, 8 and 16 cells per
//     period, from the big lobes of a cloud down to its small billows.

out vec4 fragColor;

const float SIZE = 64.0;
const float TILE = 66.0;

vec3 hash33(vec3 p3) {
	p3 = fract(p3 * vec3(0.1031, 0.1030, 0.0973));
	p3 += dot(p3, p3.yxz + 33.33);
	return fract((p3.xxy + p3.yxx) * p3.zyx);
}

// Inverted Worley noise (1 at the feature points), tileable: cells per period of p (period 1).
float worley(vec3 p, float cells) {
	vec3 q = p * cells;
	vec3 id = floor(q);
	vec3 f = q - id;
	float d = 1.0;
	for (int z = -1; z <= 1; z++) {
		for (int y = -1; y <= 1; y++) {
			for (int x = -1; x <= 1; x++) {
				vec3 o = vec3(float(x), float(y), float(z));
				vec3 r = o + hash33(mod(id + o, cells) + cells * 7.31) - f;
				d = min(d, dot(r, r));
			}
		}
	}
	return 1.0 - sqrt(d);
}

float worleyFbm(vec3 p, float cells) {
	return worley(p, cells) * 0.625 + worley(p, cells * 2.0) * 0.25 + worley(p, cells * 4.0) * 0.125;
}

// Tileable gradient noise, about -1..1.
float gradientCorner(vec3 i, vec3 f, vec3 o, float cells) {
	vec3 g = hash33(mod(i + o, cells) + cells * 3.17) * 2.0 - 1.0;
	return dot(g, f - o);
}

float perlin(vec3 p, float cells) {
	vec3 q = p * cells;
	vec3 i = floor(q);
	vec3 f = q - i;
	vec3 u = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);
	float n000 = gradientCorner(i, f, vec3(0.0, 0.0, 0.0), cells);
	float n100 = gradientCorner(i, f, vec3(1.0, 0.0, 0.0), cells);
	float n010 = gradientCorner(i, f, vec3(0.0, 1.0, 0.0), cells);
	float n110 = gradientCorner(i, f, vec3(1.0, 1.0, 0.0), cells);
	float n001 = gradientCorner(i, f, vec3(0.0, 0.0, 1.0), cells);
	float n101 = gradientCorner(i, f, vec3(1.0, 0.0, 1.0), cells);
	float n011 = gradientCorner(i, f, vec3(0.0, 1.0, 1.0), cells);
	float n111 = gradientCorner(i, f, vec3(1.0, 1.0, 1.0), cells);
	return mix(
		mix(mix(n000, n100, u.x), mix(n010, n110, u.x), u.y),
		mix(mix(n001, n101, u.x), mix(n011, n111, u.x), u.y),
		u.z) * 1.6;
}

void main() {
	vec2 texel = floor(gl_FragCoord.xy);
	vec2 tile = floor(texel / TILE);
	// Texel inside its slice, the border wrapping to the opposite edge.
	vec2 local = mod(texel - tile * TILE - 1.0, SIZE);
	float slice = tile.y * 8.0 + tile.x;
	vec3 p = (vec3(local, slice) + 0.5) / SIZE;

	float w1 = worley(p, 4.0);
	float w2 = worley(p, 8.0);
	float w3 = worley(p, 16.0);
	float w4 = worley(p, 32.0);
	float g = w1 * 0.625 + w2 * 0.25 + w3 * 0.125;
	float b = w2 * 0.625 + w3 * 0.25 + w4 * 0.125;
	float a = w3 * 0.75 + w4 * 0.25;

	float pf = perlin(p, 4.0) + perlin(p, 8.0) * 0.5 + perlin(p, 16.0) * 0.25 + perlin(p, 32.0) * 0.125;
	pf = clamp(pf * 0.8 + 0.5, 0.0, 1.0);
	// Perlin noise dilated by the Worley cells: the cells' round cores stay, Perlin links them up.
	float r = g + pf * (1.0 - g);
	fragColor = clamp(vec4(r, g, b, a), 0.0, 1.0);
}
