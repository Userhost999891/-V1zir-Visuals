#version 150

// Dual Kawase upsample.
uniform sampler2D Sampler0;

layout(std140) uniform BlurParams {
	vec4 Blur; // xy = source texel size, z = offset
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
	vec2 h = Blur.xy * 0.5 * Blur.z;
	vec4 sum = texture(Sampler0, texCoord + vec2(-h.x * 2.0, 0.0));
	sum += texture(Sampler0, texCoord + vec2(-h.x, h.y)) * 2.0;
	sum += texture(Sampler0, texCoord + vec2(0.0, h.y * 2.0));
	sum += texture(Sampler0, texCoord + vec2(h.x, h.y)) * 2.0;
	sum += texture(Sampler0, texCoord + vec2(h.x * 2.0, 0.0));
	sum += texture(Sampler0, texCoord + vec2(h.x, -h.y)) * 2.0;
	sum += texture(Sampler0, texCoord + vec2(0.0, -h.y * 2.0));
	sum += texture(Sampler0, texCoord + vec2(-h.x, -h.y)) * 2.0;
	fragColor = vec4(sum.rgb / 12.0, 1.0);
}
