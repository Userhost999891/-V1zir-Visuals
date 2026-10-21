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
	vec3 warm = vec3(1.00, 0.74, 0.50);
	vec3 white = vec3(1.00, 0.97, 0.93);
	vec3 cold = vec3(0.70, 0.81, 1.00);
	return k < 0.5 ? mix(warm, white, k * 2.0) : mix(white, cold, (k - 0.5) * 2.0);
}

// ---------------------------------------------------------------------------
//  Shared sky elements
// ---------------------------------------------------------------------------

// Size of one screen pixel in radians (keeps stars 1-2 px wide at every resolution).
float vPixelAngle(vec3 rd) {
	return max(length(fwidth(rd)), 1e-5);
}

// Glowing points on a jittered grid, sized in screen pixels: the Magma preset's embers.
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
		// Keep every point well inside its cell so the glow never gets clipped by the 2x2x2 search.
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

// ---------------------------------------------------------------------------
//  Stars: one fixed catalogue over the whole sky, about 1600 stars at density 1, so a 70 degree
//  view of a clear night shows some 150 to 250 of them. Their brightness follows a power law like
//  the real sky: most are faint pin-points, a handful are bright. Every star is a point source
//  drawn in screen pixels (a fixed 0.6 px Gaussian), so it stays a crisp 1-2 px point at any
//  resolution and field of view, and whether a star exists depends on the star alone, so the
//  count is the same at any resolution. Only the brightest get a small, tight glow.
// ---------------------------------------------------------------------------

// The Milky Way's pole: its band is the great circle at right angles to it.
const vec3 V_GALAXY_POLE = vec3(0.2757, 0.6105, -0.7188);
// Cells per radian of the star grid, and the chance that a cell holds a star at density 1.
const float V_STAR_SCALE = 40.0;
const float V_STAR_CHANCE = 0.27;

// Screen derivatives of the view direction, taken once in uniform control flow (v1zirSky), for
// presets that draw stars directly instead of through the sky cache.
vec3 gRdDx = vec3(0.0);
vec3 gRdDy = vec3(0.0);

// Linear star light in direction rd; dx, dy = the change of the view direction over one pixel.
vec3 vCrispStars(vec3 rd, vec3 dx, vec3 dy, float t, float density) {
	// Offsets on the sky map to pixels through the pseudo inverse of (dx, dy): stars stay round
	// even at the edges of a wide field of view.
	float a = dot(dx, dx);
	float b = dot(dx, dy);
	float c = dot(dy, dy);
	float det = max(a * c - b * b, 1e-24);
	float pixelAngle = sqrt(sqrt(det));
	// A star is only found from the 2x2x2 cells around rd: its light has to stay within a third
	// of a cell of its centre, so the glow is cut down where pixels are huge (low res, wide FOV).
	float reach = 0.33 / (V_STAR_SCALE * pixelAngle);
	float reach2 = reach * reach;
	vec3 base = floor(rd * V_STAR_SCALE - 0.5);
	vec3 col = vec3(0.0);
	for (int i = 0; i < 8; i++) {
		vec3 cell = base + vec3(float(i & 1), float((i >> 1) & 1), float((i >> 2) & 1));
		vec3 j = vHash33(cell);
		vec3 P = cell + j;
		float len = length(P);
		// Only cells right on the sphere hold stars, so every star is well inside the search.
		if (abs(len - V_STAR_SCALE) > 0.15) continue;
		vec3 sp = P / len;
		// A few more faint stars along the Milky Way.
		float lat = dot(sp, V_GALAXY_POLE);
		float chance = V_STAR_CHANCE * density * (0.75 + 0.9 * exp(-lat * lat * 22.0));
		if (vHash13(cell + 17.0) > chance) continue;
		vec3 o = rd - sp;
		float ox = dot(o, dx);
		float oy = dot(o, dy);
		vec2 px = vec2(c * ox - b * oy, a * oy - b * ox) / det;
		float r2 = dot(px, px);
		if (r2 > reach2) continue;
		// Brightness: a power law, about 7 % of the stars ten times brighter than the faintest.
		float flux = min(pow(max(vHash13(cell + 3.7), 0.004), -0.85), 60.0);
		// Gentle twinkle, stronger low in the sky where the light crosses more air.
		float tw = 0.10 + 0.22 * (1.0 - smoothstep(0.0, 0.45, sp.y));
		float phase = vHash13(cell + 9.1) * V_TAU;
		flux *= 1.0 + tw * (0.6 * sin(t * (2.1 + 2.6 * j.x) + phase) + 0.4 * sin(t * (3.7 + 3.1 * j.y) + phase * 2.3));
		// Faint stars look white (the eye sees no colour in them), bright ones show their tint.
		vec3 tint = mix(vec3(1.0), vStarTint(j.z), 0.35 + 0.65 * smoothstep(2.0, 14.0, flux));
		float core = exp(-r2 * 1.39) * 0.44;
		float glow = exp(-r2 * 0.22) * 0.0045 * max(flux - 4.0, 0.0);
		// Fade out at the reach instead of clipping.
		float fade = 1.0 - smoothstep(0.6 * reach2, reach2, r2);
		col += tint * (core * flux * 0.09 + glow) * fade;
	}
	return col;
}

// The same stars for a preset drawn directly (no sky cache): needs gRdDx / gRdDy.
vec3 vStars(vec3 rd, float t, float density) {
	return vCrispStars(rd, gRdDx, gRdDy, t, density);
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
	// Even thin cloud hides starlight: any visible cloud covers the stars behind it.
	gStarWeight *= 1.0 - smoothstep(0.0, 0.25, over.a);
	return mix(under, over.rgb, over.a);
}

