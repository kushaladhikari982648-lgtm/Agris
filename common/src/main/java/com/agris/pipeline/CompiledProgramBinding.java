package com.agris.pipeline;

import com.agris.gl.GlStateSync;
import com.agris.gl.RenderTargetSet;
import com.agris.gl.ShaderCompiler;
import com.agris.glsl.ProgramDirectives;
import com.agris.pack.PackDirectives;
import org.lwjgl.opengl.GL20;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Pre-linked OpenGL program wrapper that caches every uniform and sampler location
 * at link time in primitive fields and small arrays — zero {@code glGetUniformLocation}
 * calls and zero heap allocations during rendering.
 */
public final class CompiledProgramBinding implements AutoCloseable {
    private enum SamplerSource {
        VANILLA_TEX0,
        VANILLA_OVERLAY,
        VANILLA_LIGHTMAP,
        NOISE_TEX,
        DEPTH_0,
        DEPTH_1,
        DEPTH_2,
        SHADOW_TEX_0,
        SHADOW_TEX_1,
        SHADOW_COLOR_0,
        SHADOW_COLOR_1,
        COLORTEX_0, COLORTEX_1, COLORTEX_2, COLORTEX_3,
        COLORTEX_4, COLORTEX_5, COLORTEX_6, COLORTEX_7,
        COLORTEX_8, COLORTEX_9, COLORTEX_10, COLORTEX_11,
        COLORTEX_12, COLORTEX_13, COLORTEX_14, COLORTEX_15
    }

    private record ActiveSampler(int uniformLocation, int textureUnit, SamplerSource source) {
    }

    private record CustomUniformSlot(int uniformLocation, String name) {
    }

    private static final SamplerSource[] COLORTEX_SOURCES = {
            SamplerSource.COLORTEX_0, SamplerSource.COLORTEX_1, SamplerSource.COLORTEX_2, SamplerSource.COLORTEX_3,
            SamplerSource.COLORTEX_4, SamplerSource.COLORTEX_5, SamplerSource.COLORTEX_6, SamplerSource.COLORTEX_7,
            SamplerSource.COLORTEX_8, SamplerSource.COLORTEX_9, SamplerSource.COLORTEX_10, SamplerSource.COLORTEX_11,
            SamplerSource.COLORTEX_12, SamplerSource.COLORTEX_13, SamplerSource.COLORTEX_14, SamplerSource.COLORTEX_15
    };

    private int programId;
    private final String name;
    private final ProgramDirectives directives;

    // Fast-path per-draw uniform locations (-1 when unused by this shader)
    private final int locAgrisMv;
    private final int locAgrisMvInv;
    private final int locAgrisProj;
    private final int locAgrisProjInv;
    private final int locAgrisNormMat;
    private final int locAgrisTexMat;
    private final int locAgrisLightmapMat;
    private final int locAgrisChunkOffset;
    private final int locAgrisColorModulator;

    private final int locIrisMv;
    private final int locIrisMvInv;
    private final int locIrisProj;
    private final int locIrisProjInv;
    private final int locIrisNormMat;
    private final int locIrisTexMat;
    private final int locIrisChunkOffset;
    private final int locIrisColorModulator;
    private final int locAlphaTestRef;

    // Global world/camera uniform locations
    private final int locViewWidth;
    private final int locViewHeight;
    private final int locAspectRatio;
    private final int locFrameCounter;
    private final int locFrameTime;
    private final int locFrameTimeCounter;
    private final int locWorldTime;
    private final int locWorldDay;
    private final int locMoonPhase;
    private final int locSkyAngle;
    private final int locSunAngle;
    private final int locShadowAngle;
    private final int locRainStrength;
    private final int locWetness;
    private final int locNear;
    private final int locFar;
    private final int locEyeAltitude;
    private final int locEyeBrightness;
    private final int locEyeBrightnessSmooth;
    private final int locCenterDepthSmooth;
    private final int locIsEyeInWater;
    private final int locBlindness;
    private final int locNightVision;
    private final int locDarknessFactor;
    private final int locScreenBrightness;
    private final int locHideGui;

    private final int locHeldItemId;
    private final int locHeldBlockLightValue;
    private final int locHeldItemId2;
    private final int locHeldBlockLightValue2;
    private final int locEntityId;
    private final int locBlockEntityId;
    private final int locEntityColor;

