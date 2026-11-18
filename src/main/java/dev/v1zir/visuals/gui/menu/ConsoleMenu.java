package dev.v1zir.visuals.gui.menu;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import dev.v1zir.visuals.V1zir;
import dev.v1zir.visuals.account.Accounts;
import dev.v1zir.visuals.chat.ChatService;
import dev.v1zir.visuals.chat.Conversation;
import dev.v1zir.visuals.chat.Message;
import dev.v1zir.visuals.chat.Messages;
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
 * Singleplayer and Multiplayer; Messages, the settings, the account and the clock at the top right. Under them a row
 * of tiles: the client's own (home) first, then the worlds, newest first, or the servers of the list, and last one
 * that opens the whole list. The tile picked is larger and ringed with light, its name under the row; under that,
 * large, what it is, and a white Play button. Two cards at the bottom right show Messages: the newest conversation and
 * who is online. It all stands on the loading screen's {@link Backdrop}, so the loading screen fades into it without a
 * seam.
 * <p>
 * The arrow keys (or the wheel) move along the row, Tab switches the tab, Enter plays. A click picks a tile, a click on
 * the picked one (or a double click) plays it.
 */
public final class ConsoleMenu extends Screen {
	private static final int HOME = 0;
	private static final int WORLD = 1;
	private static final int SERVER = 2;
	private static final int ALL = 3;
	private static final int MAX_TILES = 14;
	private static final int TEXT = 0xFFFFFFFF;
	private static final int DIM = 0xB8FFFFFF;
	private static final int FAINT = 0x80FFFFFF;
	private static final int INK = 0xFF0E1224;
	private static final int CARD = 0x990C1230;
	private static final int HOME_FROM = 0xFF6A4CFF;
	private static final int HOME_TO = 0xFF2F6BFF;
	private static final int RING = 0xFFFFFFFF;
	private static final int RING_GLOW = 0xFFA9C4FF;
	/** The tab shown last, kept for when the menu comes back. */
	private static int lastTab;

	private final List<List<Item>> tabs = new ArrayList<>();
	private final int[] selected = new int[2];
	private int tab;
	private final Anim tabAnim = new Anim(0f, 14f);
	private final Anim cursor = new Anim(0f, 12f);
	private final Anim scroll = new Anim(0f, 10f);
	private final Anim hero = new Anim(1f, 7f);
	private final Anim playHover = new Anim(0f, 20f);
	private final Anim moreHover = new Anim(0f, 20f);
	private final Anim[] iconHover = {new Anim(0f, 20f), new Anim(0f, 20f), new Anim(0f, 20f), new Anim(0f, 20f), new Anim(0f, 20f)};
	private final AccountsPanel accounts = new AccountsPanel();
	private long openedAt = -1L;
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
	private float cardsX;
	private float cardsY;
	private float cardsW;
	private float cardsH;