// Milky Way: a band along the great circle around V_GALAXY_POLE, wider and warmer towards the
// galactic centre, made of mottled star clouds and split by dark dust lanes. The noise is
// sampled in galactic coordinates stretched along the band, so clouds and lanes run with it.
vec3 vMilkyWay(vec3 rd, float strength) {
	float lat = dot(rd, V_GALAXY_POLE);
	if (abs(lat) > 0.6) return vec3(0.0);
	vec3 u = normalize(cross(V_GALAXY_POLE, vec3(0.0, 1.0, 0.0)));
	vec3 v = cross(V_GALAXY_POLE, u);
	vec2 g = vec2(dot(rd, u), dot(rd, v));
	// Closeness to the galactic centre (at longitude 0.9 rad).
	float centre = 0.5 + 0.5 * dot(normalize(g + 1e-5), vec2(cos(0.9), sin(0.9)));
	centre = centre * centre * centre;
	float width = mix(0.085, 0.16, centre);
	float x = lat / width;
	float band = exp(-x * x);
	float halo = exp(-lat * lat * 9.0);
	vec3 q = vec3(g * 2.6, lat * 6.0);
	// Star clouds and their fine grain.
	float clouds = vFbm3(q * 1.7 + 1.3, 5);
	float grain = vFbm3(q * 8.0 + 7.1, 3);
	float lum = band * (0.2 + 1.3 * smoothstep(0.35, 0.8, clouds)) * (0.55 + 0.9 * grain * grain);
	lum = lum * (0.45 + 0.8 * centre) + halo * 0.12;
	// Dust: lanes along the middle of the band (the Great Rift towards the centre) and patches.
	float lane = exp(-(lat + 0.012) * (lat + 0.012) / (width * width * 0.18));
	float dust = smoothstep(0.42, 0.66, vFbm3(q * 3.2 + 3.1, 5));
	lum *= 1.0 - 0.8 * dust * mix(0.45, 1.0, lane * centre);
	vec3 col = mix(vec3(0.62, 0.70, 0.95), vec3(1.0, 0.86, 0.68), smoothstep(0.2, 0.9, centre * band));
	return col * lum * 0.13 * strength;
}

// ---------------------------------------------------------------------------
//  Physically based atmosphere: single scattering by air (Rayleigh) and haze (Mie) with ozone
//  absorption, along the whole view ray up to the top of the atmosphere, plus a cheap estimate of
//  multiple scattering. Distances in km. The optical depth towards the sun or moon comes from an
//  analytic Chapman function instead of a second march, so every sample is cheap and accurate down
//  to grazing angles; past the horizon it runs on through the tangent point, which gives the
//  reddened twilight glow and the Earth's shadow at dusk on its own.
// ---------------------------------------------------------------------------

const float V_RG = 6360.0;
const float V_RT = 6460.0;
const vec3 V_BETA_R = vec3(5.802e-3, 13.558e-3, 33.1e-3);
const float V_BETA_M = 8.0e-3;
// Haze also absorbs a little: extinction over scattering.
const float V_MIE_EXT = 1.11;
const vec3 V_BETA_O = vec3(0.650e-3, 1.881e-3, 0.085e-3);
const float V_HR = 8.0;
const float V_HM = 1.4;
const float V_OBSERVER = 0.3;

vec2 vRaySphere(vec3 ro, vec3 rd, float radius) {
	float b = dot(ro, rd);
	float c = dot(ro, ro) - radius * radius;
	float h = b * b - c;
	if (h < 0.0) return vec2(-1.0);
	h = sqrt(h);
	return vec2(-b - h, -b + h);
}

// Column of an exponential layer (scale height H, density 1 at the ground) from altitude h along
// a ray with zenith cosine mu, in km of ground-level air: Schueler's Chapman approximation.
float vChapman(float h, float mu, float H) {
	float r = V_RG + h;
	float c = sqrt(1.5708 * r / H);
	float up = c / ((c - 1.0) * abs(mu) + 1.0) * exp(-h / H);
	if (mu >= 0.0) return up * H;
	// Downwards: twice the horizontal column at the tangent point minus the upward one. A ray
	// that dives into the ground gets an enormous column, i.e. the planet's shadow.
	float h0 = r * sqrt(max(1.0 - mu * mu, 0.0)) - V_RG;
	float c0 = sqrt(1.5708 * (V_RG + max(h0, -V_RG * 0.5)) / H);
	return max(2.0 * c0 * exp(min(-h0 / H, 30.0)) - up, up) * H;
}

// Ozone (a layer 15 km either side of 25 km) above altitude h along mu, stretched by the slant
// of the ray through the layer.
float vOzoneColumn(float h, float mu) {
	float lo = max(h - 10.0, 0.0);
	float hi = max(40.0 - h, 0.0);
	float above = h < 25.0 ? 15.0 - 0.5 * lo * lo / 15.0 : 0.5 * hi * hi / 15.0;
	float s = (V_RG + h) * sqrt(max(1.0 - mu * mu, 0.0)) / (V_RG + 25.0);
	return above * inversesqrt(max(1.0 - s * s, 0.0025));
}

float vOzone(float h) {
	return max(0.0, 1.0 - abs(h - 25.0) / 15.0);
}

