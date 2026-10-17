// SkyParams uniform block - written by dev.v1zir.visuals.render.sky.SkyUniforms (std140).
layout(std140) uniform SkyParams {
	mat4 InvViewProj;
	vec4 SunDir;
	vec4 MoonDir;
	vec4 Timing;
	vec4 ColorA;
	vec4 ColorB;
	vec4 Settings;
	vec4 Camera;
	vec4 Extra;
};
