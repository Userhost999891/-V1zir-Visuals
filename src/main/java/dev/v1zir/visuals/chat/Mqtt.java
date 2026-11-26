package dev.v1zir.visuals.chat;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Just enough MQTT 3.1.1 for Messages: connect (with a will), subscribe, publish at QoS 0 or 1 and receive, over TLS
 * with the server's name checked against its certificate. One reader thread per connection reads the packets, answers
 * a QoS 1 message with its PUBACK, and pings the server when nothing has been sent for half the keep-alive.
 */
final class Mqtt {
	interface Handler {
		void message(String topic, byte[] payload);

		/** The server has the QoS 1 message published under this packet id. */
		void acked(int packetId);
	}

	private static final int CONNECT = 1;
	private static final int CONNACK = 2;
	private static final int PUBLISH = 3;
	private static final int PUBACK = 4;
	private static final int PUBREC = 5;
	private static final int PUBREL = 6;
	private static final int SUBSCRIBE = 8;
	private static final int UNSUBSCRIBE = 10;
	private static final int PINGREQ = 12;
	private static final int DISCONNECT = 14;
	private static final int MAX_PACKET = 256 * 1024;
	private static final int TIMEOUT_MS = 10_000;

	private final Socket socket;
	private final DataInputStream in;
	private final OutputStream out;
	private final Handler handler;
	private final int keepAliveMs;
	private final Object writeLock = new Object();
	private final Thread reader;
	private volatile boolean closed;
	private volatile long lastRead;
	private volatile long lastWrite;
	private volatile Throwable failure;
	private int nextId;

	private Mqtt(Socket socket, Handler handler, int keepAliveSeconds) throws IOException {
		this.socket = socket;
		this.in = new DataInputStream(socket.getInputStream());
		this.out = socket.getOutputStream();
		this.handler = handler;
		this.keepAliveMs = keepAliveSeconds * 1000;
		this.reader = new Thread(this::read, "V1zir messages reader");
		this.reader.setDaemon(true);
	}

	/** Connects and logs in; throws when the server cannot be reached or refuses. */
	static Mqtt connect(String host, int port, boolean tls, String clientId, boolean cleanSession, String willTopic, byte[] will,
						int keepAliveSeconds, Handler handler) throws IOException {
		Socket raw = new Socket();
		Socket socket = raw;
		try {
			raw.connect(new InetSocketAddress(host, port), TIMEOUT_MS);
			if (tls) {
				SSLSocket ssl = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(raw, host, port, true);
				SSLParameters params = ssl.getSSLParameters();
				// Java checks the certificate chain but not the name on it unless asked.
				params.setEndpointIdentificationAlgorithm("HTTPS");
				ssl.setSSLParameters(params);
				ssl.setSoTimeout(TIMEOUT_MS);
				ssl.startHandshake();
				socket = ssl;
			}
			socket.setTcpNoDelay(true);
			socket.setSoTimeout(TIMEOUT_MS);
			Mqtt mqtt = new Mqtt(socket, handler, keepAliveSeconds);
			mqtt.login(clientId, cleanSession, willTopic, will);
			mqtt.reader.start();
			return mqtt;
		} catch (IOException | RuntimeException e) {
			try {
				socket.close();
			} catch (IOException ignored) {
				// Already failing.
			}
			throw e instanceof IOException io ? io : new IOException(e);
		}
	}

	private void login(String clientId, boolean cleanSession, String willTopic, byte[] will) throws IOException {
		ByteArrayOutputStream body = new ByteArrayOutputStream();
		string(body, "MQTT");
		body.write(4);
		int flags = cleanSession ? 0x02 : 0;
		if (willTopic != null) flags |= 0x04 | 0x08 | 0x20; // will, at QoS 1, retained
		body.write(flags);
		body.write(keepAliveMs / 1000 >> 8);
		body.write(keepAliveMs / 1000 & 0xFF);
		string(body, clientId);
		if (willTopic != null) {
			string(body, willTopic);
			binary(body, will);
		}
		send(CONNECT << 4, body.toByteArray());
		int first = in.read();
		if (first < 0) throw new EOFException("Closed before CONNACK");
		int length = length();
		byte[] ack = in.readNBytes(length);
		if (first >> 4 != CONNACK || ack.length < 2) throw new IOException("No CONNACK");
		if (ack[1] != 0) throw new IOException("Refused (" + ack[1] + ")");
		lastRead = System.currentTimeMillis();
	}

	/** The next packet id, to register its acknowledgement before the message goes out. */
	int nextPacketId() {
		synchronized (writeLock) {
			nextId = nextId % 0xFFFF + 1;
			return nextId;
		}
	}

	void publish(String topic, byte[] payload, int qos, boolean retain, int packetId) throws IOException {
		ByteArrayOutputStream body = new ByteArrayOutputStream(payload.length + topic.length() + 8);
		string(body, topic);
		if (qos > 0) {
			body.write(packetId >> 8);
			body.write(packetId & 0xFF);
		}
		body.writeBytes(payload);
		send(PUBLISH << 4 | qos << 1 | (retain ? 1 : 0), body.toByteArray());
	}

