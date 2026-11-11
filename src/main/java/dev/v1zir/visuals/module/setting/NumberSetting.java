package dev.v1zir.visuals.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.Locale;

public class NumberSetting extends Setting<Double> {
	private final double min;
	private final double max;
	private final double step;
	private String suffix = "";

	public NumberSetting(String name, double defaultValue, double min, double max, double step) {
		super(name, defaultValue);
		this.min = min;
		this.max = max;
		this.step = step;
	}

	public NumberSetting suffix(String suffix) {
		this.suffix = suffix;
		return this;
	}

	@Override
	public void set(Double value) {
		double v = Math.max(min, Math.min(max, value));
		if (step > 0) v = Math.round(v / step) * step;
		super.set(Math.max(min, Math.min(max, v)));
	}

	public float f() {
		return value.floatValue();
	}

	public int i() {
		return (int) Math.round(value);
	}

	public double min() {
		return min;
	}

	public double max() {
		return max;
	}

	public double step() {
		return step;
	}

	/** Position of the value on the slider, 0..1. */
	public double fraction() {
		return max == min ? 0 : (value - min) / (max - min);
	}

	public void setFraction(double fraction) {
		set(min + (max - min) * Math.max(0, Math.min(1, fraction)));
	}

	public String display() {
		String number;
		if (step >= 1) number = String.valueOf(Math.round(value));
		else if (step >= 0.1) number = String.format(Locale.ROOT, "%.1f", value);
		else number = String.format(Locale.ROOT, "%.2f", value);
		return number + suffix;
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(value);
	}

	@Override
	public void fromJson(JsonElement element) {
		if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) set(element.getAsDouble());
	}
}
