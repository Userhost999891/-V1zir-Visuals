package dev.v1zir.visuals.gui.menu;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import dev.v1zir.visuals.V1zir;
import dev.v1zir.visuals.account.Accounts;
import dev.v1zir.visuals.chat.ChatService;
import dev.v1zir.visuals.chat.Conversation;
import dev.v1zir.visuals.chat.Message;
import dev.v1zir.visuals.chat.Messages;
import dev.v1zir.visuals.gui.UiSounds;
import dev.v1zir.visuals.gui.clickgui.Ui;
import dev.v1zir.visuals.gui.loading.Backdrop;
import dev.v1zir.visuals.gui.messages.MessagesScreen;
import dev.v1zir.visuals.gui.messages.MessagesUi;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.impl.client.MainMenuModule;
import dev.v1zir.visuals.module.impl.client.ThemeModule;
import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.font.Icons;
import dev.v1zir.visuals.render.font.SdfFont;
import dev.v1zir.visuals.render.ui.Heads;
import dev.v1zir.visuals.render.ui.Logo;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.ServerList;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * The main menu with the PS5 style theme, laid out like the home screen of a console. Two tabs at the top left,
 * Singleplayer and Multiplayer, the open one a white pill; the icons, the account and the clock at the top right. Under
 * them a row of tiles: the client's own (home) first, then the worlds, newest first, or the servers of the list, and
 * last a dark one that opens the whole list. The tile picked is much larger, framed in lavender light, and stays near
 * the left edge as the row scrolls under it, one tile before it still in view; its name sits under the smaller tiles
 * beside it, after a small tag.
 * <p>
 * A world or a server picked fills the screen with its picture, as a console shows a game's art: the client takes one
 * while it is played ({@link MenuShots}); the pictures cross-fade as the pick moves and drift slowly closer. Without a
 * picture it stands on the loading screen's starry {@link Backdrop} with its icon large at the right. Over it, its
 * name, large, what it is, and a white Play pill with a round "..." beside it for more (a small menu). Home shows cards
 * at the bottom right instead: Messages, what was played last (to continue it) and who is online.
 * <p>
 * The arrow keys (or the wheel) move along the row, down goes to the buttons or the cards and up back to the row, Tab
 * switches the tab, Enter plays. A click picks a tile, a click on the picked one (or a double click) plays it.
 */
public final class ConsoleMenu extends Screen {
	private static final int HOME = 0;
	private static final int WORLD = 1;
	private static final int SERVER = 2;
	private static final int ALL = 3;
	private static final int MAX_TILES = 14;
	/** Where the keys are: the row, or the first or second button (Play and "..."; on home, the cards). */
	private static final int ROW = 0;
	private static final int TEXT = 0xFFFFFFFF;
	private static final int DIM = 0xB3FFFFFF;
	private static final int FAINT = 0x80FFFFFF;
	private static final int INK = 0xFF120F1E;
	private static final int CARD = 0x8C2A2440;
	private static final int CARD_HOT = 0xB3393056;
	private static final int CHIP = 0x2EFFFFFF;
	private static final int HOME_FROM = 0xFFA587F5;
	private static final int HOME_TO = 0xFF6A4BCF;
	private static final int FRAME = 0x995B45B5;
	private static final int RING = 0xFFD9CCFF;
	private static final int RING_GLOW = 0xFF9C7CFF;
	private static final int ONLINE = 0xFF3DDC84;
	private static final int MESSAGE_DOT = 0xFF4C8DFF;
	/** The tab shown last, kept for when the menu comes back. */
	private static int lastTab;

	private final List<List<Item>> tabs = new ArrayList<>();
	private final int[] selected = new int[2];
	private int tab;
	private int focus = ROW;
	private final Anim pillX = new Anim(0f, 14f);
	private final Anim pillW = new Anim(0f, 14f);
	private final Anim cursor = new Anim(0f, 12f);
	private final Anim scroll = new Anim(0f, 10f);
	private final Anim hero = new Anim(1f, 6f);
	private final Anim tabSwap = new Anim(1f, 7f);
	private final Anim[] buttonHover = {new Anim(0f, 20f), new Anim(0f, 20f), new Anim(0f, 20f)};
	private final Anim[] iconHover = {new Anim(0f, 20f), new Anim(0f, 20f), new Anim(0f, 20f), new Anim(0f, 20f), new Anim(0f, 20f)};
	private final AccountsPanel accounts = new AccountsPanel();
	private final ConsolePopup popup = new ConsolePopup();
	/** Pictures shown, the one picked last at the end (on top); those under it go once it covers them. */
	private final List<Item> art = new ArrayList<>();
	private int swapDirection = 1;
	private long openedAt = -1L;
	private boolean pillPlaced;
	/** Bounds as last drawn, for the mouse. */
	private final float[] tabX = new float[2];
	private final float[] tabW = new float[2];
	private float tabY;
	private float tabH;
	private final List<float[]> tileBounds = new ArrayList<>();
	private float playX;
	private float playY;
	private float playW;
	private float playH;
	private float moreX;
	private float moreY;
	private float moreR;
	private final List<TopIcon> icons = new ArrayList<>();
	private float avatarX;
	private float avatarY;
	private float avatarS;
	/** The home cards as last drawn: Activity, Messages, Online (x, y, w, h each). */
	private final float[][] cards = new float[3][4];
	private boolean cardsShown;

	public ConsoleMenu() {
		super(Text.literal(V1zir.NAME));
		tab = lastTab;
	}

	@Override
	protected void init() {
		if (!Theme.ps5()) {
			// The style theme was changed back: the client's own menu.
			client.setScreen(MainMenus.create());
			return;
		}
		Accounts.init(client);
		release();
		tabs.clear();
		tabs.add(load(false));
		tabs.add(load(true));
		for (int i = 0; i < 2; i++) selected[i] = Math.min(selected[i], tabs.get(i).size() - 1);
		cursor.snap(selected[tab]);
		scroll.snap(scrollTarget());
		if (openedAt < 0L) openedAt = System.nanoTime();
		icons.clear();
		icons.add(new TopIcon(Icons.POWER, () -> client.scheduleStop()));
		icons.add(new TopIcon(Icons.SETTINGS, () -> client.setScreen(new OptionsScreen(this, client.options))));
		if (FabricLoader.getInstance().isModLoaded("modmenu")) icons.add(new TopIcon(Icons.PUZZLE, this::openModMenu));
		icons.add(new TopIcon(Icons.MESSAGE_CIRCLE, () -> Messages.open(this, null, null)));
		pillPlaced = false;
		showArt();
	}

