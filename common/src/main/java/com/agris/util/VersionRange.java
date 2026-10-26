package com.agris.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Semantic and Maven-style version comparator and range matcher ({@code [1.0,2.0)}, {@code >=1.17.0}, {@code *}).
 */
public final class VersionRange {
    private final String raw;
    private final Bound lower;
    private final Bound upper;

    private VersionRange(final String raw, final Bound lower, final Bound upper) {
        this.raw = raw;
        this.lower = lower;
        this.upper = upper;
    }

    public static VersionRange parse(final String spec) {
        Objects.requireNonNull(spec, "spec");
        final String trimmed = spec.trim();
        if (trimmed.isEmpty() || "*".equals(trimmed)) {
            return new VersionRange(trimmed, null, null);
        }
        if ((trimmed.startsWith("[") || trimmed.startsWith("("))
                && (trimmed.endsWith("]") || trimmed.endsWith(")"))) {
            final boolean minInclusive = trimmed.charAt(0) == '[';
            final boolean maxInclusive = trimmed.charAt(trimmed.length() - 1) == ']';
            final String body = trimmed.substring(1, trimmed.length() - 1).trim();
            final int comma = body.indexOf(',');
            if (comma < 0) {
                return new VersionRange(trimmed, new Bound(body, minInclusive), new Bound(body, maxInclusive));
            }
            final String left = body.substring(0, comma).trim();
            final String right = body.substring(comma + 1).trim();
            final Bound lo = left.isEmpty() ? null : new Bound(left, minInclusive);
            final Bound hi = right.isEmpty() ? null : new Bound(right, maxInclusive);
            return new VersionRange(trimmed, lo, hi);
        }
        if (trimmed.contains(" ") || (trimmed.contains(",") && !trimmed.startsWith("[") && !trimmed.startsWith("("))) {
            Bound lo = null;
            Bound hi = null;
            for (final String part : trimmed.split("[,\\s]+")) {
                if (part.isEmpty()) {
                    continue;
                }
                final VersionRange sub = parse(part);
                if (sub.lower != null) {
                    lo = sub.lower;
                }
                if (sub.upper != null) {
                    hi = sub.upper;
                }
            }
            return new VersionRange(trimmed, lo, hi);
        }
        if (trimmed.startsWith(">=")) {
            return new VersionRange(trimmed, new Bound(trimmed.substring(2).trim(), true), null);
        }
        if (trimmed.startsWith(">")) {
            return new VersionRange(trimmed, new Bound(trimmed.substring(1).trim(), false), null);
        }
        if (trimmed.startsWith("<=")) {
            return new VersionRange(trimmed, null, new Bound(trimmed.substring(2).trim(), true));
        }
        if (trimmed.startsWith("<")) {
            return new VersionRange(trimmed, null, new Bound(trimmed.substring(1).trim(), false));
        }
        return new VersionRange(trimmed, new Bound(trimmed, true), new Bound(trimmed, true));
    }

    public boolean contains(final String version) {
        if (version == null || version.isBlank() || "unknown".equalsIgnoreCase(version.trim())) {
            return this.lower == null && this.upper == null;
        }
        if (this.lower != null) {
            final int cmp = compareVersions(version, this.lower.version);
            if (this.lower.inclusive ? cmp < 0 : cmp <= 0) {
                return false;
            }
        }
        if (this.upper != null) {
            final int cmp = compareVersions(version, this.upper.version);
            if (this.upper.inclusive ? cmp > 0 : cmp >= 0) {
                return false;
            }
        }
        return true;
    }

    public static int compareVersions(final String a, final String b) {
        final List<Integer> left = numericParts(a);
        final List<Integer> right = numericParts(b);
        final int max = Math.max(left.size(), right.size());
        for (int i = 0; i < max; i++) {
            final int lv = i < left.size() ? left.get(i) : 0;
            final int rv = i < right.size() ? right.get(i) : 0;
            if (lv != rv) {
                return Integer.compare(lv, rv);
            }
        }
        return 0;
    }

    private static List<Integer> numericParts(final String raw) {
        final List<Integer> parts = new ArrayList<>(4);
        if (raw == null) {
            return parts;
        }
        final int plus = raw.indexOf('+');
        final String clean = (plus >= 0 ? raw.substring(0, plus) : raw).trim();
        int value = 0;
        boolean inDigits = false;
        for (int i = 0; i < clean.length(); i++) {
            final char c = clean.charAt(i);
            if (c >= '0' && c <= '9') {
                value = value * 10 + (c - '0');
                inDigits = true;
            } else {
                if (inDigits) {
                    parts.add(value);
                    value = 0;
                    inDigits = false;
                }
                if (c == '-') {
                    break;
                }
            }
        }
        if (inDigits) {
            parts.add(value);
        }
        return parts;
    }

    @Override
    public String toString() {
        return this.raw;
    }

    private record Bound(String version, boolean inclusive) {
    }
}
