#version 150

#moj_import <v1zir:transition.glsl>

// The click GUI window captured into Sampler0 (premultiplied), composited over the frame:
// mode 0 swings it in with a perspective tilt, 1 fades it out, 2 draws only the backdrop (under the
// shatter shards). Closing modes also fade the blurred, dimmed backdrop.
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

in vec2 texCoord;

out vec4 fragColor;

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
