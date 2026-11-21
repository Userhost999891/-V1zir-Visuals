#version 150

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <v1zir:knife_world.glsl>
#moj_import <v1zir:knife.glsl>

in vec3 viewPos;
in vec3 viewNormal;
in vec2 pattern;
in vec4 tint;
flat in ivec2 surface;
in vec3 worldLight;
in float shade;
in float fog;

out vec4 fragColor;

void main() {
	vec3 color = knifeShade(surface.x, surface.y, pattern, tint, viewPos, viewNormal, worldLight, shade);
	color = mix(color, FogColor.rgb, fog * FogColor.a);
	fragColor = vec4(color, 1.0) * ColorModulator;
}
