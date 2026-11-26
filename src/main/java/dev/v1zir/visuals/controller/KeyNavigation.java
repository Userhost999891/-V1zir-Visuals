package dev.v1zir.visuals.controller;

/**
 * A screen of the client's own that goes through what it shows with the arrow keys itself (the PS5 style menu, the
 * main menu's lock screen): a controller's D-pad is the arrow keys there, instead of moving the pointer from place to
 * place (Targets), as long as it says so.
 */
public interface KeyNavigation {
	/** Whether the arrow keys go through what it shows now; false: the D-pad moves the pointer (an open panel, say). */
	default boolean keysNow() {
		return true;
	}
}