// Optical depth (rgb) from altitude h towards a light at zenith cosine mu.
vec3 vLightDepth(float h, float mu) {
	return V_BETA_R * vChapman(h, mu, V_HR) + V_BETA_M * V_MIE_EXT * vChapman(h, mu, V_HM) + V_BETA_O * vOzoneColumn(h, mu);
}

// Fraction of the light of a source at zenith cosine mu that reaches altitude h.
vec3 vLightTransmittance(float h, float mu) {
	return exp(-vLightDepth(h, mu));
}

float vPhaseRayleigh(float mu) {
	return 3.0 / (16.0 * V_PI) * (1.0 + mu * mu);
}

// Cornette-Shanks: Henyey-Greenstein with the Rayleigh-like (1 + mu^2) term, normalised.
float vPhaseMie(float mu, float g) {
	float g2 = g * g;
	return 3.0 / (8.0 * V_PI) * ((1.0 - g2) * (1.0 + mu * mu)) / ((2.0 + g2) * pow(max(1.0 + g2 - 2.0 * g * mu, 1e-4), 1.5));
}

// Light of a source at zenith cosine mu that reaches altitude h after scattering more than once,
// per unit of scattering coefficient. It is mostly skylight, so it stays blue when the direct light
// turns orange (a third keeps the colour of the direct light, which warms the twilight horizon), and
// it follows the direct light into the Earth's shadow with a soft edge because the lit sky above
// still shines in for a while: the twilight sky stays blue instead of turning grey or black.
vec3 vMultiScatter(float h, float mu) {
	vec3 t = exp(-vLightDepth(h + 3.0, mu + 0.06));
	vec3 sky = dot(t, vec3(0.2, 0.7, 0.1)) * vec3(0.3, 0.52, 1.0);
	return mix(sky, t * 0.85, 0.35) * 0.05;
}

// Linear radiance of the sky in direction rd, lit by the sun (sunI) and the moon (moonI). Rays
// below the horizon see the haze over the ground. mieG sets how tight the glow around the light is.
vec3 vAtmosphere(vec3 rd, vec3 sunDir, float sunI, vec3 moonDir, float moonI, int steps, float mieG) {
	vec3 ro = vec3(0.0, V_RG + V_OBSERVER, 0.0);
	// Below the horizon the sky is mirrored (the presets darken it towards the ground): terrain hides
	// it in game, and where it shows, at the edge of the world or from high up, a hazy reflection
	// that is brightest at the horizon looks better than the line where a real ground would start.
	vec3 dir = normalize(vec3(rd.x, abs(rd.y), rd.z));
	float tMax = vRaySphere(ro, dir, V_RT).y;
	bool sunOn = sunI > 0.0 && sunDir.y > -0.35;
	bool moonOn = moonI > 0.0 && moonDir.y > -0.35;

	vec3 odView = vec3(0.0);
	vec3 sunR = vec3(0.0);
	vec3 sunM = vec3(0.0);
	vec3 moonR = vec3(0.0);
	vec3 moonM = vec3(0.0);
	vec3 ms = vec3(0.0);
	float prev = 0.0;
	float n = float(steps);
	for (int i = 0; i < 32; i++) {
		if (i >= steps) break;
		// Quadratic spacing: dense near the observer where the air is.
		float f = (float(i) + 1.0) / n;
		float t1 = tMax * f * f;
		float ds = t1 - prev;
		vec3 p = ro + dir * (prev + 0.5 * ds);
		prev = t1;
		float r = length(p);
		float h = max(r - V_RG, 0.0);
		vec3 up = p / r;
		vec3 dens = vec3(exp(-h / V_HR), exp(-h / V_HM), vOzone(h)) * ds;
		vec3 tauView = V_BETA_R * (odView.x + 0.5 * dens.x) + V_BETA_M * V_MIE_EXT * (odView.y + 0.5 * dens.y) + V_BETA_O * (odView.z + 0.5 * dens.z);
		odView += dens;
		vec3 tV = exp(-tauView);
		vec3 scatter = V_BETA_R * dens.x + V_BETA_M * dens.y;
		if (sunOn) {
			float muS = dot(up, sunDir);
			vec3 tS = tV * exp(-vLightDepth(h, muS));
			sunR += dens.x * tS;
			sunM += dens.y * tS;
			ms += scatter * tV * vMultiScatter(h, muS) * sunI;
		}
		if (moonOn) {
			float muM = dot(up, moonDir);
			vec3 tM = tV * exp(-vLightDepth(h, muM));
			moonR += dens.x * tM;
			moonM += dens.y * tM;
			ms += scatter * tV * vMultiScatter(h, muM) * moonI;
		}
	}
	vec3 col = ms;
	if (sunOn) {
		float mu = dot(dir, sunDir);
		col += (sunR * V_BETA_R * vPhaseRayleigh(mu) + sunM * V_BETA_M * vPhaseMie(mu, mieG)) * sunI;
	}
	if (moonOn) {
		float mu = dot(dir, moonDir);
		col += (moonR * V_BETA_R * vPhaseRayleigh(mu) + moonM * V_BETA_M * vPhaseMie(mu, mieG)) * moonI;
	}
	return col;
}

// Filmic tone curve on the brightest channel: the hue of saturated light survives (a curve per
// channel turns a deep orange sunset yellow), and only light far above white bleaches towards it.
vec3 vTonemap(vec3 c) {
	float m = max(max(c.r, c.g), c.b);
	vec3 hue = c * (vAces(vec3(m)).r / max(m, 1e-6));
	return mix(hue, vAces(c), smoothstep(1.0, 10.0, m) * 0.85);
}

