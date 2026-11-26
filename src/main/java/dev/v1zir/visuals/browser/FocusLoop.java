package dev.v1zir.visuals.browser;

import dev.v1zir.visuals.V1zir;
import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import org.cef.handler.CefFocusHandler;
import org.cef.handler.CefFocusHandlerAdapter;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;

/**
 * Cuts the endless focus loop between JCEF and Chromium, which crashed the game on Windows (exit code 0xC0000409).
 * Off screen, giving a page focus (the full screen browser opening, a click in it, or Chromium itself when a page
 * loads) makes Chromium tell the client at once, on its own thread and inside the same call, that the page got focus.
 * JCEF's CefClient.onGotFocus then gives the page focus again, which tells it again: setFocus, onGotFocus, setFocus...
 * until the thread's stack runs out. On Linux the last call into Java fails and the loop unwinds (the "Exception in
 * thread "AWT-EventQueue-0"" lines with nothing after them); on Windows the game died. The client's focus handler
 * turns down a focus asked for from inside onGotFocus: the page already has it.
 */
final class FocusLoop extends CefFocusHandlerAdapter {
	private static final StackWalker STACK = StackWalker.getInstance();
	/** How many times a loop was cut (CI's check prints it). */
	private static volatile int cut;

	/** A focus handler the client had before, which still hears everything else. */
	@Nullable
	private final CefFocusHandler next;

	private FocusLoop(@Nullable CefFocusHandler next) {
		this.next = next;
	}

	/** Gives the client the handler; before its first page, as pages ask for focus as soon as they load. */
	static void guard(CefClient client) {
		try {
			// JCEF keeps one focus handler and its addFocusHandler keeps one already there, so it is set here, in front
			// of any other.
			Field field = CefClient.class.getDeclaredField("focusHandler_");
			field.setAccessible(true);
			Object current = field.get(client);
			if (!(current instanceof FocusLoop)) field.set(client, new FocusLoop((CefFocusHandler) current));
		} catch (ReflectiveOperationException | RuntimeException e) {
			V1zir.LOGGER.warn("Browser: focus handler set the public way", e);
			client.addFocusHandler(new FocusLoop(null));
		}
	}

	static int cut() {
		return cut;
	}

	@Override
	public boolean onSetFocus(CefBrowser browser, FocusSource source) {
		if (insideGotFocus()) {
			cut++;
			return true;
		}
		return next != null && next.onSetFocus(browser, source);
	}

	@Override
	public void onGotFocus(CefBrowser browser) {
		if (next != null) next.onGotFocus(browser);
	}

	@Override
	public void onTakeFocus(CefBrowser browser, boolean forward) {
		if (next != null) next.onTakeFocus(browser, forward);
	}

	/** Whether JCEF's CefClient.onGotFocus is under this call, on this thread (Chromium's). */
	private static boolean insideGotFocus() {
		return STACK.walk(frames -> frames.anyMatch(frame ->
			"onGotFocus".equals(frame.getMethodName()) && "org.cef.CefClient".equals(frame.getClassName())));
	}
}
