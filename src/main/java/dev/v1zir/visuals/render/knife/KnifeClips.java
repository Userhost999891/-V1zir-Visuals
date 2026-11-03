package dev.v1zir.visuals.render.knife;

/**
 * The choreography, timed like a modern tactical shooter's knives: a deploy of about a second, slashes that cross the
 * view in a fraction of that and settle back over the rest of a second, a heavy stab, and inspects of five seconds in
 * phases (raise, a trick in the fingers, one flat shown, the forearm rolled over to the other, back to rest).
 * <p>
 * Channels (see {@link KnifeClip}): hand = the shoulder carrying the arm (view space, blocks: x right, y up, z towards
 * the viewer); turn = pitch about the elbow (lifts the fist), yaw about the elbow (swings it left) and the forearm's
 * roll about its own axis (shows the flats); swing = the whole arm swung from the shoulder behind the view (yaw left,
 * pitch up); aim = arm and knife turned together about the fist (yaw left, pitch up); spin and shift = the knife in the
 * fingers about the clip's pivot (degrees about the knife's axes: x along the blade, y towards the spine, z through the
 * flat; a negative z spin tips the point forward and down). Spins and part angles end on whole turns so a finished clip
 * hands over to rest without a jump.
 * <p>
 * Directions are kept consistent per knife: a knife that whirls round a finger does so the same way in its draw, its
 * inspect and its attacks (a karambit's claw goes forward and up over the top), and every slash leads with the edge
 * (a karambit's with the point of the claw, so the inner edge draws through).
 */
final class KnifeClips {
	// ------------------------------------------------------------------ attacks
	// Slashes swing the whole arm from the shoulder diagonally across the view, edge first, fast at first and easing
	// out, then rebound and settle; the first one after a pause always comes from the right, a quick follow-up comes
	// back from the left. Heavy stabs drive the point at the crosshair.

	/**
	 * Forward grips, forehand: the wrist cocks the point up as the hand rises to the right, then hand, forearm and
	 * wrist whip the blade over the top and across to the left, edge first, fast at first and easing out; a short
	 * rebound, then it settles.
	 */
	private static final KnifeClip SLASH = new KnifeClip(0.85f)
		.at(0.055f).hand(0f, 0.04f, 0f).swing(-18f, 3f, 0f).aim(14f, 3f).spin(0f, 0f, 26f)
		.at(0.1f).hand(0f, 0.06f, 0f).swing(-3f, 2f, 0f).aim(2f, 2f).spin(0f, 0f, 20f)
		.at(0.15f).hand(0f, 0.08f, 0f).swing(14f, 1f, 0f).aim(-11f, 1f).spin(0f, 0f, 10f)
		.at(0.2f).hand(0f, 0.1f, 0f).swing(29f, -1f, 0f).aim(-23f, 0f).spin(0f, 0f, -2f)
		.at(0.26f).hand(0f, 0.1f, 0f).swing(38f, -3f, 0f).aim(-30f, -1f).spin(0f, 0f, -10f)
		.at(0.34f).hand(0f, 0.08f, 0f).swing(41f, -5f, 0f).aim(-33f, -2f).spin(0f, 0f, -14f)
		.at(0.46f).hand(0f, 0.03f, 0f).swing(25f, -6f, 0f).aim(-20f, -2f).spin(0f, 0f, -9f)
		.at(0.62f).hand(0f, 0f, 0f).swing(6f, -2f, 0f).aim(-5f, -1f).spin(0f, 0f, -2f);

