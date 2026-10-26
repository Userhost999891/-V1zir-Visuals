package dev.v1zir.visuals.module.impl.hud;

import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.font.SdfFont;
import dev.v1zir.visuals.render.world.WorldEffectRenderer;
import dev.v1zir.visuals.render.world.WorldText;
import dev.v1zir.visuals.util.Anim;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The 3D preset of Spotify Lyrics. Every line appears at a new spot in the field of view
 * (alternating sides and heights), as extruded letters that pop in one after another. It stays
 * where it appeared but trails the player on a lagging spring, turns to face the camera smoothly,
 * and when it ends the letters fade, drift up and scatter. Lines are laid out once when they
 * spawn; frames only move letters.
 */
final class Lyrics3D {
	private static final float LETTER_DELAY = 0.032f;
	private static final float LEAVE_DELAY = 0.018f;
	/** Widest row in blocks before a line wraps. */
	private static final float MAX_WIDTH = 2.8f;
	private static final float MAX_PITCH = 0.6f;
	private static final float[] SIDES = {-22f, 18f, -8f, 26f, -29f, 10f};
	private static final float[] HEIGHTS = {0.35f, -0.2f, 0.6f, 0.05f, -0.3f, 0.45f};

	private static final class Letter {
		final String text;
		/** Position in the line plane (blocks at size 1): x from the centre, y down from the first row. */
		final float x;
		final float y;
		final float delay;
		final float leaveDelay;
		/** Direction the letter scatters in when the line dissolves. */
		final float sx;
		final float sy;
		final float sz;
		final Anim in = new Anim(0f, 8f);
		final Anim out = new Anim(0f, 3f);

		Letter(String text, float x, float y, float delay, float leaveDelay, ThreadLocalRandom random) {
			this.text = text;
			this.x = x;
			this.y = y;
			this.delay = delay;
			this.leaveDelay = leaveDelay;
			this.sx = random.nextFloat() * 2f - 1f;
			this.sy = random.nextFloat() * 0.8f;
			this.sz = random.nextFloat() * 2f - 1f;
		}
	}

	private static final class Line {
		final Letter[] letters;
		/** Camera position when it appeared; the springs below are relative to it, which keeps floats precise. */
		final double ox;
		final double oy;
		final double oz;
		/** Where it sits relative to the camera, fixed in world directions (it does not turn with the view). */
		final float offX;
		final float offY;
		final float offZ;
		final Anim px = new Anim(0f, 1.8f);
		final Anim py = new Anim(0f, 1.8f);
		final Anim pz = new Anim(0f, 1.8f);
		final Anim yaw = new Anim(0f, 4f);
		final Anim pitch = new Anim(0f, 4f);
		final Vector3f right = new Vector3f();
		final Vector3f up = new Vector3f();
		final long born = System.nanoTime();
		long left = -1L;
		/** Turned to the camera at least once (the first frame snaps instead of turning). */
		boolean faced;

		Line(Letter[] letters, double ox, double oy, double oz, float offX, float offY, float offZ) {
			this.letters = letters;
			this.ox = ox;
			this.oy = oy;
			this.oz = oz;
			this.offX = offX;
			this.offY = offY;
			this.offZ = offZ;
			px.snap(offX);
			py.snap(offY);
			pz.snap(offZ);
		}
	}

	private final List<Line> lines = new ArrayList<>();
	private final WorldText.Style style = new WorldText.Style();
	private int spawned;

