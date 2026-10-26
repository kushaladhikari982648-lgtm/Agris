package com.agris.mobile;

import com.agris.config.AgrisConfig;
import com.agris.pack.BuiltinShaderPacks;

/**
 * One-tap mobile optimization presets tailored for phones, tablets, foldables,
 * handhelds, and Android launchers (PojavLauncher, Zalith, Fold Craft).
 */
public enum MobilePreset {
    AUTO("Auto Mobile", "Adapts automatically when an Android launcher or TBDR mobile GPU is detected."),
    BATTERY_SAVER("Battery Saver", "Ultra-cool thermal profile: 65% scale, shadows off, single-pass Agris Pocket."),
    BALANCED_60FPS("Mobile 60 FPS", "Crisp Agris Pocket pack: 80% scale, half-rate world-locked shadows, 6-chunk shadow radius."),
    FLAGSHIP_120HZ("Flagship 120Hz", "Snapdragon 8 / Dimensity 9000 / Apple M profile: 85% dynamic scale, 120 FPS governor."),
    DESKTOP_FULL("Desktop Full", "Unrestricted desktop quality: 100% scale, every-frame shadows, full MRT precision.");

    private final String label;
    private final String summary;

    MobilePreset(final String label, final String summary) {
        this.label = label;
        this.summary = summary;
    }

    public String label() {
        return this.label;
    }

    public String summary() {
        return this.summary;
    }

    public MobilePreset next() {
        final MobilePreset[] vals = values();
        return vals[(this.ordinal() + 1) % vals.length];
    }

    /**
     * Applies this preset's coordinated settings to {@code config}.
     */
    public void applyTo(final AgrisConfig config, final MobileGpuArchitecture arch) {
        switch (this) {
            case BATTERY_SAVER -> {
                config.shaderPack.set(BuiltinShaderPacks.ID_MOBILE);
                config.activeProfile.set("Battery_Saver");
                config.enabled.set(true);
                config.shadowEnabled.set(false);
                config.renderScale.set(65);
                config.mobileMode.set(AgrisConfig.MobileMode.ON);
                config.mobileDynamicScale.set(true);
                config.mobileTargetFps.set(60);
                config.mobileMinScale.set(50);
                config.mobileShadowCadence.set(3);
                config.handDepth.set(false);
            }
            case BALANCED_60FPS -> {
                config.shaderPack.set(BuiltinShaderPacks.ID_MOBILE);
                config.activeProfile.set("Mobile_Balanced");
                config.enabled.set(true);
                config.shadowEnabled.set(true);
                config.shadowResolutionScale.set(75);
                config.shadowMaxDistance.set(6);
                config.shadowEntities.set(false);
                config.shadowBlockEntities.set(false);
                config.shadowTranslucent.set(false);
                config.renderScale.set(80);
                config.mobileMode.set(AgrisConfig.MobileMode.ON);
                config.mobileDynamicScale.set(true);
                config.mobileTargetFps.set(60);
                config.mobileMinScale.set(60);
                config.mobileShadowCadence.set(2);
                config.handDepth.set(false);
            }
            case FLAGSHIP_120HZ -> {
                config.shaderPack.set(BuiltinShaderPacks.ID_MOBILE);
                config.activeProfile.set("Flagship_120Hz");
                config.enabled.set(true);
                config.shadowEnabled.set(true);
                config.shadowResolutionScale.set(100);
                config.shadowMaxDistance.set(8);
                config.shadowEntities.set(true);
                config.shadowBlockEntities.set(false);
                config.shadowTranslucent.set(false);
                config.renderScale.set(85);
                config.mobileMode.set(AgrisConfig.MobileMode.ON);
                config.mobileDynamicScale.set(true);
                config.mobileTargetFps.set(120);
                config.mobileMinScale.set(65);
                config.mobileShadowCadence.set(2);
                config.handDepth.set(false);
            }
            case DESKTOP_FULL -> {
                config.mobileMode.set(AgrisConfig.MobileMode.OFF);
                config.mobileDynamicScale.set(false);
                config.mobileShadowCadence.set(1);
                config.renderScale.set(100);
                config.shadowResolutionScale.set(100);
                config.shadowMaxDistance.set(12);
                config.shadowEntities.set(true);
                config.shadowBlockEntities.set(true);
                config.shadowTranslucent.set(true);
                config.handDepth.set(true);
            }
            case AUTO -> {
                config.mobileMode.set(AgrisConfig.MobileMode.AUTO);
                if (arch != null && arch.isMobileOrTbdr()) {
                    config.shaderPack.set(BuiltinShaderPacks.ID_MOBILE);
                    config.renderScale.set(arch.silicon().recommendedRenderScalePercent());
                    config.shadowMaxDistance.set(arch.silicon().recommendedShadowChunks());
                    config.mobileShadowCadence.set(0);
                    config.mobileDynamicScale.set(true);
                }
            }
        }
    }
}