	/**
	 * Forward grips, backhand: the wrist turns the knife over (edge up) as the hand cocks to the left, then the blade
	 * sweeps back up across the view to the right, edge first, and the wrist turns it back on the way to rest.
	 */
	private static final KnifeClip SLASH_BACK = new KnifeClip(0.85f)
		.at(0.07f).hand(-0.02f, -0.02f, 0f).swing(24f, -3f, 0f).aim(-18f, -2f).spin(160f, 0f, 12f)
		.at(0.11f).hand(-0.01f, -0.01f, 0f).swing(13f, -1f, 0f).aim(-10f, -1f).spin(168f, 0f, 6f)
		.at(0.16f).hand(0f, 0.02f, 0f).swing(0f, 2f, 0f).aim(0f, 1f).spin(172f, 0f, -3f)
		.at(0.21f).hand(0f, 0.05f, 0f).swing(-11f, 5f, 0f).aim(9f, 3f).spin(174f, 0f, -11f)
		.at(0.27f).hand(0f, 0.07f, 0f).swing(-17f, 7f, 0f).aim(13f, 4f).spin(175f, 0f, -16f)
		.at(0.35f).hand(0f, 0.07f, 0f).swing(-19f, 8f, 0f).aim(15f, 4f).spin(172f, 0f, -18f)
		.at(0.5f).hand(0f, 0.03f, 0f).swing(-10f, 4f, 0f).aim(8f, 2f).spin(110f, 0f, -9f)
		.at(0.66f).hand(0f, 0f, 0f).swing(-2f, 1f, 0f).aim(2f, 0f).spin(24f, 0f, -2f);

	/**
	 * Forward grips, heavy stab: drawn back and up beside the head, then driven point first down at the crosshair,
	 * held there a moment, withdrawn.
	 */
	private static final KnifeClip HEAVY = new KnifeClip(1.0f)
		.at(0.15f).hand(0.06f, 0.05f, 0.22f).swing(-8f, 10f, 0f).aim(-38f, 18f).spin(0f, 0f, 10f)
		.at(0.21f).hand(0.03f, 0.04f, 0.07f).swing(-3f, 5f, 0f).aim(-40f, 7f).spin(0f, 0f, 4f)
		.at(0.26f).hand(-0.04f, 0.02f, -0.3f).swing(4f, -1f, 0f).aim(-42f, -3f).spin(0f, 0f, -2f)
		.at(0.37f).hand(-0.05f, 0.01f, -0.33f).swing(5f, -2f, 0f).aim(-43f, -4f).spin(0f, 0f, -3f)
		.at(0.56f).hand(-0.02f, 0f, -0.12f).swing(2f, -1f, 0f).aim(-22f, -2f).spin(0f, 0f, -1f)
		.at(0.76f).hand(0f, 0f, 0f).swing(0f, 0f, 0f).aim(-4f, 0f).spin(0f, 0f, 0f);

	/**
	 * Karambit and talon, forehand: a hooking rake from the right across to the lower left, the claw leading so its
	 * point bites first and the inner edge draws through.
	 */
	private static final KnifeClip SLASH_RING = new KnifeClip(0.85f, -5.5f, 0.1f)
		.at(0.055f).hand(0f, 0.05f, 0f).swing(-18f, 4f, 0f).aim(14f, 4f).spin(0f, 0f, 14f)
		.at(0.1f).hand(0f, 0.07f, 0f).swing(-3f, 3f, 0f).aim(2f, 3f).spin(0f, 0f, 8f)
		.at(0.15f).hand(0f, 0.09f, 0f).swing(14f, 1f, 0f).aim(-11f, 1f).spin(0f, 0f, 0f)
		.at(0.2f).hand(0f, 0.1f, 0f).swing(29f, -1f, 0f).aim(-23f, 0f).spin(0f, 0f, -8f)
		.at(0.26f).hand(0f, 0.1f, 0f).swing(38f, -3f, 0f).aim(-30f, -1f).spin(0f, 0f, -14f)
		.at(0.34f).hand(0f, 0.08f, 0f).swing(41f, -5f, 0f).aim(-33f, -2f).spin(0f, 0f, -17f)
		.at(0.46f).hand(0f, 0.03f, 0f).swing(25f, -6f, 0f).aim(-20f, -2f).spin(0f, 0f, -10f)
		.at(0.62f).hand(0f, 0f, 0f).swing(6f, -2f, 0f).aim(-5f, -1f).spin(0f, 0f, -2f);

