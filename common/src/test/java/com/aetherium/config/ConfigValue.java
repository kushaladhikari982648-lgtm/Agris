package com.aetherium.config;

public final class ConfigValue<T> {
    private T value;

    public ConfigValue(final T initial) {
        this.value = initial;
    }

    public T get() {
        return this.value;
    }

    public void set(final T next) {
        this.value = next;
    }
}
