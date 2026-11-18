package dev.v1zir.visuals.chat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * Messages between players of V1zir Visuals, by Minecraft name, end-to-end encrypted ({@link ChatCrypto}) over a public
 * relay that only ever sees sealed bytes and hashed topic names:
 * <ul>
 *   <li>{@code k/<id>}: someone's key, kept on the relay (retained) so it can be found while they are away;</li>
 *   <li>{@code p/<id>}: whether they are online, kept too, set offline by the relay when their game drops;</li>
 *   <li>{@code i/<id>}: their inbox, where messages, delivery and read receipts, typing and the like are sent.</li>
 * </ul>
 * A name's key is trusted the first time it is seen (from its announcement, or from its first message when it has
 * none); a premium account proves its key through Mojang's session server, the way a client proves itself to a server
 * it joins, and a proven key replaces any other. Messages wait in the conversation until they can go, are sent again
 * until their delivery is confirmed, and are kept on disk per account.
 * <p>
 * Everything but the transport's callbacks runs on the game's thread; the callbacks are moved there through the host.
 */
public final class ChatService implements ChatTransport.Listener {
	/** What the service needs of the game. */
	public interface Host {
		/** The name of the account in use. */
		String name();

		UUID uuid();

		/** Whether this account can prove itself through Mojang's session server (a premium account). */
		boolean canVerify();

		/** Joins the "server" of this id with the session (Mojang), as a proof: true when it worked. */
		CompletableFuture<Boolean> join(String serverId);

		/** The UUID of the account that joined this id just now (Mojang), or null. */
		CompletableFuture<UUID> hasJoined(String name, String serverId);

		/** The file this account's messages and key are kept in. */
		Path file();

		/** Runs the task on the game's thread. */
		void post(Runnable task);

		long now();
	}

	/** What the game shows of it. */
	public interface Events {
		void received(Conversation conversation, Message message);
	}

	public enum Status { CONNECTING, ONLINE, OFFLINE }

	public static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
	public static final int MAX_TEXT = 500;
	private static final String PREFIX = "v1zir/chat1/";
	/** Sent again when they come online, or this game reconnects, unless it went out this recently. */
	private static final long RESEND_MS = 1_000L;
	private static final long PENDING_RETRY_MS = 15_000L;
	/** With them online, a message their game has not confirmed after this long goes again. */
	private static final long UNCONFIRMED_RETRY_MS = 30_000L;
	private static final long MAX_AGE_MS = 7L * 24L * 3600_000L;
	private static final int MAX_TRIES = 40;
	private static final long LOOKUP_MS = 5_000L;
	private static final long TYPING_MS = 4_500L;
	private static final long TYPING_SEND_MS = 3_000L;
	private static final long VERIFY_ASK_MS = 10L * 60_000L;
	private static final long ANNOUNCE_MIN_MS = 20_000L;
	private static final long FRESH_MS = 60_000L;
	private static final long SAVE_DELAY_MS = 1_500L;

	private final Host host;
	private final ChatTransport transport;
	private final Events events;
	private final String name;
	private final String myId;
	private ChatCrypto.Keys keys;
	private final Map<String, Contact> contacts = new LinkedHashMap<>();
	private final Map<String, Contact> byId = new HashMap<>();
	private final Map<String, Conversation> conversations = new LinkedHashMap<>();
	/** Messages from people whose key is still being looked up. */
	private final Map<String, List<ChatCrypto.Opened>> held = new HashMap<>();
	private Status status = Status.CONNECTING;
	private String error = "";
	private boolean appearOnline = true;
	private boolean started;
	private boolean dirty;
	private long dirtySince;
	private long lastAnnounce;
	private long lastRetry;
	private Conversation viewing;

	public ChatService(Host host, ChatTransport transport, Events events) {
		this.host = host;
		this.transport = transport;
		this.events = events;
		this.name = host.name();
		this.myId = ChatCrypto.id(name);
	}

	// ------------------------------------------------------------------ life

