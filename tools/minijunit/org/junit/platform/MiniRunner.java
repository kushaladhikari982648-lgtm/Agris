package org.junit.platform;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.regex.Pattern;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

/** Tiny JUnit 5 runner: @Test/@BeforeEach/@AfterEach/@BeforeAll/@AfterAll/@TempDir/@EnabledIfSystemProperty. */
public final class MiniRunner {
    private static int passed, failed, skipped;

    public static void main(final String[] args) throws Throwable {
        for (final String name : args) {
            runClass(Class.forName(name));
        }
        System.out.println();
        System.out.println("tests: " + passed + " passed, " + failed + " failed, " + skipped + " skipped");
        System.exit(failed == 0 ? 0 : 1);
    }

    private static boolean enabled(final AnnotatedElement e) {
        final EnabledIfSystemProperty c = e.getAnnotation(EnabledIfSystemProperty.class);
        if (c == null) return true;
        final String v = System.getProperty(c.named());
        return v != null && Pattern.matches(c.matches(), v);
    }

    private static List<Method> annotated(final Class<?> type, final Class<? extends java.lang.annotation.Annotation> a) {
        final List<Method> out = new ArrayList<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (final Method m : c.getDeclaredMethods()) {
                if (m.isAnnotationPresent(a)) {
                    m.setAccessible(true);
                    out.add(m);
                }
            }
        }
        out.sort(Comparator.comparing(Method::getName));
        return out;
    }

    private static void runClass(final Class<?> type) throws Throwable {
        if (!enabled(type)) {
            skipped += annotated(type, Test.class).size();
            return;
        }
        final boolean perClass = type.isAnnotationPresent(TestInstance.class)
                && type.getAnnotation(TestInstance.class).value() == TestInstance.Lifecycle.PER_CLASS;
        final List<Path> temps = new ArrayList<>();
        Object shared = perClass ? newInstance(type, temps) : null;
        for (final Method m : annotated(type, BeforeAll.class)) invoke(m, Modifier.isStatic(m.getModifiers()) ? null : shared, temps);
        for (final Method test : annotated(type, Test.class)) {
            final String label = type.getSimpleName() + "." + test.getName();
            if (!enabled(test)) {
                skipped++;
                continue;
            }
            final Object inst = perClass ? shared : newInstance(type, temps);
            Throwable error = null;
            try {
                for (final Method m : annotated(type, BeforeEach.class)) invoke(m, inst, temps);
                invoke(test, inst, temps);
            } catch (final Throwable t) {
                error = t;
            }
            try {
                for (final Method m : annotated(type, AfterEach.class)) invoke(m, inst, temps);
            } catch (final Throwable t) {
                if (error == null) error = t;
            }
            if (error == null) {
                passed++;
            } else {
                failed++;
                System.out.println("FAIL " + label + ": " + error);
                for (final StackTraceElement el : error.getStackTrace()) {
                    if (el.getClassName().startsWith("com.aetherium")) System.out.println("     at " + el);
                }
            }
            if (!perClass) deleteAll(temps);
        }
        for (final Method m : annotated(type, AfterAll.class)) {
            try {
                invoke(m, Modifier.isStatic(m.getModifiers()) ? null : shared, temps);
            } catch (final Throwable t) {
                failed++;
                System.out.println("FAIL " + type.getSimpleName() + " @AfterAll: " + t);
            }
        }
        deleteAll(temps);
    }

    private static Object newInstance(final Class<?> type, final List<Path> temps) throws Exception {
        final Constructor<?> c = type.getDeclaredConstructor();
        c.setAccessible(true);
        final Object inst = c.newInstance();
        for (Class<?> k = type; k != null && k != Object.class; k = k.getSuperclass()) {
            for (final Field f : k.getDeclaredFields()) {
                if (f.isAnnotationPresent(TempDir.class)) {
                    f.setAccessible(true);
                    f.set(Modifier.isStatic(f.getModifiers()) ? null : inst, temp(f.getType(), temps));
                }
            }
        }
        return inst;
    }

    private static Object temp(final Class<?> kind, final List<Path> temps) throws IOException {
        final Path p = Files.createTempDirectory("minijunit");
        temps.add(p);
        return kind == File.class ? p.toFile() : p;
    }

    private static void invoke(final Method m, final Object inst, final List<Path> temps) throws Throwable {
        final Parameter[] ps = m.getParameters();
        final Object[] args = new Object[ps.length];
        for (int i = 0; i < ps.length; i++) {
            if (ps[i].isAnnotationPresent(TempDir.class)) args[i] = temp(ps[i].getType(), temps);
            else throw new IllegalStateException("unsupported parameter " + ps[i] + " on " + m);
        }
        try {
            m.invoke(inst, args);
        } catch (final InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private static void deleteAll(final List<Path> temps) {
        for (final Path root : temps) {
            try {
                Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(final Path f, final BasicFileAttributes a) throws IOException {
                        Files.delete(f);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(final Path d, final IOException e) throws IOException {
                        Files.delete(d);
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (final IOException ignored) {
                // best effort
            }
        }
        temps.clear();
    }
}
