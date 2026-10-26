package dev.v1zir.visuals.gui.menu;

import dev.v1zir.visuals.module.impl.client.MainMenuModule;
import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.font.Icons;
import dev.v1zir.visuals.render.font.SdfFont;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.text.DateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;

/**
 * The time, the date in the game's language and a greeting for the time of day, shown by the lock screen and the
 * main menu header. The texts are rebuilt once a second (or when a setting or the language changes), never per frame.
 */
final class MenuClock {
	private static final String[] ENGLISH = {"Good morning", "Good afternoon", "Good evening", "Good night"};
	/** Polish has no afternoon greeting: "Dzień dobry" lasts until the evening. */
	private static final String[] POLISH = {"Dzień dobry", "Dzień dobry", "Dobry wieczór", "Dobranoc"};
	private static final int[] ICONS = {Icons.SUNRISE, Icons.SUN, Icons.SUNSET, Icons.MOON};

	/** "14:07", "2:07" in 12-hour mode, with ":32" when seconds are shown. */
	String time = "";
	/** "AM" or "PM" in 12-hour mode, otherwise empty. */
	String period = "";
	/** "Monday, 26 October", or "poniedziałek, 26 października" in Polish. */
	String date = "";
	/** "Good evening, Steve"; empty when the greeting is off. */
	String greeting = "";
	int greetingIcon = Icons.SUN;
	String hint = "";

	private long second = Long.MIN_VALUE;
	private String language = "";
	private boolean twelveHour;
	private boolean seconds;
	private boolean greet;
	private DateTimeFormatter timeFormat;
	private DateTimeFormatter dateFormat;
	private boolean polish;

	void update(MinecraftClient client, MainMenuModule module) {
		long now = System.currentTimeMillis() / 1000L;
		String lang = client.getLanguageManager().getLanguage();
		boolean twelve = module != null && module.timeFormat.is("12-hour");
		boolean secs = module != null && module.seconds.on();
		boolean hello = module == null || module.greeting.on();
		if (now == second && lang.equals(language) && twelve == twelveHour && secs == seconds && hello == greet) return;
		if (!lang.equals(language) || dateFormat == null) {
			language = lang;
			polish = lang.startsWith("pl_");
			dateFormat = DateTimeFormatter.ofPattern("EEEE, d MMMM", locale(lang));
			hint = polish ? "Przesuń w górę, aby otworzyć" : "Swipe up to open";
		}
		if (timeFormat == null || twelve != twelveHour || secs != seconds) {
			timeFormat = DateTimeFormatter.ofPattern((twelve ? "h:mm" : "HH:mm") + (secs ? ":ss" : ""));
		}
		second = now;
		twelveHour = twelve;
		seconds = secs;
		greet = hello;

		LocalDateTime t = LocalDateTime.now();
		int hour = t.getHour();
		time = timeFormat.format(t);
		period = twelve ? (hour < 12 ? "AM" : "PM") : "";
		date = dateFormat.format(t);
		int slot = hour < 5 ? 3 : hour < 12 ? 0 : hour < 18 ? 1 : hour < 22 ? 2 : 3;
		greetingIcon = ICONS[slot];
		greeting = hello ? (polish ? POLISH : ENGLISH)[slot] + ", " + client.getSession().getUsername() : "";
	}

	/** Height of the digits at this size, to centre the time on them rather than on its line box. */
	static float digitHeight(float size) {
		SdfFont font = Fonts.clock();
		return -(font.glyph('0').y0() + SdfFont.PADDING) * font.k(size);
	}

	/** Draws the time (and a small AM/PM) centred on cx with its digits standing on baseline; returns their left edge. */
	float drawTime(DrawContext ctx, float cx, float baseline, float size, Render2D.TextStyle style) {
		SdfFont font = Fonts.clock();
		SdfFont small = Fonts.semibold();
		float periodSize = size * 0.2f;
		float gap = size * 0.08f;
		float timeW = font.width(time, size);
		float periodW = period.isEmpty() ? 0f : gap + small.width(period, periodSize);
		float x = cx - (timeW + periodW) / 2f;
		Render2D.text(ctx, font, time, x, baseline - font.ascent(size), size, style);
		if (periodW > 0f) {
			Render2D.text(ctx, small, period, x + timeW + gap, baseline - small.ascent(periodSize), periodSize, ColorUtil.fade(style.color, 0.8f));
		}
		return x;
	}

	/** Draws the greeting with its time-of-day icon, centred on (cx, cy). */
	void drawGreeting(DrawContext ctx, float cx, float cy, float size, int color) {
		SdfFont font = Fonts.semibold();
		float icon = size * 1.15f;
		float gap = size * 0.5f;
		float x = cx - (icon + gap + font.width(greeting, size)) / 2f;
		Render2D.icon(ctx, greetingIcon, x + icon / 2f, cy, icon, ColorUtil.withAlpha(ColorUtil.lerp(0xFFFFFF, Theme.accent2(), 0.55f), ColorUtil.alpha(color)));
		Render2D.textMiddle(ctx, font, greeting, x + icon + gap, cy, size, color);
	}

	/**
	 * The game language as a date locale, or English when Java has no names for it or the UI fonts (Latin and
	 * Cyrillic) cannot draw them.
	 */
	private static Locale locale(String language) {
		Locale locale = Locale.forLanguageTag(language.replace('_', '-'));
		if (!Arrays.asList(DateFormat.getAvailableLocales()).contains(locale)) return Locale.ENGLISH;
		SdfFont font = Fonts.medium();
		String sample = DateTimeFormatter.ofPattern("EEEE MMMM", locale).format(LocalDate.of(2026, 9, 30));
		for (int i = 0; i < sample.length(); ) {
			int cp = sample.codePointAt(i);
			i += Character.charCount(cp);
			if (!font.has(cp)) return Locale.ENGLISH;
		}
		return locale;
	}
}
