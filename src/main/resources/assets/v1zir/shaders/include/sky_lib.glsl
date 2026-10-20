// ============================================================================
//  V1zir Visuals - shader sky library
//
//  Shared by the in-world sky pass and the animated main menu background.
//  The host shader declares the SkyParams values (see sky_params.glsl):
//
//    SunDir   xyz = direction towards the sun,  w = unused
//    MoonDir  xyz = direction towards the moon, w = moon phase (0 = full .. 1)
//    Timing   x = animation time (seconds, already speed-scaled)
//             y = thunder gradient, z = rain gradient, w = star brightness multiplier
//    ColorA   rgb = preset colour A (tint / zenith),  a = colour strength
//    ColorB   rgb = preset colour B (secondary / horizon)
//    Settings x = brightness, y = cloud coverage, z = star density, w = quality (0..2)
//    Camera   xyz = camera position (blocks), w = preset index
//    Extra    x = horizon fade (1 = fade below horizon), y = clouds enabled,
//             z = vanilla sun & moon (1 = do not draw ours), w = dither seed
// ============================================================================

#define V_PI 3.14159265359
#define V_TAU 6.28318530718

#define PRESET_REALISTIC 0
#define PRESET_MAGMA 1
#define PRESET_AURORA 2
#define PRESET_NEBULA 3
#define PRESET_SUNSET 4
#define PRESET_INK 5
#define PRESET_SYNTHWAVE 6
#define PRESET_BLOOD_MOON 7
#define PRESET_CUSTOM 8

// ---------------------------------------------------------------------------
//  Hashing & noise (sine-free hashes keep the patterns stable on every GPU)
// ---------------------------------------------------------------------------

float vHash12(vec2 p) {
	vec3 p3 = fract(vec3(p.xyx) * 0.1031);
	p3 += dot(p3, p3.yzx + 33.33);
	return fract((p3.x + p3.y) * p3.z);
}

float vHash13(vec3 p3) {
	p3 = fract(p3 * 0.1031);
	p3 += dot(p3, p3.zyx + 31.32);
	return fract((p3.x + p3.y) * p3.z);
}

vec2 vHash22(vec2 p) {
	vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
	p3 += dot(p3, p3.yzx + 33.33);
	return fract((p3.xx + p3.yz) * p3.zy);
}

vec3 vHash33(vec3 p3) {
	p3 = fract(p3 * vec3(0.1031, 0.1030, 0.0973));
	p3 += dot(p3, p3.yxz + 33.33);
	return fract((p3.xxy + p3.yxx) * p3.zyx);
}

float vNoise2(vec2 p) {
	vec2 i = floor(p);
	vec2 f = fract(p);
	vec2 u = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);
	float a = vHash12(i);
	float b = vHash12(i + vec2(1.0, 0.0));
	float c = vHash12(i + vec2(0.0, 1.0));
	float d = vHash12(i + vec2(1.0, 1.0));
	return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float vNoise3(vec3 p) {
	vec3 i = floor(p);
	vec3 f = fract(p);
	vec3 u = f * f * (3.0 - 2.0 * f);
	float n000 = vHash13(i);
	float n100 = vHash13(i + vec3(1.0, 0.0, 0.0));
	float n010 = vHash13(i + vec3(0.0, 1.0, 0.0));
	float n110 = vHash13(i + vec3(1.0, 1.0, 0.0));
	float n001 = vHash13(i + vec3(0.0, 0.0, 1.0));
	float n101 = vHash13(i + vec3(1.0, 0.0, 1.0));
	float n011 = vHash13(i + vec3(0.0, 1.0, 1.0));
	float n111 = vHash13(i + vec3(1.0, 1.0, 1.0));
	return mix(
		mix(mix(n000, n100, u.x), mix(n010, n110, u.x), u.y),
		mix(mix(n001, n101, u.x), mix(n011, n111, u.x), u.y),
		u.z);
}

const mat2 V_ROT2 = mat2(0.80, 0.60, -0.60, 0.80);
const mat3 V_ROT3 = mat3(0.00, 0.80, 0.60, -0.80, 0.36, -0.48, -0.60, -0.48, 0.64);

float vFbm2(vec2 p, int octaves) {
	float sum = 0.0;
	float amp = 0.5;
	float norm = 0.0;
	for (int i = 0; i < 8; i++) {
		if (i >= octaves) break;
		sum += amp * vNoise2(p);
		norm += amp;
		p = V_ROT2 * p * 2.03 + vec2(1.7, 9.2);
		amp *= 0.5;
	}
	return sum / norm;
}

float vFbm3(vec3 p, int octaves) {
	float sum = 0.0;
	float amp = 0.5;
	float norm = 0.0;
	for (int i = 0; i < 8; i++) {
		if (i >= octaves) break;
		sum += amp * vNoise3(p);
		norm += amp;
		p = V_ROT3 * p * 2.02 + vec3(3.1, 1.7, 5.3);
		amp *= 0.5;
	}
	return sum / norm;
}

// Ridged fbm - sharp creases, used for molten veins and smoke filaments.
float vRidged2(vec2 p, int octaves) {
	float sum = 0.0;
	float amp = 0.5;
	float norm = 0.0;
	float prev = 1.0;
	for (int i = 0; i < 8; i++) {
		if (i >= octaves) break;
		float n = 1.0 - abs(vNoise2(p) * 2.0 - 1.0);
		n *= n;
		sum += n * amp * prev;
		norm += amp;
		prev = n;
		p = V_ROT2 * p * 2.07 + vec2(4.1, 2.3);
		amp *= 0.5;
	}
	return sum / norm;
}

