#version 150

#moj_import <v1zir:sdf_shapes.glsl>
#moj_import <v1zir:transition.glsl>

// The click GUI window captured into Sampler0 (premultiplied), composited over the frame:
// mode 0 swings it in with a perspective tilt, 1 fades it out, 2 freezes it over and fades it, 3 draws
// only the backdrop (under the shatter shards). Closing modes also fade the blurred, dimmed backdrop.
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

in vec2 texCoord;

out vec4 fragColor;

const vec2 DISC[8] = vec2[8](vec2(-0.326, -0.406), vec2(-0.840, -0.074), vec2(-0.696, 0.457), vec2(-0.203, 0.621),
	vec2(0.962, -0.195), vec2(0.473, -0.480), vec2(0.519, 0.767), vec2(0.185, -0.893));

vec4 window(vec2 p) {
	vec2 uv = transitionUv(p);
	if (uv.x < 0.0 || uv.y < 0.0 || uv.x > 1.0 || uv.y > 1.0) return vec4(0.0);
	return texture(Sampler0, uv);
}

// The window as a plane tilted back around its horizontal axis and pushed away, seen from in front of
// the screen centre: the ray through this pixel meets the plane at the window point to sample.
vec4 warp(vec2 p) {
	vec2 c = Rect.xy + Rect.zw * 0.5;
	vec2 s = Screen.xy * 0.5;
	float tilt = Shape.z;
	vec3 eye = vec3(s, -Shape.y);
	vec3 dir = vec3(p - s, Shape.y);
	vec3 center = vec3(c, Shape.w);
	vec3 normal = vec3(0.0, sin(tilt), cos(tilt));
	vec3 down = vec3(0.0, cos(tilt), -sin(tilt));
	float t = dot(normal, center - eye) / dot(normal, dir);
	vec3 q = eye + dir * t - center;
	vec4 col = window(c + vec2(q.x, dot(q, down)));
	// A plane turned away from the light reads a touch darker (matte, no highlight).
	return vec4(col.rgb * (1.0 - 0.3 * abs(sin(tilt))), col.a);
}

float valueNoise(vec2 p) {
	vec2 i = floor(p);
	vec2 f = fract(p);
	f = f * f * (3.0 - 2.0 * f);
	float a = transitionHash(i);
	float b = transitionHash(i + vec2(1.0, 0.0));
	float c = transitionHash(i + vec2(0.0, 1.0));
	float d = transitionHash(i + vec2(1.0, 1.0));
	return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

// Distances to the nearest and second nearest crystal seed and a random value of the nearest cell.
vec3 crystals(vec2 p) {
	vec2 i = floor(p);
	vec2 f = fract(p);
	float f1 = 8.0;
	float f2 = 8.0;
	float id = 0.0;
	for (int y = -1; y <= 1; y++) {
		for (int x = -1; x <= 1; x++) {
			vec2 g = vec2(float(x), float(y));
			vec2 o = vec2(transitionHash(i + g), transitionHash(i + g + 57.0));
			vec2 r = g + o - f;
			float d = dot(r, r);
			if (d < f1) {
				f2 = f1;
				f1 = d;
				id = transitionHash(i + g + 113.0);
			} else if (d < f2) {
				f2 = d;
			}
		}
	}
	return vec3(sqrt(f1), sqrt(f2), id);
}

// Ice growing in from the window edges in crystalline facets: the frozen part blurs, turns icy white,
// glints, and a bright seam runs along the growing front.
vec4 frost(vec2 p) {
	vec2 c = Rect.xy + Rect.zw * 0.5;
	float halfSize = min(Rect.z, Rect.w) * 0.5;
	float inner = clamp(-v1zirRoundBox(p - c, Rect.zw * 0.5, Shape.x) / halfSize, 0.0, 1.0);
	float unit = halfSize * 0.15;
	vec3 cell = crystals(p / unit);
	float fine = valueNoise(p / (unit * 0.3)) * 0.6 + valueNoise(p / (unit * 0.12)) * 0.4;
	float freezeAt = inner * 0.9 + cell.z * 0.22 + fine * 0.18;
	float front = Anim.x * 1.45;
	float amount = smoothstep(freezeAt - 0.08, freezeAt, front);
	float seam = exp(-pow((front - freezeAt) / 0.035, 2.0));

	float radius = amount * unit * 0.22;
	vec4 base = window(p);
	for (int i = 0; i < 8; i++) base += window(p + DISC[i] * radius);
	base /= 9.0;
	vec3 ice = vec3(0.84, 0.92, 1.0);
	float edges = 1.0 - smoothstep(0.0, 0.08, cell.y - cell.x);
	vec3 col = mix(base.rgb, ice * base.a, 0.5 * amount);
	col += ice * base.a * (((cell.z - 0.5) * 0.08 + edges * 0.22) * amount + seam * 0.35);
	float glint = step(0.9965, transitionHash(p * 0.5 + 3.7)) * amount * (0.5 + 0.5 * sin(Anim.x * 40.0 + cell.z * 30.0));
	col += glint * 0.7 * base.a;
	return vec4(col, base.a);
}

void main() {
	vec2 p = vec2(gl_FragCoord.x, Screen.y - gl_FragCoord.y);
	int mode = int(Anim.z + 0.5);
	vec4 gui = vec4(0.0);
	// Outside the window only the backdrop is left to draw (the warp may reach anywhere).
	bool inside = all(greaterThan(p, Rect.xy - 4.0)) && all(lessThan(p, Rect.xy + Rect.zw + 4.0));
	if (mode == 0) {
		gui = warp(p);
	} else if (mode == 1 && inside) {
		vec2 c = Rect.xy + Rect.zw * 0.5;
		gui = window(c + (p - c) / Anim.w);
	} else if (mode == 2 && inside) {
		gui = frost(p);
	}
	gui *= Anim.y;

	vec4 back = vec4(0.0);
	if (Backdrop.x > 0.0) {
		if (Backdrop.y > 0.5) {
			// Same treatment as the glass backdrop the open GUI draws (gui_glass.fsh), dimmed.
			vec3 b = texture(Sampler1, texCoord).rgb;
			float luma = dot(b, vec3(0.2126, 0.7152, 0.0722));
			b = mix(vec3(luma), b, 0.85) * (1.0 - 0.3 * smoothstep(0.45, 1.0, luma));
			back = vec4(mix(b, vec3(0.0), Backdrop.z), 1.0) * Backdrop.x;
		} else {
			back = vec4(0.0, 0.0, 0.0, Backdrop.z * Backdrop.x);
		}
	}
	fragColor = gui + back * (1.0 - gui.a);
}
