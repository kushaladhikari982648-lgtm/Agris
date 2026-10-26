package com.agris.mobile;

import com.agris.util.MathUtil;

/**
 * Zero-allocation frame-time governor for mobile devices.
 *
 * <p>Mobile SoCs throttle rapidly under sustained GPU memory bandwidth load. Rather than
 * letting FPS collapse or reallocating FBOs every frame (which itself causes stutters!),
 * {@link MobileFrameGovernor} maintains an outlier-clamped exponential moving average of
 * frame duration and adjusts:</p>
 * <ul>
 *   <li><b>Internal shader resolution</b> in quantized <b>5% steps</b> with a strict
 *       <b>1.5-second resize cooldown</b> so FBO reallocations happen smoothly and rarely.</li>
 *   <li><b>World-locked shadow update cadence</b> ({@code 1} = every frame, {@code 2} =
 *       half-rate, {@code 3} = third-rate) with immediate override whenever the sun/moon
 *       jumps ({@code /time set}) or the camera teleports.</li>
 * </ul>
 */
public final class MobileFrameGovernor {
    private static final long RESIZE_COOLDOWN_NANOS = 1_500_000_000L;
    private static final int SCALE_STEP_PERCENT = 5;
    private static final float TELEPORT_DIST_SQ = 32.0f * 32.0f;
    private static final float SKY_ANGLE_JUMP_THRESHOLD = 0.015f;

    private float emaFrameMs = 16.67f;
    private int currentScalePercent = 100;
    private long lastResizeNanos = -1L;

    // Shadow cadence state
    private int framesSinceShadowRender = 999;
    private float lastShadowSkyAngle = -1.0f;
    private double lastShadowCamX;
    private double lastShadowCamY;
    private double lastShadowCamZ;
    private boolean hasShadowAnchor;

    public void reset(final int baseScalePercent) {
        this.emaFrameMs = 16.67f;
        this.currentScalePercent = MathUtil.clamp(baseScalePercent, 50, 200);
        this.lastResizeNanos = -1L;
        this.framesSinceShadowRender = 999;
        this.hasShadowAnchor = false;
    }

    /**
     * Records a completed frame's delta and computes the current quantized render scale percentage.
     *
     * @param frameDeltaSeconds last frame duration in seconds
     * @param nowNanos          monotonic timestamp in nanoseconds
     * @param dynamicEnabled    whether dynamic resolution scaling is enabled
     * @param targetFps         target frame rate (30..144)
     * @param minScalePercent   floor percentage (50..100)
     * @param maxScalePercent   ceiling percentage configured by user (50..200)
     * @return effective render scale percentage to apply this frame
     */
    public int recordFrameAndComputeScale(final float frameDeltaSeconds,
                                          final long nowNanos,
                                          final boolean dynamicEnabled,
                                          final int targetFps,
                                          final int minScalePercent,
                                          final int maxScalePercent) {
        final float clampedMs = MathUtil.clamp(frameDeltaSeconds * 1000.0f, 1.0f, 120.0f);
        this.emaFrameMs = this.emaFrameMs + (clampedMs - this.emaFrameMs) * 0.12f;

        final int safeMax = MathUtil.clamp(maxScalePercent, 50, 200);
        final int safeMin = MathUtil.clamp(minScalePercent, 50, safeMax);
        if (!dynamicEnabled) {
            this.currentScalePercent = safeMax;
            return safeMax;
        }

        this.currentScalePercent = MathUtil.clamp(this.currentScalePercent, safeMin, safeMax);
        if (this.lastResizeNanos >= 0L && (nowNanos - this.lastResizeNanos) < RESIZE_COOLDOWN_NANOS) {
            return this.currentScalePercent;
        }

        final float budgetMs = 1000.0f / Math.max(20, Math.min(240, targetFps));
        if (this.emaFrameMs > budgetMs * 1.12f && this.currentScalePercent > safeMin) {
            // Step down faster (10% if severely over budget, 5% otherwise) to recover 60/120 FPS quickly.
            final int drop = this.emaFrameMs > budgetMs * 1.35f ? SCALE_STEP_PERCENT * 2 : SCALE_STEP_PERCENT;
            this.currentScalePercent = Math.max(safeMin, this.currentScalePercent - drop);
            this.lastResizeNanos = nowNanos;
        } else if (this.emaFrameMs < budgetMs * 0.78f && this.currentScalePercent < safeMax) {
            this.currentScalePercent = Math.min(safeMax, this.currentScalePercent + SCALE_STEP_PERCENT);
            this.lastResizeNanos = nowNanos;
        }
        return this.currentScalePercent;
    }

    /**
     * Decides whether the shadow map should be re-rendered on the current frame.
     *
     * @param configuredCadence {@code 0} = automatic based on frame budget, {@code 1..4} = fixed interval
     * @param mobileActive      whether mobile optimizations are active
     * @param targetFps         target FPS budget
     * @param skyAngle          current celestial angle [0..1]
     * @param camX              camera world X
     * @param camY              camera world Y
     * @param camZ              camera world Z
     * @return {@code true} if the shadow FBO should be rendered this frame; {@code false} to reuse the
     *         world-locked latched shadow map
     */
    public boolean shouldRenderShadowThisFrame(final int configuredCadence,
                                               final boolean mobileActive,
                                               final int targetFps,
                                               final float skyAngle,
                                               final double camX,
                                               final double camY,
                                               final double camZ) {
        final int effectiveInterval;
        if (configuredCadence > 0) {
            effectiveInterval = MathUtil.clamp(configuredCadence, 1, 4);
        } else if (!mobileActive) {
            effectiveInterval = 1;
        } else {
            final float budgetMs = 1000.0f / Math.max(20, Math.min(240, targetFps));
            if (this.emaFrameMs > budgetMs * 1.15f) {
                effectiveInterval = 3;
            } else if (this.emaFrameMs > budgetMs * 0.72f) {
                effectiveInterval = 2;
            } else {
                effectiveInterval = 1;
            }
        }

        this.framesSinceShadowRender++;
        final boolean firstFrame = !this.hasShadowAnchor;
        final boolean skyJumped = Math.abs(skyAngle - this.lastShadowSkyAngle) > SKY_ANGLE_JUMP_THRESHOLD;
        final double dx = camX - this.lastShadowCamX;
        final double dy = camY - this.lastShadowCamY;
        final double dz = camZ - this.lastShadowCamZ;
        final boolean teleported = (dx * dx + dy * dy + dz * dz) > TELEPORT_DIST_SQ;

        if (firstFrame || skyJumped || teleported || this.framesSinceShadowRender >= effectiveInterval) {
            this.framesSinceShadowRender = 0;
            this.lastShadowSkyAngle = skyAngle;
            this.lastShadowCamX = camX;
            this.lastShadowCamY = camY;
            this.lastShadowCamZ = camZ;
            this.hasShadowAnchor = true;
            return true;
        }
        return false;
    }

    public float smoothedFrameMs() {
        return this.emaFrameMs;
    }

    public int currentScalePercent() {
        return this.currentScalePercent;
    }
}