// Voronoi with exact border distance (x = distance to the nearest cell border,
// y = distance to the nearest feature point, z = cell id).
vec3 vVoronoi(vec2 x, float t) {
	vec2 n = floor(x);
	vec2 f = fract(x);
	vec2 mg = vec2(0.0);
	vec2 mr = vec2(0.0);
	float md = 8.0;
	for (int j = -1; j <= 1; j++) {
		for (int i = -1; i <= 1; i++) {
			vec2 g = vec2(float(i), float(j));
			vec2 o = vHash22(n + g);
			o = 0.5 + 0.42 * sin(t + V_TAU * o);
			vec2 r = g + o - f;
			float d = dot(r, r);
			if (d < md) {
				md = d;
				mr = r;
				mg = g;
			}
		}
	}
	float border = 8.0;
	for (int j = -2; j <= 2; j++) {
		for (int i = -2; i <= 2; i++) {
			vec2 g = mg + vec2(float(i), float(j));
			vec2 o = vHash22(n + g);
			o = 0.5 + 0.42 * sin(t + V_TAU * o);
			vec2 r = g + o - f;
			if (dot(mr - r, mr - r) > 0.00001) {
				border = min(border, dot(0.5 * (mr + r), normalize(r - mr)));
			}
		}
	}
	return vec3(border, sqrt(md), vHash12(n + mg));
}

// ---------------------------------------------------------------------------
//  Colour helpers
// ---------------------------------------------------------------------------

vec3 vAces(vec3 x) {
	const float a = 2.51;
	const float b = 0.03;
	const float c = 2.43;
	const float d = 0.59;
	const float e = 0.14;
	return clamp((x * (a * x + b)) / (x * (c * x + d) + e), 0.0, 1.0);
}

vec3 vToDisplay(vec3 linearColor) {
	return pow(max(linearColor, vec3(0.0)), vec3(1.0 / 2.2));
}

vec3 vSaturate(vec3 c, float amount) {
	float l = dot(c, vec3(0.2126, 0.7152, 0.0722));
	return mix(vec3(l), c, amount);
}

// Black-body-ish star tint: 0 = orange dwarf, 1 = blue giant.
vec3 vStarTint(float k) {
	vec3 warm = vec3(1.00, 0.72, 0.48);
	vec3 white = vec3(1.00, 0.96, 0.92);
	vec3 cold = vec3(0.66, 0.78, 1.00);
	return k < 0.5 ? mix(warm, white, k * 2.0) : mix(white, cold, (k - 0.5) * 2.0);
}

// ---------------------------------------------------------------------------
//  Shared sky elements
// ---------------------------------------------------------------------------

// Size of one screen pixel in radians (keeps stars 1-2 px wide at every resolution).
float vPixelAngle(vec3 rd) {
	return max(length(fwidth(rd)), 1e-5);
}

vec3 vStarLayer(vec3 rd, float scale, float density, float t, float pixelAngle, float sizeBoost) {
	vec3 p = rd * scale;
	vec3 base = floor(p - 0.5);
	vec3 col = vec3(0.0);
	float px = pixelAngle * scale;
	for (int i = 0; i < 8; i++) {
		vec3 cell = base + vec3(float(i & 1), float((i >> 1) & 1), float((i >> 2) & 1));
		float h = vHash13(cell + 17.0);
		if (h > density) continue;
		vec3 j = vHash33(cell);
		// Keep every star well inside its cell so the glow never gets clipped by the 2x2x2 search.
		vec3 sp = normalize(cell + 0.3 + j * 0.4);
		float d = length(rd - sp) * scale;
		float bright = pow(vHash13(cell + 3.7), 9.0) * 3.0 + 0.15;
		float radius = min(px * (0.6 + 0.35 * sqrt(bright)) * sizeBoost, 0.1);
		float core = exp(-d * d / (radius * radius));
		float halo = exp(-d * d / (radius * radius * 7.0)) * 0.1 * bright;
		float twinkle = 0.72 + 0.28 * sin(t * (1.3 + j.y * 2.9) + h * 91.0);
		col += vStarTint(j.z) * (core + halo) * bright * twinkle;
	}
	return col;
}

vec3 vStars(vec3 rd, float t, float density) {
	float pa = vPixelAngle(rd);
	vec3 s = vStarLayer(rd, 95.0, 0.075 * density, t, pa, 1.25);
	s += vStarLayer(rd, 210.0, 0.11 * density, t * 1.3, pa, 1.0) * 0.55;
	s += vStarLayer(rd, 420.0, 0.16 * density, t * 0.7, pa, 0.9) * 0.25;
	return s;
}

// In the sky cache (V1ZIR_SKY_CACHE) a panorama texel only records how visible the stars are in its
// direction (gStarWeight, stored in alpha): the screen pass draws the stars themselves, crisp at full
// resolution (vCrispStars). Presets add stars with vStarsW and lay clouds over them with vOver.
float gStarWeight = 0.0;

vec3 vStarsW(vec3 rd, float t, float density, float weight) {
#ifdef V1ZIR_SKY_CACHE
	gStarWeight += weight;
	return vec3(0.0);
#else
	return vStars(rd, t, density) * weight;
#endif
}

vec3 vOver(vec3 under, vec4 over) {
	// Even thin cloud hides most starlight, so the stars fade faster than the cloud's coverage.
	gStarWeight *= (1.0 - over.a) * (1.0 - over.a);
	return mix(under, over.rgb, over.a);
}

// Stars drawn as small round points in screen pixels: the offset to each star is mapped to pixels
// through the pseudo inverse of the view direction's screen derivatives (dx, dy), so stars stay round
// and crisp at any resolution and field of view, even at the edges of a wide one. Each star keeps the
// light it has in vStarLayer (its flux, from its angular size), spread over a fixed ~0.6 px point, so
// the faint dense layers stay faint and nothing pulses as a star crosses pixel boundaries.
vec3 vCrispStarLayer(vec3 rd, vec3 dx, vec3 dy, float scale, float density, float t, float sizeBoost) {
	vec3 base = floor(rd * scale - 0.5);
	float a = dot(dx, dx);
	float b = dot(dx, dy);
	float c = dot(dy, dy);
	float det = max(a * c - b * b, 1e-24);
	float pixelArea = sqrt(det);
	float pixelAngle = sqrt(pixelArea);
	const float SIGMA = 0.6;
	const float HALO = 2.2;
	vec3 col = vec3(0.0);
	for (int i = 0; i < 8; i++) {
		vec3 cell = base + vec3(float(i & 1), float((i >> 1) & 1), float((i >> 2) & 1));
		float h = vHash13(cell + 17.0);
		if (h > density) continue;
		vec3 j = vHash33(cell);
		vec3 o = rd - normalize(cell + 0.3 + j * 0.4);
		float ox = dot(o, dx);
		float oy = dot(o, dy);
		vec2 px = vec2(c * ox - b * oy, a * oy - b * ox) / det;
		float r2 = dot(px, px);
		if (r2 > 49.0) continue;
		float bright = pow(vHash13(cell + 3.7), 9.0) * 3.0 + 0.15;
		float radius = min(pixelAngle * (0.6 + 0.35 * sqrt(bright)) * sizeBoost, 0.1 / scale);
		float flux = 3.14159 * radius * radius / pixelArea * bright;
		// Faint stars would only fill the sky with a carpet of dots: keep the ones that read as stars.
		if (flux < 0.09) continue;
		float core = flux * exp(-r2 / (2.0 * SIGMA * SIGMA)) / (6.28318 * SIGMA * SIGMA);
		float halo = 0.7 * flux * exp(-r2 / (2.0 * HALO * HALO)) / (6.28318 * HALO * HALO);
		float twinkle = 0.86 + 0.14 * sin(t * (1.1 + j.y * 1.9) + h * 91.0);
		col += vStarTint(j.z) * (core + halo) * twinkle;
	}
	return col;
}

