package dev.v1zir.visuals.optimize;

import dev.v1zir.visuals.V1zir;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GLCapabilities;

/**
 * How long the graphics card spends on the client's own passes: the sky, reflections and wet surfaces, the world's
 * effects, the post effects, the blur behind glass panels, the hand's looks and the whole GUI. The card runs behind the
 * game by a frame or two, so the time a part takes there never shows in the game's own timing (HitchMonitor sees only
 * the processor's side): a timestamp is taken on the card before and after each part, and read a few frames later,
 * when it is surely done, so the game never waits for the card to answer. Each part keeps a smoothed time per frame.
 * <p>
 * Timestamps do not nest or clash with the game's own timer queries. Render thread only; nothing at all where the
 * driver has no timer queries (OpenGL 3.3).
 */
public final class GpuTimer {
	public static final int SKY = 0;
	public static final int REFLECTIONS = 1;
	public static final int WORLD = 2;
	public static final int POST = 3;
	public static final int BLUR = 4;
	public static final int HAND = 5;
	public static final int GUI = 6;
	private static final String[] NAMES = {"Shader Sky", "reflections", "world effects", "post effects", "glass blur", "Hand Chams", "GUI"};
	private static final int PARTS = NAMES.length;
	/** Frames in flight: a frame's timestamps are read when it comes round again. */
	private static final int FRAMES = 4;
	/** Timestamp pairs per frame (a part may run more than once in a frame: its times add up). */
	private static final int PAIRS = 24;
	private static final float SMOOTHING = 0.08f;

	private static int[] begins;
	private static int[] ends;
	private static final int[] partOf = new int[FRAMES * PAIRS];
	private static final int[] used = new int[FRAMES];
	private static final int[] open = new int[PARTS];
	private static final float[] ms = new float[PARTS];
	private static final float[] frame = new float[PARTS];
	private static int slot;
	/** 0: not checked yet, 1: timer queries work, -1: they do not (or failed): nothing is measured. */
	private static int state;

	private GpuTimer() {
	}

	private static boolean ready() {
		if (state == 0) {
			try {
				GLCapabilities caps = GL.getCapabilities();
				if (caps.OpenGL33 || caps.GL_ARB_timer_query) {
					begins = new int[FRAMES * PAIRS];
					ends = new int[FRAMES * PAIRS];
					GL15.glGenQueries(begins);
					GL15.glGenQueries(ends);
					for (int i = 0; i < PARTS; i++) open[i] = -1;
					state = 1;
				} else {
					state = -1;
				}
			} catch (RuntimeException | LinkageError e) {
				V1zir.LOGGER.debug("No GPU timer queries", e);
				state = -1;
			}
		}
		return state == 1;
	}

	/** The card's time before the part. */
	public static void begin(int part) {
		if (!ready() || open[part] >= 0 || used[slot] >= PAIRS) return;
		int pair = slot * PAIRS + used[slot]++;
		partOf[pair] = part;
		open[part] = pair;
		GL33.glQueryCounter(begins[pair], GL33.GL_TIMESTAMP);
	}

	/** The card's time after the part. */
	public static void end(int part) {
		if (state != 1 || open[part] < 0) return;
		GL33.glQueryCounter(ends[open[part]], GL33.GL_TIMESTAMP);
		open[part] = -1;
	}

	/** Once per finished frame: reads the oldest frame's times (they are done by now) and starts the next. */
	public static void endFrame() {
		if (state != 1) return;
		for (int i = 0; i < PARTS; i++) {
			// A part left open (an exception between begin and end) is closed here so its pair is still whole.
			if (open[i] >= 0) end(i);
		}
		slot = (slot + 1) % FRAMES;
		int count = used[slot];
		if (count == 0) {
			decay();
			return;
		}
		for (int i = 0; i < PARTS; i++) frame[i] = 0f;
		boolean complete = true;
		for (int p = slot * PAIRS; p < slot * PAIRS + count; p++) {
			if (GL15.glGetQueryObjecti(ends[p], GL15.GL_QUERY_RESULT_AVAILABLE) == 0) {
				// Not done yet (a very slow card): this frame's times are left out rather than waited for.
				complete = false;
				break;
			}
			long nanos = GL33.glGetQueryObjecti64(ends[p], GL15.GL_QUERY_RESULT) - GL33.glGetQueryObjecti64(begins[p], GL15.GL_QUERY_RESULT);
			if (nanos > 0L && nanos < 1_000_000_000L) frame[partOf[p]] += nanos / 1.0e6f;
		}
		used[slot] = 0;
		if (!complete) return;
		for (int i = 0; i < PARTS; i++) ms[i] += (frame[i] - ms[i]) * SMOOTHING;
	}

	/** Frames without any part: their times fade out. */
	private static void decay() {
		for (int i = 0; i < PARTS; i++) ms[i] -= ms[i] * SMOOTHING;
	}

	/** Whether the card's times are measured here. */
	public static boolean measured() {
		return state == 1;
	}

	/** The smoothed time of a part per frame, in milliseconds. */
	public static float ms(int part) {
		return ms[part];
	}

	/** The client's own passes per frame, the GUI left out (the game's own GUI is in it), in milliseconds. */
	public static float effects() {
		float total = 0f;
		for (int i = 0; i < PARTS; i++) if (i != GUI) total += ms[i];
		return total;
	}

	/** The costliest of the client's own passes (the GUI left out), -1 when none takes any time. */
	public static int costliest() {
		int best = -1;
		for (int i = 0; i < PARTS; i++) {
			if (i != GUI && ms[i] > 0.05f && (best < 0 || ms[i] > ms[best])) best = i;
		}
		return best;
	}

	public static String name(int part) {
		return NAMES[part];
	}
}
