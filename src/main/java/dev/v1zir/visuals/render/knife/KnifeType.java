package dev.v1zir.visuals.render.knife;

/**
 * The knife library: every type with its model, how the hand holds it and its own draw and inspect choreography.
 * Models are generated the first time they are needed and kept: a detailed one for the view model and the Knives
 * tab, a lighter one for knives in other players' hands.
 */
public enum KnifeType {
	KARAMBIT("Karambit", Grip.REVERSE, KnifeModels::karambit, 1.55f),
	TALON("Talon", Grip.REVERSE, KnifeModels::talon, 1.45f),
	BAYONET("Bayonet", Grip.FORWARD, KnifeModels::bayonet),
	BUTTERFLY("Butterfly", Grip.FORWARD, KnifeModels::butterfly),
	FLIP("Flip", Grip.FORWARD, KnifeModels::flip),
	GUT("Gut", Grip.FORWARD, KnifeModels::gut),
	HUNTSMAN("Huntsman", Grip.FORWARD, KnifeModels::huntsman),
	BOWIE("Bowie", Grip.FORWARD, KnifeModels::bowie),
	FALCHION("Falchion", Grip.FORWARD, KnifeModels::falchion),
	PUSH_DAGGER("Push Dagger", Grip.PUSH, KnifeModels::pushDagger, 1.25f),
	STILETTO("Stiletto", Grip.FORWARD, KnifeModels::stiletto),
	KUKRI("Kukri", Grip.FORWARD, KnifeModels::kukri),
	SKELETON("Skeleton", Grip.FORWARD, KnifeModels::skeleton),
	CLASSIC("Classic", Grip.FORWARD, KnifeModels::classic);

	/** Detail of the models held by other players. */
	private static final float WORLD_DETAIL = 0.55f;
	private static final KnifeType[] VALUES = values();
	private static final String[] TITLES = new String[VALUES.length];

	static {
		for (int i = 0; i < VALUES.length; i++) TITLES[i] = VALUES[i].title;
	}

	private final String title;
	final Grip grip;
	/** Size in hand relative to the others: small knives are drawn up a little, as shooters do with theirs. */
	final float viewScale;
	private final Model model;
	private KnifeMesh view;
	private KnifeMesh world;

	KnifeType(String title, Grip grip, Model model, float viewScale) {
		this.title = title;
		this.grip = grip;
		this.model = model;
		this.viewScale = viewScale;
	}

	KnifeType(String title, Grip grip, Model model) {
		this(title, grip, model, 1f);
	}

	public String title() {
		return title;
	}

	/** The detailed model (first person, previews). */
	public KnifeMesh mesh() {
		if (view == null) view = model.build(1f);
		return view;
	}

	/** The light model, for knives in third person. */
	public KnifeMesh worldMesh() {
		if (world == null) world = model.build(WORLD_DETAIL);
		return world;
	}

	public static String[] titles() {
		return TITLES.clone();
	}

	public static KnifeType byTitle(String title) {
		for (KnifeType type : VALUES) if (type.title.equals(title)) return type;
		return KARAMBIT;
	}

	/** How the fist holds the knife (see KnifeAnimator for the arm it implies). */
	enum Grip {
		/** Blade forward out of the thumb side, edge down. */
		FORWARD,
		/** Ice-pick grip: the blade below the little finger, a karambit's ring around the index finger. */
		REVERSE,
		/** T-handle across the palm, the blade out between the fingers along the forearm. */
		PUSH
	}

	@FunctionalInterface
	interface Model {
		KnifeMesh build(float detail);
	}
}