vec3 vCrispStars(vec3 rd, vec3 dx, vec3 dy, float t, float density) {
	// Two layers: the third, densest one of vStars only added dots too faint to matter.
	vec3 s = vCrispStarLayer(rd, dx, dy, 95.0, 0.075 * density, t, 1.15);
	s += vCrispStarLayer(rd, dx, dy, 210.0, 0.11 * density, t * 1.3, 1.0) * 0.45;
	return s;
}

// Milky way: a tilted great circle with a warm bulge, glow and dark dust lanes.
vec3 vMilkyWay(vec3 rd, float strength) {
	vec3 n = normalize(vec3(0.28, 0.62, -0.73));
	vec3 u = normalize(cross(n, vec3(0.0, 1.0, 0.0)));
	vec3 v = cross(n, u);
	float lat = dot(rd, n);
	float lon = atan(dot(rd, v), dot(rd, u));
	float band = exp(-lat * lat * 22.0);
	if (band < 0.002) return vec3(0.0);
	float dl = lon - 0.9;
	float bulge = exp(-dl * dl * 1.2) * exp(-lat * lat * 40.0);
	vec3 q = rd * 3.0;
	float glow = vFbm3(q * 2.2, 5);
	float detail = vFbm3(q * 7.0 + 11.0, 4);
	float dust = smoothstep(0.48, 0.78, vFbm3(q * 4.5 + 3.1, 5)) * exp(-lat * lat * 90.0);
	vec3 cold = vec3(0.30, 0.42, 0.85);
	vec3 warm = vec3(1.00, 0.78, 0.55);
	vec3 col = mix(cold, warm, clamp(bulge * 1.4, 0.0, 1.0));
	float intensity = band * (0.25 + 0.75 * glow) * (0.7 + 0.6 * detail) + bulge * 0.9 * glow;
	intensity *= 1.0 - dust * 0.9;
	return col * intensity * 0.32 * strength;
}

// ---------------------------------------------------------------------------
//  Physically based atmosphere (single scattering, Rayleigh + Mie)
// ---------------------------------------------------------------------------

const float V_RG = 6360.0;
const float V_RT = 6420.0;
const vec3 V_BETA_R = vec3(5.8e-3, 13.5e-3, 33.1e-3);
const float V_BETA_M = 21e-3;
const float V_HR = 7.994;
const float V_HM = 1.2;
const vec3 V_BETA_O = vec3(0.650e-3, 1.881e-3, 0.085e-3);

float vOzone(float h) {
	return max(0.0, 1.0 - abs(h - 25.0) / 15.0);
}

vec2 vRaySphere(vec3 ro, vec3 rd, float radius) {
	float b = dot(ro, rd);
	float c = dot(ro, ro) - radius * radius;
	float h = b * b - c;
	if (h < 0.0) return vec2(-1.0);
	h = sqrt(h);
	return vec2(-b - h, -b + h);
}

vec3 vTransmittance(vec3 pos, vec3 dir, int steps) {
	vec2 hit = vRaySphere(pos, dir, V_RT);
	float len = hit.y;
	float ds = len / float(steps);
	vec3 od = vec3(0.0);
	for (int i = 0; i < 8; i++) {
		if (i >= steps) break;
		vec3 p = pos + dir * (ds * (float(i) + 0.5));
		float h = length(p) - V_RG;
		if (h < 0.0) return vec3(0.0);
		od += vec3(exp(-h / V_HR), exp(-h / V_HM), vOzone(h)) * ds;
	}
	return exp(-(V_BETA_R * od.x + V_BETA_M * 1.1 * od.y + V_BETA_O * od.z));
}

// Returns linear radiance for a view direction; sunColor receives the light that
// reaches the observer directly (used for the sun disk and cloud lighting).
vec3 vAtmosphere(vec3 rd, vec3 sunDir, float intensity, int viewSteps, int lightSteps, out vec3 sunColor) {
	vec3 ro = vec3(0.0, V_RG + 0.35, 0.0);
	vec3 dir = rd;
	// Keep the horizon well defined: rays below it are bent slightly up, ground is faded later.
	dir.y = max(dir.y, -0.02);
	dir = normalize(dir);
	vec2 top = vRaySphere(ro, dir, V_RT);
	float tMax = top.y;
	vec2 ground = vRaySphere(ro, dir, V_RG);
	if (ground.x > 0.0) tMax = min(tMax, ground.x);
	// Capping the path length stands in for multiple scattering: the horizon stays a soft
	// white haze instead of turning brown.
	tMax = min(tMax, 140.0);

	float ds = tMax / float(viewSteps);
	vec3 odView = vec3(0.0);
	vec3 sumR = vec3(0.0);
	vec3 sumM = vec3(0.0);
	for (int i = 0; i < 24; i++) {
		if (i >= viewSteps) break;
		vec3 p = ro + dir * (ds * (float(i) + 0.5));
		float h = length(p) - V_RG;
		vec3 dens = vec3(exp(-h / V_HR), exp(-h / V_HM), vOzone(h)) * ds;
		odView += dens;
		vec3 tSun = vTransmittance(p, sunDir, lightSteps);
		vec3 att = exp(-(V_BETA_R * odView.x + V_BETA_M * 1.1 * odView.y + V_BETA_O * odView.z)) * tSun;
		sumR += dens.x * att;
		sumM += dens.y * att;
	}
	float mu = dot(dir, sunDir);
	float phaseR = 3.0 / (16.0 * V_PI) * (1.0 + mu * mu);
	const float g = 0.76;
	float phaseM = 3.0 / (8.0 * V_PI) * ((1.0 - g * g) * (1.0 + mu * mu)) / ((2.0 + g * g) * pow(max(1.0 + g * g - 2.0 * g * mu, 1e-4), 1.5));
	sunColor = vTransmittance(ro, sunDir, lightSteps) * intensity;
	return (sumR * V_BETA_R * phaseR + sumM * V_BETA_M * phaseM) * intensity;
}

