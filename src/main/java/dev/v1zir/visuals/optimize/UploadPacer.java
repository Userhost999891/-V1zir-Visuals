package dev.v1zir.visuals.optimize;

import net.minecraft.client.MinecraftClient;

import java.util.AbstractQueue;
import java.util.Iterator;
import java.util.Queue;

/**
 * Stands in for ChunkBuilder's upload queue. Vanilla drains the whole queue every frame, so a burst of
 * finished sections (joining, teleporting, flying fast) uploads in one long frame; here the render thread's
 * poll() stops handing out uploads once a frame has spent its budget, and the rest follow next frame.
 * Everything else (adding from the builder threads, size, clearing) goes straight to the original queue.
 */
public final class UploadPacer extends AbstractQueue<Runnable> {
	private static final long BUDGET_NANOS = 3_000_000L;

	private final Queue<Runnable> queue;
	private int frame = -1;
	private long deadline;

	public UploadPacer(Queue<Runnable> queue) {
		this.queue = queue;
	}

	@Override
	public Runnable poll() {
		if (Optimizations.uploads() && MinecraftClient.getInstance().isOnThread()) {
			long now = System.nanoTime();
			if (frame != Optimizations.frame()) {
				frame = Optimizations.frame();
				deadline = now + BUDGET_NANOS;
			} else if (now > deadline) {
				return null;
			}
		}
		return queue.poll();
	}

	@Override
	public boolean offer(Runnable task) {
		return queue.offer(task);
	}

	@Override
	public Runnable peek() {
		return queue.peek();
	}

	@Override
	public int size() {
		return queue.size();
	}

	@Override
	public boolean isEmpty() {
		return queue.isEmpty();
	}

	@Override
	public void clear() {
		queue.clear();
	}

	@Override
	public Iterator<Runnable> iterator() {
		return queue.iterator();
	}
}