	public ConsoleMenu() {
		super(Text.literal(V1zir.NAME));
		tab = lastTab;
		tabAnim.snap(tab);
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
		if (openedAt < 0L) openedAt = System.nanoTime();
		icons.clear();
		icons.add(new TopIcon(Icons.POWER, () -> client.scheduleStop()));
		icons.add(new TopIcon(Icons.SETTINGS, () -> client.setScreen(new OptionsScreen(this, client.options))));
		if (FabricLoader.getInstance().isModLoaded("modmenu")) icons.add(new TopIcon(Icons.PUZZLE, this::openModMenu));
		icons.add(new TopIcon(Icons.MESSAGE_CIRCLE, () -> Messages.open(this, null, null)));
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
				Item item = new Item(SERVER, info.name, address, "Join", info.address, info.getFavicon());
				item.server = info;
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

	private void select(int index) {
		List<Item> items = items();
		if (items.isEmpty()) return;
		int next = Math.max(0, Math.min(items.size() - 1, index));
		if (next != selected[tab]) {
			selected[tab] = next;
			hero.snap(0f);
		}
	}

	private void switchTab(int next) {
		if (next == tab || next < 0 || next > 1) return;
		tab = next;
		lastTab = next;
		cursor.snap(selected[tab]);
		scroll.snap(scrollTarget());
		hero.snap(0f);
	}

	/** Plays the picked tile: its world, its server, the last one played (home) or the whole list. */
	private void play() {
		List<Item> items = items();
		if (items.isEmpty()) return;
		Item item = items.get(selected[tab]);
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
		if (tabs.isEmpty()) return;
		float t = openedAt < 0L ? 1f : (System.nanoTime() - openedAt) / 1.0e9f;
		float pad = Math.max(14f, width * 0.045f);
		renderTop(ctx, mouseX, mouseY, t, pad);
		float rowY = tabY + tabH + Math.max(12f, height * 0.06f);
		float rowBottom = renderRow(ctx, mouseX, mouseY, t, pad, rowY);
		renderHero(ctx, mouseX, mouseY, t, pad, rowBottom);
		renderCards(ctx, mouseX, mouseY, t, pad);
		accounts.render(ctx, client, Math.min(width - pad - AccountsPanel.WIDTH, avatarX), avatarY + avatarS + 6f, height, mouseX, mouseY);
	}

	private static float in(float t, float start, float length) {
		return Anim.easeOutCubic((t - start) / length);
	}

	/** The tabs at the left, the round icons, the account and the clock at the right. */
	private void renderTop(DrawContext ctx, int mouseX, int mouseY, float t, float pad) {
		float appear = in(t, 0.05f, 0.5f);
		SdfFont font = Fonts.medium();
		float size = 8f;
		tabH = 16f;
		tabY = 12f;
		float x = pad;
		float slide = tabAnim.target(tab).get();
		String[] names = {label("menu.singleplayer"), label("menu.multiplayer")};
		for (int i = 0; i < 2; i++) {
			tabW[i] = font.width(names[i], size) + 16f;
			tabX[i] = x;
			x += tabW[i] + 4f;
		}
		// The white pill slides from tab to tab.
		float pillX = tabX[0] + (tabX[1] - tabX[0]) * slide;
		float pillW = tabW[0] + (tabW[1] - tabW[0]) * slide;
		Render2D.round(ctx, pillX, tabY, pillW, tabH, tabH / 2f, ColorUtil.fade(0xFFFFFFFF, appear));
		for (int i = 0; i < 2; i++) {
			float on = 1f - Math.min(1f, Math.abs(slide - i));
			boolean over = Ui.inside(mouseX, mouseY, tabX[i], tabY, tabW[i], tabH);
			int color = ColorUtil.lerp(over ? TEXT : DIM, INK, on);
			Render2D.textMiddle(ctx, font, names[i], tabX[i] + 8f, tabY + tabH / 2f, size, ColorUtil.fade(color, appear));
		}

		// From the right: the clock, the account, then the round icons.
		String time = clock();
		float right = width - pad;
		float clockW = font.width(time, size);
		Render2D.textMiddle(ctx, font, time, right - clockW, tabY + tabH / 2f, size, ColorUtil.fade(TEXT, appear));
		avatarS = 15f;
		avatarX = right - clockW - 10f - avatarS;
		avatarY = tabY + (tabH - avatarS) / 2f;
		Accounts.Account account = Accounts.current();
		SkinTextures skin = account != null ? accounts.skin(client, account) : null;
		boolean overAvatar = Ui.inside(mouseX, mouseY, avatarX, avatarY, avatarS, avatarS);
		Render2D.circle(ctx, avatarX + avatarS / 2f, avatarY + avatarS / 2f, avatarS / 2f + 1.5f,
			ColorUtil.fade(overAvatar || accounts.shown() ? 0xFFFFFFFF : 0x33FFFFFF, appear));
		if (skin != null) Heads.skin(ctx, skin, avatarX, avatarY, avatarS, avatarS / 2f, ColorUtil.fade(0xFFFFFFFF, appear));
		else Render2D.icon(ctx, Icons.CIRCLE_USER, avatarX + avatarS / 2f, avatarY + avatarS / 2f, avatarS, ColorUtil.fade(TEXT, appear));
		float ix = avatarX - 12f;
		for (int i = 0; i < icons.size(); i++) {
			TopIcon icon = icons.get(icons.size() - 1 - i);
			float r = 8f;
			float cx = ix - r;
			float cy = tabY + tabH / 2f;
			icon.cx = cx;
			icon.cy = cy;
			icon.r = r;
			boolean over = (mouseX - cx) * (mouseX - cx) + (mouseY - cy) * (mouseY - cy) <= r * r;
			float hv = iconHover[Math.min(i, iconHover.length - 1)].target(over ? 1f : 0f).get();
			if (hv > 0.01f) Render2D.circle(ctx, cx, cy, r, ColorUtil.withAlpha(0xFFFFFF, Math.round(46 * hv * appear)));
			Render2D.icon(ctx, icon.icon, cx, cy, 9f, ColorUtil.fade(ColorUtil.lerp(DIM, TEXT, hv), appear));
			if (icon.icon == Icons.MESSAGE_CIRCLE) MessagesUi.badge(ctx, cx + 5.5f, cy - 5f, Messages.unread());
			ix = cx - r - 4f;
		}
	}

	/** The row of tiles; returns where it ends below. */
	private float renderRow(DrawContext ctx, int mouseX, int mouseY, float t, float pad, float y) {
		List<Item> items = items();
		float s = Math.max(26f, Math.min(52f, height * 0.15f));
		float big = s * 1.36f;
		float gap = s * 0.17f;
		float at = cursor.target(selected[tab]).get();
		float offset = scroll.target(scrollTarget()).get();
		tileBounds.clear();
		float x = pad - offset;
		for (int i = 0; i < items.size(); i++) {
			float focus = Math.max(0f, 1f - Math.abs(i - at));
			float size = s + (big - s) * focus;
			float e = in(t, 0.12f + i * 0.045f, 0.55f);
			float dx = (1f - e) * 40f;
			if (e > 0.01f && x + dx < width && x + size + dx > 0f) tile(ctx, items.get(i), x + dx, y, size, focus, e, Ui.inside(mouseX, mouseY, x, y, size, size));
			tileBounds.add(new float[]{x, y, size, size});
			x += size + gap;
		}
		// The picked tile's name under the row, from its left edge.
		float[] picked = tileBounds.get(Math.min(selected[tab], tileBounds.size() - 1));
		Item item = items.get(selected[tab]);
		float labelY = y + big + 9f;
		float appear = in(t, 0.4f, 0.5f) * hero.target(1f).get();
		SdfFont font = Fonts.medium();
		float lx = picked[0];
		if (item.kind == HOME) {
			SdfFont bold = Fonts.bold();
			String tag = "V1ZIR";
			float tw = bold.width(tag, 5.6f) + 8f;
			Render2D.round(ctx, lx, labelY - 5f, tw, 10f, 5f, ColorUtil.fade(0xFFFFFFFF, appear));
			Render2D.textMiddle(ctx, bold, tag, lx + 4f, labelY, 5.6f, ColorUtil.fade(INK, appear));
			lx += tw + 5f;
			Render2D.textMiddle(ctx, font, "Home", lx, labelY, 8f, ColorUtil.fade(TEXT, appear));
		} else {
			Render2D.textMiddle(ctx, font, item.title, lx, labelY, 8f, ColorUtil.fade(TEXT, appear));
		}
		return labelY + 8f;
	}

	/** How far the row scrolls so the picked tile is in view. */
	private float scrollTarget() {
		List<Item> items = items();
		float s = Math.max(26f, Math.min(52f, height * 0.15f));
		float big = s * 1.36f;
		float gap = s * 0.17f;
		float pad = Math.max(14f, width * 0.045f);
		int picked = selected[tab];
		float end = pad + picked * (s + gap) + big;
		float total = pad + Math.max(0, items.size() - 1) * (s + gap) + big + pad;
		float over = end - (width - pad - s * 1.5f);
		return Math.max(0f, Math.min(over, Math.max(0f, total - width)));
	}

	/** One tile: its picture (the logo for home, the world's or server's icon, a symbol), ringed with light when picked. */
	private void tile(DrawContext ctx, Item item, float x, float y, float size, float focus, float alpha, boolean hovered) {
		float r = size * 0.17f;
		Render2D.shadow(ctx, x, y + 2f, size, size, r, size * 0.25f, ColorUtil.withAlpha(0x000000, Math.round((70 + 60 * focus) * alpha)));
		if (focus > 0.01f) {
			Render2D.glow(ctx, x - 3f, y - 3f, size + 6f, size + 6f, r + 3f, 10f * focus, ColorUtil.fade(RING_GLOW, 0.45f * focus * alpha),
				ColorUtil.fade(Backdrop.PS5_BEAM, 0.35f * focus * alpha));
		}
		TextureSetup texture = item.texture();
		if (item.kind == HOME) {
			Render2D.round(ctx, x, y, size, size, r, ColorUtil.fade(HOME_FROM, alpha), ColorUtil.fade(HOME_TO, alpha), ColorUtil.fade(HOME_TO, alpha),
				ColorUtil.fade(HOME_FROM, alpha));
			float inset = size * 0.2f;
			Render2D.setAlpha(alpha);
			Logo.draw(ctx, x + inset, y + inset, size - inset * 2f);
			Render2D.setAlpha(1f);
		} else if (texture != null) {
			Render2D.image(ctx, texture, x, y, size, size, r, ColorUtil.fade(0xFFFFFFFF, alpha));
		} else {
			int from = item.kind == SERVER ? 0xFF1E2B5C : item.kind == ALL ? 0xFF151B38 : 0xFF213A3A;
			int to = item.kind == SERVER ? 0xFF2D4FA8 : item.kind == ALL ? 0xFF222B52 : 0xFF2F6B5A;
			Render2D.round(ctx, x, y, size, size, r, ColorUtil.fade(from, alpha), ColorUtil.fade(from, alpha), ColorUtil.fade(to, alpha), ColorUtil.fade(to, alpha));
			int icon = item.kind == SERVER ? Icons.SERVER : item.kind == ALL ? Icons.LAYOUT_GRID : Icons.MOUNTAIN;
			Render2D.icon(ctx, icon, x + size / 2f, y + size / 2f, size * 0.38f, ColorUtil.fade(0xE6FFFFFF, alpha));
		}
		if (hovered && focus < 0.99f) Render2D.round(ctx, x, y, size, size, r, ColorUtil.withAlpha(0xFFFFFF, Math.round(26 * alpha)));
		if (focus > 0.01f) Render2D.outline(ctx, x - 2.5f, y - 2.5f, size + 5f, size + 5f, r + 2.5f, 1.4f, ColorUtil.fade(RING, focus * alpha));
	}

	/** What the picked tile is, large, and its Play button with the more button beside it. */
	private void renderHero(DrawContext ctx, int mouseX, int mouseY, float t, float pad, float top) {
		Item item = items().get(selected[tab]);
		float e = Anim.easeOutCubic(hero.target(1f).get()) * in(t, 0.35f, 0.6f);
		float dx = (1f - e) * 10f;
		float x = pad + dx;
		float y = top + Math.max(10f, height * 0.06f);
		SdfFont bold = Fonts.bold();
		SdfFont font = Fonts.medium();
		float titleSize = Math.max(13f, Math.min(24f, height * 0.075f));
		Render2D.text(ctx, bold, item.title, x, y, titleSize, ColorUtil.fade(TEXT, e));
		y += titleSize + 5f;
		Render2D.text(ctx, font, item.subtitle, x, y, 7.5f, ColorUtil.fade(DIM, e));
		y += 18f;
		playH = 19f;
		playW = Fonts.semibold().width(item.action, 8f) + 34f;
		playX = x;
		playY = y;
		boolean overPlay = Ui.inside(mouseX, mouseY, playX, playY, playW, playH);
		float hv = playHover.target(overPlay ? 1f : 0f).get();
		Render2D.shadow(ctx, playX, playY + 2f, playW, playH, playH / 2f, 8f, ColorUtil.withAlpha(0x000000, Math.round(90 * e)));
		if (hv > 0.01f) Render2D.glow(ctx, playX, playY, playW, playH, playH / 2f, 8f * hv, ColorUtil.fade(RING_GLOW, 0.5f * hv * e), ColorUtil.fade(RING_GLOW, 0.5f * hv * e));
		Render2D.round(ctx, playX, playY, playW, playH, playH / 2f, ColorUtil.fade(ColorUtil.lerp(0xFFF2F4FA, 0xFFFFFFFF, hv), e));
		int icon = item.kind == SERVER ? Icons.SERVER : item.kind == ALL ? Icons.LIST : Icons.PLAY;
		Render2D.icon(ctx, icon, playX + 12f, playY + playH / 2f, 8f, ColorUtil.fade(INK, e));
		Render2D.textMiddle(ctx, Fonts.semibold(), item.action, playX + 21f, playY + playH / 2f, 8f, ColorUtil.fade(INK, e));
		moreR = playH / 2f;
		moreX = playX + playW + 6f + moreR;
		moreY = playY + moreR;
		boolean overMore = (mouseX - moreX) * (mouseX - moreX) + (mouseY - moreY) * (mouseY - moreY) <= moreR * moreR;
		float mh = moreHover.target(overMore ? 1f : 0f).get();
		Render2D.circle(ctx, moreX, moreY, moreR, ColorUtil.withAlpha(0xFFFFFF, Math.round((40 + 40 * mh) * e)));
		Render2D.icon(ctx, Icons.ELLIPSIS, moreX, moreY, 9f, ColorUtil.fade(TEXT, e));
	}

	/** Messages at the bottom right: the newest conversation, and who is online. */
	private void renderCards(DrawContext ctx, int mouseX, int mouseY, float t, float pad) {
		ChatService service = Messages.service();
		if (service == null) {
			cardsW = 0f;
			return;
		}
		float e = in(t, 0.5f, 0.6f);
		float w = Math.min(132f, width * 0.24f);
		float h = 40f;
		float gap = 6f;
		cardsW = w * 2f + gap;
		cardsH = h;
		cardsX = width - pad - cardsW;
		cardsY = height - pad - h;
		Conversation newest = null;
		int online = 0;
		StringBuilder names = new StringBuilder();
		for (Conversation c : service.conversations()) {
			if (newest == null || c.activity() > newest.activity()) newest = c;
			if (c.contact().online()) {
				online++;
				if (names.length() < 40) names.append(names.length() > 0 ? ", " : "").append(c.contact().name());
			}
		}
		String latest = "No conversations yet";
		if (newest != null) {
			Message last = newest.last();
			latest = last != null ? (last.out() ? "You: " : newest.contact().name() + ": ") + last.text() : newest.contact().name();
		}
		card(ctx, cardsX, cardsY, w, h, Icons.USERS, "Online", online > 0 ? names.toString() : "Nobody is online", online, 0xFF4CD787, e,
			Ui.inside(mouseX, mouseY, cardsX, cardsY, w, h));
		card(ctx, cardsX + w + gap, cardsY, w, h, Icons.MESSAGE_CIRCLE, "Messages", latest, Messages.unread(), Backdrop.PS5_BEAM_2, e,
			Ui.inside(mouseX, mouseY, cardsX + w + gap, cardsY, w, h));
	}

	private void card(DrawContext ctx, float x, float y, float w, float h, int icon, String title, String line, int count, int dot, float alpha,
					  boolean hovered) {
		float r = 8f;
		Render2D.shadow(ctx, x, y + 2f, w, h, r, 10f, ColorUtil.withAlpha(0x000000, Math.round(80 * alpha)));
		Render2D.round(ctx, x, y, w, h, r, ColorUtil.fade(hovered ? 0xB3182046 : CARD, alpha));
		Render2D.outline(ctx, x, y, w, h, r, 1f, ColorUtil.withAlpha(0xFFFFFF, Math.round((hovered ? 60 : 28) * alpha)));
		SdfFont font = Fonts.medium();
		Render2D.icon(ctx, icon, x + 12f, y + 12f, 8f, ColorUtil.fade(TEXT, alpha));
		Render2D.textMiddle(ctx, Fonts.semibold(), title, x + 21f, y + 12f, 7f, ColorUtil.fade(TEXT, alpha));
		if (count > 0) {
			String n = String.valueOf(count);
			float nw = font.width(n, 6.5f);
			Render2D.circle(ctx, x + w - 14f - nw, y + 12f, 2.2f, ColorUtil.fade(dot, alpha));
			Render2D.textMiddle(ctx, font, n, x + w - 9f - nw, y + 12f, 6.5f, ColorUtil.fade(TEXT, alpha));
		}
		String text = fit(font, line, 6.5f, w - 20f);
		Render2D.textMiddle(ctx, font, text, x + 10f, y + 28f, 6.5f, ColorUtil.fade(FAINT, alpha));
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
		if (accounts.shown()) {
			if (!accounts.mouseClicked(client, mx, my)) accounts.close();
			return true;
		}
		if (Ui.inside(mx, my, avatarX, avatarY, avatarS, avatarS)) {
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
		if (Ui.inside(mx, my, playX, playY, playW, playH)) {
			play();
			return true;
		}
		if ((mx - moreX) * (mx - moreX) + (my - moreY) * (my - moreY) <= moreR * moreR) {
			openList();
			return true;
		}
		if (cardsW > 0f && Ui.inside(mx, my, cardsX, cardsY, cardsW, cardsH)) {
			client.setScreen(new MessagesScreen(this, null, null));
			return true;
		}
		return super.mouseClicked(click, doubled);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (accounts.mouseScrolled(mouseX, mouseY, verticalAmount)) return true;
		double amount = verticalAmount != 0 ? verticalAmount : -horizontalAmount;
		if (amount != 0) select(selected[tab] + (amount < 0 ? 1 : -1));
		return true;
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		if (accounts.keyPressed(client, input.key(), input.modifiers())) return true;
		int key = input.key();
		switch (key) {
			case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_A -> select(selected[tab] - 1);
			case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_D -> select(selected[tab] + 1);
			case GLFW.GLFW_KEY_TAB, GLFW.GLFW_KEY_Q, GLFW.GLFW_KEY_E -> switchTab(1 - tab);
			case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> play();
			case GLFW.GLFW_KEY_HOME -> select(0);
			default -> {
				return super.keyPressed(input);
			}
		}
		return true;
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
				release();
			}
			return setup;
		}

		void release() {
			if (backing != null) backing.close();
			backing = null;
			setup = null;
		}
	}
}
