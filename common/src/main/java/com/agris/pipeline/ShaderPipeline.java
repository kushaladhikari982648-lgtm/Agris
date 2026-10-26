package com.agris.pipeline;

import com.agris.compat.AetheriumBridge;
import com.agris.config.AgrisConfig;
import com.agris.gl.FullscreenQuad;
import com.agris.gl.GlLimits;
import com.agris.gl.NoiseTexture;
import com.agris.gl.PackSamplerUsage;
import com.agris.gl.RenderTargetSet;
import com.agris.gl.ShaderCompiler;
import com.agris.glsl.GlslTransformer;
import com.agris.glsl.ProgramDirectives;
import com.agris.mobile.MobileFrameGovernor;
import com.agris.pack.PackDirectives;
import com.agris.pack.ShaderPack;
import com.agris.util.AgrisLog;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Active OpenGL multi-pass shader pipeline for a loaded {@link ShaderPack} and dimension,
 * engineered for both desktop OpenGL 3.3 Core and mobile TBDR GPUs (Adreno, Mali, Xclipse,
 * Apple Silicon, PojavLauncher, Zalith, Fold Craft).
 */
public final class ShaderPipeline implements AutoCloseable {
    private record AttrVariant(List<String> attributes, CompiledProgramBinding binding) {
    }

    private final ShaderPack pack;
    private final String dimensionId;
    private final GlLimits glLimits;
    private final AgrisConfig config;
    private final AetheriumBridge aetheriumBridge;
    private final UniformState uniformState;
    private final MobileFrameGovernor mobileGovernor;
    private final boolean mobileOptimizationActive;

    private final PackSamplerUsage samplerUsage;
    private final RenderTargetSet targets;
    private final NoiseTexture noiseTexture;
    private final FullscreenQuad fullscreenQuad;

    // Zero-allocation per-draw EnumMap lookup instead of allocating cache keys per draw call
    private final Map<ProgramId, ShaderPack.ProgramSource> resolvedGbufferSources = new EnumMap<>(ProgramId.class);
    private final Map<ProgramId, List<AttrVariant>> compiledGbuffers = new EnumMap<>(ProgramId.class);

    private final List<CompositePass> preparePasses;
    private final List<CompositePass> deferredPasses;
    private final List<CompositePass> compositePasses;
    private final CompositePass finalPass;

    private final boolean shadowPassAvailable;
    private final float effectiveShadowDistance;

    // Live per-frame execution flags
    private boolean insideWorldFrame;
    private boolean renderingShadowPass;
    private boolean renderShadowThisFrame = true;
    private boolean deferredExecutedThisFrame;
    private boolean handDepthCopiedThisFrame;
    private int activeScalePercent;
    private WorldRenderingPhase currentPhase = WorldRenderingPhase.NONE;
    private CompiledProgramBinding currentlyBoundProgram;
    private int savedMainFramebufferId;
    private int savedMainWidth;
    private int savedMainHeight;

