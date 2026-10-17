package dev.v1zir.visuals.module.impl.visuals;

import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.module.setting.NumberSetting;

/** Client side time of day and weather. */
public final class AmbienceModule extends Module {
	public final BoolSetting customTime = add(new BoolSetting("Custom time", true));
	public final NumberSetting time = add(new NumberSetting("Time", 18000, 0, 24000, 100).visibleIf(customTime::on));
	public final BoolSetting clearWeather = add(new BoolSetting("Clear weather", true));

	private long serverTime = -1;

	public AmbienceModule() {
		super("Ambience", "Changes time of day and weather on your side only", Category.VISUALS);
	}

	@Override
	public String suffix() {
		return customTime.on() ? String.valueOf(time.i()) : null;
	}

	/** Remembers the real time so it can be restored. Returns true to drop the packet. */
	public boolean onServerTime(long timeOfDay) {
		serverTime = timeOfDay;
		return customTime.on();
	}

	@Override
	protected void onEnable() {
		if (mc.world != null) serverTime = mc.world.getTimeOfDay();
	}

	@Override
	protected void onDisable() {
		if (mc.world != null && serverTime >= 0) mc.world.getLevelProperties().setTimeOfDay(serverTime);
	}

	@Override
	public void onTick() {
		if (mc.world == null) return;
		if (customTime.on()) mc.world.getLevelProperties().setTimeOfDay(time.i());
		if (clearWeather.on()) {
			mc.world.setRainGradient(0f);
			mc.world.setThunderGradient(0f);
		}
	}
}
