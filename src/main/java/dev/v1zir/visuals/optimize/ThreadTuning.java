package dev.v1zir.visuals.optimize;

import dev.v1zir.visuals.V1zir;

/**
 * Thread priorities: the render thread first. The game's background workers (chunk building, loading, saving,
 * downloads) and the client's own background threads share the processor with the render thread at the same priority,
 * so on a busy processor a frame can wait behind them; here the render thread is raised and those threads lowered a
 * step, so frames come first and the background work fills the time left over. Threads the pools start later are
 * caught every few seconds. The world's own simulation (the integrated server) and sound are left as they are. Java
 * thread priorities count on Windows; elsewhere they are a hint the system may ignore.
 */
public final class ThreadTuning {
	private static final String[] BACKGROUND = {"Worker-Main", "IO-Worker", "Download-", "Bootstrap", "V1zir "};
	private static boolean raised;

	private ThreadTuning() {
	}

	/** Render thread: raises it once, then lowers the background threads (again every few seconds for new ones). */
	public static void apply() {
		if (!Optimizations.threads()) return;
		if (!raised) {
			raised = true;
			Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
			V1zir.LOGGER.info("Thread priorities: the render thread first, background workers a step lower");
		}
		ThreadGroup root = Thread.currentThread().getThreadGroup();
		while (root.getParent() != null) root = root.getParent();
		Thread[] threads = new Thread[root.activeCount() * 2 + 16];
		int count = root.enumerate(threads, true);
		for (int i = 0; i < count; i++) {
			Thread thread = threads[i];
			if (thread == null || thread.getPriority() < Thread.NORM_PRIORITY) continue;
			String name = thread.getName();
			for (String prefix : BACKGROUND) {
				if (name.startsWith(prefix)) {
					thread.setPriority(Thread.NORM_PRIORITY - 1);
					break;
				}
			}
		}
	}
}
