package com.terraformersmc.modmenu.api;

/**
 * Compile-time Mod Menu API stand-in (excluded from the shipped jar in {@code fabric/build.gradle.kts}).
 */
public interface ModMenuApi {
    default ConfigScreenFactory<?> getModConfigScreenFactory() {
        return screen -> null;
    }
}