// ---------------------------------------------------------------------------
//  Sun, moon & clouds
// ---------------------------------------------------------------------------

vec3 vSunDisk(vec3 rd, vec3 sunDir, vec3 sunColor, float size) {
	float cosA = dot(rd, sunDir);
	float angle = acos(clamp(cosA, -1.0, 1.0));
	float radius = 0.0095 * size;
	float edge = vPixelAngle(rd) * 1.5;
	float disk = 1.0 - smoothstep(radius - edge, radius + edge, angle);
	float r = clamp(angle / radius, 0.0, 1.0);
	float limb = 1.0 - 0.55 * (1.0 - sqrt(max(1.0 - r * r, 0.0)));
	vec3 col = sunColor * disk * limb * 40.0;
	col += sunColor * exp(-angle * 55.0 / size) * 0.9;
	col += sunColor * exp(-angle * 9.0) * 0.12;
	return col;
}

// Moon with phase shading, maria and craters. phase: 0 = full, 0.5 = new.
vec3 vMoon(vec3 rd, vec3 moonDir, float phase, vec3 tint, float size, out float coverage) {
	coverage = 0.0;
	float radius = 0.024 * size;
	float cosA = dot(rd, moonDir);
	float angle = acos(clamp(cosA, -1.0, 1.0));
	vec3 glow = tint * (exp(-angle * 18.0 / size) * 0.12 + exp(-angle * 4.0) * 0.025);
	if (angle > radius * 1.6) return glow;

	vec3 up = abs(moonDir.y) > 0.99 ? vec3(1.0, 0.0, 0.0) : vec3(0.0, 1.0, 0.0);
	vec3 right = normalize(cross(up, moonDir));
	vec3 top = cross(moonDir, right);
	vec2 uv = vec2(dot(rd, right), dot(rd, top)) / sin(radius);
	float r2 = dot(uv, uv);
	float edge = vPixelAngle(rd) / sin(radius) * 1.5;
	float mask = 1.0 - smoothstep(1.0 - edge, 1.0 + edge, sqrt(r2));
	coverage = mask;
	vec3 normal = vec3(uv, sqrt(max(1.0 - r2, 0.0)));

	// Surface: dark maria + craters.
	float maria = smoothstep(0.45, 0.62, vFbm2(uv * 1.6 + 4.0, 5));
	float craters = 0.0;
	for (int i = 0; i < 3; i++) {
		vec3 vor = vVoronoi(uv * (3.0 + float(i) * 2.7) + float(i) * 7.3, 0.0);
		craters += (1.0 - smoothstep(0.0, 0.32, vor.y)) * smoothstep(0.0, 0.08, vor.y) * 0.5 / (1.0 + float(i));
	}
	float albedo = 0.78 - maria * 0.28 + craters * 0.18 + (vFbm2(uv * 9.0, 3) - 0.5) * 0.12;

	// Phase lighting: light direction rotates around the moon.
	float a = phase * V_TAU;
	vec3 lightDir = normalize(vec3(sin(a), 0.0, cos(a)));
	float lambert = max(dot(normal, lightDir), 0.0);
	float earthshine = 0.035;
	vec3 surface = tint * albedo * (lambert * 1.35 + earthshine);
	return glow * (1.0 - mask) + surface * mask;
}

// Fast 2.5D cloud layer: projected plane, domain-warped fbm, self shadowing
// towards the light and forward scattering ("silver lining").
vec4 vClouds(vec3 rd, vec3 lightDir, vec3 lightColor, vec3 ambient, float coverage, float t, vec3 camPos) {
	if (rd.y <= 0.0) return vec4(0.0);
	vec2 uv = rd.xz / (rd.y + 0.045) * 0.9;
	uv += camPos.xz * 0.00035 + vec2(t * 0.011, t * 0.0045);
	vec2 warp = vec2(vFbm2(uv * 0.55 + vec2(t * 0.004, 0.0), 4), vFbm2(uv * 0.55 + vec2(5.2, 1.3), 4));
	vec2 p = uv * 1.25 + (warp - 0.5) * 1.1;
	float n = vFbm2(p, 6);
	float detail = vFbm2(p * 3.1 + 7.0, 4);
	n = n * 0.82 + detail * 0.18;
	float threshold = mix(0.66, 0.36, clamp(coverage, 0.0, 1.0));
	float density = smoothstep(threshold, threshold + 0.24, n);
	if (density <= 0.002) return vec4(0.0);

	vec2 lightStep = normalize(lightDir.xz + vec2(1e-4)) * 0.11;
	float n2 = vFbm2(p + lightStep, 4) * 0.82 + detail * 0.18;
	float n3 = vFbm2(p + lightStep * 2.2, 3) * 0.82 + detail * 0.18;
	float shadow = clamp(1.0 - ((n2 - n) * 2.2 + (n3 - n) * 1.4), 0.0, 1.0);
	float thickness = smoothstep(threshold, threshold + 0.5, n);

	float mu = dot(rd, lightDir);
	float forward = pow(max(mu, 0.0), 10.0) * (1.0 - thickness) * 3.0;
	float elevation = clamp(lightDir.y * 4.0 + 0.25, 0.0, 1.0);
	vec3 lit = lightColor * (0.45 + 0.9 * shadow + forward) * elevation;
	vec3 col = ambient * (0.9 - thickness * 0.45) + lit * (1.0 - thickness * 0.55);
	float fade = smoothstep(0.0, 0.16, rd.y);
	return vec4(col, density * fade);
}

