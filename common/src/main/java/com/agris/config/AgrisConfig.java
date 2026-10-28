package com.agris.config;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Authoritative declaration of every Agris configuration option, including
 * TBDR / Android mobile GPU bandwidth, dynamic resolution, and shadow-interleaving settings.
 */
public final class AgrisConfig {
    public static final int CURRENT_VERSION = 2;

    public enum LightingMode {
        AUTO,
        ON,
        OFF
    }

    public enum MobileMode {
        AUTO,
        ON,
        OFF
    }

    // --------------------------------------------------------------- general
    public final ConfigValue<Boolean> enabled = ConfigValue.bool(
            "general.enabled", false,
            "Master shader pipeline switch. Live toggle (default shortcut: K).");
    public final ConfigValue<String> shaderPack = ConfigValue.string(
            "general.shader_pack", "(agris-mobile)",
            "Selected shader pack filename, directory name, or built-in preset id.");
    public final ConfigValue<String> activeProfile = ConfigValue.string(
            "general.profile", "",
            "Optional profile override for the active shader pack (empty = pack default or Custom).");
    public final ConfigValue<Boolean> notifyConflicts = ConfigValue.bool(
            "general.notify_conflicts", true,
            "Log a diagnostic report when another shader mod or incompatible renderer is installed.");

    // ---------------------------------------------------------------- shadow
    public final ConfigValue<Boolean> shadowEnabled = ConfigValue.bool(
            "shadow.enabled", true, true,
            "Render the real-time directional sun/moon shadow map when requested by the shader pack.");
    public final ConfigValue<Integer> shadowResolutionScale = ConfigValue.intRange(
            "shadow.resolution_scale", 100, 25, 200, true,
            "Percentage multiplier applied to the shader pack's shadowMapResolution.");
    public final ConfigValue<Integer> shadowMaxDistance = ConfigValue.intRange(
            "shadow.max_distance", 8, 2, 32, false,
            "Maximum shadow render distance in chunks (caps the pack's shadowDistance for high FPS).");
    public final ConfigValue<Boolean> shadowEntities = ConfigValue.bool(
            "shadow.entities", true, false,
            "Draw mobs, players, and dropped items into the shadow map.");
    public final ConfigValue<Boolean> shadowBlockEntities = ConfigValue.bool(
            "shadow.block_entities", false, false,
            "Draw chests, signs, banners, and other block entities into the shadow map.");
    public final ConfigValue<Boolean> shadowTranslucent = ConfigValue.bool(
            "shadow.translucent", false, true,
            "Render stained glass and water into shadowcolor0/shadowcolor1 for colored shadows.");

    // --------------------------------------------------------------- quality
    public final ConfigValue<Integer> renderScale = ConfigValue.intRange(
            "quality.render_scale", 85, 50, 200, true,
            "Internal GBuffer/composite resolution percentage (complements Aetherium's renderScale).");
    public final ConfigValue<Integer> maxDrawBuffers = ConfigValue.intRange(
            "quality.max_draw_buffers", 0, 0, 16, true,
            "Cap MRT draw buffers per pass (0 = hardware/Android translator automatic limit).");
    public final ConfigValue<Boolean> handDepth = ConfigValue.bool(
            "quality.hand_depth", false, false,
            "Capture separate pre-hand depth into depthtex2 so Depth-of-Field does not blur the held item.");
    public final ConfigValue<LightingMode> oldLighting = ConfigValue.enumerated(
            "quality.old_lighting", LightingMode.AUTO, LightingMode.class, true,
            "Classic directional block-face shading mode (AUTO honors the shader pack's oldLighting directive).");

    // ---------------------------------------------------------------- mobile
    public final ConfigValue<MobileMode> mobileMode = ConfigValue.enumerated(
            "mobile.mode", MobileMode.AUTO, MobileMode.class, true,
            "Mobile TBDR & Android optimization engine (AUTO enables automatically on Adreno/Mali/Apple/Android).");
    public final ConfigValue<Boolean> mobileDynamicScale = ConfigValue.bool(
            "mobile.dynamic_scale", true, false,
            "Smoothly step internal shader resolution in 5% increments to hold target FPS without FBO thrashing.");
    public final ConfigValue<Integer> mobileTargetFps = ConfigValue.intRange(
            "mobile.target_fps", 60, 30, 144, false,
            "Target frame rate budget used by the mobile resolution and shadow-cadence governor.");
    public final ConfigValue<Integer> mobileMinScale = ConfigValue.intRange(
            "mobile.min_scale", 60, 50, 100, false,
            "Minimum dynamic shader resolution percentage under heavy thermal or GPU load.");
    public final ConfigValue<Integer> mobileShadowCadence = ConfigValue.intRange(
            "mobile.shadow_cadence", 0, 0, 4, false,
            "World-locked shadow update interval (0 = Auto, 1 = Every frame, 2 = Half-rate, 3 = Third-rate).");
    public final ConfigValue<Boolean> mobileHalfBandwidthHdr = ConfigValue.bool(
            "mobile.half_bandwidth_hdr", true, true,
            "Compact 64-bit RGBA16F HDR buffers into 32-bit R11F_G11F_B10F on TBDR GPUs when alpha is unused.");
    public final ConfigValue<Boolean> mobileTileDiscard = ConfigValue.bool(
            "mobile.tile_discard", true, false,
            "Issue glInvalidateFramebuffer on transient depth tiles at frame end to prevent LPDDR writeback.");
    public final ConfigValue<Boolean> mobileStripUnusedBuffers = ConfigValue.bool(
            "mobile.strip_unused_buffers", true, true,
            "Skip allocating and blitting depthtex1, depthtex2, shadowtex1, and shadowcolor* when unreferenced.");

