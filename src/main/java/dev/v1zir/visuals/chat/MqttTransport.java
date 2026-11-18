package dev.v1zir.visuals.chat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Messages over an MQTT relay, kept connected by a thread of its own: it connects, subscribes to everything asked for
 * so far, and after a drop waits a little longer each time (2 s up to a minute) before it tries again. The session is
 * kept on the relay between connections, so messages sent while the game was closed are delivered when it connects.
 */
public final class MqttTransport implements ChatTransport {
	private static final long[] BACKOFF_MS = {2_000L, 5_000L, 10_000L, 30_000L, 60_000L};
	private static final int KEEP_ALIVE_SECONDS = 40;

	private final String host;
	private final int port;
	private final boolean tls;
	private final Set<String> topics = ConcurrentHashMap.newKeySet();
	private final Map<Integer, Runnable> acks = new ConcurrentHashMap<>();
	private volatile Mqtt client;
	private volatile boolean running;
	private Thread loop;

	public MqttTransport(String host, int port, boolean tls) {
		this.host = host;
		this.port = port;
		this.tls = tls;
	}

	@Override
	public void start(String clientId, String willTopic, byte[] will, Listener listener) {
		if (running) return;
		running = true;
		loop = new Thread(() -> run(clientId, willTopic, will, listener), "V1zir messages");
		loop.setDaemon(true);
		loop.start();
	}

	private void run(String clientId, String willTopic, byte[] will, Listener listener) {
		int attempt = 0;
		while (running) {
			String reason = "Closed";
			try {
				Mqtt mqtt = Mqtt.connect(host, port, tls, clientId, false, willTopic, will, KEEP_ALIVE_SECONDS, new Mqtt.Handler() {
					@Override
					public void message(String topic, byte[] payload) {
						listener.message(topic, payload);
					}

					@Override
					public void acked(int packetId) {
						Runnable ack = acks.remove(packetId);
						if (ack != null) ack.run();
					}
				});
				client = mqtt;
				attempt = 0;
				mqtt.subscribe(new ArrayList<>(topics), 1);
				listener.connected();
				Throwable cause = mqtt.awaitClose();
				if (cause != null) reason = cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
			} catch (IOException e) {
				reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			}
			client = null;
			acks.clear();
			listener.disconnected(reason);
			if (!running) break;
			try {
				Thread.sleep(BACKOFF_MS[Math.min(attempt++, BACKOFF_MS.length - 1)]);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			}
		}
	}

	@Override
	public void subscribe(String topic) {
		if (!topics.add(topic)) return;
		Mqtt mqtt = client;
		if (mqtt == null) return;
		try {
			mqtt.subscribe(List.of(topic), 1);
		} catch (IOException e) {
			mqtt.close(e);
		}
	}

	@Override
	public void unsubscribe(String topic) {
		if (!topics.remove(topic)) return;
		Mqtt mqtt = client;
		if (mqtt == null) return;
		try {
			mqtt.unsubscribe(topic);
		} catch (IOException e) {
			mqtt.close(e);
		}
	}

	@Override
	public boolean publish(String topic, byte[] payload, int qos, boolean retain, Runnable onAck) {
		Mqtt mqtt = client;
		if (mqtt == null || mqtt.closed()) return false;
		int id = qos > 0 ? mqtt.nextPacketId() : 0;
		// Registered before it goes out: the acknowledgement can be back before publish returns.
		if (qos > 0 && onAck != null) acks.put(id, onAck);
		try {
			mqtt.publish(topic, payload, qos, retain, id);
		} catch (IOException e) {
			acks.remove(id);
			mqtt.close(e);
			return false;
		}
		if (qos == 0 && onAck != null) onAck.run();
		return true;
	}

	@Override
	public boolean connected() {
		Mqtt mqtt = client;
		return mqtt != null && !mqtt.closed();
	}

	@Override
	public void stop(String lastTopic, byte[] lastPayload) {
		running = false;
		Mqtt mqtt = client;
		if (mqtt != null) {
			if (lastTopic != null) {
				try {
					mqtt.publish(lastTopic, lastPayload, 1, true, mqtt.nextPacketId());
				} catch (IOException ignored) {
					// The will says the same.
				}
			}
			mqtt.disconnect();
		}
		if (loop != null) loop.interrupt();
	}
}
