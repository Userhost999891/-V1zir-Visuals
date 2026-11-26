package dev.v1zir.visuals.module.impl.hud;

import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.font.SdfFont;
import dev.v1zir.visuals.render.world.WorldEffectRenderer;
import dev.v1zir.visuals.render.world.WorldText;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The 3D preset of Spotify Lyrics, in three looks:
 * <ul>
 *   <li>Scatter: every word of the line floats on its own at its own spot around the view (left to
 *   right in reading order, spread up and down and in depth, a little tilted, in varied sizes). A word
 *   pops in when it is sung with a flare of glow in the accent around a hot white core and a glint
 *   across it, and dissolves (thinning, drifting up) once the line is over.</li>
 *   <li>Shine: the line a few blocks ahead, a little above the eyes. Words come in one by one in time
 *   with the song (onsets estimated from the line's duration, weighted by word length): the next word
 *   shows up small, thin and faint just before it is sung, then springs to full size while a glint
 *   sweeps across it and its glow flares; long words throw a few sparks. A slow shine keeps passing
 *   over the playing line.</li>
 *   <li>Extruded: every line appears at a new spot in the field of view (alternating sides and
 *   heights) as extruded letters that pop in one after another.</li>
 * </ul>
 * Lines and words stay where they appear in the world: walk on or turn away and they are left behind.
 * They appear along the full view direction (looking down puts them below, looking up above) at the
 * set distance plus about a second of the camera's motion, so a moving or falling player sees them in
 * time, and they keep turning to face the camera in yaw and pitch, so they read from above and below.
 * They are the topmost layer (WorldText overlay: through blocks and over the hand), fade out before the
 * camera gets close enough for the near plane to cut them, and grow a little with distance so far ones
 * stay readable. Lines are laid out once when they spawn; frames only move letters and words.
 */
final class Lyrics3D {
	enum Look {
		SCATTER, SHINE, EXTRUDED
	}

	private static final float LETTER_DELAY = 0.032f;
	private static final float LEAVE_DELAY = 0.018f;
	/** Widest row in blocks before a line wraps: extruded lines sit to the side, shiny ones in the middle. */
	private static final float MAX_WIDTH = 2.8f;
	private static final float SHINE_WIDTH = 4.4f;
	private static final float[] SIDES = {-22f, 18f, -8f, 26f, -29f, 10f};
	private static final float[] HEIGHTS = {0.35f, -0.2f, 0.6f, 0.05f, -0.3f, 0.45f};
	/** Shiny lines use a fraction of SIDES, so they stay nearly straight ahead. */
	private static final float SHINE_SPREAD = 0.3f;
	/** A new shiny line rises this far (blocks) into its spot. */
	private static final float RISE = 0.18f;
	/** Gentle float of a line or word in its spot: amplitude in blocks and speed in radians per second. */
	private static final float FLOAT = 0.035f;
	private static final float FLOAT_SPEED = 1.3f;
	/** Shiny lines float this far above eye height, in blocks. */
	private static final float LIFT = 0.12f;
	/** Longest time per letter when word onsets are spread over a line, so a line before a long pause is not sung slowly. */
	private static final float SECONDS_PER_LETTER = 0.11f;
	/** How long before its onset the next shiny word shows up, and how far in it is until then. */
	private static final float LEAD = 0.28f;
	private static final float PREVIEW = 0.3f;
	/** Half widths of the glint across a sung word and of the slow shine, in letter heights. */
	private static final float GLINT = 0.45f;
	private static final float SWEEP_BAND = 0.7f;
	/** Seconds the slow shine takes to cross the playing line. */
	private static final float SWEEP = 3.2f;
	/** Cool white of the letters; the shine turns it pure white, the gradient leans to the accent. */
	private static final int SILVER = 0xFFE2E6EE;
	/** Words with this many letters throw sparks when they are sung. */
	private static final int SPARK_LETTERS = 6;
	private static final int SPARKS = 48;
	private static final int SPARKS_PER_WORD = 7;
	private static final float SPARK_LIFE = 0.8f;

	/** Seconds of camera motion a new line or word is placed ahead by, and the limits of that lead. */
	private static final float MOTION_LEAD = 1f;
	private static final float MAX_AHEAD = 12f;
	/** Sideways lead per block of distance (about 24 degrees), so a strafing or falling player still has it in view. */
	private static final float MAX_SIDEWAYS = 0.45f;
	/** Time constant of the smoothed camera velocity, in seconds. */
	private static final float VELOCITY_SMOOTHING = 0.25f;
	/** Spring speed of the turn towards the camera. */
	private static final float TURN = 5f;
	/** Letters fade out between these distances from the camera, long before the near plane could cut them. */
	private static final float NEAR = 0.9f;
	private static final float NEAR_FADE = 1.1f;
	/** Size grows with distance beyond the set one to this power, so far text stays readable but still recedes. */
	private static final float GROW = 0.6f;

	/** Scatter: half the spread of the words around the view (degrees) and of their depth (fraction of the distance). */
	private static final float SCATTER_YAW = 24f;
	private static final float SCATTER_PITCH = 11f;
	private static final float SCATTER_DEPTH = 0.45f;
	/** Space between scattered words, in letter heights, before they are nudged about. */
	private static final float SCATTER_GAP = 0.8f;
	/** Scatter words pop in this much before their onset, so they are in place on the beat. */
	private static final float POP_LEAD = 0.06f;
	private static final float WORD_LEAVE_DELAY = 0.07f;
	/** Scatter words with this many letters throw sparks, a few each. */
	private static final int SCATTER_SPARK_LETTERS = 5;
	private static final int SCATTER_SPARKS = 4;

	/** A text plane that turns on springs to face the camera, upright on screen; a tilt leans it a little out of that. */
	private static final class Plane {
		final Anim nx = new Anim(0f, TURN);
		final Anim ny = new Anim(0f, TURN);
		final Anim nz = new Anim(1f, TURN);
		final Anim ux = new Anim(0f, TURN);
		final Anim uy = new Anim(1f, TURN);
		final Anim uz = new Anim(0f, TURN);
		final Vector3f right = new Vector3f(1f, 0f, 0f);
		final Vector3f up = new Vector3f(0f, 1f, 0f);
		boolean faced;

		/** Turns towards the camera from (x, y, z); yaw, pitch and roll (radians, small) tilt it away from facing it squarely. */
		void face(double x, double y, double z, float yaw, float pitch, float roll) {
			float dx = (float) (WorldEffectRenderer.camX - x);
			float dy = (float) (WorldEffectRenderer.camY - y);
			float dz = (float) (WorldEffectRenderer.camZ - z);
			float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (len < 1e-4f) return;
			dx /= len;
			dy /= len;
			dz /= len;
			// Upright in the world until the camera looks (nearly) straight down or up at it: world up has no
			// direction left in the plane there, so the camera's own up takes over and it still reads on screen.
			Vector3f cam = WorldEffectRenderer.UP;
			float w = smoothstep(0.8f, 0.97f, Math.abs(dy));
			float rx = cam.x * w;
			float ry = 1f - w + cam.y * w;
			float rz = cam.z * w;
			float dot = rx * dx + ry * dy + rz * dz;
			float tx = rx - dx * dot;
			float ty = ry - dy * dot;
			float tz = rz - dz * dot;
			if (!faced) {
				faced = true;
				snap(dx, dy, dz, tx, ty, tz);
			}
			float cnx = nx.target(dx).get();
			float cny = ny.target(dy).get();
			float cnz = nz.target(dz).get();
			float n = (float) Math.sqrt(cnx * cnx + cny * cny + cnz * cnz);
			if (n < 0.2f) {
				// Turned through itself faster than the springs follow (the camera passed through it): start over.
				snap(dx, dy, dz, tx, ty, tz);
				cnx = dx;
				cny = dy;
				cnz = dz;
				n = 1f;
			}
			cnx /= n;
			cny /= n;
			cnz /= n;
			// Lean out of facing the camera by the tilt (small angles: the right and up directions added).
			float cux = ux.target(tx).get();
			float cuy = uy.target(ty).get();
			float cuz = uz.target(tz).get();
			float d = cux * cnx + cuy * cny + cuz * cnz;
			cux -= cnx * d;
			cuy -= cny * d;
			cuz -= cnz * d;
			float u = Math.max((float) Math.sqrt(cux * cux + cuy * cuy + cuz * cuz), 1e-4f);
			cux /= u;
			cuy /= u;
			cuz /= u;
			float crx = cuy * cnz - cuz * cny;
			float cry = cuz * cnx - cux * cnz;
			float crz = cux * cny - cuy * cnx;
			if (yaw != 0f || pitch != 0f) {
				cnx += crx * yaw + cux * pitch;
				cny += cry * yaw + cuy * pitch;
				cnz += crz * yaw + cuz * pitch;
				n = (float) Math.sqrt(cnx * cnx + cny * cny + cnz * cnz);
				cnx /= n;
				cny /= n;
				cnz /= n;
				d = cux * cnx + cuy * cny + cuz * cnz;
				cux -= cnx * d;
				cuy -= cny * d;
				cuz -= cnz * d;
				u = (float) Math.sqrt(cux * cux + cuy * cuy + cuz * cuz);
				cux /= u;
				cuy /= u;
				cuz /= u;
				crx = cuy * cnz - cuz * cny;
				cry = cuz * cnx - cux * cnz;
				crz = cux * cny - cuy * cnx;
			}
			float c = (float) Math.cos(roll);
			float s = (float) Math.sin(roll);
			right.set(crx * c + cux * s, cry * c + cuy * s, crz * c + cuz * s);
			up.set(cux * c - crx * s, cuy * c - cry * s, cuz * c - crz * s);
		}

		private void snap(float dx, float dy, float dz, float tx, float ty, float tz) {
			nx.snap(dx);
			ny.snap(dy);
			nz.snap(dz);
			ux.snap(tx);
			uy.snap(ty);
			uz.snap(tz);
		}
	}

	/** A word of a shiny line; x and y in letter heights like its letters. */
	private static final class Word {
		final float left;
		final float right;
		final float y;
		/** Onset in seconds from the start of the line. */
		final float time;
		final boolean sparks;
		final Anim in = new Anim(0f, 9f);
		/** Snaps to 1 at the onset and springs back to 0: carries the glint across the word and the glow flare. */
		final Anim flash = new Anim(0f, 4.5f);
		boolean sung;

		Word(float left, float right, float y, float time, boolean sparks) {
			this.left = left;
			this.right = right;
			this.y = y;
			this.time = time;
			this.sparks = sparks;
		}
	}

	private static final class Letter {
		final String text;
		/** Position in the line plane (blocks at size 1): x from the centre, y down from the first row. */
		final float x;
		final float y;
		/** The word it belongs to on shiny lines, null on extruded ones. */
		final Word word;
		/** Where its left and right edges sit across its row (0 to 1), for the gradient. */
		final float t0;
		final float t1;
		final float delay;
		final float leaveDelay;
		/** Direction the letter scatters in when the line dissolves. */
		final float sx;
		final float sy;
		final float sz;
		final Anim in = new Anim(0f, 8f);
		final Anim out = new Anim(0f, 3f);

		Letter(String text, float x, float y, Word word, float t0, float t1, float delay, float leaveDelay, ThreadLocalRandom random) {
			this.text = text;
			this.x = x;
			this.y = y;
			this.word = word;
			this.t0 = t0;
			this.t1 = t1;
			this.delay = delay;
			this.leaveDelay = leaveDelay;
			this.sx = random.nextFloat() * 2f - 1f;
			this.sy = random.nextFloat() * 0.8f;
			this.sz = random.nextFloat() * 2f - 1f;
		}
	}

	/** A word of a Scatter line: it finds its own spot when it is sung, with its own size, tilt and turn. */
	private static final class Floater {
		final String text;
		/** Onset in seconds from the start of the line. */
		final float time;
		/** Width at size 1 (measured once). */
		final float width;
		final float scale;
		/** Where it appears: angles off the view direction (radians) and a factor on the distance. */
		final float spotYaw;
		final float spotPitch;
		final float depth;
		/** Its lean out of facing the camera squarely (radians). */
		final float yaw;
		final float pitch;
		final float roll;
		final float phase;
		final float leaveDelay;
		final boolean sparks;
		final Plane plane = new Plane();
		final Anim in = new Anim(0f, 10f);
		final Anim flash = new Anim(0f, 4f);
		final Anim out = new Anim(0f, 2.6f);
		boolean placed;
		double x;
		double y;
		double z;
		long placedAt;

		Floater(String text, float time, float width, float spotYaw, float spotPitch, int index, ThreadLocalRandom random) {
			this.text = text;
			this.time = time;
			this.width = width;
			this.spotYaw = spotYaw;
			this.spotPitch = spotPitch;
			int letters = text.codePointCount(0, text.length());
			scale = (0.82f + 0.36f * random.nextFloat()) * (letters >= 7 ? 1.12f : 1f);
			depth = 1f - SCATTER_DEPTH * 0.4f + SCATTER_DEPTH * random.nextFloat();
			yaw = (random.nextFloat() - 0.5f) * 0.4f;
			pitch = (random.nextFloat() - 0.5f) * 0.28f;
			roll = (random.nextFloat() - 0.5f) * 0.22f;
			phase = random.nextFloat() * 6.2832f;
			leaveDelay = index * WORD_LEAVE_DELAY;
			sparks = letters >= SCATTER_SPARK_LETTERS;
		}
	}

	private static final class Line {
		final Look look;
		final Letter[] letters;
		final Word[] words;
		final Floater[] floaters;
		/** Half the widest row, in letter heights. */
		final float half;
		/** Where it appeared; it rises into place and floats along lift, the camera's up then. */
		final double ox;
		final double oy;
		final double oz;
		final float liftX;
		final float liftY;
		final float liftZ;
		/** The set distance when it appeared, which its size grows from. */
		final float distance;
		/** Phase of the gentle float, so neighbouring lines do not bob in step. */
		final float phase;
		final Anim rise = new Anim(0f, 1.8f);
		final Plane plane = new Plane();
		final long born = System.nanoTime();
		long left = -1L;
		/** Song time into the line (seconds); frozen once it leaves. */
		float clock;
		/** Anchor and letter size of the last frame. */
		double wx;
		double wy;
		double wz;
		float k;

		Line(Look look, Letter[] letters, Word[] words, Floater[] floaters, float half, double x, double y, double z, float distance) {
			this.look = look;
			this.letters = letters;
			this.words = words;
			this.floaters = floaters;
			this.half = half;
			this.ox = x;
			this.oy = y;
			this.oz = z;
			Vector3f up = WorldEffectRenderer.UP;
			this.liftX = up.x;
			this.liftY = up.y;
			this.liftZ = up.z;
			this.distance = distance;
			this.phase = ThreadLocalRandom.current().nextFloat() * 6.2832f;
		}
	}

	/** World position of the letter being drawn (set by put) and of the last spawn point (set by spawn). */
	private static double atX;
	private static double atY;
	private static double atZ;

	private final List<Line> lines = new ArrayList<>();
	private final WorldText.Style extruded = new WorldText.Style().overlay(true);
	private final WorldText.Style shine = new WorldText.Style().shadow(0x50000000).overlay(true);
	private final WorldText.Style scatter = new WorldText.Style().overlay(true);
	private final WorldText.Style halo = new WorldText.Style().overlay(true);
	private final WorldText.Style spark = new WorldText.Style().overlay(true);
	private int spawned;

	// Smoothed camera velocity (blocks per second) from the camera positions of past frames.
	private float vx;
	private float vy;
	private float vz;
	private double lastX;
	private double lastY;
	private double lastZ;
	private long lastSample;

	// Sparks in world space: start, velocity per second, size, birth (0 when the slot is free).
	private final double[] sparkX = new double[SPARKS];
	private final double[] sparkY = new double[SPARKS];
	private final double[] sparkZ = new double[SPARKS];
	private final float[] sparkVX = new float[SPARKS];
	private final float[] sparkVY = new float[SPARKS];
	private final float[] sparkVZ = new float[SPARKS];
	private final float[] sparkSize = new float[SPARKS];
	private final long[] sparkBorn = new long[SPARKS];
	private int sparkNext;

	/** Follows the camera's motion; every frame, before show. */
	void track() {
		long now = System.nanoTime();
		double x = WorldEffectRenderer.camX;
		double y = WorldEffectRenderer.camY;
		double z = WorldEffectRenderer.camZ;
		float dt = (now - lastSample) / 1.0e9f;
		float ix = (float) (x - lastX);
		float iy = (float) (y - lastY);
		float iz = (float) (z - lastZ);
		if (lastSample == 0L || dt > 0.25f || ix * ix + iy * iy + iz * iz > 64f) {
			// First frame, a pause or a teleport: start from rest.
			vx = 0f;
			vy = 0f;
			vz = 0f;
		} else if (dt > 1e-4f) {
			float k = 1f - (float) Math.exp(-dt / VELOCITY_SMOOTHING);
			vx += (ix / dt - vx) * k;
			vy += (iy / dt - vy) * k;
			vz += (iz / dt - vz) * k;
		}
		lastX = x;
		lastY = y;
		lastZ = z;
		lastSample = now;
	}

	/**
	 * Dissolves the lines on screen and, unless text is empty, brings in a new one; size is the
	 * letter height and duration the line's length in seconds (words are timed by it).
	 */
	void show(String text, float size, float distance, Look look, float duration) {
		leaveAll();
		if (text.isEmpty()) return;
		boolean shiny = look == Look.SHINE;
		SdfFont font = look == Look.EXTRUDED ? Fonts.bold() : Fonts.title();
		// Every word weighs its letters plus one, so short words still get a moment.
		int total = 0;
		for (int i = 0; i < text.length(); ) {
			int cp = text.codePointAt(i);
			if (!Character.isWhitespace(cp)) total += i == 0 || Character.isWhitespace(text.codePointBefore(i)) ? 2 : 1;
			i += Character.charCount(cp);
		}
		float span = Math.min(duration * 0.9f, total * SECONDS_PER_LETTER);
		ThreadLocalRandom random = ThreadLocalRandom.current();
		if (look == Look.SCATTER) {
			showScatter(text, font, size, span, total, distance, random);
			return;
		}
		LyricLine line = LyricLine.of(text, font, (shiny ? SHINE_WIDTH : MAX_WIDTH) / size);
		float rowHeight = font.lineHeight(1f) * 0.92f;
		List<Letter> letters = new ArrayList<>(text.length());
		List<Word> words = new ArrayList<>(8);
		int count = line.rowCount();
		int before = 0;
		for (int r = 0; r < count; r++) {
			float rowStart = line.prefix[line.start[r]];
			float rowWidth = Math.max(line.width(r), 1e-4f);
			float half = rowWidth / 2f;
			// Rows are centred on the anchor vertically too.
			float y = (r - (count - 1) / 2f) * rowHeight;
			Word word = null;
			for (int i = line.start[r]; i < line.end[r]; ) {
				int cp = text.codePointAt(i);
				int next = i + Character.charCount(cp);
				if (Character.isWhitespace(cp)) {
					word = null;
				} else {
					if (shiny && word == null) {
						int end = next;
						int n = 1;
						while (end < line.end[r] && !Character.isWhitespace(text.codePointAt(end))) {
							end += Character.charCount(text.codePointAt(end));
							n++;
						}
						word = new Word(line.prefix[i] - rowStart - half, line.prefix[end] - rowStart - half, y,
							span * before / total, n >= SPARK_LETTERS);
						before += n + 1;
						words.add(word);
					}
					float left = line.prefix[i] - rowStart;
					float right = line.prefix[next] - rowStart;
					int index = letters.size();
					letters.add(new Letter(text.substring(i, next), (left + right) / 2f - half, y, word, left / rowWidth, right / rowWidth,
						index * LETTER_DELAY, index * LEAVE_DELAY, random));
				}
				i = next;
			}
		}
		Letter[] laid = letters.toArray(new Letter[0]);
		Word[] timed = words.toArray(new Word[0]);
		int slot = spawned++ % SIDES.length;
		if (shiny) {
			// Straight ahead in the current view, nudged a little to alternating sides, rising into place.
			spawn(distance, (float) Math.toRadians(SIDES[slot] * SHINE_SPREAD), 0f, LIFT);
			Line spawn = new Line(look, laid, timed, null, line.widest() / 2f, atX, atY, atZ, distance);
			spawn.rise.snap(-RISE);
			lines.add(spawn);
			return;
		}
		// A spot in the current view, turned to alternating sides and heights.
		spawn(distance, (float) Math.toRadians(SIDES[slot]), 0f, HEIGHTS[slot] * size * 2f);
		lines.add(new Line(look, laid, timed, null, line.widest() / 2f, atX, atY, atZ, distance));
	}

	/**
	 * A Scatter line: its words with their onsets and spots. The words are laid out as a sentence (two rows when it
	 * would not fit the cone of view), so they read left to right, then each is nudged up or down; each finds its
	 * place in the world when it is sung.
	 */
	private void showScatter(String text, SdfFont font, float size, float span, int total, float distance, ThreadLocalRandom random) {
		List<String> words = new ArrayList<>(8);
		float[] onsets = new float[text.length()];
		int before = 0;
		for (int i = 0; i < text.length(); ) {
			int cp = text.codePointAt(i);
			if (Character.isWhitespace(cp)) {
				i += Character.charCount(cp);
				continue;
			}
			int end = i;
			int letters = 0;
			while (end < text.length() && !Character.isWhitespace(text.codePointAt(end))) {
				end += Character.charCount(text.codePointAt(end));
				letters++;
			}
			onsets[words.size()] = span * before / total;
			words.add(text.substring(i, end));
			before += letters + 1;
			i = end;
		}
		int count = words.size();
		float[] widths = new float[count];
		float sentence = -SCATTER_GAP;
		for (int i = 0; i < count; i++) {
			widths[i] = font.width(words.get(i), 1f);
			sentence += widths[i] + SCATTER_GAP;
		}
		// Half the cone in letter heights at the set distance; a longer sentence breaks into two rows near its middle.
		float cone = distance * (float) Math.tan(Math.toRadians(SCATTER_YAW)) / size;
		int rows = sentence > cone * 2f || count > 7 ? 2 : 1;
		int split = count;
		if (rows == 2) {
			float run = 0f;
			for (split = 0; split < count - 1 && run + widths[split] / 2f < sentence / 2f; split++) run += widths[split] + SCATTER_GAP;
			split = Math.max(1, split);
		}
		Floater[] floaters = new Floater[count];
		for (int row = 0, first = 0; row < rows; row++) {
			int last = row == 0 ? split : count;
			float rowWidth = -SCATTER_GAP;
			for (int i = first; i < last; i++) rowWidth += widths[i] + SCATTER_GAP;
			// A row wider than the cone is pressed into it; the words then part in depth and height instead.
			float squeeze = Math.min(1f, cone * 2f / Math.max(rowWidth, 1e-3f));
			float band = rows == 2 ? (row == 0 ? 0.6f : -0.6f) : 0f;
			float x = -rowWidth / 2f;
			for (int i = first; i < last; i++) {
				float center = (x + widths[i] / 2f) * squeeze;
				x += widths[i] + SCATTER_GAP;
				float yaw = (float) Math.atan(center * size / distance) + (float) Math.toRadians((random.nextFloat() - 0.5f) * 4f);
				float wobble = ((i - first) % 2 == 0 ? 0.5f : -0.5f) * (rows == 2 ? 0.55f : 1f);
				float pitch = (float) Math.toRadians((band + wobble + (random.nextFloat() - 0.5f) * 0.45f) * SCATTER_PITCH);
				floaters[i] = new Floater(words.get(i), onsets[i], widths[i], yaw, pitch, i, random);
			}
			first = last;
		}
		lines.add(new Line(Look.SCATTER, new Letter[0], new Word[0], floaters, 0f,
			WorldEffectRenderer.camX, WorldEffectRenderer.camY, WorldEffectRenderer.camZ, distance));
	}

	void leaveAll() {
		long now = System.nanoTime();
		for (int i = 0; i < lines.size(); i++) {
			Line line = lines.get(i);
			if (line.left < 0L) line.left = now;
		}
	}

	void clear() {
		lines.clear();
		Arrays.fill(sparkBorn, 0L);
	}

	boolean isEmpty() {
		return lines.isEmpty();
	}

	/**
	 * Draws the lines. size is the letter height in blocks, clock the song time into the playing
	 * line in seconds, accent the colour of glows and gradients; depth extrudes the extruded look,
	 * glow adds the halos.
	 */
	void render(float size, float clock, int accent, boolean depth, boolean glow) {
		long now = System.nanoTime();
		extruded.font(Fonts.bold()).weight(0.05f).extrude(depth ? 0.24f : 0f, depth ? 10 : 0).extrudeColor(depth ? ColorUtil.darken(accent, 0.1f) : 0)
			.glow(glow ? accent : 0, glow ? 0.3f : 0f).color(ColorUtil.lerp(0xFFFFFFFF, accent, 0.12f));
		shine.font(Fonts.title());
		scatter.font(Fonts.title());
		int tint = ColorUtil.lerp(SILVER, accent, 0.55f);
		for (int l = lines.size() - 1; l >= 0; l--) {
			Line line = lines.get(l);
			boolean visible;
			if (line.look == Look.SCATTER) {
				visible = drawScatter(line, size, clock, accent, glow, now);
			} else {
				// Anchored where it appeared; it only settles in, floats a little and turns to the camera.
				float t = (now - line.born) / 1.0e9f;
				float lift = line.rise.target(0f).get() + (float) Math.sin(t * FLOAT_SPEED + line.phase) * FLOAT;
				line.wx = line.ox + line.liftX * lift;
				line.wy = line.oy + line.liftY * lift;
				line.wz = line.oz + line.liftZ * lift;
				line.plane.face(line.wx, line.wy, line.wz, 0f, 0f, 0f);
				line.k = size * grow(line.wx, line.wy, line.wz, line.distance);
				visible = line.look == Look.SHINE ? drawShiny(line, clock, accent, tint, glow, now) : drawExtruded(line, now);
			}
			if (!visible && line.left >= 0L) lines.remove(l);
		}
		drawSparks(accent, now);
	}

	/** Extruded letters popping in one after another; returns whether anything is still showing. */
	private boolean drawExtruded(Line line, long now) {
		float age = (now - line.born) / 1.0e9f;
		float leftAge = line.left < 0L ? -1f : (now - line.left) / 1.0e9f;
		float size = line.k;
		boolean visible = false;
		for (Letter letter : line.letters) {
			if (age >= letter.delay) letter.in.target(1f);
			if (leftAge >= letter.leaveDelay) letter.out.target(1f);
			float in = letter.in.get();
			float out = letter.out.get();
			float alpha = in * (1f - out);
			if (alpha <= 0.004f) {
				if (out < 0.99f) visible = true;
				continue;
			}
			visible = true;
			// Rises into place while it appears; drifts up and scatters while it dissolves.
			float px = letter.x * size;
			float py = -letter.y * size - (1f - in) * 0.45f * size + out * 0.9f * size;
			float scale = (0.35f + 0.65f * in) * (1f - 0.35f * out);
			put(line, letter, px, py, out * size * 1.8f);
			alpha *= nearFade(atX, atY, atZ);
			WorldText.draw(letter.text, atX, atY, atZ, extruded.size(size * scale).alpha(alpha).basis(line.plane.right, line.plane.up));
		}
		return visible;
	}

	/** Shiny words in time with the song; returns whether anything is still showing. */
	private boolean drawShiny(Line line, float clock, int accent, int tint, boolean glow, long now) {
		boolean playing = line.left < 0L;
		if (playing) line.clock = clock;
		float t = line.clock;
		float size = line.k;
		if (playing) {
			for (Word word : line.words) {
				if (t >= word.time) {
					if (word.sung) continue;
					word.sung = true;
					word.in.target(1f);
					word.flash.snap(1f);
					word.flash.target(0f);
					if (word.sparks) {
						float center = (word.left + word.right) / 2f * size;
						float py = -word.y * size;
						Vector3f r = line.plane.right;
						Vector3f u = line.plane.up;
						burst(line.wx + r.x * center + u.x * py, line.wy + r.y * center + u.y * py, line.wz + r.z * center + u.z * py,
							r, u, (word.right - word.left) / 2f * size, size, SPARKS_PER_WORD, now);
					}
				} else if (t >= word.time - LEAD) {
					word.in.target(PREVIEW);
				}
			}
		}
		// The slow shine crosses the widest row and a little beyond, over and over.
		float sweep = -line.half - SWEEP_BAND + (2f * line.half + 2f * SWEEP_BAND) * (t % SWEEP) / SWEEP;
		float leftAge = playing ? -1f : (now - line.left) / 1.0e9f;
		boolean visible = false;
		for (Letter letter : line.letters) {
			if (leftAge >= letter.leaveDelay * 0.7f) letter.out.target(1f);
			Word word = letter.word;
			float in = word.in.get();
			float out = letter.out.get();
			float alpha = in * (1f - out);
			if (alpha <= 0.004f) {
				if (out < 0.99f) visible = true;
				continue;
			}
			visible = true;
			float flash = word.flash.get();
			// The word grows around its centre and rises into place; dissolving letters drift up and scatter.
			float scale = (0.55f + 0.45f * in) * (1f - 0.3f * out);
			float center = (word.left + word.right) / 2f;
			float px = (center + (letter.x - center) * scale) * size;
			float py = -letter.y * size - (1f - in) * 0.35f * size + out * 1.4f * size;
			put(line, letter, px, py, out * size * 1.8f);
			alpha *= nearFade(atX, atY, atZ);
			// The word's own glint while it flashes, otherwise the slow shine of the playing line.
			float band;
			float width;
			float strength;
			if (flash > 0.02f) {
				band = word.left - GLINT + (word.right - word.left + 2f * GLINT) * (1f - flash);
				width = GLINT;
				strength = Math.min(1f, flash * 4f);
			} else {
				band = sweep;
				width = SWEEP_BAND;
				strength = playing ? 0.35f : 0f;
			}
			float k = size * scale;
			// Thin, faint and glowing while it appears, then crisp: a blur-to-sharp feel without a blur.
			shine.size(k).alpha(alpha).basis(line.plane.right, line.plane.up)
				.color(ColorUtil.lerp(SILVER, tint, letter.t0)).gradient(ColorUtil.lerp(SILVER, tint, letter.t1))
				.weight(0.02f - 0.08f * (1f - in))
				.glow(glow ? accent : 0, glow ? 0.28f + 0.5f * flash + 0.35f * (1f - in) : 0f)
				.shine((band - letter.x) * k, width * k, strength * (1f - out));
			WorldText.draw(letter.text, atX, atY, atZ, shine);
		}
		return visible;
	}

	/** Scatter words: each pops in at its own spot when it is sung and dissolves when the line is over. */
	private boolean drawScatter(Line line, float size, float clock, int accent, boolean glow, long now) {
		boolean playing = line.left < 0L;
		if (playing) line.clock = clock;
		float t = line.clock;
		float leftAge = playing ? -1f : (now - line.left) / 1.0e9f;
		// A white-hot core with a breath of the accent; the glow around it carries the colour.
		int core = ColorUtil.lerp(0xFFFFFFFF, accent, 0.2f);
		boolean visible = false;
		for (Floater word : line.floaters) {
			if (!word.placed) {
				if (!playing) continue;
				visible = true;
				if (t < word.time - POP_LEAD) continue;
				// Placed now, relative to where the camera is and is heading, so it lands in view.
				spawn(line.distance * word.depth, word.spotYaw, word.spotPitch, 0f);
				word.x = atX;
				word.y = atY;
				word.z = atZ;
				word.placed = true;
				word.placedAt = now;
				word.in.target(1f);
				word.flash.snap(1f);
				word.flash.target(0f);
				word.plane.face(word.x, word.y, word.z, word.yaw, word.pitch, word.roll);
				if (word.sparks) {
					float k = size * word.scale * grow(word.x, word.y, word.z, line.distance);
					burst(word.x, word.y, word.z, word.plane.right, word.plane.up, word.width * k * 0.5f, k, SCATTER_SPARKS, now);
				}
			}
			if (leftAge >= word.leaveDelay) word.out.target(1f);
			float in = word.in.get();
			float out = word.out.get();
			float flash = word.flash.get();
			float alpha = in * (1f - out);
			if (alpha <= 0.004f) {
				if (out < 0.99f) visible = true;
				continue;
			}
			visible = true;
			float age = (now - word.placedAt) / 1.0e9f;
			float k = size * word.scale * grow(word.x, word.y, word.z, line.distance) * (0.6f + 0.4f * in) * (1f + 0.15f * out);
			// Bobs gently in place; comes up into it and drifts on upwards as it dissolves.
			float lift = (float) Math.sin(age * FLOAT_SPEED + word.phase) * FLOAT - (1f - in) * 0.25f * k + out * 0.9f * k;
			double x = word.x;
			double y = word.y + lift;
			double z = word.z;
			alpha *= nearFade(x, y, z);
			if (alpha <= 0.004f) continue;
			word.plane.face(x, y, z, word.yaw, word.pitch, word.roll);
			Vector3f right = word.plane.right;
			Vector3f up = word.plane.up;
			float w = word.width * k;
			if (glow) {
				// A soft bloom of the accent behind the word, brightest as it pops in.
				halo.alpha(1f).basis(right, up);
				WorldText.halo(x, y, z, 0f, 0f, w + k * 0.5f, k * 1.05f, k * 0.5f, k * 0.85f,
					ColorUtil.fade(accent, (0.18f + 0.35f * flash) * alpha), true, halo);
			}
			float glint = w * 0.5f + GLINT * k;
			scatter.size(k).alpha(alpha).basis(right, up)
				.color(ColorUtil.lerp(core, accent, 0.35f * out)).gradient(0)
				// Thins and softens away as it dissolves, like smoke.
				.weight(0.03f - 0.1f * out)
				.glow(glow ? accent : 0, glow ? 0.6f + 0.4f * flash : 0f)
				.shine(-glint + 2f * glint * (1f - flash), GLINT * k, Math.min(1f, flash * 4f) * (1f - out));
			WorldText.draw(word.text, x, y, z, scatter);
		}
		return visible;
	}

	/** Places a letter at (px, py) in the line plane, pushed along its scatter direction by spread. */
	private static void put(Line line, Letter letter, float px, float py, float spread) {
		Vector3f r = line.plane.right;
		Vector3f u = line.plane.up;
		atX = line.wx + r.x * px + u.x * py + letter.sx * spread;
		atY = line.wy + r.y * px + u.y * py + letter.sy * spread;
		atZ = line.wz + r.z * px + u.z * py + letter.sz * spread;
	}

	/**
	 * The spawn point of a new line or word (into atX, atY, atZ): distance along the view direction turned by yaw
	 * and pitch (radians, in view space) and height up the screen, plus where the camera will be in about a second:
	 * ahead along the view (never much behind) and sideways only so far that it stays in view.
	 */
	private void spawn(float distance, float yaw, float pitch, float height) {
		Vector3f r = WorldEffectRenderer.RIGHT;
		Vector3f u = WorldEffectRenderer.UP;
		// UP x RIGHT is the view direction.
		float fx = u.y * r.z - u.z * r.y;
		float fy = u.z * r.x - u.x * r.z;
		float fz = u.x * r.y - u.y * r.x;
		float cy = (float) Math.cos(yaw);
		float sy = (float) Math.sin(yaw);
		float cp = (float) Math.cos(pitch);
		float sp = (float) Math.sin(pitch);
		float dx = (fx * cy + r.x * sy) * cp + u.x * sp;
		float dy = (fy * cy + r.y * sy) * cp + u.y * sp;
		float dz = (fz * cy + r.z * sy) * cp + u.z * sp;
		float along = vx * fx + vy * fy + vz * fz;
		float ahead = Math.max(-0.25f * distance, Math.min(MAX_AHEAD, along * MOTION_LEAD));
		float sx = (vx - fx * along) * MOTION_LEAD;
		float sz = (vz - fz * along) * MOTION_LEAD;
		float sv = (vy - fy * along) * MOTION_LEAD;
		float side = (float) Math.sqrt(sx * sx + sv * sv + sz * sz);
		float maxSide = (distance + Math.max(ahead, 0f)) * MAX_SIDEWAYS;
		if (side > maxSide) {
			float k = maxSide / side;
			sx *= k;
			sv *= k;
			sz *= k;
		}
		atX = WorldEffectRenderer.camX + dx * distance + fx * ahead + sx + u.x * height;
		atY = WorldEffectRenderer.camY + dy * distance + fy * ahead + sv + u.y * height;
		atZ = WorldEffectRenderer.camZ + dz * distance + fz * ahead + sz + u.z * height;
	}

	/** Throws count sparks from (x, y, z) along a word spread wide (blocks) in the plane (right, up); k is the letter size. */
	private void burst(double x, double y, double z, Vector3f right, Vector3f up, float spread, float k, int count, long now) {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		for (int i = 0; i < count; i++) {
			int s = sparkNext;
			sparkNext = (sparkNext + 1) % SPARKS;
			float along = (random.nextFloat() * 2f - 1f) * spread;
			float across = (random.nextFloat() - 0.5f) * 0.5f * k;
			sparkX[s] = x + right.x * along + up.x * across;
			sparkY[s] = y + right.y * along + up.y * across;
			sparkZ[s] = z + right.z * along + up.z * across;
			float vr = (random.nextFloat() * 2f - 1f) * 0.9f * k;
			float vu = (0.5f + random.nextFloat() * 1.1f) * k;
			sparkVX[s] = right.x * vr + up.x * vu;
			sparkVY[s] = right.y * vr + up.y * vu;
			sparkVZ[s] = right.z * vr + up.z * vu;
			sparkSize[s] = k;
			sparkBorn[s] = now;
		}
	}

	/** Sparks fly out of their word, fall a little and fade from white to the accent. */
	private void drawSparks(int accent, long now) {
		for (int i = 0; i < SPARKS; i++) {
			if (sparkBorn[i] == 0L) continue;
			float age = (now - sparkBorn[i]) / 1.0e9f;
			if (age >= SPARK_LIFE) {
				sparkBorn[i] = 0L;
				continue;
			}
			float life = age / SPARK_LIFE;
			float k = sparkSize[i];
			double x = sparkX[i] + sparkVX[i] * age;
			double y = sparkY[i] + sparkVY[i] * age - 0.9f * k * age * age;
			double z = sparkZ[i] + sparkVZ[i] * age;
			float s = k * 0.07f * (1f - 0.5f * life);
			int color = ColorUtil.fade(ColorUtil.lerp(0xFFFFFFFF, accent, life), (1f - life) * nearFade(x, y, z));
			WorldText.halo(x, y, z, 0f, 0f, s, s, s * 0.5f, s * 1.6f, color, true, spark);
		}
	}

	/** How much bigger than its size text at (x, y, z) is drawn: it grows (slower than it recedes) past the set distance. */
	private static float grow(double x, double y, double z, float distance) {
		double dx = x - WorldEffectRenderer.camX;
		double dy = y - WorldEffectRenderer.camY;
		double dz = z - WorldEffectRenderer.camZ;
		float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
		return d <= distance ? 1f : (float) Math.pow(d / distance, GROW);
	}

	/** Fades text out as the camera comes close, so the near plane never cuts it. */
	private static float nearFade(double x, double y, double z) {
		double dx = x - WorldEffectRenderer.camX;
		double dy = y - WorldEffectRenderer.camY;
		double dz = z - WorldEffectRenderer.camZ;
		float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
		return Anim.clamp01((d - NEAR) / NEAR_FADE);
	}

	private static float smoothstep(float edge0, float edge1, float x) {
		float t = Anim.clamp01((x - edge0) / (edge1 - edge0));
		return t * t * (3f - 2f * t);
	}
}
