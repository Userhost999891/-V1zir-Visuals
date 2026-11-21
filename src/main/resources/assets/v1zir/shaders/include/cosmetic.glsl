// Materials of the cosmetics (render/cosmetic/CosmeticMesh): tint.rgb is the base colour, uv the surface pattern
// coordinates in pixels, time in seconds (animated materials). Returns the colour and its opacity.

const int C_MATTE = 0;
const int C_SATIN = 1;
const int C_GOLD = 2;
const int C_GEM = 3;
const int C_GLOW = 4;
const int C_FEATHER = 5;
const int C_MEMBRANE = 6;
const int C_IRIDESCENT = 7;
const int C_ENERGY = 8;
const int C_GALAXY = 9;
const int C_SILK = 10;
const int C_KNIT = 11;
const int C_FUR = 12;
const int C_EYE = 13;
const int C_LEAF = 14;
const int C_BONE = 15;
const int C_SILVER = 16;
const int C_SWIRL = 17;

float cHash(vec2 p) {
	p = fract(p * vec2(123.34, 456.21));
	p += dot(p, p + 45.32);
	return fract(p.x * p.y);
}

float cNoise(vec2 p) {
	vec2 i = floor(p);
	vec2 f = fract(p);
	f = f * f * (3.0 - 2.0 * f);
	return mix(mix(cHash(i), cHash(i + vec2(1.0, 0.0)), f.x), mix(cHash(i + vec2(0.0, 1.0)), cHash(i + vec2(1.0, 1.0)), f.x), f.y);
}

float cFbm(vec2 p) {
	float s = 0.0;
	float a = 0.5;
	for (int i = 0; i < 4; i++) {
		s += a * cNoise(p);
		p = p * 2.03 + vec2(1.7, 9.2);
		a *= 0.5;
	}
	return s;
}

// Studio surroundings reflected by metals and gems: a bright soft sky, a horizon band, a dark floor.
vec3 cEnv(vec3 r) {
	vec3 sky = mix(vec3(0.55, 0.6, 0.7), vec3(0.95, 0.97, 1.0), smoothstep(0.0, 0.8, r.y));
	vec3 c = mix(vec3(0.1, 0.09, 0.085), sky, smoothstep(-0.25, 0.1, r.y));
	c += vec3(0.7) * smoothstep(0.05, 0.0, abs(r.y - 0.15));
	return c;
}

vec3 cHue(float h) {
	return clamp(abs(mod(h * 6.0 + vec3(0.0, 4.0, 2.0), 6.0) - 3.0) - 1.0, 0.0, 1.0);
}

