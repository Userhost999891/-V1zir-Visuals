#version 150

#moj_import <v1zir:snow_params.glsl>

// Where snow can fall: one texel per block column round the camera (128 by 128), red = the top of the
// highest block that stops it, above the map's base.
uniform sampler2D Sampler0;

// Every flake is a quad from a static buffer: three random numbers in Position, its corner in UV0 (-1 or 1),
// four more random numbers in Color. Nothing about a flake is worked out on the CPU.
in vec3 Position;
in vec2 UV0;
in vec4 Color;

out vec2 corner;
out float fade;
out float soft;

void main() {
	float t = SnowCamera.w;
	vec3 seed = Position;
	float big = Color.r;
	float phase = Color.g * 6.2831853;
	// Larger flakes fall a little faster; all drift with the wind and sway in loose loops as they tumble.
	vec3 p = seed * SnowBox.xyz;
	p.y -= t * SnowWind.y * mix(0.78, 1.22, big);
	p.xz += t * SnowWind.xz * mix(0.85, 1.15, Color.b);
	p.x += sin(t * 0.9 + phase) * 0.32 + sin(t * 2.3 + phase * 1.7) * 0.08;
	p.z += cos(t * 0.75 + phase * 1.3) * 0.32;
	p.y += sin(t * 1.4 + phase * 2.1) * 0.06;
	// The box of snow travels with the camera: every flake wraps round inside it.
	vec3 rel = mod(p - SnowCamera.xyz, SnowBox.xyz) - SnowBox.xyz * 0.5;
	// Faded towards the box's edges (no flake pops in or out) and right in front of the eye.
	vec3 edge = SnowBox.xyz * 0.5 - abs(rel);
	float d = length(rel);
	fade = clamp(min(min(edge.x, edge.y), edge.z) / SnowBox.w, 0.0, 1.0) * smoothstep(0.35, 1.1, d);
	if (SnowHeight.y > 0.5) {
		// Under a roof (or anything snow stops on) there is no snow.
		ivec2 column = ivec2(floor(rel.xz + SnowHeight.zw)) + ivec2(64);
		if (column.x >= 0 && column.y >= 0 && column.x < 128 && column.y < 128) {
			float top = texelFetch(Sampler0, column, 0).r * 255.0;
			if (rel.y + SnowHeight.x < top) fade = 0.0;
		}
	}
	// Close flakes are out of focus: a little larger and softer, as a lens sees them.
	soft = smoothstep(2.5, 0.6, d);
	float size = SnowWind.w * mix(0.55, 1.45, big * big) * (1.0 + soft * 0.8);
	if (fade <= 0.0) {
		gl_Position = vec4(0.0, 0.0, 2.0, 1.0);
		corner = UV0;
		return;
	}
	// A quad facing the camera, turning slowly as the flake tumbles.
	float spin = t * mix(-1.6, 1.6, Color.a) + phase;
	vec2 c = vec2(cos(spin) * UV0.x - sin(spin) * UV0.y, sin(spin) * UV0.x + cos(spin) * UV0.y);
	vec4 view = SnowView * vec4(rel, 1.0);
	view.xy += c * size;
	gl_Position = SnowProj * view;
	corner = UV0;
}