// ---------------------------------------------------------------------------
//  Presets
// ---------------------------------------------------------------------------

vec3 vSkyRealistic(vec3 rd, float t, int quality) {
	vec3 sunDir = normalize(SunDir.xyz);
	vec3 moonDir = normalize(MoonDir.xyz);
	float rain = Timing.z;
	// The scattering integral is smooth: a few steps look the same as many.
	int viewSteps = quality == 0 ? 6 : (quality == 1 ? 8 : 12);
	int lightSteps = quality == 0 ? 2 : (quality == 1 ? 2 : 4);

	vec3 sunColor;
	vec3 col = vAtmosphere(rd, sunDir, 22.0, viewSteps, lightSteps, sunColor);

	// Night: moonlit atmosphere approximation + stars + milky way.
	float night = (1.0 - smoothstep(-0.22, 0.06, sunDir.y));
	vec3 nightSky = mix(vec3(0.010, 0.016, 0.034), vec3(0.002, 0.004, 0.012), smoothstep(0.0, 0.8, rd.y));
	nightSky += vec3(0.02, 0.035, 0.06) * exp(-max(rd.y, 0.0) * 6.0) * max(moonDir.y + 0.2, 0.0);
	col += nightSky * night;
	float starVis = night * (1.0 - rain) * Timing.w * smoothstep(-0.05, 0.12, rd.y);
	if (starVis > 0.001) {
		col += vStarsW(rd, t, Settings.z, 0.9 * starVis) + vMilkyWay(rd, 1.0) * starVis;
	}

	bool drawCelestial = Extra.z < 0.5;
	if (drawCelestial && rd.y > -0.05) {
		col += vSunDisk(rd, sunDir, sunColor, 1.6) * (1.0 - rain * 0.9);
		float cover;
		vec3 moon = vMoon(rd, moonDir, MoonDir.w, vec3(0.92, 0.94, 1.0), 1.5, cover);
		float moonVis = smoothstep(-0.05, 0.25, -sunDir.y + 0.15) * (1.0 - rain * 0.85);
		col = mix(col, col * (1.0 - cover * 0.9) + moon * 0.9, moonVis);
	}

	if (Extra.y > 0.5) {
		vec3 lightDir = sunDir.y > -0.1 ? sunDir : moonDir;
		vec3 lightCol = sunDir.y > -0.1 ? sunColor * 0.09 : vec3(0.08, 0.10, 0.16);
		vec3 dummy;
		vec3 ambient = vAtmosphere(normalize(vec3(0.0, 1.0, 0.0)), sunDir, 22.0, 6, 2, dummy) * 0.9 + vec3(0.012, 0.016, 0.03) * night;
		ambient = mix(ambient, vec3(dot(ambient, vec3(0.33))), rain * 0.7);
		vec4 clouds = vClouds(rd, lightDir, lightCol, ambient, Settings.y + rain * 0.6, t, Camera.xyz);
		col = vOver(col, clouds);
	}

	// Below the horizon: darker haze (terrain covers most of it in game).
	col *= mix(1.0, 0.38, (1.0 - smoothstep(-0.25, 0.0, rd.y)));

	// Overcast: desaturate and darken.
	float grey = dot(col, vec3(0.2126, 0.7152, 0.0722));
	col = mix(col, vec3(grey) * 0.6, rain * 0.75);
	col *= 1.0 - Timing.y * 0.5;

	col = vAces(col * 1.05);
	return vToDisplay(col);
}

