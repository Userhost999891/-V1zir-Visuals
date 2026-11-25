package dev.v1zir.visuals.gui.messages;

import dev.v1zir.visuals.chat.Emojis;
import dev.v1zir.visuals.chat.ChatService;
import dev.v1zir.visuals.chat.Contact;
import dev.v1zir.visuals.chat.Conversation;
import dev.v1zir.visuals.chat.Message;
import dev.v1zir.visuals.chat.Messages;
import dev.v1zir.visuals.controller.Targets;
import dev.v1zir.visuals.gui.clickgui.Ui;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.impl.client.ClickGuiModule;
import dev.v1zir.visuals.module.impl.client.ThemeModule;
import dev.v1zir.visuals.render.BackdropBlur;
import dev.v1zir.visuals.render.V1zirPipelines;
import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.font.Icons;
import dev.v1zir.visuals.render.font.SdfFont;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.render.ui.ShapeElement;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Messages, in the click GUI's glass: the conversations on the left (with their last message, unread count and who
 * is online), the open one on the right as bubbles grouped by sender and day, with the time and the delivery state
 * under each group (a clock while it waits, one check with the relay, two once their game has it, two in the accent
 * once read), "typing..." while they type, and the field to write in at the bottom. Opened from a notification, it
 * shows that conversation scrolled to the message, which glows for a moment.
 */
