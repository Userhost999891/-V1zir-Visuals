#version 150

#moj_import <v1zir:snow_params.glsl>

// Where snow can fall: one texel per block column round the camera (128 by 128), red = the top of the
// highest block that stops it, above the map's base.
uniform sampler2D Sampler0;

// Every flake is a quad from a static buffer: three random numbers in Position (x is 1 more for a flake of the near
// box), its corner in UV0 (-1 or 1), four more random numbers in Color. Nothing about a flake is worked out on the CPU.
in vec3 Position;
in vec2 UV0;
in vec4 Color;

out vec2 corner;
out float fade;
out float soft;
// The flake's own random numbers (the shape of its clump) and the sun's direction in view space (its shading).
out vec4 seed4;
out vec3 sunView;

void main() {
	float t = SnowCamera.w;
	// Two boxes of snow travel with the camera: a small dense one close by and a large one for the distance. The
	// large box is three times the small one each way, so wrapping round either agrees.
	bool near = Position.x >= 1.0;
	vec3 seed = vec3(near ? Position.x - 1.0 : Position.x, Position.yz);
	vec3 box = near ? SnowNear.xyz : SnowBox.xyz;
	float edgeFade = near ? 2.0 : SnowBox.w;
	float big = Color.r;
	float phase = Color.g * 6.2831853;
	// Larger flakes fall a little faster; all drift with the wind and sway in loose loops as they tumble.
	vec3 p = seed * box;
	p.y -= t * SnowWind.y * mix(0.78, 1.22, big);
	// The wind's drift is added up on the CPU frame by frame (it changes with every gust); each flake
	// answers it a little differently, in loose loops of its own.
	p.xz += SnowWind.xz;
	float own = mix(0.7, 1.3, Color.b);
	p.x += sin(t * 0.9 * own + phase) * 0.32 + sin(t * 2.3 + phase * 1.7) * 0.08;
	p.z += cos(t * 0.75 * own + phase * 1.3) * 0.32;
	p.y += sin(t * 1.4 + phase * 2.1) * 0.06;
	// Every flake wraps round inside its box.
	vec3 rel = mod(p - SnowCamera.xyz, box) - box * 0.5;
	// Faded towards the box's edges (no flake pops in or out) and right in front of the eye.
	vec3 edge = box * 0.5 - abs(rel);
	float d = length(rel);
	fade = clamp(min(min(edge.x, edge.y), edge.z) / edgeFade, 0.0, 1.0) * smoothstep(0.5, 1.4, d);
	if (SnowHeight.y > 0.5) {
		// Under a roof (or anything snow stops on) there is no snow.
		ivec2 column = ivec2(floor(rel.xz + SnowHeight.zw)) + ivec2(64);
		if (column.x >= 0 && column.y >= 0 && column.x < 128 && column.y < 128) {
			float top = texelFetch(Sampler0, column, 0).r * 255.0;
			if (rel.y + SnowHeight.x < top) fade = 0.0;
		}
	}
	// Close flakes are out of focus: a little larger and softer, as a lens sees them.
	soft = 1.0 - smoothstep(0.6, 2.5, d);
	float size = SnowWind.w * mix(0.55, 1.45, big * big) * (1.0 + soft * 0.8);
	seed4 = Color;
	sunView = normalize((SnowView * vec4(SnowSun.xyz, 0.0)).xyz);
	if (fade <= 0.0) {
		gl_Position = vec4(0.0, 0.0, 2.0, 1.0);
		corner = UV0;
		return;
	}
	// A quad facing the camera, turning slowly as the flake tumbles.
	float spin = t * mix(-1.6, 1.6, Color.a) + phase;
	vec2 c = vec2(cos(spin) * UV0.x - sin(spin) * UV0.y, sin(spin) * UV0.x + cos(spin) * UV0.y);
	vec4 view = SnowView * vec4(rel, 1.0);
	// Far flakes would shrink below a pixel and flicker: they keep a pixel and a half across and fade instead, so
	// the light they give stays the same and far snow reads as a soft haze.
	float pixels = size * SnowProj[1][1] / max(-view.z, 0.05) * SnowNear.w * 0.5;
	if (pixels < 0.75) {
		float k = pixels / 0.75;
		fade *= k * k;
		size *= 1.0 / max(k, 0.05);
	}
	view.xy += c * size;
	gl_Position = SnowProj * view;
	corner = UV0;
}
