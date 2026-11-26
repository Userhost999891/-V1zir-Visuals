#version 150

#moj_import <v1zir:knife_preview.glsl>
#moj_import <v1zir:knife.glsl>

in vec3 viewPos;
in vec3 viewNormal;
in vec2 pattern;
in vec4 tint;
flat in ivec2 surface;

out vec4 fragColor;

void main() {
	vec3 n = normalize(viewNormal);
	// Studio lighting stands in for the world: full light, and vanilla's directional term from two fixed lights.
	float shade = min(1.0, (max(0.0, dot(PreviewLight0.xyz, n)) + max(0.0, dot(PreviewLight1.xyz, n))) * 0.6 + 0.4);
	vec3 color = knifeShade(surface.x, surface.y, pattern, tint, viewPos, viewNormal, vec3(PreviewParams.y), shade);
	// Premultiplied over a transparent target (see KnifePreview).
	fragColor = vec4(color, 1.0);
}
