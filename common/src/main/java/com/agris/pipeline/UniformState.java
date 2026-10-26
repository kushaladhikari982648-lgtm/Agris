package com.agris.pipeline;

import com.agris.pack.PackDirectives;
import com.agris.pack.UniformExpression;
import com.agris.util.MathUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * Frame-coherent state container for all 80+ canonical OptiFine / Iris uniforms,
 * previous-frame temporal matrices, shadow matrices, smoothed world variables,
 * and pack custom uniform expressions.
 */
public final class UniformState {
    // Viewport & timing
    public float viewWidth = 1920.0f;
    public float viewHeight = 1080.0f;
    public float aspectRatio = 1920.0f / 1080.0f;
    public int frameCounter;
    public float frameTime = 1.0f / 60.0f;
    public float frameTimeCounter;
    private long lastFrameNanos = -1L;

    // World & celestial
    public int worldTime;
    public int worldDay;
    public int moonPhase;
    public float skyAngle;
    public float sunAngle;
    public float shadowAngle;
    public float rainStrength;
    public float wetness;
    public float near = 0.05f;
    public float far = 192.0f;

    // Camera & player state
    public final float[] cameraPosition = new float[3];
    public final float[] previousCameraPosition = new float[3];
    public float eyeAltitude = 64.0f;
    public int eyeBrightnessBlock;
    public int eyeBrightnessSky = 240;
    public float eyeBrightnessSmoothBlock;
    public float eyeBrightnessSmoothSky = 240.0f;
    public float centerDepthSmooth = 0.5f;
    public int isEyeInWater;
    public float blindness;
    public float nightVision;
    public float darknessFactor;
    public float screenBrightness = 1.0f;
    public int hideGUI;

    // Held items & entity context
    public int heldItemId = -1;
    public int heldBlockLightValue;
    public int heldItemId2 = -1;
    public int heldBlockLightValue2;
    public int entityId = -1;
    public int blockEntityId = -1;
    public final float[] entityColor = {0.0f, 0.0f, 0.0f, 0.0f};

    // Fog & sky colors
    public int fogMode = 9729; // GL_LINEAR
    public int fogShape = 1;
    public float fogStart = 128.0f;
    public float fogEnd = 192.0f;
    public float fogDensity = 0.01f;
    public final float[] fogColor = {0.70f, 0.80f, 1.0f};
    public final float[] skyColor = {0.48f, 0.66f, 1.0f};

    // Celestial vectors in view space (length 100, matching OptiFine/Iris spec)
    public final float[] sunPosition = {0.0f, 100.0f, 0.0f};
    public final float[] moonPosition = {0.0f, -100.0f, 0.0f};
    public final float[] shadowLightPosition = {0.0f, 100.0f, 0.0f};
    public final float[] upPosition = {0.0f, 100.0f, 0.0f};

    // Global camera GBuffer & Shadow matrices (column-major float[16])
    public final float[] gbufferModelView = MathUtil.identity4(new float[16]);
    public final float[] gbufferModelViewInverse = MathUtil.identity4(new float[16]);
    public final float[] gbufferProjection = MathUtil.identity4(new float[16]);
    public final float[] gbufferProjectionInverse = MathUtil.identity4(new float[16]);
    public final float[] gbufferPreviousModelView = MathUtil.identity4(new float[16]);
    public final float[] gbufferPreviousProjection = MathUtil.identity4(new float[16]);

    public final float[] shadowModelView = MathUtil.identity4(new float[16]);
    public final float[] shadowModelViewInverse = MathUtil.identity4(new float[16]);
    public final float[] shadowProjection = MathUtil.identity4(new float[16]);
    public final float[] shadowProjectionInverse = MathUtil.identity4(new float[16]);

    // World-locked shadow anchor for mobile half-rate / third-rate shadow interleaving
    private final float[] latchedShadowModelView = MathUtil.identity4(new float[16]);
    private double latchedShadowCamX;
    private double latchedShadowCamY;
    private double latchedShadowCamZ;
    private boolean hasLatchedShadow;

    // Per-draw active matrices & state
    public final float[] drawModelView = MathUtil.identity4(new float[16]);
    public final float[] drawModelViewInverse = MathUtil.identity4(new float[16]);
    public final float[] drawProjection = MathUtil.identity4(new float[16]);
    public final float[] drawProjectionInverse = MathUtil.identity4(new float[16]);
    public final float[] drawNormalMatrix = MathUtil.identity3(new float[9]);
    public final float[] drawTextureMatrix = MathUtil.identity4(new float[16]);
    public final float[] lightmapTextureMatrix = buildLightmapMatrix();
    public final float[] colorModulator = {1.0f, 1.0f, 1.0f, 1.0f};
    public final float[] chunkOffset = {0.0f, 0.0f, 0.0f};
    public float alphaTestRef = 0.0f;

