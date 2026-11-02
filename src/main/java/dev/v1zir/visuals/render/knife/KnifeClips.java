package dev.v1zir.visuals.render.knife;

/**
 * The choreography: attack swings shared by every knife, a draw per kind of knife and an inspect per knife type.
 * Hand offsets are in view space (blocks), turns and spins in degrees about the knife's axes (x along the blade,
 * y towards the spine, z through the flat; a negative z spin tips the point forward and down), shifts in centimetres.
 * Spins end on whole turns so a finished clip hands over to the resting pose without a jump.
 */
final class KnifeClips {
	// ------------------------------------------------------------------ attacks

	/** Quick stab: a short wind-back, a thrust towards the crosshair and a smooth recovery. */
	static final KnifeClip STAB = new KnifeClip(0.36f)
		.at(0.06f).hand(0.012f, -0.012f, 0.035f).turn(0f, 0f, -6f)
		.at(0.13f).hand(-0.075f, 0.035f, -0.25f).turn(0f, -8f, -10f)
		.at(0.19f).hand(-0.07f, 0.03f, -0.23f).turn(0f, -8f, -9f);

	/** Short slash: up and out to the right, then the edge sweeps across and down. */
	static final KnifeClip SLASH = new KnifeClip(0.42f)
		.at(0.07f).hand(0.06f, 0.05f, 0.02f).turn(-12f, 22f, 26f)
		.at(0.17f).hand(-0.13f, -0.05f, -0.09f).turn(8f, -34f, -24f)
		.at(0.24f).hand(-0.14f, -0.065f, -0.07f).turn(10f, -37f, -27f);

	/** Heavy stab of a fully charged hit: drawn back and raised, then driven forward and held for a beat. */
	static final KnifeClip HEAVY = new KnifeClip(0.64f)
		.at(0.17f).hand(0.045f, 0.035f, 0.09f).turn(0f, 6f, 20f)
		.at(0.29f).hand(-0.085f, 0.045f, -0.32f).turn(0f, -10f, -12f)
		.at(0.38f).hand(-0.08f, 0.04f, -0.3f).turn(0f, -10f, -11f);

	// ------------------------------------------------------------------ draws

	/** Fixed blades: up from below the view, the knife flipping once over the hand on its way into the grip. */
	private static final KnifeClip DRAW = new KnifeClip(0.72f, 3f, 0f)
		.at(0f).hand(0.08f, -0.42f, 0.12f).turn(0f, 0f, -55f).spin(0f, 0f, 360f)
		.at(0.3f).hand(0.03f, -0.08f, 0.04f).turn(0f, 0f, -12f).spin(0f, 0f, 70f)
		.at(0.48f).hand(0f, 0.012f, 0f).turn(0f, 0f, 4f).spin(0f, 0f, 0f);

	/** Karambit and talon: rising while the knife whirls once round the finger in its ring. */
	private static final KnifeClip DRAW_RING = new KnifeClip(0.78f, -5.5f, 0.1f)
		.at(0f).hand(0.06f, -0.4f, 0.1f).turn(-20f, 0f, 30f).spin(0f, 0f, -360f)
		.at(0.34f).hand(0.02f, -0.06f, 0.03f).turn(-6f, 0f, 8f).spin(0f, 0f, -60f)
		.at(0.5f).hand(0f, 0.01f, 0f).turn(0f, 0f, -3f).spin(0f, 0f, 0f);

	/** Balisong: comes up closed and is flipped open, handle first, then the blade swinging out. */
	private static final KnifeClip DRAW_BALISONG = new KnifeClip(0.9f)
		.at(0f).hand(0.05f, -0.38f, 0.1f).turn(-30f, 0f, -20f).parts(180f, 180f)
		.at(0.26f).hand(0.02f, -0.06f, 0.03f).turn(-15f, 0f, 6f).parts(180f, 180f)
		.at(0.42f).hand(0.01f, 0.01f, 0f).turn(-20f, 0f, 12f).parts(180f, 0f)
		.at(0.6f).hand(0f, -0.01f, 0f).turn(-4f, 0f, -4f).parts(0f, 0f);