    private final int locFogMode;
    private final int locFogShape;
    private final int locFogStart;
    private final int locFogEnd;
    private final int locFogDensity;
    private final int locFogColor;
    private final int locAgrisFogColor;
    private final int locAgrisFogDensity;
    private final int locAgrisFogStart;
    private final int locAgrisFogEnd;
    private final int locAgrisFogScale;
    private final int locSkyColor;

    private final int locCameraPosition;
    private final int locPrevCameraPosition;
    private final int locSunPosition;
    private final int locMoonPosition;
    private final int locShadowLightPosition;
    private final int locUpPosition;

    private final int locGbufferMv;
    private final int locGbufferMvInv;
    private final int locGbufferProj;
    private final int locGbufferProjInv;
    private final int locGbufferPrevMv;
    private final int locGbufferPrevProj;
    private final int locShadowMv;
    private final int locShadowMvInv;
    private final int locShadowProj;
    private final int locShadowProjInv;

    private final ActiveSampler[] samplers;
    private final CustomUniformSlot[] customUniforms;
    private final float[] offsetAdjustedMv = new float[16];

    public CompiledProgramBinding(final int programId,
                                  final String name,
                                  final ProgramDirectives directives,
                                  final PackDirectives packDirectives,
                                  final int maxTextureUnits) {
        this.programId = programId;
        this.name = name;
        this.directives = directives;

        this.locAgrisMv = loc("agris_ModelViewMatrix");
        this.locAgrisMvInv = loc("agris_ModelViewMatrixInverse");
        this.locAgrisProj = loc("agris_ProjectionMatrix");
        this.locAgrisProjInv = loc("agris_ProjectionMatrixInverse");
        this.locAgrisNormMat = loc("agris_NormalMatrix");
        this.locAgrisTexMat = loc("agris_TextureMatrix");
        this.locAgrisLightmapMat = loc("agris_LightmapTextureMatrix");
        this.locAgrisChunkOffset = loc("agris_ChunkOffset");
        this.locAgrisColorModulator = loc("agris_ColorModulator");

        this.locIrisMv = firstLoc("modelViewMatrix", "ModelViewMat");
        this.locIrisMvInv = firstLoc("modelViewMatrixInverse", "ModelViewMatInverse");
        this.locIrisProj = firstLoc("projectionMatrix", "ProjMat");
        this.locIrisProjInv = firstLoc("projectionMatrixInverse", "ProjMatInverse");
        this.locIrisNormMat = firstLoc("normalMatrix", "NormalMat");
        this.locIrisTexMat = firstLoc("textureMatrix", "TextureMat");
        this.locIrisChunkOffset = firstLoc("chunkOffset", "ChunkOffset");
        this.locIrisColorModulator = firstLoc("colorModulator", "ColorModulator");
        this.locAlphaTestRef = loc("alphaTestRef");

        this.locViewWidth = loc("viewWidth");
        this.locViewHeight = loc("viewHeight");
        this.locAspectRatio = loc("aspectRatio");
        this.locFrameCounter = loc("frameCounter");
        this.locFrameTime = loc("frameTime");
        this.locFrameTimeCounter = loc("frameTimeCounter");
        this.locWorldTime = loc("worldTime");
        this.locWorldDay = loc("worldDay");
        this.locMoonPhase = loc("moonPhase");
        this.locSkyAngle = loc("skyAngle");
        this.locSunAngle = loc("sunAngle");
        this.locShadowAngle = loc("shadowAngle");
        this.locRainStrength = loc("rainStrength");
        this.locWetness = loc("wetness");
        this.locNear = loc("near");
        this.locFar = loc("far");
        this.locEyeAltitude = loc("eyeAltitude");
        this.locEyeBrightness = loc("eyeBrightness");
        this.locEyeBrightnessSmooth = loc("eyeBrightnessSmooth");
        this.locCenterDepthSmooth = loc("centerDepthSmooth");
        this.locIsEyeInWater = loc("isEyeInWater");
        this.locBlindness = loc("blindness");
        this.locNightVision = loc("nightVision");
        this.locDarknessFactor = loc("darknessFactor");
        this.locScreenBrightness = loc("screenBrightness");
        this.locHideGui = loc("hideGUI");

        this.locHeldItemId = loc("heldItemId");
        this.locHeldBlockLightValue = loc("heldBlockLightValue");
        this.locHeldItemId2 = loc("heldItemId2");
        this.locHeldBlockLightValue2 = loc("heldBlockLightValue2");
        this.locEntityId = loc("entityId");
        this.locBlockEntityId = loc("blockEntityId");
        this.locEntityColor = loc("entityColor");

        this.locFogMode = loc("fogMode");
        this.locFogShape = loc("fogShape");
        this.locFogStart = loc("fogStart");
        this.locFogEnd = loc("fogEnd");
        this.locFogDensity = loc("fogDensity");
        this.locFogColor = loc("fogColor");
        this.locAgrisFogColor = loc("agris_Fog.color");
        this.locAgrisFogDensity = loc("agris_Fog.density");
        this.locAgrisFogStart = loc("agris_Fog.start");
        this.locAgrisFogEnd = loc("agris_Fog.end");
        this.locAgrisFogScale = loc("agris_Fog.scale");
        this.locSkyColor = loc("skyColor");

        this.locCameraPosition = loc("cameraPosition");
        this.locPrevCameraPosition = loc("previousCameraPosition");
        this.locSunPosition = loc("sunPosition");
        this.locMoonPosition = loc("moonPosition");
        this.locShadowLightPosition = loc("shadowLightPosition");
        this.locUpPosition = loc("upPosition");

        this.locGbufferMv = loc("gbufferModelView");
        this.locGbufferMvInv = loc("gbufferModelViewInverse");
        this.locGbufferProj = loc("gbufferProjection");
        this.locGbufferProjInv = loc("gbufferProjectionInverse");
        this.locGbufferPrevMv = loc("gbufferPreviousModelView");
        this.locGbufferPrevProj = loc("gbufferPreviousProjection");
        this.locShadowMv = loc("shadowModelView");
        this.locShadowMvInv = loc("shadowModelViewInverse");
        this.locShadowProj = loc("shadowProjection");
        this.locShadowProjInv = loc("shadowProjectionInverse");

        this.samplers = discoverSamplers(maxTextureUnits);
        this.customUniforms = discoverCustomUniforms(packDirectives);
    }

