package dev.v1zir.visuals.media;

import java.util.concurrent.ScheduledExecutorService;

/** Linux (and other MPRIS desktops) through playerctl: the Spotify player first, else any player. */
final class LinuxMediaSource extends PolledMediaSource {
	private static final String PLAYERS = "--player=spotify,%any";
	/** Title last, so a tab inside it cannot shift the other fields. Lengths and positions are in microseconds. */
	private static final String FORMAT = "{{status}}\t{{mpris:length}}\t{{position}}\t{{mpris:artUrl}}\t{{xesam:album}}\t{{xesam:artist}}\t{{xesam:title}}";

	LinuxMediaSource(ScheduledExecutorService worker) {
		super(worker);
	}

	@Override
	protected String[] query() {
		return new String[]{"playerctl", PLAYERS, "metadata", "--format", FORMAT};
	}

	@Override
	protected void report(String output) {
		String[] f = output == null ? null : output.split("\t", 7);
		if (f == null || f.length < 7 || f[6].isBlank() || f[0].equals("Stopped")) {
			publish(null, "", 0L, false);
			return;
		}
		Track track = new Track(f[6], f[5], f[4], micros(f[1]));
		publish(track, f[3], micros(f[2]), f[0].equals("Playing"));
	}

	private static long micros(String value) {
		try {
			return Long.parseLong(value.trim()) / 1000L;
		} catch (NumberFormatException e) {
			return 0L;
		}
	}

	@Override
	protected String[] commandLine(Media.Command command) {
		String action = switch (command) {
			case PLAY_PAUSE -> "play-pause";
			case NEXT -> "next";
			case PREVIOUS -> "previous";
		};
		return new String[]{"playerctl", PLAYERS, action};
	}

	@Override
	protected String missing() {
		return "Install playerctl to see what is playing";
	}
}
