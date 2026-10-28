package com.agris.util;

import java.lang.reflect.Method;

/**
 * Zero-dependency logger facade backed by SLF4J when available on the runtime
 * classpath and clean {@code System.err} formatting during headless unit tests.
 */
public final class AgrisLog {
    private static final String PREFIX = "[Agris] ";
    private static final Backend BACKEND = resolveBackend();
    private static volatile boolean debugEnabled = Boolean.getBoolean("agris.debug");

    private AgrisLog() {
    }

    public static void setDebugEnabled(final boolean enabled) {
        debugEnabled = enabled;
    }

    public static boolean isDebugEnabled() {
        return debugEnabled;
    }

    public static void info(final String message, final Object... args) {
        BACKEND.log(Level.INFO, format(message, args), null);
    }

    public static void warn(final String message, final Object... args) {
        BACKEND.log(Level.WARN, format(message, args), null);
    }

    public static void error(final String message, final Throwable error) {
        BACKEND.log(Level.ERROR, message == null ? "Unhandled error" : message, error);
    }

    public static void error(final String message, final Object... args) {
        Throwable trailing = null;
        Object[] effective = args;
        if (args != null && args.length > 0 && args[args.length - 1] instanceof Throwable) {
            trailing = (Throwable) args[args.length - 1];
            effective = new Object[args.length - 1];
            System.arraycopy(args, 0, effective, 0, effective.length);
        }
        BACKEND.log(Level.ERROR, format(message, effective), trailing);
    }

    public static void debug(final String message, final Object... args) {
        if (debugEnabled) {
            BACKEND.log(Level.DEBUG, format(message, args), null);
        }
    }

    public static String format(final String template, final Object... args) {
        if (template == null) {
            return "";
        }
        if (args == null || args.length == 0) {
            return template;
        }
        final StringBuilder out = new StringBuilder(template.length() + 32);
        int cursor = 0;
        int argIndex = 0;
        while (cursor < template.length()) {
            final int brace = template.indexOf("{}", cursor);
            if (brace < 0 || argIndex >= args.length) {
                out.append(template, cursor, template.length());
                break;
            }
            out.append(template, cursor, brace);
            out.append(args[argIndex++]);
            cursor = brace + 2;
        }
        return out.toString();
    }

    private enum Level {
        DEBUG,
        INFO,
        WARN,
        ERROR
    }

    private interface Backend {
        void log(Level level, String message, Throwable error);
    }

    private static Backend resolveBackend() {
        try {
            final Class<?> factoryClass = Class.forName("org.slf4j.LoggerFactory");
            final Method getLogger = factoryClass.getMethod("getLogger", String.class);
            final Object logger = getLogger.invoke(null, "Agris");
            if (logger != null) {
                final Class<?> loggerClass = logger.getClass();
                final Method info = loggerClass.getMethod("info", String.class);
                final Method warn = loggerClass.getMethod("warn", String.class);
                final Method debug = loggerClass.getMethod("debug", String.class);
                final Method errorMsg = loggerClass.getMethod("error", String.class);
                final Method errorThr = loggerClass.getMethod("error", String.class, Throwable.class);
                return (level, message, error) -> {
                    try {
                        switch (level) {
                            case DEBUG -> debug.invoke(logger, PREFIX + message);
                            case WARN -> warn.invoke(logger, PREFIX + message);
                            case ERROR -> {
                                if (error != null) {
                                    errorThr.invoke(logger, PREFIX + message, error);
                                } else {
                                    errorMsg.invoke(logger, PREFIX + message);
                                }
                            }
                            default -> info.invoke(logger, PREFIX + message);
                        }
                    } catch (final ReflectiveOperationException ignored) {
                        fallbackLog(level, message, error);
                    }
                };
            }
        } catch (final Throwable ignored) {
            // Headless unit test environment without SLF4J implementation.
        }
        return AgrisLog::fallbackLog;
    }

    private static void fallbackLog(final Level level, final String message, final Throwable error) {
        System.err.println(PREFIX + '[' + level.name() + "] " + message);
        if (error != null) {
            error.printStackTrace(System.err);
        }
    }
}