	private void openModMenu() {
		try {
			Class<?> screen = Class.forName("com.terraformersmc.modmenu.gui.ModsScreen");
			client.setScreen((Screen) screen.getConstructor(Screen.class).newInstance(this));
		} catch (ReflectiveOperationException | ClassCastException e) {
			V1zir.LOGGER.warn("Could not open Mod Menu", e);
		}
	}

	// ------------------------------------------------------------------ the tiles

	private List<Item> load(boolean servers) {
		List<Item> items = new ArrayList<>();
		String user = client.getSession().getUsername();
		boolean last = LastPlayed.available(client);
		items.add(new Item(HOME, V1zir.NAME, last ? "Continue: " + LastPlayed.label() : "Welcome, " + user, last ? "Continue" : "Play", null, null));
		if (servers) loadServers(items);
		else loadWorlds(items);
		items.add(new Item(ALL, servers ? "Server list" : "All worlds", servers ? "Add, edit and join servers" : "Create, edit and open your worlds",
			"Open", null, null));
		return items;
	}

	private record Save(Path folder, long played) {
	}

	/** The worlds in the saves folder, the one played last first (by its level.dat), with their icons. */
	private void loadWorlds(List<Item> items) {
		List<Save> saves = new ArrayList<>();
		try (Stream<Path> folders = Files.list(client.getLevelStorage().getSavesDirectory())) {
			for (Path folder : (Iterable<Path>) folders::iterator) {
				Path level = folder.resolve("level.dat");
				try {
					if (Files.isRegularFile(level)) saves.add(new Save(folder, Files.getLastModifiedTime(level).toMillis()));
				} catch (IOException | RuntimeException ignored) {
					// A world that cannot be read is left out; the full list still shows it.
				}
			}
		} catch (IOException | RuntimeException e) {
			V1zir.LOGGER.debug("Could not list the worlds", e);
		}
		saves.sort((a, b) -> Long.compare(b.played, a.played));
		for (Save save : saves) {
			if (items.size() > MAX_TILES) break;
			String name = save.folder.getFileName().toString();
			byte[] icon = null;
			try {
				Path file = save.folder.resolve("icon.png");
				if (Files.isRegularFile(file) && Files.size(file) < 1_048_576L) icon = Files.readAllBytes(file);
			} catch (IOException | RuntimeException ignored) {
				// No icon: the tile shows a symbol.
			}
			Item item = new Item(WORLD, name, "Played " + ago(save.played), "Play", name, icon);
			item.pictureKey = MenuShots.worldKey(name);
			items.add(item);
		}
	}

	/** The servers of the list, in its order, with their icons. */
	private void loadServers(List<Item> items) {
		try {
			ServerList list = new ServerList(client);
			list.loadFile();
			for (int i = 0; i < list.size() && items.size() <= MAX_TILES; i++) {
				ServerInfo info = list.get(i);
				if (info == null || info.isRealm()) continue;
				String address = info.address.strip();
				if (address.endsWith(":25565")) address = address.substring(0, address.length() - 6);
				Item item = new Item(SERVER, info.name, "IP: " + address, "Join", info.address, info.getFavicon());
				item.server = info;
				item.pictureKey = MenuShots.serverKey(info.address);
				items.add(item);
			}
		} catch (RuntimeException e) {
			V1zir.LOGGER.debug("Could not read the server list", e);
		}
	}

	private static String ago(long time) {
		long minutes = Math.max(0L, (System.currentTimeMillis() - time) / 60_000L);
		if (minutes < 1L) return "just now";
		if (minutes < 60L) return minutes == 1L ? "a minute ago" : minutes + " minutes ago";
		long hours = minutes / 60L;
		if (hours < 24L) return hours == 1L ? "an hour ago" : hours + " hours ago";
		long days = hours / 24L;
		if (days < 2L) return "yesterday";
		if (days < 30L) return days + " days ago";
		long months = days / 30L;
		return months < 12L ? (months == 1L ? "a month ago" : months + " months ago") : "over a year ago";
	}

	private List<Item> items() {
		return tabs.isEmpty() ? List.of() : tabs.get(tab);
	}

	@Nullable
	private Item picked() {
		List<Item> items = items();
		return items.isEmpty() ? null : items.get(Math.min(selected[tab], items.size() - 1));
	}

	private void select(int index) {
		List<Item> items = items();
		if (items.isEmpty()) return;
		int next = Math.max(0, Math.min(items.size() - 1, index));
		if (next != selected[tab]) {
			selected[tab] = next;
			hero.snap(0f);
			focus = ROW;
			UiSounds.play(UiSounds.MOVE);
			showArt();
		}
	}

	private void switchTab(int next) {
		if (next == tab || next < 0 || next > 1) return;
		UiSounds.play(UiSounds.TAB);
		swapDirection = next > tab ? 1 : -1;
		tab = next;
		lastTab = next;
		focus = ROW;
		cursor.snap(selected[tab]);
		scroll.snap(scrollTarget());
		hero.snap(0f);
		tabSwap.snap(0f);
		showArt();
	}

	/** Puts the picked tile's picture on top of those shown, starting to read it if it was not yet. */
	private void showArt() {
		Item item = picked();
		if (item == null || item.pictureKey == null) return;
		if (item.picture == null && !item.pictureChecked) {
			item.pictureChecked = true;
			item.picture = MenuShots.load(item.pictureKey);
		}
		if (item.picture == null) return;
		if (!art.isEmpty() && art.get(art.size() - 1) == item) return;
		// On top again, it fades in over what is shown now.
		art.remove(item);
		item.artAlpha.snap(0f);
		art.add(item);
		item.shownAt = System.nanoTime();
	}

	/** Plays the picked tile: its world, its server, the last one played (home) or the whole list. */
	private void play() {
		Item item = picked();
		if (item == null) return;
		UiSounds.play(UiSounds.CONFIRM);
		switch (item.kind) {
			case WORLD -> client.createIntegratedServerLoader().start(item.target, () -> client.setScreen(this));
			case SERVER -> {
				ServerInfo info = item.server != null ? item.server : new ServerInfo(item.title, item.target, ServerInfo.ServerType.OTHER);
				ConnectScreen.connect(this, client, ServerAddress.parse(item.target), info, false, null);
			}
			case HOME -> {
				if (LastPlayed.available(client)) LastPlayed.join(client, this);
				else openList();
			}
			default -> openList();
		}
	}

