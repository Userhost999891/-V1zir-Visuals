package dev.v1zir.visuals.compat.voicechat;

import dev.v1zir.visuals.V1zir;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.Consumer;

/**
 * The plugin Simple Voice Chat loads for Voice Chat Music. Its API is not needed to build the mod: the plugin is a proxy
 * of its plugin interface made when voice chat asks for it (VoiceChatAdapter), and the events are reached through
 * reflection. It merges the music into what the microphone sends, also while the player is not talking
 * (MergeClientSoundEvent); with an older voice chat that lacks that event, it mixes it into the microphone's own
 * audio while they talk (ClientSoundEvent).
 */
public final class VoiceChatBridge implements InvocationHandler {
	private static final String API = "de.maxhenkel.voicechat.api.";
	private static Method merge;
	private static Method rawAudio;
	private static Method setRawAudio;
	private static boolean failed;

	private VoiceChatBridge() {
	}

	/** A proxy of voice chat's plugin interface. */
	static Object plugin(Class<?> type) {
		return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, new VoiceChatBridge());
	}

	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		switch (method.getName()) {
			case "getPluginId":
				return V1zir.MOD_ID + "_music";
			case "initialize":
				return null;
			case "registerEvents":
				register(args[0]);
				return null;
			case "hashCode":
				return System.identityHashCode(proxy);
			case "equals":
				return proxy == args[0];
			case "toString":
				return V1zir.NAME + " voice chat music";
			default:
				if (method.isDefault()) return InvocationHandler.invokeDefault(proxy, method, args);
				Class<?> type = method.getReturnType();
				if (type == boolean.class) return false;
				if (type.isPrimitive() && type != void.class) return 0;
				return null;
		}
	}

	private static void register(Object registration) {
		try {
			ClassLoader loader = registration.getClass().getClassLoader();
			Method register = Class.forName(API + "events.EventRegistration", false, loader).getMethod("registerEvent", Class.class, Consumer.class);
			try {
				Class<?> event = Class.forName(API + "events.MergeClientSoundEvent", false, loader);
				merge = event.getMethod("mergeAudio", short[].class);
				register.invoke(registration, event, (Consumer<Object>) VoiceChatBridge::onMerge);
				V1zir.LOGGER.info("Voice Chat Music is ready (merged into the microphone)");
				return;
			} catch (ClassNotFoundException | NoSuchMethodException e) {
				// An older voice chat: mix into the microphone's own audio instead.
			}
			Class<?> event = Class.forName(API + "events.ClientSoundEvent", false, loader);
			rawAudio = event.getMethod("getRawAudio");
			setRawAudio = event.getMethod("setRawAudio", short[].class);
			register.invoke(registration, event, (Consumer<Object>) VoiceChatBridge::onSound);
			V1zir.LOGGER.info("Voice Chat Music is ready (mixed into the microphone while talking)");
		} catch (ReflectiveOperationException | RuntimeException e) {
			V1zir.LOGGER.warn("Voice Chat Music could not hook into Simple Voice Chat", e);
		}
	}

	private static void onMerge(Object event) {
		if (failed) return;
		short[] frame = MusicFeed.pull();
		if (frame == null) return;
		try {
			merge.invoke(event, (Object) frame);
		} catch (IllegalAccessException | InvocationTargetException | RuntimeException e) {
			fail(e);
		}
	}

	private static void onSound(Object event) {
		if (failed) return;
		short[] frame = MusicFeed.pull();
		if (frame == null) return;
		try {
			short[] raw = (short[]) rawAudio.invoke(event);
			if (raw == null) return;
			short[] mixed = new short[raw.length];
			for (int i = 0; i < raw.length; i++) {
				int music = i < frame.length ? frame[i] : 0;
				mixed[i] = (short) Math.max(-32768, Math.min(32767, raw[i] + music));
			}
			setRawAudio.invoke(event, (Object) mixed);
		} catch (IllegalAccessException | InvocationTargetException | RuntimeException e) {
			fail(e);
		}
	}

	private static void fail(Exception e) {
		failed = true;
		V1zir.LOGGER.warn("Voice Chat Music stopped: Simple Voice Chat refused its audio", e);
	}
}