	/** Dissolves the lines on screen and, unless text is empty, brings in a new one. */
	void show(String text, float size, float distance) {
		leaveAll();
		if (text.isEmpty()) return;
		SdfFont font = Fonts.bold();
		LyricLine line = LyricLine.of(text, font, MAX_WIDTH / size);
		float rowHeight = font.lineHeight(1f) * 0.92f;
		List<Letter> letters = new ArrayList<>(text.length());
		ThreadLocalRandom random = ThreadLocalRandom.current();
		int count = line.rowCount();
		for (int r = 0; r < count; r++) {
			float half = line.width(r) / 2f;
			for (int i = line.start[r]; i < line.end[r]; ) {
				int cp = text.codePointAt(i);
				int next = i + Character.charCount(cp);
				if (!Character.isWhitespace(cp)) {
					float center = (line.prefix[i] + line.prefix[next]) / 2f - line.prefix[line.start[r]] - half;
					// Rows are centred on the anchor vertically too.
					float y = (r - (count - 1) / 2f) * rowHeight;
					int index = letters.size();
					letters.add(new Letter(text.substring(i, next), center, y, index * LETTER_DELAY, index * LEAVE_DELAY, random));
				}
				i = next;
			}
		}
		// A spot in the current view, turned to alternating sides and heights.
		Vector3f r = WorldEffectRenderer.RIGHT;
		Vector3f u = WorldEffectRenderer.UP;
		float fx = u.y * r.z - u.z * r.y;
		float fy = u.z * r.x - u.x * r.z;
		float fz = u.x * r.y - u.y * r.x;
		float horizontal = (float) Math.sqrt(fx * fx + fz * fz);
		if (horizontal < 1e-3f) {
			fx = -u.x;
			fz = -u.z;
			horizontal = Math.max((float) Math.sqrt(fx * fx + fz * fz), 1e-3f);
		}
		fx /= horizontal;
		fz /= horizontal;
		int slot = spawned++ % SIDES.length;
		float angle = (float) Math.toRadians(SIDES[slot]);
		float cos = (float) Math.cos(angle);
		float sin = (float) Math.sin(angle);
		float dx = (fx * cos - fz * sin) * distance;
		float dz = (fx * sin + fz * cos) * distance;
		float dy = Math.max(-0.35f, Math.min(0.35f, fy)) * distance + HEIGHTS[slot] * size * 2f;
		lines.add(new Line(letters.toArray(new Letter[0]), WorldEffectRenderer.camX, WorldEffectRenderer.camY, WorldEffectRenderer.camZ, dx, dy, dz));
	}

	void leaveAll() {
		long now = System.nanoTime();
		for (Line line : lines) if (line.left < 0L) line.left = now;
	}

	void clear() {
		lines.clear();
	}

	boolean isEmpty() {
		return lines.isEmpty();
	}

	/**
	 * Draws the lines. size is the letter height in blocks; face lights the front, side colours the
	 * extruded depth (0 for flat letters), glow the additive halo (0 for none).
	 */
	void render(float size, int face, int side, int glow) {
		long now = System.nanoTime();
		style.font(Fonts.bold()).weight(0.05f).extrude(side != 0 ? 0.24f : 0f, side != 0 ? 10 : 0).extrudeColor(side)
			.glow(glow, glow != 0 ? 0.3f : 0f).color(face);
		double camX = WorldEffectRenderer.camX;
		double camY = WorldEffectRenderer.camY;
		double camZ = WorldEffectRenderer.camZ;
		for (int l = lines.size() - 1; l >= 0; l--) {
			Line line = lines.get(l);
			// Follow the camera loosely: the target moves with it, the spring lags behind.
			float lx = line.px.target((float) (camX - line.ox) + line.offX).get();
			float ly = line.py.target((float) (camY - line.oy) + line.offY).get();
			float lz = line.pz.target((float) (camZ - line.oz) + line.offZ).get();
			double wx = line.ox + lx;
			double wy = line.oy + ly;
			double wz = line.oz + lz;
			face(line, (float) (camX - wx), (float) (camY - wy), (float) (camZ - wz), !line.faced);
			line.faced = true;

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
				float spread = out * size * 1.8f;
				Vector3f r = line.right;
				Vector3f u = line.up;
				double x = wx + r.x * px + u.x * py + letter.sx * spread;
				double y = wy + r.y * px + u.y * py + letter.sy * spread;
				double z = wz + r.z * px + u.z * py + letter.sz * spread;
				float scale = (0.35f + 0.65f * in) * (1f - 0.35f * out);
				WorldText.draw(letter.text, x, y, z, style.size(size * scale).alpha(alpha).basis(r, u));
			}
			if (!visible && line.left >= 0L) lines.remove(l);
		}
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
