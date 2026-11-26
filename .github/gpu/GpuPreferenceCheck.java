import dev.v1zir.visuals.optimize.GpuPreference;

import java.util.ArrayList;
import java.util.List;

/**
 * CI on Windows: the GPU option's graphics preference against a real registry, through the same reg.exe calls as in
 * the game. For the Java running this and for a path with spaces, each choice is written and read back; a setting of
 * Windows' own in the same value stays. It leaves nothing behind; any mismatch fails it (exit code 1).
 */
public class GpuPreferenceCheck {
	private static int failures;

	public static void main(String[] args) throws Exception {
		String own = GpuPreference.executable();
		System.out.println("supported: " + GpuPreference.supported() + ", this Java: " + own);
		expect(GpuPreference.supported(), "supported on Windows");
		String before = GpuPreference.data(own);
		cycle(own);
		if (before != null) reg("add", GpuPreference.KEY, "/v", own, "/t", "REG_SZ", "/d", before, "/f");

		String spaced = "C:\\Program Files\\V1zir Check\\bin\\java.exe";
		cycle(spaced);
		// A setting of Windows' own in the same value stays, with the preference and without it.
		reg("add", GpuPreference.KEY, "/v", spaced, "/t", "REG_SZ", "/d", "SwapEffectUpgradeEnable=1;", "/f");
		expect(GpuPreference.write(spaced, GpuPreference.HIGH_PERFORMANCE), "write beside another setting");
		String both = GpuPreference.data(spaced);
		System.out.println("beside another setting: " + both);
		expect("SwapEffectUpgradeEnable=1;GpuPreference=2;".equals(both), "both settings kept");
		expect(GpuPreference.read(spaced) == GpuPreference.HIGH_PERFORMANCE, "read beside another setting");
		expect(GpuPreference.write(spaced, GpuPreference.AUTO), "Auto beside another setting");
		String rest = GpuPreference.data(spaced);
		System.out.println("Auto beside another setting: " + rest);
		expect("SwapEffectUpgradeEnable=1;".equals(rest), "only the other setting left");
		reg("delete", GpuPreference.KEY, "/v", spaced, "/f");

		System.out.println(failures == 0 ? "V1ZIR-GPU-PREF OK" : "V1ZIR-GPU-PREF FAILED: " + failures + " mismatches");
		System.exit(failures == 0 ? 0 : 1);
	}

	/** High performance, power saving, Auto, high performance again and Auto again: each written and read back. */
	private static void cycle(String program) {
		int[] order = {GpuPreference.HIGH_PERFORMANCE, GpuPreference.POWER_SAVING, GpuPreference.AUTO, GpuPreference.HIGH_PERFORMANCE, GpuPreference.AUTO};
		for (int preference : order) {
			boolean ok = GpuPreference.write(program, preference);
			int back = GpuPreference.read(program);
			String data = GpuPreference.data(program);
			System.out.println(program + ": wrote " + preference + " (" + (ok ? "taken" : "refused") + "), read " + back + ", value " + data);
			expect(ok && back == preference, "round trip of " + preference + " for " + program);
			expect(preference == GpuPreference.AUTO ? data == null : ("GpuPreference=" + preference + ";").equals(data), "value for " + preference);
		}
	}

	private static void reg(String... args) throws Exception {
		List<String> command = new ArrayList<>(List.of("reg"));
		command.addAll(List.of(args));
		new ProcessBuilder(command).inheritIO().start().waitFor();
	}

	private static void expect(boolean ok, String what) {
		if (ok) return;
		failures++;
		System.out.println("MISMATCH: " + what);
	}
}
