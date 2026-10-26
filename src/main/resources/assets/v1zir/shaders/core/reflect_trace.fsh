#version 150

// Trace pass of the reflections (render/Reflections), usually at half resolution: the reflected
// colour of every pixel that reflects, alpha 1 there and 0 everywhere else.
#moj_import <v1zir:reflect.glsl>

out vec4 fragColor;

void main() {
	// The full-resolution pixel this texel stands for.
	ivec2 p = ivec2(gl_FragCoord.xy * Screen.xy / Screen.zw);
	SurfaceInfo s;
	if (!rSurface(p, s) || max(s.tint.r, max(s.tint.g, s.tint.b)) < 0.002) {
		fragColor = vec4(0.0);
		return;
	}
	vec3 view = normalize(s.pos);
	// Interleaved gradient noise: a fixed per-pixel offset that hides the march steps.
	float noise = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
	// Roughness is a blur in the composite, not jittered rays: no grain.
	vec3 dir = reflect(view, s.normal);
	// Under a roof a ray that finds nothing sees the dim room rather than the sky; sideways rays
	// still catch some of the horizon (under trees, at the edge of an overhang).
	vec3 miss = mix(texelFetch(Sampler0, p, 0).rgb * 0.3, rSky(dir), max(s.exposure, 0.4 - 0.4 * abs(dir.y)));
	vec3 hit = rMarch(s.pos + s.normal * (0.02 + length(s.pos) * 0.002), dir, noise);
	// Rays turning back towards the camera mostly meet what is behind it.
	float confidence = hit.z * smoothstep(-0.9, -0.4, dot(dir, Forward.xyz));
	fragColor = vec4(confidence > 0.0 ? mix(miss, texture(Sampler0, hit.xy).rgb, confidence) : miss, 1.0);
}