	/**
	 * Karambit and talon, backhand: the forearm rolls the fist over so the claw stands up and points right, and it is
	 * raked back across to the right, claw first; the knife stays put in the hand, the forearm rolls back on the way
	 * to rest.
	 */
	private static final KnifeClip SLASH_RING_BACK = new KnifeClip(0.85f, -5.5f, 0.1f)
		.at(0.08f).hand(-0.02f, 0.02f, 0f).swing(20f, -2f, 0f).aim(-15f, -1f).turn(0f, 0f, 135f)
		.at(0.12f).hand(-0.01f, 0.03f, 0f).swing(10f, 0f, 0f).aim(-8f, 0f).turn(0f, 0f, 145f)
		.at(0.17f).hand(0f, 0.05f, 0f).swing(-1f, 2f, 0f).aim(1f, 1f).turn(0f, 0f, 150f)
		.at(0.22f).hand(0f, 0.06f, 0f).swing(-8f, 3f, 0f).aim(6f, 2f).turn(0f, 0f, 152f)
		.at(0.28f).hand(0f, 0.06f, 0f).swing(-11f, 4f, 0f).aim(9f, 2f).turn(0f, 0f, 150f)
		.at(0.36f).hand(0f, 0.05f, 0f).swing(-12f, 4f, 0f).aim(10f, 2f).turn(0f, 0f, 144f)
		.at(0.52f).hand(0f, 0.02f, 0f).swing(-6f, 2f, 0f).aim(5f, 1f).turn(0f, 0f, 60f)
		.at(0.68f).hand(0f, 0f, 0f).swing(-1f, 0f, 0f).aim(1f, 0f).turn(0f, 0f, 10f);

	/** Karambit and talon, heavy: raised high, then the claw hooked down and forward through the crosshair. */
	private static final KnifeClip HEAVY_RING = new KnifeClip(1.0f, -5.5f, 0.1f)
		.at(0.16f).hand(0.02f, 0.14f, 0.12f).swing(-4f, 16f, 0f).aim(-20f, 10f).spin(0f, 0f, 20f)
		.at(0.22f).hand(0f, 0.1f, 0f).swing(2f, 8f, 0f).aim(-24f, 0f).spin(0f, 0f, 6f)
		.at(0.28f).hand(-0.06f, -0.02f, -0.26f).swing(8f, -6f, 0f).aim(-28f, -12f).spin(0f, 0f, -14f)
		.at(0.37f).hand(-0.06f, -0.03f, -0.28f).swing(8f, -7f, 0f).aim(-28f, -13f).spin(0f, 0f, -16f)
		.at(0.56f).hand(-0.02f, -0.01f, -0.1f).swing(3f, -3f, 0f).aim(-12f, -5f).spin(0f, 0f, -6f)
		.at(0.76f).hand(0f, 0f, 0f).swing(1f, 0f, 0f).aim(-2f, 0f).spin(0f, 0f, 0f);

	/** Push dagger: a jab that rakes the blade across to the left. */
	private static final KnifeClip SLASH_PUSH = new KnifeClip(0.7f)
		.at(0.08f).hand(0.04f, 0.03f, 0.1f).swing(-10f, 4f, 0f).aim(-20f, 4f)
		.at(0.15f).hand(-0.04f, 0.02f, -0.24f).swing(10f, 0f, 0f).aim(-26f, 0f)
		.at(0.2f).hand(-0.05f, 0.01f, -0.25f).swing(20f, -3f, 0f).aim(-24f, -2f)
		.at(0.28f).hand(-0.03f, 0f, -0.16f).swing(22f, -5f, 0f).aim(-18f, -2f)
		.at(0.46f).hand(0f, 0f, -0.03f).swing(8f, -3f, 0f).aim(-5f, 0f);

