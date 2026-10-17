#version 150

// Dual Kawase downsample.
uniform sampler2D Sampler0;

layout(std140) uniform BlurParams {
	vec4 Blur; // xy = source texel size, z = offset
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
	vec2 h = Blur.xy * 0.5 * Blur.z;
	vec4 sum = texture(Sampler0, texCoord) * 4.0;
	sum += texture(Sampler0, texCoord - h);
	sum += texture(Sampler0, texCoord + h);
	sum += texture(Sampler0, texCoord + vec2(h.x, -h.y));
	sum += texture(Sampler0, texCoord - vec2(h.x, -h.y));
	fragColor = vec4(sum.rgb / 8.0, 1.0);
}
