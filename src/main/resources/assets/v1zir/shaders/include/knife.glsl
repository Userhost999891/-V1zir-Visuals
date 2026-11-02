// Knife shading (render/knife): materials (KnifeMesh), the procedural blade finishes (KnifeFinish) and the lighting.
// Everything is computed in view space (+y up, the camera looks down -z). Polished metal reflects a soft studio
// environment and catches a key light from the view; all of it is scaled by the world's light at the knife (the light
// map colour), so a knife in a dark cave stays dark. Vanilla's directional term shades it like the arm holding it.

const int KNIFE_BLADE = 0;
const int KNIFE_BEVEL = 1;
const int KNIFE_EDGE = 2;
const int KNIFE_STEEL = 3;
const int KNIFE_DARK = 4;
const int KNIFE_BRASS = 5;
const int KNIFE_WOOD = 6;
const int KNIFE_POLYMER = 7;
const int KNIFE_CORD = 8;
const int KNIFE_LEATHER = 9;

const int FINISH_STEEL = 0;
const int FINISH_SUNSET = 1;
const int FINISH_RUBY = 2;
const int FINISH_SAPPHIRE = 3;
const int FINISH_EMERALD = 4;
const int FINISH_DAMASCUS = 5;
const int FINISH_TIGER = 6;
const int FINISH_NIGHT = 7;
const int FINISH_MARBLE = 8;
const int FINISH_CARBON = 9;
const int FINISH_GOLD = 10;

// Key light: from above and a little to the left of the view.
const vec3 KNIFE_KEY = vec3(-0.38, 0.62, 0.69);

float knifeHash(vec2 p) {
	p = fract(p * vec2(123.34, 456.21));
	p += dot(p, p + 45.32);
	return fract(p.x * p.y);
}

