package dev.v1zir.visuals.chat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.v1zir.visuals.V1zir;
import dev.v1zir.visuals.account.Accounts;
import dev.v1zir.visuals.gui.UiSounds;
import dev.v1zir.visuals.gui.messages.MessageBanners;
import dev.v1zir.visuals.gui.messages.MessagesScreen;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.impl.client.MessagesModule;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.session.Session;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Messages in the game: one {@link ChatService} for the account in use while the Messages module is on, started again
 * when the account or the server changes; new messages become banners and a soft chime. A premium account proves its
 * key through Mojang's session server with its own session, the request a client makes when it joins a server.
 * <p>
 * Game thread only.
 */
public final class Messages {
	private static final String SESSION_SERVER = "https://sessionserver.mojang.com/session/minecraft/";
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
	private static ChatService service;
	private static String account;
	private static String server;
	/** The game test's relay, instead of the network. */
	private static Supplier<ChatTransport> testTransport;

	private Messages() {
	}

	/** The running service, or null while Messages is off. */
	public static ChatService service() {
		return service;
	}

	public static int unread() {
		return service == null ? 0 : service.unread();
	}

	/** Every client tick, in the menus too. */
	public static void tick(MinecraftClient client) {
		if (testTransport == null && "loopback".equals(System.getProperty("v1zir.messages.relay"))) {
			// The game test: nothing goes out until it gives Messages its in-game relay.
			stop();
			return;
		}
		MessagesModule module = ModuleManager.get(MessagesModule.class);
		boolean on = module != null && module.isEnabled();
		String name = client.getSession().getUsername();
		String preset = testTransport != null ? "test" : module != null ? module.server.get() : "";
		if (service != null && (!on || !name.equals(account) || !preset.equals(server))) stop();
		if (service == null && on && ChatService.NAME.matcher(name).matches()) {
			account = name;
			server = preset;
			ChatTransport transport = testTransport != null ? testTransport.get() : module.transport();
			service = new ChatService(new GameHost(client, name), transport, Messages::received);
			service.start(module.appearOnline.on());
		}
		if (service != null) {
			service.setAppearOnline(module.appearOnline.on());
			service.tick();
		}
	}

	/** Disconnects (others see this account go offline) and keeps everything on disk. */
	public static void stop() {
		if (service == null) return;
		service.stop();
		service = null;
		account = null;
	}

	/** For the game test: an in-game relay from now on instead of the network (null for the network again). */
	public static void useTransport(Supplier<ChatTransport> transport) {
		testTransport = transport;
		stop();
	}

	/** Another player on the same in-game relay, for the game test: its messages are kept in dir. */
	public static ChatService peer(String name, ChatTransport transport, Path dir, ChatService.Events events) {
		MinecraftClient client = MinecraftClient.getInstance();
		ChatService.Host host = new ChatService.Host() {
			private final UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));

			@Override
			public String name() {
				return name;
			}

			@Override
			public UUID uuid() {
				return uuid;
			}

			@Override
			public boolean canVerify() {
				return false;
			}

			@Override
			public CompletableFuture<Boolean> join(String serverId) {
				return CompletableFuture.completedFuture(false);
			}

			@Override
			public CompletableFuture<UUID> hasJoined(String who, String serverId) {
				return CompletableFuture.completedFuture(null);
			}

			@Override
			public Path file() {
				return dir.resolve(name.toLowerCase(Locale.ROOT) + ".json");
			}

			@Override
			public void post(Runnable task) {
				client.execute(task);
			}