// ---------------------------------------------------------------------------
//  Sun, moon & clouds
// ---------------------------------------------------------------------------

// Angle between two unit vectors, precise for small angles too (acos of a dot product is not).
float vAngle(vec3 a, vec3 b) {
	float s = length(cross(a, b));
	return dot(a, b) >= 0.0 ? asin(min(s, 1.0)) : V_PI - asin(min(s, 1.0));
}

// Sun disk with limb darkening (darker and redder towards the edge) and a soft bloom. sunColor is
// the sunlight reaching the observer: the disk takes its hue at a fixed brightness, so it reads as
// a crisp white-yellow disk at noon and a deep orange one at sunset, never a blown-out blob.
vec3 vSunDisk(vec3 rd, vec3 sunDir, vec3 sunColor, float size) {
	if (dot(rd, sunDir) < 0.8) return vec3(0.0);
	float angle = vAngle(rd, sunDir);
	float radius = 0.0095 * size;
	float edge = max(vPixelAngle(rd), radius * 0.03);
	float disk = 1.0 - smoothstep(radius - edge, radius + edge, angle);
	float r = clamp(angle / radius, 0.0, 1.0);
	float mu = sqrt(max(1.0 - r * r, 0.0));
	vec3 limb = pow(vec3(max(mu, 0.02)), vec3(0.32, 0.45, 0.65));
	float peak = max(max(sunColor.r, sunColor.g), max(sunColor.b, 1e-6));
	vec3 hue = sunColor / peak;
	vec3 col = hue * limb * disk * 7.0;
	col += sunColor * (exp(-angle * 75.0 / size) * 0.35 + exp(-angle * 14.0) * 0.04);
	return col;
}

// A dark mare: an ellipse (centre c, radii r) with a soft shore.
float vMare(vec2 p, vec2 c, vec2 r) {
	return 1.0 - smoothstep(0.7, 1.05, length((p - c) / r));
}

// The maria of the near side, so the moon shows its familiar face (uv: disk coordinates, y up).
float vMoonMaria(vec2 uv) {
	vec2 w = uv + (vec2(vFbm2(uv * 3.1 + 1.7, 3), vFbm2(uv * 3.1 + 8.3, 3)) - 0.5) * 0.2;
	float m = vMare(w, vec2(-0.28, 0.45), vec2(0.27, 0.22));  // Imbrium
	m = max(m, vMare(w, vec2(-0.62, 0.08), vec2(0.25, 0.44)));  // Oceanus Procellarum
	m = max(m, vMare(w, vec2(0.20, 0.42), vec2(0.16, 0.15)));   // Serenitatis
	m = max(m, vMare(w, vec2(0.38, 0.12), vec2(0.19, 0.16)));   // Tranquillitatis
	m = max(m, vMare(w, vec2(0.74, 0.30), vec2(0.09, 0.11)));   // Crisium
	m = max(m, vMare(w, vec2(0.62, -0.10), vec2(0.12, 0.17)));  // Fecunditatis
	m = max(m, vMare(w, vec2(0.42, -0.28), vec2(0.08, 0.09)));  // Nectaris
	m = max(m, vMare(w, vec2(-0.18, -0.36), vec2(0.18, 0.14))); // Nubium
	m = max(m, vMare(w, vec2(-0.52, -0.40), vec2(0.09, 0.09))); // Humorum
	m = max(m, vMare(w, vec2(0.0, 0.74), vec2(0.44, 0.06)));    // Frigoris
	m = max(m, vMare(w, vec2(0.05, 0.18), vec2(0.10, 0.07)));   // Vaporum
	m = max(m, vMare(w, vec2(-0.30, 0.02), vec2(0.15, 0.12)));  // Insularum
	return m;
}

// One layer of craters on a unit grid: x = albedo change (bright rims and ejecta, darker floors),
// yz = slope of the relief, which shades the craters near the terminator.
vec3 vCraterLayer(vec2 p, float density, float seed) {
	vec2 n = floor(p);
	vec2 f = fract(p);
	vec3 res = vec3(0.0);
	for (int j = -1; j <= 1; j++) {
		for (int i = -1; i <= 1; i++) {
			vec2 g = vec2(float(i), float(j));
			vec2 id = n + g + seed;
			if (vHash12(id + 31.7) > density) continue;
			vec2 o = g + 0.25 + 0.5 * vHash22(id) - f;
			float rad = 0.16 + 0.26 * vHash12(id + 7.1);
			float len = length(o);
			float d = len / rad;
			if (d > 1.7) continue;
			// Height: a bowl, a raised rim at d = 1 and ejecta outside; its slope along d.
			float rim = exp(-(d - 1.0) * (d - 1.0) * 14.0);
			float dh = (d < 1.0 ? 1.0 * d : 0.0) - rim * (d - 1.0) * 7.0;
			res.yz -= o / max(len, 1e-4) * dh / rad;
			res.x += rim * 0.10 - (d < 1.0 ? 0.05 * (1.0 - d * d) : 0.0) + exp(-d * 1.4) * 0.05 * vHash12(id + 2.9);
		}
	}
	return res;
}

