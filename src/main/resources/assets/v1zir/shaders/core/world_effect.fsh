#version 150

layout(std140) uniform WorldParams {
	mat4 WorldProj;
	mat4 WorldView;
	vec4 WorldTime;
};

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

// UV0 selects the falloff of every effect quad:
//   u > 2                    solid quad, no falloff
//   v in [2, 4], u in [-1,1] camera facing orb, radial falloff around (0, 3)
//   otherwise                glowing ribbon, falloff across u in [-1, 1]
void main() {
	float a;
	if (texCoord0.x > 2.0) {
		a = 1.0;
	} else if (texCoord0.y >= 1.5) {
		float r = length(vec2(texCoord0.x, texCoord0.y - 3.0));
		a = exp(-r * r * 5.0) * 0.55 + exp(-r * r * 28.0) * 0.9;
		a *= 1.0 - smoothstep(0.8, 1.0, r);
	} else {
		float x = abs(texCoord0.x);
		a = exp(-x * x * 18.0) + exp(-x * x * 3.0) * 0.45;
		a *= 1.0 - smoothstep(0.9, 1.0, x);
	}
	if (a <= 0.003) discard;
	fragColor = vec4(vertexColor.rgb, vertexColor.a * min(a, 1.0));
}