    // Evaluated custom variables & uniforms from shaders.properties
    private final Map<String, Float> evaluatedCustomValues = new HashMap<>();
    private boolean hasHistory;

    private static float[] buildLightmapMatrix() {
        // Vanilla encodes lightmap UVs as shorts in [0..240]; multiplying by 1/256 and adding 1/32
        // maps block/sky [0..240] cleanly onto [0.03125..0.96875] of vanilla's 16x16 lightmap texture!
        final float[] m = MathUtil.identity4(new float[16]);
        final float scale = 1.0f / 256.0f;
        final float bias = 1.0f / 32.0f;
        m[0] = scale;
        m[5] = scale;
        m[12] = bias;
        m[13] = bias;
        return m;
    }

    public void advanceFrameClock(final long nowNanos, final int width, final int height) {
        if (this.lastFrameNanos < 0L) {
            this.frameTime = 1.0f / 60.0f;
        } else {
            final float dt = (nowNanos - this.lastFrameNanos) * 1.0e-9f;
            this.frameTime = MathUtil.clamp(dt, 0.0001f, 0.5f);
        }
        this.lastFrameNanos = nowNanos;
        this.frameTimeCounter = (this.frameTimeCounter + this.frameTime) % 3600.0f;
        this.frameCounter = (this.frameCounter + 1) & 0x7FFF_FFFF;

        this.viewWidth = Math.max(1.0f, width);
        this.viewHeight = Math.max(1.0f, height);
        this.aspectRatio = this.viewWidth / this.viewHeight;
    }

    public void updateWorldMatricesAndState(final float[] currentMv,
                                            final float[] currentProj,
                                            final double camX, final double camY, final double camZ,
                                            final float currentSkyAngle,
                                            final PackDirectives directives,
                                            final float effectiveShadowDistance) {
        updateWorldMatricesAndState(
                currentMv, currentProj, camX, camY, camZ,
                currentSkyAngle, directives, effectiveShadowDistance, true);
    }

    public void updateWorldMatricesAndState(final float[] currentMv,
                                            final float[] currentProj,
                                            final double camX, final double camY, final double camZ,
                                            final float currentSkyAngle,
                                            final PackDirectives directives,
                                            final float effectiveShadowDistance,
                                            final boolean renderShadowThisFrame) {
        if (this.hasHistory) {
            System.arraycopy(this.gbufferModelView, 0, this.gbufferPreviousModelView, 0, 16);
            System.arraycopy(this.gbufferProjection, 0, this.gbufferPreviousProjection, 0, 16);
            System.arraycopy(this.cameraPosition, 0, this.previousCameraPosition, 0, 3);
        }

        System.arraycopy(currentMv, 0, this.gbufferModelView, 0, 16);
        // Strip any residual camera-bob translation into pure view orientation where expected, keeping exact inverse.
        MathUtil.invert4(this.gbufferModelView, this.gbufferModelViewInverse);
        System.arraycopy(currentProj, 0, this.gbufferProjection, 0, 16);
        MathUtil.invert4(this.gbufferProjection, this.gbufferProjectionInverse);

        this.cameraPosition[0] = (float) camX;
        this.cameraPosition[1] = (float) camY;
        this.cameraPosition[2] = (float) camZ;
        this.eyeAltitude = (float) camY;

        if (!this.hasHistory) {
            System.arraycopy(this.gbufferModelView, 0, this.gbufferPreviousModelView, 0, 16);
            System.arraycopy(this.gbufferProjection, 0, this.gbufferPreviousProjection, 0, 16);
            System.arraycopy(this.cameraPosition, 0, this.previousCameraPosition, 0, 3);
            this.hasHistory = true;
        }

        this.skyAngle = currentSkyAngle;
        this.sunAngle = MathUtil.computeSunAngle(currentSkyAngle);
        this.shadowAngle = MathUtil.computeShadowAngle(this.sunAngle);

        final float sunTilt = directives.sunPathRotation();
        MathUtil.computeCelestialPositions(
                this.gbufferModelView, currentSkyAngle, sunTilt,
                this.sunPosition, this.moonPosition, this.shadowLightPosition, this.upPosition);

        if (renderShadowThisFrame || !this.hasLatchedShadow) {
            MathUtil.createShadowModelView(
                    this.shadowAngle, sunTilt, directives.shadowIntervalSize(),
                    camX, camY, camZ, this.latchedShadowModelView);
            this.latchedShadowCamX = camX;
            this.latchedShadowCamY = camY;
            this.latchedShadowCamZ = camZ;
            this.hasLatchedShadow = true;

            System.arraycopy(this.latchedShadowModelView, 0, this.shadowModelView, 0, 16);
            MathUtil.invert4(this.shadowModelView, this.shadowModelViewInverse);

            MathUtil.createShadowOrthoMatrix(effectiveShadowDistance, this.shadowProjection);
            MathUtil.invert4(this.shadowProjection, this.shadowProjectionInverse);
        } else {
            // World-locked shadow reuse: M_sh(t) = M_sh(t0) * T(cam(t) - cam(t0)) so any camera-relative
            // fragment position p_cam(t) = W - cam(t) maps to the exact same shadow texel (u, v, z)
            // rendered on frame t0 with zero camera-motion shadow swimming.
            final float deltaX = (float) (camX - this.latchedShadowCamX);
            final float deltaY = (float) (camY - this.latchedShadowCamY);
            final float deltaZ = (float) (camZ - this.latchedShadowCamZ);
            System.arraycopy(this.latchedShadowModelView, 0, this.shadowModelView, 0, 16);
            MathUtil.translate4(this.shadowModelView, deltaX, deltaY, deltaZ);
            MathUtil.invert4(this.shadowModelView, this.shadowModelViewInverse);
        }

        // Update smoothed temporal uniforms
        final float wetHalfLife = this.rainStrength > this.wetness
                ? directives.wetnessHalfLife()
                : directives.drynessHalfLife();
        this.wetness = MathUtil.smoothHalfLife(this.wetness, this.rainStrength, this.frameTime, wetHalfLife);
        this.eyeBrightnessSmoothBlock = MathUtil.smoothHalfLife(
                this.eyeBrightnessSmoothBlock, this.eyeBrightnessBlock,
                this.frameTime, directives.eyeBrightnessHalfLife());
        this.eyeBrightnessSmoothSky = MathUtil.smoothHalfLife(
                this.eyeBrightnessSmoothSky, this.eyeBrightnessSky,
                this.frameTime, directives.eyeBrightnessHalfLife());

        evaluateCustomUniforms(directives);
    }

