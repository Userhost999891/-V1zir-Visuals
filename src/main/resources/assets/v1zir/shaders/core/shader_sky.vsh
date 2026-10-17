#version 150

#moj_import <v1zir:sky_params.glsl>

in vec3 Position;

out vec3 viewRay;

void main() {
	// Full screen triangle; the ray is rebuilt from the near and far plane so any
	// translation in the view matrix cancels out.
	gl_Position = vec4(Position.xy, 0.0, 1.0);
	vec4 nearPoint = InvViewProj * vec4(Position.xy, -1.0, 1.0);
	vec4 farPoint = InvViewProj * vec4(Position.xy, 1.0, 1.0);
	viewRay = farPoint.xyz / farPoint.w - nearPoint.xyz / nearPoint.w;
}