    public int programId() {
        return this.programId;
    }

    public String name() {
        return this.name;
    }

    public ProgramDirectives directives() {
        return this.directives;
    }

    /** Binds program, uploads all active uniforms, and binds active texture samplers. */
    public void bindAndUpload(final UniformState state,
                              final RenderTargetSet targets,
                              final int noiseTexId,
                              final int vanillaTex0,
                              final int vanillaOverlay1,
                              final int vanillaLightmap2,
                              final float effectiveAlphaRef) {
        final GlStateSync sync = GlStateSync.current();
        sync.useProgram(this.programId);

        // Set safe generic vertex attributes for extended Iris slots when a VAO leaves them disabled.
        GL20.glVertexAttrib4f(ShaderCompiler.ATTR_MC_ENTITY, state.entityId >= 0 ? state.entityId : -1.0f, 0.0f, 0.0f, 1.0f);
        GL20.glVertexAttrib4f(ShaderCompiler.ATTR_MC_MID_TEX_COORD, 0.5f, 0.5f, 0.0f, 1.0f);
        GL20.glVertexAttrib4f(ShaderCompiler.ATTR_AT_TANGENT, 1.0f, 0.0f, 0.0f, 1.0f);

        uploadDrawUniforms(state, effectiveAlphaRef);
        uploadWorldUniforms(state);
        bindSamplers(targets, noiseTexId, vanillaTex0, vanillaOverlay1, vanillaLightmap2);
    }

    /**
     * Ultra-fast per-section {@code CHUNK_OFFSET} update called inside
     * {@code LevelRenderer.renderSectionLayer}'s tight section loop.
     */
    public void uploadChunkOffsetFast(final UniformState state, final float dx, final float dy, final float dz) {
        if (this.locAgrisChunkOffset != -1) {
            GL20.glUniform3f(this.locAgrisChunkOffset, dx, dy, dz);
        }
        if (this.locIrisChunkOffset != -1) {
            GL20.glUniform3f(this.locIrisChunkOffset, dx, dy, dz);
        } else if (this.locAgrisChunkOffset == -1 && this.locIrisMv != -1) {
            // Core shader using modelViewMatrix * vaPosition without explicit chunkOffset uniform:
            // fold section translation directly into modelViewMatrix!
            System.arraycopy(state.drawModelView, 0, this.offsetAdjustedMv, 0, 16);
            com.agris.util.MathUtil.translate4(this.offsetAdjustedMv, dx, dy, dz);
            GL20.glUniformMatrix4fv(this.locIrisMv, false, this.offsetAdjustedMv);
        }
    }