// Moon disk with a real face: maria, craters with relief, Tycho's rays, a soft terminator for the
// phases and faint earthshine on the dark side. phase: 0 = full .. 0.5 = new (waning first, like
// the game). Returns the disk (with a little glow around it); coverage = how much of the sky
// behind it hides.
vec3 vMoon(vec3 rd, vec3 moonDir, float phase, vec3 tint, float size, out float coverage) {
	coverage = 0.0;
	if (dot(rd, moonDir) < 0.7) return vec3(0.0);
	float radius = 0.024 * size;
	float angle = vAngle(rd, moonDir);
	float lit = 0.5 + 0.5 * cos(phase * V_TAU);
	vec3 glow = tint * (exp(-angle * 22.0 / size) * 0.10 + exp(-angle * 5.0) * 0.02) * (0.2 + 0.8 * lit);
	if (angle > radius * 1.2) return glow;

	vec3 up = abs(moonDir.y) > 0.99 ? vec3(1.0, 0.0, 0.0) : vec3(0.0, 1.0, 0.0);
	vec3 right = normalize(cross(up, moonDir));
	vec3 top = cross(moonDir, right);
	vec2 uv = vec2(dot(rd, right), dot(rd, top)) / sin(radius);
	float r = length(uv);
	float edge = max(vPixelAngle(rd), radius * 0.02) / sin(radius);
	float mask = 1.0 - smoothstep(1.0 - edge, 1.0 + edge, r);
	coverage = mask;
	vec2 suv = uv / max(r, 1.0);
	vec3 sphere = vec3(suv, sqrt(max(1.0 - dot(suv, suv), 0.0)));

	// Albedo: bright highlands, dark maria, mottling, craters and Tycho's bright ray system.
	float maria = vMoonMaria(suv);
	float mottle = vFbm2(suv * 7.0 + 2.3, 4);
	vec3 cr = vCraterLayer(suv * 4.5, 0.55, 0.0) + vCraterLayer(suv * 9.0, 0.5, 13.0) * 0.6 + vCraterLayer(suv * 17.0, 0.45, 27.0) * 0.35;
	vec2 tycho = suv - vec2(-0.13, -0.70);
	float tl = length(tycho);
	float rays = pow(vNoise2(vec2(atan(tycho.y, tycho.x) * 7.0, 3.0)), 4.0) * exp(-tl * 2.2) * smoothstep(0.03, 0.08, tl);
	float albedo = mix(0.80, 0.42, maria) * (0.86 + 0.28 * mottle) + cr.x * (1.0 - maria * 0.5) + rays * 0.35;
	albedo += exp(-tl * tl * 900.0) * 0.4;
	vec2 cop = suv - vec2(-0.30, 0.15);
	albedo += exp(-dot(cop, cop) * 700.0) * 0.25 + pow(vNoise2(vec2(atan(cop.y, cop.x) * 5.0, 9.0)), 4.0) * exp(-length(cop) * 9.0) * 0.12;

	// Relief: the crater slopes tilt the normal, strongest in the maria-free highlands.
	vec3 n = normalize(sphere - vec3(cr.yz, 0.0) * 0.035 * sphere.z);
	float a = phase * V_TAU;
	vec3 sunL = normalize(vec3(-sin(a), 0.0, cos(a)));
	// A rough surface scatters light back to the sun (the full moon looks flat, not like a ball).
	float ndl = dot(n, sunL);
	float lambert = smoothstep(-0.04, 0.12, ndl) * mix(max(ndl, 0.0), 1.0, 0.55);
	float earthshine = 0.03 * (1.0 - lit * 0.7);
	vec3 surface = tint * albedo * (lambert * 1.4 + earthshine * vec3(0.8, 0.9, 1.1));
	return glow * (1.0 - mask) + surface * mask;
}

// ---------------------------------------------------------------------------
//  Volumetric clouds: fair-weather cumulus in a layer between V_CLOUD_BASE and V_CLOUD_TOP km
//  above the observer, on a curved Earth, so towards the horizon the clouds get smaller, flatter
//  and denser and fade into the haze. A short march through the layer integrates density and
//  light; a few samples towards the light give the self shadowing: tops facing the light are
//  bright, bases dark, thin edges let the light through and glow when it is behind them.
//
//  Shape: a domain-warped fbm decides where the clouds stand; the higher a point sits in the
//  layer, the stronger the fbm has to be, so every cloud has a flat base and a rounded dome
//  that rises with its strength. A billowy 3D noise then carves the outline into round puffs
//  (cauliflower tops) and soft wisps (bases).
// ---------------------------------------------------------------------------

const float V_CLOUD_BASE = 1.5;
const float V_CLOUD_TOP = 3.3;

// Lighting of the cloud layer.
struct VCloudLight {
	vec3 dir;    // towards the main light (sun or moon)
	vec3 color;  // its light where the clouds are
	vec3 top;    // skylight from above
	vec3 bottom; // light from below (ground bounce)
};

// Distance along a ray (zenith cosine mu) from the observer to the altitude h on a curved Earth.
float vShellDistance(float mu, float h) {
	float r0 = V_RG + V_OBSERVER;
	float r = V_RG + V_OBSERVER + h;
	return -r0 * mu + sqrt(max(r0 * r0 * (mu * mu - 1.0) + r * r, 0.0));
}

// Local cloud cover (0..1): the coverage setting, varied by slow weather patches so some parts
// of the sky are clearer than others.
float vCloudWeather(vec2 xz, float coverage) {
	float w = vNoise2(xz * 0.032 + 3.7) * 0.7 + vNoise2(xz * 0.085 + 1.1) * 0.3;
	return clamp(coverage + (w - 0.5) * 0.5, 0.0, 1.0);
}