vec3 vSkyMagma(vec3 rd, float t) {
	vec3 tint = mix(vec3(1.0), ColorA.rgb, ColorA.a);
	float up = rd.y;
	// Map the dome onto a molten "ceiling" that stretches towards the horizon.
	vec2 uv = rd.xz / (abs(up) + 0.30) * 0.85;
	uv += vec2(t * 0.010, -t * 0.006);

	// Slow convection: the whole field is advected by a warped flow.
	vec2 q = vec2(vFbm2(uv * 0.5 + vec2(0.0, t * 0.03), 4), vFbm2(uv * 0.5 + vec2(5.2, 1.3) - t * 0.025, 4));
	vec2 w = (q - 0.5) * 1.4;
	vec2 p = uv * 1.6 + w;
	// Detail fades out with distance (towards the horizon) instead of aliasing.
	float lod = clamp(length(fwidth(p)) * 7.0, 0.0, 1.0);
	float lodFine = clamp(length(fwidth(p)) * 22.0, 0.0, 1.0);

	// Open molten pools vs. floating crust.
	float m = vFbm2(uv * 0.55 + q * 0.9 + vec2(t * 0.01, 0.0), 5);
	float pool = smoothstep(0.60, 0.67, m);

	// Crust plates separated by glowing cracks of varying width.
	vec3 vor = vVoronoi(p * 1.6, t * 0.08);
	float plate = vHash12(vec2(vor.z * 113.0, 7.0));
	float border = vor.x + (vNoise2(p * 6.0) - 0.5) * 0.035;
	float width = 0.012 + 0.05 * vNoise2(p * 2.5 + 3.1) * vNoise2(p * 0.7 - t * 0.01);
	// Per-plate values only apply away from the crack, so both sides of a crack match.
	float side = smoothstep(0.0, 0.15, vor.x);
	float plateS = mix(0.5, plate, side);
	// Never thinner than ~1.5 px: thin cracks get wider and dimmer instead of breaking up and crawling.
	float bpx = max(fwidth(border), 1e-5);
	float wAA = max(width, 1.5 * bpx);
	float crack = (1.0 - smoothstep(0.0, wAA, max(border, 0.0))) * (width / wAA);
	float crackGlow = exp(-max(border, 0.0) * 10.0) * 0.32 + exp(-max(border, 0.0) * 3.5) * 0.07;
	float edgeHeat = exp(-max(border, 0.0) * 5.0) * (0.15 + 0.3 * plateS);
	float pores = smoothstep(0.70, 0.95, vFbm2(p * 7.0 + vor.z * 5.0, 3)) * 0.08 * plate * side;

	// Lava lake inside the pools: thin crust broken into small dark islands drifting on a
	// white-hot surface, separated by wide glowing gaps.
	vec2 fp = p * 2.2 + vec2(t * 0.05, -t * 0.035);
	float surf = vFbm2(fp + w * 2.0, 5);
	vec3 isl = vVoronoi(p * 4.4 + w * 0.6, t * 0.18);
	float gap = 0.05 + 0.17 * vNoise2(p * 1.4 + vec2(t * 0.02, 0.0)) + (1.0 - pool) * 0.02;
	float lakeGap = 1.0 - smoothstep(0.0, gap, isl.x);
	float islandRock = vFbm2(p * 18.0 + isl.z * 7.0, 3);
	float lava = lakeGap * (0.78 + 0.3 * surf) + exp(-isl.x * 9.0) * 0.22 + islandRock * 0.045;
	lava = mix(lava, 0.32 + 0.2 * surf, lodFine);

	// Rough basalt crust: high frequency rock texture, lit by the glow of nearby cracks.
	float rock = vFbm2(p * 14.0, 4);
	float rockLit = exp(-max(border, 0.0) * 6.0) * (0.25 + 0.75 * rock) * 0.22;

	float pulse = mix(0.85 + 0.15 * sin(t * 0.8 + vNoise2(p * 0.9 + 2.7) * 9.0), 0.85 + 0.15 * sin(t * (0.5 + plate) + vor.z * 30.0), side);
	// Some crack segments run hotter than others.
	float hot = 0.55 + 0.45 * vNoise2(p * 0.9 + vec2(t * 0.03, -t * 0.02));
	float crustHeat = crack * (0.75 + 0.35 * plateS) * pulse * hot + crackGlow * pulse * (0.6 + 0.4 * rock) + edgeHeat * 0.25 * rock + pores;
	float heat = mix(crustHeat, lava, pool);
	heat = mix(heat, 0.05 + pool * 0.3, lod * 0.9);

	// Incandescence curve: black crust -> grey -> blinding white core, plus a soft heat glow.
	vec3 col = tint * (pow(heat, 2.4) * 1.1 + pow(max(heat - 0.72, 0.0), 2.0) * 3.2);
	// Bloom around the molten network and the basalt it lights up.
	col += tint * (crackGlow * 0.22 * hot * (1.0 - pool) + rockLit * 0.5 * (1.0 - lod));
	col += tint * (smoothstep(0.5, 0.75, m) * 0.06 + 0.004);

	// Rising embers.
	// Two phase-shifted layers; each fades out before its drift wraps, so the offset stays bounded
	// (an unbounded offset squeezes the field into streaks and blobs after a few minutes).
	float emberPa = vPixelAngle(rd);
	vec3 embers = vec3(0.0);
	for (int k = 0; k < 2; k++) {
		float ph = fract(t / 120.0 + 0.5 * float(k));
		vec3 ed = normalize(rd + vec3(0.0, -ph * 0.18, 0.0));
		if (k == 1) ed = vec3(ed.z, ed.y, -ed.x);
		embers += vStarLayer(ed, 140.0, 0.03, t * 3.0, emberPa, 1.2) * (1.0 - abs(ph * 2.0 - 1.0));
	}
	col += embers * tint * 0.5 * smoothstep(-0.1, 0.3, up);

	// Fade into black at and below the horizon, with a faint heat haze.
	float horizon = smoothstep(-0.03, 0.2, up);
	col *= horizon;
	col += tint * 0.025 * exp(-abs(up) * 16.0) * (0.5 + 0.5 * m);
	col *= Settings.x;
	return col / (1.0 + col * 0.3);
}

float vAuroraField(vec2 p, float t) {
	float warp = vFbm2(p * 0.12 + vec2(t * 0.015, -t * 0.01), 4);
	float x = p.y * 0.32 + warp * 4.2 + sin(p.x * 0.09 + t * 0.12) * 0.9;
	float band = abs(fract(x * 0.22) - 0.5) * 2.0;
	float sheet = exp(-band * band * 26.0);
	float rays = 0.55 + 0.45 * vNoise2(vec2(p.x * 2.4 + t * 0.35, x * 1.3));
	float patches = smoothstep(0.30, 0.72, vFbm2(p * 0.05 - vec2(t * 0.012, 0.0), 3));
	return sheet * rays * patches;
}

vec3 vSkyAurora(vec3 rd, float t, int quality, vec2 fragCoord) {
	vec3 low = mix(vec3(0.15, 1.0, 0.55), ColorA.rgb, ColorA.a);
	vec3 high = mix(vec3(0.55, 0.22, 1.0), ColorB.rgb, ColorA.a);
	float up = max(rd.y, 0.0);
	vec3 col = mix(vec3(0.020, 0.040, 0.070), vec3(0.003, 0.006, 0.018), pow(up, 0.6));
	col += vec3(0.02, 0.05, 0.06) * exp(-abs(rd.y) * 9.0);
	col *= mix(1.0, 0.25, (1.0 - smoothstep(-0.3, 0.0, rd.y)));
	col += vStarsW(rd, t, Settings.z, smoothstep(-0.05, 0.2, rd.y) * Timing.w);
	col += vMilkyWay(rd, 0.6) * smoothstep(0.0, 0.3, rd.y);

	if (rd.y > 0.0) {
		int steps = quality == 0 ? 18 : (quality == 1 ? 28 : 42);
		// Jitter locked to the sky direction: locked to screen pixels it stays put while the
		// curtains slide past during a turn, so they boil.
		float dither = vHash13(floor(rd * 700.0) + 7.3);
		vec3 acc = vec3(0.0);
		for (int i = 0; i < 42; i++) {
			if (i >= steps) break;
			float fi = (float(i) + dither) / float(steps);
			float h = mix(1.0, 4.4, fi);
			vec2 p = rd.xz * (h / (rd.y + 0.05)) * 3.2 + vec2(0.0, 6.0);
			float a = vAuroraField(p, t);
			vec3 c = mix(low, high, smoothstep(0.15, 0.95, fi));
			c = mix(c, vec3(1.0, 0.25, 0.45), smoothstep(0.82, 1.0, fi) * 0.35);
			acc += c * a * exp(-fi * 2.2);
		}
		acc *= 5.0 / float(steps);
		acc *= smoothstep(0.0, 0.12, rd.y);
		col += acc;
		col += low * 0.04 * exp(-rd.y * 7.0) * smoothstep(0.0, 0.05, rd.y);
	}
	col *= Settings.x;
	return vToDisplay(vAces(col * 1.6));
}

