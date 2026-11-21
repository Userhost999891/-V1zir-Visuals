package dev.v1zir.visuals.chat;

/** One message of a conversation: sent (out) or received, or a note of the app. Game thread only. */
public final class Message {
	/** Written, not yet with the relay. */
	public static final int PENDING = 0;
	/** The relay has it. */
	public static final int SENT = 1;
	/** Their game has it. */
	public static final int DELIVERED = 2;
	/** They have seen it. */
	public static final int READ = 3;

	final String id;
	final boolean out;
	final long time;
	final String text;
	int state;
	/** Received with a key their name is not trusted with. */
	boolean unverified;
	/** A note of the app (a key change), not a message. */
	boolean system;
	int tries;
	transient long lastSent;

	Message(String id, boolean out, long time, String text) {
		this.id = id;
		this.out = out;
		this.time = time;
		this.text = text;
	}

	public String id() {
		return id;
	}

	public boolean out() {
		return out;
	}

	public long time() {
		return time;
	}

	public String text() {
		return text;
	}

	public int state() {
		return state;
	}

	public boolean unverified() {
		return unverified;
	}

	public boolean system() {
		return system;
	}
}
