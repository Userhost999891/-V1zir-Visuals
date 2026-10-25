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
//   v < -1.5                 face of a rounded box: glowing rounded border (see Draw3D.roundedBox)
//   u > 2                    solid quad, no falloff
//   v in [2, 4], u in [-1,1] camera facing orb, radial falloff around (0, 3)
//   otherwise                glowing ribbon, falloff across u in [-1, 1]
void main() {
	float a;
	if (texCoord0.y < -1.5) {
		// UV0 = (16 qu + px, py - 16 qv - 8): position p on the face around its centre and the
		// half size h = q / 64, packed so that interpolation keeps both exact.
		vec2 q = floor(vec2(texCoord0.x, -texCoord0.y - 8.0) / 16.0 + 0.5);
		vec2 p = vec2(texCoord0.x - 16.0 * q.x, texCoord0.y + 16.0 * q.y + 8.0);
		vec2 h = q / 64.0;
		float r = min(0.12, 0.35 * min(h.x, h.y));
		vec2 e = abs(p) - h + r;
		float d = length(max(e, 0.0)) + min(max(e.x, e.y), 0.0) - r;
		// Distance inside the border in pixels: a crisp line and a soft glow of constant screen size,
		// antialiased outwards so the face corners stay round.
		float px = -d / max(fwidth(d), 1e-6);
		a = (1.0 - smoothstep(1.0, 2.4, px) + exp(-max(px, 0.0) * 0.16) * 0.5) * clamp(px + 1.0, 0.0, 1.0);
	} else if (texCoord0.x > 2.0) {
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
