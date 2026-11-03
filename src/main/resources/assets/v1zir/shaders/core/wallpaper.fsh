#version 150

#moj_import <v1zir:wallpaper_params.glsl>

// Dedicated main menu wallpapers (render/wallpaper/Wallpaper, SHADER kind), rendered at reduced resolution like the
// menu sky and upsampled by menu_background. Calm by design: large soft shapes moving slowly, nothing busy in the
// middle where the clock sits. Every one costs a handful of noise lookups per pixel.

in vec2 texCoord;

out vec4 fragColor;

const int EMBER = 0;
const int TIDE = 1;
const int SILK = 2;
const int STARDUST = 3;
const int GLOW = 4;
const int DUNES = 5;

float wHash(vec2 p) {
	vec3 p3 = fract(vec3(p.xyx) * 0.1031);
	p3 += dot(p3, p3.yzx + 33.33);
	return fract((p3.x + p3.y) * p3.z);
}

vec4 wHash4(vec2 p) {
	vec4 p4 = fract(vec4(p.xyxy) * vec4(0.1031, 0.1030, 0.0973, 0.1099));
	p4 += dot(p4, p4.wzxy + 33.33);
	return fract((p4.xxyz + p4.yzzw) * p4.zywx);
}

float wNoise(vec2 p) {
	vec2 i = floor(p);
	vec2 f = fract(p);
	vec2 u = f * f * (3.0 - 2.0 * f);
	return mix(mix(wHash(i), wHash(i + vec2(1.0, 0.0)), u.x), mix(wHash(i + vec2(0.0, 1.0)), wHash(i + vec2(1.0, 1.0)), u.x), u.y);
}

float wFbm(vec2 p, int octaves) {
	float sum = 0.0;
	float amp = 0.5;
	for (int i = 0; i < 5; i++) {
		if (i >= octaves) break;
		sum += amp * wNoise(p);
		p = mat2(1.6, 1.2, -1.2, 1.6) * p;
		amp *= 0.5;
	}
	return sum;
}

float wBlob(vec2 q, vec2 center, float size) {
	vec2 d = q - center;
	return exp(-dot(d, d) * size);
}

// Mesh gradient: five colour points drifting on slow Lissajous paths, blended with gaussian weights over a dark base.
// The domain is warped a little so the blends flow instead of staying round.
vec3 meshGradient(vec2 p, float t, vec3 base, vec3 c0, vec3 c1, vec3 c2, vec3 c3, vec3 c4) {
	vec2 q = p + 0.07 * vec2(sin(p.y * 2.3 + t * 0.05) + sin(p.x * 1.4 - t * 0.04), sin(p.x * 1.9 - t * 0.045) + sin(p.y * 1.2 + t * 0.035));
	float w0 = wBlob(q, vec2(-0.66, 0.30) + 0.20 * vec2(sin(t * 0.071), cos(t * 0.053)), 7.0);
	float w1 = wBlob(q, vec2(0.62, 0.34) + 0.18 * vec2(cos(t * 0.062 + 1.0), sin(t * 0.081)), 6.0);
	float w2 = wBlob(q, vec2(0.50, -0.40) + 0.22 * vec2(sin(t * 0.057 + 2.0), cos(t * 0.066 + 0.5)), 6.5);
	float w3 = wBlob(q, vec2(-0.58, -0.42) + 0.20 * vec2(cos(t * 0.049 + 3.0), sin(t * 0.074 + 1.2)), 7.5);
	float w4 = wBlob(q, vec2(0.05, 0.02) + 0.28 * vec2(sin(t * 0.043 + 4.0), cos(t * 0.058 + 2.2)), 9.0);
	float wb = 0.3;
	vec3 col = (base * wb + c0 * w0 + c1 * w1 + c2 * w2 + c3 * w3 + c4 * w4) / (wb + w0 + w1 + w2 + w3 + w4);
	// Light falls from the top: the lower part stays deeper so the buttons and text read well.
	return col * (0.7 + 0.3 * smoothstep(-0.5, 0.5, p.y));
}

float silkHeight(vec2 p, float t) {
	vec2 q = p + 0.16 * vec2(sin(p.y * 1.9 + t * 0.07), sin(p.x * 1.6 - t * 0.06));
	q += 0.04 * vec2(sin(q.y * 4.1 - t * 0.05), sin(q.x * 3.7 + t * 0.04));
	return sin(q.y * 6.4 - q.x * 4.2 + t * 0.06) * 0.6 + sin(q.y * 3.1 - q.x * 3.6 - t * 0.05) * 0.18 + sin(q.x * 7.9 + q.y * 2.7 - t * 0.04) * 0.08;
}

