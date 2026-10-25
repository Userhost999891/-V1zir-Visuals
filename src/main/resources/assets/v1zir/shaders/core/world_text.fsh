#version 150

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 vertexColor;
in vec3 glyphStyle;
in vec4 plateShape;

out vec4 fragColor;

// Rounded rectangle of half size (aspect, 1) and corner radius r, in half heights.
float plateDistance(vec2 p, float aspect, float r) {
	vec2 q = abs(p) - vec2(aspect, 1.0) + r;
	return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

// Premultiplied output: alpha blended layers write their coverage as alpha, additive (glow)
// layers write zero alpha so they only add light, all within one draw.
void main() {
	float a;
	if (plateShape.w > 0.5) {
		float d = plateDistance(texCoord0, plateShape.x, plateShape.y);
		if (plateShape.z > 0.0) {
			// Soft shadow or glow, fading out over the softness.
			a = 1.0 - smoothstep(-0.35 * plateShape.z, plateShape.z, d);
			a *= a * (3.0 - 2.0 * a);
		} else {
			a = clamp(0.5 - d / max(fwidth(d), 1e-4), 0.0, 1.0);
		}
	} else {
		float sd = texture(Sampler0, texCoord0).r;
		float edge = 0.5 - glyphStyle.x;
		float w = max(fwidth(sd) * 0.7, 1e-4) + glyphStyle.y;
		if (glyphStyle.y > 0.02) {
			// Soft layers (glow, shadow) have no edge to alias.
			a = smoothstep(edge - w, edge + w, sd);
		} else {
			// Crisp layers supersample a small neighbourhood so small or distant text stays sharp.
			vec2 dx = dFdx(texCoord0) * 0.25;
			vec2 dy = dFdy(texCoord0) * 0.25;
			a = smoothstep(edge - w, edge + w, sd) * 0.5;
			a += smoothstep(edge - w, edge + w, texture(Sampler0, texCoord0 + dx + dy).r) * 0.125;
			a += smoothstep(edge - w, edge + w, texture(Sampler0, texCoord0 - dx - dy).r) * 0.125;
			a += smoothstep(edge - w, edge + w, texture(Sampler0, texCoord0 + dx - dy).r) * 0.125;
			a += smoothstep(edge - w, edge + w, texture(Sampler0, texCoord0 - dx + dy).r) * 0.125;
		}
	}
	float alpha = vertexColor.a * a;
	if (alpha <= 0.003) discard;
	fragColor = vec4(vertexColor.rgb * alpha, alpha * (1.0 - glyphStyle.z));
}
