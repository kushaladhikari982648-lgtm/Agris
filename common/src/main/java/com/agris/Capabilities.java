package com.agris;

/**
 * Compile-time era capability constants for Fabric Minecraft 1.20.1 (Java 17).
 *
 * <p>Mirrors Aetherium's {@code Capabilities} design for Minecraft 1.20.1.</p>
 */
public final class Capabilities {
    public static final String REFERENCE_MINECRAFT = "1.20.1";
    public static final int MC_VERSION_INT = 12001;
    public static final boolean CORE_PROFILE_GL = true;
    public static final boolean JOML_MATRICES = true;
    public static final boolean DRAW_GRAPHICS_GUI = true;
    public static final boolean DELTA_TRACKER_RENDER = false;

    private Capabilities() {
    }
}