	public void start(boolean appearOnline) {
		if (started) return;
		started = true;
		this.appearOnline = appearOnline;
		load();
		if (keys == null) {
			keys = ChatCrypto.generate();
			dirty();
		}
		transport.start(clientId(), topic('p', myId), presence(false), this);
	}

	public void stop() {
		if (!started) return;
		started = false;
		save();
		transport.stop(topic('p', myId), presence(false));
	}

	/** Every game tick: retries, lookups that ran out, saving. */
	public void tick() {
		if (!started) return;
		long now = host.now();
		if (transport.connected() && now - lastRetry > 5_000L) {
			lastRetry = now;
			for (Conversation conversation : conversations.values()) retry(conversation, now);
		}
		if (!held.isEmpty()) {
			for (Contact contact : new ArrayList<>(contacts.values())) {
				if (held.containsKey(key(contact.name)) && contact.watchedAt > 0 && now - contact.watchedAt > LOOKUP_MS) release(contact);
			}
		}
		if (dirty && now - dirtySince > SAVE_DELAY_MS) save();
	}

	public void setAppearOnline(boolean online) {
		if (appearOnline == online) return;
		appearOnline = online;
		if (transport.connected()) transport.publish(topic('p', myId), presence(online), 1, true, null);
	}

	// ------------------------------------------------------------------ what the screen uses

	public String name() {
		return name;
	}

	public Status status() {
		return status;
	}

	public String error() {
		return error;
	}

	/** The conversations, the latest first. */
	public List<Conversation> conversations() {
		List<Conversation> list = new ArrayList<>(conversations.values());
		list.sort((a, b) -> Long.compare(b.activity, a.activity));
		return list;
	}

	public int unread() {
		int total = 0;
		for (Conversation conversation : conversations.values()) total += conversation.unread;
		return total;
	}

	public Conversation find(String who) {
		return who == null ? null : conversations.get(key(who));
	}

	/** The conversation with this name, started if new; null for a name that cannot be one (or is this account's). */
	public Conversation open(String who) {
		if (who == null || !NAME.matcher(who).matches() || who.equalsIgnoreCase(name)) return null;
		Contact contact = contact(who);
		Conversation conversation = conversation(contact);
		if (conversation.activity == 0L) conversation.activity = host.now();
		dirty();
		return conversation;
	}

	/** The conversation the screen shows: its messages count as read as they come. */
	public void viewing(Conversation conversation) {
		viewing = conversation;
		if (conversation != null && conversation.unread > 0) {
			conversation.unread = 0;
			dirty();
		}
		if (conversation != null) reportRead(conversation);
	}

	public Message send(Conversation conversation, String text) {
		String clean = clean(text);
		if (clean.isEmpty()) return null;
		Message message = new Message(newId(), true, host.now(), clean);
		conversation.add(message);
		conversation.activity = message.time;
		dirty();
		deliver(conversation, message);
		return message;
	}

	/** Typing in the conversation: they see it (a few seconds at a time). */
	public void typing(Conversation conversation) {
		long now = host.now();
		if (now - conversation.typingSent < TYPING_SEND_MS || conversation.contact.key == null) return;
		conversation.typingSent = now;
		sendTo(conversation.contact, decode(conversation.contact.key), body("y"), 0, null);
	}

	/** Trusts the other key that showed up under their name from now on. */
	public void trustNewKey(Conversation conversation) {
		Contact contact = conversation.contact;
		if (contact.pendingKey == null) return;
		pin(contact, contact.pendingKey, false);
		for (Message message : conversation.messages) message.unverified = false;
		note(conversation, "You trusted " + contact.name + "'s new security key");
	}

	public void delete(Conversation conversation) {
		conversations.remove(key(conversation.contact.name));
		if (viewing == conversation) viewing = null;
		dirty();
	}

	// ------------------------------------------------------------------ transport callbacks (any thread)

	@Override
	public void connected() {
		host.post(this::onConnected);
	}