	/** Folding knives: up with the blade closed, then flicked open with a snap. */
	private static final KnifeClip DRAW_FOLDER = new KnifeClip(0.74f)
		.at(0f).hand(0.06f, -0.38f, 0.1f).turn(0f, 0f, -40f).parts(-165f, 0f)
		.at(0.28f).hand(0.02f, -0.04f, 0.02f).turn(0f, 0f, -8f).parts(-160f, 0f)
		.at(0.4f).hand(-0.01f, 0.02f, 0f).turn(0f, 0f, 10f).parts(8f, 0f)
		.at(0.5f).hand(0f, 0.005f, 0f).turn(0f, 0f, 2f).parts(0f, 0f);

	/** Push dagger: the fist comes up and twists the blade level. */
	private static final KnifeClip DRAW_PUSH = new KnifeClip(0.62f)
		.at(0f).hand(0.05f, -0.36f, 0.1f).turn(-80f, 0f, -30f)
		.at(0.32f).hand(0.01f, -0.03f, 0.02f).turn(-12f, 0f, -4f)
		.at(0.44f).hand(0f, 0.01f, 0f).turn(4f, 0f, 2f);

	// ------------------------------------------------------------------ inspects

	/** Karambit: the knife spins round the finger in its ring, is caught, then turned to show both flats. */
	private static final KnifeClip INSPECT_KARAMBIT = ringInspect(-5.45f, 0.1f);
	private static final KnifeClip INSPECT_TALON = ringInspect(-5.7f, 0.15f);
	private static final KnifeClip INSPECT_SKELETON = ringInspect(-6.5f, 0f);

	private static KnifeClip ringInspect(float ringX, float ringY) {
		return new KnifeClip(3.0f, ringX, ringY)
			.at(0.3f).hand(-0.03f, 0.06f, 0.03f).turn(-25f, 18f, 10f)
			.at(0.55f).hand(-0.04f, 0.075f, 0.03f).turn(-30f, 24f, 16f).spin(0f, 0f, -70f)
			.at(0.78f).spin(0f, 0f, -210f)
			.at(1.0f).spin(0f, 0f, -335f)
			.at(1.16f).hand(-0.035f, 0.065f, 0.03f).turn(-27f, 21f, 12f).spin(0f, 0f, -360f)
			.at(1.6f).hand(-0.06f, 0.05f, 0.05f).turn(-75f, 38f, 24f)
			.at(2.05f).hand(-0.055f, 0.06f, 0.045f).turn(-100f, 34f, 30f)
			.at(2.35f).hand(-0.02f, 0.03f, 0.02f).turn(-35f, 12f, 12f).spin(0f, 0f, -420f)
			.at(2.6f).hand(0f, 0.01f, 0f).turn(-6f, 2f, 2f).spin(0f, 0f, -660f)
			.at(3.0f).hand(0f, 0f, 0f).turn(0f, 0f, 0f).spin(0f, 0f, -720f).hold();
	}

	/** Bayonet: tossed up, turning end over end, caught, then the blade shown to the light. */
	private static final KnifeClip INSPECT_BAYONET = new KnifeClip(2.9f, 4f, 0f)
		.at(0.24f).hand(0f, -0.035f, 0.01f).turn(0f, 0f, -16f)
		.at(0.4f).hand(0f, 0.05f, 0f).turn(0f, 0f, 12f).shift(0f, 3f, 0f).spin(0f, 0f, -40f)
		.at(0.62f).hand(0f, 0.025f, 0f).shift(0f, 10f, 0f).spin(0f, 0f, -190f)
		.at(0.84f).shift(0f, 3f, 0f).spin(0f, 0f, -335f)
		.at(0.94f).hand(0f, -0.025f, 0f).turn(0f, 0f, -6f).shift(0f, 0f, 0f).spin(0f, 0f, -360f)
		.at(1.12f).hand(0f, 0f, 0f).turn(-10f, 0f, 2f)
		.at(1.55f).hand(-0.05f, 0.06f, 0.04f).turn(-80f, 30f, 16f)
		.at(2.05f).hand(-0.06f, 0.07f, 0.04f).turn(-102f, 34f, 22f)
		.at(2.45f).hand(-0.02f, 0.02f, 0.01f).turn(-28f, 10f, 6f)
		.at(2.9f).hand(0f, 0f, 0f).turn(0f, 0f, 0f).hold();

