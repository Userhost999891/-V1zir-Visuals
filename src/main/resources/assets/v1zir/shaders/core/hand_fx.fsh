#version 150

// The first-person hand as Hand Chams shows it (render/HandFx). The arm and the held items were drawn into a texture of
// their own (premultiplied colour, clear elsewhere); this pass lays them over the frame with the chosen look:
//   Outline - the hand's own colours, a crisp line around its true silhouette and a soft glow beyond it;
//   Sky     - the hand as a window into a sky preset (stars, ink, plasma...), fixed in the world as the view turns,
//             with some of the hand's own shading kept so its shape still reads, and a rim of light along its edges.
#moj_import <v1zir:sky_params.glsl>
#moj_import <v1zir:sky_lib.glsl>

uniform sampler2D Sampler0; // the hand

layout(std140) uniform HandParams {
	vec4 Screen; // xy = 1 / size, z = aspect ratio, w = time in seconds
	vec4 Look;   // x = look (1 = outline, 2 = sky), y = how much of the sky replaces the hand's colours, z = shading kept, w = edge glow
	vec4 Edge;   // rgb = colour of the outline, the glow and the rim, a = outline width in pixels
	vec4 Lift;   // x = the height the sky seen through the hand is lifted to, y = how much the view's pitch still moves it,
	             // z = strength of the outline's line (the glow and the rim follow Look.w)
};

in vec2 texCoord;

out vec4 fragColor;

const vec3 LUMA = vec3(0.2126, 0.7152, 0.0722);
// Sixteen directions round a circle, turned a little from the axes so no tap lies on a pixel row.
const mat2 TURN = mat2(0.92388, 0.38268, -0.38268, 0.92388);

void main() {
	vec2 uv = texCoord;
	vec4 hand = texture(Sampler0, uv);
	bool sky = Look.x > 1.5;

	// The view ray through this pixel, as the world's sky finds it, lifted so the sky seen through the hand is the sky
	// overhead (the hand is mostly below the horizon). Its derivatives are taken here, before anything branches.
	vec3 rd = vec3(0.0, 1.0, 0.0);
	vec3 rdDx = vec3(0.0);
	vec3 rdDy = vec3(0.0);
	if (sky) {
		vec2 ndc = uv * 2.0 - 1.0;
		vec4 nearPoint = InvViewProj * vec4(ndc, -1.0, 1.0);
		vec4 midPoint = InvViewProj * vec4(ndc, 0.0, 1.0);
		vec3 view = normalize(midPoint.xyz / midPoint.w - nearPoint.xyz / nearPoint.w);
		rd = normalize(vec3(view.x, Lift.x + view.y * Lift.y, view.z));
	}
	rdDx = dFdx(rd);
	rdDy = dFdy(rd);

	// Most of the screen is far from the hand: a few taps as far out as the glow reaches tell, and nothing is drawn.
	float width = max(Edge.a, 1.0);
	float near = hand.a;
	vec2 d = vec2(1.0, 0.0);
	for (int i = 0; i < 8; i++) {
		near = max(near, texture(Sampler0, uv + d * Screen.xy * width * 3.0).a);
		near = max(near, texture(Sampler0, uv + d * Screen.xy * width * 1.5).a);
		d = TURN * TURN * d;
	}
	if (near < 0.002) {
		fragColor = vec4(0.0);
		return;
	}

	// Coverage of the hand around this pixel: the nearest ring gives a crisp outline, the outer one a soft glow, the
	// smallest one tells how close to the inside of the edge the pixel is (for the rim).
	d = vec2(1.0, 0.0);
	float outline = 0.0;
	float glow = 0.0;
	float inner = 1.0;
	for (int i = 0; i < 16; i++) {
		vec2 tap = d * Screen.xy;
		outline = max(outline, texture(Sampler0, uv + tap * width).a);
		glow += texture(Sampler0, uv + tap * width * 3.0).a;
		if ((i & 1) == 0) inner = min(inner, texture(Sampler0, uv + tap * width * 0.75).a);
		d = TURN * d;
	}
	glow /= 16.0;
	float outside = 1.0 - hand.a;
	float rim = hand.a * (1.0 - inner);

	vec3 col = hand.rgb;
	float alpha = hand.a;
	if (sky && hand.a > 0.003) {
		vec3 own = hand.rgb / hand.a;
		float lum = dot(own, LUMA);
		vec3 through = v1zirSkyD(rd, gl_FragCoord.xy, rdDx, rdDy);
		// Some of the hand's own light and shade on the sky, so the arm, the blade and the folds of the skin still read.
		through *= mix(1.0, 0.3 + 1.3 * lum, Look.z);
		// The rim takes the sky's own colour there, brightened, with the edge colour mixed in.
		vec3 rimColor = mix(through * 2.2 + 0.12, Edge.rgb, 0.45);
		through += rimColor * rim * Look.w * 0.9;
		col = mix(own, through, Look.y) * hand.a;
	} else if (!sky) {
		col += Edge.rgb * rim * Look.w * 0.35;
	}
	// The line just outside the silhouette covers what is behind it; the glow beyond is light added on top.
	float line = outline * outside * Lift.z;
	col = col * (1.0 - line) + Edge.rgb * line;
	alpha = alpha + (1.0 - alpha) * line;
	col += Edge.rgb * glow * outside * (1.0 - line) * Look.w * 0.8;
	fragColor = vec4(col, alpha);
}
