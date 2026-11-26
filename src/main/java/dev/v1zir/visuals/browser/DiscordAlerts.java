package dev.v1zir.visuals.browser;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.v1zir.visuals.V1zir;
import dev.v1zir.visuals.gui.messages.MessageBanners;
import net.dimaskama.mcef.api.MCEFBrowser;
import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.browser.CefMessageRouter;
import org.cef.callback.CefQueryCallback;
import org.cef.handler.CefMessageRouterHandlerAdapter;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Discord's messages in the game, without a bot or a token: Discord's own web app, logged in once in the browser, runs
 * in a hidden page in the background, and the notifications it would show (direct messages and mentions, as the
 * account's notification settings say) become banners in the game (MessageBanners), as apps such as Ferdium do. Nothing
 * is sent or read on the account's behalf; only the browser's notification object is replaced by one that hands each
 * notification to the mod (a JCEF message router, window.v1zirQuery). The unread count in Discord's title goes to
 * the browser's Discord button too.
 */
public final class DiscordAlerts {
	private static final String APP = "https://discord.com/app";
	/** The hidden page's size in pixels; it never shows, and draws once a second at most. */
	private static final int HIDDEN_W = 1280;
	private static final int HIDDEN_H = 800;
	/**
	 * The replacement notification object: permission granted, and each notification handed to the mod. Installed once
	 * per page (the app keeps its page and changes only its address while it is used).
	 */
	public static final String SHIM = "(function(){if(window.__v1zirAlerts)return;window.__v1zirAlerts=true;"
		+ "var send=function(o){try{if(typeof window.v1zirQuery==='function')window.v1zirQuery({request:JSON.stringify(o),"
		+ "onSuccess:function(){},onFailure:function(){}});}catch(e){}};"
		+ "var seq=0;var notes={};"
		+ "var N=function(title,options){options=options||{};var id=++seq;notes[id]=this;delete notes[id-40];"
		+ "this.title=title;this.body=options.body;this.onclick=null;this.onclose=null;this.onshow=null;this.onerror=null;"
		+ "send({kind:'note',id:id,title:String(title==null?'':title),"
		+ "body:String(options.body==null?'':options.body),icon:String(options.icon||'')});};"
		+ "N.permission='granted';N.maxActions=0;"
		+ "N.requestPermission=function(cb){try{if(cb)cb('granted');}catch(e){}return Promise.resolve('granted');};"
		+ "N.prototype.close=function(){};N.prototype.removeEventListener=function(){};"
		+ "N.prototype.addEventListener=function(t,f){if(t==='click')this.__click=f;};"
		// A click on the mod's banner: what a click on Discord's notification does (it opens that conversation).
		+ "window.__v1zirClick=function(id){var n=notes[id];if(!n)return;var ev={target:n,preventDefault:function(){}};"
		+ "try{if(typeof n.onclick==='function')n.onclick(ev);else if(n.__click)n.__click(ev);}catch(e){}};"
		+ "try{Object.defineProperty(window,'Notification',{value:N,writable:true,configurable:true});}catch(e){window.Notification=N;}"
		+ "var last=-1;var report=function(){var m=/^\\((\\d+)\\)/.exec(document.title||'');var n=m?parseInt(m[1],10):0;"
		+ "if(n!==last){last=n;send({kind:'unread',count:n});}};setInterval(report,3000);report();})();";

	/** A notification and whether the hidden page sent it (else the page the player sees). */
	private record Note(JsonObject json, boolean fromHidden) {
	}

	private static final ConcurrentLinkedQueue<Note> INBOX = new ConcurrentLinkedQueue<>();
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
		.followRedirects(HttpClient.Redirect.NORMAL).build();
	/** When each (title, text) last showed, so the same notification from two pages shows once. */
	private static final Map<String, Long> RECENT = new HashMap<>();

	@Nullable
	private static CefMessageRouter router;
	@Nullable
	private static volatile MCEFBrowser hidden;
	/** Ticks until the conversation a banner's click opened in the hidden page is opened in the player's too. */
	private static int follow;
	private static String hiddenUrl = "";
	private static int hiddenTicks;
	private static String mainUrl = "";
	private static int mainTicks;
	private static volatile int unread;
	/** How many notifications came in, for CI's check. */
	private static volatile int received;

	private DiscordAlerts() {
	}

	/** Unread direct messages and mentions in Discord, from its title; 0 when there are none or it is not open. */
	public static int unread() {
		return unread;
	}

	public static int received() {
		return received;
	}

	/**
	 * Gives MCEF's client the message router that notifications arrive by, before the first page is made (pages made
	 * before a router is added do not get its function). Called once MCEF is ready.
	 */
	static void prepare(CefClient client) {
		if (router != null) return;
		try {
			CefMessageRouter made = CefMessageRouter.create(new CefMessageRouter.CefMessageRouterConfig("v1zirQuery", "v1zirQueryCancel"),
				new CefMessageRouterHandlerAdapter() {
					@Override
					public boolean onQuery(CefBrowser browser, CefFrame frame, long queryId, String request, boolean persistent, CefQueryCallback callback) {
						try {
							if (request != null && request.length() < 16_384) {
								MCEFBrowser page = hidden;
								boolean fromHidden = page != null && page.getCefBrowser() == browser;
								INBOX.add(new Note(JsonParser.parseString(request).getAsJsonObject(), fromHidden));
							}
						} catch (RuntimeException ignored) {
							// Not ours, or not JSON.
						}
						callback.success("");
						return true;
					}
				});
			client.addMessageRouter(made);
			router = made;
		} catch (Throwable t) {
			V1zir.LOGGER.warn("Discord alerts are off: the browser's message router could not be added", t);
		}
	}

	/** Every client tick while the browser is on; wanted is whether Discord alerts are on. */
	public static void tick(boolean wanted) {
		if (!wanted || !Browser.ready() || router == null) {
			closeHidden();
			INBOX.clear();
			return;
		}
		if (hidden == null) {
			hidden = Browser.createHidden(APP, HIDDEN_W, HIDDEN_H);
			hiddenUrl = "";
			hiddenTicks = 0;
		}
		MCEFBrowser page = hidden;
		if (page != null) keepHidden(page);
		keepMain();
		drain();
		// After a banner's click the hidden page went to that conversation: the player's page follows it there.
		if (follow > 0 && --follow == 0 && hiddenUrl.contains("/channels/")) Browser.load(hiddenUrl);
	}

	/**
	 * A click on a Discord banner: Discord does what a click on its notification does (opens that conversation), in the
	 * page that sent it; the player's page follows when it was the hidden one.
	 */
	public static void open(MessageBanners.Banner banner) {
		int id = banner.discordId();
		if (id <= 0) return;
		String click = "window.__v1zirClick&&window.__v1zirClick(" + id + ");";
		MCEFBrowser page = hidden;
		if (banner.discordFromHidden() && page != null) {
			run(page.getCefBrowser(), click);
			follow = 30;
		} else {
			Browser.execute(click);
		}
	}

	/** The hidden page: the notification object replaced on every new page and every few seconds; logged in later on. */
	private static void keepHidden(MCEFBrowser page) {
		hiddenTicks++;
		String url;
		try {
			url = page.getCefBrowser().getURL();
		} catch (Throwable t) {
			return;
		}
		if (url == null) url = "";
		if (!url.equals(hiddenUrl) || hiddenTicks % 100 == 0) {
			hiddenUrl = url;
			run(page.getCefBrowser(), SHIM);
		}
		// Still at the login page: once the account is logged in in the browser, the hidden page reloads into the app.
		if (hiddenTicks % 600 == 0 && (url.contains("/login") || url.contains("/register"))) page.getCefBrowser().loadURL(APP);
	}

	/** The browser the player uses: on Discord it gets the same notification object, so both agree. */
	private static void keepMain() {
		mainTicks++;
		String url = Browser.url();
		if (!isDiscord(url)) {
			mainUrl = url;
			return;
		}
		if (!url.equals(mainUrl) || mainTicks % 100 == 0) {
			mainUrl = url;
			Browser.execute(SHIM);
		}
	}

	private static void run(CefBrowser page, String script) {
		try {
			page.executeJavaScript(script, page.getURL(), 0);
		} catch (Throwable t) {
			V1zir.LOGGER.debug("Discord script not run", t);
		}
	}

	private static boolean isDiscord(String url) {
		try {
			String host = URI.create(url).getHost();
			return host != null && (host.equals("discord.com") || host.endsWith(".discord.com"));
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	/** Notifications that came in become banners (on the render thread); the same one twice within a few seconds once. */
	private static void drain() {
		long now = System.currentTimeMillis();
		for (Iterator<Map.Entry<String, Long>> it = RECENT.entrySet().iterator(); it.hasNext(); ) {
			if (now - it.next().getValue() > 8_000L) it.remove();
		}
		Note in;
		while ((in = INBOX.poll()) != null) {
			JsonObject note = in.json();
			String kind = text(note, "kind");
			if ("unread".equals(kind)) {
				unread = note.has("count") ? Math.max(0, note.get("count").getAsInt()) : 0;
				continue;
			}
			if (!"note".equals(kind)) continue;
			String title = text(note, "title");
			String body = text(note, "body");
			if (title.isEmpty() && body.isEmpty()) continue;
			if (RECENT.putIfAbsent(title + "\n" + body, now) != null) continue;
			received++;
			MessageBanners.Banner banner = MessageBanners.pushDiscord(title, body);
			if (banner != null) banner.discordSource(note.has("id") ? note.get("id").getAsInt() : 0, in.fromHidden());
			String icon = text(note, "icon");
			if (banner != null && icon.startsWith("https://")) fetchIcon(banner, icon);
		}
	}

	/** The sender's picture for a banner, as PNG (Discord's CDN gives any picture as PNG). */
	private static void fetchIcon(MessageBanners.Banner banner, String icon) {
		String url = icon.replace(".webp", ".png").replace(".gif", ".png");
		if (!url.contains("size=")) url += (url.contains("?") ? "&" : "?") + "size=64";
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).GET().build();
			HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).thenAccept(response -> {
				byte[] bytes = response.body();
				if (response.statusCode() == 200 && bytes != null && bytes.length > 0 && bytes.length < 512 * 1024) banner.picture(bytes);
			});
		} catch (RuntimeException e) {
			V1zir.LOGGER.debug("Discord picture not fetched", e);
		}
	}

	private static String text(JsonObject o, String key) {
		return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : "";
	}

	private static void closeHidden() {
		MCEFBrowser page = hidden;
		if (page != null) {
			try {
				page.close();
			} catch (Throwable t) {
				V1zir.LOGGER.debug("Hidden Discord page did not close cleanly", t);
			}
			hidden = null;
		}
		unread = 0;
	}

	/** The browser is closing: the hidden page goes with it (the router stays with MCEF's client, which lives on). */
	public static void stop() {
		closeHidden();
		INBOX.clear();
	}
}
