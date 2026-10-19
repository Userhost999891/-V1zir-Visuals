#version 150

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 vertexColor;
in vec3 glyphStyle;

out vec4 fragColor;

// Premultiplied output: alpha blended layers write their coverage as alpha, additive (glow)
// layers write zero alpha so they only add light, all within one draw.
void main() {
	float sd = texture(Sampler0, texCoord0).r;
	float edge = 0.5 - glyphStyle.x;
	float w = max(fwidth(sd) * 0.7, 1e-4) + glyphStyle.y;
	float a;
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
	float alpha = vertexColor.a * a;
	if (alpha <= 0.003) discard;
	fragColor = vec4(vertexColor.rgb * alpha, alpha * (1.0 - glyphStyle.z));
}
