#version 150

#moj_import <v1zir:transition.glsl>

// One vertex of a glass shard of the shattering click GUI (WindowTransition / ShardMesh).
// Position.xy = where it sat in the window, Position.z = 1 at the shard's centre and 0 on its rim,
// UV0 = the shard's centre, Color = four random values of the shard.
in vec3 Position;
in vec2 UV0;
in vec4 Color;

out vec2 uv;
out vec2 origin;
out float edge;
out float shade;
out float glint;
out float opacity;

vec3 rotate(vec3 v, vec3 axis, float angle) {
	float c = cos(angle);
	float s = sin(angle);
	return v * c + cross(axis, v) * s + axis * dot(axis, v) * (1.0 - c);
}

void main() {
	vec2 center = UV0;
	float size = max(Screen.x, Screen.y);
	// Shards near the impact let go first; every shard waits until the cracks have spread.
	float delay = Origin.w + length(center - Origin.xy) / size * 0.35 + Color.a * 0.06;
	float t = max(Anim.x - delay, 0.0);

	vec2 away = center - Origin.xy;
	away = away / max(length(away), 1.0);
	float speed = 0.06 + 0.16 * Color.b;
	vec3 velocity = vec3(away * speed * size, -(0.05 + 0.14 * Color.g) * size);
	velocity.y -= 0.12 * size * (1.0 - Color.b);
	vec3 offset = velocity * t + vec3(0.0, 0.5 * Origin.z * t * t, 0.0);

	float angle = Color.r * 6.2831853;
	vec3 axis = normalize(vec3(cos(angle), sin(angle), (Color.g - 0.5) * 0.8));
	float spin = (Color.g * 2.0 - 1.0) * (3.0 + 5.0 * Color.b) * t;
	vec3 local = rotate(vec3(Position.xy - center, 0.0), axis, spin);
	vec3 world = vec3(center, 0.0) + local + offset;

	// Perspective from in front of the screen centre; w carries the depth so the texture stays perspective-correct.
	vec2 s = Screen.xy * 0.5;
	float w = max((Shape.y + world.z) / Shape.y, 0.2);
	vec2 screen = s + (world.xy - s) / w;
	gl_Position = vec4((screen.x * Screen.z * 2.0 - 1.0) * w, (1.0 - screen.y * Screen.w * 2.0) * w, 0.0, w);

	vec3 normal = rotate(vec3(0.0, 0.0, -1.0), axis, spin);
	vec3 light = normalize(vec3(-0.35, -0.6, -1.0));
	vec3 reflected = reflect(-light, normal);
	glint = pow(max(dot(reflected, vec3(0.0, 0.0, -1.0)), 0.0), 28.0) * step(0.0001, t);
	shade = 0.72 + 0.28 * abs(normal.z);
	opacity = 1.0 - smoothstep(0.45, 1.0, t);
	uv = transitionUv(Position.xy);
	origin = Position.xy;
	edge = Position.z;
}
