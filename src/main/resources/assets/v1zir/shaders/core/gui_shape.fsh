#version 150

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <v1zir:v1zir_gui.glsl>
#moj_import <v1zir:sdf_shapes.glsl>

in vec4 vertexColor;
in vec2 localPos;
in vec4 shape;
in vec3 style;

out vec4 fragColor;

// Arc (mode 3): a ring of thickness style.x inside the shape's circle, swept clockwise from twelve o'clock over
// shape.w degrees, with round caps.
float arcDistance(vec2 p, vec4 shape, float thickness) {
	float aperture = radians(clamp(shape.w, 0.0, 360.0)) * 0.5;
	vec2 q = vec2(p.x, -p.y);
	q = vec2(q.x * cos(aperture) - q.y * sin(aperture), q.x * sin(aperture) + q.y * cos(aperture));
	q.x = abs(q.x);
	vec2 sc = vec2(sin(aperture), cos(aperture));
	float ra = shape.x - thickness * 0.5;
	return (sc.y * q.x > sc.x * q.y ? length(q - sc * ra) : abs(length(q) - ra)) - thickness * 0.5;
}

void main() {
	float d;
	float coverage;
	if ((int(style.y) & 7) == 3) {
		d = arcDistance(localPos, shape, style.x);
		coverage = clamp(0.5 - d / max(fwidth(d), 1e-4), 0.0, 1.0);
	} else {
		d = v1zirShapeDistance(localPos, shape, style.z);
		coverage = v1zirShapeCoverage(d, shape, style);
	}
	if (coverage <= 0.0) discard;
	vec4 color = vertexColor;
	int flags = int(style.y);
	if ((flags & V1ZIR_LIT) != 0) {
		// The cursor carries a soft light: rims catch most of it, surfaces brighten a little.
		float light = v1zirCursorLight(gl_FragCoord.xy, 120.0);
		if ((flags & 7) == 1) {
			color = vec4(mix(color.rgb, vec3(1.0), light * 0.5), color.a + (1.0 - color.a) * light * 0.3);
		} else {
			color = vec4(color.rgb + light * 0.07, color.a + (1.0 - color.a) * light * 0.05);
		}
	}
	if ((flags & V1ZIR_MATTE) != 0) color.rgb += v1zirGrain(gl_FragCoord.xy) * 0.03;
	fragColor = vec4(color.rgb, color.a * coverage) * ColorModulator;
}