	@Override
	public void disconnected(String reason) {
		host.post(() -> {
			if (!started) return;
			status = Status.OFFLINE;
			error = reason == null ? "" : reason;
			for (Contact contact : contacts.values()) contact.online = false;
		});
	}

	@Override
	public void message(String topic, byte[] payload) {
		host.post(() -> {
			if (started) onMessage(topic, payload);
		});
	}

	// ------------------------------------------------------------------ protocol

	private void onConnected() {
		if (!started) return;
		status = Status.ONLINE;
		error = "";
		transport.subscribe(topic('i', myId));
		for (Contact contact : contacts.values()) watch(contact);
		announce(false);
		transport.publish(topic('p', myId), presence(appearOnline), 1, true, null);
		long now = host.now();
		for (Conversation conversation : conversations.values()) flush(conversation, RESEND_MS, now);
	}

	private void onMessage(String topic, byte[] payload) {
		if (!topic.startsWith(PREFIX) || topic.length() < PREFIX.length() + 3 || topic.charAt(PREFIX.length() + 1) != '/') return;
		char kind = topic.charAt(PREFIX.length());
		String id = topic.substring(PREFIX.length() + 2);
		if (kind == 'i') {
			if (id.equals(myId)) inbox(payload);
			return;
		}
		Contact contact = byId.get(id);
		if (contact == null) return;
		if (kind == 'k') keyAnnounced(contact, payload);
		else if (kind == 'p') presence(contact, payload);
	}

	/** Puts this account's key on the relay, proven through Mojang when the account can. */
	private void announce(boolean asked) {
		long now = host.now();
		if (asked && now - lastAnnounce < ANNOUNCE_MIN_MS) return;
		lastAnnounce = now;
		String key = encode(keys.pub());
		if (!host.canVerify()) {
			publishKey(key, now, "");
			return;
		}
		String serverId = serverId(key, now);
		host.join(serverId).whenComplete((ok, failure) -> host.post(() -> {
			if (started) publishKey(key, now, Boolean.TRUE.equals(ok) ? serverId : "");
		}));
	}

	private void publishKey(String key, long time, String serverId) {
		JsonObject o = new JsonObject();
		o.addProperty("v", 1);
		o.addProperty("n", name);
		UUID uuid = host.uuid();
		if (uuid != null) o.addProperty("u", uuid.toString());
		o.addProperty("k", key);
		o.addProperty("t", time);
		o.addProperty("s", serverId);
		transport.publish(topic('k', myId), bytes(o), 1, true, null);
	}

	/** The id a key is proven under: the key and the time hashed, so a proof is good for that key only. */
	static String serverId(String key, long time) {
		return ChatCrypto.hex(ChatCrypto.sha1(("v1zir-chat:" + key + ":" + time).getBytes(StandardCharsets.UTF_8)), 20);
	}

	private void keyAnnounced(Contact contact, byte[] payload) {
		JsonObject o = json(payload);
		if (o == null) return;
		String who = str(o, "n");
		String key = str(o, "k");
		String serverId = str(o, "s");
		long time = num(o, "t");
		if (who == null || !who.equalsIgnoreCase(contact.name) || key == null || decode(key).length != ChatCrypto.KEY) return;
		contact.announced = true;
		contact.name = who;
		String uuid = str(o, "u");
		if (uuid != null && contact.uuid == null) contact.uuid = parseUuid(uuid);
		if (contact.key == null) pin(contact, key, false);
		else if (!contact.key.equals(key) && !key.equals(contact.pendingKey)) {
			contact.pendingKey = key;
			dirty();
		}
		// A proof made just now (Mojang keeps a join only briefly), for this very key and time.
		boolean fresh = serverId != null && !serverId.isEmpty() && Math.abs(host.now() - time) < FRESH_MS && serverId.equals(serverId(key, time));
		if (fresh && (!contact.verified || !key.equals(contact.key))) verify(contact, who, key, serverId);
		release(contact);
	}

