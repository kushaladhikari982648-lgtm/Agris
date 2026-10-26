package com.agris.platform;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.ServiceLoader;

/**
 * Resolves and caches the active {@link PlatformAdapter}.
 */
public final class PlatformServices {
    private static volatile PlatformAdapter active;

    private PlatformServices() {
    }

    /** Explicitly installs an adapter (used by loader entrypoints and unit tests). */
    public static void install(final PlatformAdapter adapter) {
        active = Objects.requireNonNull(adapter, "adapter");
    }

    /** Clears any explicitly installed adapter (used by test teardown). */
    public static void reset() {
        active = null;
    }

    /** Returns the active adapter, falling back to {@link ServiceLoader} or a local fallback. */
    public static PlatformAdapter get() {
        PlatformAdapter current = active;
        if (current != null) {
            return current;
        }
        synchronized (PlatformServices.class) {
            current = active;
            if (current != null) {
                return current;
            }
            for (final PlatformAdapter discovered : ServiceLoader.load(PlatformAdapter.class,
                    PlatformServices.class.getClassLoader())) {
                active = discovered;
                return discovered;
            }
            current = new FallbackAdapter();
            active = current;
            return current;
        }
    }

    private static final class FallbackAdapter implements PlatformAdapter {
        @Override
        public String platformName() {
            return "standalone";
        }

        @Override
        public String minecraftVersion() {
            return "1.20.1";
        }

        @Override
        public Path gameDirectory() {
            return Paths.get(".").toAbsolutePath().normalize();
        }

        @Override
        public Path configDirectory() {
            return gameDirectory().resolve("config");
        }

        @Override
        public boolean isClient() {
            return true;
        }

        @Override
        public boolean isDevelopmentEnvironment() {
            return false;
        }

        @Override
        public boolean isModLoaded(final String modId) {
            return "agris".equals(modId);
        }

        @Override
        public String getModVersion(final String modId) {
            return "agris".equals(modId) ? "1.0.0" : "unknown";
        }
    }
}