// Round cloud cells: every cell of a jittered grid holds one cumulus, a paraboloid of random
// size and strength around its point; the strongest one at p wins (so neighbours stay separate).
// Values reach 1 at the centre of the strongest cells and turn negative outside the clouds.
float vCloudCells(vec2 p) {
	vec2 n = floor(p);
	vec2 f = fract(p);
	float best = -4.0;
	for (int j = -1; j <= 1; j++) {
		for (int i = -1; i <= 1; i++) {
			vec2 g = vec2(float(i), float(j));
			vec2 id = n + g;
			vec2 r = g + 0.15 + 0.7 * vHash22(id) - f;
			float h = vHash12(id + 7.7);
			float radius = 0.42 + 0.3 * vHash12(id + 3.1);
			best = max(best, 0.3 + 0.7 * h - dot(r, r) / (radius * radius));
		}
	}
	return best;
}

// Strength of the cloud column over xz (km): 1 = the core of a big cloud, 0 = its edge, negative
// in clear sky. Round cells (one cumulus each) whose outlines a domain-warped fbm breaks up.
float vCloudColumn(vec2 xz, float cover, int octaves) {
	vec2 warp = vec2(vNoise2(xz * 0.21 + 7.3), vNoise2(xz * 0.21 + vec2(2.9, 11.1))) - 0.5;
	vec2 w = xz + warp * 1.1;
	float n = vFbm2(w * 0.9, octaves);
	float c = vCloudCells(w * 0.55) + (n - 0.5) * 0.9;
	// More cover lowers the level a column has to clear (below zero the cells spread and merge).
	float threshold = mix(0.85, -0.75, cover);
	return (c - threshold) / (1.0 - threshold);
}

// Shape of the cloud at height fraction hf (0 = cloud base, 1 = top of the layer) over a column
// of strength c: positive inside, negative outside, roughly in km. A flat base and a dome that
// rises with the strength of the column.
float vCloudShape(float c, float hf) {
	return min(c - hf * hf * 1.1, (hf - 0.02) * 3.0);
}

// Density (0..1) of a shape value: a defined but soft outline about 150 m deep.
float vCloudDensity(float shape) {
	return smoothstep(0.0, 0.16, shape);
}

// Billowy detail: a 3D noise pushes the surface in and out, rounded lobes between sharp creases
// (cauliflower puffs) on the sides and tops and softer, smoother wisps at the base. detail fades
// the smaller puffs out with distance instead of turning them into noise.
float vCloudDetail(vec3 p, float hf, float detail) {
	float n1 = vNoise3(p * 2.1);
	float n2 = vNoise3(p * 5.3 + 7.0);
	float billow = abs(n1 * 2.0 - 1.0) * 0.65 + abs(n2 * 2.0 - 1.0) * 0.35 * detail;
	float wisp = n1 * 0.65 + n2 * 0.35;
	float e = mix(wisp, billow, smoothstep(0.04, 0.3, hf));
	return (e - 0.45) * mix(0.3, 0.62, detail);
}

float vHG(float mu, float g) {
	float g2 = g * g;
	return (1.0 - g2) / (4.0 * V_PI * pow(max(1.0 + g2 - 2.0 * g * mu, 1e-4), 1.5));
}