    private void uploadDrawUniforms(final UniformState s, final float alphaRef) {
        setMat4(this.locAgrisMv, s.drawModelView);
        setMat4(this.locAgrisMvInv, s.drawModelViewInverse);
        setMat4(this.locAgrisProj, s.drawProjection);
        setMat4(this.locAgrisProjInv, s.drawProjectionInverse);
        setMat3(this.locAgrisNormMat, s.drawNormalMatrix);
        setMat4(this.locAgrisTexMat, s.drawTextureMatrix);
        setMat4(this.locAgrisLightmapMat, s.lightmapTextureMatrix);
        setVec3(this.locAgrisChunkOffset, s.chunkOffset[0], s.chunkOffset[1], s.chunkOffset[2]);
        setVec4(this.locAgrisColorModulator, s.colorModulator);

        setMat4(this.locIrisMv, s.drawModelView);
        setMat4(this.locIrisMvInv, s.drawModelViewInverse);
        setMat4(this.locIrisProj, s.drawProjection);
        setMat4(this.locIrisProjInv, s.drawProjectionInverse);
        setMat3(this.locIrisNormMat, s.drawNormalMatrix);
        setMat4(this.locIrisTexMat, s.drawTextureMatrix);
        setVec3(this.locIrisChunkOffset, s.chunkOffset[0], s.chunkOffset[1], s.chunkOffset[2]);
        setVec4(this.locIrisColorModulator, s.colorModulator);
        setFloat(this.locAlphaTestRef, alphaRef);
    }

    private void uploadWorldUniforms(final UniformState s) {
        setFloat(this.locViewWidth, s.viewWidth);
        setFloat(this.locViewHeight, s.viewHeight);
        setFloat(this.locAspectRatio, s.aspectRatio);
        setInt(this.locFrameCounter, s.frameCounter);
        setFloat(this.locFrameTime, s.frameTime);
        setFloat(this.locFrameTimeCounter, s.frameTimeCounter);
        setInt(this.locWorldTime, s.worldTime);
        setInt(this.locWorldDay, s.worldDay);
        setInt(this.locMoonPhase, s.moonPhase);
        setFloat(this.locSkyAngle, s.skyAngle);
        setFloat(this.locSunAngle, s.sunAngle);
        setFloat(this.locShadowAngle, s.shadowAngle);
        setFloat(this.locRainStrength, s.rainStrength);
        setFloat(this.locWetness, s.wetness);
        setFloat(this.locNear, s.near);
        setFloat(this.locFar, s.far);
        setFloat(this.locEyeAltitude, s.eyeAltitude);
        if (this.locEyeBrightness != -1) {
            GL20.glUniform2i(this.locEyeBrightness, s.eyeBrightnessBlock, s.eyeBrightnessSky);
        }
        if (this.locEyeBrightnessSmooth != -1) {
            GL20.glUniform2i(this.locEyeBrightnessSmooth,
                    Math.round(s.eyeBrightnessSmoothBlock), Math.round(s.eyeBrightnessSmoothSky));
        }
        setFloat(this.locCenterDepthSmooth, s.centerDepthSmooth);
        setInt(this.locIsEyeInWater, s.isEyeInWater);
        setFloat(this.locBlindness, s.blindness);
        setFloat(this.locNightVision, s.nightVision);
        setFloat(this.locDarknessFactor, s.darknessFactor);
        setFloat(this.locScreenBrightness, s.screenBrightness);
        setInt(this.locHideGui, s.hideGUI);

        setInt(this.locHeldItemId, s.heldItemId);
        setInt(this.locHeldBlockLightValue, s.heldBlockLightValue);
        setInt(this.locHeldItemId2, s.heldItemId2);
        setInt(this.locHeldBlockLightValue2, s.heldBlockLightValue2);
        setInt(this.locEntityId, s.entityId);
        setInt(this.locBlockEntityId, s.blockEntityId);
        setVec4(this.locEntityColor, s.entityColor);

        setInt(this.locFogMode, s.fogMode);
        setInt(this.locFogShape, s.fogShape);
        setFloat(this.locFogStart, s.fogStart);
        setFloat(this.locFogEnd, s.fogEnd);
        setFloat(this.locFogDensity, s.fogDensity);
        setVec3(this.locFogColor, s.fogColor[0], s.fogColor[1], s.fogColor[2]);
        if (this.locAgrisFogColor != -1) {
            GL20.glUniform4f(this.locAgrisFogColor, s.fogColor[0], s.fogColor[1], s.fogColor[2], 1.0f);
        }
        setFloat(this.locAgrisFogDensity, s.fogDensity);
        setFloat(this.locAgrisFogStart, s.fogStart);
        setFloat(this.locAgrisFogEnd, s.fogEnd);
        setFloat(this.locAgrisFogScale, 1.0f / Math.max(0.001f, s.fogEnd - s.fogStart));
        setVec3(this.locSkyColor, s.skyColor[0], s.skyColor[1], s.skyColor[2]);

        setVec3(this.locCameraPosition, s.cameraPosition[0], s.cameraPosition[1], s.cameraPosition[2]);
        setVec3(this.locPrevCameraPosition, s.previousCameraPosition[0], s.previousCameraPosition[1], s.previousCameraPosition[2]);
        setVec3(this.locSunPosition, s.sunPosition[0], s.sunPosition[1], s.sunPosition[2]);
        setVec3(this.locMoonPosition, s.moonPosition[0], s.moonPosition[1], s.moonPosition[2]);
        setVec3(this.locShadowLightPosition, s.shadowLightPosition[0], s.shadowLightPosition[1], s.shadowLightPosition[2]);
        setVec3(this.locUpPosition, s.upPosition[0], s.upPosition[1], s.upPosition[2]);

        setMat4(this.locGbufferMv, s.gbufferModelView);
        setMat4(this.locGbufferMvInv, s.gbufferModelViewInverse);
        setMat4(this.locGbufferProj, s.gbufferProjection);
        setMat4(this.locGbufferProjInv, s.gbufferProjectionInverse);
        setMat4(this.locGbufferPrevMv, s.gbufferPreviousModelView);
        setMat4(this.locGbufferPrevProj, s.gbufferPreviousProjection);
        setMat4(this.locShadowMv, s.shadowModelView);
        setMat4(this.locShadowMvInv, s.shadowModelViewInverse);
        setMat4(this.locShadowProj, s.shadowProjection);
        setMat4(this.locShadowProjInv, s.shadowProjectionInverse);

        for (final CustomUniformSlot slot : this.customUniforms) {
            GL20.glUniform1f(slot.uniformLocation(), s.customUniformValue(slot.name()));
        }
    }

