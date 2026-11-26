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

const vec3 LUMA = vec3(0.2126, 0.7152, 0.0722);

// Outward normal (GUI space, y down) of a rounded box of half size b and corner radius r at p: smooth along the
// sides and round the corners as long as p is no deeper inside than r.
vec2 roundBoxNormal(vec2 p, vec2 b, float r) {
	vec2 q = abs(p) - b + r;
	vec2 n = q.x > 0.0 && q.y > 0.0 ? normalize(q) : (q.x > q.y ? vec2(1.0, 0.0) : vec2(0.0, 1.0));
	return n * vec2(p.x < 0.0 ? -1.0 : 1.0, p.y < 0.0 ? -1.0 : 1.0);
}

void main() {
	float d = v1zirShapeDistance(localPos, shape, style.z);
	float coverage = v1zirShapeCoverage(d, shape, style);
	if (coverage <= 0.0) discard;
	int flags = int(style.y);
	bool clear = (flags & V1ZIR_CLEAR) != 0;
	bool frosted = (flags & V1ZIR_FROSTED) != 0;
	vec3 col;
	if (clear || frosted) {
		// A pane of glass with a rounded edge. The edge's profile follows its own outline, rounded at the corners at
		// least as much as it is wide, so it bends evenly round them instead of creasing along their diagonals.
		vec2 hs = shape.xy;
		float side = min(hs.x, hs.y);
		float bevel = clamp(side * 0.8, 2.0, clear ? 9.0 : 7.0);
		float corner = min(max(min(shape.z, side), bevel), side);
		float t = clamp(1.0 + v1zirRoundBox(localPos, hs, corner) / bevel, 0.0, 1.0);
		float bend = t * t * t;
		vec2 n = roundBoxNormal(localPos, hs, corner);
		// The rounded edge bends the light like a lens: the world is drawn in from further inside, more towards the rim.
		vec2 shift = vec2(-n.x, n.y) * bend * bevel * (clear ? 0.6 : 0.4) * GuiTime.y;
		vec2 px = ScreenInfo.zw;
		vec3 c;
		if (clear) {
			// Clear glass (Sampler0: the frame behind, sharp), its colours split a very little where it bends.
			c.r = texture(Sampler0, (gl_FragCoord.xy + shift) * px).r;
			c.g = texture(Sampler0, (gl_FragCoord.xy + shift * 1.04) * px).g;
			c.b = texture(Sampler0, (gl_FragCoord.xy + shift * 1.08) * px).b;
			c = mix(c, vertexColor.rgb, vertexColor.a);
		} else {
			// Frosted glass (Sampler0: the blurred world): the frost diffuses the world behind, mutes it a little and
			// holds its bright parts down, so what is on the pane stays readable; a thin tint and a milky haze over it,
			// and the fine grain of the frost, fixed to the pane.
			c = texture(Sampler0, (gl_FragCoord.xy + shift) * px).rgb;
			float l = dot(c, LUMA);
			c = mix(vec3(l), c, 0.85) * (1.0 - 0.3 * smoothstep(0.5, 1.0, l));
			c = mix(c, vertexColor.rgb, vertexColor.a);
			c = mix(c, vec3(0.86, 0.89, 0.93), 0.07);
			c += v1zirGrain((localPos + hs + 4.0) * GuiTime.y) * 0.01;
		}
		// The edge reflects more than it lets through, catches the light where it faces it (the top left) and is in
		// its own shadow across from it, as the edge of a thick pane is.
		float facing = dot(n, vec2(-0.6, -0.8));
		float rim = smoothstep(0.3, 1.0, t);
		c = mix(c, vec3(1.0), bend * (clear ? 0.1 : 0.06));
		c += rim * pow(max(facing, 0.0), 3.0) * (clear ? 0.4 : 0.28);
		c *= 1.0 - rim * pow(max(-facing, 0.0), 3.0) * 0.12;
		// The thin bright line of the very edge.
		c += (1.0 - smoothstep(0.0, 1.0, -d)) * 0.08;
		col = c;
	} else {
		// Black glass (Sampler0: the blurred world): the blur, a little desaturated and with bright skies pulled down,
		// under the tint. No grain and no veil: the surface is clean.
		vec3 backdrop = texture(Sampler0, gl_FragCoord.xy * ScreenInfo.zw).rgb;
		float l = dot(backdrop, LUMA);
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
