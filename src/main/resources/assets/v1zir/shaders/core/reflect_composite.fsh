#version 150

// Composite pass of the reflections (render/Reflections) at full resolution, over the frame: the
// traced reflections blurred by roughness, the wet look and the sun or moon glint. Pixels it does
// not change are discarded.
#moj_import <v1zir:reflect.glsl>

uniform sampler2D Sampler4; // traced reflections, alpha 1 where a pixel reflects

out vec4 fragColor;

void main() {
	ivec2 p = ivec2(gl_FragCoord.xy);
	SurfaceInfo s;
	if (!rSurface(p, s)) discard;
	vec4 scene = texelFetch(Sampler0, p, 0);
	// Four taps spread by roughness, weighted by the trace alpha so pixels that do not reflect never bleed in.
	vec2 uv = gl_FragCoord.xy / Screen.xy;
	vec2 r = (0.5 + s.roughness * 8.0) / Screen.zw;
	vec4 sum = texture(Sampler4, uv + vec2(-r.x, -0.5 * r.y)) + texture(Sampler4, uv + vec2(0.5 * r.x, -r.y))
		+ texture(Sampler4, uv + vec2(r.x, 0.5 * r.y)) + texture(Sampler4, uv + vec2(-0.5 * r.x, r.y));
	vec3 reflection = sum.rgb / max(sum.a, 0.001);
	float coverage = clamp(sum.a * 0.5, 0.0, 1.0);
	// Metal tints what it reflects with its own colour, so the sky's hue must not take over.
	reflection = mix(reflection, vec3(dot(reflection, vec3(0.2126, 0.7152, 0.0722))), 0.55 * s.metallic);
	// A water film darkens what is under it and deepens its colour.
	vec3 base = mix(scene.rgb, pow(scene.rgb, vec3(1.25)) * 0.88, s.wet);
	vec3 color = base * mix(1.0, s.diffuse, coverage) + reflection * s.tint * coverage;
	if (s.glint > 0.0 && LightDir.w > 0.0) {
		vec3 bounce = reflect(normalize(s.pos), s.normal);
		float power = mix(1500.0, 24.0, sqrt(s.roughness));
		// Surfaces in shadow get no highlight.
		float lit = smoothstep(0.06, 0.3, dot(scene.rgb, vec3(0.2126, 0.7152, 0.0722)));
		float highlight = pow(max(dot(bounce, LightDir.xyz), 0.0), power) * (power + 8.0) * 0.0015;
		color += LightColor.rgb * (LightDir.w * s.glint * lit * highlight);
	}
	fragColor = vec4(clamp(color, 0.0, 1.0), scene.a);
}
