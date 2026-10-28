package dev.v1zir.visuals.render.font;

import dev.v1zir.visuals.V1zir;

import java.util.EnumMap;
import java.util.Map;

/**
 * Bundled font families. All of them are SIL Open Font License fonts (Inter, Montserrat, Rubik,
 * Nunito, Comfortaa - the same families used by ZeroVisual); icons come from Lucide (ISC).
 * Static weights were instanced from the official variable fonts and subset to Latin,
 * Latin Extended-A (Polish) and Cyrillic.
 */
public final class Fonts {
	public enum Weight {
		REGULAR, MEDIUM, SEMIBOLD, BOLD
	}

	public enum Family {
		INTER("Inter"),
		MONTSERRAT("Montserrat"),
		RUBIK("Rubik"),
		NUNITO("Nunito"),
		COMFORTAA("Comfortaa");

		private final String title;

		Family(String title) {
			this.title = title;
		}

		public String title() {
			return title;
		}

		public static Family byTitle(String title) {
			for (Family family : values()) if (family.title.equalsIgnoreCase(title)) return family;
			return INTER;
		}
	}

	private static final Map<Family, EnumMap<Weight, SdfFont>> CACHE = new EnumMap<>(Family.class);
	private static SdfFont fallback;
	private static SdfFont title;
	private static SdfFont clock;
	private static SdfFont clockBold;
	private static SdfFont icons;
	private static Family family = Family.INTER;

	private Fonts() {
	}

	public static void setFamily(Family family) {
		Fonts.family = family;
	}

	public static Family family() {
		return family;
	}

	public static SdfFont ui(Weight weight) {
		return get(family, weight);
	}

	public static SdfFont regular() {
		return ui(Weight.REGULAR);
	}

	public static SdfFont medium() {
		return ui(Weight.MEDIUM);
	}

	public static SdfFont semibold() {
		return ui(Weight.SEMIBOLD);
	}

	public static SdfFont bold() {
		return ui(Weight.BOLD);
	}

	/** Display face for big headings such as the "V1zir Visuals" logo. */
	public static SdfFont title() {
		if (title == null) title = SdfFont.load("montserrat_extrabold", path("montserrat_extrabold"), 72).withFallback(fallback());
		return title;
	}

	/**
	 * Inter Display Bold (or Light) for big clocks: digits, colon and space only, with the tabular digits and the
	 * raised colon as the default glyphs so the time never jitters. Rasterised large because it is shown large.
	 */
	public static SdfFont clock(boolean bold) {
		if (bold) {
			if (clockBold == null) clockBold = SdfFont.load("inter_display_bold", path("inter_display_bold"), 96).withFallback(fallback());
			return clockBold;
		}
		if (clock == null) clock = SdfFont.load("inter_display_light", path("inter_display_light"), 96).withFallback(fallback());
		return clock;
	}

	public static SdfFont icons() {
		if (icons == null) icons = SdfFont.load("lucide", path("lucide"), 64);
		return icons;
	}

	private static SdfFont fallback() {
		if (fallback == null) fallback = SdfFont.load("inter_regular", path("inter_regular"));
		return fallback;
	}

	public static SdfFont get(Family family, Weight weight) {
		return CACHE.computeIfAbsent(family, f -> new EnumMap<>(Weight.class)).computeIfAbsent(weight, w -> {
			String file = file(family, w);
			try {
				return SdfFont.load(file, path(file)).withFallback(fallback());
			} catch (RuntimeException e) {
				V1zir.LOGGER.error("Failed to load font {}, falling back to Inter", file, e);
				return fallback();
			}
		});
	}

	private static String file(Family family, Weight weight) {
		return switch (family) {
			case INTER -> switch (weight) {
				case REGULAR -> "inter_regular";
				case MEDIUM -> "inter_medium";
				case SEMIBOLD -> "inter_semibold";
				case BOLD -> "inter_bold";
			};
			case MONTSERRAT -> switch (weight) {
				case REGULAR, MEDIUM -> "montserrat_semibold";
				case SEMIBOLD -> "montserrat_bold";
				case BOLD -> "montserrat_extrabold";
			};
			case RUBIK -> weight == Weight.REGULAR || weight == Weight.MEDIUM ? "rubik_regular" : "rubik_semibold";
			case NUNITO -> weight == Weight.REGULAR || weight == Weight.MEDIUM ? "nunito_regular" : "nunito_semibold";
			case COMFORTAA -> "comfortaa";
		};
	}

	private static String path(String file) {
		return "/assets/" + V1zir.MOD_ID + "/fonts/" + file + ".ttf";
	}
}
