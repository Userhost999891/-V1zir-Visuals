package dev.v1zir.visuals.render.knife;

/**
 * The choreography, timed like a modern tactical shooter's knives: a deploy of about a second, slashes that cross the
 * view in a fraction of that and settle back over the rest of a second, a heavy stab, and inspects of five seconds in
 * phases (raise, a trick in the fingers, one flat shown, the forearm rolled over to the other, back to rest).
 * <p>
 * Channels (see {@link KnifeClip}): hand = the shoulder carrying the arm (view space, blocks: x right, y up, z towards
 * the viewer); turn = pitch about the elbow (lifts the fist), yaw about the elbow (swings it left) and the forearm's
 * roll about its own axis (shows the flats); spin and shift = the knife in the fingers about the clip's pivot (degrees
 * about the knife's axes: x along the blade, y towards the spine, z through the flat; a negative z spin tips the point
 * forward and down). Spins and part angles end on whole turns so a finished clip hands over to rest without a jump.
 */
final class KnifeClips {
	// ------------------------------------------------------------------ attacks

	/**
	 * Primary slash: cocked up and back to the right, then the whole arm sweeps the blade down across the view to the
	 * lower left, edge first (the edge faces down, the way the blade travels), and settles back.
	 */
	static final KnifeClip SLASH = new KnifeClip(0.85f)
		.at(0.09f).hand(0.02f, 0.05f, 0.03f).turn(8f, -24f, 6f).spin(0f, 0f, 14f)
		.at(0.16f).hand(-0.06f, 0.03f, -0.06f).turn(4f, 16f, 8f).spin(0f, 0f, -4f)
		.at(0.23f).hand(-0.1f, -0.02f, -0.03f).turn(-2f, 40f, 10f).spin(0f, 0f, -14f)
		.at(0.35f).hand(-0.08f, -0.02f, -0.02f).turn(-2f, 34f, 8f).spin(0f, 0f, -10f)
		.at(0.58f).hand(-0.02f, 0f, 0f).turn(0f, 8f, 2f).spin(0f, 0f, -2f);

	/**
	 * The return slash: the forearm rolls the edge up while the arm swings across to the left, then the blade sweeps
	 * back across the view to the right, edge first, and the forearm rolls back.
	 */
	static final KnifeClip SLASH_BACK = new KnifeClip(0.85f)
		.at(0.09f).hand(-0.06f, 0.0f, -0.02f).turn(4f, 34f, 70f).spin(0f, 0f, -8f)
		.at(0.16f).hand(0.0f, 0.03f, -0.06f).turn(6f, 4f, 80f).spin(0f, 0f, 4f)
		.at(0.23f).hand(0.05f, 0.05f, -0.03f).turn(8f, -24f, 82f).spin(0f, 0f, 12f)
		.at(0.35f).hand(0.04f, 0.04f, -0.02f).turn(6f, -20f, 70f).spin(0f, 0f, 8f)
		.at(0.6f).hand(0.01f, 0.01f, 0f).turn(1f, -4f, 14f).spin(0f, 0f, 2f);

	/** Heavy stab of a critical hit: drawn back and raised, driven deep towards the crosshair, held, withdrawn. */
	static final KnifeClip HEAVY = new KnifeClip(1.05f)
		.at(0.14f).hand(0.04f, 0.1f, 0.2f).turn(22f, -6f, 8f).spin(0f, 0f, 18f)
		.at(0.26f).hand(-0.24f, 0.06f, -0.5f).turn(6f, 16f, 0f).spin(0f, 0f, -6f)
		.at(0.36f).hand(-0.23f, 0.05f, -0.47f).turn(5f, 15f, 0f).spin(0f, 0f, -5f)
		.at(0.52f).hand(-0.12f, 0f, -0.2f).turn(4f, 8f, 0f).spin(0f, 0f, -2f);

	// ------------------------------------------------------------------ deploys

	/** Fixed blades: up from below the view upside down, flipped over once in the fingers, settling with a small bounce. */
	private static final KnifeClip DRAW = new KnifeClip(0.96f, 3f, 0f)
		.at(0f).hand(0.08f, -0.4f, 0.08f).turn(-35f, -10f, -40f).spin(0f, 0f, 360f)
		.at(0.22f).hand(0.04f, -0.13f, 0.03f).turn(-10f, -4f, -10f).spin(0f, 0f, 60f)
		.at(0.48f).hand(-0.01f, 0.03f, -0.01f).turn(6f, 3f, 8f).spin(0f, 0f, -8f)
		.at(0.66f).hand(0f, 0f, 0f).turn(-1f, 0f, -1f).spin(0f, 0f, 2f);

