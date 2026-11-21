package dev.v1zir.visuals.optimize;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Which graphics card Windows starts this game on: the graphics preference Windows keeps for each program (Settings >
 * System > Display > Graphics), here for the Java the game runs on. It lives in the user's registry
 * (HKCU\\Software\\Microsoft\\DirectX\\UserGpuPreferences, the program's path and "GpuPreference=n;"), so setting it
 * needs no administrator; it takes effect the next time Java starts. Windows only. Plain Java on purpose (nothing of the
 * game or the mod), so CI runs it as it is against a real Windows registry (.github/gpu/GpuPreferenceCheck.java).
 */
public final class GpuPreference {
	/** Windows decides (usually the power saving card on battery, as it likes). */
	public static final int AUTO = 0;
	/** The card built into the processor. */
	public static final int POWER_SAVING = 1;
	/** The card of its own. */
	public static final int HIGH_PERFORMANCE = 2;
	public static final String KEY = "HKCU\\Software\\Microsoft\\DirectX\\UserGpuPreferences";
	private static final Pattern VALUE = Pattern.compile("GpuPreference=(\\d)");
	private static final Pattern SETTING = Pattern.compile("GpuPreference=\\d;?");

	private GpuPreference() {
	}

	/** Whether this system keeps such a preference (Windows) and the game's Java is known. */
	public static boolean supported() {
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win") && executable() != null;
	}

	/** The Java program this game runs on (java.exe or javaw.exe, its whole path), or null when unknown. */
	public static String executable() {
		return ProcessHandle.current().info().command().orElse(null);
	}

	/** The preference Windows has for this game's Java: AUTO when it has none. Blocks briefly (reg.exe). */
	public static int read() {
		String program = executable();
		return program == null ? AUTO : read(program);
	}

	/** The preference Windows has for a program (its whole path): AUTO when it has none. */
	public static int read(String program) {
		String data = data(program);
		Matcher matcher = VALUE.matcher(data == null ? "" : data);
		return matcher.find() ? Integer.parseInt(matcher.group(1)) : AUTO;
	}

	/** Sets the preference for this game's Java (AUTO removes it); true when Windows took it. Blocks briefly (reg.exe). */
	public static boolean write(int preference) {
		String program = executable();
		return program != null && write(program, preference);
	}

	/**
	 * Sets the preference for a program (its whole path), keeping Windows' other settings for it (AUTO removes only the
	 * preference, and the value when nothing else is in it); true when Windows took it.
	 */
	public static boolean write(String program, int preference) {
		String data = data(program);
		String rest = data == null ? "" : SETTING.matcher(data).replaceAll("");
		String value = preference == AUTO ? rest : rest + "GpuPreference=" + preference + ";";
		if (value.isEmpty()) {
			// A value that was never there is no failure.
			return data == null || run(List.of("reg", "delete", KEY, "/v", program, "/f")) == 0;
		}
		return run(List.of("reg", "add", KEY, "/v", program, "/t", "REG_SZ", "/d", value, "/f")) == 0;
	}

	/** What Windows keeps for a program ("GpuPreference=2;" and the like), or null when nothing. */
	public static String data(String program) {
		String out = output(List.of("reg", "query", KEY, "/v", program));
		if (out == null) return null;
		for (String line : out.split("\\R")) {
			int type = line.indexOf("REG_SZ");
			if (type >= 0) return line.substring(type + "REG_SZ".length()).strip();
		}
		return null;
	}

	/** Runs a command, its exit code (-1 when it could not run or took too long). */
	private static int run(List<String> command) {
		try {
			Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
			process.getInputStream().readAllBytes();
			return process.waitFor(5, TimeUnit.SECONDS) ? process.exitValue() : -1;
		} catch (IOException | RuntimeException e) {
			return -1;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return -1;
		}
	}

	/** What a command printed, or null when it failed. */
	private static String output(List<String> command) {
		try {
			Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
			String out = new String(process.getInputStream().readAllBytes(), Charset.defaultCharset());
			return process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0 ? out : null;
		} catch (IOException | RuntimeException e) {
			return null;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return null;
		}
	}
}
