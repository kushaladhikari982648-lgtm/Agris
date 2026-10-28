package com.aetherium;

import com.aetherium.config.AetheriumConfig;

public final class Aetherium {
    public static boolean ACTIVE = true;
    public static final AetheriumConfig CONFIG = new AetheriumConfig();

    private Aetherium() {
    }

    public static boolean isActive() {
        return ACTIVE;
    }

    public static AetheriumConfig config() {
        return CONFIG;
    }

    public static Object android() {
        return null;
    }
}
