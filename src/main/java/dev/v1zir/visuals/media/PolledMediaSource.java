package dev.v1zir.visuals.media;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Backend that asks a command line tool for the player state once a second (positions in between
 * are interpolated by {@link NowPlaying}) and runs another command for play/pause, next and previous.
 */
abstract class PolledMediaSource extends MediaSource {
	private static final long INTERVAL_MS = 1000L;
	/** How long to wait before looking for a missing tool again. */
	private static final long RETRY_MS = 30_000L;
	private static final long TIMEOUT_MS = 3000L;

	private volatile boolean stopped;
	private long retryAt;

	PolledMediaSource(ScheduledExecutorService worker) {
		super(worker);
	}

	/** Command printing the player state. */
	protected abstract String[] query();

	/** Publishes the output of {@link #query()}; null when it failed (usually: no player running). */
	protected abstract void report(String output);

	protected abstract String[] commandLine(Media.Command command);

	/** Shown when the tool cannot be started at all. */
	protected abstract String missing();

	@Override
	void start() {
		worker.scheduleWithFixedDelay(this::poll, 0L, INTERVAL_MS, TimeUnit.MILLISECONDS);
	}

	@Override
	void command(Media.Command command) {
		try {
			run(commandLine(command));
			// Show the result without waiting for the next regular poll.
			worker.schedule(this::poll, 250L, TimeUnit.MILLISECONDS);
		} catch (IOException | RejectedExecutionException ignored) {
			// Tool missing or shutting down: the poll reports it.
		}
	}

	@Override
	void stop() {
		stopped = true;
	}

	private void poll() {
		long now = System.currentTimeMillis();
		if (stopped || now < retryAt) return;
		try {
			report(run(query()));
		} catch (IOException e) {
			retryAt = now + RETRY_MS;
			unavailable(missing());
		} catch (RuntimeException e) {
			// Output we do not understand; an exception here would also cancel the polling.
			publish(null, "", 0L, false);
		}
	}

	/** Runs a command and returns its trimmed standard output, or null if it failed or hung. */
	private static String run(String... command) throws IOException {
		Process process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
		try (InputStream out = process.getInputStream()) {
			process.getOutputStream().close();
			// The output is a line or two, far below the pipe buffer, so waiting first cannot dead-lock.
			if (!process.waitFor(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
				process.destroyForcibly();
				return null;
			}
			String text = new String(out.readAllBytes(), StandardCharsets.UTF_8).trim();
			return process.exitValue() == 0 ? text : null;
		} catch (InterruptedException e) {
			process.destroyForcibly();
			Thread.currentThread().interrupt();
			return null;
		}
	}
}
