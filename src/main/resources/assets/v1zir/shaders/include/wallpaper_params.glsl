// WallpaperParams uniform block - written by dev.v1zir.visuals.render.wallpaper.WallpaperRenderer (std140).
layout(std140) uniform WallpaperParams {
	// x = seconds, y = aspect (width / height), z = wallpaper index (or the blur sigma in texels), w = image fit (1 = contain)
	vec4 WallInfo;
	// xy = mouse parallax -1..1, zw = target size in pixels
	vec4 WallView;
	// image: cover-fit transform of the screen uv (scale xy, offset zw); blur: one texel along the pass direction (xy)
	vec4 WallCover;
	// image: contain-fit transform of the screen uv (scale xy, offset zw)
	vec4 WallContain;
};