    private void bindSamplers(final RenderTargetSet targets,
                              final int noiseTexId,
                              final int vanillaTex0,
                              final int vanillaOverlay1,
                              final int vanillaLightmap2) {
        if (this.samplers.length == 0) {
            return;
        }
        final GlStateSync sync = GlStateSync.current();
        for (final ActiveSampler s : this.samplers) {
            final int texId = switch (s.source()) {
                case VANILLA_TEX0 -> vanillaTex0;
                case VANILLA_OVERLAY -> vanillaOverlay1;
                case VANILLA_LIGHTMAP -> vanillaLightmap2;
                case NOISE_TEX -> noiseTexId;
                case DEPTH_0 -> targets.depthtex0();
                case DEPTH_1 -> targets.depthtex1();
                case DEPTH_2 -> targets.depthtex2();
                case SHADOW_TEX_0 -> targets.shadowtex0();
                case SHADOW_TEX_1 -> targets.shadowtex1();
                case SHADOW_COLOR_0 -> targets.shadowcolor0();
                case SHADOW_COLOR_1 -> targets.shadowcolor1();
                default -> targets.readTexture(s.source().ordinal() - SamplerSource.COLORTEX_0.ordinal());
            };
            if (texId != 0) {
                sync.bindTexture2D(s.textureUnit(), texId);
            }
            GL20.glUniform1i(s.uniformLocation(), s.textureUnit());
        }
        sync.activeTextureUnit0();
    }