    public void setPerDrawMatrices(final float[] mv, final float[] proj, final float[] texMat) {
        System.arraycopy(mv, 0, this.drawModelView, 0, 16);
        MathUtil.invert4(this.drawModelView, this.drawModelViewInverse);
        MathUtil.normalMatrix3(this.drawModelView, this.drawNormalMatrix);

        System.arraycopy(proj, 0, this.drawProjection, 0, 16);
        MathUtil.invert4(this.drawProjection, this.drawProjectionInverse);

        if (texMat != null) {
            System.arraycopy(texMat, 0, this.drawTextureMatrix, 0, 16);
        } else {
            MathUtil.identity4(this.drawTextureMatrix);
        }
    }

    public void evaluateCustomUniforms(final PackDirectives directives) {
        if (directives.customVariables().isEmpty() && directives.customUniforms().isEmpty()) {
            return;
        }
        for (final Map.Entry<String, UniformExpression> e : directives.customVariables().entrySet()) {
            this.evaluatedCustomValues.put(e.getKey(), e.getValue().evaluateFloat(this::resolveSymbol));
        }
        for (final Map.Entry<String, UniformExpression> e : directives.customUniforms().entrySet()) {
            this.evaluatedCustomValues.put(e.getKey(), e.getValue().evaluateFloat(this::resolveSymbol));
        }
    }

    public float customUniformValue(final String name) {
        return this.evaluatedCustomValues.getOrDefault(name, 0.0f);
    }

    public double resolveSymbol(final String name) {
        final Float custom = this.evaluatedCustomValues.get(name);
        if (custom != null) {
            return custom;
        }
        return switch (name) {
            case "viewWidth" -> this.viewWidth;
            case "viewHeight" -> this.viewHeight;
            case "aspectRatio" -> this.aspectRatio;
            case "frameCounter" -> this.frameCounter;
            case "frameTime" -> this.frameTime;
            case "frameTimeCounter" -> this.frameTimeCounter;
            case "worldTime" -> this.worldTime;
            case "worldDay" -> this.worldDay;
            case "moonPhase" -> this.moonPhase;
            case "skyAngle" -> this.skyAngle;
            case "sunAngle" -> this.sunAngle;
            case "shadowAngle" -> this.shadowAngle;
            case "rainStrength" -> this.rainStrength;
            case "wetness" -> this.wetness;
            case "near" -> this.near;
            case "far" -> this.far;
            case "eyeAltitude" -> this.eyeAltitude;
            case "isEyeInWater" -> this.isEyeInWater;
            case "blindness" -> this.blindness;
            case "nightVision" -> this.nightVision;
            case "darknessFactor" -> this.darknessFactor;
            case "screenBrightness" -> this.screenBrightness;
            case "hideGUI" -> this.hideGUI;
            case "heldBlockLightValue" -> this.heldBlockLightValue;
            case "heldBlockLightValue2" -> this.heldBlockLightValue2;
            case "centerDepthSmooth" -> this.centerDepthSmooth;
            default -> 0.0;
        };
    }

    public void resetHistory() {
        this.hasHistory = false;
        this.hasLatchedShadow = false;
    }
}