	void subscribe(List<String> topics, int qos) throws IOException {
		if (topics.isEmpty()) return;
		ByteArrayOutputStream body = new ByteArrayOutputStream();
		int id = nextPacketId();
		body.write(id >> 8);
		body.write(id & 0xFF);
		for (String topic : topics) {
			string(body, topic);
			body.write(qos);
		}
		send(SUBSCRIBE << 4 | 0x02, body.toByteArray());
	}

	void unsubscribe(String topic) throws IOException {
		ByteArrayOutputStream body = new ByteArrayOutputStream();
		int id = nextPacketId();
		body.write(id >> 8);
		body.write(id & 0xFF);
		string(body, topic);
		send(UNSUBSCRIBE << 4 | 0x02, body.toByteArray());
	}

	/** Says goodbye (the will is not sent then) and closes. */
	void disconnect() {
		try {
			send(DISCONNECT << 4, new byte[0]);
		} catch (IOException ignored) {
			// Closing anyway.
		}
		close(null);
	}

	boolean closed() {
		return closed;
	}

	/** Blocks until the connection is closed; returns what closed it (null for a disconnect of ours). */
	Throwable awaitClose() throws InterruptedException {
		reader.join();
		return failure;
	}

	void close(Throwable cause) {
		if (closed) return;
		if (failure == null) failure = cause;
		closed = true;
		try {
			socket.close();
		} catch (IOException ignored) {
			// Nothing more to do.
		}
	}

	private void read() {
		try {
			while (!closed) {
				int first;
				try {
					socket.setSoTimeout(1000);
					first = in.read();
				} catch (SocketTimeoutException e) {
					keepAlive();
					continue;
				}
				if (first < 0) throw new EOFException("Closed by the server");
				socket.setSoTimeout(30_000);
				int length = length();
				if (length > MAX_PACKET) throw new IOException("Packet too large");
				byte[] body = in.readNBytes(length);
				if (body.length < length) throw new EOFException("Cut short");
				lastRead = System.currentTimeMillis();
				handle(first, body);
				keepAlive();
			}
		} catch (IOException | RuntimeException e) {
			close(e);
		}
	}

	private void keepAlive() throws IOException {
		long now = System.currentTimeMillis();
		if (now - lastRead > keepAliveMs * 3L / 2L) throw new SocketTimeoutException("Server silent");
		if (now - lastWrite > keepAliveMs / 2L) send(PINGREQ << 4, new byte[0]);
	}

	private void handle(int first, byte[] body) throws IOException {
		int type = first >> 4;
		switch (type) {
			case PUBLISH -> {
				int qos = first >> 1 & 3;
				int topicLength = (body[0] & 0xFF) << 8 | body[1] & 0xFF;
				String topic = new String(body, 2, topicLength, StandardCharsets.UTF_8);
				int at = 2 + topicLength;
				int id = 0;
				if (qos > 0) {
					id = (body[at] & 0xFF) << 8 | body[at + 1] & 0xFF;
					at += 2;
				}
				byte[] payload = new byte[body.length - at];
				System.arraycopy(body, at, payload, 0, payload.length);
				if (qos == 1) send(PUBACK << 4, new byte[]{(byte) (id >> 8), (byte) id});
				// QoS 2 is never asked for; should a server send it anyway, its first step is answered so it stops resending.
				else if (qos == 2) send(PUBREC << 4, new byte[]{(byte) (id >> 8), (byte) id});
				handler.message(topic, payload);
			}
			case PUBACK -> {
				if (body.length >= 2) handler.acked((body[0] & 0xFF) << 8 | body[1] & 0xFF);
			}
			case PUBREL -> {
				if (body.length >= 2) send(7 << 4, new byte[]{body[0], body[1]});
			}
			default -> {
				// CONNACK, SUBACK, UNSUBACK and PINGRESP need nothing from here.
			}
		}
	}

	private void send(int header, byte[] body) throws IOException {
		ByteArrayOutputStream packet = new ByteArrayOutputStream(body.length + 5);
		packet.write(header);
		int length = body.length;
		do {
			int digit = length % 128;
			length /= 128;
			if (length > 0) digit |= 0x80;
			packet.write(digit);
		} while (length > 0);
		packet.writeBytes(body);
		synchronized (writeLock) {
			if (closed) throw new IOException("Closed");
			out.write(packet.toByteArray());
			out.flush();
			lastWrite = System.currentTimeMillis();
		}
	}

	private int length() throws IOException {
		int value = 0;
		int multiplier = 1;
		InputStream stream = in;
		for (int i = 0; i < 4; i++) {
			int digit = stream.read();
			if (digit < 0) throw new EOFException("Cut short");
			value += (digit & 0x7F) * multiplier;
			if ((digit & 0x80) == 0) return value;
			multiplier *= 128;
		}
		throw new IOException("Bad length");
	}

	private static void string(ByteArrayOutputStream out, String value) {
		binary(out, value.getBytes(StandardCharsets.UTF_8));
	}

	private static void binary(ByteArrayOutputStream out, byte[] value) {
		out.write(value.length >> 8);
		out.write(value.length & 0xFF);
		out.writeBytes(value);
	}
}