	/** Karambit and talon: rising while the knife whirls once round the finger in its ring. */
	private static final KnifeClip DRAW_RING = new KnifeClip(0.96f, -5.5f, 0.1f)
		.at(0f).hand(0.08f, -0.4f, 0.08f).turn(-30f, -8f, 30f).spin(0f, 0f, -360f)
		.at(0.24f).hand(0.03f, -0.12f, 0.02f).turn(-8f, -2f, 8f).spin(0f, 0f, -120f)
		.at(0.52f).hand(-0.01f, 0.02f, 0f).turn(4f, 1f, -4f).spin(0f, 0f, 10f)
		.at(0.7f).hand(0f, 0f, 0f).turn(0f, 0f, 0f).spin(0f, 0f, 0f);

	/** Balisong: comes up closed, the bite handle swings round, then the blade flies open. */
	private static final KnifeClip DRAW_BALISONG = new KnifeClip(0.96f)
		.at(0f).hand(0.07f, -0.4f, 0.07f).turn(-30f, 0f, -20f).parts(180f, 180f)
		.at(0.22f).hand(0.02f, -0.08f, 0.02f).turn(-10f, 0f, 5f).parts(180f, 180f)
		.at(0.42f).hand(0f, 0f, 0f).turn(-4f, 0f, 10f).parts(180f, 0f)
		.at(0.58f).turn(4f, 0f, -6f).parts(0f, 0f)
		.at(0.75f).turn(0f, 0f, 0f);

	/** Folding knives: up with the blade closed, then flicked open with a snap of the wrist. */
	private static final KnifeClip DRAW_FOLDER = new KnifeClip(0.96f)
		.at(0f).hand(0.07f, -0.4f, 0.07f).turn(-25f, 0f, -30f).parts(-165f, 0f)
		.at(0.24f).hand(0.02f, -0.06f, 0.02f).turn(-5f, 0f, -8f).parts(-160f, 0f)
		.at(0.44f).hand(-0.01f, 0.02f, 0f).turn(6f, 0f, 10f).parts(10f, 0f)
		.at(0.54f).hand(0f, 0.005f, 0f).turn(1f, 0f, 2f).parts(0f, 0f);

	/** Push dagger: the fist comes up and twists the blade level. */
	private static final KnifeClip DRAW_PUSH = new KnifeClip(0.96f)
		.at(0f).hand(0.06f, -0.4f, 0.07f).turn(-40f, 0f, -40f)
		.at(0.26f).hand(0.01f, -0.04f, 0.01f).turn(-6f, 0f, -6f)
		.at(0.5f).hand(0f, 0.01f, 0f).turn(3f, 0f, 2f);

	// ------------------------------------------------------------------ inspects

	/**
	 * The second half of a forward grip's inspect, from t: raised to the eye with one flat to the light, the knife
	 * rolled over in the fingers to show the other, a slow drift on each, and back to rest. The knife's spin and the
	 * parts carry on from the trick before it (whole turns, so rest matches).
	 */
	private static KnifeClip present(KnifeClip clip, float t, float spinX, float spinZ, float partA, float partB) {
		return clip
			.at(t).hand(-0.1f, 0.12f, 0f).turn(15f, 10f, -40f).spin(spinX, 0f, spinZ).shift(0f, 0f, 0f).parts(partA, partB)
			.at(t + 0.8f).hand(-0.11f, 0.13f, -0.01f).turn(17f, 11f, -46f)
			.at(t + 1.4f).turn(17f, 11f, -40f).spin(spinX - 180f, 0f, spinZ)
			.at(t + 2.1f).hand(-0.11f, 0.12f, -0.01f).turn(18f, 12f, -34f)
			.at(t + 2.6f).hand(-0.03f, 0.03f, 0f).turn(4f, 3f, -8f).spin(spinX - 330f, 0f, spinZ)
			.at(t + 3.1f).hand(0f, 0f, 0f).turn(0f, 0f, 0f).spin(spinX - 360f, 0f, spinZ).hold();
	}

