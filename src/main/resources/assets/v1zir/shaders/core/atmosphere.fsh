#version 150

// Mist and light shafts over the world (render/PostFx), before bloom and grading, so both glow and are graded with
// the rest: the frame is copied once and this pass reads the copy and the depth buffer.
#moj_import <v1zir:post_params.glsl>

uniform sampler2D Sampler0; // the frame
uniform sampler2D Sampler1; // its depth

out vec4 fragColor;

const vec3 LUMA = vec3(0.2126, 0.7152, 0.0722);

void main() {
	ivec2 pixel = ivec2(gl_FragCoord.xy);
	vec2 uv = gl_FragCoord.xy * Screen.xy;
	vec4 scene = texelFetch(Sampler0, pixel, 0);
	float depth = texelFetch(Sampler1, pixel, 0).r;
	vec3 col = scene.rgb;

	if (Fog.a > 0.0) {
		vec4 h = InvViewProj * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
		vec3 rel = h.xyz / h.w;
		float dist = length(rel);
		vec3 dir = rel / max(dist, 1.0e-4);
		// The sky is as far as the mist reaches: it lies along the horizon and thins upwards.
		dist = depth >= 1.0 ? 260.0 : min(dist, 260.0);
		// Mist whose density falls off with height, summed along the view ray in closed form: thick looking down
		// into a valley, thin looking up.
		float k = clamp(FogShape.x * dir.y * dist, -30.0, 30.0);
		float along = abs(k) > 1.0e-3 ? (1.0 - exp(-k)) / k : 1.0 - 0.5 * k;
		float amount = Fog.a * exp(-FogShape.x * FogShape.y) * dist * along;
		if (FogShape.z > 0.0) {
			// It drifts: slow noise fixed in the world, read part of the way along the ray.
			vec2 at = Camera.xz + dir.xz * min(dist, 48.0) * 0.6;
			float n = pNoise(at * 0.05 + vec2(Screen.w * 0.02, 0.0)) * 0.6 + pNoise(at * 0.13 - vec2(0.0, Screen.w * 0.035)) * 0.4;
			amount *= mix(1.0, 0.45 + 1.1 * n, FogShape.z);
		}
		col = mix(col, Fog.rgb, (1.0 - exp(-amount)) * FogShape.w);
	}

	if (Light.z > 0.001) {
		// Each pixel looks towards the light: how much of the glow around the sun or moon it sees through open sky
		// on the way is the light that reaches it. Whatever stands in the sky cuts the glow into shafts.
		int samples = int(LightColor.a);
		vec2 toLight = Light.xy - uv;
		vec2 stride = toLight * (Light.w / float(samples));
		// Interleaved gradient noise: a fixed offset per pixel hides the steps.
		float jitter = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
		vec2 p = uv + stride * jitter;
		float sum = 0.0;
		float total = 0.0;
		float weight = 1.0;
		for (int i = 0; i < 40; i++) {
			if (i >= samples) break;
			vec2 fromLight = (p - Light.xy) * vec2(Screen.z, 1.0);
			float glow = exp(-dot(fromLight, fromLight) * 7.0);
			bool inside = all(greaterThanEqual(p, vec2(0.0))) && all(lessThanEqual(p, vec2(1.0)));
			// Off screen nothing is known: counted as half open.
			float open = inside ? step(1.0, textureLod(Sampler1, p, 0.0).r) : 0.5;
			sum += weight * glow * open;
			total += weight;
			weight *= 0.97;
			p += stride;
		}
		float shaft = sum / max(total, 1.0e-4);
		vec2 away = toLight * vec2(Screen.z, 1.0);
		float fall = exp(-dot(away, away) * 0.9);
		// Less over what is bright already, so the sky around the sun does not burn out.
		col += LightColor.rgb * (shaft * fall * Light.z * (1.0 - 0.55 * clamp(dot(col, LUMA), 0.0, 1.0)));
	}
	fragColor = vec4(clamp(col, 0.0, 1.0), scene.a);
}
