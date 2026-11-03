package dev.v1zir.visuals.media;

import java.util.concurrent.ScheduledExecutorService;

/**
 * A platform backend of {@link Media}. start and command run on the media thread (the worker);
 * stop may be called from any thread and must not block.
 */
abstract class MediaSource {
	protected final ScheduledExecutorService worker;

	MediaSource(ScheduledExecutorService worker) {
		this.worker = worker;
	}

	static MediaSource forPlatform(String os, ScheduledExecutorService worker) {
		if (os.startsWith("windows")) return new WindowsMediaSource(worker);
		if (os.contains("mac") || os.contains("darwin")) return new MacMediaSource(worker);
		return new LinuxMediaSource(worker);
	}

	abstract void start();

	abstract void command(Media.Command command);

	abstract void stop();

	/** Reports the player state; a null track means nothing is playing. */
	protected final void publish(Track track, String art, long positionMs, boolean playing) {
		Media.publish(this, track, art == null ? "" : art, positionMs, playing);
	}

	protected final void unavailable(String reason) {
		Media.unavailable(this, reason);
	}
}