	private void verify(Contact contact, String who, String key, String serverId) {
		host.hasJoined(who, serverId).whenComplete((uuid, failure) -> host.post(() -> {
			if (!started || uuid == null) return;
			contact.uuid = uuid;
			if (!key.equals(contact.key)) {
				boolean changed = contact.key != null;
				pin(contact, key, true);
				Conversation conversation = conversations.get(key(contact.name));
				if (changed && conversation != null) note(conversation, contact.name + "'s security key changed (confirmed by Mojang)");
			}
			contact.verified = true;
			if (key.equals(contact.pendingKey)) contact.pendingKey = null;
			dirty();
		}));
	}

	private void presence(Contact contact, byte[] payload) {
		JsonObject o = json(payload);
		if (o == null) return;
		boolean online = num(o, "o") == 1L;
		long time = num(o, "t");
		boolean was = contact.online;
		contact.online = online;
		if (online) contact.lastSeen = host.now();
		else if (time > 0L) contact.lastSeen = time;
		if (online && !was) {
			Conversation conversation = conversations.get(key(contact.name));
			if (conversation != null) flush(conversation, RESEND_MS, host.now());
			// Ask them to prove their key while they are here (a premium account can).
			if (!contact.verified && contact.key != null && host.now() - contact.lastVerifyAsk > VERIFY_ASK_MS) {
				contact.lastVerifyAsk = host.now();
				sendTo(contact, decode(contact.key), body("v"), 1, null);
			}
		}
	}

	private void inbox(byte[] payload) {
		ChatCrypto.Opened opened;
		try {
			opened = ChatCrypto.unwrap(keys, payload);
		} catch (GeneralSecurityException | RuntimeException e) {
			// Not for this key (sealed to an older one), or damaged.
			return;
		}
		String from = opened.from();
		if (!NAME.matcher(from).matches() || from.equalsIgnoreCase(name)) return;
		Contact contact = contact(from);
		if (contact.key == null) {
			// Their announced key decides whom to trust with the name: wait for it a few seconds.
			held.computeIfAbsent(key(contact.name), k -> new ArrayList<>()).add(opened);
			watch(contact);
			return;
		}
		process(contact, opened);
	}

	/** Their key is known now (or was not found in time): handles what came from them meanwhile. */
	private void release(Contact contact) {
		List<ChatCrypto.Opened> list = held.remove(key(contact.name));
		if (list == null || list.isEmpty()) return;
		if (contact.key == null) pin(contact, encode(list.get(0).fromKey()), false);
		for (ChatCrypto.Opened opened : list) process(contact, opened);
	}

	private void process(Contact contact, ChatCrypto.Opened opened) {
		String fromKey = encode(opened.fromKey());
		boolean trusted = fromKey.equals(contact.key);
		if (!trusted && !fromKey.equals(contact.pendingKey)) {
			contact.pendingKey = fromKey;
			dirty();
		}
		JsonObject body = json(opened.body());
		String type = body == null ? null : str(body, "t");
		if (type == null) return;
		Conversation conversation = conversation(contact);
		switch (type) {
			case "m" -> {
				String id = str(body, "i");
				String text = str(body, "x");
				if (id == null || text == null || id.length() > 32) return;
				if (conversation.find(id) == null) {
					long time = num(body, "ts");
					Message message = new Message(id, false, time > 0L ? time : host.now(), clean(text));
					message.unverified = !trusted;
					conversation.add(message);
					conversation.activity = host.now();
					contact.typingUntil = 0L;
					if (conversation == viewing) reportRead(conversation);
					else conversation.unread++;
					dirty();
					events.received(conversation, message);
				}
				// Confirmed every time, so their game stops sending it again.
				JsonObject ack = body("a");
				ack.addProperty("i", id);
				sendTo(contact, opened.fromKey(), ack, 1, null);
			}
			case "a" -> {
				Message message = conversation.find(str(body, "i"));
				if (trusted && message != null && message.out && message.state < Message.DELIVERED) {
					message.state = Message.DELIVERED;
					conversation.revision++;
					dirty();
				}
			}
			case "r" -> {
				if (!trusted) return;
				long upTo = num(body, "ts");
				for (Message message : conversation.messages) {
					if (message.out && !message.system && message.time <= upTo && message.state < Message.READ) message.state = Message.READ;
				}
				conversation.revision++;
				dirty();
			}
			case "y" -> {
				if (trusted) contact.typingUntil = host.now() + TYPING_MS;
			}
			case "v" -> {
				if (trusted) announce(true);
			}
			default -> {
				// Newer kinds are left for newer versions.
			}
		}
	}