public final class MessagesScreen extends Screen {
	private static final float SIDE = 158f;
	private static final float ROW = 34f;
	private static final float HEADER = 38f;
	private static final float FIELD_H = 24f;
	/** How much larger Messages are than the click GUI, room allowing. */
	private static final float ZOOM = 1.3f;
	/** The bar of this account's own profile at the bottom of the list. */
	private static final float PROFILE_H = 30f;
	private static final float TEXT = 6.8f;
	private static final float LINE = 9.4f;
	private static final float PAD_X = 7.5f;
	private static final float PAD_Y = 5.5f;
	private static final float BUBBLE_RADIUS = 8f;
	private static final long GROUP_MS = 5L * 60_000L;
	private static final int GREEN = 0xFF4ADE80;
	private static final int AMBER = 0xFFFBBF24;
	private static final int RED = 0xFFF87171;
	private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);

	private final Screen parent;
	private Conversation selected;
	private Message highlight;
	private long highlightAt;
	private boolean scrollToHighlight;
	private String draft = "";
	/** The emoji list over the field while a :name is typed. */
	private final EmojiSuggestions emojiList = new EmojiSuggestions();
	/** The field for a new conversation's name has the keyboard. */
	private boolean naming;
	private String newName = "";
	private String nameError = "";
	private long nameErrorUntil;
	/** The profile editor (this account's name and picture for Messages) is open, or asked to open. */
	private boolean editing;
	private boolean editAsked;
	private String editName = "";
	@Nullable
	private byte[] editPicture;
	private final Anim editor = new Anim(0f, 14f);
	private float profileY;
	private float edX;
	private float edY;
	private float edW;
	private float edH;
	private float changeX;
	private float changeY;
	private float changeW;
	private float headY;
	private float saveX;
	private float saveY;
	private float saveW;
	private float cancelX;
	private float cancelW;
	private final Anim open = new Anim(0f, 9f);
	private final Anim nameField = new Anim(0f, 14f);
	private final Anim send = new Anim(0f, 16f);
	private float listScroll;
	/** How far the messages are scrolled up from the newest, in window units. */
	private float msgScroll;
	private float msgScrollShown;

	private float winX;
	private float winY;
	private float winW;
	private float winH;
	private float listTop;
	private float viewTop;
	private float viewBottom;
	private float mainX;
	private float mainW;
	private float fieldY;
	private float sendX;
	private float sendY;
	private float trustX;
	private float trustY;
	private float trustW;
	private float plusX;
	private float plusY;
	private float nameFieldY;
	private float emptyButtonX;
	private float emptyButtonY;
	private float emptyButtonW;

	/** The messages of the open conversation laid out at the width they were laid out for. */
	private final List<Item> items = new ArrayList<>();
	private Conversation laid;
	private int laidRevision = -1;
	private int laidCount = -1;
	private float laidWidth = -1f;
	private float contentHeight;

	private static final int BUBBLE = 0;
	private static final int DAY_SEPARATOR = 1;
	private static final int NOTE = 2;

	private static final class Item {
		final int kind;
		final Message message;
		final List<String> lines;
		final float w;
		final float h;
		float y;
		boolean first;
		boolean last;
		String label;

		Item(int kind, Message message, List<String> lines, float w, float h) {
			this.kind = kind;
			this.message = message;
			this.lines = lines;
			this.w = w;
			this.h = h;
		}
	}

	public MessagesScreen(Screen parent, Conversation select, Message highlight) {
		super(Text.literal("Messages"));
		this.parent = parent;
		this.selected = select;
		this.highlight = highlight;
		this.highlightAt = System.currentTimeMillis();
		this.scrollToHighlight = highlight != null;
	}

	/** Whether this conversation is the one open here: its messages are read as they arrive. */
	public boolean showing(Conversation conversation) {
		return conversation != null && conversation == selected;
	}

	public Conversation selected() {
		return selected;
	}

	public void select(Conversation conversation) {
		if (conversation == selected) return;
		selected = conversation;
		draft = "";
		msgScroll = 0f;
		msgScrollShown = 0f;
		highlight = null;
		laid = null;
		ChatService service = Messages.service();
		if (service != null) service.viewing(conversation);
	}

	/** Shows the conversation scrolled to the message: a banner was clicked while this was open. */
	public void focus(Conversation conversation, Message message) {
		if (conversation == null) return;
		naming = false;
		select(conversation);
		highlight = message;
		highlightAt = System.currentTimeMillis();
		scrollToHighlight = message != null;
	}

	/** Opens the profile editor (this account's name and picture for Messages), as its bar under the list does. */
	public void editProfile() {
		editAsked = true;
	}

	private void openEditor(ChatService service) {
		editing = true;
		editAsked = false;
		naming = false;
		editName = service.displayName();
		editPicture = service.avatar();
	}

	private void saveProfile(ChatService service) {
		service.setProfile(editName, editPicture);
		editing = false;
	}

	/** Opens the field for a new conversation's name, as the + button does. */
	public void newConversation() {
		naming = true;
		newName = "";
	}

	/** Writes the text into the field and sends it, as the Enter key does (the game test uses it). */
	public void sendText(String text) {
		draft = text;
		submit();
	}

	// ------------------------------------------------------------------ space

	/**
	 * The size of everything here: Messages are read, not glanced at, so a third larger than the click GUI where the
	 * screen has room for it (the window keeps at least its smallest size inside the screen).
	 */
	private float scale() {
		ClickGuiModule gui = ModuleManager.get(ClickGuiModule.class);
		float base = gui != null ? gui.scale.f() : 1f;
		float room = Math.min(width / base / 436f, height / base / 276f);
		return base * Math.max(1f, Math.min(ZOOM, room));
	}

	private float spaceW() {
		return width / scale();
	}

	private float spaceH() {
		return height / scale();
	}

	private double toSpaceX(double x) {
		return (x - width / 2.0) / scale() + spaceW() / 2.0;
	}

	private double toSpaceY(double y) {
		return (y - height / 2.0) / scale() + spaceH() / 2.0;
	}

	private void layout() {
		float sw = spaceW();
		float sh = spaceH();
		winW = Math.min(sw - 16f, Math.max(420f, Math.min(720f, sw * 0.68f)));
		winH = Math.min(sh - 16f, Math.max(260f, Math.min(450f, sh * 0.76f)));
		winX = Math.round((sw - winW) / 2f);
		winY = Math.round((sh - winH) / 2f);
		mainX = winX + Ui.PAD + SIDE + Ui.PAD;
		mainW = winX + winW - Ui.PAD - mainX;
		fieldY = winY + winH - Ui.PAD - FIELD_H - 4f;
	}

	// ------------------------------------------------------------------ rendering

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
		// Drawn in render().
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float deltaTicks) {
		ThemeModule theme = ModuleManager.get(ThemeModule.class);
		if (theme != null) theme.apply();
		ChatService service = Messages.service();
		if (service != null) service.viewing(selected);
		float t = open.target(1f).get();
		boolean inWorld = client != null && client.world != null;
		Render2D.setAlpha(t);
		if (!inWorld) renderPanoramaBackground(ctx, deltaTicks);
		if (inWorld && BackdropBlur.available()) Render2D.glass(ctx, 0, 0, width, height, 0, 0x34000000);
		else Render2D.rect(ctx, 0, 0, width, height, inWorld ? 0x80000000 : 0x8C000000);

		layout();
		float s = scale() * (0.95f + 0.05f * t);
		double mx = toSpaceX(mouseX);
		double my = toSpaceY(mouseY);
		// The places in the window, for a controller's D-pad, in the screen's coordinates.
		Targets.scale(scale());
		ctx.getMatrices().pushMatrix();
		ctx.getMatrices().translate(width / 2f, height / 2f + (1f - t) * 10f);
		ctx.getMatrices().scale(s, s);
		ctx.getMatrices().translate(-spaceW() / 2f, -spaceH() / 2f);
		Render2D.setAlpha(t);

		Render2D.panelShadow(ctx, winX, winY + 6f, winW, winH, Ui.RADIUS, 28f, 0x8C000000, winX, winY, winW, winH);
		Render2D.glow(ctx, winX, winY, winW, winH, Ui.RADIUS, 22f, ColorUtil.withAlpha(Theme.accent(), 24), ColorUtil.withAlpha(Theme.accent2(), 16));
		Render2D.panel(ctx, winX, winY, winW, winH, Ui.RADIUS, 0, Ui.LIT);
		float lineW = winW * 0.55f;
		Render2D.gradientH(ctx, winX + (winW - lineW) * 0.5f, winY, lineW * 0.5f, 1f, 0f, ColorUtil.withAlpha(Theme.accent(), 0),
			ColorUtil.withAlpha(Theme.accent(), 170));
		Render2D.gradientH(ctx, winX + winW * 0.5f, winY, lineW * 0.5f, 1f, 0f, ColorUtil.withAlpha(Theme.accent2(), 170),
			ColorUtil.withAlpha(Theme.accent2(), 0));

		if (editAsked && service != null) openEditor(service);
		renderList(ctx, service, mx, my);
		if (service == null) renderOff(ctx);
		else if (selected == null) renderEmpty(ctx, mx, my);
		else renderConversation(ctx, service, mx, my);
		renderEditor(ctx, service, mx, my);
		ctx.getMatrices().popMatrix();
		Targets.scale(1f);
		Render2D.setAlpha(1f);
	}

	private void renderList(DrawContext ctx, ChatService service, double mx, double my) {
		float x = winX + Ui.PAD;
		float y = winY + Ui.PAD;
		float h = winH - Ui.PAD * 2f;
		Render2D.matte(ctx, x, y, SIDE, h, Ui.PANEL_RADIUS, ColorUtil.withAlpha(0x000000, 46), 0);
		SdfFont bold = Fonts.semibold();
		SdfFont font = Fonts.regular();
		Render2D.textMiddle(ctx, bold, "Messages", x + 10f, y + 14f, 9f, 0xF2FFFFFF);
		// New conversation.
		plusX = x + SIDE - 15f;
		plusY = y + 14f;
		boolean overPlus = inCircle(mx, my, plusX, plusY, 8f);
		Render2D.circle(ctx, plusX, plusY, 8f, overPlus || naming ? ColorUtil.withAlpha(Theme.accent(), 210) : ColorUtil.withAlpha(0xFFFFFF, 22));
		Render2D.icon(ctx, naming ? Icons.X : Icons.MESSAGE_CIRCLE_PLUS, plusX, plusY, 7.5f, 0xF2FFFFFF);
		// Connection.
		int dot;
		String status;
		if (service == null) {
			dot = 0x80FFFFFF;
			status = "Off (Messages module)";
		} else {
			switch (service.status()) {
				case ONLINE -> {
					dot = GREEN;
					status = "Connected · encrypted";
				}
				case CONNECTING -> {
					dot = ColorUtil.withAlpha(AMBER, Math.round(140 + 115 * pulse()));
					status = "Connecting…";
				}
				default -> {
					dot = RED;
					status = "Offline · retrying";
				}
			}
		}
		Render2D.circle(ctx, x + 12f, y + 27f, 2.2f, dot);
		Render2D.textMiddle(ctx, font, status, x + 18f, y + 27f, 5.8f, 0x99FFFFFF);

		// The field for a new conversation, sliding open under the header.
		float open = nameField.target(naming ? 1f : 0f).get();
		listTop = y + 36f + open * 32f;
		if (open > 0.01f) {
			float a = Render2D.alpha();
			Render2D.setAlpha(a * open);
			nameFieldY = y + 36f;
			float fx = x + 6f;
			float fw = SIDE - 12f;
			boolean error = System.currentTimeMillis() < nameErrorUntil;
			Render2D.round(ctx, fx, nameFieldY, fw, 18f, 9f, ColorUtil.withAlpha(0x000000, 90));
			Render2D.outline(ctx, fx, nameFieldY, fw, 18f, 9f, 0.8f, error ? RED : ColorUtil.withAlpha(Theme.accent(), 200));
			float cy = nameFieldY + 9f;
			if (newName.isEmpty()) {
				Render2D.textMiddle(ctx, font, "Their Minecraft name", fx + 8f, cy, 6.2f, 0x73FFFFFF);
			} else {
				Render2D.textMiddle(ctx, bold, newName, fx + 8f, cy, 6.5f, error ? RED : 0xF2FFFFFF);
			}
			if (caret()) Render2D.rect(ctx, fx + 8.5f + (newName.isEmpty() ? 0f : bold.width(newName, 6.5f)), cy - 4f, 0.8f, 8f, 0xE6FFFFFF);
			boolean ok = ChatService.NAME.matcher(newName).matches();
			Render2D.circle(ctx, fx + fw - 9f, cy, 6.5f, ok ? Theme.accent() : ColorUtil.withAlpha(0xFFFFFF, 26));
			Render2D.icon(ctx, Icons.CHEVRON_RIGHT, fx + fw - 8.5f, cy, 6f, ok ? 0xFFFFFFFF : 0x80FFFFFF);
			String hint = error ? nameError : "Friends need Aesthetic Visuals to get messages";
			Render2D.textMiddle(ctx, font, hint, fx + 4f, nameFieldY + 25f, 5.2f, error ? RED : 0x66FFFFFF);
			Render2D.setAlpha(a);
		}

		// The conversations, the latest first, above the bar of this account's own profile.
		float listBottom = y + h - 4f - PROFILE_H;
		List<Conversation> conversations = service == null ? List.of() : service.conversations();
		float total = conversations.size() * ROW;
		listScroll = Math.max(0f, Math.min(listScroll, Math.max(0f, total - (listBottom - listTop))));
		if (conversations.isEmpty() && service != null) {
			float cy = (listTop + listBottom) / 2f;
			Render2D.icon(ctx, Icons.MESSAGE_CIRCLE, x + SIDE / 2f, cy - 12f, 16f, 0x40FFFFFF);
			Render2D.textCentered(ctx, font, "No conversations yet", x + SIDE / 2f, cy + 2f, 6.2f, 0x80FFFFFF);
			Render2D.textCentered(ctx, font, "Start one with the + above", x + SIDE / 2f, cy + 11f, 5.4f, 0x59FFFFFF);
		}
		ctx.enableScissor((int) Math.floor(x), (int) Math.floor(listTop), (int) Math.ceil(x + SIDE), (int) Math.ceil(listBottom));
		long now = System.currentTimeMillis();
		for (int i = 0; i < conversations.size(); i++) {
			Conversation conversation = conversations.get(i);
			float ry = listTop + i * ROW - listScroll;
			if (ry + ROW < listTop || ry > listBottom) continue;
			renderRow(ctx, conversation, x + 4f, ry, SIDE - 8f, mx, my, now, my >= listTop && my <= listBottom);
		}
		ctx.disableScissor();
		renderProfileBar(ctx, service, x, y + h - PROFILE_H, mx, my);
	}

	/** This account's own picture and name for Messages, under the list; a click opens the editor. */
	private void renderProfileBar(DrawContext ctx, ChatService service, float x, float y, double mx, double my) {
		profileY = y;
		if (service == null) return;
		Render2D.rect(ctx, x + 8f, y, SIDE - 16f, 1f, Ui.HAIRLINE);
		float bx = x + 4f;
		float bw = SIDE - 8f;
		float by = y + 3f;
		float bh = PROFILE_H - 6f;
		boolean over = !editing && Ui.inside(mx, my, bx, by, bw, bh);
		if (over) Render2D.round(ctx, bx, by, bw, bh, 7f, ColorUtil.withAlpha(0xFFFFFF, 14));
		float head = 18f;
		float hx = bx + 5f;
		float hy = by + (bh - head) / 2f;
		Avatar.drawOwn(ctx, service.avatar(), hx, hy, head, head / 2f);
		SdfFont bold = Fonts.semibold();
		SdfFont font = Fonts.regular();
		boolean named = !service.displayName().isEmpty();
		float tx = hx + head + 6f;
		float maxW = bx + bw - 18f - tx;
		Render2D.textMiddle(ctx, bold, TextWrap.ellipsize(bold, named ? service.displayName() : service.name(), 6.6f, maxW), tx, by + bh / 2f - 4f, 6.6f, 0xF2FFFFFF);
		String sub = named ? "@" + service.name() : "Your name and picture";
		Render2D.textMiddle(ctx, font, TextWrap.ellipsize(font, sub, 5.4f, maxW), tx, by + bh / 2f + 4.5f, 5.4f, 0x80FFFFFF);
		Render2D.icon(ctx, Icons.PENCIL, bx + bw - 9f, by + bh / 2f, 6f, over ? 0xF2FFFFFF : 0x80FFFFFF);
	}

	/**
	 * The profile editor over the window: the picture (a file of one's own, cut to a square, or the Minecraft head) and
	 * the name others see (empty: the Minecraft name, which stays shown beside a chosen one), Save and Cancel.
	 */
	private void renderEditor(DrawContext ctx, ChatService service, double mx, double my) {
		float t = editor.target(editing ? 1f : 0f).get();
		if (t < 0.01f || service == null) return;
		byte[] picked = Faces.takePicked();
		if (picked != null && editing) editPicture = picked;
		float alpha = Render2D.alpha();
		Render2D.setAlpha(alpha * t);
		Render2D.round(ctx, winX, winY, winW, winH, Ui.RADIUS, 0x8C000000);
		edW = 236f;
		edH = 172f;
		edX = winX + (winW - edW) / 2f;
		edY = winY + (winH - edH) / 2f + (1f - t) * 8f;
		Render2D.shadow(ctx, edX, edY + 4f, edW, edH, 12f, 20f, 0x99000000);
		Render2D.panel(ctx, edX, edY, edW, edH, 12f);
		SdfFont bold = Fonts.semibold();
		SdfFont font = Fonts.regular();
		float px = edX + 14f;
		Render2D.textMiddle(ctx, bold, "Your profile", px, edY + 16f, 9f, 0xF2FFFFFF);
		Render2D.textMiddle(ctx, font, "What the people you write to see", px, edY + 27f, 5.8f, 0x80FFFFFF);
		float head = 46f;
		float hy = edY + 38f;
		Avatar.drawOwn(ctx, editPicture, px, hy, head, 12f);
		changeX = px + head + 12f;
		changeW = edX + edW - 14f - changeX;
		changeY = hy + 4f;
		pill(ctx, changeX, changeY, changeW, 16f, Faces.picking() ? "Choosing…" : "Change picture", Icons.IMAGE, mx, my, !Faces.picking());
		headY = changeY + 21f;
		pill(ctx, changeX, headY, changeW, 16f, "Use Minecraft head", Icons.USER, mx, my, editPicture != null);
		float ly = hy + head + 10f;
		Render2D.textMiddle(ctx, font, "Name", px, ly, 5.6f, 0x99FFFFFF);
		float fieldW = edW - 28f;
		float fieldTop = ly + 6f;
		Render2D.round(ctx, px, fieldTop, fieldW, 18f, 9f, ColorUtil.withAlpha(0x000000, 90));
		Render2D.outline(ctx, px, fieldTop, fieldW, 18f, 9f, 0.8f, ColorUtil.withAlpha(Theme.accent(), 200));
		float cy = fieldTop + 9f;
		if (editName.isEmpty()) Render2D.textMiddle(ctx, font, service.name(), px + 8f, cy, 6.4f, 0x66FFFFFF);
		else Render2D.textMiddle(ctx, bold, editName, px + 8f, cy, 6.5f, 0xF2FFFFFF);
		if (editing && caret()) Render2D.rect(ctx, px + 8.5f + (editName.isEmpty() ? 0f : bold.width(editName, 6.5f)), cy - 4f, 0.8f, 8f, 0xE6FFFFFF);
		String error = Faces.pickError();
		String hint = !error.isEmpty() ? error : "Your Minecraft name (@" + service.name() + ") stays beside it";
		Render2D.textMiddle(ctx, font, TextWrap.ellipsize(font, hint, 5.2f, fieldW), px, fieldTop + 26f, 5.2f, error.isEmpty() ? 0x66FFFFFF : RED);
		saveW = bold.width("Save", 6.5f) + 26f;
		saveX = edX + edW - 14f - saveW;
		saveY = edY + edH - 14f - 18f;
		boolean overSave = Ui.inside(mx, my, saveX, saveY, saveW, 18f);
		Render2D.round(ctx, saveX, saveY, saveW, 18f, 9f, ColorUtil.lerp(Theme.accent(), 0xFFFFFFFF, overSave ? 0.15f : 0f));
		Render2D.textCentered(ctx, bold, "Save", saveX + saveW / 2f, Render2D.middle(bold, saveY + 9f, 6.5f), 6.5f, 0xFFFFFFFF);
		cancelW = font.width("Cancel", 6.5f) + 22f;
		cancelX = saveX - 6f - cancelW;
		boolean overCancel = Ui.inside(mx, my, cancelX, saveY, cancelW, 18f);
		Render2D.round(ctx, cancelX, saveY, cancelW, 18f, 9f, ColorUtil.withAlpha(0xFFFFFF, overCancel ? 34 : 18));
		Render2D.textCentered(ctx, font, "Cancel", cancelX + cancelW / 2f, Render2D.middle(font, saveY + 9f, 6.5f), 6.5f, 0xE6FFFFFF);
		Render2D.setAlpha(alpha);
	}

	/** A pill button with an icon and words, dimmed when it does nothing. */
	private static void pill(DrawContext ctx, float x, float y, float w, float h, String label, int icon, double mx, double my, boolean enabled) {
		boolean over = enabled && Ui.inside(mx, my, x, y, w, h);
		Render2D.round(ctx, x, y, w, h, h / 2f, ColorUtil.withAlpha(0xFFFFFF, !enabled ? 8 : over ? 34 : 18));
		SdfFont font = Fonts.medium();
		int color = enabled ? 0xE6FFFFFF : 0x4DFFFFFF;
		float tw = font.width(label, 6f) + 10f;
		float tx = x + (w - tw) / 2f;
		Render2D.icon(ctx, icon, tx + 3f, y + h / 2f, 6f, color);
		Render2D.textMiddle(ctx, font, label, tx + 10f, y + h / 2f, 6f, color);
	}

	private void renderRow(DrawContext ctx, Conversation conversation, float x, float y, float w, double mx, double my, long now, boolean inList) {
		Contact contact = conversation.contact();
		boolean over = inList && Ui.inside(mx, my, x, y + 1f, w, ROW - 2f);
		boolean chosen = conversation == selected;
		if (chosen) {
			Render2D.round(ctx, x, y + 1f, w, ROW - 2f, 7f, ColorUtil.withAlpha(Theme.accent(), 38));
			Render2D.round(ctx, x, y + 9f, 2f, ROW - 18f, 1f, Theme.accent());
		} else if (over) {
			Render2D.round(ctx, x, y + 1f, w, ROW - 2f, 7f, ColorUtil.withAlpha(0xFFFFFF, 14));
		}
		float head = 22f;
		float hx = x + 7f;
		float hy = y + (ROW - head) / 2f;
		Avatar.draw(ctx, contact, hx, hy, head, 6f);
		if (contact.online()) {
			Render2D.circle(ctx, hx + head - 1f, hy + head - 1f, 3.6f, 0xFF15171C);
			Render2D.circle(ctx, hx + head - 1f, hy + head - 1f, 2.5f, GREEN);
		}
		SdfFont bold = Fonts.semibold();
		SdfFont font = Fonts.regular();
		float tx = hx + head + 7f;
		float right = x + w - 6f;
		Message last = conversation.last();
		String time = last != null ? shortTime(last.time(), now) : "";
		float timeW = font.width(time, 5.4f);
		// Hovered, the trash can takes the time's place.
		if (over) {
			Render2D.icon(ctx, Icons.TRASH_2, right - 4f, y + 11f, 6.5f, inCircle(mx, my, right - 4f, y + 11f, 6f) ? RED : 0x80FFFFFF);
		} else if (!time.isEmpty()) {
			Render2D.textMiddle(ctx, font, time, right - timeW, y + 11f, 5.4f, conversation.unread() > 0 ? Theme.accent() : 0x73FFFFFF);
		}
		String name = TextWrap.ellipsize(bold, contact.displayName(), 7f, right - 16f - tx - timeW);
		Render2D.textMiddle(ctx, bold, name, tx, y + 11f, 7f, 0xF2FFFFFF);
		if (contact.verified()) {
			float vx = tx + bold.width(name, 7f) + 5f;
			Render2D.circle(ctx, vx, y + 11f, 3f, Theme.accent());
			Render2D.icon(ctx, Icons.CHECK, vx, y + 11f, 3.6f, 0xFFFFFFFF);
		}
		String preview;
		int previewColor = 0x8CFFFFFF;
		if (contact.typing(now)) {
			preview = "typing…";
			previewColor = Theme.accent();
		} else if (last == null) {
			preview = contact.notFound(now) ? "Not on Aesthetic Visuals yet" : "Say hi";
			previewColor = 0x66FFFFFF;
		} else {
			preview = (last.out() ? "You: " : "") + EmojiText.of(last.text());
		}
		float badge = 0f;
		if (conversation.unread() > 0) {
			String count = conversation.unread() > 99 ? "99+" : String.valueOf(conversation.unread());
			float bw = Math.max(9f, font.width(count, 5.2f) + 5f);
			Render2D.round(ctx, right - bw, y + 18f, bw, 8.5f, 4.25f, Theme.accent());
			Render2D.textCentered(ctx, Fonts.semibold(), count, right - bw / 2f, Render2D.middle(font, y + 22.25f, 5.2f), 5.2f, 0xFFFFFFFF);
			badge = bw + 4f;
		}
		preview = TextWrap.ellipsize(font, preview, 6f, right - badge - tx);
		EmojiText.drawMiddle(ctx, font, preview, tx, y + 22.5f, 6f, previewColor);
	}

	private void renderOff(DrawContext ctx) {
		float cx = mainX + mainW / 2f;
		float cy = winY + winH / 2f;
		SdfFont font = Fonts.regular();
		Render2D.icon(ctx, Icons.WIFI_OFF, cx, cy - 18f, 18f, 0x59FFFFFF);
		Render2D.textCentered(ctx, Fonts.semibold(), "Messages are off", cx, cy + 2f, 8f, 0xD9FFFFFF);
		Render2D.textCentered(ctx, font, "Turn on the Messages module in Settings to chat.", cx, cy + 14f, 6f, 0x80FFFFFF);
	}

	private void renderEmpty(DrawContext ctx, double mx, double my) {
		float cx = mainX + mainW / 2f;
		float cy = winY + winH / 2f - 8f;
		SdfFont font = Fonts.regular();
		Render2D.circle(ctx, cx, cy - 26f, 20f, ColorUtil.withAlpha(Theme.accent(), 40));
		Render2D.icon(ctx, Icons.MESSAGE_CIRCLE, cx, cy - 26f, 18f, Theme.accent());
		Render2D.textCentered(ctx, Fonts.semibold(), "Your messages", cx, cy + 2f, 9f, 0xF2FFFFFF);
		Render2D.textCentered(ctx, font, "Private chat with friends who play with Aesthetic Visuals.", cx, cy + 16f, 6f, 0x99FFFFFF);
		Render2D.textCentered(ctx, font, "End-to-end encrypted: only you and they can read it.", cx, cy + 25f, 6f, 0x99FFFFFF);
		String label = "New message";
		emptyButtonW = Fonts.semibold().width(label, 6.5f) + 28f;
		emptyButtonX = cx - emptyButtonW / 2f;
		emptyButtonY = cy + 38f;
		boolean over = Ui.inside(mx, my, emptyButtonX, emptyButtonY, emptyButtonW, 18f);
		Render2D.round(ctx, emptyButtonX, emptyButtonY, emptyButtonW, 18f, 9f, ColorUtil.lerp(Theme.accent(), 0xFFFFFFFF, over ? 0.15f : 0f));
		Render2D.icon(ctx, Icons.MESSAGE_CIRCLE_PLUS, emptyButtonX + 11f, emptyButtonY + 9f, 6.5f, 0xFFFFFFFF);
		Render2D.textMiddle(ctx, Fonts.semibold(), label, emptyButtonX + 19f, emptyButtonY + 9f, 6.5f, 0xFFFFFFFF);
	}

	private void renderConversation(DrawContext ctx, ChatService service, double mx, double my) {
		Contact contact = selected.contact();
		long now = System.currentTimeMillis();
		SdfFont bold = Fonts.semibold();
		SdfFont font = Fonts.regular();
		float top = winY + Ui.PAD;
		// Header: who, and what they are doing.
		float head = 24f;
		Avatar.draw(ctx, contact, mainX + 4f, top + 3f, head, 7f);
		float tx = mainX + 4f + head + 8f;
		String shown = contact.displayName();
		Render2D.textMiddle(ctx, bold, shown, tx, top + 10f, 8.5f, 0xF2FFFFFF);
		float after = tx + bold.width(shown, 8.5f);
		if (contact.verified()) {
			float vx = after + 6f;
			Render2D.circle(ctx, vx, top + 10f, 3.6f, Theme.accent());
			Render2D.icon(ctx, Icons.CHECK, vx, top + 10f, 4.2f, 0xFFFFFFFF);
			after = vx + 4f;
		}
		// A name of their own: their Minecraft name beside it, which is who they are.
		if (contact.hasDisplayName()) Render2D.textMiddle(ctx, font, "@" + contact.name(), after + 5f, top + 10.5f, 6f, 0x73FFFFFF);
		String status;
		int statusColor = 0x8CFFFFFF;
		if (contact.typing(now)) {
			status = "typing…";
			statusColor = Theme.accent();
		} else if (contact.online()) {
			status = "Online";
			statusColor = GREEN;
		} else if (contact.notFound(now)) {
			status = "Not on Aesthetic Visuals yet: messages wait until they are";
			statusColor = AMBER;
		} else if (contact.lastSeen() > 0L) {
			status = "Last seen " + ago(contact.lastSeen(), now);
		} else {
			status = "Offline";
		}
		Render2D.textMiddle(ctx, font, status, tx, top + 21f, 6f, statusColor);
		String lock = contact.verified() ? "Verified · end-to-end encrypted" : "End-to-end encrypted";
		float lw = font.width(lock, 5.4f);
		float right = mainX + mainW - 4f;
		Render2D.textMiddle(ctx, font, lock, right - lw, top + 15f, 5.4f, 0x66FFFFFF);
		Render2D.icon(ctx, Icons.LOCK, right - lw - 6f, top + 15f, 5.6f, 0x66FFFFFF);
		Render2D.rect(ctx, mainX, top + HEADER - 4f, mainW, 1f, Ui.HAIRLINE);

		viewTop = top + HEADER;
		trustW = 0f;
		if (contact.keyConflict()) {
			float bh = 20f;
			Render2D.round(ctx, mainX + 2f, viewTop, mainW - 4f, bh, 6f, ColorUtil.withAlpha(AMBER, 34));
			Render2D.icon(ctx, Icons.SHIELD, mainX + 11f, viewTop + bh / 2f, 6.5f, AMBER);
			Render2D.textMiddle(ctx, font, "Another key uses this name: messages marked ! may not be theirs", mainX + 19f, viewTop + bh / 2f, 5.6f, 0xE6FFFFFF);
			String label = "Trust new key";
			trustW = font.width(label, 5.6f) + 12f;
			trustX = mainX + mainW - 6f - trustW;
			trustY = viewTop + 3f;
			boolean over = Ui.inside(mx, my, trustX, trustY, trustW, bh - 6f);
			Render2D.round(ctx, trustX, trustY, trustW, bh - 6f, 7f, ColorUtil.withAlpha(AMBER, over ? 150 : 90));
			Render2D.textCentered(ctx, font, label, trustX + trustW / 2f, Render2D.middle(font, trustY + (bh - 6f) / 2f, 5.6f), 5.6f, 0xFFFFFFFF);
			viewTop += bh + 4f;
		}
		viewBottom = fieldY - 6f;
		renderMessages(ctx, now);
		renderField(ctx, contact, mx, my);
	}

	private void renderMessages(DrawContext ctx, long now) {
		float left = mainX + 8f;
		float width = mainW - 16f;
		layoutMessages(width);
		float viewH = viewBottom - viewTop;
		float max = Math.max(0f, contentHeight - viewH);
		if (scrollToHighlight && highlight != null) {
			for (Item item : items) {
				if (item.message == highlight) {
					msgScroll = Math.max(0f, Math.min(max, contentHeight - item.y - item.h / 2f - viewH / 2f));
					msgScrollShown = msgScroll;
				}
			}
			scrollToHighlight = false;
		}
		msgScroll = Math.max(0f, Math.min(msgScroll, max));
		msgScrollShown += (msgScroll - msgScrollShown) * 0.35f;
		if (Math.abs(msgScroll - msgScrollShown) < 0.2f) msgScrollShown = msgScroll;
		float base = viewBottom - contentHeight + msgScrollShown;
		if (contentHeight < viewH) base = viewBottom - contentHeight;
		ctx.enableScissor((int) Math.floor(mainX), (int) Math.floor(viewTop), (int) Math.ceil(mainX + mainW), (int) Math.ceil(viewBottom));
		SdfFont font = Fonts.regular();
		float glow = highlight == null ? 0f : Math.max(0f, 1f - (now - highlightAt) / 2600f);
		for (Item item : items) {
			float y = base + item.y;
			if (y + item.h + 14f < viewTop || y > viewBottom) continue;
			switch (item.kind) {
				case DAY_SEPARATOR -> {
					float w = font.width(item.label, 5.4f) + 14f;
					float cx = left + width / 2f;
					Render2D.round(ctx, cx - w / 2f, y + 2f, w, 11f, 5.5f, ColorUtil.withAlpha(0x000000, 70));
					Render2D.textCentered(ctx, font, item.label, cx, Render2D.middle(font, y + 7.5f, 5.4f), 5.4f, 0x99FFFFFF);
				}
				case NOTE -> {
					float cx = left + width / 2f;
					Render2D.round(ctx, cx - item.w / 2f, y, item.w, item.h, 6f, ColorUtil.withAlpha(AMBER, 26));
					for (int i = 0; i < item.lines.size(); i++) {
						Render2D.textCentered(ctx, font, item.lines.get(i), cx, Render2D.middle(font, y + 6f + i * 8f + 4f, 5.6f), 5.6f, 0xCCFFFFFF);
					}
				}
				default -> renderBubble(ctx, item, left, width, y, now, item.message == highlight ? glow : 0f);
			}
		}
		ctx.disableScissor();
		// The newest is below: a soft edge where the list goes under the field.
		if (max - msgScrollShown > 1f) {
			Render2D.gradientV(ctx, mainX, viewTop, mainW, 10f, 0f, ColorUtil.withAlpha(0x000000, 60), 0x00000000);
		}
		if (msgScrollShown > 1f) {
			Render2D.gradientV(ctx, mainX, viewBottom - 10f, mainW, 10f, 0f, 0x00000000, ColorUtil.withAlpha(0x000000, 60));
		}
	}

	private void renderBubble(DrawContext ctx, Item item, float left, float width, float y, long now, float glow) {
		Message message = item.message;
		boolean out = message.out();
		float x = out ? left + width - item.w : left;
		int corners;
		if (out) corners = item.last ? Render2D.CORNER_TL | Render2D.CORNER_TR | Render2D.CORNER_BL : 0;
		else corners = item.last ? Render2D.CORNER_TL | Render2D.CORNER_TR | Render2D.CORNER_BR : 0;
		if (glow > 0f) {
			float pulse = 0.6f + 0.4f * (float) Math.sin((now - highlightAt) / 140.0);
			Render2D.shadow(ctx, x - 2f, y - 2f, item.w + 4f, item.h + 4f, BUBBLE_RADIUS + 2f, 12f,
				ColorUtil.withAlpha(Theme.accent(), Math.round(200 * glow * pulse)));
		}
		if (out) {
			int a = ColorUtil.withAlpha(Theme.accent(), 235);
			int b = ColorUtil.withAlpha(ColorUtil.lerp(Theme.accent(), Theme.accent2(), 0.7f), 235);
			Render2D.shape(ctx, V1zirPipelines.SHAPE, TextureSetup.empty(), x, y, item.w, item.h, BUBBLE_RADIUS, 0, 0, ShapeElement.FILL, corners,
				a, b, b, a);
		} else {
			Render2D.round(ctx, x, y, item.w, item.h, BUBBLE_RADIUS, corners, ColorUtil.withAlpha(0xFFFFFF, 30));
			if (message.unverified()) {
				Render2D.circle(ctx, x + item.w + 7f, y + item.h / 2f, 4f, ColorUtil.withAlpha(AMBER, 200));
				Render2D.textCentered(ctx, Fonts.bold(), "!", x + item.w + 7f, Render2D.middle(Fonts.bold(), y + item.h / 2f, 5.6f), 5.6f, 0xFF15171C);
			}
		}
		SdfFont font = Fonts.regular();
		for (int i = 0; i < item.lines.size(); i++) {
			EmojiText.drawMiddle(ctx, font, item.lines.get(i), x + PAD_X, y + PAD_Y + LINE * i + LINE / 2f - 0.5f, TEXT, 0xF7FFFFFF);
		}
		if (!item.last) return;
		// Under the last of a group: the time, and for mine how far it got.
		String time = clock(message.time());
		float ty = y + item.h + 5.5f;
		float tw = font.width(time, 5.2f);
		if (out) {
			float iconX = left + width - 4f;
			int state = message.state();
			int icon = state == Message.PENDING ? Icons.CLOCK : state == Message.SENT ? Icons.CHECK : Icons.CHECK_CHECK;
			int color = state == Message.READ ? Theme.accent() : 0x8CFFFFFF;
			Render2D.icon(ctx, icon, iconX, ty, 5.6f, color);
			Render2D.textMiddle(ctx, font, time, iconX - 6f - tw, ty, 5.2f, 0x66FFFFFF);
		} else {
			Render2D.textMiddle(ctx, font, time, x + 2f, ty, 5.2f, 0x66FFFFFF);
		}
	}

	private void renderField(DrawContext ctx, Contact contact, double mx, double my) {
		SdfFont font = Fonts.regular();
		float fx = mainX + 4f;
		float fw = mainW - 8f - FIELD_H - 6f;
		boolean active = !naming;
		Render2D.round(ctx, fx, fieldY, fw, FIELD_H, FIELD_H / 2f, ColorUtil.withAlpha(0x000000, active ? 96 : 70));
		Render2D.outline(ctx, fx, fieldY, fw, FIELD_H, FIELD_H / 2f, 0.8f, ColorUtil.withAlpha(active ? Theme.accent() : 0xFFFFFF, active ? 120 : 34));
		float cy = fieldY + FIELD_H / 2f;
		float room = fw - 20f;
		if (draft.isEmpty()) {
			Render2D.textMiddle(ctx, font, "Message " + contact.displayName() + "…", fx + 11f, cy, 6.6f, 0x66FFFFFF);
			if (active && caret()) Render2D.rect(ctx, fx + 10.5f, cy - 4.5f, 0.8f, 9f, 0xE6FFFFFF);
		} else {
			// The end of what is written shows when it is longer than the field; emoji in colour.
			String shown = EmojiText.of(draft);
			while (EmojiText.width(font, shown, 6.6f) > room && shown.length() > 1) shown = shown.substring(shown.offsetByCodePoints(0, 1));
			float shownW = EmojiText.drawMiddle(ctx, font, shown, fx + 11f, cy, 6.6f, 0xF7FFFFFF);
			if (active && caret()) Render2D.rect(ctx, fx + 11.5f + shownW, cy - 4.5f, 0.8f, 9f, 0xE6FFFFFF);
			int length = draft.codePointCount(0, draft.length());
			if (length > ChatService.MAX_TEXT - 80) {
				String left = String.valueOf(ChatService.MAX_TEXT - length);
				Render2D.textMiddle(ctx, font, left, fx + fw - 6f - font.width(left, 5.2f), fieldY - 5f, 5.2f, length >= ChatService.MAX_TEXT ? RED : 0x80FFFFFF);
			}
		}
		sendX = fx + fw + 6f + FIELD_H / 2f;
		sendY = cy;
		boolean can = !draft.isBlank();
		float lit = send.target(can ? 1f : 0f).get();
		boolean over = can && inCircle(mx, my, sendX, sendY, FIELD_H / 2f);
		Render2D.circle(ctx, sendX, sendY, FIELD_H / 2f, ColorUtil.lerp(ColorUtil.withAlpha(0xFFFFFF, 22),
			ColorUtil.lerp(Theme.accent(), 0xFFFFFFFF, over ? 0.15f : 0f), lit));
		Render2D.icon(ctx, Icons.SEND_HORIZONTAL, sendX + 0.5f, sendY, 9f, ColorUtil.lerp(0x80FFFFFF, 0xFFFFFFFF, lit));
		// Emoji whose names start with the :name being typed, over the field.
		if (active) {
			emojiList.update(draft);
			emojiList.render(ctx, fx + 4f, fieldY - 4f, mx, my);
		}
	}

	// ------------------------------------------------------------------ layout of the messages

	private void layoutMessages(float width) {
		List<Message> messages = selected.messages();
		if (laid == selected && laidRevision == selected.revision() && laidCount == messages.size() && laidWidth == width) return;
		laid = selected;
		laidRevision = selected.revision();
		laidCount = messages.size();
		laidWidth = width;
		items.clear();
		SdfFont font = Fonts.regular();
		float maxBubble = Math.min(width * 0.7f, 320f);
		float y = 4f;
		LocalDate lastDay = null;
		Message previous = null;
		for (int i = 0; i < messages.size(); i++) {
			Message message = messages.get(i);
			LocalDate day = day(message.time());
			if (!day.equals(lastDay)) {
				Item separator = new Item(DAY_SEPARATOR, null, List.of(), width, 15f);
				separator.label = dayLabel(day);
				separator.y = y;
				items.add(separator);
				y += 19f;
				lastDay = day;
				previous = null;
			}
			if (message.system()) {
				List<String> lines = TextWrap.wrap(font, message.text(), 5.6f, width * 0.8f);
				float w = 0f;
				for (String line : lines) w = Math.max(w, font.width(line, 5.6f));
				Item note = new Item(NOTE, message, lines, w + 16f, lines.size() * 8f + 4f);
				note.y = y;
				items.add(note);
				y += note.h + 6f;
				previous = null;
				continue;
			}
			boolean grouped = previous != null && previous.out() == message.out() && message.time() - previous.time() < GROUP_MS;
			if (previous != null && !grouped) y += 4f;
			Message next = i + 1 < messages.size() ? messages.get(i + 1) : null;
			boolean last = next == null || next.system() || next.out() != message.out() || next.time() - message.time() >= GROUP_MS
				|| !day(next.time()).equals(day);
			List<String> lines = TextWrap.wrap(font, EmojiText.of(message.text()), TEXT, maxBubble - PAD_X * 2f);
			float textW = 0f;
			for (String line : lines) textW = Math.max(textW, EmojiText.width(font, line, TEXT));
			Item bubble = new Item(BUBBLE, message, lines, textW + PAD_X * 2f, lines.size() * LINE + PAD_Y * 2f - 1f);
			bubble.y = y;
			bubble.first = !grouped;
			bubble.last = last;
			items.add(bubble);
			y += bubble.h + (last ? 13f : 2f);
			previous = message;
		}
		contentHeight = y + 2f;
	}

	// ------------------------------------------------------------------ input

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseClicked(click, doubled);
		double mx = toSpaceX(click.x());
		double my = toSpaceY(click.y());
		ChatService service = Messages.service();
		if (service == null) return true;
		if (editing) {
			if (!Faces.picking() && Ui.inside(mx, my, changeX, changeY, changeW, 16f)) Faces.pick();
			else if (editPicture != null && Ui.inside(mx, my, changeX, headY, changeW, 16f)) editPicture = null;
			else if (Ui.inside(mx, my, saveX, saveY, saveW, 18f)) saveProfile(service);
			else if (Ui.inside(mx, my, cancelX, saveY, cancelW, 18f) || !Ui.inside(mx, my, edX, edY, edW, edH)) editing = false;
			return true;
		}
		if (!naming && selected != null) {
			String next = emojiList.mouseClicked(mx, my, draft);
			if (next != null) {
				draft = next;
				return true;
			}
		}
		if (Ui.inside(mx, my, winX + Ui.PAD + 4f, profileY + 3f, SIDE - 8f, PROFILE_H - 6f)) {
			openEditor(service);
			return true;
		}
		if (inCircle(mx, my, plusX, plusY, 9f)) {
			naming = !naming;
			newName = "";
			return true;
		}
		if (naming && nameField.get() > 0.5f && Ui.inside(mx, my, winX + Ui.PAD + 6f, nameFieldY, SIDE - 12f, 18f)) {
			if (mx > winX + Ui.PAD + SIDE - 24f) startConversation();
			return true;
		}
		float listBottom = winY + winH - Ui.PAD - 4f - PROFILE_H;
		if (Ui.inside(mx, my, winX + Ui.PAD, listTop, SIDE, listBottom - listTop)) {
			List<Conversation> conversations = service.conversations();
			int index = (int) ((my - listTop + listScroll) / ROW);
			if (index >= 0 && index < conversations.size()) {
				Conversation conversation = conversations.get(index);
				float ry = listTop + index * ROW - listScroll;
				float right = winX + Ui.PAD + 4f + SIDE - 8f - 6f;
				if (inCircle(mx, my, right - 4f, ry + 11f, 6f)) {
					service.delete(conversation);
					if (selected == conversation) select(null);
				} else {
					naming = false;
					select(conversation);
				}
			}
			return true;
		}
		if (selected == null) {
			if (Ui.inside(mx, my, emptyButtonX, emptyButtonY, emptyButtonW, 18f)) {
				naming = true;
				newName = "";
			}
			return true;
		}
		if (trustW > 0f && Ui.inside(mx, my, trustX, trustY, trustW, 14f)) {
			service.trustNewKey(selected);
			return true;
		}
		if (inCircle(mx, my, sendX, sendY, FIELD_H / 2f)) {
			submit();
			return true;
		}
		if (Ui.inside(mx, my, mainX, fieldY, mainW, FIELD_H)) naming = false;
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		double mx = toSpaceX(mouseX);
		double my = toSpaceY(mouseY);
		if (editing) return true;
		if (Ui.inside(mx, my, winX + Ui.PAD, listTop, SIDE, winY + winH - Ui.PAD - 4f - PROFILE_H - listTop)) {
			listScroll -= (float) verticalAmount * ROW;
			return true;
		}
		if (selected != null && Ui.inside(mx, my, mainX, viewTop, mainW, viewBottom - viewTop)) {
			msgScroll += (float) verticalAmount * 24f;
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		int key = input.key();
		boolean shortcut = (input.modifiers() & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0;
		if (editing) {
			ChatService service = Messages.service();
			if (key == GLFW.GLFW_KEY_ESCAPE || service == null) editing = false;
			else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) saveProfile(service);
			else if (key == GLFW.GLFW_KEY_BACKSPACE) editName = shortcut ? "" : dropLast(editName);
			else if (key == GLFW.GLFW_KEY_V && shortcut) type(client.keyboard.getClipboard());
			return true;
		}
		if (!naming && selected != null) {
			String next = emojiList.keyPressed(key, draft);
			if (next != null) {
				draft = next;
				return true;
			}
		}
		if (key == GLFW.GLFW_KEY_ESCAPE) {
			if (naming) naming = false;
			else close();
			return true;
		}
		if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
			if (naming) startConversation();
			else submit();
			return true;
		}
		if (key == GLFW.GLFW_KEY_BACKSPACE) {
			if (naming) newName = shortcut ? "" : dropLast(newName);
			else if (selected != null) draft = shortcut ? dropWord(draft) : dropLast(draft);
			return true;
		}
		if (key == GLFW.GLFW_KEY_V && shortcut) {
			type(client.keyboard.getClipboard());
			return true;
		}
		if (key == GLFW.GLFW_KEY_TAB) {
			// Through the conversations, Shift going back.
			ChatService service = Messages.service();
			if (service != null && !service.conversations().isEmpty()) {
				List<Conversation> list = service.conversations();
				int at = Math.max(0, list.indexOf(selected));
				boolean back = (input.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
				naming = false;
				select(list.get(Math.floorMod(at + (back ? -1 : 1), list.size())));
			}
			return true;
		}
		return super.keyPressed(input);
	}

	@Override
	public boolean charTyped(CharInput input) {
		if (!input.isValidChar()) return false;
		type(input.asString());
		return true;
	}

	private void type(String text) {
		if (text == null || text.isEmpty()) return;
		if (editing) {
			StringBuilder name = new StringBuilder(editName);
			text.codePoints().forEach(cp -> {
				if (!Character.isISOControl(cp) && name.codePointCount(0, name.length()) < ChatService.MAX_DISPLAY_NAME) name.appendCodePoint(cp);
			});
			editName = name.toString();
			return;
		}
		if (naming) {
			StringBuilder name = new StringBuilder(newName);
			for (int i = 0; i < text.length() && name.length() < 16; i++) {
				char c = text.charAt(i);
				if (c < 128 && (Character.isLetterOrDigit(c) || c == '_')) name.append(c);
			}
			newName = name.toString();
			return;
		}
		if (selected == null) return;
		StringBuilder line = new StringBuilder(draft);
		text.codePoints().forEach(cp -> {
			if (line.codePointCount(0, line.length()) < ChatService.MAX_TEXT) line.appendCodePoint(Character.isISOControl(cp) ? ' ' : cp);
		});
		draft = line.toString();
		// As on Discord, :name: turns into its emoji as soon as the closing colon is typed.
		String closed = Emojis.closeShortcode(draft);
		if (closed != null) draft = closed;
		ChatService service = Messages.service();
		if (service != null) service.typing(selected);
	}

	private void submit() {
		ChatService service = Messages.service();
		if (service == null || selected == null || draft.isBlank()) return;
		service.send(selected, Emojis.replaceShortcodes(draft));
		draft = "";
		msgScroll = 0f;
	}

	private void startConversation() {
		ChatService service = Messages.service();
		if (service == null) return;
		if (!ChatService.NAME.matcher(newName).matches()) {
			nameError = "3 to 16 letters, digits or _";
			nameErrorUntil = System.currentTimeMillis() + 1600L;
			return;
		}
		if (newName.equalsIgnoreCase(service.name())) {
			nameError = "That's you";
			nameErrorUntil = System.currentTimeMillis() + 1600L;
			return;
		}
		Conversation conversation = service.open(newName);
		if (conversation == null) return;
		naming = false;
		newName = "";
		select(conversation);
	}

	@Override
	public void close() {
		ChatService service = Messages.service();
		if (service != null) service.viewing(null);
		client.setScreen(parent);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	// ------------------------------------------------------------------ helpers

	private static String dropLast(String text) {
		return text.isEmpty() ? text : text.substring(0, text.offsetByCodePoints(text.length(), -1));
	}

	private static String dropWord(String text) {
		String trimmed = text.stripTrailing();
		int space = trimmed.lastIndexOf(' ');
		return space < 0 ? "" : trimmed.substring(0, space + 1);
	}

	private static boolean inCircle(double mx, double my, float cx, float cy, float r) {
		Targets.add(cx - r, cy - r, r * 2f, r * 2f);
		double dx = mx - cx;
		double dy = my - cy;
		return dx * dx + dy * dy <= r * r;
	}

	private static boolean caret() {
		return System.nanoTime() / 500_000_000L % 2L == 0L;
	}

	private static float pulse() {
		return (float) (0.5 + 0.5 * Math.sin(System.nanoTime() / 1.6e8));
	}

	private static LocalDate day(long time) {
		return Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).toLocalDate();
	}

	private static String dayLabel(LocalDate day) {
		LocalDate today = LocalDate.now();
		if (day.equals(today)) return "Today";
		if (day.equals(today.minusDays(1))) return "Yesterday";
		return DAY.format(day);
	}

	static String clock(long time) {
		return CLOCK.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(time), ZoneId.systemDefault()));
	}

	/** The time of the list: the clock today, otherwise the day. */
	private static String shortTime(long time, long now) {
		LocalDate day = day(time);
		LocalDate today = LocalDate.now();
		if (day.equals(today)) return clock(time);
		if (day.equals(today.minusDays(1))) return "Yesterday";
		return DAY.format(day);
	}

	static String ago(long time, long now) {
		long minutes = Math.max(0L, (now - time) / 60_000L);
		if (minutes < 1L) return "just now";
		if (minutes < 60L) return minutes + " min ago";
		long hours = minutes / 60L;
		if (hours < 24L) return hours + " h ago";
		LocalDate day = day(time);
		if (day.equals(LocalDate.now().minusDays(1))) return "yesterday at " + clock(time);
		return DAY.format(day);
	}
}
