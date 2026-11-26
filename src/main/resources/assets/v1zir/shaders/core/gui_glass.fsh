#version 150

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <v1zir:v1zir_gui.glsl>
#moj_import <v1zir:sdf_shapes.glsl>

uniform sampler2D Sampler0;

in vec4 vertexColor;
in vec2 localPos;
in vec4 shape;
in vec3 style;

out vec4 fragColor;

void main() {
	float d = v1zirShapeDistance(localPos, shape, style.z);
	// Which way the edge faces on screen (y up), taken before anything is discarded: derivatives need the whole quad.
	vec2 grad = vec2(dFdx(d), dFdy(d));
	float coverage = v1zirShapeCoverage(d, shape, style);
	if (coverage <= 0.0) discard;
	int flags = int(style.y);
	vec3 col;
	if ((flags & V1ZIR_FROST) != 0) {
		// Clear glass (Sampler0: the frame behind the GUI, sharp). Its middle is flat and lets the world through as it
		// is; along the edge a rounded bevel bends the light like a lens, drawing the world in from further inside and
		// splitting its colours a little, as thick glass does, and reflects more than it lets through there.
		float bevel = clamp(min(shape.x, shape.y) * 0.9, 2.5, 12.0);
		// 0 where the flat middle begins, 1 at the rim.
		float t = clamp(1.0 + d / bevel, 0.0, 1.0);
		float bend = t * t * t;
		vec2 n = dot(grad, grad) > 1e-12 ? normalize(grad) : vec2(0.0);
		vec2 shift = -n * bend * bevel * 0.85 * GuiTime.y;
		vec2 px = ScreenInfo.zw;
		col.r = texture(Sampler0, (gl_FragCoord.xy + shift) * px).r;
		col.g = texture(Sampler0, (gl_FragCoord.xy + shift * 1.12) * px).g;
		col.b = texture(Sampler0, (gl_FragCoord.xy + shift * 1.25) * px).b;
		// The little the glass takes from the light (Panel opacity), and the steep bevel's reflection.
		col = mix(col, vertexColor.rgb, vertexColor.a);
		col = mix(col, vec3(1.0), bend * 0.12);
		// The rim catches the light: bright where it faces it (the top left), faint across from it.
		float rim = smoothstep(0.35, 1.0, t);
		float facing = dot(n, vec2(-0.6, 0.8));
		col += rim * (pow(max(facing, 0.0), 2.0) * 0.5 + pow(max(-facing, 0.0), 2.0) * 0.16);
		// The thin bright line of the pane's very edge.
		col += (1.0 - smoothstep(0.0, 1.2, -d)) * 0.12;
	} else {
		// Black glass (Sampler0: the blurred world): the blur, a little desaturated and with bright skies pulled down,
		// under the tint. No grain and no veil: the surface is clean.
		vec3 backdrop = texture(Sampler0, gl_FragCoord.xy * ScreenInfo.zw).rgb;
		float l = dot(backdrop, vec3(0.2126, 0.7152, 0.0722));
		backdrop = mix(vec3(l), backdrop, 0.8) * (1.0 - 0.35 * smoothstep(0.45, 1.0, l));
		col = mix(backdrop, vertexColor.rgb, vertexColor.a);
		// Its inner edge catches a faint light, brighter at the top.
		if ((flags & V1ZIR_MATTE) != 0) {
			float v = clamp((localPos.y + shape.y) / max(2.0 * shape.y, 1.0), 0.0, 1.0);
			float inner = 1.0 - smoothstep(0.5, 2.5, -d);
			col += inner * mix(0.035, 0.0, v);
		}
	}
	if ((flags & V1ZIR_LIT) != 0) col += v1zirCursorLight(gl_FragCoord.xy, 150.0) * 0.04;
	// Half a step of dither against banding: below what the eye can see as grain.
	col += v1zirGrain(gl_FragCoord.xy) * (1.0 / 255.0);
	// Vertex alpha is the tint strength; the fade of the whole panel arrives in the thickness channel.
	float fade = clamp(style.x / 16.0, 0.0, 1.0);
	fragColor = vec4(col, coverage * fade) * ColorModulator;
}