// rgb = cloud colour, a = coverage of the sky behind (lay it over the sky with vOver).
vec4 vClouds(vec3 rd, VCloudLight light, float coverage, float t, vec3 camPos, int quality) {
	if (rd.y < -0.01 || coverage <= 0.0) return vec4(0.0);
	float t0 = vShellDistance(rd.y, V_CLOUD_BASE);
	float t1 = vShellDistance(rd.y, V_CLOUD_TOP);
	// Beyond ~120 km the layer is lost in the haze.
	if (t0 > 120.0) return vec4(0.0);
	// March at most 10 km through the layer: further on the clouds are hidden anyway.
	float len = min(t1 - t0, 10.0);
	int steps = quality == 0 ? 12 : (quality == 1 ? 18 : 26);
	float dt = len / float(steps);
	// Per-direction jitter of the start: the sky cache jitters the direction every build, so the
	// slicing averages out into smooth density over a few builds.
	float jitter = vHash13(rd * 4096.0);
	// Wind (km): a slow drift, and the camera moves the layer with real parallax (at half the
	// strength, which keeps fast flight from smearing it in the cache). The detail noise drifts
	// a little faster than the shapes, so the clouds slowly boil and change.
	vec2 wind = vec2(t * 0.0045, t * 0.0018) + camPos.xz * 0.0005;
	vec3 windDetail = vec3(t * 0.0025, -t * 0.0008, t * 0.0012);
	// Fine detail fades out with distance instead of aliasing into noise.
	float detail = 1.0 - smoothstep(18.0, 60.0, t0);
	int octaves = t0 < 25.0 ? 5 : (t0 < 60.0 ? 4 : 3);
	int lightSteps = quality == 0 ? 3 : 4;

	float mu = dot(rd, light.dir);
	// Phase: a strong forward lobe (silver lining) and a soft backward one; the lobes of the
	// multiple scattering get wider and wider (Wrenninge).
	float ph0 = mix(vHG(mu, 0.78), vHG(mu, -0.25), 0.25);
	float ph1 = mix(vHG(mu, 0.40), vHG(mu, -0.12), 0.25);
	float ph2 = 0.08;
	float thick = V_CLOUD_TOP - V_CLOUD_BASE;

	vec3 scattered = vec3(0.0);
	float trans = 1.0;
	float depthSum = 0.0;
	float weightSum = 0.0;
	for (int i = 0; i < 26; i++) {
		if (i >= steps || trans < 0.02) break;
		float s = t0 + (float(i) + jitter) * dt;
		vec3 p = rd * s;
		// Height above the observer's ground, with the Earth's curvature.
		float h = p.y + dot(p.xz, p.xz) / (2.0 * V_RG);
		float hf = (h - V_CLOUD_BASE) / thick;
		if (hf < 0.0 || hf > 1.0) continue;
		vec2 xz = p.xz + wind;
		float cover = vCloudWeather(xz, coverage);
		float c = vCloudColumn(xz, cover, octaves);
		float shape = vCloudShape(c, hf);
		// The detail moves the surface by at most ~0.35: skip what it cannot reach.
		if (shape < -0.36) continue;
		vec3 q = vec3(xz.x, h, xz.y) + windDetail;
		float d = vCloudDensity(shape + vCloudDetail(q, hf, detail));
		if (d <= 0.004) continue;

		float sigma = d * 24.0;
		// Optical depth towards the light: samples of the cloud shape at growing distances.
		float od = 0.0;
		float ls = 0.0;
		for (int k = 0; k < 4; k++) {
			if (k >= lightSteps) break;
			float lstep = 0.07 * exp2(float(k) * 1.15);
			ls += lstep;
			vec3 lp = p + light.dir * ls;
			float lh = lp.y + dot(lp.xz, lp.xz) / (2.0 * V_RG);
			float lhf = (lh - V_CLOUD_BASE) / thick;
			if (lhf > 1.0) break;
			vec2 lxz = lp.xz + wind;
			float lshape = vCloudShape(vCloudColumn(lxz, cover, 3), lhf);
			// The nearest sample sees the puffs, so each one is shaded on its own.
			if (k == 0) lshape += vCloudDetail(vec3(lxz.x, lh, lxz.y) + windDetail, lhf, detail);
			od += vCloudDensity(lshape) * lstep * 24.0;
		}
		od += sigma * 0.03;
		// Beer's law plus two wider, fainter multiple scattering lobes that reach deeper.
		vec3 sun = light.color * (exp(-od) * ph0 + exp(-od * 0.35) * 0.4 * ph1 + exp(-od * 0.1) * 0.2 * ph2) * 2.4;
		// Powder: light scattered towards the viewer has to be scattered in first, which thin
		// fringes and the creases between puffs do less, so they look darker than the cores; it
		// fades when looking into the light, where the fringes glow instead.
		float powder = 1.0 - exp(-sigma * 0.14);
		sun *= mix(powder, 1.0, 0.35 + 0.65 * smoothstep(0.2, 0.95, mu));
		// Skylight from above, shaded by the cloud above the point; ground light from below.
		float skyOpen = smoothstep(-0.1, 1.0, hf) * exp(-od * 0.08);
		vec3 amb = light.top * skyOpen + light.bottom * (1.0 - 0.5 * hf);
		float st = exp(-sigma * dt);
		float absorbed = trans * (1.0 - st);
		scattered += (sun + amb) * absorbed;
		depthSum += s * absorbed;
		weightSum += absorbed;
		trans *= st;
	}
	float alpha = 1.0 - trans;
	if (alpha < 0.002) return vec4(0.0);
	// Aerial perspective: distant clouds fade into the haze of the sky behind them.
	float dist = depthSum / max(weightSum, 1e-4);
	float haze = exp(-dist / 60.0);
	vec3 col = scattered / alpha;
	return vec4(col, alpha * haze * smoothstep(-0.01, 0.015, rd.y));
}

// ---------------------------------------------------------------------------
//  Presets
// ---------------------------------------------------------------------------

// Sunlight at the top of the atmosphere, in the units of vAtmosphere.
const float V_SUN_I = 14.0;

// Exposure of the daylight skies for a sun at height y (the sine of its elevation): the eye
// adapts, so the sky darkens through the twilight without going black at once.
float vDayExposure(float y) {
	return exp(0.13 - 0.3 * smoothstep(0.17, 0.9, y) + 0.5 * smoothstep(0.17, 0.0, y) + 2.2 * smoothstep(0.0, -0.14, y));
}

// Light for the clouds of a physically lit sky: sunlight (or moonlight at night) where the clouds
// are, skylight from above (the zenith radiance, which tracks the colour of the sky through the
// day) and light bounced off the ground below. ex is the exposure applied to the sky.
VCloudLight vSkyCloudLight(vec3 sunDir, vec3 moonDir, float moonI, float ex, vec3 nightAir) {
	VCloudLight L;
	vec3 zenith = vAtmosphere(vec3(0.0, 1.0, 0.0), sunDir, V_SUN_I, moonDir, moonI, 5, 0.8) * ex + nightAir;
	float h = 0.5 * (V_CLOUD_BASE + V_CLOUD_TOP);
	bool day = sunDir.y > -0.06;
	L.dir = day ? sunDir : moonDir;
	L.color = (day ? V_SUN_I : moonI) * vLightTransmittance(h, L.dir.y) * ex;
	vec3 groundLight = (vLightTransmittance(0.0, sunDir.y) * max(sunDir.y, 0.0) * V_SUN_I + vLightTransmittance(0.0, moonDir.y) * max(moonDir.y, 0.0) * moonI) * ex;
	L.top = zenith * 1.5;
	L.bottom = groundLight * vec3(0.030, 0.034, 0.028) + zenith * 0.3;
	return L;
}

