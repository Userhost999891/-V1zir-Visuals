package dev.v1zir.visuals.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The messages with one contact, oldest first. Game thread only. */
public final class Conversation {
	/** Messages kept per conversation; older ones are dropped. */
	static final int KEEP = 400;

	final Contact contact;
	final List<Message> messages = new ArrayList<>();
	int unread;
	/** The last received message they were told was read. */
	String readReported;
	/** Time of the last message, or of the opening for an empty one: the list is sorted by it. */
	long activity;
	transient long typingSent;
	/** Bumped on every change, so the screen knows to lay the messages out again. */
	transient int revision;

	Conversation(Contact contact) {
		this.contact = contact;
	}

	public Contact contact() {
		return contact;
	}

	public List<Message> messages() {
		return Collections.unmodifiableList(messages);
	}

	public int unread() {
		return unread;
	}

	public long activity() {
		return activity;
	}

	public int revision() {
		return revision;
	}

	public Message last() {
		for (int i = messages.size() - 1; i >= 0; i--) if (!messages.get(i).system) return messages.get(i);
		return null;
	}

	public Message find(String id) {
		if (id == null) return null;
		for (int i = messages.size() - 1; i >= 0; i--) if (messages.get(i).id.equals(id)) return messages.get(i);
		return null;
	}

	void add(Message message) {
		messages.add(message);
		while (messages.size() > KEEP) messages.remove(0);
		revision++;
	}
}