	private void deliver(Conversation conversation, Message message) {
		Contact contact = conversation.contact;
		if (contact.key == null) return;
		JsonObject body = body("m");
		body.addProperty("i", message.id);
		body.addProperty("ts", message.time);
		body.addProperty("x", message.text);
		message.lastSent = host.now();
		message.tries++;
		sendTo(contact, decode(contact.key), body, 1, () -> {
			if (message.state < Message.SENT) {
				message.state = Message.SENT;
				conversation.revision++;
				dirty();
			}
		});
	}

	/**
	 * Sends what is waiting, and what went out longer ago than resendAfter (ms, -1 for never) but is not confirmed by
	 * their game: their game drops what it already has, and confirms it again.
	 */
	private void flush(Conversation conversation, long resendAfter, long now) {
		for (Message message : conversation.messages) {
			if (!sendable(message, now)) continue;
			if (message.state == Message.PENDING || resendAfter >= 0L && now - message.lastSent > resendAfter) deliver(conversation, message);
		}
	}

	/**
	 * Now and then: messages that never reached the relay (the connection dropped first) go again, and with them
	 * online so does what their game has not confirmed for a while.
	 */
	private void retry(Conversation conversation, long now) {
		boolean online = conversation.contact.online;
		for (Message message : conversation.messages) {
			if (!sendable(message, now)) continue;
			long since = now - message.lastSent;
			if (message.state == Message.PENDING && since > PENDING_RETRY_MS || online && since > UNCONFIRMED_RETRY_MS) deliver(conversation, message);
		}
	}

	private static boolean sendable(Message message, long now) {
		return message.out && !message.system && message.state < Message.DELIVERED && now - message.time < MAX_AGE_MS && message.tries < MAX_TRIES;
	}

	private void reportRead(Conversation conversation) {
		Message last = null;
		for (int i = conversation.messages.size() - 1; i >= 0; i--) {
			Message message = conversation.messages.get(i);
			if (!message.out && !message.system) {
				last = message;
				break;
			}
		}
		if (last == null || last.id.equals(conversation.readReported) || conversation.contact.key == null) return;
		JsonObject body = body("r");
		body.addProperty("i", last.id);
		body.addProperty("ts", last.time);
		if (sendTo(conversation.contact, decode(conversation.contact.key), body, 1, null)) {
			conversation.readReported = last.id;
			dirty();
		}
	}

	private boolean sendTo(Contact contact, byte[] theirKey, JsonObject body, int qos, Runnable onAck) {
		if (!transport.connected() || theirKey == null || theirKey.length != ChatCrypto.KEY) return false;
		byte[] wire;
		try {
			wire = ChatCrypto.wrap(keys, name, theirKey, bytes(body));
		} catch (GeneralSecurityException e) {
			return false;
		}
		return transport.publish(topic('i', contact.id), wire, qos, false, onAck == null ? null : () -> host.post(onAck));
	}

	private void pin(Contact contact, String key, boolean verified) {
		contact.key = key;
		contact.verified = verified;
		if (key.equals(contact.pendingKey)) contact.pendingKey = null;
		dirty();
		Conversation conversation = conversations.get(key(contact.name));
		if (conversation != null) flush(conversation, -1L, host.now());
	}

	private void note(Conversation conversation, String text) {
		Message note = new Message(newId(), false, host.now(), text);
		note.system = true;
		conversation.add(note);
		dirty();
	}

