#version 150

#moj_import <v1zir:sky_params.glsl>

in vec3 Position;

out vec3 viewRay;

void main() {
	// Full screen triangle; the ray is rebuilt from two unprojected points so any translation
	// in the view matrix cancels out. The second point is at NDC z = 0, not on the far plane:
	// there w = 1/far is the difference of two numbers near 10, and float rounding bent every
	// ray by several pixels, differently on every camera change, so the sky jittered.
	gl_Position = vec4(Position.xy, 0.0, 1.0);
	vec4 nearPoint = InvViewProj * vec4(Position.xy, -1.0, 1.0);
	vec4 midPoint = InvViewProj * vec4(Position.xy, 0.0, 1.0);
	viewRay = midPoint.xyz / midPoint.w - nearPoint.xyz / nearPoint.w;
}
