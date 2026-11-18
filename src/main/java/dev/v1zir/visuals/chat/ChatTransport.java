package dev.v1zir.visuals.chat;

/**
 * How Messages reach other players: a publish/subscribe relay. Callbacks come on the transport's own thread; the
 * service moves them onto the game's thread itself.
 */
public interface ChatTransport {
	interface Listener {
		void connected();

		void disconnected(String reason);

		void message(String topic, byte[] payload);
	}

	/** Starts connecting (and reconnecting whenever the connection drops); the will is published if it drops. */
	void start(String clientId, String willTopic, byte[] will, Listener listener);

	/** Keeps the topic subscribed from now on, across reconnects; its retained message comes right away. */
	void subscribe(String topic);

	void unsubscribe(String topic);

	/**
	 * Sends a message; false when there is no connection right now. onAck runs (on the transport's thread) once the
	 * relay has it.
	 */
	boolean publish(String topic, byte[] payload, int qos, boolean retain, Runnable onAck);

	boolean connected();

	/** Publishes the last message (retained), if any, and disconnects for good. */
	void stop(String lastTopic, byte[] lastPayload);
}