	private void openList() {
		client.setScreen(tab == 1 ? new MultiplayerScreen(this) : new SelectWorldScreen(this));
	}

	/** The small menu of the picked tile's "...". */
	private void more() {
		Item item = picked();
		if (item == null) return;
		List<ConsolePopup.Choice> choices = new ArrayList<>();
		switch (item.kind) {
			case HOME -> {
				if (LastPlayed.available(client)) choices.add(new ConsolePopup.Choice("Continue " + LastPlayed.label(), Icons.PLAY, this::play));
				choices.add(new ConsolePopup.Choice("All worlds", Icons.MOUNTAIN, () -> client.setScreen(new SelectWorldScreen(this))));
				choices.add(new ConsolePopup.Choice("Server list", Icons.SERVER, () -> client.setScreen(new MultiplayerScreen(this))));
				choices.add(new ConsolePopup.Choice("Options", Icons.SETTINGS, () -> client.setScreen(new OptionsScreen(this, client.options))));
				if (FabricLoader.getInstance().isModLoaded("modmenu")) choices.add(new ConsolePopup.Choice("Mods", Icons.PUZZLE, this::openModMenu));
				choices.add(new ConsolePopup.Choice("Quit game", Icons.POWER, () -> client.scheduleStop()));
			}
			case WORLD, SERVER -> {
				choices.add(new ConsolePopup.Choice(item.action, item.kind == SERVER ? Icons.SERVER : Icons.PLAY, this::play));
				if (item.kind == SERVER) {
					choices.add(new ConsolePopup.Choice("Copy address", Icons.LIST, () -> client.keyboard.setClipboard(item.target)));
					choices.add(new ConsolePopup.Choice("Edit in the server list", Icons.PENCIL, () -> client.setScreen(new MultiplayerScreen(this))));
				} else {
					choices.add(new ConsolePopup.Choice("Edit in all worlds", Icons.PENCIL, () -> client.setScreen(new SelectWorldScreen(this))));
				}
				if (item.picture != null) choices.add(new ConsolePopup.Choice("Remove picture", Icons.TRASH_2, () -> removePicture(item)));
			}
			default -> {
				choices.add(new ConsolePopup.Choice(item.action, Icons.LIST, this::openList));
				choices.add(new ConsolePopup.Choice("Options", Icons.SETTINGS, () -> client.setScreen(new OptionsScreen(this, client.options))));
			}
		}
		UiSounds.play(UiSounds.CONFIRM);
		popup.show(item.kind == HOME ? V1zir.NAME : item.title, choices);
	}

	private void removePicture(Item item) {
		if (item.pictureKey != null) MenuShots.remove(item.pictureKey);
		art.remove(item);
		if (item.picture != null) item.picture.release();
		item.picture = null;
	}

