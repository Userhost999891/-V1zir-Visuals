#version 150

#moj_import <v1zir:wallpaper_params.glsl>

// A picture from the wallpapers folder, rendered into a wallpaper target. WallCover maps the screen uv (origin at
// the top left) to the image filling the screen; WallContain to the whole image fitted inside it. Both carry the
// slow Ken Burns drift. Fitted, the bars around the image show the filled one, darkened and smeared.
uniform sampler2D Sampler0;

in vec2 texCoord;

out vec4 fragColor;

// Box filter over the pixel's footprint (up to 3x3 bilinear taps): a large picture drawn small stays smooth.
vec3 wallSample(vec2 uv) {
	vec2 size = vec2(textureSize(Sampler0, 0));
	vec2 fx = dFdx(uv);
	vec2 fy = dFdy(uv);
	float texels = max(length(fx * size), length(fy * size));
	int n = int(clamp(ceil(texels * 0.5), 1.0, 3.0));
	vec3 sum = vec3(0.0);
	for (int i = 0; i < 3; i++) {
		if (i >= n) break;
		for (int j = 0; j < 3; j++) {
			if (j >= n) break;
			vec2 o = (vec2(float(i), float(j)) + 0.5) / float(n) - 0.5;
			sum += texture(Sampler0, uv + fx * o.x + fy * o.y).rgb;
		}
	}
	return sum / float(n * n);
}

void main() {
	vec2 screen = vec2(texCoord.x, 1.0 - texCoord.y);
	vec2 cover = screen * WallCover.xy + WallCover.zw;
	if (WallInfo.w < 0.5) {
		fragColor = vec4(wallSample(cover), 1.0);
		return;
	}
	vec2 contain = screen * WallContain.xy + WallContain.zw;
	vec2 edge = min(contain, 1.0 - contain) * WallView.zw / max(WallContain.xy, vec2(1e-4));
	float inside = clamp(min(edge.x, edge.y) + 0.5, 0.0, 1.0);
	vec3 col = vec3(0.0);
	if (inside < 1.0) {
		// Eight wide taps of the filled picture: a cheap, very soft backdrop for the bars.
		vec3 bars = vec3(0.0);
		for (int i = 0; i < 8; i++) {
			float a = float(i) * 0.785398;
			bars += texture(Sampler0, cover + vec2(cos(a), sin(a)) * 0.035).rgb;
		}
		col = bars * (0.3 / 8.0);
	}
	if (inside > 0.0) col = mix(col, wallSample(clamp(contain, 0.0, 1.0)), inside);
	fragColor = vec4(col, 1.0);
}
