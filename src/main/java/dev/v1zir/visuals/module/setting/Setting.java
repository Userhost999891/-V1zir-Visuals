package dev.v1zir.visuals.module.setting;

import com.google.gson.JsonElement;

import java.util.function.BooleanSupplier;

public abstract class Setting<T> {
	private final String name;
	private final T defaultValue;
	protected T value;
	private BooleanSupplier visibility = () -> true;

	protected Setting(String name, T defaultValue) {
		this.name = name;
		this.defaultValue = defaultValue;
		this.value = defaultValue;
	}

	public String name() {
		return name;
	}

	public T get() {
		return value;
	}

	public void set(T value) {
		this.value = value;
	}

	public T defaultValue() {
		return defaultValue;
	}

	public void reset() {
		set(defaultValue);
	}

	public boolean isVisible() {
		return visibility.getAsBoolean();
	}

	@SuppressWarnings("unchecked")
	public <S extends Setting<T>> S visibleIf(BooleanSupplier condition) {
		this.visibility = condition;
		return (S) this;
	}

	public abstract JsonElement toJson();

	public abstract void fromJson(JsonElement element);
}