	// ------------------------------------------------------------------ drawing

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
		// The backdrop is drawn in render().
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float deltaTicks) {
		ThemeModule theme = ModuleManager.get(ThemeModule.class);
		if (theme != null) theme.apply();
		float scale = (float) client.getWindow().getScaleFactor();
		Matrix3x2fStack m = ctx.getMatrices();
		m.pushMatrix();
		m.scale(1f / scale, 1f / scale);
		Backdrop.ps5(ctx, client.getWindow().getFramebufferWidth(), client.getWindow().getFramebufferHeight(), scale, 1f, 1f, 1f);
		m.popMatrix();
		Item item = picked();
		if (item == null) return;
		float t = openedAt < 0L ? 1f : (System.nanoTime() - openedAt) / 1.0e9f;
		boolean blocked = popup.shown() || accounts.shown();
		int mx = blocked ? -1000 : mouseX, my = blocked ? -1000 : mouseY;
		float pictured = renderArt(ctx);
		float pad = Math.max(14f, width * 0.033f);
		renderTop(ctx, mx, my, t, pad);
		float rowTop = tabY + tabH + Math.max(8f, height * 0.075f);
		renderRow(ctx, mx, my, t, pad, rowTop, pictured);
		if (item.kind == HOME) {
			cardsShown = true;
			renderCards(ctx, mx, my, t);
		} else {
			cardsShown = false;
			renderHero(ctx, item, mx, my, t, pad, pictured);
		}
		popup.render(ctx, width, height, mouseX, mouseY);
		accounts.render(ctx, client, Math.min(width - pad - AccountsPanel.WIDTH, avatarX), avatarY + avatarS + 6f, height, mouseX, mouseY);
	}

	/**
	 * The pictures of the worlds and servers picked, over the whole screen, the newest on top fading in and drifting
	 * slowly closer, and the shade over them that keeps the words readable. Returns how much a picture covers the
	 * backdrop (0 to 1).
	 */
	private float renderArt(DrawContext ctx) {
		Item picked = picked();
		// While the tile picked has a picture, the ones under it stay until it has faded in over them; else all fade out.
		boolean keep = picked != null && picked.picture != null && !picked.picture.failed() && !art.isEmpty() && art.get(art.size() - 1) == picked;
		float top = 0f;
		for (int i = 0; i < art.size(); i++) {
			Item item = art.get(i);
			boolean newest = i == art.size() - 1;
			TextureSetup texture = item.picture != null ? item.picture.setup() : null;
			float a = item.artAlpha.target(keep && (!newest || texture != null) ? 1f : 0f).get();
			if (texture == null || a <= 0.005f) continue;
			float since = (System.nanoTime() - item.shownAt) / 1.0e9f;
			float zoom = 1.02f + 0.05f * Anim.easeOutCubic(Math.min(1f, since / 24f));
			float aspect = item.picture.aspect();
			float w = Math.max(width, height * aspect) * zoom;
			float h = w / aspect;
			Render2D.image(ctx, texture, (width - w) / 2f, (height - h) / 2f, w, h, 0f, ColorUtil.fade(0xFFFFFFFF, a));
			if (newest) top = a;
		}
		// Those under a picture fully shown are covered: let them go.
		if (top >= 0.995f) while (art.size() > 1) art.remove(0).artAlpha.snap(0f);
		float shade = 0f;
		for (Item item : art) shade = Math.max(shade, item.artAlpha.get());
		if (shade > 0.005f) {
			Render2D.gradientH(ctx, 0f, 0f, width * 0.7f, height, 0f, ColorUtil.withAlpha(0x07050E, Math.round(210 * shade)), 0x0007050E);
			Render2D.gradientV(ctx, 0f, 0f, width, height * 0.42f, 0f, ColorUtil.withAlpha(0x07050E, Math.round(170 * shade)), 0x0007050E);
			Render2D.gradientV(ctx, 0f, height * 0.5f, width, height * 0.5f, 0f, 0x0007050E, ColorUtil.withAlpha(0x07050E, Math.round(200 * shade)));
		}
		return shade;
	}

	private static float in(float t, float start, float length) {
		return Anim.easeOutCubic((t - start) / length);
	}

	/** The tabs at the left, the open one in a white pill; the round icons, the account and the clock at the right. */
	private void renderTop(DrawContext ctx, int mouseX, int mouseY, float t, float pad) {
		float appear = in(t, 0.05f, 0.45f);
		SdfFont font = Fonts.medium();
		float size = Math.max(7f, Math.min(12f, height * 0.031f));
		tabH = Math.max(13f, Math.min(26f, height * 0.066f));
		float centerY = Math.max(14f, height * 0.12f);
		tabY = centerY - tabH / 2f;
		float inner = tabH * 0.55f;
		String[] names = {label("menu.singleplayer"), label("menu.multiplayer")};
		float x = pad - inner;
		for (int i = 0; i < 2; i++) {
			tabW[i] = font.width(names[i], size) + inner * 2f;
			tabX[i] = x;
			x += tabW[i] + 4f;
		}
		if (!pillPlaced) {
			pillPlaced = true;
			pillX.snap(tabX[tab]);
			pillW.snap(tabW[tab]);
		}
		float px = pillX.target(tabX[tab]).get(), pw = pillW.target(tabW[tab]).get();
		Render2D.shadow(ctx, px, tabY + 1f, pw, tabH, tabH / 2f, 6f, ColorUtil.withAlpha(0x000000, Math.round(80 * appear)));
		Render2D.round(ctx, px, tabY, pw, tabH, tabH / 2f, ColorUtil.fade(0xFFFFFFFF, appear));
		for (int i = 0; i < 2; i++) {
			// Over the pill the words are dark, beside it white.
			float overlap = Math.max(0f, Math.min(px + pw, tabX[i] + tabW[i]) - Math.max(px, tabX[i])) / tabW[i];
			boolean over = Ui.inside(mouseX, mouseY, tabX[i], tabY, tabW[i], tabH);
			int beside = over ? TEXT : 0xD9FFFFFF;
			int color = ColorUtil.lerp(beside, INK, Anim.clamp01(overlap));
			Render2D.textMiddle(ctx, font, names[i], tabX[i] + inner, centerY, size, ColorUtil.fade(color, appear));
		}

		// From the right: the clock, the account, then the round icons.
		String time = clock();
		float right = width - pad;
		float clockW = font.width(time, size);
		Render2D.textMiddle(ctx, font, time, right - clockW, centerY, size, Render2D.TextStyle.of(ColorUtil.fade(TEXT, appear)).shadow(0x66000000));
		avatarS = Math.max(12f, Math.min(22f, height * 0.055f));
		avatarX = right - clockW - size * 1.4f - avatarS;
		avatarY = centerY - avatarS / 2f;
		Accounts.Account account = Accounts.current();
		SkinTextures skin = account != null ? accounts.skin(client, account) : null;
		boolean overAvatar = Ui.inside(mouseX, mouseY, avatarX, avatarY, avatarS, avatarS);
		Render2D.circle(ctx, avatarX + avatarS / 2f, avatarY + avatarS / 2f, avatarS / 2f + 1.5f,
			ColorUtil.fade(overAvatar || accounts.shown() ? 0xFFFFFFFF : 0x33FFFFFF, appear));
		if (skin != null) Heads.skin(ctx, skin, avatarX, avatarY, avatarS, avatarS / 2f, ColorUtil.fade(0xFFFFFFFF, appear));
		else Render2D.icon(ctx, Icons.CIRCLE_USER, avatarX + avatarS / 2f, avatarY + avatarS / 2f, avatarS, ColorUtil.fade(TEXT, appear));
		float ix = avatarX - size * 1.2f;
		float r = Math.max(7f, size * 1.05f);
		for (int i = 0; i < icons.size(); i++) {
			TopIcon icon = icons.get(icons.size() - 1 - i);
			float cx = ix - r;
			icon.cx = cx;
			icon.cy = centerY;
			icon.r = r;
			boolean over = (mouseX - cx) * (mouseX - cx) + (mouseY - centerY) * (mouseY - centerY) <= r * r;
			float hv = iconHover[Math.min(i, iconHover.length - 1)].target(over ? 1f : 0f).get();
			if (hv > 0.01f) Render2D.circle(ctx, cx, centerY, r, ColorUtil.withAlpha(0xFFFFFF, Math.round(46 * hv * appear)));
			Render2D.icon(ctx, icon.icon, cx, centerY, size * 1.15f, ColorUtil.fade(ColorUtil.lerp(0xE6FFFFFF, TEXT, hv), appear));
			if (icon.icon == Icons.MESSAGE_CIRCLE) MessagesUi.badge(ctx, cx + r * 0.65f, centerY - r * 0.6f, Messages.unread());
			ix = cx - r - 2f;
		}
	}

	private float small() {
		return Math.max(24f, Math.min(72f, height * 0.13f));
	}

	private float big() {
		return small() * 1.69f;
	}

	private float gap() {
		return Math.max(3f, height * 0.015f);
	}

	/** How far the row scrolls: the picked tile near the left edge, one small tile before it still in view. */
	private float scrollTarget() {
		int picked = selected[tab];
		return picked == 0 ? 0f : picked * (small() + gap()) - small() * 0.95f;
	}

	/** The row of tiles, and the picked one's name under the small tiles beside it. */
	private void renderRow(DrawContext ctx, int mouseX, int mouseY, float t, float pad, float top, float pictured) {
		List<Item> items = items();
		float s = small();
		float b = big();
		float gap = gap();
		float at = cursor.target(selected[tab]).get();
		float offset = scroll.target(scrollTarget()).get();
		float swap = Anim.easeOutCubic(tabSwap.target(1f).get());
		float swapDx = (1f - swap) * 26f * swapDirection;
		// The picked tile grows into place after the row has come in.
		float grow = in(t, 0.3f, 0.45f);
		tileBounds.clear();
		float x = pad - offset + swapDx;
		float labelX = 0f;
		for (int i = 0; i < items.size(); i++) {
			float focus = Math.max(0f, 1f - Math.abs(i - at)) * grow;
			float size = s + (b - s) * focus;
			// The smaller tiles hang from near the top of the picked one.
			float ty = top + (1f - focus) * b * 0.05f;
			float e = in(t, 0.1f + i * 0.04f, 0.5f) * swap;
			float dx = (1f - in(t, 0.1f + i * 0.04f, 0.5f)) * 34f;
			boolean hovered = Ui.inside(mouseX, mouseY, x, ty, size, size);
			if (e > 0.01f && x + dx < width && x + size + dx > -size) {
				tile(ctx, items.get(i), x + dx, ty, size, focus, e, hovered, pictured);
			}
			tileBounds.add(new float[]{x, ty, size, size});
			if (i == Math.round(at)) labelX = x + size + gap;
			x += size + gap;
		}
		Item picked = items.get(selected[tab]);
		float labelY = top + b * 0.05f + s + (b - s - b * 0.05f) / 2f + b * 0.04f;
		float appear = in(t, 0.5f, 0.35f) * Anim.easeOutCubic(hero.target(1f).get()) * swap;
		name(ctx, picked, labelX + s * 0.08f + (1f - appear) * 6f, labelY, appear, pictured);
	}

	/** The picked tile's name, under the row: a small white tag, then the name. */
	private void name(DrawContext ctx, Item item, float x, float cy, float alpha, float pictured) {
		if (alpha <= 0.01f) return;
		SdfFont bold = Fonts.bold();
		SdfFont font = Fonts.medium();
		float tagSize = Math.max(5f, Math.min(9f, height * 0.021f));
		float nameSize = Math.max(8f, Math.min(16f, height * 0.04f));
		float tagH = tagSize * 1.85f;
		String tag = switch (item.kind) {
			case HOME -> "V1ZIR";
			case WORLD -> "WORLD";
			case SERVER -> "SERVER";
			default -> "";
		};
		if (!tag.isEmpty()) {
			float tw = bold.width(tag, tagSize) + tagH;
			Render2D.round(ctx, x, cy - tagH / 2f, tw, tagH, tagH / 2f, ColorUtil.fade(0xFFFFFFFF, alpha));
			Render2D.textMiddle(ctx, bold, tag, x + tagH / 2f, cy, tagSize, ColorUtil.fade(INK, alpha));
			x += tw + tagSize * 0.8f;
		}
		String text = item.kind == HOME ? "Home" : item.title;
		Render2D.textMiddle(ctx, font, fit(font, text, nameSize, width * 0.42f), x, cy, nameSize,
			Render2D.TextStyle.of(ColorUtil.fade(TEXT, alpha)).shadow(ColorUtil.withAlpha(0x000000, Math.round(110 * pictured * alpha))));
	}

	/** One tile: its picture (the logo for home, the world's or server's icon, a symbol); framed in light when picked. */
	private void tile(DrawContext ctx, Item item, float x, float y, float size, float focus, float alpha, boolean hovered, float pictured) {
		float r = size * 0.16f;
		Render2D.shadow(ctx, x, y + 2f, size, size, r, size * 0.22f, ColorUtil.withAlpha(0x000000, Math.round((60 + 70 * focus + 40 * pictured) * alpha)));
		if (focus > 0.01f) {
			Render2D.glow(ctx, x - 2f, y - 2f, size + 4f, size + 4f, r + 2f, 12f * focus, ColorUtil.fade(RING_GLOW, 0.5f * focus * alpha),
				ColorUtil.fade(RING_GLOW, 0.3f * focus * alpha));
		}
		TextureSetup texture = item.texture();
		// Picked, the picture sits in a frame of light a little inside the tile's edge.
		float inset = size * 0.075f * focus;
		float ir = Math.max(2f, r - inset * 0.6f);
		if (item.kind == HOME) {
			Render2D.round(ctx, x, y, size, size, r, ColorUtil.fade(HOME_FROM, alpha), ColorUtil.fade(HOME_FROM, alpha), ColorUtil.fade(HOME_TO, alpha),
				ColorUtil.fade(HOME_TO, alpha));
			Render2D.gradientV(ctx, x, y, size, size * 0.55f, r, ColorUtil.withAlpha(0xFFFFFF, Math.round(46 * alpha)), 0x00FFFFFF);
			float logo = size * 0.22f;
			Render2D.setAlpha(alpha);
			Logo.draw(ctx, x + logo, y + logo, size - logo * 2f);
			Render2D.setAlpha(1f);
		} else if (item.kind == ALL) {
			Render2D.round(ctx, x, y, size, size, r, ColorUtil.withAlpha(0xFFFFFF, Math.round((hovered ? 52 : 34) * alpha)));
			Render2D.icon(ctx, Icons.LAYOUT_GRID, x + size / 2f, y + size / 2f, size * 0.3f, ColorUtil.fade(0x8CFFFFFF, alpha * (0.4f + 0.6f * focus)));
		} else if (texture != null) {
			if (focus > 0.01f) Render2D.round(ctx, x, y, size, size, r, ColorUtil.fade(FRAME, focus * alpha));
			Render2D.image(ctx, texture, x + inset, y + inset, size - inset * 2f, size - inset * 2f, ir, ColorUtil.fade(0xFFFFFFFF, alpha));
		} else {
			int from = item.kind == SERVER ? 0xFF2A2152 : 0xFF213A3A;
			int to = item.kind == SERVER ? 0xFF4A3A9C : 0xFF2F6B5A;
			Render2D.round(ctx, x, y, size, size, r, ColorUtil.fade(from, alpha), ColorUtil.fade(from, alpha), ColorUtil.fade(to, alpha), ColorUtil.fade(to, alpha));
			int icon = item.kind == SERVER ? Icons.SERVER : Icons.MOUNTAIN;
			Render2D.icon(ctx, icon, x + size / 2f, y + size / 2f, size * 0.38f, ColorUtil.fade(0xE6FFFFFF, alpha));
		}
		if (hovered && focus < 0.5f) Render2D.round(ctx, x, y, size, size, r, ColorUtil.withAlpha(0xFFFFFF, Math.round(26 * alpha)));
		// The ring dims while the keys are on the buttons under the row.
		if (focus > 0.01f) Render2D.outline(ctx, x, y, size, size, r, Math.max(1.5f, size * 0.028f), ColorUtil.fade(RING, focus * alpha * (this.focus == ROW ? 1f : 0.55f)));
	}

	/**
	 * What the picked tile is, large: its name, what it is and a chip, and its Play pill with the round "..." beside it;
	 * with no picture behind it, its icon large at the right.
	 */
	private void renderHero(DrawContext ctx, Item item, int mouseX, int mouseY, float t, float pad, float pictured) {
		float base = Anim.easeOutCubic(hero.target(1f).get()) * in(t, 0.45f, 0.45f) * Anim.easeOutCubic(tabSwap.get());
		if (base <= 0.01f) {
			playW = 0f;
			moreR = 0f;
			return;
		}
		SdfFont bold = Fonts.bold();
		SdfFont font = Fonts.medium();
		int shadow = ColorUtil.withAlpha(0x000000, Math.round(130 * Math.max(pictured, 0.4f)));
		float x = pad;
		float titleSize = Math.max(12f, Math.min(30f, height * 0.055f));
		float e = stagger(base, 0f);
		Render2D.textMiddle(ctx, bold, fit(bold, item.title, titleSize, width * 0.6f), x, height * 0.57f + (1f - e) * 8f, titleSize,
			Render2D.TextStyle.of(ColorUtil.fade(TEXT, e)).shadow(ColorUtil.fade(shadow, e)));
		e = stagger(base, 0.12f);
		float subSize = Math.max(6.5f, Math.min(14f, height * 0.028f));
		Render2D.textMiddle(ctx, font, item.subtitle, x, height * 0.635f + (1f - e) * 8f, subSize,
			Render2D.TextStyle.of(ColorUtil.fade(DIM, e)).shadow(ColorUtil.fade(shadow, e)));
		String chip = chip(item);
		if (!chip.isEmpty()) {
			e = stagger(base, 0.22f);
			float chipH = Math.max(12f, Math.min(28f, height * 0.066f));
			float chipSize = Math.max(6f, Math.min(12f, height * 0.025f));
			float cw = font.width(chip, chipSize) + chipH * 1.35f;
			float cy = height * 0.68f + (1f - e) * 8f;
			Render2D.round(ctx, x, cy, cw, chipH, chipH * 0.3f, ColorUtil.fade(CHIP, e));
			Render2D.circle(ctx, x + chipH * 0.45f, cy + chipH / 2f, chipH * 0.12f, ColorUtil.fade(item.kind == SERVER ? ONLINE : RING, e));
			Render2D.textMiddle(ctx, font, chip, x + chipH * 0.75f, cy + chipH / 2f, chipSize, ColorUtil.fade(0xE6FFFFFF, e));
		}
		// The buttons.
		e = stagger(base, 0.3f);
		playH = Math.max(16f, Math.min(44f, height * 0.093f));
		SdfFont semibold = Fonts.semibold();
		float playSize = Math.max(7f, Math.min(15f, height * 0.034f));
		playW = Math.max(width * 0.2f, semibold.width(item.action, playSize) + playH * 2.2f);
		playX = x;
		playY = Math.min(height * 0.85f, height - height * 0.05f - playH) + (1f - e) * 8f;
		boolean overPlay = Ui.inside(mouseX, mouseY, playX, playY, playW, playH);
		float hv = buttonHover[0].target(overPlay || focus == 1 ? 1f : 0f).get();
		Render2D.shadow(ctx, playX, playY + 2f, playW, playH, playH / 2f, 9f, ColorUtil.withAlpha(0x000000, Math.round(110 * e)));
		if (hv > 0.01f) {
			// Picked, the pill gets a ring of its own a little outside it, as a console marks what the keys are on.
			float o = playH * 0.12f;
			Render2D.outline(ctx, playX - o, playY - o, playW + o * 2f, playH + o * 2f, playH / 2f + o, 1.6f, ColorUtil.fade(0xFFFFFFFF, hv * e));
		}
		Render2D.round(ctx, playX, playY, playW, playH, playH / 2f, ColorUtil.fade(0xFFFFFFFF, e));
		int icon = item.kind == SERVER ? Icons.SERVER : item.kind == ALL ? Icons.LIST : Icons.PLAY;
		float labelW = semibold.width(item.action, playSize);
		float contentW = labelW + playSize * 1.6f;
		float lx = playX + (playW - contentW) / 2f;
		Render2D.textMiddle(ctx, semibold, item.action, lx, playY + playH / 2f, playSize, ColorUtil.fade(INK, e));
		Render2D.icon(ctx, icon, lx + labelW + playSize * 1.0f, playY + playH / 2f, playSize * 1.05f, ColorUtil.fade(INK, e));
		moreR = playH / 2f;
		moreX = playX + playW + playH * 0.35f + moreR;
		moreY = playY + moreR;
		boolean overMore = (mouseX - moreX) * (mouseX - moreX) + (mouseY - moreY) * (mouseY - moreY) <= moreR * moreR;
		float mh = buttonHover[1].target(overMore || focus == 2 ? 1f : 0f).get();
		Render2D.circle(ctx, moreX, moreY, moreR, ColorUtil.fade(ColorUtil.lerp(0x38FFFFFF, 0xFFFFFFFF, mh), e));
		Render2D.icon(ctx, Icons.ELLIPSIS, moreX, moreY, playSize * 1.25f, ColorUtil.fade(ColorUtil.lerp(TEXT, INK, mh), e));

		// No picture: the icon, large, at the right.
		float iconAlpha = stagger(base, 0.18f) * (1f - pictured);
		if (iconAlpha > 0.01f) {
			float size = height * 0.31f;
			float ix = Math.min(width * 0.76f, width - pad - size);
			float iy = height * 0.70f - size / 2f;
			float grow = 0.92f + 0.08f * iconAlpha;
			float cx = ix + size / 2f, cy = iy + size / 2f;
			size *= grow;
			ix = cx - size / 2f;
			iy = cy - size / 2f;
			float r = size * 0.12f;
			Render2D.shadow(ctx, ix, iy + 4f, size, size, r, 18f, ColorUtil.withAlpha(0x000000, Math.round(120 * iconAlpha)));
			Render2D.round(ctx, ix, iy, size, size, r, ColorUtil.fade(0x33FFFFFF, iconAlpha));
			TextureSetup texture = item.texture();
			float inset = size * 0.035f;
			if (texture != null) {
				Render2D.image(ctx, texture, ix + inset, iy + inset, size - inset * 2f, size - inset * 2f, r - inset, ColorUtil.fade(0xFFFFFFFF, iconAlpha));
			} else {
				int symbol = item.kind == SERVER ? Icons.SERVER : item.kind == ALL ? Icons.LAYOUT_GRID : Icons.MOUNTAIN;
				Render2D.icon(ctx, symbol, cx, cy, size * 0.34f, ColorUtil.fade(0xCCFFFFFF, iconAlpha));
			}
		}
	}

	/** A part of the hero coming in after the others: 0 to 1 from base (0 to 1) and how late it is. */
	private static float stagger(float base, float delay) {
		return Anim.easeOutCubic(Anim.clamp01((base - delay) / (1f - delay)));
	}

	/** The chip under the name: a server's last visit (when its picture was taken), or what a world is. */
	private String chip(Item item) {
		if (item.kind == WORLD) return "Singleplayer";
		if (item.kind != SERVER) return "";
		if (item.pictureKey != null) {
			try {
				Path file = MenuShots.file(item.pictureKey);
				if (Files.isRegularFile(file)) return "Played " + ago(Files.getLastModifiedTime(file).toMillis());
			} catch (IOException ignored) {
				// No time to show.
			}
		}
		return "Multiplayer";
	}

	/**
	 * Home's cards at the bottom right, as a console's widgets: Messages (the newest conversation, the unread count by a
	 * blue dot), what was played last (to continue it) and who is online (by a green dot).
	 */
	private void renderCards(DrawContext ctx, int mouseX, int mouseY, float t) {
		ChatService service = Messages.service();
		Conversation newest = null;
		int online = 0;
		StringBuilder names = new StringBuilder();
		if (service != null) {
			for (Conversation c : service.conversations()) {
				if (newest == null || c.activity() > newest.activity()) newest = c;
				if (c.contact().online()) {
					online++;
					if (names.length() < 40) names.append(names.length() > 0 ? ", " : "").append(c.contact().name());
				}
			}
		}
		String latest = service == null ? "Messages are off" : "No conversations yet";
		if (newest != null) {
			Message last = newest.last();
			latest = last != null ? (last.out() ? "You: " : newest.contact().name() + ": ") + last.text() : newest.contact().name();
		}
		boolean last = LastPlayed.available(client);
		String activity = last ? "Continue " + LastPlayed.label() + (LastPlayed.server && LastPlayed.ping > 0 ? "  ·  " + LastPlayed.ping + " ms" : "")
			: "Nothing played yet";
		float w = width, h = height;
		float bottom = h * 0.97f;
		set(cards[1], w * 0.636f, h * 0.586f, w * 0.336f, h * 0.2f);
		set(cards[0], w * 0.196f, h * 0.785f, w * 0.427f, bottom - h * 0.785f);
		set(cards[2], w * 0.636f, h * 0.816f, w * 0.336f, bottom - h * 0.816f);
		card(ctx, cards[1], 0.6f, t, Icons.MESSAGE_CIRCLE, "Messages", latest, Messages.unread(), MESSAGE_DOT, true, mouseX, mouseY, focus == 2);
		card(ctx, cards[0], 0.66f, t, Icons.GAMEPAD_2, "Recent activity", activity, 0, 0, false, mouseX, mouseY, focus == 1);
		card(ctx, cards[2], 0.72f, t, Icons.USERS, "Online", online > 0 ? names.toString() : "Nobody is online", online, ONLINE, true, mouseX, mouseY,
			focus == 3);
	}

	private static void set(float[] bounds, float x, float y, float w, float h) {
		bounds[0] = x;
		bounds[1] = y;
		bounds[2] = w;
		bounds[3] = h;
	}

	private void card(DrawContext ctx, float[] b, float delay, float t, int icon, String title, String line, int count, int dot, boolean showCount,
					  int mouseX, int mouseY, boolean keyed) {
		float e = in(t, delay, 0.5f) * Anim.easeOutCubic(hero.target(1f).get()) * Anim.easeOutCubic(tabSwap.get());
		if (e <= 0.01f) return;
		float x = b[0], y = b[1] + (1f - e) * 12f, w = b[2], h = b[3];
		boolean hovered = Ui.inside(mouseX, mouseY, x, y, w, h) || keyed;
		float r = Math.max(8f, Math.min(18f, height * 0.025f));
		Render2D.shadow(ctx, x, y + 3f, w, h, r, 14f, ColorUtil.withAlpha(0x000000, Math.round(90 * e)));
		Render2D.round(ctx, x, y, w, h, r, ColorUtil.fade(hovered ? CARD_HOT : CARD, e));
		Render2D.gradientV(ctx, x, y, w, h, r, ColorUtil.withAlpha(0xFFFFFF, Math.round(16 * e)), 0x00FFFFFF);
		if (keyed) Render2D.outline(ctx, x, y, w, h, r, 1.5f, ColorUtil.fade(RING, e));
		SdfFont font = Fonts.medium();
		float titleSize = Math.max(7f, Math.min(13f, height * 0.03f));
		float lineSize = Math.max(6f, Math.min(11f, height * 0.026f));
		float rowY = y + Math.max(9f, height * 0.046f);
		float lx = x + Math.max(8f, height * 0.028f);
		Render2D.icon(ctx, icon, lx + titleSize * 0.5f, rowY, titleSize * 1.05f, ColorUtil.fade(TEXT, e));
		Render2D.textMiddle(ctx, font, title, lx + titleSize * 1.5f, rowY, titleSize, ColorUtil.fade(TEXT, e));
		if (showCount) {
			String n = String.valueOf(count);
			float nw = Fonts.semibold().width(n, titleSize);
			float rx = x + w - Math.max(8f, height * 0.03f);
			Render2D.textMiddle(ctx, Fonts.semibold(), n, rx - nw, rowY, titleSize, ColorUtil.fade(TEXT, e));
			Render2D.circle(ctx, rx - nw - titleSize * 0.75f, rowY, titleSize * 0.36f, ColorUtil.fade(dot, e));
		}
		float lineY = y + Math.max(20f, height * 0.133f);
		Render2D.textMiddle(ctx, font, fit(font, line, lineSize, w - (lx - x) * 2f), lx, Math.min(lineY, y + h - lineSize), lineSize, ColorUtil.fade(FAINT, e));
	}

	private static String fit(SdfFont font, String text, float size, float width) {
		if (font.width(text, size) <= width) return text;
		String dots = "…";
		int n = text.length();
		while (n > 0 && font.width(text.substring(0, n) + dots, size) > width) n--;
		return text.substring(0, n).strip() + dots;
	}

	private static String label(String key) {
		String text = Text.translatable(key).getString().strip();
		while (text.endsWith(".") || text.endsWith("…")) text = text.substring(0, text.length() - 1).strip();
		return text;
	}

	private static String clock() {
		MainMenuModule module = ModuleManager.get(MainMenuModule.class);
		boolean twelve = module != null && module.timeFormat.is("12-hour");
		return LocalTime.now().format(DateTimeFormatter.ofPattern(twelve ? "h:mm" : "HH:mm", Locale.ROOT));
	}

	// ------------------------------------------------------------------ input

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseClicked(click, doubled);
		double mx = click.x(), my = click.y();
		if (popup.mouseClicked(mx, my)) return true;
		if (accounts.shown()) {
			if (!accounts.mouseClicked(client, mx, my)) accounts.close();
			return true;
		}
		if (Ui.inside(mx, my, avatarX, avatarY, avatarS, avatarS)) {
			UiSounds.play(accounts.shown() ? UiSounds.BACK : UiSounds.TAB);
			accounts.toggle();
			return true;
		}
		for (int i = 0; i < 2; i++) {
			if (Ui.inside(mx, my, tabX[i], tabY, tabW[i], tabH)) {
				switchTab(i);
				return true;
			}
		}
		for (TopIcon icon : icons) {
			if ((mx - icon.cx) * (mx - icon.cx) + (my - icon.cy) * (my - icon.cy) <= icon.r * icon.r) {
				UiSounds.play(UiSounds.CONFIRM);
				icon.action.run();
				return true;
			}
		}
		for (int i = 0; i < tileBounds.size(); i++) {
			float[] b = tileBounds.get(i);
			if (Ui.inside(mx, my, b[0], b[1], b[2], b[3])) {
				if (i == selected[tab] || doubled) play();
				else select(i);
				return true;
			}
		}
		Item item = picked();
		if (item != null && item.kind != HOME) {
			if (playW > 0f && Ui.inside(mx, my, playX, playY, playW, playH)) {
				play();
				return true;
			}
			if (moreR > 0f && (mx - moreX) * (mx - moreX) + (my - moreY) * (my - moreY) <= moreR * moreR) {
				more();
				return true;
			}
		}
		if (cardsShown) {
			for (int i = 0; i < 3; i++) {
				float[] b = cards[i];
				if (Ui.inside(mx, my, b[0], b[1], b[2], b[3])) {
					useCard(i);
					return true;
				}
			}
		}
		return super.mouseClicked(click, doubled);
	}

	/** A home card: 0 continues what was played last, 1 opens Messages, 2 opens Messages on who is online. */
	private void useCard(int card) {
		if (card == 0) {
			play();
			return;
		}
		UiSounds.play(UiSounds.CONFIRM);
		client.setScreen(new MessagesScreen(this, null, null));
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (popup.shown()) return true;
		if (accounts.mouseScrolled(mouseX, mouseY, verticalAmount)) return true;
		double amount = verticalAmount != 0 ? verticalAmount : -horizontalAmount;
		if (amount != 0) select(selected[tab] + (amount < 0 ? 1 : -1));
		return true;
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		if (popup.keyPressed(input.key())) return true;
		if (accounts.keyPressed(client, input.key(), input.modifiers())) return true;
		// While the accounts panel is open, letters go into its name field.
		if (accounts.shown()) return super.keyPressed(input);
		Item item = picked();
		boolean home = item != null && item.kind == HOME;
		int buttons = home ? 3 : 2;
		int key = input.key();
		switch (key) {
			case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_A -> {
				if (focus == ROW) select(selected[tab] - 1);
				else moveFocus(Math.max(1, focus - 1));
			}
			case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_D -> {
				if (focus == ROW) select(selected[tab] + 1);
				else moveFocus(Math.min(buttons, focus + 1));
			}
			case GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_S -> {
				if (focus == ROW) moveFocus(1);
			}
			case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_W -> {
				if (focus != ROW) moveFocus(ROW);
			}
			case GLFW.GLFW_KEY_TAB, GLFW.GLFW_KEY_Q, GLFW.GLFW_KEY_E -> switchTab(1 - tab);
			case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> {
				if (focus == ROW || focus == 1 && !home) play();
				else if (home) useCard(focus == 1 ? 0 : focus == 2 ? 1 : 2);
				else more();
			}
			case GLFW.GLFW_KEY_HOME -> select(0);
			default -> {
				return super.keyPressed(input);
			}
		}
		return true;
	}

	private void moveFocus(int next) {
		if (next == focus) return;
		focus = next;
		UiSounds.play(UiSounds.MOVE);
	}

	@Override
	public boolean charTyped(CharInput input) {
		if (input.isValidChar() && accounts.charTyped(input.asString())) return true;
		return super.charTyped(input);
	}

	@Override
	public void removed() {
		release();
		// Coming back to the menu plays its short intro again.
		openedAt = -1L;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	private void release() {
		for (List<Item> items : tabs) for (Item item : items) item.release();
		art.clear();
	}

	/** A round icon at the top right. */
	private static final class TopIcon {
		final int icon;
		final Runnable action;
		float cx;
		float cy;
		float r;

		TopIcon(int icon, Runnable action) {
			this.icon = icon;
			this.action = action;
		}
	}

	/** A tile: what it is, the words under it, what its button says, the world's folder or server's address, and its icon. */
	private static final class Item {
		final int kind;
		final String title;
		final String subtitle;
		final String action;
		@Nullable
		final String target;
		@Nullable
		private byte[] icon;
		@Nullable
		ServerInfo server;
		/** The name its picture goes by (MenuShots), null for tiles that have none. */
		@Nullable
		String pictureKey;
		@Nullable
		MenuShots.Picture picture;
		boolean pictureChecked;
		final Anim artAlpha = new Anim(0f, 6f);
		long shownAt;
		@Nullable
		private NativeImageBackedTexture backing;
		@Nullable
		private TextureSetup setup;

		Item(int kind, String title, String subtitle, String action, @Nullable String target, @Nullable byte[] icon) {
			this.kind = kind;
			this.title = title;
			this.subtitle = subtitle;
			this.action = action;
			this.target = target;
			this.icon = icon;
		}

		/** The icon as a texture, uploaded when first drawn; null when there is none or it cannot be read. */
		@Nullable
		TextureSetup texture() {
			if (setup != null || icon == null) return setup;
			byte[] bytes = icon;
			icon = null;
			NativeImage image = null;
			try {
				image = NativeImage.read(bytes);
				backing = new NativeImageBackedTexture(() -> "V1zir menu tile", image);
				setup = TextureSetup.of(backing.getGlTextureView(), RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
			} catch (IOException | RuntimeException e) {
				if (backing == null && image != null) image.close();
				releaseIcon();
			}
			return setup;
		}

		private void releaseIcon() {
			if (backing != null) backing.close();
			backing = null;
			setup = null;
		}

		void release() {
			releaseIcon();
			if (picture != null) picture.release();
			picture = null;
			pictureChecked = false;
		}
	}
}
