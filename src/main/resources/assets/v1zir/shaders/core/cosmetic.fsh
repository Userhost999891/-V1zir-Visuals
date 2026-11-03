#version 150

#moj_import <v1zir:knife_world.glsl>
#moj_import <v1zir:cosmetic.glsl>

in vec3 viewPos;
in vec3 viewNormal;
in vec2 pattern;
in vec4 tint;
flat in int material;
flat in float time;
in vec3 worldLight;
in float shade;
in float fog;

out vec4 fragColor;

void main() {
	vec4 color = cosmeticShade(material, pattern, tint, viewPos, viewNormal, worldLight, shade, time);
	color.rgb = mix(color.rgb, FogColor.rgb, fog * FogColor.a);
	fragColor = color;
}
