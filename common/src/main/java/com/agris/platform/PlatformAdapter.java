package com.agris.platform;

import java.nio.file.Path;

/**
 * Loader-independent service provider interface implemented by the {@code fabric}
 * and {@code neoforge} modules (and by headless harnesses in unit tests).
 */
public interface PlatformAdapter {
    /** Short lowercase loader name ({@code "fabric"}, {@code "neoforge"}, {@code "test"}). */
    String platformName();

    /** Active Minecraft version string, e.g. {@code "1.20.1"}. */
    String minecraftVersion();

    /** Root game directory ({@code .minecraft} or instance root). */
    Path gameDirectory();

    /** Configuration directory ({@code <game>/config}). */
    Path configDirectory();

    /** Shader pack directory ({@code <game>/shaderpacks}). */
    default Path shaderpacksDirectory() {
        return gameDirectory().resolve("shaderpacks");
    }

    /** True when running on the physical client distribution. */
    boolean isClient();

    /** True when running inside a development workspace. */
    boolean isDevelopmentEnvironment();

    /** True when a mod (or provided alias) with {@code modId} is present. */
    boolean isModLoaded(String modId);

    /**
     * True when a mod OTHER than Agris itself provides {@code modId}.
     * Used so Agris can declare {@code provides: ["iris"]} for downstream ecosystem
     * compatibility while still detecting if standalone Iris is installed alongside it.
     */
    default boolean isExternalModLoaded(final String modId) {
        if ("agris".equals(modId)) {
            return false;
        }
        return isModLoaded(modId);
    }

    /** Installed version string of {@code modId}, or {@code "unknown"} when absent. */
    String getModVersion(String modId);
}