    public ShaderPipeline(final ShaderPack pack,
                          final String dimensionId,
                          final int initialWidth,
                          final int initialHeight,
                          final GlLimits glLimits,
                          final AgrisConfig config,
                          final AetheriumBridge aetheriumBridge,
                          final UniformState sharedUniformState,
                          final MobileFrameGovernor sharedMobileGovernor) {
        this.pack = pack;
        this.dimensionId = dimensionId;
        this.glLimits = glLimits;
        this.config = config;
        this.aetheriumBridge = aetheriumBridge;
        this.uniformState = sharedUniformState;
        this.mobileGovernor = sharedMobileGovernor == null ? new MobileFrameGovernor() : sharedMobileGovernor;
        this.mobileOptimizationActive = config.isMobileOptimizationActive(glLimits.isMobileGpu());
        this.mobileGovernor.reset(config.renderScale.get());
        this.activeScalePercent = config.renderScale.get();

        final PackDirectives directives = pack.directives();
        final boolean[] referencedBuffers = new boolean[PackDirectives.MAX_COLOR_BUFFERS];
        referencedBuffers[0] = true;
        final List<String> allActiveGlsl = new ArrayList<>(32);

        // Pre-resolve every ProgramId along its fallback chain and collect active GLSL for usage stripping.
        for (final ProgramId id : ProgramId.values()) {
            if (id == ProgramId.Final) {
                continue;
            }
            final ShaderPack.ProgramSource src = resolveWithFallback(pack, id, dimensionId);
            if (src != null) {
                this.resolvedGbufferSources.put(id, src);
                markDrawBuffers(src.directives(), referencedBuffers);
                allActiveGlsl.add(src.vertexSource());
                allActiveGlsl.add(src.fragmentSource());
            }
        }

        final float renderScaleFactor = config.renderScale.get() / 100.0f;
        final float shadowScaleFactor = config.shadowResolutionScale.get() / 100.0f;
        final int scaledW = Math.max(1, Math.round(initialWidth * renderScaleFactor));
        final int scaledH = Math.max(1, Math.round(initialHeight * renderScaleFactor));
        final int scaledShadowRes = Math.max(64, Math.round(directives.shadowMapResolution() * shadowScaleFactor));
        this.effectiveShadowDistance = Math.min(directives.shadowDistance(), config.shadowMaxDistance.get() * 16.0f);
        this.shadowPassAvailable = config.shadowEnabled.get() && pack.hasShadowProgram(dimensionId);

        this.preparePasses = compileFullscreenSeries("prepare", 16, referencedBuffers, allActiveGlsl, renderScaleFactor, shadowScaleFactor);
        this.deferredPasses = compileFullscreenSeries("deferred", 16, referencedBuffers, allActiveGlsl, renderScaleFactor, shadowScaleFactor);
        this.compositePasses = compileFullscreenSeries("composite", 16, referencedBuffers, allActiveGlsl, renderScaleFactor, shadowScaleFactor);
        this.finalPass = compileFinalPass(allActiveGlsl, renderScaleFactor, shadowScaleFactor);

        this.samplerUsage = config.mobileStripUnusedBuffers.get()
                ? PackSamplerUsage.analyze(allActiveGlsl)
                : PackSamplerUsage.allUsed();

        final boolean mobileSafeFormats = config.androidSafeGlsl.get()
                && !glLimits.androidProfile().translator().supportsFloatFboReliably();
        final boolean compactHdr = this.mobileOptimizationActive
                && config.mobileHalfBandwidthHdr.get()
                && glLimits.mobileArchitecture().silicon().prefersPackedR11G11B10Hdr();
        final boolean tileDiscard = this.mobileOptimizationActive && config.mobileTileDiscard.get();
        final int maxDrawBuffers = glLimits.effectiveDrawBufferLimit(config.maxDrawBuffers.get());

        this.targets = new RenderTargetSet(
                directives, scaledW, scaledH, scaledShadowRes,
                referencedBuffers, this.shadowPassAvailable,
                mobileSafeFormats, maxDrawBuffers,
                this.samplerUsage, compactHdr, tileDiscard);
        this.noiseTexture = new NoiseTexture(directives.noiseTextureResolution());
        this.fullscreenQuad = new FullscreenQuad();
    }

