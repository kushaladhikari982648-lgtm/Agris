package org.junit.jupiter.api;

import java.util.Objects;
import java.util.function.Supplier;

import org.junit.jupiter.api.function.Executable;

/** Minimal JUnit 5 Assertions for running the MC-free tests without Maven (tools/testrun.py). */
public final class Assertions {
    private Assertions() {
    }

    private static String msg(final Object message) {
        if (message instanceof Supplier) {
            final Object v = ((Supplier<?>) message).get();
            return v == null ? "" : v + " ==> ";
        }
        return message == null ? "" : message + " ==> ";
    }

    private static void failNotEqual(final Object expected, final Object actual, final Object message) {
        throw new AssertionFailedError(msg(message) + "expected: <" + expected + "> but was: <" + actual + ">");
    }

    public static <V> V fail(final String message) {
        throw new AssertionFailedError(message);
    }

    // ---- equals
    public static void assertEquals(final Object e, final Object a) { assertEquals(e, a, (Object) null); }
    public static void assertEquals(final Object e, final Object a, final String m) { assertEquals(e, a, (Object) m); }
    public static void assertEquals(final Object e, final Object a, final Supplier<String> m) { assertEquals(e, a, (Object) m); }
    private static void assertEquals(final Object e, final Object a, final Object m) {
        if (!Objects.equals(e, a)) failNotEqual(e, a, m);
    }
    public static void assertEquals(final long e, final long a) { if (e != a) failNotEqual(e, a, null); }
    public static void assertEquals(final long e, final long a, final String m) { if (e != a) failNotEqual(e, a, m); }
    public static void assertEquals(final long e, final long a, final Supplier<String> m) { if (e != a) failNotEqual(e, a, m); }
    public static void assertEquals(final int e, final int a) { if (e != a) failNotEqual(e, a, null); }
    public static void assertEquals(final int e, final int a, final String m) { if (e != a) failNotEqual(e, a, m); }
    public static void assertEquals(final int e, final int a, final Supplier<String> m) { if (e != a) failNotEqual(e, a, m); }
    public static void assertEquals(final char e, final char a) { if (e != a) failNotEqual(e, a, null); }
    public static void assertEquals(final boolean e, final boolean a) { if (e != a) failNotEqual(e, a, null); }
    public static void assertEquals(final boolean e, final boolean a, final String m) { if (e != a) failNotEqual(e, a, m); }
    public static void assertEquals(final double e, final double a) { if (Double.doubleToLongBits(e) != Double.doubleToLongBits(a)) failNotEqual(e, a, null); }
    public static void assertEquals(final double e, final double a, final String m) { if (Double.doubleToLongBits(e) != Double.doubleToLongBits(a)) failNotEqual(e, a, m); }
    public static void assertEquals(final double e, final double a, final double d) { if (Math.abs(e - a) > d) failNotEqual(e, a, null); }
    public static void assertEquals(final double e, final double a, final double d, final String m) { if (Math.abs(e - a) > d) failNotEqual(e, a, m); }
    public static void assertEquals(final double e, final double a, final double d, final Supplier<String> m) { if (Math.abs(e - a) > d) failNotEqual(e, a, m); }
    public static void assertEquals(final float e, final float a) { if (Float.floatToIntBits(e) != Float.floatToIntBits(a)) failNotEqual(e, a, null); }
    public static void assertEquals(final float e, final float a, final float d) { if (Math.abs(e - a) > d) failNotEqual(e, a, null); }
    public static void assertEquals(final float e, final float a, final float d, final String m) { if (Math.abs(e - a) > d) failNotEqual(e, a, m); }

    // ---- booleans / nulls
    public static void assertTrue(final boolean c) { if (!c) throw new AssertionFailedError("expected: <true> but was: <false>"); }
    public static void assertTrue(final boolean c, final String m) { if (!c) throw new AssertionFailedError(msg(m) + "expected: <true> but was: <false>"); }
    public static void assertTrue(final boolean c, final Supplier<String> m) { if (!c) throw new AssertionFailedError(msg(m) + "expected: <true> but was: <false>"); }
    public static void assertFalse(final boolean c) { if (c) throw new AssertionFailedError("expected: <false> but was: <true>"); }
    public static void assertFalse(final boolean c, final String m) { if (c) throw new AssertionFailedError(msg(m) + "expected: <false> but was: <true>"); }
    public static void assertFalse(final boolean c, final Supplier<String> m) { if (c) throw new AssertionFailedError(msg(m) + "expected: <false> but was: <true>"); }
    public static void assertNull(final Object o) { if (o != null) failNotEqual(null, o, null); }
    public static void assertNull(final Object o, final String m) { if (o != null) failNotEqual(null, o, m); }
    public static void assertNotNull(final Object o) { if (o == null) throw new AssertionFailedError("expected: not <null>"); }
    public static void assertNotNull(final Object o, final String m) { if (o == null) throw new AssertionFailedError(msg(m) + "expected: not <null>"); }

    // ---- types / exceptions
    public static <T> T assertInstanceOf(final Class<T> type, final Object o) { return assertInstanceOf(type, o, null); }
    public static <T> T assertInstanceOf(final Class<T> type, final Object o, final String m) {
        if (!type.isInstance(o)) {
            throw new AssertionFailedError(msg(m) + "expected instance of " + type.getName() + " but was " + (o == null ? "null" : o.getClass().getName()));
        }
        return type.cast(o);
    }
    public static <T extends Throwable> T assertThrows(final Class<T> type, final Executable e) { return assertThrows(type, e, null); }
    public static <T extends Throwable> T assertThrows(final Class<T> type, final Executable e, final String m) {
        try {
            e.execute();
        } catch (final Throwable t) {
            if (type.isInstance(t)) return type.cast(t);
            throw new AssertionFailedError(msg(m) + "expected " + type.getName() + " but " + t + " was thrown");
        }
        throw new AssertionFailedError(msg(m) + "expected " + type.getName() + " to be thrown, but nothing was thrown");
    }
}
