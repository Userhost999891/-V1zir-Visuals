package dev.v1zir.visuals.module.impl.client;

import dev.v1zir.visuals.chat.ChatTransport;
import dev.v1zir.visuals.chat.Messages;
import dev.v1zir.visuals.chat.MqttTransport;
import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.module.setting.KeySetting;
import dev.v1zir.visuals.module.setting.ModeSetting;
import org.lwjgl.glfw.GLFW;

/**
 * Messages: private chat with other players of V1zir Visuals ({@link Messages}). On by default; off, the game does not
 * connect at all. Everyone has to be on the same server to reach each other, so the server is a choice of public
 * relays rather than a free field.
 */
public final class MessagesModule extends Module {
	public final BoolSetting notifications = add(new BoolSetting("Notifications", true));
	public final BoolSetting sound = add(new BoolSetting("Sound", true));
	/** Others see when you are online (and when you were last). */
	public final BoolSetting appearOnline = add(new BoolSetting("Appear online", true));
	/** Opens Messages in game, or the conversation of the notification showing. */
	public final KeySetting openKey = add(new KeySetting("Open key", GLFW.GLFW_KEY_Y));
	public final ModeSetting server = add(new ModeSetting("Server", "EMQX", "EMQX", "HiveMQ"));
	/** Turned on, it opens Messages with the editor of your name and picture there, and turns itself off again. */
	public final BoolSetting editProfile = add(new BoolSetting("Edit profile", false));

	public MessagesModule() {
		super("Messages", "Private, end-to-end encrypted chat with other V1zir Visuals players", Category.SETTINGS);
		setEnabled(true, false);
	}

	@Override
	public boolean showInList() {
		return false;
	}

	@Override
	public void onTick() {
		// The "button": done once, then off again.
		if (editProfile.on()) {
			editProfile.set(false);
			Messages.openProfile(mc.currentScreen);
		}
	}

	@Override
	public void onKeyPressed(int key) {
		if (openKey.isSet() && key == openKey.key()) Messages.openLatest(mc.currentScreen);
	}

	/** The relay of the chosen server: MQTT over TLS. */
	public ChatTransport transport() {
		return switch (server.get()) {
			case "HiveMQ" -> new MqttTransport("broker.hivemq.com", 8883, true);
			default -> new MqttTransport("broker.emqx.io", 8883, true);
		};
	}
}