// Liquid silk: a smooth height field of diagonal folds lit from the top left; near-black hollows, a deep wine body
// and a warm satin sheen running along the crests.
vec3 silk(vec2 p, vec2 parallax, float t) {
	p += parallax * 0.03;
	float e = 0.003;
	float h = silkHeight(p, t);
	vec2 g = vec2(silkHeight(p + vec2(e, 0.0), t) - h, silkHeight(p + vec2(0.0, e), t) - h) / e;
	// Slopes facing the light (top left) brighten, the others fall into shadow.
	float lit = dot(g, vec2(-0.55, 0.83)) * 0.3;
	float shade = 0.5 + 0.5 * lit / sqrt(1.0 + lit * lit);
	vec3 col = mix(vec3(0.008, 0.004, 0.008), vec3(0.19, 0.045, 0.085), smoothstep(0.1, 0.75, shade));
	col += vec3(1.0, 0.58, 0.42) * pow(shade, 6.0) * 0.42 + vec3(1.0, 0.80, 0.66) * pow(shade, 16.0) * 0.32;
	return col * (0.7 + 0.3 * smoothstep(-0.6, 0.5, p.y));
}

// Deep space with out-of-focus lights: four layers of soft discs drifting up, the nearest largest and softest,
// quieter around the middle.
vec3 stardust(vec2 p, vec2 parallax, float t) {
	vec3 col = mix(vec3(0.010, 0.012, 0.030), vec3(0.030, 0.030, 0.075), smoothstep(-0.5, 0.6, p.y));
	float haze = wFbm(p * 1.4 + vec2(t * 0.012, 2.0), 4);
	col += vec3(0.16, 0.06, 0.22) * smoothstep(0.42, 0.85, haze) * 0.45;
	col += vec3(0.02, 0.10, 0.13) * smoothstep(0.48, 0.9, wFbm(p * 1.1 + vec2(3.7, -t * 0.01), 3)) * 0.45;
	float calm = 0.3 + 0.7 * smoothstep(0.08, 0.5, length(p * vec2(0.65, 1.25)));
	for (int i = 0; i < 4; i++) {
		float fi = float(i);
		float k = fi / 3.0;
		vec2 q = p * (2.6 + fi * 2.1) + vec2(fi * 7.31, -t * (0.035 + 0.02 * fi)) - parallax * (0.5 - 0.12 * fi);
		vec4 h = wHash4(floor(q) + fi * 19.7);
		if (h.w > 0.3 - 0.04 * fi) continue;
		float r = mix(0.12, 0.24, h.z);
		float d = length(fract(q) - (0.25 + 0.5 * h.xy));
		float soft = mix(0.45, 0.12, k) * r;
		float disc = 1.0 - smoothstep(r - soft, r, d);
		if (disc <= 0.0) continue;
		float rim = smoothstep(r * 0.35, r, d) * disc;
		float twinkle = 0.7 + 0.3 * sin(t * (0.25 + h.x * 0.4) + h.y * 6.283);
		vec3 tint = h.y < 0.55 ? mix(vec3(1.0, 0.58, 0.28), vec3(1.0, 0.38, 0.45), h.x) : mix(vec3(0.50, 0.72, 1.0), vec3(0.78, 0.58, 1.0), h.x);
		col += tint * (disc * 0.5 + rim * 0.3) * twinkle * mix(0.34, 0.2, k) * calm;
	}
	return col;
}

// A minimal aurora: two soft curtains of light low on the screen whose glow rises and fades, over a dark night.
vec3 glow(vec2 p, vec2 parallax, float t) {
	vec3 col = mix(vec3(0.006, 0.012, 0.022), vec3(0.012, 0.028, 0.045), smoothstep(-0.5, 0.5, p.y));
	float x = p.x + parallax.x * 0.04;
	for (int i = 0; i < 2; i++) {
		float fi = float(i);
		float edge = -0.16 + fi * 0.12 + 0.16 * (wNoise(vec2(x * 1.2 + fi * 3.1 + t * 0.035, fi * 5.0)) - 0.5) + 0.03 * sin(x * 2.3 + t * 0.06 + fi * 2.0);
		float dy = p.y - edge;
		float body = dy > 0.0 ? exp(-dy * (2.6 + fi * 2.0)) : exp(-dy * dy * 500.0);
		float rays = 0.45 + 0.55 * wNoise(vec2(x * 7.0 + fi * 11.0 + dy * 1.5, t * 0.12));
		vec3 low = i == 0 ? vec3(0.10, 0.95, 0.62) : vec3(0.18, 0.70, 0.95);
		vec3 high = i == 0 ? vec3(0.32, 0.30, 0.95) : vec3(0.62, 0.26, 0.86);
		col += mix(low, high, clamp(dy * 2.4, 0.0, 1.0)) * body * rays * (0.5 - fi * 0.2);
	}
	// A faint reflection of the glow on the ground haze below.
	col += vec3(0.04, 0.16, 0.14) * exp(-abs(p.y + 0.42) * 9.0) * 0.5;
	return col;
}