    public void beginWorldFrame(final int mainFramebufferId,
                                final int mainWidth,
                                final int mainHeight,
                                final float[] cameraMv,
                                final float[] cameraProj,
                                final double camX, final double camY, final double camZ,
                                final float skyAngle,
                                final int vanillaLightmapTexId) {
        this.savedMainFramebufferId = mainFramebufferId;
        this.savedMainWidth = Math.max(1, mainWidth);
        this.savedMainHeight = Math.max(1, mainHeight);

        final long nowNanos = System.nanoTime();
        final boolean dynScale = this.mobileOptimizationActive && this.config.mobileDynamicScale.get();
        this.activeScalePercent = this.mobileGovernor.recordFrameAndComputeScale(
                this.uniformState.frameTime,
                nowNanos,
                dynScale,
                this.config.mobileTargetFps.get(),
                this.config.mobileMinScale.get(),
                this.config.renderScale.get());

        final float scale = this.activeScalePercent / 100.0f;
        final int internalW = Math.max(1, Math.round(this.savedMainWidth * scale));
        final int internalH = Math.max(1, Math.round(this.savedMainHeight * scale));
        this.targets.resizeIfNeeded(internalW, internalH);

        this.renderShadowThisFrame = this.shadowPassAvailable
                && this.mobileGovernor.shouldRenderShadowThisFrame(
                        this.config.mobileShadowCadence.get(),
                        this.mobileOptimizationActive,
                        this.config.mobileTargetFps.get(),
                        skyAngle, camX, camY, camZ);

        this.uniformState.advanceFrameClock(nowNanos, internalW, internalH);
        this.uniformState.updateWorldMatricesAndState(
                cameraMv, cameraProj, camX, camY, camZ, skyAngle,
                this.pack.directives(), this.effectiveShadowDistance,
                this.renderShadowThisFrame);

        this.targets.beginFrameClears(this.renderShadowThisFrame);
        this.insideWorldFrame = true;
        this.renderingShadowPass = false;
        this.deferredExecutedThisFrame = false;
        this.handDepthCopiedThisFrame = false;
        this.currentPhase = WorldRenderingPhase.NONE;
        this.currentlyBoundProgram = null;

        if (!this.preparePasses.isEmpty()) {
            for (final CompositePass pass : this.preparePasses) {
                pass.execute(this.uniformState, this.targets, this.fullscreenQuad,
                        this.pack.directives(), this.noiseTexture.textureId(), vanillaLightmapTexId,
                        this.savedMainFramebufferId, this.savedMainWidth, this.savedMainHeight);
            }
        }

        // Bind default GBuffer FBO so world clears & sky draw into colortex0 + depthtex0!
        this.targets.bindDefaultGbufferFbo();
    }

    public void executeShadowPass(final ShadowSceneCallback callback) {
        if (!this.insideWorldFrame || !this.shadowPassAvailable || !this.renderShadowThisFrame || callback == null) {
            return;
        }
        this.renderingShadowPass = true;
        this.aetheriumBridge.beginShadowPass(this.config);
        try {
            this.targets.bindShadowFbo();
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(true);

            callback.renderStage(ShadowSceneCallback.Stage.OPAQUE_TERRAIN,
                    this.uniformState.shadowModelView, this.uniformState.shadowProjection);

            if (this.config.shadowEntities.get() || this.config.shadowBlockEntities.get()) {
                this.currentPhase = WorldRenderingPhase.ENTITIES;
                callback.renderStage(ShadowSceneCallback.Stage.ENTITIES_AND_BLOCK_ENTITIES,
                        this.uniformState.shadowModelView, this.uniformState.shadowProjection);
            }

            this.targets.copyPreTranslucentShadowDepth();

            if (this.config.shadowTranslucent.get()) {
                this.currentPhase = WorldRenderingPhase.TERRAIN_TRANSLUCENT;
                callback.renderStage(ShadowSceneCallback.Stage.TRANSLUCENT_TERRAIN,
                        this.uniformState.shadowModelView, this.uniformState.shadowProjection);
            }
        } finally {
            this.aetheriumBridge.endShadowPass();
            this.renderingShadowPass = false;
            this.currentPhase = WorldRenderingPhase.NONE;
            this.targets.bindDefaultGbufferFbo();
        }
    }

    public void setPhase(final WorldRenderingPhase phase) {
        if (!this.insideWorldFrame) {
            return;
        }
        this.currentPhase = phase == null ? WorldRenderingPhase.NONE : phase;
    }

    public void onBeginTranslucents(final int vanillaLightmapTexId) {
        if (!this.insideWorldFrame || this.renderingShadowPass || this.deferredExecutedThisFrame) {
            return;
        }
        this.deferredExecutedThisFrame = true;
        this.targets.copyPreTranslucentDepth();

        if (!this.deferredPasses.isEmpty()) {
            for (final CompositePass pass : this.deferredPasses) {
                pass.execute(this.uniformState, this.targets, this.fullscreenQuad,
                        this.pack.directives(), this.noiseTexture.textureId(), vanillaLightmapTexId,
                        this.savedMainFramebufferId, this.savedMainWidth, this.savedMainHeight);
            }
            this.targets.bindDefaultGbufferFbo();
        }
    }

