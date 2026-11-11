// PostParams uniform block of the atmosphere and lens passes - written by dev.v1zir.visuals.render.PostFx (std140).
layout(std140) uniform PostParams {
	mat4 InvViewProj; // clip space to camera-relative world space
	vec4 Screen;      // xy = 1 / size, z = aspect ratio, w = time in seconds
	vec4 Light;       // xy = the sun or moon on screen (0..1, may lie outside), z = strength of the shafts, w = their reach (0..1)
	vec4 LightColor;  // rgb = colour of the shafts, a = samples along each
	vec4 Fog;         // rgb = colour of the mist, a = its density at its base (0 = none)
	vec4 FogShape;    // x = 1 / the height it thins over, y = camera height above its base, z = drift (0..1), w = most of the view it may hide
	vec4 Camera;      // xyz = camera position (x and z modulo 4096, for noise that stays put in the world)
	vec4 Lens;        // x = colour fringes, y = film grain, z = letterbox bars (share of the height each), w = vignette
};

float pHash(vec2 p) {
	vec3 p3 = fract(vec3(p.xyx) * 0.1031);
	p3 += dot(p3, p3.yzx + 33.33);
	return fract((p3.x + p3.y) * p3.z);
}

float pNoise(vec2 p) {
	vec2 i = floor(p);
	vec2 f = p - i;
	f = f * f * (3.0 - 2.0 * f);
	return mix(mix(pHash(i), pHash(i + vec2(1.0, 0.0)), f.x), mix(pHash(i + vec2(0.0, 1.0)), pHash(i + 1.0), f.x), f.y);
}
