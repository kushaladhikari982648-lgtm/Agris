package org.slf4j;

/** Offline stand-in: a quiet logger (run with -Dstub.log=true to print), so code under test can log. */
public final class LoggerFactory {
    private static final boolean PRINT = Boolean.getBoolean("stub.log");

    public static Logger getLogger(final String name) {
        return new Logger() {
            private void out(final String level, final String m, final Object... a) {
                if (PRINT) {
                    System.err.println("[" + level + "] " + name + ": " + m + (a.length > 0 ? " " + java.util.Arrays.toString(a) : ""));
                }
            }
            public void info(final String m, final Object... a) { out("INFO", m, a); }
            public void warn(final String m, final Object... a) { out("WARN", m, a); }
            public void error(final String m, final Object... a) { out("ERROR", m, a); }
            public void debug(final String m, final Object... a) { out("DEBUG", m, a); }
            public void trace(final String m, final Object... a) { out("TRACE", m, a); }
            public boolean isDebugEnabled() { return PRINT; }
        };
    }

    public static Logger getLogger(final Class<?> c) {
        return getLogger(c.getName());
    }
}