	/**
	 * Karambit and talon: raised with the knife brought out in front of the fist and whirled round the finger in the
	 * ring three times, caught, then one flat shown and the forearm rolled right over to show the other.
	 */
	private static KnifeClip ringInspect(float ringX, float ringY) {
		return new KnifeClip(5.0f, ringX, ringY)
			.at(0.35f).hand(-0.12f, 0.12f, -0.1f).turn(10f, 8f, -40f).shift(0f, 0f, 2.5f)
			.at(0.6f).hand(-0.2f, 0.06f, -0.38f).spin(0f, 0f, -60f)
			.at(1.0f).hand(-0.21f, 0.08f, -0.4f).spin(0f, 0f, -360f)
			.at(1.4f).hand(-0.2f, 0.06f, -0.4f).spin(0f, 0f, -720f)
			.at(1.8f).hand(-0.21f, 0.08f, -0.38f).spin(0f, 0f, -1050f)
			.at(1.95f).hand(-0.14f, 0.1f, -0.12f).spin(0f, 0f, -1080f).shift(0f, 0f, 0f)
			.at(2.4f).hand(-0.1f, 0.12f, 0f).turn(15f, 10f, -80f)
			.at(3.1f).hand(-0.11f, 0.13f, -0.01f).turn(17f, 11f, -88f)
			.at(3.65f).turn(17f, 11f, -160f)
			.at(4.2f).turn(18f, 12f, -166f)
			.at(4.6f).hand(-0.03f, 0.03f, 0f).turn(4f, 3f, -30f)
			.at(5.0f).hand(0f, 0f, 0f).turn(0f, 0f, 0f).spin(0f, 0f, -1080f).hold();
	}

	private static final KnifeClip INSPECT_KARAMBIT = ringInspect(-5.45f, 0.1f);
	private static final KnifeClip INSPECT_TALON = ringInspect(-5.7f, 0.15f);

	/** Skeleton: spun once round the finger in the ring at its butt, in front of the fist, then shown. */
	private static final KnifeClip INSPECT_SKELETON = present(new KnifeClip(5.0f, -6.5f, 0f)
		.at(0.35f).hand(-0.1f, 0.12f, 0f).turn(12f, 8f, -30f).shift(0f, 0f, 2.5f)
		.at(0.7f).spin(0f, 0f, -120f)
		.at(1.1f).spin(0f, 0f, -360f)
		.at(1.5f).spin(0f, 0f, -600f)
		.at(1.8f).spin(0f, 0f, -720f).shift(0f, 0f, 0f), 2.1f, 0f, -720f, 0f, 0f);

	/** Bayonet: tossed up end over end in front of the view, caught, then both flats shown. */
	private static final KnifeClip INSPECT_BAYONET = present(new KnifeClip(5.2f, 4f, 0f)
		.at(0.4f).hand(-0.08f, 0.1f, 0f).turn(10f, 8f, -30f)
		.at(0.7f).hand(-0.08f, 0.06f, 0f).turn(6f, 6f, -26f)
		.at(0.95f).hand(-0.08f, 0.14f, 0f).shift(0f, 6f, 2f).spin(0f, 0f, -150f)
		.at(1.25f).shift(0f, 11f, 2f).spin(0f, 0f, -270f)
		.at(1.55f).hand(-0.08f, 0.07f, 0f).shift(0f, 0f, 0f).spin(0f, 0f, -360f)
		.at(1.75f).hand(-0.08f, 0.1f, 0f), 2.1f, 0f, -360f, 0f, 0f);

	/** Butterfly: flipped closed and open again twice with the handles fanning round the pivots, then shown. */
	private static final KnifeClip INSPECT_BUTTERFLY = present(new KnifeClip(5.5f)
		.at(0.4f).hand(-0.1f, 0.12f, 0f).turn(12f, 10f, -40f)
		.at(0.6f).parts(180f, 0f)
		.at(0.85f).parts(180f, 180f).turn(8f, 8f, -46f)
		.at(1.1f).parts(360f, 180f).turn(14f, 10f, -36f)
		.at(1.35f).parts(360f, 360f)
		.at(1.7f).parts(540f, 360f).turn(10f, 8f, -46f)
		.at(1.95f).parts(540f, 540f)
		.at(2.2f).parts(720f, 540f).turn(14f, 10f, -36f)
		.at(2.45f).parts(720f, 720f), 2.75f, 0f, 0f, 720f, 720f);