	private void watch(Contact contact) {
		if (!transport.connected()) return;
		transport.subscribe(topic('k', contact.id));
		transport.subscribe(topic('p', contact.id));
		if (contact.watchedAt == 0L) contact.watchedAt = host.now();
	}

	private Contact contact(String who) {
		Contact contact = contacts.get(key(who));
		if (contact == null) {
			contact = new Contact(who);
			contacts.put(key(who), contact);
			byId.put(contact.id, contact);
			watch(contact);
		}
		return contact;
	}

	private Conversation conversation(Contact contact) {
		return conversations.computeIfAbsent(key(contact.name), k -> new Conversation(contact));
	}

	private byte[] presence(boolean online) {
		JsonObject o = new JsonObject();
		o.addProperty("o", online ? 1 : 0);
		o.addProperty("t", appearOnline ? host.now() : 0L);
		return bytes(o);
	}

	/** One id per account and key, 23 characters as MQTT 3.1.1 allows everywhere: the relay keeps its session by it. */
	private String clientId() {
		return "v1z" + ChatCrypto.hex(ChatCrypto.sha256(keys.pub()), 10);
	}

	private static String topic(char kind, String id) {
		return PREFIX + kind + "/" + id;
	}

	private static JsonObject body(String type) {
		JsonObject o = new JsonObject();
		o.addProperty("t", type);
		return o;
	}

	/** One line of at most MAX_TEXT characters, without control characters. */
	static String clean(String text) {
		if (text == null) return "";
		StringBuilder out = new StringBuilder(Math.min(text.length(), MAX_TEXT));
		text.strip().codePoints().limit(MAX_TEXT).forEach(cp -> out.appendCodePoint(Character.isISOControl(cp) ? ' ' : cp));
		return out.toString().strip();
	}

	private static String newId() {
		return ChatCrypto.hex(ChatCrypto.random(8), 8);
	}

	private static String key(String who) {
		return who.toLowerCase(Locale.ROOT);
	}

	// ------------------------------------------------------------------ storage

	private void dirty() {
		if (!dirty) dirtySince = host.now();
		dirty = true;
	}

