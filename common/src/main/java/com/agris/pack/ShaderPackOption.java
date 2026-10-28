package com.agris.pack;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents a single user-configurable option declared by a shader pack
 * (boolean {@code #define}, value {@code #define}, or {@code const} option).
 */
public final class ShaderPackOption {
    public enum Kind {
        BOOLEAN_DEFINE,
        VALUE_DEFINE,
        BOOLEAN_CONST,
        VALUE_CONST
    }

    private final String name;
    private final Kind kind;
    private final String glslType;
    private final String defaultValue;
    private final List<String> allowedValues;
    private final String comment;
    private boolean slider;

    private ShaderPackOption(final String name, final Kind kind, final String glslType,
                             final String defaultValue, final List<String> allowedValues,
                             final String comment) {
        this.name = Objects.requireNonNull(name, "name");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.glslType = Objects.requireNonNull(glslType, "glslType");
        this.defaultValue = Objects.requireNonNull(defaultValue, "defaultValue");
        this.allowedValues = Collections.unmodifiableList(allowedValues);
        this.comment = comment == null ? "" : comment;
    }

    public static ShaderPackOption booleanDefine(final String name, final boolean defaultValue, final String comment) {
        final String def = Boolean.toString(defaultValue);
        return new ShaderPackOption(name, Kind.BOOLEAN_DEFINE, "bool", def, List.of("false", "true"), comment);
    }

    public static ShaderPackOption valueDefine(final String name, final String defaultValue,
                                               final List<String> allowedValues, final String comment) {
        return new ShaderPackOption(name, Kind.VALUE_DEFINE, "define", defaultValue, allowedValues, comment);
    }

    public static ShaderPackOption constBoolean(final String name, final boolean defaultValue, final String comment) {
        final String def = Boolean.toString(defaultValue);
        return new ShaderPackOption(name, Kind.BOOLEAN_CONST, "bool", def, List.of("false", "true"), comment);
    }

    public static ShaderPackOption constValue(final String name, final String glslType, final String defaultValue,
                                              final List<String> allowedValues, final String comment) {
        return new ShaderPackOption(name, Kind.VALUE_CONST, glslType, defaultValue, allowedValues, comment);
    }

    public String name() {
        return this.name;
    }

    public Kind kind() {
        return this.kind;
    }

    public String glslType() {
        return this.glslType;
    }

    public String defaultValue() {
        return this.defaultValue;
    }

    public List<String> allowedValues() {
        return this.allowedValues;
    }

    public String comment() {
        return this.comment;
    }

    public boolean isBoolean() {
        return this.kind == Kind.BOOLEAN_DEFINE || this.kind == Kind.BOOLEAN_CONST;
    }

    public boolean isSlider() {
        return this.slider && !isBoolean() && this.allowedValues.size() > 1;
    }

    public void setSlider(final boolean slider) {
        this.slider = slider;
    }

    public boolean isValidValue(final String candidate) {
        if (candidate == null) {
            return false;
        }
        if (isBoolean()) {
            return "true".equalsIgnoreCase(candidate) || "false".equalsIgnoreCase(candidate);
        }
        return this.allowedValues.isEmpty() || this.allowedValues.contains(candidate);
    }

    public String nextValue(final String current) {
        if (isBoolean()) {
            return Boolean.toString(!Boolean.parseBoolean(current));
        }
        if (this.allowedValues.isEmpty()) {
            return this.defaultValue;
        }
        final int idx = this.allowedValues.indexOf(current);
        final int next = idx < 0 ? 0 : (idx + 1) % this.allowedValues.size();
        return this.allowedValues.get(next);
    }

    public String previousValue(final String current) {
        if (isBoolean()) {
            return Boolean.toString(!Boolean.parseBoolean(current));
        }
        if (this.allowedValues.isEmpty()) {
            return this.defaultValue;
        }
        final int idx = this.allowedValues.indexOf(current);
        final int prev = idx <= 0 ? this.allowedValues.size() - 1 : idx - 1;
        return this.allowedValues.get(prev);
    }
}