	/** Push dagger: a hooked jab that rakes the blade back to the right. */
	private static final KnifeClip SLASH_PUSH_BACK = new KnifeClip(0.7f)
		.at(0.08f).hand(-0.02f, 0.03f, 0.08f).swing(16f, 3f, 0f).aim(-30f, 4f).turn(0f, 0f, 30f)
		.at(0.15f).hand(-0.03f, 0.03f, -0.22f).swing(0f, 2f, 0f).aim(-32f, 2f)
		.at(0.2f).hand(-0.03f, 0.02f, -0.24f).swing(-10f, 1f, 0f).aim(-30f, 0f)
		.at(0.28f).hand(-0.02f, 0.01f, -0.16f).swing(-12f, 0f, 0f).aim(-22f, 0f).turn(0f, 0f, 24f)
		.at(0.46f).hand(0f, 0f, -0.03f).swing(-4f, 0f, 0f).aim(-6f, 0f).turn(0f, 0f, 6f);

	/** Push dagger, heavy: wound back, then a straight punch that drives the blade at the crosshair. */
	private static final KnifeClip HEAVY_PUSH = new KnifeClip(0.95f)
		.at(0.15f).hand(0.06f, 0.02f, 0.22f).swing(-8f, 4f, 0f).aim(-30f, 8f)
		.at(0.21f).hand(0.02f, 0.02f, 0.05f).swing(-2f, 2f, 0f).aim(-36f, 2f)
		.at(0.26f).hand(-0.08f, 0.03f, -0.34f).swing(6f, 0f, 0f).aim(-40f, -2f)
		.at(0.35f).hand(-0.08f, 0.03f, -0.36f).swing(6f, -1f, 0f).aim(-40f, -3f)
		.at(0.54f).hand(-0.02f, 0.01f, -0.1f).swing(2f, 0f, 0f).aim(-14f, -1f)
		.at(0.74f).hand(0f, 0f, 0f).swing(0f, 0f, 0f).aim(-2f, 0f);

	static KnifeClip slash(KnifeType type, boolean back) {
		return switch (type.grip) {
			case REVERSE -> back ? SLASH_RING_BACK : SLASH_RING;
			case PUSH -> back ? SLASH_PUSH_BACK : SLASH_PUSH;
			case FORWARD -> back ? SLASH_BACK : SLASH;
		};
	}

	static KnifeClip heavy(KnifeType type) {
		return switch (type.grip) {
			case REVERSE -> HEAVY_RING;
			case PUSH -> HEAVY_PUSH;
			case FORWARD -> HEAVY;
		};
	}

	// ------------------------------------------------------------------ deploys

	/** Fixed blades: up from below the view upside down, flipped over once in the fingers, settling with a small bounce. */
	private static final KnifeClip DRAW = new KnifeClip(0.96f, 3f, 0f)
		.at(0f).hand(0.08f, -0.4f, 0.08f).turn(-35f, -10f, -40f).spin(0f, 0f, 360f)
		.at(0.22f).hand(0.04f, -0.13f, 0.03f).turn(-10f, -4f, -10f).spin(0f, 0f, 60f)
		.at(0.48f).hand(-0.01f, 0.03f, -0.01f).turn(6f, 3f, 8f).spin(0f, 0f, -8f)
		.at(0.66f).hand(0f, 0f, 0f).turn(-1f, 0f, -1f).spin(0f, 0f, 2f);

	/**
	 * Karambit and talon: rising while the knife whirls once round the finger in its ring, the claw going forward and
	 * up over the top the same way as the spins of the inspect.
	 */
	private static final KnifeClip DRAW_RING = new KnifeClip(0.96f, -5.5f, 0.1f)
		.at(0f).hand(0.08f, -0.4f, 0.08f).turn(-30f, -8f, 30f).spin(0f, 0f, 360f)
		.at(0.24f).hand(0.03f, -0.12f, 0.02f).turn(-8f, -2f, 8f).spin(0f, 0f, 120f)
		.at(0.52f).hand(-0.01f, 0.02f, 0f).turn(4f, 1f, -4f).spin(0f, 0f, -10f)
		.at(0.7f).hand(0f, 0f, 0f).turn(0f, 0f, 0f).spin(0f, 0f, 0f);

