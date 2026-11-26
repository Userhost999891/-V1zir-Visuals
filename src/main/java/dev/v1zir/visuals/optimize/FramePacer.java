package dev.v1zir.visuals.optimize;

import dev.v1zir.visuals.V1zir;

import java.util.concurrent.locks.LockSupport;

/**
 * Frame pacing: holds a capped frame rate (Max Framerate, the menus' 60, the background limit) to an even beat. The
 * game waits for the next frame with an event wait whose timer is coarse on most systems (on Windows often a whole
 * 15.6 ms tick), and measures each wait from when the last one ended, so frames come unevenly and the rate drifts below
 * the cap. Here every frame has its deadline on a steady beat; the wait sleeps until shortly before it and spins for
 * the rest, as precisely as the clock allows. How long a short sleep really takes is measured once: where sleeping is
 * too coarse to trust, the game's own wait is kept. Render thread only.
 */
public final class FramePacer {
	/** A frame that ends this many beats late starts the beat afresh instead of rushing to catch up. */
	private static final int RESYNC_BEATS = 2;
	/** Short sleeps that overshoot by more than this (nanoseconds) cannot be paced with: the game's wait is kept. */
	private static final long COARSE = 3_000_000L;

	private static long next;
	private static long period;
	/** How much earlier than the deadline sleeping stops and spinning starts (nanoseconds); -1 until measured. */
	private static long margin = -1L;

	private FramePacer() {
	}

	/** Waits for the next frame at fps frames a second; false when it cannot pace better than the game (nothing waited). */
	public static boolean limit(int fps) {
		if (margin < 0L) measure();
		if (margin == 0L || fps <= 0) return false;
		long beat = 1_000_000_000L / fps;
		long now = System.nanoTime();
		if (beat != period || next == 0L || now - next > beat * RESYNC_BEATS) {
			period = beat;
			next = now;
		}
		next += beat;
		while (true) {
			long left = next - System.nanoTime();
			if (left <= 0L) break;
			if (left > margin) LockSupport.parkNanos(left - margin);
			else Thread.onSpinWait();
		}
		return true;
	}

	/**
	 * Measures how late a one millisecond sleep wakes on this system: the spin starts that much (and a little more) before
	 * each deadline. A system whose sleeps overshoot by milliseconds is left to the game's own wait.
	 */
	private static void measure() {
		long worst = 0L;
		for (int i = 0; i < 6; i++) {
			long start = System.nanoTime();
			LockSupport.parkNanos(1_000_000L);
			long over = System.nanoTime() - start - 1_000_000L;
			if (i > 0) worst = Math.max(worst, over);
		}
		if (worst > COARSE) {
			margin = 0L;
			V1zir.LOGGER.info("Frame pacing stands down: sleeps here wake {} ms late", String.format("%.1f", worst / 1.0e6));
		} else {
			margin = Math.max(300_000L, worst + 250_000L);
			V1zir.LOGGER.info("Frame pacing: sleeps wake {} ms late, spinning the last {} ms of each wait", String.format("%.2f", worst / 1.0e6),
				String.format("%.2f", margin / 1.0e6));
		}
	}
}
