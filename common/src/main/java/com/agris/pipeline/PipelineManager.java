package com.agris.pipeline;

import com.agris.compat.AetheriumBridge;
import com.agris.config.AgrisConfig;
import com.agris.gl.GlLimits;
import com.agris.mobile.MobileFrameGovernor;
import com.agris.pack.ShaderPack;
import com.agris.pack.ShaderPackDiscovery;
import com.agris.util.AgrisLog;

import java.util.Objects;

/**
 * Manages loading, hot-reloading, dimension switching, and crash-safe fallback
 * of the active {@link ShaderPipeline}.
 *
 * <p>If a shader pack fails to parse, compile, or link on the active GPU driver,
 * {@link PipelineManager} captures the diagnostic message for the Agris GUI banner,
 * disposes any partial GL objects, and falls back immediately to vanilla/Aetherium
 * rendering — never leaving a black screen or crashing the client.</p>
 */
public final class PipelineManager {
    private final AgrisConfig config;
    private final ShaderPackDiscovery discovery;
    private final AetheriumBridge aetheriumBridge;
    private final UniformState uniformState = new UniformState();
    private final MobileFrameGovernor mobileGovernor = new MobileFrameGovernor();

    private GlLimits glLimits;
    private ShaderPack loadedPack;
    private ShaderPipeline activePipeline;
    private String activeDimension = "minecraft:overworld";
    private String lastErrorMessage = "";
    private boolean reloadRequested = true;
    private boolean pipelineBlockedByConflict;

    public PipelineManager(final AgrisConfig config,
                           final ShaderPackDiscovery discovery,
                           final AetheriumBridge aetheriumBridge,
                           final boolean pipelineBlockedByConflict) {
        this.config = Objects.requireNonNull(config, "config");
        this.discovery = Objects.requireNonNull(discovery, "discovery");
        this.aetheriumBridge = Objects.requireNonNull(aetheriumBridge, "aetheriumBridge");
        this.pipelineBlockedByConflict = pipelineBlockedByConflict;
    }

    public void onGlContextInitialized() {
        this.glLimits = GlLimits.queryLive(
                this.aetheriumBridge.queryAndroidRendererId(),
                this.aetheriumBridge.queryIsAndroidHost());
        AgrisLog.info("OpenGL context ready: {} ({}) | Profile: {} | Architecture: {}",
                this.glLimits.renderer(), this.glLimits.version(),
                this.glLimits.androidProfile().describe(),
                this.glLimits.mobileArchitecture().summary());
        this.reloadRequested = true;
    }

    public void requestReload() {
        this.reloadRequested = true;
    }

    public void setActiveDimension(final String dimensionId) {
        final String normalized = (dimensionId == null || dimensionId.isBlank())
                ? "minecraft:overworld"
                : dimensionId;
        if (!normalized.equals(this.activeDimension)) {
            this.activeDimension = normalized;
            this.uniformState.resetHistory();
            if (this.activePipeline != null) {
                this.reloadRequested = true;
            }
        }
    }

    /** Called on the render thread at the start of {@code LevelRenderer.renderLevel}. */
    public ShaderPipeline prepareForFrame(final int viewportWidth,
                                          final int viewportHeight,
                                          final String activeLocale) {
        if (this.pipelineBlockedByConflict || !this.config.enabled.get()) {
            if (this.activePipeline != null) {
                destroyPipeline();
            }
            this.aetheriumBridge.syncShaderPackActive(false, this.config);
            return null;
        }
        if (this.glLimits == null) {
            onGlContextInitialized();
        }
        if (this.reloadRequested) {
            this.reloadRequested = false;
            rebuildPipeline(viewportWidth, viewportHeight, activeLocale);
        }
        return this.activePipeline;
    }

    public void rebuildPipeline(final int viewportWidth,
                                final int viewportHeight,
                                final String activeLocale) {
        destroyPipeline();
        this.lastErrorMessage = "";

        if (this.pipelineBlockedByConflict || !this.config.enabled.get()) {
            this.aetheriumBridge.syncShaderPackActive(false, this.config);
            return;
        }
        final String packId = this.config.shaderPack.get();
        if (packId == null || packId.isBlank() || "off".equalsIgnoreCase(packId)) {
            this.aetheriumBridge.syncShaderPackActive(false, this.config);
            return;
        }

        try {
            if (this.glLimits == null) {
                onGlContextInitialized();
            }
            this.loadedPack = this.discovery.loadPack(packId, activeLocale);
            if (this.loadedPack == null) {
                this.aetheriumBridge.syncShaderPackActive(false, this.config);
                return;
            }
            this.activePipeline = new ShaderPipeline(
                    this.loadedPack,
                    this.activeDimension,
                    Math.max(1, viewportWidth),
                    Math.max(1, viewportHeight),
                    this.glLimits,
                    this.config,
                    this.aetheriumBridge,
                    this.uniformState,
                    this.mobileGovernor);
            this.aetheriumBridge.syncShaderPackActive(true, this.config);
            AgrisLog.info("Activated shader pack '{}' for dimension '{}' (shadow={}, mobileTbdr={}).",
                    this.loadedPack.displayName(), this.activeDimension,
                    this.activePipeline.isShadowPassAvailable()
                            ? this.activePipeline.shadowResolution() + "px"
                            : "off",
                    this.activePipeline.isMobileOptimizationActive() ? "ON" : "OFF");
        } catch (final Throwable ex) {
            this.lastErrorMessage = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            AgrisLog.error("Failed to build shader pipeline for '{}' — falling back cleanly to vanilla/Aetherium: {}",
                    packId, this.lastErrorMessage);
            destroyPipeline();
            this.aetheriumBridge.syncShaderPackActive(false, this.config);
        }
    }

    public void handleRuntimeRenderFailure(final Throwable ex) {
        this.lastErrorMessage = "Runtime error: " + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        AgrisLog.error("Disabling active shader pipeline after runtime fault to protect frame rendering", ex);
        destroyPipeline();
        this.config.enabled.set(false);
        this.aetheriumBridge.syncShaderPackActive(false, this.config);
    }

    public void destroyPipeline() {
        if (this.activePipeline != null) {
            try {
                this.activePipeline.close();
            } catch (final Throwable ignored) {
                // Best-effort GL cleanup.
            }
            this.activePipeline = null;
        }
    }

    public ShaderPipeline activePipelineOrNull() {
        return this.activePipeline;
    }

    public ShaderPack loadedPackOrNull() {
        return this.loadedPack;
    }

    public UniformState uniformState() {
        return this.uniformState;
    }

    public MobileFrameGovernor mobileGovernor() {
        return this.mobileGovernor;
    }

    public GlLimits glLimitsOrNull() {
        return this.glLimits;
    }

    public GlLimits glLimits() {
        if (this.glLimits != null) {
            return this.glLimits;
        }
        return new GlLimits("Unknown", "Uninitialized", "3.3", "3.30", 8, 8, 16, null, null);
    }

    public String lastErrorMessage() {
        return this.lastErrorMessage;
    }

    public boolean isShaderPackInUse() {
        return !this.pipelineBlockedByConflict
                && this.config.enabled.get()
                && !this.config.shaderPack.get().isBlank()
                && this.lastErrorMessage.isEmpty();
    }

    public boolean isRenderingShadowPass() {
        final ShaderPipeline p = this.activePipeline;
        return p != null && p.isRenderingShadowPass();
    }

    public float currentSunPathRotation() {
        final ShaderPack p = this.loadedPack;
        return p != null ? p.directives().sunPathRotation() : 0.0f;
    }
}
