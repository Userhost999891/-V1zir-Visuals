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
 * The 3D preset of Spotify Lyrics, in two looks:
 * <ul>
 *   <li>Shine: the line appears a few blocks ahead in the current view, a little above the eyes, and
 *   stays at that spot in the world: walk on or turn away and it is left behind. It only turns to
 *   face the camera and floats gently. Words come in one by one in time with the song (onsets estimated
 *   from the line's duration, weighted by word length): the next word shows up small, thin and
 *   faint just before it is sung, then springs to full size while a glint sweeps across it and its
 *   glow flares; long words throw a few sparks. A slow shine keeps passing over the playing line.</li>
 *   <li>Extruded: every line appears at a new spot in the field of view (alternating sides and
 *   heights) as extruded letters that pop in one after another, and stays at that spot in the world.</li>
 * </ul>
 * Either way finished lines fade, drift up and scatter letter by letter. Lines are laid out once
 * when they spawn; frames only move letters.
 */
final class Lyrics3D {
	private static final float LETTER_DELAY = 0.032f;
	private static final float LEAVE_DELAY = 0.018f;
	/** Widest row in blocks before a line wraps: extruded lines sit to the side, shiny ones in the middle. */
	private static final float MAX_WIDTH = 2.8f;
	private static final float SHINE_WIDTH = 4.4f;
	private static final float MAX_PITCH = 0.6f;
	private static final float[] SIDES = {-22f, 18f, -8f, 26f, -29f, 10f};
	private static final float[] HEIGHTS = {0.35f, -0.2f, 0.6f, 0.05f, -0.3f, 0.45f};
	/** Shiny lines use a fraction of SIDES, so they stay nearly straight ahead. */
	private static final float SHINE_SPREAD = 0.3f;
	/** A new shiny line rises this far (blocks) into its spot. */
	private static final float RISE = 0.18f;
	/** Gentle float of a line in its spot: amplitude in blocks and speed in radians per second. */
	private static final float FLOAT = 0.035f;
	private static final float FLOAT_SPEED = 1.3f;
	/** Shiny lines float this far above eye height, in blocks. */
	private static final float LIFT = 0.12f;
	/** Longest time per letter when word onsets are spread over a line, so a line before a long pause is not sung slowly. */
	private static final float SECONDS_PER_LETTER = 0.11f;
	/** How long before its onset the next word shows up, and how far in it is until then. */
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

	private static final class Line {
		final Letter[] letters;
		final Word[] words;
		final boolean shiny;
		/** Half the widest row, in letter heights. */
		final float half;
		/** Camera position when it appeared; the offsets below are relative to it, which keeps floats precise. */
		final double ox;
		final double oy;
		final double oz;
		/** Where it sits in the world relative to (ox, oy, oz); fixed once it appears. */
		final float offX;
		final float offY;
		final float offZ;
		/** Phase of the gentle float, so neighbouring lines do not bob in step. */
		final float phase;
		final Anim px = new Anim(0f, 1.8f);
		final Anim py = new Anim(0f, 1.8f);
		final Anim pz = new Anim(0f, 1.8f);
		final Anim yaw = new Anim(0f, 4f);
		final Anim pitch = new Anim(0f, 4f);
		final Vector3f right = new Vector3f();
		final Vector3f up = new Vector3f();
		final long born = System.nanoTime();
		long left = -1L;
		/** Song time into the line (seconds); frozen once it leaves. */
		float clock;
		/** Anchor of the last frame, for the sparks. */
		double wx;
		double wy;
		double wz;
		/** Turned to the camera at least once (the first frame snaps instead of turning). */
		boolean faced;

		Line(Letter[] letters, Word[] words, boolean shiny, float half, float offX, float offY, float offZ) {
			this.letters = letters;
			this.words = words;
			this.shiny = shiny;
			this.half = half;
			this.ox = WorldEffectRenderer.camX;
			this.oy = WorldEffectRenderer.camY;
			this.oz = WorldEffectRenderer.camZ;
			this.offX = offX;
			this.offY = offY;
			this.offZ = offZ;
			this.phase = ThreadLocalRandom.current().nextFloat() * 6.2832f;
			px.snap(offX);
			py.snap(offY);
			pz.snap(offZ);
		}
	}

	/** View direction of the frame: horizontal part normalised (fx, fz), vertical part as is (fy). */
	private static float fx;
	private static float fy;
	private static float fz;
	/** World position of the letter being drawn (set by put). */
	private static double atX;
	private static double atY;
	private static double atZ;

	private final List<Line> lines = new ArrayList<>();
	private final WorldText.Style extruded = new WorldText.Style();
	private final WorldText.Style shine = new WorldText.Style().shadow(0x50000000);
	private int spawned;

	// Sparks: their line, start in its plane (letter heights, y up), velocity per second and birth.
	private final Line[] sparkLine = new Line[SPARKS];
	private final float[] sparkX = new float[SPARKS];
	private final float[] sparkY = new float[SPARKS];
	private final float[] sparkVX = new float[SPARKS];
	private final float[] sparkVY = new float[SPARKS];
	private final long[] sparkBorn = new long[SPARKS];
	private int sparkNext;

	/**
	 * Dissolves the lines on screen and, unless text is empty, brings in a new one; size is the
	 * letter height and duration the line's length in seconds (shiny lines time their words by it).
	 */
	void show(String text, float size, float distance, boolean shiny, float duration) {
		leaveAll();
		if (text.isEmpty()) return;
		SdfFont font = shiny ? Fonts.title() : Fonts.bold();
		LyricLine line = LyricLine.of(text, font, (shiny ? SHINE_WIDTH : MAX_WIDTH) / size);
		float rowHeight = font.lineHeight(1f) * 0.92f;
		// Every word weighs its letters plus one, so short words still get a moment.
		int total = 0;
		for (int i = 0; i < text.length(); ) {
			int cp = text.codePointAt(i);
			if (!Character.isWhitespace(cp)) total += i == 0 || Character.isWhitespace(text.codePointBefore(i)) ? 2 : 1;
			i += Character.charCount(cp);
		}
		float span = Math.min(duration * 0.9f, total * SECONDS_PER_LETTER);
		List<Letter> letters = new ArrayList<>(text.length());
		List<Word> words = new ArrayList<>(8);
		ThreadLocalRandom random = ThreadLocalRandom.current();
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
		forward();
		Letter[] laid = letters.toArray(new Letter[0]);
		Word[] timed = words.toArray(new Word[0]);
		float half = line.widest() / 2f;
		int slot = spawned++ % SIDES.length;
		if (shiny) {
			// Straight ahead in the current view, nudged a little to alternating sides, rising into place.
			double heading = Math.atan2(fx, fz) + Math.toRadians(SIDES[slot] * SHINE_SPREAD);
			Line spawn = new Line(laid, timed, true, half, (float) Math.sin(heading) * distance, lift(distance),
				(float) Math.cos(heading) * distance);
			spawn.py.snap(spawn.offY - RISE);
			lines.add(spawn);
			return;
		}
		// A spot in the current view, turned to alternating sides and heights.
		float angle = (float) Math.toRadians(SIDES[slot]);
		float cos = (float) Math.cos(angle);
		float sin = (float) Math.sin(angle);
		float dx = (fx * cos - fz * sin) * distance;
		float dz = (fx * sin + fz * cos) * distance;
		float dy = Math.max(-0.35f, Math.min(0.35f, fy)) * distance + HEIGHTS[slot] * size * 2f;
		lines.add(new Line(laid, timed, false, half, dx, dy, dz));
	}

	void leaveAll() {
		long now = System.nanoTime();
		for (Line line : lines) if (line.left < 0L) line.left = now;
	}

	void clear() {
		lines.clear();
		Arrays.fill(sparkLine, null);
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
		int tint = ColorUtil.lerp(SILVER, accent, 0.55f);
		forward();
		double camX = WorldEffectRenderer.camX;
		double camY = WorldEffectRenderer.camY;
		double camZ = WorldEffectRenderer.camZ;
		for (int l = lines.size() - 1; l >= 0; l--) {
			Line line = lines.get(l);
			// Anchored in the world where it appeared; it only settles in and floats a little.
			float t = (now - line.born) / 1.0e9f;
			float lx = line.px.target(line.offX).get();
			float ly = line.py.target(line.offY).get() + (float) Math.sin(t * FLOAT_SPEED + line.phase) * FLOAT;
			float lz = line.pz.target(line.offZ).get();
			line.wx = line.ox + lx;
			line.wy = line.oy + ly;
			line.wz = line.oz + lz;
			face(line, (float) (camX - line.wx), (float) (camY - line.wy), (float) (camZ - line.wz), !line.faced);
			line.faced = true;
			boolean visible = line.shiny ? drawShiny(line, size, clock, accent, tint, glow, now) : drawExtruded(line, size, now);
			if (!visible && line.left >= 0L) lines.remove(l);
		}
		drawSparks(size, accent, now);
	}

	/** Extruded letters popping in one after another; returns whether anything is still showing. */
	private boolean drawExtruded(Line line, float size, long now) {
		float age = (now - line.born) / 1.0e9f;
		float leftAge = line.left < 0L ? -1f : (now - line.left) / 1.0e9f;
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
			WorldText.draw(letter.text, atX, atY, atZ, extruded.size(size * scale).alpha(alpha).basis(line.right, line.up));
		}
		return visible;
	}

	/** Shiny words in time with the song; returns whether anything is still showing. */
	private boolean drawShiny(Line line, float size, float clock, int accent, int tint, boolean glow, long now) {
		boolean playing = line.left < 0L;
		if (playing) line.clock = clock;
		float t = line.clock;
		if (playing) {
			for (Word word : line.words) {
				if (t >= word.time) {
					if (word.sung) continue;
					word.sung = true;
					word.in.target(1f);
					word.flash.snap(1f);
					word.flash.target(0f);
					if (word.sparks) burst(line, word, now);
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
			shine.size(k).alpha(alpha).basis(line.right, line.up)
				.color(ColorUtil.lerp(SILVER, tint, letter.t0)).gradient(ColorUtil.lerp(SILVER, tint, letter.t1))
				.weight(0.02f - 0.08f * (1f - in))
				.glow(glow ? accent : 0, glow ? 0.28f + 0.5f * flash + 0.35f * (1f - in) : 0f)
				.shine((band - letter.x) * k, width * k, strength * (1f - out));
			WorldText.draw(letter.text, atX, atY, atZ, shine);
		}
		return visible;
	}

	/** Places a letter at (px, py) in the line plane, pushed along its scatter direction by spread. */
	private static void put(Line line, Letter letter, float px, float py, float spread) {
		Vector3f r = line.right;
		Vector3f u = line.up;
		atX = line.wx + r.x * px + u.x * py + letter.sx * spread;
		atY = line.wy + r.y * px + u.y * py + letter.sy * spread;
		atZ = line.wz + r.z * px + u.z * py + letter.sz * spread;
	}

	/** Throws a few sparks off a word that is being sung. */
	private void burst(Line line, Word word, long now) {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		for (int i = 0; i < SPARKS_PER_WORD; i++) {
			int s = sparkNext;
			sparkNext = (sparkNext + 1) % SPARKS;
			sparkLine[s] = line;
			sparkX[s] = word.left + (word.right - word.left) * random.nextFloat();
			sparkY[s] = -word.y + (random.nextFloat() - 0.5f) * 0.5f;
			sparkVX[s] = (random.nextFloat() * 2f - 1f) * 0.9f;
			sparkVY[s] = 0.5f + random.nextFloat() * 1.1f;
			sparkBorn[s] = now;
		}
	}

	/** Sparks fly out of their word, fall a little and fade from white to the accent. */
	private void drawSparks(float size, int accent, long now) {
		WorldEffectRenderer.Mesh mesh = WorldEffectRenderer.glow();
		for (int i = 0; i < SPARKS; i++) {
			Line line = sparkLine[i];
			if (line == null) continue;
			float age = (now - sparkBorn[i]) / 1.0e9f;
			if (age >= SPARK_LIFE) {
				sparkLine[i] = null;
				continue;
			}
			float life = age / SPARK_LIFE;
			float px = (sparkX[i] + sparkVX[i] * age) * size;
			float py = (sparkY[i] + sparkVY[i] * age - 0.9f * age * age) * size;
			Vector3f r = line.right;
			Vector3f u = line.up;
			mesh.orb(line.wx + r.x * px + u.x * py, line.wy + r.y * px + u.y * py, line.wz + r.z * px + u.z * py,
				size * 0.12f * (1f - 0.5f * life), ColorUtil.fade(ColorUtil.lerp(0xFFFFFFFF, accent, life), 1f - life));
		}
	}

	/** Height of a shiny line relative to the eyes: a little above, following the pitch only partly. */
	private static float lift(float distance) {
		return Math.max(-0.35f, Math.min(0.35f, fy)) * distance + LIFT;
	}

	/** Reads the view direction of this frame from the camera vectors. */
	private static void forward() {
		Vector3f r = WorldEffectRenderer.RIGHT;
		Vector3f u = WorldEffectRenderer.UP;
		fx = u.y * r.z - u.z * r.y;
		fy = u.z * r.x - u.x * r.z;
		fz = u.x * r.y - u.y * r.x;
		float horizontal = (float) Math.sqrt(fx * fx + fz * fz);
		if (horizontal < 1e-3f) {
			fx = -u.x;
			fz = -u.z;
			horizontal = Math.max((float) Math.sqrt(fx * fx + fz * fz), 1e-3f);
		}
		fx /= horizontal;
		fz /= horizontal;
	}

	/** Turns the line plane towards the camera direction (dx, dy, dz) on springs; snap for a new line. */
	private static void face(Line line, float dx, float dy, float dz, boolean snap) {
		float yawTarget = (float) Math.atan2(dx, dz);
		float pitchTarget = Math.max(-MAX_PITCH, Math.min(MAX_PITCH, (float) Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
		float yaw;
		float pitch;
		if (snap) {
			line.yaw.snap(yawTarget);
			line.pitch.snap(pitchTarget);
			yaw = yawTarget;
			pitch = pitchTarget;
		} else {
			float current = line.yaw.target();
			// Take the short way round.
			float delta = (float) Math.IEEEremainder(yawTarget - current, Math.PI * 2.0);
			yaw = line.yaw.target(current + delta).get();
			pitch = line.pitch.target(pitchTarget).get();
		}
		// Normal towards the camera; right stays horizontal and up = normal x right.
		float cp = (float) Math.cos(pitch);
		float nx = (float) Math.sin(yaw) * cp;
		float ny = (float) Math.sin(pitch);
		float nz = (float) Math.cos(yaw) * cp;
		float rx = (float) Math.cos(yaw);
		float rz = (float) -Math.sin(yaw);
		line.right.set(rx, 0f, rz);
		line.up.set(ny * rz, nz * rx - nx * rz, -ny * rx);
	}
}