			@Override
			public long now() {
				return System.currentTimeMillis();
			}
		};
		return new ChatService(host, transport, events);
	}

	private static void received(Conversation conversation, Message message) {
		MinecraftClient client = MinecraftClient.getInstance();
		MessagesModule module = ModuleManager.get(MessagesModule.class);
		if (module == null) return;
		boolean shown = client.currentScreen instanceof MessagesScreen screen && screen.showing(conversation);
		if (!shown && module.notifications.on()) MessageBanners.push(conversation, message);
		if (module.sound.on()) {
			// The PS5 style has its own two bells.
			if (UiSounds.on()) UiSounds.play(UiSounds.NOTIFY, shown ? 1.12f : 1f);
			else client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), shown ? 1.8f : 1.5f, shown ? 0.2f : 0.4f));
		}
	}

	/** Opens Messages over parent: at the conversation (and message) given, or the list. */
	public static void open(Screen parent, Conversation conversation, Message highlight) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (conversation != null) MessageBanners.clear(conversation);
		if (client.currentScreen instanceof MessagesScreen screen) {
			screen.focus(conversation, highlight);
			return;
		}
		client.setScreen(new MessagesScreen(parent, conversation, highlight));
	}

	/** The Open key: the conversation of the newest banner, else the latest with unread messages, else the list. */
	public static void openLatest(Screen parent) {
		if (service == null) return;
		MessageBanners.Banner banner = MessageBanners.newest();
		Conversation conversation = banner != null ? banner.conversation() : null;
		Message message = banner != null ? banner.message() : null;
		if (conversation == null) {
			for (Conversation c : service.conversations()) {
				if (c.unread() > 0) {
					conversation = c;
					break;
				}
			}
		}
		open(parent, conversation, message);
	}

	/** What the service needs of the game, for the account in use. */
	private static final class GameHost implements ChatService.Host {
		private final MinecraftClient client;
		private final String name;
		private final UUID uuid;
		private final String token;
		private final boolean premium;

		GameHost(MinecraftClient client, String name) {
			Session session = client.getSession();
			this.client = client;
			this.name = name;
			this.uuid = session.getUuidOrNull();
			this.token = session.getAccessToken();
			Accounts.Account current = Accounts.current();
			// The launcher's own login (a real token): offline accounts and dev sessions have none to prove anything with.
			this.premium = uuid != null && token != null && token.length() > 40 && (current == null || current.launcher());
		}

		@Override
		public String name() {
			return name;
		}

		@Override
		public UUID uuid() {
			return uuid;
		}

		@Override
		public boolean canVerify() {
			return premium;
		}

		@Override
		public CompletableFuture<Boolean> join(String serverId) {
			if (!premium) return CompletableFuture.completedFuture(false);
			JsonObject body = new JsonObject();
			body.addProperty("accessToken", token);
			body.addProperty("selectedProfile", uuid.toString().replace("-", ""));
			body.addProperty("serverId", serverId);
			HttpRequest request = HttpRequest.newBuilder(URI.create(SESSION_SERVER + "join")).timeout(Duration.ofSeconds(15))
				.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
			return HTTP.sendAsync(request, HttpResponse.BodyHandlers.discarding()).thenApply(r -> r.statusCode() == 204).exceptionally(e -> false);
		}

		@Override
		public CompletableFuture<UUID> hasJoined(String who, String serverId) {
			URI uri = URI.create(SESSION_SERVER + "hasJoined?username=" + URLEncoder.encode(who, StandardCharsets.UTF_8) + "&serverId="
				+ URLEncoder.encode(serverId, StandardCharsets.UTF_8));
			HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15)).GET().build();
			return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
				if (response.statusCode() != 200) return (UUID) null;
				JsonObject profile = JsonParser.parseString(response.body()).getAsJsonObject();
				String id = profile.get("id").getAsString();
				if (!profile.get("name").getAsString().equalsIgnoreCase(who) || id.length() != 32) return null;
				return UUID.fromString(id.substring(0, 8) + "-" + id.substring(8, 12) + "-" + id.substring(12, 16) + "-" + id.substring(16, 20)
					+ "-" + id.substring(20));
			}).exceptionally(e -> null);
		}

		@Override
		public Path file() {
			String key = uuid != null ? uuid.toString() : name.toLowerCase(Locale.ROOT);
			return FabricLoader.getInstance().getConfigDir().resolve(V1zir.MOD_ID).resolve("messages").resolve(key + ".json");
		}

		@Override
		public void post(Runnable task) {
			client.execute(task);
		}

		@Override
		public long now() {
			return System.currentTimeMillis();
		}
	}
}
