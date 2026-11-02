#version 150

// Motion blur of the world (render/MotionBlur): the frame is smeared along the camera's motion on the
// screen and blended with the previous result, so turning the view leaves soft trails.
uniform sampler2D Sampler0; // this frame
uniform sampler2D Sampler1; // the previous result

layout(std140) uniform MotionParams {
	vec4 Motion; // xy = the camera's motion on the screen this frame (uv), z = weight of the previous result
};

in vec2 texCoord;

out vec4 fragColor;

const int TAPS = 6;

void main() {
	vec3 sum = vec3(0.0);
	for (int i = 0; i < TAPS; i++) {
		float t = float(i) / float(TAPS - 1) - 0.5;
		sum += texture(Sampler0, texCoord + Motion.xy * t).rgb;
	}
	vec3 history = texture(Sampler1, texCoord).rgb;
	fragColor = vec4(mix(sum / float(TAPS), history, Motion.z), 1.0);
}