vec3 vSkyNebula(vec3 rd, float t, int quality, vec2 fragCoord) {
	vec3 col = vec3(0.004, 0.003, 0.010);
	int steps = quality == 0 ? 10 : (quality == 1 ? 16 : 26);
	float dither = vHash13(floor(rd * 700.0) + 3.1);
	vec3 acc = vec3(0.0);
	float trans = 1.0;
	vec3 drift = vec3(t * 0.004, 0.0, t * 0.002);
	vec3 cA = mix(vec3(0.95, 0.25, 0.65), ColorA.rgb, ColorA.a);
	vec3 cB = mix(vec3(0.20, 0.55, 1.00), ColorB.rgb, ColorA.a);
	for (int i = 0; i < 26; i++) {
		if (i >= steps) break;
		float s = (float(i) + dither) / float(steps);
		vec3 p = rd * (1.2 + s * 2.4) + drift;
		vec3 w = vec3(vFbm3(p * 0.9, 3), vFbm3(p * 0.9 + 4.7, 3), vFbm3(p * 0.9 + 9.1, 3));
		float d = vFbm3(p * 1.5 + w * 1.6, 5);
		float mask = smoothstep(0.35, 0.65, vFbm3(p * 0.45 + 2.0, 3));
		d = smoothstep(0.5, 0.86, d) * mask;
		vec3 c = mix(cA, cB, smoothstep(0.3, 0.7, w.x));
		c = mix(c, vec3(1.0, 0.65, 0.35), smoothstep(0.62, 0.8, w.y) * 0.6);
		acc += trans * c * d * 0.15;
		trans *= exp(-d * 0.55);
	}
	col += acc;
	col += vStarsW(rd, t, Settings.z * 1.6, (0.4 + 0.6 * trans) * Timing.w);
	col += vMilkyWay(rd, 0.5);
	col *= Settings.x;
	return vToDisplay(vAces(col * 1.05));
}

vec3 vSkySunset(vec3 rd, float t, int quality) {
	// Locked golden hour: keep the real sun azimuth, clamp it just above the horizon.
	vec3 s = SunDir.xyz;
	vec2 az = normalize(s.xz + vec2(1e-3, 0.0));
	vec3 sunDir = normalize(vec3(az.x, 0.055, az.y));
	int viewSteps = quality == 0 ? 6 : (quality == 1 ? 8 : 12);
	vec3 sunColor;
	vec3 col = vAtmosphere(rd, sunDir, 26.0, viewSteps, quality == 2 ? 4 : 2, sunColor);
	col = vSaturate(col, 1.3);
	col += vec3(0.9, 0.3, 0.6) * 0.06 * exp(-abs(rd.y - 0.12) * 5.0);
	col *= mix(1.0, 0.35, (1.0 - smoothstep(-0.25, 0.0, rd.y)));
	if (Extra.z < 0.5) col += vSunDisk(rd, sunDir, sunColor, 2.6);
	col += vStarsW(rd, t, Settings.z * 0.5, smoothstep(0.35, 0.9, rd.y) * 0.25 * Timing.w);
	if (Extra.y > 0.5) {
		vec3 ambient = vec3(0.32, 0.22, 0.30);
		vec4 clouds = vClouds(rd, sunDir, sunColor * 0.12 * vec3(1.0, 0.75, 0.6), ambient * 0.35, Settings.y * 0.8 + 0.1, t, Camera.xyz);
		clouds.rgb = vSaturate(clouds.rgb, 1.35);
		col = vOver(col, clouds);
	}
	col *= Settings.x;
	return vToDisplay(vAces(col * 1.1));
}

vec3 vSkyInk(vec3 rd, float t) {
	vec3 tint = mix(vec3(1.0), ColorA.rgb, ColorA.a);
	float up = rd.y;
	vec2 uv = rd.xz / (abs(up) + 0.38);
	uv += vec2(t * 0.008, t * 0.004);
	vec2 q = vec2(vFbm2(uv * 0.8 + t * 0.02, 5), vFbm2(uv * 0.8 + vec2(3.3, 7.1) - t * 0.018, 5));
	vec2 r = vec2(vFbm2(uv * 1.4 + q * 3.0 + vec2(1.2, 4.4), 5), vFbm2(uv * 1.4 + q * 3.0 + vec2(6.1, 0.7), 5));
	float smoke = vFbm2(uv * 1.8 + r * 2.6, 6);
	float filaments = vRidged2(uv * 2.2 + r * 3.2 - t * 0.02, 6);
	float ink = smoothstep(0.46, 0.9, smoke) * 0.55 + pow(filaments, 3.5) * 1.35;
	ink *= smoothstep(0.25, 0.75, q.x + 0.2);
	vec3 col = tint * ink;
	col += vStarsW(rd, t, Settings.z, (1.0 - ink) * 0.8 * Timing.w);
	col *= smoothstep(-0.05, 0.25, up);
	col *= Settings.x;
	return col / (1.0 + col * 0.25);
}