    public void onBeginHandRender() {
        if (!this.insideWorldFrame || this.renderingShadowPass || this.handDepthCopiedThisFrame) {
            return;
        }
        this.handDepthCopiedThisFrame = true;
        if (this.config.handDepth.get()) {
            this.targets.copyPreHandDepth();
        }
    }

    public void onVanillaShaderApply(final String vanillaShaderName,
                                     final List<String> vertexAttributeNames,
                                     final int vanillaTex0,
                                     final int vanillaOverlay1,
                                     final int vanillaLightmap2) {
        if (!this.insideWorldFrame) {
            return;
        }
        final ProgramId id = ProgramId.resolveForDraw(this.currentPhase, this.renderingShadowPass, vanillaShaderName);
        final CompiledProgramBinding binding = getOrCompileGbuffer(id, vertexAttributeNames);
        if (binding == null) {
            this.currentlyBoundProgram = null;
            return;
        }

        if (!this.renderingShadowPass) {
            this.targets.bindGbufferFbo(binding.directives().drawBuffers());
        } else {
            this.targets.bindShadowFbo();
        }

        final float configuredAlpha = binding.directives().alphaTestRef();
        final float effectiveAlpha = configuredAlpha >= 0.0f ? configuredAlpha : id.defaultAlphaRef();
        binding.bindAndUpload(this.uniformState, this.targets, this.noiseTexture.textureId(),
                vanillaTex0, vanillaOverlay1, vanillaLightmap2, effectiveAlpha);
        this.currentlyBoundProgram = binding;
    }

    public void onChunkOffsetUpload(final float dx, final float dy, final float dz) {
        this.uniformState.chunkOffset[0] = dx;
        this.uniformState.chunkOffset[1] = dy;
        this.uniformState.chunkOffset[2] = dz;
        if (this.currentlyBoundProgram != null) {
            this.currentlyBoundProgram.uploadChunkOffsetFast(this.uniformState, dx, dy, dz);
        }
    }

    public void onVanillaShaderClear() {
        this.uniformState.chunkOffset[0] = 0.0f;
        this.uniformState.chunkOffset[1] = 0.0f;
        this.uniformState.chunkOffset[2] = dzZero();
        if (this.currentlyBoundProgram != null) {
            this.currentlyBoundProgram = null;
            GL20.glUseProgram(0);
        }
    }

    private static float dzZero() {
        return 0.0f;
    }

