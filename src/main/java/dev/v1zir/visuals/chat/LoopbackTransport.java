package dev.v1zir.visuals.chat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A relay inside the game, for the game test: everything published on one bus reaches the transports on it that
 * subscribed to the topic, on a thread of the bus (as a real relay's messages arrive on the network thread), and
 * retained messages are kept for whoever subscribes later.
 */
public final class LoopbackTransport implements ChatTransport {
	public static final class Bus {
		private final Map<String, byte[]> retained = new ConcurrentHashMap<>();
		private final List<LoopbackTransport> clients = new CopyOnWriteArrayList<>();
		private final ExecutorService delivery = Executors.newSingleThreadExecutor(r -> {
			Thread thread = new Thread(r, "V1zir messages loopback");
			thread.setDaemon(true);
			return thread;
		});
	}

	private final Bus bus;
	private final Set<String> topics = ConcurrentHashMap.newKeySet();
	private volatile Listener listener;
	private volatile boolean up;

	public LoopbackTransport(Bus bus) {
		this.bus = bus;
	}

	@Override
	public void start(String clientId, String willTopic, byte[] will, Listener listener) {
		this.listener = listener;
		up = true;
		bus.clients.add(this);
		bus.delivery.execute(listener::connected);
	}

	@Override
	public void subscribe(String topic) {
		if (!topics.add(topic) || !up) return;
		byte[] kept = bus.retained.get(topic);
		if (kept != null) bus.delivery.execute(() -> listener.message(topic, kept));
	}

	@Override
	public void unsubscribe(String topic) {
		topics.remove(topic);
	}

	@Override
	public boolean publish(String topic, byte[] payload, int qos, boolean retain, Runnable onAck) {
		if (!up) return false;
		byte[] copy = payload.clone();
		if (retain) {
			if (copy.length == 0) bus.retained.remove(topic);
			else bus.retained.put(topic, copy);
		}
		for (LoopbackTransport client : bus.clients) {
			if (client.up && client.topics.contains(topic)) bus.delivery.execute(() -> client.listener.message(topic, copy));
		}
		if (onAck != null) bus.delivery.execute(onAck);
		return true;
	}

	@Override
	public boolean connected() {
		return up;
	}

	@Override
	public void stop(String lastTopic, byte[] lastPayload) {
		if (lastTopic != null) publish(lastTopic, lastPayload, 1, true, null);
		up = false;
		bus.clients.remove(this);
	}
}