// world: the light map colour at the wearer; shade: vanilla's directional term (0.4 to 1).
vec4 cosmeticShade(int material, vec2 uv, vec4 tint, vec3 viewPos, vec3 viewNormal, vec3 world, float shade, float time) {
	vec3 n = normalize(viewNormal);
	vec3 v = normalize(-viewPos);
	float facing = dot(n, v);
	if (facing < 0.0) n = -n;
	float nv = abs(facing);
	float fresnel = pow(1.0 - nv, 5.0);
	vec3 r = reflect(-v, n);
	vec3 key = normalize(vec3(-0.35, 0.8, 0.5));
	float spec = pow(max(dot(normalize(key + v), n), 0.0), 48.0);
	float diffuse = shade * 0.75 + max(dot(n, key), 0.0) * 0.3;
	vec3 light = world * mix(0.8, 1.08, (shade - 0.4) / 0.6);
	vec3 base = tint.rgb;

	if (material == C_GLOW) {
		// Self lit: its own colour, a little brighter where it faces the eye.
		return vec4(base * (1.15 + 0.35 * nv), tint.a * mix(0.55, 1.0, nv));
	}
	if (material == C_ENERGY) {
		// Bands of light running along the surface, brightest in thin crests.
		float flow = uv.y * 0.35 - time * 1.6 + cNoise(uv * 0.4 + time * 0.3) * 1.5;
		float crest = pow(0.5 + 0.5 * sin(flow * 3.0), 6.0);
		float body = 0.35 + 0.65 * cNoise(uv * 0.25 - vec2(0.0, time * 0.8));
		vec3 c = base * (0.7 + body * 0.8) + vec3(1.0) * crest * 0.55;
		return vec4(c, tint.a * clamp(0.35 + body * 0.45 + crest * 0.5 + (1.0 - nv) * 0.3, 0.0, 1.0));
	}
	if (material == C_SWIRL) {
		vec2 p = uv * 0.12;
		float a = cFbm(p + vec2(time * 0.08, -time * 0.05));
		float b = cFbm(p * 1.7 + vec2(a * 2.0, time * 0.1));
		float swirl = 0.5 + 0.5 * sin((a * 6.0 + b * 4.0) - time * 0.9);
		vec3 c = mix(base * 0.18, mix(base, vec3(1.0), 0.12), smoothstep(0.45, 0.98, swirl));
		c += mix(base, vec3(1.0), 0.5) * pow(swirl, 14.0) * 0.35;
		return vec4(c * mix(0.75, 1.0, diffuse) + spec * 0.15 * light, 1.0);
	}
	if (material == C_GALAXY) {
		// Deep space: a slow nebula in the tint, then two layers of stars drifting and twinkling.
		vec2 p = uv * 0.18;
		float neb = cFbm(p + vec2(time * 0.015, time * 0.01));
		float neb2 = cFbm(p * 2.3 - vec2(time * 0.02, 0.0));
		vec3 c = mix(vec3(0.02, 0.015, 0.05), base * 0.75, smoothstep(0.35, 0.85, neb));
		c += mix(base.bgr, vec3(0.9, 0.4, 0.8), 0.4) * smoothstep(0.55, 0.95, neb2) * 0.45;
		for (int layer = 0; layer < 2; layer++) {
			float scale = layer == 0 ? 0.9 : 1.7;
			vec2 q = uv * scale + vec2(time * (0.12 + float(layer) * 0.1), time * 0.05);
			vec2 cell = floor(q);
			vec2 f = fract(q) - 0.5;
			float h = cHash(cell + float(layer) * 17.0);
			vec2 off = vec2(cHash(cell + 3.1), cHash(cell + 7.7)) - 0.5;
			float star = smoothstep(0.09, 0.0, length(f - off * 0.6)) * step(0.78, h);
			float twinkle = 0.55 + 0.45 * sin(time * (2.0 + h * 5.0) + h * 40.0);
			c += vec3(1.0, 0.95, 0.9) * star * twinkle * 1.6;
		}
		c += spec * 0.12 * light;
		return vec4(c, 1.0);
	}
	if (material == C_GOLD || material == C_SILVER) {
		vec3 tone = material == C_GOLD ? vec3(1.0, 0.78, 0.36) : vec3(0.86, 0.88, 0.92);
		tone *= base;
		vec3 env = cEnv(r);
		vec3 c = env * mix(tone, vec3(1.0), fresnel * 0.6) * light;
		c += tone * spec * 1.6 * light;
		c += tone * 0.12 * diffuse * light;
		return vec4(c, 1.0);
	}
	if (material == C_GEM) {
		// Facets catch the light: the environment tinted deep, sharp highlights and a slow glint.
		vec3 env = cEnv(r + n * 0.4);
		float glint = pow(max(sin(time * 2.2 + uv.x * 3.0 + uv.y * 5.0), 0.0), 18.0);
		vec3 c = base * (0.25 + env * 0.55) * light + base * 0.3 + vec3(1.0) * (spec * 1.4 + glint * 0.8) * light;
		return vec4(c, 1.0);
	}
	if (material == C_EYE) {
		vec3 c = base * diffuse * light + vec3(1.0) * pow(max(dot(normalize(key + v), n), 0.0), 120.0) * 1.5 * light;
		return vec4(c + cEnv(r) * 0.08 * light, 1.0);
	}
	if (material == C_IRIDESCENT) {
		// Thin film colours that shift with the angle, over a pattern of veins and eye spots.
		float film = nv * 0.9 + uv.x * 0.02 + uv.y * 0.015;
		vec3 iri = cHue(film + time * 0.03);
		vec2 c2 = uv * 0.09;
		float veins = smoothstep(0.92, 1.0, abs(sin(atan(uv.y, uv.x) * 9.0))) * 0.6;
		float spot = smoothstep(0.32, 0.27, length(fract(c2 + 0.5) - 0.5));
		vec3 c = mix(base, iri, 0.55) * (0.55 + diffuse * 0.6) * light;
		c = mix(c, vec3(0.05), veins);
		c = mix(c, base.gbr * 1.2 * light, spot * 0.8);
		c += vec3(1.0) * spec * 0.6 * light + iri * fresnel * 0.5;
		return vec4(c, tint.a * mix(0.62, 0.95, fresnel + veins));
	}
	if (material == C_MEMBRANE) {
		// Leathery skin between the bones: veins, wrinkles, and light glowing through where it is thin.
		float veins = smoothstep(0.86, 1.0, abs(sin(uv.x * 0.9 + cNoise(uv * 0.3) * 4.0)));
		float wrinkles = cFbm(uv * 0.6) * 0.3;
		vec3 c = base * (0.5 + diffuse * 0.55 + wrinkles) * light;
		c = mix(c, base * 0.35 * light, veins * 0.7);
		c += base * vec3(1.4, 0.6, 0.5) * (1.0 - nv) * 0.25 * light;
		c += vec3(1.0) * spec * 0.25 * light;
		return vec4(c, 1.0);
	}
	if (material == C_FEATHER) {
		// Barbs running out from the shaft at the feather's centre line (u = 0), with a soft sheen.
		float shaft = smoothstep(0.35, 0.0, abs(uv.x));
		float barbs = 0.5 + 0.5 * sin((uv.y * 2.4 - abs(uv.x) * 1.6) * 6.2832);
		float edge = cNoise(uv * vec2(0.5, 3.0));
		vec3 c = base * (0.72 + barbs * 0.12 + edge * 0.1) * (0.45 + diffuse * 0.7) * light;
		c = mix(c, base * 1.05 * light, shaft * 0.6);
		c += vec3(1.0) * spec * 0.35 * light + base * fresnel * 0.15 * light;
		return vec4(c, 1.0);
	}
	if (material == C_KNIT) {
		// Knitted rib: columns of interlocking stitches.
		vec2 k = uv * vec2(0.9, 1.3);
		float column = abs(fract(k.x) - 0.5) * 2.0;
		float stitch = abs(fract(k.y + (fract(k.x) < 0.5 ? 0.25 : 0.75)) - 0.5) * 2.0;
		float relief = 1.0 - 0.35 * column * column - 0.2 * stitch;
		vec3 c = base * relief * (0.5 + diffuse * 0.6) * light;
		c += base * cNoise(uv * 3.0) * 0.06 * light;
		return vec4(c, 1.0);
	}
	if (material == C_FUR) {
		float strands = cNoise(uv * vec2(4.0, 0.8)) * 0.5 + cNoise(uv * 9.0) * 0.5;
		vec3 c = base * (0.78 + strands * 0.32) * (0.5 + diffuse * 0.62) * light;
		c += base * fresnel * 0.35 * light;
		return vec4(c, 1.0);
	}
	if (material == C_LEAF) {
		float vein = smoothstep(0.25, 0.0, abs(uv.x)) * 0.25;
		vec3 c = base * (0.55 + diffuse * 0.6 + vein) * light + vec3(1.0) * spec * 0.2 * light;
		return vec4(c, 1.0);
	}
	if (material == C_BONE) {
		float grain = cNoise(uv * vec2(0.6, 3.0)) * 0.12;
		vec3 c = base * (0.55 + diffuse * 0.55 + grain) * light + vec3(1.0) * spec * 0.45 * light + cEnv(r) * fresnel * 0.2 * light;
		return vec4(c, 1.0);
	}
	if (material == C_SATIN || material == C_SILK) {
		// Woven sheen: a highlight stretched across the weave.
		float sheen = pow(1.0 - abs(dot(n, normalize(vec3(0.0, 1.0, 0.25)))), 2.0);
		float weave = material == C_SILK ? 0.0 : (0.5 + 0.5 * sin(uv.x * 6.0)) * 0.05;
		vec3 c = base * (0.5 + diffuse * 0.55 + weave) * light;
		c += mix(base, vec3(1.0), 0.5) * (spec * 0.6 + sheen * 0.18) * light;
		return vec4(c, 1.0);
	}
	// Matte: felt, wool and plain paint.
	vec3 c = base * (0.45 + diffuse * 0.62) * light * (0.96 + cNoise(uv * 2.0) * 0.08);
	c += vec3(1.0) * spec * 0.06 * light;
	return vec4(c, 1.0);
}