	/** Skeleton: rising with the knife whirled once round the finger in the ring at its butt, like its inspect. */
	private static final KnifeClip DRAW_SKELETON = new KnifeClip(0.96f, -6.5f, 0f)
		.at(0f).hand(0.08f, -0.4f, 0.08f).turn(-35f, -10f, -40f).spin(0f, 0f, 360f)
		.at(0.24f).hand(0.04f, -0.12f, 0.03f).turn(-10f, -4f, -10f).spin(0f, 0f, 110f)
		.at(0.5f).hand(-0.01f, 0.03f, -0.01f).turn(6f, 3f, 8f).spin(0f, 0f, -8f)
		.at(0.68f).hand(0f, 0f, 0f).turn(-1f, 0f, -1f).spin(0f, 0f, 2f);

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
			.at(t).hand(-0.1f, 0.07f, 0f).turn(10f, 10f, -40f).spin(spinX, 0f, spinZ).shift(0f, 0f, 0f).parts(partA, partB)
			.at(t + 0.8f).hand(-0.11f, 0.08f, -0.01f).turn(12f, 11f, -46f)
			.at(t + 1.4f).turn(12f, 11f, -40f).spin(spinX - 180f, 0f, spinZ)
			.at(t + 2.1f).hand(-0.11f, 0.07f, -0.01f).turn(13f, 12f, -34f)
			.at(t + 2.6f).hand(-0.03f, 0.02f, 0f).turn(3f, 3f, -8f).spin(spinX - 330f, 0f, spinZ)
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
			.at(1.95f).hand(-0.14f, 0.08f, -0.12f).spin(0f, 0f, -1080f).shift(0f, 0f, 0f)
			.at(2.4f).hand(-0.1f, 0.07f, 0f).turn(9f, 10f, -80f)
			.at(3.1f).hand(-0.11f, 0.08f, -0.01f).turn(11f, 11f, -88f)
			.at(3.65f).hand(-0.16f, 0.02f, -0.02f).turn(6f, 11f, -150f)
			.at(4.2f).hand(-0.17f, 0.02f, -0.02f).turn(7f, 12f, -156f)
			.at(4.6f).hand(-0.04f, 0.01f, 0f).turn(2f, 3f, -30f)
			.at(5.0f).hand(0f, 0f, 0f).turn(0f, 0f, 0f).spin(0f, 0f, -1080f).hold();
	}

	private static final KnifeClip INSPECT_KARAMBIT = ringInspect(-5.45f, 0.1f);
	private static final KnifeClip INSPECT_TALON = ringInspect(-5.7f, 0.15f);

	/** Skeleton: spun once round the finger in the ring at its butt, in front of the fist, then shown. */
	private static final KnifeClip INSPECT_SKELETON = present(new KnifeClip(5.0f, -6.5f, 0f)
		.at(0.35f).hand(-0.12f, 0.1f, -0.1f).turn(10f, 8f, -30f).shift(0f, 0f, 2.5f)
		.at(0.6f).hand(-0.2f, 0.16f, -0.4f).spin(0f, 0f, -90f)
		.at(1.0f).hand(-0.21f, 0.17f, -0.42f).spin(0f, 0f, -360f)
		.at(1.4f).hand(-0.2f, 0.16f, -0.42f).spin(0f, 0f, -630f)
		.at(1.75f).hand(-0.15f, 0.1f, -0.15f).spin(0f, 0f, -720f).shift(0f, 0f, 0f), 2.1f, 0f, -720f, 0f, 0f);

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
			case KARAMBIT, TALON -> DRAW_RING;
			case SKELETON -> DRAW_SKELETON;
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
