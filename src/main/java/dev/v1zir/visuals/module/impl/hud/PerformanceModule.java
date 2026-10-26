package dev.v1zir.visuals.module.impl.hud;

import dev.v1zir.visuals.gui.clickgui.Label;
import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.font.Icons;
import dev.v1zir.visuals.render.font.SdfFont;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.gui.DrawContext;

import java.util.Arrays;

/**
 * FPS card with a frame time graph and the lowest and average FPS of the last seconds. Frames are
 * gathered into short buckets: each bar shows the slowest frame of its bucket, so stutters stand out.
 */
public final class PerformanceModule extends HudModule {
	private static final int BARS = 40;
	private static final long BUCKET = 60_000_000L;
	private static final float W = 112f;
	private static final int SPIKE = 0xFFFF5A5A;

	public final BoolSetting graph = add(new BoolSetting("Graph", true));
	public final BoolSetting stats = add(new BoolSetting("Min / avg", true));

	private final Sparkline frames = new Sparkline(BARS);
	private final float[] worst = new float[BARS];
	private final float[] time = new float[BARS];
	private final int[] count = new int[BARS];
	private int bucket;
	private long bucketStart;
	private long lastFrame;
	private float bucketWorst;
	private float bucketTime;
	private int bucketCount;

	private final NumberText fps = new NumberText(0, "");
	private final NumberText frameTime = new NumberText(1, " ms");
	private final NumberText min = new NumberText(0, "");
	private final NumberText avg = new NumberText(0, "");
	private final Label avgLabel = new Label();

	public PerformanceModule() {
		super("Performance", "FPS with a frame time graph", 0.008f, 0.78f);
		setSubCategory("Info");
		width = W;
		height = 48f;
	}

	@Override
	public boolean showInList() {
		return false;
	}

	@Override
	protected void onEnable() {
		lastFrame = 0L;
		frames.clear();
		Arrays.fill(worst, 0f);
		Arrays.fill(time, 0f);
		Arrays.fill(count, 0);
	}

	@Override
	protected void render(DrawContext ctx, float tickDelta, float x, float y) {
		long now = System.nanoTime();
		sample(now);
		float shift = Math.min(1f, (now - bucketStart) / (float) BUCKET);

		SdfFont bold = Fonts.semibold();
		SdfFont font = Fonts.medium();
		float h = 20f + (graph.on() ? 15f : 0f) + (stats.on() ? 11f : 0f) + 2f;
		width = W;
		height = h;
		panel(ctx, x, y, W, h);
		int accent = Theme.accent();

		// Header row: FPS on the left, the average frame time on the right.
		float row = y + 10f;
		Render2D.icon(ctx, Icons.ACTIVITY, x + 10f, row, 8.5f, accent);
		fps.set(mc.getCurrentFps());
		float cx = x + 19f;
		cx += Render2D.textMiddle(ctx, bold, fps.text(), cx, row, 9f, Theme.text()) + 2.5f;
		Render2D.textMiddle(ctx, font, "fps", cx, row, 6.5f, Theme.textDim());
		Render2D.textMiddle(ctx, font, frameTime.text(), x + W - 7f - frameTime.width(font, 6.5f), row, 6.5f, Theme.textFaint());

		float cy = y + 19f;
		if (graph.on()) {
			float gx = x + 7f;
			float gw = W - 14f;
			float gh = 12f;
			Render2D.round(ctx, gx, cy + gh - 1f, gw, 1f, 0.5f, ColorUtil.withAlpha(0xFFFFFF, 18));
			// Floor of a 30 FPS frame: smooth play keeps the bars low, a stutter shoots up in red.
			frames.draw(ctx, gx, cy, gw, gh, 1000f / 30f, shift, ColorUtil.withAlpha(accent, 200), SPIKE);
			cy += 15f;
		}
		if (stats.on()) {
			float sy = cy + 4f;
			float sx = x + 8f;
			sx += Render2D.textMiddle(ctx, font, "min", sx, sy, 6f, Theme.textFaint()) + 3f;
			Render2D.textMiddle(ctx, bold, min.text(), sx, sy, 6.5f, Theme.textDim());
			float aw = avg.width(bold, 6.5f);
			Render2D.textMiddle(ctx, bold, avg.text(), x + W - 8f - aw, sy, 6.5f, Theme.textDim());
			Render2D.textMiddle(ctx, font, "avg", x + W - 8f - aw - 3f - avgLabel.width(font, "avg", 6f), sy, 6f, Theme.textFaint());
		}
	}

	/** Adds the time since the previous frame to the current bucket and closes buckets as they fill. */
	private void sample(long now) {
		if (lastFrame != 0L) {
			float dt = (now - lastFrame) / 1.0e9f;
			if (dt > 1f) {
				// The HUD was hidden for a while: start over rather than graph the gap.
				bucketStart = now;
				bucketWorst = bucketTime = 0f;
				bucketCount = 0;
			} else {
				bucketWorst = Math.max(bucketWorst, dt);
				bucketTime += dt;
				bucketCount++;
			}
		} else {
			bucketStart = now;
		}
		lastFrame = now;
		if (now - bucketStart < BUCKET) return;
		bucketStart = now - Math.min(now - bucketStart - BUCKET, BUCKET);
		frames.push(bucketWorst * 1000f);
		worst[bucket] = bucketWorst;
		time[bucket] = bucketTime;
		count[bucket] = bucketCount;
		bucket = (bucket + 1) % BARS;
		bucketWorst = bucketTime = 0f;
		bucketCount = 0;

		float slowest = 0f;
		float total = 0f;
		int frameCount = 0;
		for (int i = 0; i < BARS; i++) {
			slowest = Math.max(slowest, worst[i]);
			total += time[i];
			frameCount += count[i];
		}
		if (frameCount == 0 || total <= 0f) return;
		min.set(1f / slowest);
		avg.set(frameCount / total);
		frameTime.set(total * 1000f / frameCount);
	}
}
