#version 150

#moj_import <v1zir:sky_params.glsl>
#moj_import <v1zir:sky_lib.glsl>

in vec3 viewRay;

out vec4 fragColor;

void main() {
	fragColor = vec4(v1zirSky(normalize(viewRay), gl_FragCoord.xy), 1.0);
}