vec3 vSkySynthwave(vec3 rd, float t) {
	vec3 sunDirRaw = SunDir.xyz;
	vec2 az = normalize(sunDirRaw.xz + vec2(1e-3, 0.0));
	vec3 sunDir = normalize(vec3(az.x, 0.10, az.y));
	float up = rd.y;
	vec3 top = mix(vec3(0.04, 0.01, 0.10), ColorA.rgb * 0.12, ColorA.a);
	vec3 mid = vec3(0.35, 0.05, 0.42);
	vec3 horizon = mix(vec3(1.0, 0.35, 0.45), ColorB.rgb, ColorA.a);
	vec3 col = mix(horizon, mid, smoothstep(0.0, 0.25, up));
	col = mix(col, top, smoothstep(0.2, 0.75, up));
	col += vStars(rd, t, Settings.z) * smoothstep(0.15, 0.5, up) * Timing.w;

	// Retro sun with horizontal cut lines.
	vec3 right = normalize(cross(vec3(0.0, 1.0, 0.0), sunDir));
	vec3 sup = cross(sunDir, right);
	vec2 suv = vec2(dot(rd, right), dot(rd, sup)) / 0.17;
	float sd = length(suv);
	if (dot(rd, sunDir) > 0.0) {
		float edge = vPixelAngle(rd) / 0.17 * 1.5;
		float disk = 1.0 - smoothstep(1.0 - edge, 1.0 + edge, sd);
		float stripes = 1.0;
		if (suv.y < 0.25) {
			float k = (0.25 - suv.y) * 7.0;
			float line = fract(suv.y * (6.0 + k) - t * 0.25);
			stripes = smoothstep(0.0, 0.08, line - k * 0.06);
		}
		vec3 sunCol = mix(vec3(1.0, 0.25, 0.55), vec3(1.0, 0.92, 0.35), smoothstep(-0.9, 0.8, suv.y));
		col = mix(col, sunCol * 1.4, disk * stripes);
		col += vec3(1.0, 0.3, 0.6) * exp(-max(sd - 1.0, 0.0) * 2.2) * 0.35;
	}

	// Below the horizon: neon grid (visible in the menu or from high up).
	if (up < 0.0) {
		vec2 g = rd.xz / max(-up, 0.02);
		g.y += t * 1.5;
		vec2 grid = abs(fract(g * 0.5) - 0.5) / max(fwidth(g * 0.5), vec2(1e-4));
		float line = 1.0 - min(min(grid.x, grid.y), 1.0);
		vec3 floorCol = vec3(0.03, 0.0, 0.06) + vec3(1.0, 0.2, 0.8) * line * exp(up * 3.0) * 0.9;
		col = mix(col, floorCol, (1.0 - smoothstep(-0.02, 0.0, up)));
	}
	col += horizon * 0.25 * exp(-abs(up) * 30.0);
	col *= Settings.x;
	return col / (1.0 + col * 0.2);
}

vec3 vSkyBloodMoon(vec3 rd, float t) {
	vec3 moonDir = normalize(MoonDir.xyz);
	if (moonDir.y < 0.25) moonDir = normalize(vec3(moonDir.x, 0.25, moonDir.z) + vec3(0.0, 0.0, 1e-3));
	float up = rd.y;
	vec3 col = mix(vec3(0.07, 0.006, 0.01), vec3(0.012, 0.0015, 0.004), smoothstep(-0.05, 0.6, up));
	col *= mix(1.0, 0.3, (1.0 - smoothstep(-0.3, 0.0, up)));
	col += vStarsW(rd, t, Settings.z, smoothstep(0.1, 0.6, up) * 0.6 * Timing.w) * vec3(1.0, 0.55, 0.5);
	float cover;
	vec3 moon = vMoon(rd, moonDir, 0.0, vec3(1.0, 0.28, 0.18), 4.2, cover);
	col = col * (1.0 - cover) + moon * 1.6;
	col += vec3(0.9, 0.12, 0.08) * exp(-acos(clamp(dot(rd, moonDir), -1.0, 1.0)) * 3.0) * 0.18;
	if (Extra.y > 0.5) {
		vec4 clouds = vClouds(rd, moonDir, vec3(0.2, 0.018, 0.012), vec3(0.012, 0.0015, 0.003), Settings.y * 0.8 + 0.15, t, Camera.xyz);
		col = vOver(col, vec4(clouds.rgb, clouds.a * 0.9));
	}
	col *= Settings.x;
	return vToDisplay(vAces(col * 1.3));
}

vec3 vSkyCustom(vec3 rd, float t) {
	float up = rd.y;
	vec3 zenith = ColorA.rgb;
	vec3 horizon = ColorB.rgb;
	vec3 col = mix(horizon, zenith, smoothstep(-0.05, 0.6, up));
	vec3 lin = col;
	lin += vStarsW(rd, t, Settings.z, smoothstep(0.0, 0.3, up) * Timing.w * (1.0 - dot(lin, vec3(0.33))));
	if (Extra.y > 0.5) {
		vec3 sunDir = normalize(SunDir.xyz);
		vec4 clouds = vClouds(rd, sunDir, vec3(0.9), lin * 0.7 + 0.05, Settings.y, t, Camera.xyz);
		lin = vOver(lin, clouds);
	}
	lin *= Settings.x;
	return vToDisplay(lin / (1.0 + lin * 0.15));
}

// ---------------------------------------------------------------------------
//  Entry point
// ---------------------------------------------------------------------------

vec3 v1zirSky(vec3 rd, vec2 fragCoord) {
	rd = normalize(rd);
	gStarWeight = 0.0;
	float t = Timing.x;
	int preset = int(Camera.w + 0.5);
	int quality = int(Settings.w + 0.5);
	vec3 col;
	if (preset == PRESET_MAGMA) col = vSkyMagma(rd, t);
	else if (preset == PRESET_AURORA) col = vSkyAurora(rd, t, quality, fragCoord);
	else if (preset == PRESET_NEBULA) col = vSkyNebula(rd, t, quality, fragCoord);
	else if (preset == PRESET_SUNSET) col = vSkySunset(rd, t, quality);
	else if (preset == PRESET_INK) col = vSkyInk(rd, t);
	else if (preset == PRESET_SYNTHWAVE) col = vSkySynthwave(rd, t);
	else if (preset == PRESET_BLOOD_MOON) col = vSkyBloodMoon(rd, t);
	else if (preset == PRESET_CUSTOM) col = vSkyCustom(rd, t);
	else col = vSkyRealistic(rd, t, quality);

	// Triangular dither removes banding in the dark gradients.
	float n = vHash12(fragCoord + Extra.w) + vHash12(fragCoord.yx + 13.7 + Extra.w) - 1.0;
	col += n / 255.0;
	return clamp(col, 0.0, 1.0);
}