    public void endWorldFrame(final int vanillaLightmapTexId) {
        if (!this.insideWorldFrame) {
            return;
        }
        try {
            if (!this.deferredExecutedThisFrame) {
                onBeginTranslucents(vanillaLightmapTexId);
            }
            if (!this.handDepthCopiedThisFrame && this.config.handDepth.get()) {
                this.targets.copyPreHandDepth();
            }

            for (final CompositePass pass : this.compositePasses) {
                pass.execute(this.uniformState, this.targets, this.fullscreenQuad,
                        this.pack.directives(), this.noiseTexture.textureId(), vanillaLightmapTexId,
                        this.savedMainFramebufferId, this.savedMainWidth, this.savedMainHeight);
            }

            if (this.finalPass != null) {
                this.finalPass.execute(this.uniformState, this.targets, this.fullscreenQuad,
                        this.pack.directives(), this.noiseTexture.textureId(), vanillaLightmapTexId,
                        this.savedMainFramebufferId, this.savedMainWidth, this.savedMainHeight);
            } else {
                this.targets.prepareForFinalPresentation();
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.savedMainFramebufferId);
                GL11.glViewport(0, 0, this.savedMainWidth, this.savedMainHeight);
            }
        } finally {
            this.currentlyBoundProgram = null;
            this.insideWorldFrame = false;
            this.renderingShadowPass = false;
            this.currentPhase = WorldRenderingPhase.NONE;
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.savedMainFramebufferId);
            GL11.glViewport(0, 0, this.savedMainWidth, this.savedMainHeight);
        }
    }

    public boolean isInsideWorldFrame() {
        return this.insideWorldFrame;
    }

    public boolean isRenderingShadowPass() {
        return this.renderingShadowPass;
    }

    public boolean isOverridingShaderProgram() {
        return this.insideWorldFrame && this.currentlyBoundProgram != null;
    }

    public boolean isShadowPassAvailable() {
        return this.shadowPassAvailable;
    }

    public boolean isMobileOptimizationActive() {
        return this.mobileOptimizationActive;
    }

    public int activeScalePercent() {
        return this.activeScalePercent;
    }

    public int shadowResolution() {
        return this.targets.shadowResolution();
    }

    public PackSamplerUsage samplerUsage() {
        return this.samplerUsage;
    }

    public ShaderPack pack() {
        return this.pack;
    }

    public String dimensionId() {
        return this.dimensionId;
    }

    public UniformState uniformState() {
        return this.uniformState;
    }

    private CompiledProgramBinding getOrCompileGbuffer(final ProgramId id,
                                                       final List<String> vertexAttributes) {
        List<AttrVariant> bucket = this.compiledGbuffers.get(id);
        if (bucket != null) {
            for (int i = 0, len = bucket.size(); i < len; i++) {
                final AttrVariant v = bucket.get(i);
                if (v.attributes().equals(vertexAttributes)) {
                    return v.binding();
                }
            }
        }

        final ShaderPack.ProgramSource src = this.resolvedGbufferSources.get(id);
        if (src == null) {
            return null;
        }
        final float rq = this.config.renderScale.get() / 100.0f;
        final float sq = this.config.shadowResolutionScale.get() / 100.0f;
        final String vsh = GlslTransformer.transform(src.vertexSource(), GlslTransformer.Stage.VERTEX,
                src.directives(), this.glLimits.androidProfile(), rq, sq);
        final String gsh = src.geometrySource() == null ? null : GlslTransformer.transform(
                src.geometrySource(), GlslTransformer.Stage.GEOMETRY,
                src.directives(), this.glLimits.androidProfile(), rq, sq);
        final String fsh = GlslTransformer.transform(src.fragmentSource(), GlslTransformer.Stage.FRAGMENT,
                src.directives(), this.glLimits.androidProfile(), rq, sq);

        final int prog = ShaderCompiler.compileAndLink(
                src.name(), vsh, gsh, fsh,
                vertexAttributes,
                src.directives().drawBuffers().length,
                this.config.logShaders.get());
        final CompiledProgramBinding binding = new CompiledProgramBinding(
                prog, src.name(), src.directives(), this.pack.directives(), this.glLimits.maxTextureImageUnits());
        if (bucket == null) {
            bucket = new ArrayList<>(2);
            this.compiledGbuffers.put(id, bucket);
        }
        bucket.add(new AttrVariant(List.copyOf(vertexAttributes), binding));
        return binding;
    }

    private List<CompositePass> compileFullscreenSeries(final String prefix,
                                                        final int count,
                                                        final boolean[] referencedBuffers,
                                                        final List<String> outGlslList,
                                                        final float renderQuality,
                                                        final float shadowQuality) {
        final List<CompositePass> passes = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            final String name = i == 0 ? prefix : prefix + i;
            final ShaderPack.ProgramSource src = this.pack.resolveProgram(name, this.dimensionId);
            if (src == null) {
                continue;
            }
            markDrawBuffers(src.directives(), referencedBuffers);
            markReferencedColorSamplers(src.fragmentSource(), referencedBuffers);
            outGlslList.add(src.vertexSource());
            outGlslList.add(src.fragmentSource());

            final String vsh = GlslTransformer.transform(src.vertexSource(), GlslTransformer.Stage.VERTEX,
                    src.directives(), this.glLimits.androidProfile(), renderQuality, shadowQuality);
            final String fsh = GlslTransformer.transform(src.fragmentSource(), GlslTransformer.Stage.FRAGMENT,
                    src.directives(), this.glLimits.androidProfile(), renderQuality, shadowQuality);
            final int prog = ShaderCompiler.compileAndLink(
                    name, vsh, null, fsh,
                    FullscreenQuad.VERTEX_ATTRIBUTES,
                    src.directives().drawBuffers().length,
                    this.config.logShaders.get());
            final CompiledProgramBinding binding = new CompiledProgramBinding(
                    prog, name, src.directives(), this.pack.directives(), this.glLimits.maxTextureImageUnits());
            passes.add(new CompositePass(name, binding, false));
        }
        return passes;
    }

    private CompositePass compileFinalPass(final List<String> outGlslList,
                                           final float renderQuality,
                                           final float shadowQuality) {
        ShaderPack.ProgramSource src = this.pack.resolveProgram("final", this.dimensionId);
        if (src == null) {
            final ProgramDirectives dirs = ProgramDirectives.defaults();
            src = new ShaderPack.ProgramSource("final",
                    "#version 120\nvarying vec2 vUv;\nvoid main() { vUv = gl_MultiTexCoord0.xy; gl_Position = vec4(gl_Vertex.xy * 2.0 - 1.0, 0.0, 1.0); }\n",
                    null,
                    "#version 120\nuniform sampler2D colortex0;\nvarying vec2 vUv;\nvoid main() { gl_FragColor = texture2D(colortex0, vUv); }\n",
                    dirs);
        }
        outGlslList.add(src.vertexSource());
        outGlslList.add(src.fragmentSource());
        final String vsh = GlslTransformer.transform(src.vertexSource(), GlslTransformer.Stage.VERTEX,
                src.directives(), this.glLimits.androidProfile(), renderQuality, shadowQuality);
        final String fsh = GlslTransformer.transform(src.fragmentSource(), GlslTransformer.Stage.FRAGMENT,
                src.directives(), this.glLimits.androidProfile(), renderQuality, shadowQuality);
        final int prog = ShaderCompiler.compileAndLink(
                "final", vsh, null, fsh,
                FullscreenQuad.VERTEX_ATTRIBUTES, 1, this.config.logShaders.get());
        final CompiledProgramBinding binding = new CompiledProgramBinding(
                prog, "final", src.directives(), this.pack.directives(), this.glLimits.maxTextureImageUnits());
        return new CompositePass("final", binding, true);
    }

    private static ShaderPack.ProgramSource resolveWithFallback(final ShaderPack pack,
                                                                final ProgramId id,
                                                                final String dimensionId) {
        ProgramId cursor = id;
        while (cursor != null) {
            final ShaderPack.ProgramSource found = pack.resolveProgram(cursor.sourceName(), dimensionId);
            if (found != null) {
                return found;
            }
            cursor = cursor.fallback();
        }
        return null;
    }

    private static void markDrawBuffers(final ProgramDirectives directives, final boolean[] referenced) {
        for (final int idx : directives.drawBuffers()) {
            if (idx >= 0 && idx < referenced.length) {
                referenced[idx] = true;
            }
        }
    }

    private static void markReferencedColorSamplers(final String fsh, final boolean[] referenced) {
        for (int i = 0; i < referenced.length; i++) {
            if (fsh.contains("colortex" + i)) {
                referenced[i] = true;
            }
        }
        if (fsh.contains("gcolor")) referenced[0] = true;
        if (fsh.contains("gdepth")) referenced[1] = true;
        if (fsh.contains("gnormal")) referenced[2] = true;
        if (fsh.contains("composite")) referenced[3] = true;
        if (fsh.contains("gaux1")) referenced[4] = true;
        if (fsh.contains("gaux2")) referenced[5] = true;
        if (fsh.contains("gaux3")) referenced[6] = true;
        if (fsh.contains("gaux4")) referenced[7] = true;
    }

    @Override
    public void close() {
        for (final List<AttrVariant> bucket : this.compiledGbuffers.values()) {
            for (final AttrVariant v : bucket) {
                v.binding().close();
            }
        }
        this.compiledGbuffers.clear();
        for (final CompositePass p : this.preparePasses) {
            p.close();
        }
        for (final CompositePass p : this.deferredPasses) {
            p.close();
        }
        for (final CompositePass p : this.compositePasses) {
            p.close();
        }
        if (this.finalPass != null) {
            this.finalPass.close();
        }
        this.fullscreenQuad.close();
        this.noiseTexture.close();
        this.targets.close();
        AgrisLog.debug("Disposed Agris ShaderPipeline for pack '{}' ({})", this.pack.id(), this.dimensionId);
    }
}
