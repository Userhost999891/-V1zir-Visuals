#version 150

#moj_import <v1zir:cosmetic_preview.glsl>
#moj_import <v1zir:cosmetic.glsl>

in vec3 viewPos;
in vec3 viewNormal;
in vec2 pattern;
in vec4 tint;
flat in int material;

out vec4 fragColor;

void main() {
	vec3 n = normalize(viewNormal);
	// Two fixed lights stand in for the world's, vanilla's directional term from them.
	float shade = min(1.0, (max(0.0, dot(PreviewLight0.xyz, n)) + max(0.0, dot(PreviewLight1.xyz, n))) * 0.6 + 0.4);
	vec4 color = cosmeticShade(material, pattern, tint, viewPos, viewNormal, vec3(PreviewParams.y), shade, PreviewParams.x);
	color.rgb = mix(color.rgb, PreviewFogColor.rgb, PreviewParams.z);
	// Premultiplied, over a transparent target (previews) or blended into the world (pets).
	fragColor = vec4(color.rgb * color.a, color.a);
}