	/** Flip knife: flicked round the index finger twice in front of the fist, then both flats shown. */
	private static final KnifeClip INSPECT_FLIP = present(new KnifeClip(4.9f, 0.5f, 0f)
		.at(0.35f).hand(-0.08f, 0.1f, 0f).turn(12f, 10f, -30f).shift(0f, 0f, 1.5f)
		.at(0.6f).spin(0f, 0f, -60f)
		.at(0.85f).spin(0f, 0f, -360f).hand(-0.08f, 0.12f, 0f)
		.at(1.1f).spin(0f, 0f, -420f)
		.at(1.35f).spin(0f, 0f, -720f).hand(-0.08f, 0.09f, 0f).shift(0f, 0f, 0f), 1.8f, 0f, -720f, 0f, 0f);

	/** Gut knife: rolled through the fingers about its blade twice, then the hook shown. */
	private static final KnifeClip INSPECT_GUT = present(new KnifeClip(5.0f)
		.at(0.35f).hand(-0.08f, 0.1f, 0f).turn(12f, 10f, -30f)
		.at(0.75f).spin(-180f, 0f, 0f)
		.at(1.1f).spin(-360f, 0f, 0f)
		.at(1.45f).spin(-540f, 0f, 0f)
		.at(1.8f).spin(-720f, 0f, 0f), 2.1f, -720f, 0f, 0f, 0f);

	/** Big fixed blades: raised slowly and turned to the light, then both flats shown. */
	private static final KnifeClip INSPECT_ADMIRE = present(new KnifeClip(4.6f)
		.at(0.6f).hand(-0.07f, 0.1f, 0f).turn(10f, 8f, -24f)
		.at(1.2f).hand(-0.09f, 0.11f, 0f).turn(13f, 9f, -34f), 1.5f, 0f, 0f, 0f, 0f);

	/** Kukri: swung round point-down and back over, heavy and slow, then both flats shown. */
	private static final KnifeClip INSPECT_KUKRI = present(new KnifeClip(5.2f, 2f, 0f)
		.at(0.4f).hand(-0.06f, 0.08f, 0f).turn(10f, 8f, -24f).shift(0f, 0f, 2f)
		.at(0.95f).hand(-0.07f, 0.06f, 0f).spin(0f, 0f, -180f)
		.at(1.55f).hand(-0.07f, 0.1f, 0f).spin(0f, 0f, -360f).shift(0f, 0f, 0f), 2.0f, 0f, -360f, 0f, 0f);

	/** Folding knives: the blade folds shut and is flicked back open twice, then both flats shown. */
	private static KnifeClip folderInspect(float closed) {
		return present(new KnifeClip(5.0f)
			.at(0.35f).hand(-0.08f, 0.1f, 0f).turn(12f, 10f, -30f)
			.at(0.7f).parts(closed, 0f).turn(10f, 10f, -36f)
			.at(0.85f).parts(closed, 0f)
			.at(0.97f).parts(10f, 0f).turn(14f, 10f, -24f)
			.at(1.07f).parts(0f, 0f)
			.at(1.45f).parts(closed * 0.8f, 0f).turn(10f, 10f, -34f)
			.at(1.58f).parts(6f, 0f).turn(14f, 12f, -26f)
			.at(1.68f).parts(0f, 0f), 2.0f, 0f, 0f, 0f, 0f);
	}

	private static final KnifeClip INSPECT_STILETTO = folderInspect(-165f);
	private static final KnifeClip INSPECT_FALCHION = folderInspect(-110f);

	/** Push dagger: the fist turns the blade over to look at it, then two quick jabs, then both flats shown. */
	private static final KnifeClip INSPECT_PUSH = present(new KnifeClip(5.0f)
		.at(0.4f).hand(-0.06f, 0.08f, 0f).turn(10f, 8f, -30f)
		.at(0.9f).hand(-0.14f, 0.06f, -0.2f).turn(6f, 10f, -20f)
		.at(1.05f).hand(-0.05f, 0.07f, 0f).turn(10f, 8f, -28f)
		.at(1.25f).hand(-0.15f, 0.06f, -0.22f).turn(6f, 10f, -20f)
		.at(1.45f).hand(-0.07f, 0.08f, 0f).turn(12f, 10f, -30f), 1.8f, 0f, 0f, 0f, 0f);

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
