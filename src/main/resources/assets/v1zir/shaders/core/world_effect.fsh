#version 150

layout(std140) uniform WorldParams {
	mat4 WorldProj;
	mat4 WorldView;
	vec4 WorldTime;
};

in vec2 texCoord0;
in vec4 vertexColor;
in vec3 relPos;

out vec4 fragColor;

// Surface height of a water ripple at radius r (0 centre, 1 rim of the disc) and progress p (0..1):
// a short train of rings behind a front that spreads and slows, smaller further out and as it
// settles, and a dimple at the centre that bobs a few times.
float rippleHeight(float r, float p) {
	float front = 0.07 + 0.86 * (1.0 - pow(1.0 - p, 1.6));
	float x = (r - front) / (0.12 + 0.05 * p);
	float ahead = max(x, 0.0);
	float behind = min(x, 0.0) / 1.5;
	float h = cos(x * 6.2831853) * exp(-ahead * ahead * 5.0 - behind * behind) * pow(1.0 - p, 1.2) * inversesqrt(0.3 + r * 1.6);
	return h + exp(-r * r * 125.0) * cos(p * 22.0) * pow(1.0 - p, 3.0) * 0.9;
}

// The bright sky a water surface with normal n reflects towards the eye (Fresnel weighted), and the
// direction it reflects.
float rippleSky(vec3 view, vec3 n, out vec3 bounce) {
	float m = 1.0 - clamp(-dot(view, n), 0.0, 1.0);
	bounce = reflect(view, n);
	return (0.04 + 0.96 * m * m * m * m * m) * (0.35 + 0.65 * clamp((bounce.y + 0.1) * 2.0, 0.0, 1.0));
}

// UV0 selects the falloff of every effect quad:
//   v < -1.5                 face of a rounded box: glowing rounded border (see Draw3D.roundedBox),
//                            thin when u < 0
//   v > 8                    water ripple disc (see Draw3D.ripple)
//   u > 2                    solid quad, no falloff
//   v in [2, 4], u in [-1,1] camera facing orb, radial falloff around (0, 3)
//   otherwise                glowing ribbon, falloff across u in [-1, 1]
void main() {
	float a;
	if (texCoord0.y < -1.5) {
		// UV0 = (16 qu + px, py - 16 qv - 8): position p on the face around its centre and the
		// half size h = q / 64, packed so that interpolation keeps both exact. A negative u asks for
		// the thin border: the same line with only a short glow (Crystal chams).
		bool thin = texCoord0.x < 0.0;
		vec2 uv = vec2(abs(texCoord0.x), texCoord0.y);
		vec2 q = floor(vec2(uv.x, -uv.y - 8.0) / 16.0 + 0.5);
		vec2 p = vec2(uv.x - 16.0 * q.x, uv.y + 16.0 * q.y + 8.0);
		vec2 h = q / 64.0;
		float r = min(0.12, 0.35 * min(h.x, h.y));
		vec2 e = abs(p) - h + r;
		float d = length(max(e, 0.0)) + min(max(e.x, e.y), 0.0) - r;
		// Distance inside the border in pixels: a crisp line and a soft glow of constant screen size,
		// antialiased outwards so the face corners stay round.
		float px = -d / max(fwidth(d), 1e-6);
		float glow = thin ? exp(-max(px, 0.0) * 0.7) * 0.3 : exp(-max(px, 0.0) * 0.16) * 0.5;
		a = (1.0 - smoothstep(thin ? 0.6 : 1.0, thin ? 1.8 : 2.4, px) + glow) * clamp(px + 1.0, 0.0, 1.0);
	} else if (texCoord0.y > 8.0) {
		// UV0 = (4 k + x, 16 + 4 b + y): (x, y) on the disc in [-1, 1], b = 1000 x progress and
		// k = pass + 2 g with pass 0 for the light the ripple adds (additive) and 1 for its shade
		// (blended), g = 10 x the shine along the crests.
		float k = floor(texCoord0.x * 0.25 + 0.5);
		float b = floor((texCoord0.y - 16.0) * 0.25 + 0.5);
		vec2 q = vec2(texCoord0.x - 4.0 * k, texCoord0.y - 16.0 - 4.0 * b);
		float p = b * 0.001;
		float r = length(q);
		if (r >= 1.0) discard;
		float h = rippleHeight(r, p);
		float slope = (rippleHeight(r + 0.004, p) - rippleHeight(abs(r - 0.004), p)) * 125.0;
		vec2 radial = q / max(r, 1.0e-5);
		vec3 n = normalize(vec3(-slope * 0.05 * radial.x, 1.0, -slope * 0.05 * radial.y));
		// Only what the waves change: flat water adds and takes away nothing, so the disc melts into
		// the ground under it. Slopes towards the sky light up, slopes towards the ground darken,
		// and a sparkle runs along the crests just above the mirror direction.
		vec3 view = normalize(relPos);
		vec3 bounce;
		vec3 flatBounce;
		float sky = rippleSky(view, n, bounce) - rippleSky(view, vec3(0.0, 1.0, 0.0), flatBounce);
		vec3 sparkle = normalize(vec3(view.x, 0.25 - view.y, view.z));
		float glint = pow(max(dot(bounce, sparkle), 0.0), 60.0) - pow(max(dot(flatBounce, sparkle), 0.0), 60.0);
		float change = sky * 3.0 + glint * 0.9;
		float edge = clamp((1.0 - r) * 8.0, 0.0, 1.0);
		if (mod(k, 2.0) < 0.5) {
			float shine = floor(k * 0.5) * 0.1;
			a = clamp(change + (1.0 - exp(-max(h, 0.0) * 2.0)) * shine, 0.0, 1.0) * edge;
		} else {
			a = clamp(-change * 1.2 + max(-h, 0.0) * 0.15, 0.0, 0.45) * edge;
		}
	} else if (texCoord0.x > 2.0) {
		a = 1.0;
	} else if (texCoord0.y >= 1.5) {
		float r = length(vec2(texCoord0.x, texCoord0.y - 3.0));
		a = exp(-r * r * 5.0) * 0.55 + exp(-r * r * 28.0) * 0.9;
		a *= 1.0 - smoothstep(0.8, 1.0, r);
	} else {
		float x = abs(texCoord0.x);
		a = exp(-x * x * 18.0) + exp(-x * x * 3.0) * 0.45;
		a *= 1.0 - smoothstep(0.9, 1.0, x);
	}
	if (a <= 0.003) discard;
	fragColor = vec4(vertexColor.rgb, vertexColor.a * min(a, 1.0));
}