	private void load() {
		Path file = host.file();
		if (!Files.isRegularFile(file)) return;
		try {
			JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
			String priv = str(root, "priv");
			String pub = str(root, "pub");
			if (priv != null && pub != null) keys = ChatCrypto.restore(decode(priv), decode(pub));
			for (JsonElement e : array(root, "contacts")) {
				if (!e.isJsonObject()) continue;
				JsonObject o = e.getAsJsonObject();
				String who = str(o, "n");
				if (who == null || !NAME.matcher(who).matches()) continue;
				Contact contact = contact(who);
				contact.key = str(o, "k");
				contact.verified = bool(o, "ver");
				contact.pendingKey = str(o, "pk");
				contact.announced = bool(o, "ann");
				contact.lastSeen = num(o, "seen");
				String uuid = str(o, "u");
				if (uuid != null) contact.uuid = parseUuid(uuid);
			}
			for (JsonElement e : array(root, "chats")) {
				if (!e.isJsonObject()) continue;
				JsonObject o = e.getAsJsonObject();
				Contact contact = contacts.get(key(String.valueOf(str(o, "with"))));
				if (contact == null) continue;
				Conversation conversation = conversation(contact);
				conversation.unread = (int) num(o, "unread");
				conversation.readReported = str(o, "rr");
				conversation.activity = num(o, "act");
				for (JsonElement me : array(o, "m")) {
					if (!me.isJsonObject()) continue;
					JsonObject m = me.getAsJsonObject();
					String id = str(m, "i");
					String text = str(m, "x");
					if (id == null || text == null) continue;
					Message message = new Message(id, bool(m, "o"), num(m, "t"), text);
					message.state = (int) num(m, "s");
					message.unverified = bool(m, "uv");
					message.system = bool(m, "sys");
					message.tries = (int) num(m, "n");
					conversation.messages.add(message);
				}
			}
		} catch (IOException | RuntimeException | GeneralSecurityException e) {
			// Kept aside rather than overwritten: a new key would make this account a stranger to its contacts.
			error = "Saved messages could not be read";
			try {
				Files.move(file, file.resolveSibling(file.getFileName() + ".bad"), StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException ignored) {
				// Then it is overwritten.
			}
			contacts.clear();
			byId.clear();
			conversations.clear();
			keys = null;
		}
	}

	private void save() {
		dirty = false;
		if (keys == null) return;
		JsonObject root = new JsonObject();
		root.addProperty("v", 1);
		root.addProperty("name", name);
		root.addProperty("priv", encode(keys.encodedPrivate()));
		root.addProperty("pub", encode(keys.pub()));
		JsonArray list = new JsonArray();
		for (Contact contact : contacts.values()) {
			JsonObject o = new JsonObject();
			o.addProperty("n", contact.name);
			if (contact.key != null) o.addProperty("k", contact.key);
			if (contact.verified) o.addProperty("ver", true);
			if (contact.pendingKey != null) o.addProperty("pk", contact.pendingKey);
			if (contact.announced) o.addProperty("ann", true);
			if (contact.lastSeen > 0L) o.addProperty("seen", contact.lastSeen);
			if (contact.uuid != null) o.addProperty("u", contact.uuid.toString());
			list.add(o);
		}
		root.add("contacts", list);
		JsonArray chats = new JsonArray();
		for (Conversation conversation : conversations.values()) {
			JsonObject o = new JsonObject();
			o.addProperty("with", conversation.contact.name);
			o.addProperty("unread", conversation.unread);
			if (conversation.readReported != null) o.addProperty("rr", conversation.readReported);
			o.addProperty("act", conversation.activity);
			JsonArray messages = new JsonArray();
			for (Message message : conversation.messages) {
				JsonObject m = new JsonObject();
				m.addProperty("i", message.id);
				if (message.out) m.addProperty("o", true);
				m.addProperty("t", message.time);
				m.addProperty("x", message.text);
				if (message.state != 0) m.addProperty("s", message.state);
				if (message.unverified) m.addProperty("uv", true);
				if (message.system) m.addProperty("sys", true);
				if (message.tries != 0) m.addProperty("n", message.tries);
				messages.add(m);
			}
			o.add("m", messages);
			chats.add(o);
		}
		root.add("chats", chats);
		Path file = host.file();
		try {
			Files.createDirectories(file.getParent());
			Path temp = file.resolveSibling(file.getFileName() + ".tmp");
			Files.writeString(temp, root.toString(), StandardCharsets.UTF_8);
			Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			error = "Messages could not be saved";
		}
	}

	// ------------------------------------------------------------------ json

	private static JsonObject json(byte[] payload) {
		try {
			JsonElement element = JsonParser.parseString(new String(payload, StandardCharsets.UTF_8));
			return element.isJsonObject() ? element.getAsJsonObject() : null;
		} catch (RuntimeException e) {
			return null;
		}
	}

	private static byte[] bytes(JsonObject o) {
		return o.toString().getBytes(StandardCharsets.UTF_8);
	}

	private static String str(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
	}

	private static long num(JsonObject o, String key) {
		JsonElement e = o.get(key);
		if (e == null || !e.isJsonPrimitive()) return 0L;
		try {
			return e.getAsLong();
		} catch (RuntimeException ex) {
			return 0L;
		}
	}

	private static boolean bool(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() && e.getAsBoolean();
	}

	private static JsonArray array(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonArray() ? e.getAsJsonArray() : new JsonArray();
	}

	private static UUID parseUuid(String text) {
		try {
			return UUID.fromString(text);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private static String encode(byte[] bytes) {
		return Base64.getEncoder().encodeToString(bytes);
	}

	private static byte[] decode(String text) {
		try {
			return Base64.getDecoder().decode(text);
		} catch (IllegalArgumentException e) {
			return new byte[0];
		}
	}
}