    // ------------------------------------------------------------- aetherium
    public final ConfigValue<Boolean> integrateAetheriumUi = ConfigValue.bool(
            "aetherium.integrate_ui", true,
            "Inject a native 'Shaders' tab directly into Aetherium's video settings screen when Aetherium is installed.");
    public final ConfigValue<Boolean> suspendCullInShadows = ConfigValue.bool(
            "aetherium.suspend_cull_in_shadows", true,
            "Temporarily suspend Aetherium's camera-facing SectionCull and FogCull during the shadow pass so casters behind you still cast shadows.");
    public final ConfigValue<Boolean> deferAetheriumColorFilter = ConfigValue.bool(
            "aetherium.defer_color_filter", true,
            "Bypass Aetherium's post-processing ColorFilter while an Agris shader pack owns tonemapping, restoring it automatically when shaders are disabled.");
    public final ConfigValue<Boolean> bridgeDynamicLights = ConfigValue.bool(
            "aetherium.bridge_dynamic_lights", true,
            "Feed held-item light levels smoothly into heldBlockLightValue / heldBlockLightValue2 uniforms alongside Aetherium's dynamic lights.");
    public final ConfigValue<Boolean> androidSafeGlsl = ConfigValue.bool(
            "aetherium.android_safe_glsl", true, true,
            "Automatically adapt GLSL dialect, precision, and float FBO formats on Android GL translators (GL4ES, Krypton, MobileGlues, Zink, LTW, ANGLE).");

    // ----------------------------------------------------------------- debug
    public final ConfigValue<Boolean> logShaders = ConfigValue.bool(
            "debug.log_shaders", false,
            "Dump transformed GLSL sources and detailed OpenGL shader info logs on compilation failure.");
    public final ConfigValue<Boolean> showDebugHud = ConfigValue.bool(
            "debug.show_hud_tag", true,
            "Append active Agris shader pack, shadow resolution, mobile governor, and Aetherium bridge state to the F3 overlay.");

    private final Map<String, ConfigValue<?>> entries;

    private AgrisConfig() {
        final Map<String, ConfigValue<?>> map = new LinkedHashMap<>();
        register(map, this.enabled);
        register(map, this.shaderPack);
        register(map, this.activeProfile);
        register(map, this.notifyConflicts);

        register(map, this.shadowEnabled);
        register(map, this.shadowResolutionScale);
        register(map, this.shadowMaxDistance);
        register(map, this.shadowEntities);
        register(map, this.shadowBlockEntities);
        register(map, this.shadowTranslucent);

        register(map, this.renderScale);
        register(map, this.maxDrawBuffers);
        register(map, this.handDepth);
        register(map, this.oldLighting);

        register(map, this.mobileMode);
        register(map, this.mobileDynamicScale);
        register(map, this.mobileTargetFps);
        register(map, this.mobileMinScale);
        register(map, this.mobileShadowCadence);
        register(map, this.mobileHalfBandwidthHdr);
        register(map, this.mobileTileDiscard);
        register(map, this.mobileStripUnusedBuffers);

        register(map, this.integrateAetheriumUi);
        register(map, this.suspendCullInShadows);
        register(map, this.deferAetheriumColorFilter);
        register(map, this.bridgeDynamicLights);
        register(map, this.androidSafeGlsl);

        register(map, this.logShaders);
        register(map, this.showDebugHud);
        this.entries = Collections.unmodifiableMap(map);
    }

    public static AgrisConfig createDefaults() {
        return new AgrisConfig();
    }

    public boolean isMobileOptimizationActive(final boolean detectedMobileGpu) {
        return switch (this.mobileMode.get()) {
            case ON -> true;
            case OFF -> false;
            case AUTO -> detectedMobileGpu;
        };
    }

    public Collection<ConfigValue<?>> allValues() {
        return this.entries.values();
    }

    public ConfigValue<?> get(final String key) {
        return this.entries.get(key);
    }

    public void resetAll() {
        for (final ConfigValue<?> value : this.entries.values()) {
            value.reset();
        }
    }

    private static void register(final Map<String, ConfigValue<?>> map, final ConfigValue<?> value) {
        if (map.put(value.key(), value) != null) {
            throw new IllegalStateException("Duplicate config key: " + value.key());
        }
    }
}
