#version 150

#moj_import <v1zir:snow_params.glsl>

in vec2 corner;
in float fade;
in float soft;

out vec4 fragColor;

void main() {
	float r = length(corner);
	// A small bright flake with a soft rim; out of focus, a soft round disc.
	float rim = mix(0.25, 0.75, soft);
	float body = 1.0 - smoothstep(1.0 - rim, 1.0, r);
	float core = 1.0 - smoothstep(0.0, 0.55, r);
	// Six faint arms catch the light on the sharp ones.
	float a = atan(corner.y, corner.x);
	float arms = pow(abs(cos(a * 3.0)), 12.0) * (1.0 - smoothstep(0.2, 0.95, r)) * (1.0 - soft);
	float alpha = (body * mix(0.92, 0.5, soft) + arms * 0.25) * fade * SnowLight.a;
	if (alpha < 0.004) discard;
	vec3 color = SnowLight.rgb * (0.86 + 0.22 * core + arms * 0.2);
	fragColor = vec4(color, alpha);
}
