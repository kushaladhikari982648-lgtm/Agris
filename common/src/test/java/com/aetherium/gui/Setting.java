package com.aetherium.gui;

import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public final class Setting {
    public enum Kind { TOGGLE, SEGMENTED, DROPDOWN, SLIDER, INFO, BUTTON }

    public final String key;
    public final String label;
    public final String description;
    public BooleanSupplier availability;

    private Setting(final String key, final String label, final String description) {
        this.key = key;
        this.label = label;
        this.description = description;
    }

    public static Setting toggle(final String key, final String label, final String desc,
                                 final IntSupplier getter, final IntConsumer setter) {
        return new Setting(key, label, desc);
    }

    public static Setting choice(final String key, final Kind kind, final String label, final String desc,
                                 final String[] choices, final IntSupplier getter, final IntConsumer setter) {
        return new Setting(key, label, desc);
    }

    public static Setting slider(final String key, final String label, final String desc,
                                 final int min, final int max, final int step,
                                 final IntFunction<String> fmt, final IntSupplier getter, final IntConsumer setter) {
        return new Setting(key, label, desc);
    }

    public static Setting button(final String key, final String label, final String desc,
                                 final String buttonText, final Runnable action) {
        return new Setting(key, label, desc);
    }

    public static Setting info(final String key, final String label, final String desc,
                               final Supplier<String> supplier) {
        return new Setting(key, label, desc);
    }

    public Setting availableWhen(final BooleanSupplier cond, final String reason) {
        this.availability = cond;
        return this;
    }
}
