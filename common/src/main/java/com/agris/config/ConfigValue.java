package com.agris.config;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/**
 * Strongly typed, range-validated configuration entry with change listeners.
 *
 * @param <T> value type ({@code Boolean}, {@code Integer}, {@code String}, or {@code Enum})
 */
public final class ConfigValue<T> {
    public enum ValueKind {
        BOOLEAN,
        INTEGER,
        STRING,
        ENUM
    }

    private final String key;
    private final ValueKind kind;
    private final T defaultValue;
    private final Integer min;
    private final Integer max;
    private final Class<? extends Enum<?>> enumType;
    private final boolean requiresReload;
    private final String description;
    private final List<BiConsumer<T, T>> listeners = new CopyOnWriteArrayList<>();
    private volatile T value;

    private ConfigValue(final String key, final ValueKind kind, final T defaultValue,
                        final Integer min, final Integer max,
                        final Class<? extends Enum<?>> enumType,
                        final boolean requiresReload, final String description) {
        this.key = Objects.requireNonNull(key, "key");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.defaultValue = Objects.requireNonNull(defaultValue, "defaultValue");
        this.min = min;
        this.max = max;
        this.enumType = enumType;
        this.requiresReload = requiresReload;
        this.description = description == null ? "" : description;
        this.value = defaultValue;
    }

    public static ConfigValue<Boolean> bool(final String key, final boolean defaultValue, final String description) {
        return new ConfigValue<>(key, ValueKind.BOOLEAN, defaultValue, null, null, null, false, description);
    }

    public static ConfigValue<Boolean> bool(final String key, final boolean defaultValue,
                                            final boolean requiresReload, final String description) {
        return new ConfigValue<>(key, ValueKind.BOOLEAN, defaultValue, null, null, null, requiresReload, description);
    }

    public static ConfigValue<Integer> intRange(final String key, final int defaultValue,
                                                final int min, final int max,
                                                final boolean requiresReload, final String description) {
        if (min > max || defaultValue < min || defaultValue > max) {
            throw new IllegalArgumentException("Invalid bounds for " + key + ": [" + min + ".." + max + "], default=" + defaultValue);
        }
        return new ConfigValue<>(key, ValueKind.INTEGER, defaultValue, min, max, null, requiresReload, description);
    }

    public static ConfigValue<String> string(final String key, final String defaultValue, final String description) {
        return new ConfigValue<>(key, ValueKind.STRING, Objects.requireNonNull(defaultValue), null, null, null, false, description);
    }

    public static <E extends Enum<E>> ConfigValue<E> enumerated(final String key, final E defaultValue,
                                                                final Class<E> enumType,
                                                                final boolean requiresReload,
                                                                final String description) {
        return new ConfigValue<>(key, ValueKind.ENUM, defaultValue, null, null, enumType, requiresReload, description);
    }

    public String key() {
        return this.key;
    }

    public String group() {
        final int dot = this.key.indexOf('.');
        return dot > 0 ? this.key.substring(0, dot) : "general";
    }

    public ValueKind kind() {
        return this.kind;
    }

    public T defaultValue() {
        return this.defaultValue;
    }

    public Integer min() {
        return this.min;
    }

    public Integer max() {
        return this.max;
    }

    public boolean requiresReload() {
        return this.requiresReload;
    }

    public String description() {
        return this.description;
    }

    public T get() {
        return this.value;
    }

    public boolean set(final T candidate) {
        final T normalized = normalize(Objects.requireNonNull(candidate, "candidate"));
        final T previous;
        synchronized (this) {
            previous = this.value;
            if (Objects.equals(previous, normalized)) {
                return false;
            }
            this.value = normalized;
        }
        for (final BiConsumer<T, T> listener : this.listeners) {
            listener.accept(previous, normalized);
        }
        return true;
    }

    public void reset() {
        set(this.defaultValue);
    }

    public void onChange(final BiConsumer<T, T> listener) {
        this.listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    @SuppressWarnings("unchecked")
    public void deserializeFromJson(final Object raw) {
        if (raw == null) {
            return;
        }
        switch (this.kind) {
            case BOOLEAN -> {
                if (raw instanceof Boolean b) {
                    set((T) b);
                } else if (raw instanceof String s) {
                    set((T) Boolean.valueOf(Boolean.parseBoolean(s.trim())));
                }
            }
            case INTEGER -> {
                if (raw instanceof Number n) {
                    set((T) Integer.valueOf(n.intValue()));
                } else if (raw instanceof String s) {
                    try {
                        set((T) Integer.valueOf(Integer.parseInt(s.trim())));
                    } catch (final NumberFormatException ignored) {
                        // Keep previous/default value on malformed input.
                    }
                }
            }
            case STRING -> set((T) String.valueOf(raw));
            case ENUM -> {
                final String name = String.valueOf(raw).trim();
                for (final Enum<?> constant : this.enumType.getEnumConstants()) {
                    if (constant.name().equalsIgnoreCase(name)) {
                        set((T) constant);
                        return;
                    }
                }
            }
        }
    }

    public Object serializeToJson() {
        final T cur = this.value;
        if (cur instanceof Enum<?> e) {
            return e.name().toLowerCase(Locale.ROOT);
        }
        return cur;
    }

    @SuppressWarnings("unchecked")
    private T normalize(final T candidate) {
        if (this.kind == ValueKind.INTEGER) {
            final int v = (Integer) candidate;
            final int clamped = Math.max(this.min, Math.min(this.max, v));
            return (T) Integer.valueOf(clamped);
        }
        return candidate;
    }
}