    private ActiveSampler[] discoverSamplers(final int maxUnits) {
        final List<ActiveSampler> list = new ArrayList<>();
        // Units 0..2 are reserved for vanilla block/entity, overlay, and lightmap textures!
        addFixedUnitSampler(list, 0, SamplerSource.VANILLA_TEX0, "gtexture", "tex", "texture", "Sampler0");
        addFixedUnitSampler(list, 1, SamplerSource.VANILLA_OVERLAY, "overlay", "Sampler1");
        addFixedUnitSampler(list, 2, SamplerSource.VANILLA_LIGHTMAP, "lightmap", "Sampler2");

        int nextUnit = 3;
        nextUnit = addDynamicSampler(list, nextUnit, maxUnits, SamplerSource.NOISE_TEX, "noisetex");
        nextUnit = addDynamicSampler(list, nextUnit, maxUnits, SamplerSource.DEPTH_0, "depthtex0", "gdepthtex");
        nextUnit = addDynamicSampler(list, nextUnit, maxUnits, SamplerSource.DEPTH_1, "depthtex1");
        nextUnit = addDynamicSampler(list, nextUnit, maxUnits, SamplerSource.DEPTH_2, "depthtex2");
        nextUnit = addDynamicSampler(list, nextUnit, maxUnits, SamplerSource.SHADOW_TEX_0, "shadowtex0", "shadow", "watershadow");
        nextUnit = addDynamicSampler(list, nextUnit, maxUnits, SamplerSource.SHADOW_TEX_1, "shadowtex1");
        nextUnit = addDynamicSampler(list, nextUnit, maxUnits, SamplerSource.SHADOW_COLOR_0, "shadowcolor0", "shadowcolor");
        nextUnit = addDynamicSampler(list, nextUnit, maxUnits, SamplerSource.SHADOW_COLOR_1, "shadowcolor1");

        final String[] legacyNames = {"gcolor", "gdepth", "gnormal", "composite", "gaux1", "gaux2", "gaux3", "gaux4"};
        for (int i = 0; i < 16; i++) {
            if (i < legacyNames.length) {
                nextUnit = addDynamicSampler(list, nextUnit, maxUnits, COLORTEX_SOURCES[i],
                        "colortex" + i, legacyNames[i]);
            } else {
                nextUnit = addDynamicSampler(list, nextUnit, maxUnits, COLORTEX_SOURCES[i], "colortex" + i);
            }
        }
        return list.toArray(new ActiveSampler[0]);
    }

    private void addFixedUnitSampler(final List<ActiveSampler> out, final int unit,
                                     final SamplerSource src, final String... aliases) {
        for (final String alias : aliases) {
            final int u = loc(alias);
            if (u != -1) {
                out.add(new ActiveSampler(u, unit, src));
            }
        }
    }

    private int addDynamicSampler(final List<ActiveSampler> out, final int candidateUnit,
                                  final int maxUnits, final SamplerSource src, final String... aliases) {
        if (candidateUnit >= maxUnits) {
            return candidateUnit;
        }
        boolean matched = false;
        for (final String alias : aliases) {
            final int u = loc(alias);
            if (u != -1) {
                out.add(new ActiveSampler(u, candidateUnit, src));
                matched = true;
            }
        }
        return matched ? candidateUnit + 1 : candidateUnit;
    }

    private CustomUniformSlot[] discoverCustomUniforms(final PackDirectives packDirectives) {
        if (packDirectives.customUniforms().isEmpty()) {
            return new CustomUniformSlot[0];
        }
        final List<CustomUniformSlot> slots = new ArrayList<>();
        for (final Map.Entry<String, ?> entry : packDirectives.customUniforms().entrySet()) {
            final int u = loc(entry.getKey());
            if (u != -1) {
                slots.add(new CustomUniformSlot(u, entry.getKey()));
            }
        }
        return slots.toArray(new CustomUniformSlot[0]);
    }

    private int loc(final String uniformName) {
        return GL20.glGetUniformLocation(this.programId, uniformName);
    }

    private int firstLoc(final String primary, final String secondary) {
        final int a = loc(primary);
        return a != -1 ? a : loc(secondary);
    }

    private static void setInt(final int loc, final int v) {
        if (loc != -1) {
            GL20.glUniform1i(loc, v);
        }
    }

    private static void setFloat(final int loc, final float v) {
        if (loc != -1) {
            GL20.glUniform1f(loc, v);
        }
    }

    private static void setVec3(final int loc, final float x, final float y, final float z) {
        if (loc != -1) {
            GL20.glUniform3f(loc, x, y, z);
        }
    }

    private static void setVec4(final int loc, final float[] rgba) {
        if (loc != -1) {
            GL20.glUniform4f(loc, rgba[0], rgba[1], rgba[2], rgba[3]);
        }
    }

    private static void setMat3(final int loc, final float[] m9) {
        if (loc != -1) {
            GL20.glUniformMatrix3fv(loc, false, m9);
        }
    }

    private static void setMat4(final int loc, final float[] m16) {
        if (loc != -1) {
            GL20.glUniformMatrix4fv(loc, false, m16);
        }
    }

    @Override
    public void close() {
        if (this.programId != 0) {
            GL20.glDeleteProgram(this.programId);
            this.programId = 0;
        }
    }
}