vec3 vSkyRealistic(vec3 rd, float t, int quality) {
	vec3 sunDir = normalize(SunDir.xyz);
	vec3 moonDir = normalize(MoonDir.xyz);
	float rain = Timing.z;
	float storm = max(rain, Timing.y);
	float phase = MoonDir.w;
	float moonLit = 0.5 + 0.5 * cos(phase * V_TAU);
	int steps = quality == 0 ? 10 : (quality == 1 ? 14 : 18);
	float ex = vDayExposure(sunDir.y);
	// The moon lights the night sky a little: a deep blue around it, more at full moon.
	float moonI = 0.045 * (0.1 + 0.9 * moonLit);
	vec3 air = vAtmosphere(rd, sunDir, V_SUN_I, moonDir, moonI, steps, 0.8) * ex;
	// Night: airglow and starlight keep the darkest sky a deep blue, lighter at the horizon.
	float night = smoothstep(0.05, -0.2, sunDir.y);
	float e = abs(rd.y);
	vec3 nightAir = mix(vec3(0.011, 0.018, 0.036), vec3(0.0035, 0.0065, 0.017), smoothstep(0.0, 0.5, e)) * night;
	air += nightAir;

	// Space behind the air: stars (drawn crisp by the sky cache) and a faint Milky Way.
	vec3 space = vec3(0.0);
	float starVis = smoothstep(-0.03, -0.17, sunDir.y) * (1.0 - storm) * smoothstep(-0.02, 0.1, rd.y);
	if (starVis > 0.001) {
		space += vStarsW(rd, t, Settings.z, 0.9 * starVis * Timing.w);
		space += vMilkyWay(rd, 0.5) * starVis;
	}

	vec3 col = air + space;
	if (Extra.z < 0.5 && rd.y > -0.02) {
		float horizonCut = smoothstep(-0.004, 0.004, rd.y) * (1.0 - storm * 0.92);
		vec3 sunCol = V_SUN_I * vLightTransmittance(V_OBSERVER, sunDir.y) * ex;
		float cover;
		vec3 moonTint = vec3(1.0, 0.97, 0.92) * normalize(vLightTransmittance(V_OBSERVER, max(moonDir.y, 0.0)) + 0.05) * 1.7;
		vec3 moon = vMoon(rd, moonDir, phase, moonTint, 1.5, cover);
		float moonVis = smoothstep(-0.04, 0.02, moonDir.y) * horizonCut;
		// The moon hides the stars behind it; the air in front of it still glows over it.
		col = air + space * (1.0 - cover * moonVis) + moon * moonVis;
		gStarWeight *= 1.0 - cover * moonVis;
		col += vSunDisk(rd, sunDir, sunCol, 1.6) * horizonCut;
	}

	if (Extra.y > 0.5 && rd.y > -0.02) {
		VCloudLight L = vSkyCloudLight(sunDir, moonDir, moonI, ex, nightAir);
		// Rain greys the clouds out: less direct light, a uniform overcast.
		L.color *= 1.0 - storm * 0.85;
		float coverage = mix(Settings.y, 1.0, rain * 0.85);
		col = vOver(col, vClouds(rd, L, coverage, t, Camera.xyz, quality));
	}

	// Below the horizon: the mirrored haze darkens and greys towards the ground.
	float below = smoothstep(0.0, -0.15, rd.y);
	col = mix(col, vec3(dot(col, vec3(0.2126, 0.7152, 0.0722))), below * 0.5) * (1.0 - 0.65 * below);
	// Rain and thunder: a grey, darker sky.
	float grey = dot(col, vec3(0.2126, 0.7152, 0.0722));
	col = mix(col, vec3(grey) * 0.75, storm * 0.6);
	col *= 1.0 - Timing.y * 0.45;
	col *= Settings.x;
	return vToDisplay(vTonemap(col));
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
	vec3 s = SunDir.xyz;
	vec2 az = normalize(s.xz + vec2(1e-3, 0.0));
	vec3 sunDir = normalize(vec3(az.x, 0.055, az.y));
	vec3 col = vAtmosphere(rd, sunDir, 26.0, sunDir, 0.0, 12, 0.76);
	col *= Settings.x;
	return vToDisplay(vTonemap(col * 1.1));
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
		VCloudLight L;
		L.dir = moonDir;
		L.color = vec3(0.2, 0.018, 0.012);
		L.top = vec3(0.012, 0.0015, 0.003);
		L.bottom = L.top * 0.5;
		vec4 clouds = vClouds(rd, L, Settings.y * 0.8 + 0.15, t, Camera.xyz, 1);
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
		VCloudLight L;
		L.dir = sunDir;
		L.color = vec3(0.9);
		L.top = lin * 0.7 + 0.05;
		L.bottom = L.top * 0.6;
		vec4 clouds = vClouds(rd, L, Settings.y, t, Camera.xyz, 1);
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
	gRdDx = dFdx(rd);
	gRdDy = dFdy(rd);
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

#ifndef V1ZIR_SKY_CACHE
	// Triangular dither removes banding in the dark gradients (the cache's screen pass dithers itself).
	float n = vHash12(fragCoord + Extra.w) + vHash12(fragCoord.yx + 13.7 + Extra.w) - 1.0;
	col += n / 255.0;
#endif
	return clamp(col, 0.0, 1.0);
}
