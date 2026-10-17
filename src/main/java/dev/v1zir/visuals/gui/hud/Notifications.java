package dev.v1zir.visuals.gui.hud;

import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.impl.hud.NotificationsModule;
import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.font.Icons;
import dev.v1zir.visuals.render.font.SdfFont;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Toasts in the bottom right corner (module toggles and other messages). */
public final class Notifications {
	private static final List<Toast> TOASTS = new ArrayList<>();
	private static final float DURATION = 2.2f;

	private Notifications() {
	}

	public static void moduleToggled(Module module) {
		push(module.isEnabled() ? Icons.CHECK : Icons.X, module.name(), module.isEnabled() ? "Enabled" : "Disabled", module.isEnabled());
	}

	public static void push(int icon, String title, String text, boolean positive) {
		if (!ModuleManager.isEnabled(NotificationsModule.class)) return;
		TOASTS.removeIf(t -> t.title.equals(title));
		TOASTS.add(new Toast(icon, title, text, positive));
		while (TOASTS.size() > 6) TOASTS.remove(0);
	}

	public static void tick() {
	}

	public static void render(DrawContext ctx, int screenWidth, int screenHeight) {
		if (TOASTS.isEmpty()) return;
		long now = System.nanoTime();
		SdfFont titleFont = Fonts.semibold();
		SdfFont textFont = Fonts.regular();
		float y = screenHeight - 8f;
		Iterator<Toast> it = TOASTS.iterator();
		List<Toast> alive = new ArrayList<>();
		while (it.hasNext()) {
			Toast toast = it.next();
			float age = (now - toast.created) / 1.0e9f;
			if (age > DURATION + 0.4f) it.remove();
			else alive.add(toast);
		}
		for (int i = alive.size() - 1; i >= 0; i--) {
			Toast toast = alive.get(i);
			float age = (now - toast.created) / 1.0e9f;
			float in = Anim.easeOutBack(Anim.progress(age, 0f, 0.35f));
			float out = 1f - Anim.easeInOutCubic(Anim.progress(age, DURATION, 0.35f));
			float h = 24f;
			float w = Math.max(titleFont.width(toast.title, 7.5f), textFont.width(toast.text, 6.5f)) + 36f;
			float slot = toast.slot.target(h + 4f).get() * out;
			y -= slot;
			float x = screenWidth - 8f - w + (1f - in) * (w + 12f);
			Render2D.setAlpha(Math.min(1f, out * 1.2f));
			int accent = toast.positive ? Theme.accent() : 0xFFFF5C6C;
			Render2D.shadow(ctx, x, y + 1f, w, h, 6f, 10f, 0x66000000);
			Render2D.round(ctx, x, y, w, h, 6f, Theme.panel());
			Render2D.outline(ctx, x, y, w, h, 6f, 1f, Theme.outline());
			Render2D.circle(ctx, x + 12f, y + h / 2f, 7f, ColorUtil.withAlpha(accent, 50));
			Render2D.icon(ctx, toast.icon, x + 12f, y + h / 2f, 8f, accent);
			Render2D.text(ctx, titleFont, toast.title, x + 24f, y + 4f, 7.5f, Theme.text());
			Render2D.text(ctx, textFont, toast.text, x + 24f, y + 13f, 6.5f, Theme.textDim());
			float progress = 1f - Math.min(1f, age / DURATION);
			Render2D.round(ctx, x + 6f, y + h - 2f, (w - 12f) * progress, 1f, 0.5f, ColorUtil.withAlpha(accent, 160));
			Render2D.setAlpha(1f);
		}
	}

	private static final class Toast {
		final int icon;
		final String title;
		final String text;
		final boolean positive;
		final long created = System.nanoTime();
		final Anim slot = new Anim(0f, 14f);

		Toast(int icon, String title, String text, boolean positive) {
			this.icon = icon;
			this.title = title;
			this.text = text;
			this.positive = positive;
		}
	}
}