	/** Butterfly: closed with a swing of the blade, opened again, then a full fan of blade and handle round the pivot. */
	private static final KnifeClip INSPECT_BUTTERFLY = new KnifeClip(3.2f)
		.at(0.25f).hand(-0.03f, 0.05f, 0.02f).turn(-30f, 10f, 15f)
		.at(0.5f).turn(-35f, 12f, 20f).parts(180f, 0f)
		.at(0.72f).hand(-0.03f, 0.04f, 0.02f).turn(-25f, 10f, 5f).parts(180f, 180f)
		.at(0.98f).hand(-0.02f, 0.06f, 0.02f).turn(-40f, 10f, 22f).parts(180f, 0f)
		.at(1.22f).turn(-30f, 8f, 10f).parts(360f, 0f)
		.at(1.6f).hand(-0.05f, 0.05f, 0.04f).turn(-70f, 30f, 20f).parts(360f, 0f)
		.at(1.9f).turn(-80f, 32f, 26f).parts(540f, 0f)
		.at(2.2f).hand(-0.03f, 0.04f, 0.02f).turn(-45f, 20f, 15f).parts(720f, 0f)
		.at(2.75f).hand(-0.01f, 0.01f, 0f).turn(-10f, 4f, 3f).parts(720f, 0f)
		.at(3.2f).hand(0f, 0f, 0f).turn(0f, 0f, 0f).parts(720f, 0f).hold();

	/** Flip knife: flipped twice round the index finger, then the wrist turns it to the light. */
	private static final KnifeClip INSPECT_FLIP = new KnifeClip(2.7f, 0.5f, 0f)
		.at(0.22f).hand(-0.02f, 0.04f, 0.02f).turn(-15f, 12f, 8f)
		.at(0.5f).spin(0f, 0f, -200f)
		.at(0.72f).spin(0f, 0f, -360f)
		.at(1.0f).spin(0f, 0f, -560f)
		.at(1.22f).spin(0f, 0f, -720f).turn(-18f, 14f, 6f)
		.at(1.7f).hand(-0.05f, 0.06f, 0.04f).turn(-82f, 30f, 18f)
		.at(2.15f).hand(-0.03f, 0.04f, 0.02f).turn(-40f, 14f, 10f)
		.at(2.7f).hand(0f, 0f, 0f).turn(0f, 0f, 0f).spin(0f, 0f, -720f).hold();

	/** Gut knife: rolled through the fingers twice, then tipped up to show the hook. */
	private static final KnifeClip INSPECT_GUT = new KnifeClip(2.8f)
		.at(0.25f).hand(-0.02f, 0.05f, 0.02f).turn(-10f, 10f, 10f)
		.at(0.6f).spin(-180f, 0f, 0f)
		.at(0.95f).spin(-360f, 0f, 0f)
		.at(1.3f).spin(-540f, 0f, 0f)
		.at(1.62f).spin(-720f, 0f, 0f).turn(-20f, 15f, 20f)
		.at(2.1f).hand(-0.05f, 0.07f, 0.03f).turn(-50f, 25f, 40f)
		.at(2.8f).hand(0f, 0f, 0f).turn(0f, 0f, 0f).spin(-720f, 0f, 0f).hold();

	/** Big fixed blades: lifted and turned to admire one flat, then rolled over to the other. */
	private static final KnifeClip INSPECT_ADMIRE = new KnifeClip(3.0f)
		.at(0.35f).hand(-0.04f, 0.06f, 0.03f).turn(-30f, 25f, 18f)
		.at(0.9f).hand(-0.06f, 0.07f, 0.04f).turn(-60f, 36f, 24f)
		.at(1.35f).hand(-0.05f, 0.06f, 0.05f).turn(-150f, 30f, 20f)
		.at(1.9f).hand(-0.05f, 0.065f, 0.05f).turn(-185f, 26f, 26f)
		.at(2.4f).hand(-0.02f, 0.02f, 0.02f).turn(-300f, 8f, 8f)
		.at(3.0f).hand(0f, 0f, 0f).turn(-360f, 0f, 0f).hold();

