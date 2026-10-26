package com.aetherium.config;

public final class AetheriumConfig {
    public enum ShaderPreset {
        OFF,
        VIBRANT,
        CINEMATIC
    }

    public final ConfigValue<ShaderPreset> shaderPreset = new ConfigValue<>(ShaderPreset.VIBRANT);
    public final ConfigValue<Integer> renderScale = new ConfigValue<>(85);
    public final ConfigValue<Boolean> darkMode = new ConfigValue<>(true);
}
