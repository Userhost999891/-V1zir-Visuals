#version 150

#moj_import <v1zir:wallpaper_params.glsl>

// One direction of the separable gaussian blur of a wallpaper target (its Blur adjustment). Every tap is placed
// between two texels so the bilinear filter weighs both: 16 taps a side reach 32 texels, three sigma of the widest blur.
uniform sampler2D Sampler0;

in vec2 texCoord;

out vec4 fragColor;

void main() {
	float sigma = max(WallInfo.z, 0.5);
	float k = -0.5 / (sigma * sigma);
	int pairs = int(min(ceil(sigma * 1.5), 16.0));
	vec3 sum = texture(Sampler0, texCoord).rgb;
	float total = 1.0;
	for (int i = 1; i <= 16; i++) {
		if (i > pairs) break;
		float o1 = float(2 * i - 1);
		float o2 = float(2 * i);
		float w1 = exp(o1 * o1 * k);
		float w2 = exp(o2 * o2 * k);
		float w = w1 + w2;
		vec2 offset = WallCover.xy * ((o1 * w1 + o2 * w2) / w);
		sum += (texture(Sampler0, texCoord + offset).rgb + texture(Sampler0, texCoord - offset).rgb) * w;
		total += 2.0 * w;
	}
	fragColor = vec4(sum / total, 1.0);
}