	/** Kukri: swung round point-down and back over, heavy and slow, then the belly shown. */
	private static final KnifeClip INSPECT_KUKRI = new KnifeClip(3.1f, 2f, 0f)
		.at(0.3f).hand(-0.02f, 0.04f, 0.02f).turn(-15f, 10f, 12f)
		.at(0.75f).hand(-0.03f, 0.02f, 0.03f).spin(0f, 0f, -180f)
		.at(1.25f).hand(-0.02f, 0.05f, 0.02f).spin(0f, 0f, -360f).turn(-25f, 18f, 18f)
		.at(1.8f).hand(-0.06f, 0.07f, 0.05f).turn(-90f, 32f, 26f)
		.at(2.4f).hand(-0.03f, 0.03f, 0.02f).turn(-40f, 14f, 10f)
		.at(3.1f).hand(0f, 0f, 0f).turn(0f, 0f, 0f).spin(0f, 0f, -360f).hold();

	/** Stiletto: the blade folds shut and swings back open with a flick, twice, then is shown. */
	private static final KnifeClip INSPECT_STILETTO = folderInspect(-165f);
	/** Falchion: half-closed and snapped open twice. */
	private static final KnifeClip INSPECT_FALCHION = folderInspect(-110f);

	private static KnifeClip folderInspect(float closed) {
		return new KnifeClip(2.9f)
			.at(0.25f).hand(-0.03f, 0.05f, 0.02f).turn(-20f, 15f, 10f)
			.at(0.6f).parts(closed, 0f).turn(-25f, 15f, 4f)
			.at(0.75f).parts(closed, 0f)
			.at(0.86f).parts(10f, 0f).turn(-20f, 15f, 18f)
			.at(0.96f).parts(0f, 0f)
			.at(1.3f).parts(closed * 0.8f, 0f).turn(-35f, 18f, 6f)
			.at(1.42f).parts(6f, 0f).turn(-30f, 18f, 16f)
			.at(1.5f).parts(0f, 0f)
			.at(2.0f).hand(-0.05f, 0.06f, 0.04f).turn(-85f, 30f, 20f)
			.at(2.45f).hand(-0.02f, 0.02f, 0.01f).turn(-30f, 10f, 6f);
	}

	/** Push dagger: the fist turns the blade over, then two quick jabs. */
	private static final KnifeClip INSPECT_PUSH = new KnifeClip(2.6f)
		.at(0.35f).hand(-0.02f, 0.05f, 0.03f).turn(-60f, 10f, 10f)
		.at(0.8f).hand(-0.03f, 0.06f, 0.03f).turn(-140f, 15f, 15f)
		.at(1.25f).hand(-0.02f, 0.04f, 0.02f).turn(-30f, 5f, 5f)
		.at(1.45f).hand(-0.05f, 0.03f, -0.12f)
		.at(1.6f).hand(-0.01f, 0.03f, 0.01f)
		.at(1.78f).hand(-0.06f, 0.035f, -0.14f)
		.at(2.0f).hand(0f, 0.01f, 0f).turn(-8f, 2f, 2f);

	private KnifeClips() {
	}

	static KnifeClip draw(KnifeType type) {
		return switch (type) {
			case KARAMBIT, TALON, SKELETON -> DRAW_RING;
			case BUTTERFLY -> DRAW_BALISONG;
			case FLIP, FALCHION, STILETTO -> DRAW_FOLDER;
			case PUSH_DAGGER -> DRAW_PUSH;
			default -> DRAW;
		};
	}

	static KnifeClip inspect(KnifeType type) {
		return switch (type) {
			case KARAMBIT -> INSPECT_KARAMBIT;
			case TALON -> INSPECT_TALON;
			case SKELETON -> INSPECT_SKELETON;
			case BAYONET -> INSPECT_BAYONET;
			case BUTTERFLY -> INSPECT_BUTTERFLY;
			case FLIP -> INSPECT_FLIP;
			case GUT -> INSPECT_GUT;
			case KUKRI -> INSPECT_KUKRI;
			case STILETTO -> INSPECT_STILETTO;
			case FALCHION -> INSPECT_FALCHION;
			case PUSH_DAGGER -> INSPECT_PUSH;
			default -> INSPECT_ADMIRE;
		};
	}
}
