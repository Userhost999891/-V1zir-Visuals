package dev.v1zir.visuals.render.knife;

/**
 * Blade finishes. Each is a procedural pattern in knife.glsl (FINISH_* constants, in this order); the two colours
 * are the swatch the Knives tab shows for it.
 */
public enum KnifeFinish {
	STEEL("Steel", 0xFFC9CDD3, 0xFF7E848C),
	SUNSET("Sunset Fade", 0xFFFFD45E, 0xFF8C3CF0),
	RUBY("Ruby", 0xFFFF4D5E, 0xFF7A0614),
	SAPPHIRE("Sapphire", 0xFF6FA8FF, 0xFF0C2C9E),
	EMERALD("Emerald", 0xFF6BFFA0, 0xFF07662C),
	DAMASCUS("Damascus", 0xFFD9DADD, 0xFF45464A),
	TIGER("Tiger", 0xFFFFB43A, 0xFF2A1305),
	NIGHT("Night", 0xFF3A3B40, 0xFF0B0B0D),
	MARBLE("Marble", 0xFFF2384E, 0xFF2343E8),
	CARBON("Carbon", 0xFF4A4B50, 0xFF141416),
	GOLD("Gold", 0xFFFFE08A, 0xFFB07A22);

	private static final KnifeFinish[] VALUES = values();
	private static final String[] TITLES = new String[VALUES.length];

	static {
		for (int i = 0; i < VALUES.length; i++) TITLES[i] = VALUES[i].title;
	}

	private final String title;
	private final int light;
	private final int dark;

	KnifeFinish(String title, int light, int dark) {
		this.title = title;
		this.light = light;
		this.dark = dark;
	}

	public String title() {
		return title;
	}

	/** Swatch colours: the bright and the deep end of the finish. */
	public int light() {
		return light;
	}

	public int dark() {
		return dark;
	}

	public static String[] titles() {
		return TITLES.clone();
	}

	public static KnifeFinish byTitle(String title) {
		for (KnifeFinish finish : VALUES) if (finish.title.equals(title)) return finish;
		return STEEL;
	}
}