float knifeNoise(vec2 p) {
	vec2 i = floor(p);
	vec2 f = fract(p);
	f = f * f * (3.0 - 2.0 * f);
	float a = knifeHash(i);
	float b = knifeHash(i + vec2(1.0, 0.0));
	float c = knifeHash(i + vec2(0.0, 1.0));
	float d = knifeHash(i + vec2(1.0, 1.0));
	return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float knifeFbm(vec2 p) {
	float v = 0.0;
	float a = 0.5;
	for (int i = 0; i < 4; i++) {
		v += a * knifeNoise(p);
		p = p * 2.03 + vec2(17.1, 9.3);
		a *= 0.5;
	}
	return v;
}

// What polished metal reflects: a studio around the viewer. A large softbox above and behind the camera (what a
// blade turned towards the eye mirrors), an overhead strip, a cool strip on the left, a warm one on the right, a
// bright horizon line and a dim floor. rough widens every light (satin and brushed metal).
vec3 knifeEnv(vec3 r, float rough) {
	float w = 1.0 + rough * 5.0;
	vec3 c = mix(vec3(0.16, 0.16, 0.17), vec3(0.6, 0.62, 0.66), smoothstep(-0.5, 0.8, r.y));
	c += vec3(0.32, 0.31, 0.3) * exp(-abs(r.y + 0.04) * 14.0 / w);
	vec3 front = normalize(vec3(-0.2, 0.42, 1.0));
	c += vec3(0.78, 0.77, 0.75) * smoothstep(0.62 - 0.25 * rough, 0.93, dot(r, front));
	vec3 top = normalize(vec3(0.05, 1.0, 0.2));
	c += vec3(1.1, 1.08, 1.04) * pow(max(dot(r, top), 0.0), 34.0 / w);
	vec3 left = normalize(vec3(-1.0, 0.3, 0.25));
	c += vec3(0.5, 0.58, 0.72) * pow(max(dot(r, left), 0.0), 10.0 / w);
	vec3 right = normalize(vec3(1.0, 0.15, 0.1));
	c += vec3(0.7, 0.58, 0.44) * pow(max(dot(r, right), 0.0), 12.0 / w);
	return c;
}

// Specular of the key light: an anisotropic lobe, tight along the brushing direction t and wide across it.
float knifeKeySpec(vec3 n, vec3 v, vec3 t, float along, float across) {
	vec3 h = normalize(KNIFE_KEY + v);
	vec3 b = normalize(cross(n, t));
	float nh = max(dot(n, h), 0.0);
	float th = dot(t, h) / along;
	float bh = dot(b, h) / across;
	return exp(-2.0 * (th * th + bh * bh) / (1.0 + nh)) * step(0.0, dot(n, KNIFE_KEY));
}

struct KnifeSurface {
	vec3 albedo;    // diffuse colour (dielectrics) or reflectance (metals)
	float metal;    // 1 metal, 0 dielectric
	float rough;    // 0 mirror, 1 matte
	float aniso;    // brushed: how much the highlight stretches across the brushing
	float coat;     // clear coat over a coloured layer (anodised and painted finishes)
};

KnifeSurface knifeMetal(vec3 c, float rough) {
	return KnifeSurface(c, 1.0, rough, 0.0, 0.0);
}

// The blade's finish; along runs from the base (0) to the tip (1), across from the edge (0) to the spine (1).
KnifeSurface knifeFinish(int finish, vec2 uv, float along, float across, bool bevel) {
	if (finish == FINISH_SUNSET) {
		// Anodised fade: violet at the base through magenta and coral into gold at the tip.
		float t = clamp(along * 1.08 - 0.04 + (knifeFbm(uv * 0.6) - 0.5) * 0.18, 0.0, 1.0);
		vec3 c = mix(vec3(0.52, 0.24, 1.0), vec3(1.0, 0.3, 0.72), smoothstep(0.0, 0.42, t));
		c = mix(c, vec3(1.0, 0.56, 0.3), smoothstep(0.38, 0.7, t));
		c = mix(c, vec3(1.0, 0.9, 0.48), smoothstep(0.66, 1.0, t));
		return KnifeSurface(c, 1.0, 0.12, 0.0, 0.6);
	}
	if (finish == FINISH_RUBY || finish == FINISH_SAPPHIRE || finish == FINISH_EMERALD) {
		// Candy over polished steel: the colour pools deeper in soft clouds, and the bevel catches more light.
		vec3 deep = finish == FINISH_RUBY ? vec3(0.78, 0.03, 0.1) : finish == FINISH_SAPPHIRE ? vec3(0.06, 0.2, 0.92) : vec3(0.03, 0.62, 0.26);
		vec3 bright = finish == FINISH_RUBY ? vec3(1.0, 0.36, 0.4) : finish == FINISH_SAPPHIRE ? vec3(0.42, 0.7, 1.0) : vec3(0.42, 1.0, 0.62);
		float cloud = knifeFbm(uv * 0.9 + vec2(3.1, 1.7));
		vec3 c = mix(deep, bright, smoothstep(0.32, 0.78, cloud) * 0.8 + (bevel ? 0.2 : 0.0));
		return KnifeSurface(c, 1.0, 0.08, 0.0, 0.75);
	}
	if (finish == FINISH_DAMASCUS) {
		// Folded steel: wavy layers, alternately bright and darkly etched, bending through the blade.
		float warp = knifeFbm(uv * vec2(0.55, 0.9)) * 3.2 + knifeFbm(uv * 1.7 + 4.0) * 0.6;
		float layers = sin((uv.x * 0.9 + uv.y * 2.6 + warp) * 5.2);
		float bright = smoothstep(-0.25, 0.35, layers);
		vec3 c = mix(vec3(0.3, 0.3, 0.31), vec3(0.86, 0.86, 0.88), bright);
		return KnifeSurface(c, 1.0, mix(0.38, 0.14, bright), 0.0, 0.0);
	}
	if (finish == FINISH_TIGER) {
		// Gold anodising with dark, ragged diagonal stripes.
		float s = sin((uv.x * 1.35 - uv.y * 0.9 + knifeFbm(uv * vec2(0.8, 1.6)) * 2.4) * 3.1);
		float stripe = smoothstep(0.45, 0.8, s);
		vec3 c = mix(vec3(1.0, 0.72, 0.22), vec3(0.14, 0.06, 0.02), stripe);
		c = mix(c, vec3(1.0, 0.82, 0.4), (1.0 - stripe) * smoothstep(0.6, 1.0, knifeNoise(uv * 3.0)) * 0.3);
		return KnifeSurface(c, 1.0, 0.18, 0.0, 0.5);
	}
	if (finish == FINISH_NIGHT) {
		// Black coating with a faint satin sheen; the honed bevel shows the steel underneath.
		if (bevel) return KnifeSurface(vec3(0.7, 0.71, 0.73), 1.0, 0.14, 0.4, 0.0);
		float grain = knifeNoise(uv * 14.0) * 0.03;
		return KnifeSurface(vec3(0.05 + grain), 0.0, 0.55, 0.0, 0.25);
	}
	if (finish == FINISH_MARBLE) {
		// Swirled candy: red, ink blue and gold folded into each other.
		vec2 q = uv * 0.55;
		float a = knifeFbm(q + knifeFbm(q * 1.6 + 2.0) * 1.8);
		float b = knifeFbm(q * 1.3 + vec2(5.2, 1.3) + a * 2.2);
		vec3 c = mix(vec3(0.95, 0.08, 0.16), vec3(0.12, 0.26, 0.95), smoothstep(0.35, 0.65, a));
		c = mix(c, vec3(1.0, 0.84, 0.3), smoothstep(0.55, 0.78, b) * 0.85);
		return KnifeSurface(c, 1.0, 0.1, 0.0, 0.7);
	}
	if (finish == FINISH_CARBON) {
		// Twill weave of carbon fibre under clear coat; the bevel is bare steel.
		if (bevel) return KnifeSurface(vec3(0.7, 0.71, 0.73), 1.0, 0.14, 0.4, 0.0);
		vec2 w = uv * 4.2;
		vec2 cell = floor(w);
		vec2 f = fract(w);
		bool warp = mod(cell.x + cell.y, 2.0) < 1.0;
		float fibre = warp ? f.y : f.x;
		float tow = 0.5 + 0.5 * sin(fibre * 6.2832 * 3.0);
		float shade = warp ? 0.07 : 0.12;
		return KnifeSurface(vec3(shade + tow * 0.035), 0.0, 0.3, 0.0, 0.85);
	}
	if (finish == FINISH_GOLD) {
		return KnifeSurface(vec3(1.0, 0.82, 0.42) * (0.95 + knifeNoise(uv * vec2(1.0, 40.0)) * 0.05), 1.0, 0.1, 0.5, 0.0);
	}
	// Brushed steel: fine streaks along the blade.
	float streak = knifeNoise(vec2(uv.x * 0.6, uv.y * 70.0)) * 0.6 + knifeNoise(vec2(uv.x * 2.0, uv.y * 190.0)) * 0.4;
	return KnifeSurface(vec3(0.74, 0.75, 0.77) * (0.88 + streak * 0.16), 1.0, 0.18, 1.0, 0.0);
}

KnifeSurface knifeMaterial(int material, int finish, vec2 uv, vec4 tint) {
	if (material == KNIFE_BLADE || material == KNIFE_BEVEL) return knifeFinish(finish, uv, tint.r, tint.g, material == KNIFE_BEVEL);
	if (material == KNIFE_EDGE) return KnifeSurface(vec3(0.86, 0.87, 0.9), 1.0, 0.06, 0.6, 0.0);
	if (material == KNIFE_STEEL) return KnifeSurface(vec3(0.58, 0.59, 0.61), 1.0, 0.3, 0.6, 0.0);
	if (material == KNIFE_DARK) return KnifeSurface(vec3(0.11, 0.11, 0.12), 1.0, 0.42, 0.3, 0.0);
	if (material == KNIFE_BRASS) return knifeMetal(vec3(0.86, 0.64, 0.32), 0.22);
	if (material == KNIFE_WOOD) {
		// Grain running along the handle, with darker rings and a little figure.
		float g = knifeFbm(vec2(uv.x * 0.35, uv.y * 3.2)) + knifeNoise(vec2(uv.x * 0.6, uv.y * 14.0)) * 0.25;
		float rings = 0.5 + 0.5 * sin(g * 18.0);
		return KnifeSurface(tint.rgb * (0.72 + rings * 0.42), 0.0, 0.45, 0.0, 0.35);
	}
	if (material == KNIFE_CORD) {
		// Paracord: diagonal ridges of the weave, crossing back and forth along the wrap.
		float a = sin((uv.x * 7.0 + uv.y * 4.0) * 3.1416);
		float b = sin((uv.x * 7.0 - uv.y * 4.0) * 3.1416);
		float weave = max(a, b) * 0.5 + 0.5;
		return KnifeSurface(tint.rgb * (0.55 + weave * 0.6), 0.0, 0.8, 0.0, 0.0);
	}
	if (material == KNIFE_LEATHER) {
		float n = knifeFbm(uv * 3.5);
		return KnifeSurface(tint.rgb * (0.78 + n * 0.35), 0.0, 0.6, 0.0, 0.15);
	}
	// G10, polymer and rubber: a fine texture under a soft sheen.
	float n = knifeNoise(uv * 11.0) * 0.6 + knifeNoise(uv * 27.0) * 0.4;
	return KnifeSurface(tint.rgb * (0.85 + n * 0.3), 0.0, 0.55, 0.0, 0.1);
}

// Final colour. world is the light map colour at the knife, shade vanilla's directional term (0.4 to 1).
vec3 knifeShade(int material, int finish, vec2 uv, vec4 tint, vec3 viewPos, vec3 normal, vec3 world, float shade) {
	vec3 n = normalize(normal);
	vec3 v = normalize(-viewPos);
	if (dot(n, v) < 0.0) n = -n;
	// Brushing direction: where the pattern's u grows on screen, kept in the surface.
	vec3 dpx = dFdx(viewPos);
	vec3 dpy = dFdy(viewPos);
	vec2 dux = dFdx(uv);
	vec2 duy = dFdy(uv);
	vec3 t = dpx * duy.y - dpy * dux.y;
	t -= n * dot(n, t);
	t = dot(t, t) > 1e-12 ? normalize(t) : normalize(cross(n, vec3(0.0, 0.0, 1.0)) + vec3(1e-4));
	KnifeSurface s = knifeMaterial(material, finish, uv, tint);
	float nv = max(dot(n, v), 0.0);
	float fresnel = pow(1.0 - nv, 5.0);
	vec3 r = reflect(-v, n);
	float key = max(dot(n, KNIFE_KEY), 0.0);
	// The world's light, a little brighter where vanilla's sun-facing term lights the arm too.
	vec3 light = world * mix(0.78, 1.08, (shade - 0.4) / 0.6);
	vec3 color;
	if (s.metal > 0.5) {
		// Brushed metal smears its reflection across the brushing.
		vec3 b = normalize(cross(n, t));
		float spread = s.aniso * 0.22;
		vec3 env = (knifeEnv(r, s.rough) * 2.0 + knifeEnv(normalize(r + b * spread), s.rough) + knifeEnv(normalize(r - b * spread), s.rough)) * 0.25;
		vec3 f = mix(s.albedo, vec3(1.0), fresnel * (0.5 + s.coat * 0.3));
		color = env * f * light;
		float along = mix(0.08, 0.05, s.aniso) + s.rough * 0.35;
		float across = along + s.aniso * 0.55;
		color += mix(s.albedo, vec3(1.0), 0.35 + 0.4 * s.coat) * knifeKeySpec(n, v, t, along, across) * (1.2 - s.rough) * 1.4 * light;
		// Anodised colour also scatters a little light of its own; and no metal goes fully black where it mirrors the floor.
		color += s.albedo * (s.coat * (0.16 + key * 0.24) + 0.1 * shade) * light;
	} else {
		color = s.albedo * (shade * 0.72 + key * 0.32) * light;
		vec3 env = knifeEnv(r, max(s.rough, 0.25));
		float gloss = mix(0.04, 0.2, s.coat) + fresnel * (0.25 + s.coat * 0.5);
		color += env * gloss * (1.0 - s.rough * 0.6) * light;
		color += knifeKeySpec(n, v, t, 0.06 + s.rough * 0.5, 0.06 + s.rough * 0.5) * (0.12 + s.coat * 0.7) * light;
	}
	return color;
}
