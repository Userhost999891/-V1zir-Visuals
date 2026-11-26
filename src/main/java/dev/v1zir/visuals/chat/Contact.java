package dev.v1zir.visuals.chat;

import java.util.UUID;

/** Someone to write to, by their Minecraft name, and what is known of them. Game thread only. */
public final class Contact {
	final String id;
	String name;
	/** Their key as first seen (Base64), or null while unknown. */
	String key;
	/** Mojang confirmed that key belongs to their account. */
	boolean verified;
	/** A different key that showed up under their name, not trusted. */
	String pendingKey;
	UUID uuid;
	/** Their key was announced: they have V1zir Visuals. */
	boolean announced;
	long lastSeen;
	transient boolean online;
	transient long typingUntil;
	/** When their topics were first subscribed to, this session (0: not yet). */
	transient long watchedAt;
	transient long lastVerifyAsk;
	/** The name and picture (PNG) they chose for Messages, null for none: their Minecraft name and head then. */
	String displayName;
	byte[] avatar;
	/** The version of their profile kept here, and the version of this account's own that their game confirmed. */
	long profileRev;
	long profileSent;
	transient long profileSentAt;

	Contact(String name) {
		this.name = name;
		this.id = ChatCrypto.id(name);
	}

	public String name() {
		return name;
	}

	public UUID uuid() {
		return uuid;
	}

	/** The name they chose for Messages, else their Minecraft name. */
	public String displayName() {
		return displayName != null && !displayName.isEmpty() ? displayName : name;
	}

	/** Whether they chose a name of their own (the Minecraft name is then shown beside it). */
	public boolean hasDisplayName() {
		return displayName != null && !displayName.isEmpty() && !displayName.equalsIgnoreCase(name);
	}

	/** The picture they chose (PNG), null for their head. */
	public byte[] avatar() {
		return avatar;
	}

	/** The version of their profile: changes whenever their name or picture does. */
	public long profileRevision() {
		return profileRev;
	}

	public boolean online() {
		return online;
	}

	/** When they were last online (ms), 0 when unknown. */
	public long lastSeen() {
		return lastSeen;
	}

	public boolean typing(long now) {
		return typingUntil > now;
	}

	public boolean verified() {
		return verified;
	}

	/** They have V1zir Visuals (their key is known). */
	public boolean onV1zir() {
		return announced || key != null;
	}

	/** Their key was looked for and not found (yet): they don't use V1zir Visuals, or not since it got Messages. */
	public boolean notFound(long now) {
		return !onV1zir() && watchedAt > 0 && now - watchedAt > 5_000L;
	}

	/** Someone else claimed their name with another key. */
	public boolean keyConflict() {
		return pendingKey != null;
	}
}