// Glossy dunes at dusk: five smooth ridges in front of a warm horizon; their crests catch the low sun.
vec3 dunes(vec2 p, vec2 parallax, float t) {
	float horizon = -0.06;
	float sy = p.y - horizon;
	vec3 col = mix(vec3(0.92, 0.40, 0.26), vec3(0.30, 0.10, 0.26), smoothstep(0.0, 0.2, sy));
	col = mix(col, vec3(0.035, 0.025, 0.075), smoothstep(0.12, 0.6, sy));
	vec2 sun = vec2(0.42 + parallax.x * 0.01, horizon + 0.035);
	float sd = length((p - sun) * vec2(1.0, 1.5));
	col += vec3(1.0, 0.52, 0.28) * (exp(-sd * 8.0) * 0.45 + exp(-sd * 45.0) * 0.5);
	float pixel = 1.5 / max(WallView.w, 1.0);
	for (int i = 0; i < 5; i++) {
		float fi = float(i);
		float k = fi / 4.0;
		float f1 = 2.0 - k * 0.9;
		float f2 = 4.6 - k * 2.2;
		float x = p.x + parallax.x * (0.006 + 0.03 * k) + fi * 1.7;
		float s1 = x * f1 + t * (0.02 + 0.01 * fi) + fi;
		float s2 = x * f2 - t * 0.015 + fi * 2.3;
		float amp = 0.018 + 0.06 * k * k;
		float base = horizon - 0.01 - fi * 0.035 - k * k * 0.16;
		float y = base + amp * (0.62 * sin(s1) + 0.38 * sin(s2));
		float d = p.y - y;
		float cover = clamp(0.5 - d / pixel, 0.0, 1.0);
		if (cover <= 0.0) continue;
		float slope = amp * (0.62 * f1 * cos(s1) + 0.38 * f2 * cos(s2));
		vec2 n = normalize(vec2(-slope, 1.0));
		float facing = clamp(dot(n, normalize(vec2(sun.x - p.x, 0.25))), 0.0, 1.0);
		// Far ridges sink into the warm haze, near ones are dark and glossy.
		vec3 dune = mix(vec3(0.36, 0.13, 0.20), vec3(0.018, 0.010, 0.026), pow(k, 0.6));
		dune *= 0.75 + 0.25 * exp(d * 6.0);
		float crest = exp(d * mix(90.0, 30.0, k));
		dune += vec3(1.0, 0.50, 0.32) * (crest * mix(0.38, 0.22, k) + pow(facing, 6.0) * mix(0.10, 0.16, k) * exp(d * 4.0));
		col = mix(col, dune, cover);
	}
	return col;
}

void main() {
	float t = WallInfo.x;
	vec2 p = (texCoord - 0.5) * vec2(WallInfo.y, 1.0);
	vec2 parallax = WallView.xy;
	int index = int(WallInfo.z + 0.5);
	vec3 col;
	if (index == EMBER) {
		col = meshGradient(p + parallax * 0.015, t, vec3(0.040, 0.014, 0.024),
			vec3(0.98, 0.38, 0.08), vec3(0.92, 0.58, 0.16), vec3(0.88, 0.20, 0.38), vec3(0.42, 0.07, 0.26), vec3(0.10, 0.025, 0.045));
	} else if (index == TIDE) {
		col = meshGradient(p + parallax * 0.015, t * 0.9 + 40.0, vec3(0.010, 0.016, 0.040),
			vec3(0.10, 0.32, 0.90), vec3(0.06, 0.66, 0.70), vec3(0.46, 0.18, 0.84), vec3(0.08, 0.14, 0.46), vec3(0.015, 0.03, 0.09));
	} else if (index == SILK) {
		col = silk(p, parallax, t);
	} else if (index == STARDUST) {
		col = stardust(p, parallax, t);
	} else if (index == GLOW) {
		col = glow(p, parallax, t);
	} else {
		col = dunes(p, parallax, t);
	}
	fragColor = vec4(col, 1.0);
}
