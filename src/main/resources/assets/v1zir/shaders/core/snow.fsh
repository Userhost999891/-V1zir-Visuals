#version 150

#moj_import <v1zir:snow_params.glsl>

in vec2 corner;
in float fade;
in float soft;
in vec4 seed4;
in vec3 sunView;

out vec4 fragColor;

void main() {
	// A fluffy clump of crystals: a few soft lobes round its middle, each its own size and place, so its edge is uneven
	// and soft; out of focus close by it melts into a round blur.
	float edge = mix(0.42, 0.05, soft);
	float body = 1.0 - smoothstep(edge, 1.0, length(corner) / 0.6);
	for (int i = 0; i < 4; i++) {
		float k = float(i);
		float a = seed4.g * 6.2831853 + k * 1.7 + seed4.a * 3.0;
		vec2 o = vec2(cos(a), sin(a)) * (0.2 + 0.14 * fract(seed4.b * 7.0 + k * 0.37)) * (1.0 - soft * 0.6);
		float r = mix(0.42, 0.62, fract(seed4.r * 5.3 + k * 0.61));
		body = max(body, 1.0 - smoothstep(edge, 1.0, length(corner - o) / r));
	}
	float alpha = body * mix(0.95, 0.55, soft) * fade * SnowLight.a;
	if (alpha < 0.004) discard;
	// Lit as a small ball of snow: brighter on the side towards the sun or the moon, and glowing round its rim when
	// the light is behind it.
	vec2 q = corner * 0.9;
	vec3 normal = vec3(q, sqrt(max(1.0 - dot(q, q), 0.0)));
	// Never darker than the snow on the ground: white on white, as falling snow is.
	float lit = 0.96 + 0.16 * max(dot(normal, sunView), 0.0) * SnowSun.w - 0.04 * (1.0 - normal.z);
	float rim = pow(1.0 - normal.z, 2.0) * max(-sunView.z, 0.0) * 0.4 * SnowSun.w;
	fragColor = vec4(SnowLight.rgb * (lit + rim), alpha);
}
