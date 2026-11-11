#version 150

// The lens over the finished world picture (render/PostFx), after grading and before the GUI: colour fringes that
// grow towards the edges, a vignette, film grain and letterbox bars.
#moj_import <v1zir:post_params.glsl>

uniform sampler2D Sampler0; // the frame

out vec4 fragColor;

void main() {
	vec2 uv = gl_FragCoord.xy * Screen.xy;
	vec2 centre = uv - 0.5;
	vec2 aspect = centre * vec2(Screen.z, 1.0);
	// 0 in the middle, about 1 in the corners of a wide screen.
	float r2 = dot(aspect, aspect);
	vec4 scene = texture(Sampler0, uv);
	vec3 col = scene.rgb;
	if (Lens.x > 0.0) {
		// Red is drawn a little larger and blue a little smaller, as a simple lens bends them.
		vec2 shift = centre * (Lens.x * 0.014 * r2);
		col.r = texture(Sampler0, uv + shift).r;
		col.b = texture(Sampler0, uv - shift).b;
	}
	if (Lens.w > 0.0) {
		float v = smoothstep(0.2, 1.05, r2);
		col *= 1.0 - Lens.w * v * v;
	}
	if (Lens.y > 0.0) {
		// Grain that changes 24 times a second, finer in the highlights than in the shadows.
		float frame = floor(Screen.w * 24.0);
		float n = pHash(gl_FragCoord.xy + frame * 37.0) + pHash(gl_FragCoord.yx + frame * 17.0 + 11.0) - 1.0;
		col += n * Lens.y * 0.1 * (1.0 - 0.6 * clamp(dot(col, vec3(0.2126, 0.7152, 0.0722)), 0.0, 1.0));
	}
	if (Lens.z > 0.0) {
		float edge = min(uv.y, 1.0 - uv.y);
		col *= smoothstep(Lens.z - Screen.y, Lens.z + Screen.y, edge);
	}
	fragColor = vec4(clamp(col, 0.0, 1.0), scene.a);
}
