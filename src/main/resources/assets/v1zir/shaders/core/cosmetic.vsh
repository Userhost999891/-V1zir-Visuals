#version 150

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <v1zir:knife_world.glsl>

uniform sampler2D Sampler2;

// Entity vertex format; UV1 carries the material (x) and the animation time in 1/50 s (y).
in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

out vec3 viewPos;
out vec3 viewNormal;
out vec2 pattern;
out vec4 tint;
flat out int material;
flat out float time;
out vec3 worldLight;
out float shade;
out float fog;

void main() {
	vec4 view = ModelViewMat * vec4(Position, 1.0);
	gl_Position = ProjMat * view;
	viewPos = view.xyz;
	viewNormal = mat3(ModelViewMat) * Normal;
	pattern = UV0;
	tint = Color;
	material = UV1.x;
	time = float(UV1.y) / 50.0;
	// Lit like the wearer: the light map and vanilla's two directional lights.
	worldLight = texelFetch(Sampler2, UV2 / 16, 0).rgb;
	shade = min(1.0, (max(0.0, dot(Light0_Direction, Normal)) + max(0.0, dot(Light1_Direction, Normal))) * 0.6 + 0.4);
	fog = knifeFog(Position);
}
