package com.agris.client;

import com.agris.Agris;
import com.agris.gl.GlStateSync;
import com.agris.gui.AgrisShaderOptionsScreen;
import com.agris.gui.AgrisShaderPackScreen;
import com.agris.mixin.core.LevelRendererInvoker;
import com.agris.pack.ShaderPack;
import com.agris.pipeline.PipelineManager;
import com.agris.pipeline.ShaderPipeline;
import com.agris.pipeline.ShadowSceneCallback;
import com.agris.pipeline.UniformState;
import com.agris.pipeline.WorldRenderingPhase;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import net.irisshaders.iris.apiimpl.IrisApiV0Impl;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.FogType;
import com.agris.util.MathUtil;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Render-thread bridge connecting Minecraft's {@code LevelRenderer}, {@code GameRenderer},
 * {@code ShaderInstance}, and {@code Uniform} hooks to Agris's {@link PipelineManager}
 * and {@link ShaderPipeline}.
 */
public final class AgrisClientHooks {
    private static final int BLAZE3D_TRACKED_TEXTURE_UNITS = 12;
    private static final float[] TMP_MV = new float[16];
    private static final float[] TMP_PROJ = new float[16];
    private static final float[] TMP_TEX = new float[16];
    private static final Map<Uniform, float[]> STAGED_MATRICES = new IdentityHashMap<>(64);
    private static final Matrix4f SHADOW_MV_JOML = new Matrix4f();
    private static final Matrix4f SHADOW_PROJ_JOML = new Matrix4f();
    private static final PoseStack SHADOW_POSE_STACK = new PoseStack();

    private static Uniform activeChunkOffsetUniform;
    private static float stagedChunkX;
    private static float stagedChunkY;
    private static float stagedChunkZ;
    private static int cachedLightmapTexId;
    private static boolean clientWired;

    private static final GlStateSync BLAZE3D_SYNC = new GlStateSync() {
        @Override
        public void bindFramebuffer(final int target, final int fbo) {
            GlStateManager._glBindFramebuffer(target, fbo);
        }

        @Override
        public void viewport(final int x, final int y, final int width, final int height) {
            GlStateManager._viewport(x, y, width, height);
        }

        @Override
        public void useProgram(final int programId) {
            GlStateManager._glUseProgram(programId);
        }

        @Override
        public void bindTexture2D(final int unitIndex, final int textureId) {
            if (unitIndex >= 0 && unitIndex < BLAZE3D_TRACKED_TEXTURE_UNITS) {
                GlStateManager._activeTexture(GL13.GL_TEXTURE0 + unitIndex);
                GlStateManager._bindTexture(textureId);
            } else {
                GL13.glActiveTexture(GL13.GL_TEXTURE0 + unitIndex);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
            }
        }

        @Override
        public void activeTextureUnit0() {
            GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        }

        @Override
        public void bindScratchTexture2D(final int textureId) {
            GL13.glActiveTexture(SCRATCH_TEXTURE_UNIT);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
        }

        @Override
        public void endScratchTexture2D() {
            GL13.glActiveTexture(SCRATCH_TEXTURE_UNIT);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            final int blazeUnit = GlStateManager._getActiveTexture();
            GL13.glActiveTexture(blazeUnit >= GL13.GL_TEXTURE0 ? blazeUnit : GL13.GL_TEXTURE0);
        }

        @Override
        public void bindVertexArray(final int vao) {
            // VertexBuffer.unbind() invokes BufferUploader.invalidate() at bytecode offset 0!
            VertexBuffer.unbind();
            GlStateManager._glBindVertexArray(vao);
        }

        @Override
        public void unbindVertexArray() {
            VertexBuffer.unbind();
        }

        @Override
        public void prepareForClear() {
            GlStateManager._disableScissorTest();
            GlStateManager._colorMask(true, true, true, true);
            GlStateManager._depthMask(true);
        }

        @Override
        public void prepareFullscreenQuadState() {
            GlStateManager._disableScissorTest();
            GlStateManager._disableDepthTest();
            GlStateManager._depthMask(false);
            GlStateManager._disableBlend();
            GlStateManager._disableCull();
            GlStateManager._colorMask(true, true, true, true);
        }

        @Override
        public void restoreAfterFullscreenQuad() {
            GlStateManager._depthMask(true);
            GlStateManager._enableDepthTest();
        }

        @Override
        public void prepareShadowPassState() {
            GlStateManager._disableScissorTest();
            GlStateManager._enableDepthTest();
            GlStateManager._depthMask(true);
            GlStateManager._disableBlend();
            GlStateManager._disableCull();
            GlStateManager._colorMask(true, true, true, true);
        }

        @Override
        public void restoreAfterShadowPass() {
            GlStateManager._enableCull();
        }

        @Override
        public void restoreMainRenderTarget(final int mainFbo, final int mainWidth, final int mainHeight) {
            final Minecraft mc = Minecraft.getInstance();
            final RenderTarget mainTarget = mc != null ? mc.getMainRenderTarget() : null;
            if (mainTarget != null && mainTarget.frameBufferId == mainFbo) {
                mainTarget.bindWrite(true);
            } else {
                GlStateManager._glBindFramebuffer(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER, mainFbo);
                GlStateManager._viewport(0, 0, mainWidth, mainHeight);
            }
            GlStateManager._colorMask(true, true, true, true);
            GlStateManager._depthMask(true);
            GlStateManager._enableDepthTest();
            GlStateManager._glUseProgram(0);
            VertexBuffer.unbind();
            GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        }
    };

    private AgrisClientHooks() {
    }

    public static void ensureClientWired() {
        if (clientWired) {
            return;
        }
        clientWired = true;
        GlStateSync.install(BLAZE3D_SYNC);
        Agris.initialize();
        IrisApiV0Impl.installScreenFactory(parent ->
                new AgrisShaderPackScreen(parent instanceof Screen s ? s : null));
        Agris.installScreenOpener(parent -> {
            final Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
                mc.setScreen(new AgrisShaderPackScreen(parent instanceof Screen s ? s : mc.screen));
            }
        });
    }

    public static void openShaderPackScreenFromCurrent() {
        ensureClientWired();
        final Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.setScreen(new AgrisShaderPackScreen(mc.screen));
        }
    }

    public static void openShaderOptionsScreenFromCurrent() {
        ensureClientWired();
        final Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }
        ShaderPack pack = Agris.pipeline().loadedPackOrNull();
        if (pack == null) {
            try {
                pack = Agris.packs().loadPack(Agris.config().shaderPack.get(), activeLanguageCode(mc));
            } catch (final Throwable ignored) {
                pack = null;
            }
        }
        if (pack != null) {
            mc.setScreen(new AgrisShaderOptionsScreen(mc.screen, pack));
        } else {
            mc.setScreen(new AgrisShaderPackScreen(mc.screen));
        }
    }

    public static void reloadShadersAndChunks() {
        ensureClientWired();
        Agris.pipeline().requestReload();
        final Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.levelRenderer != null) {
            mc.levelRenderer.allChanged();
        }
    }

    public static void onRenderSystemInit() {
        ensureClientWired();
        Agris.pipeline().onGlContextInitialized();
    }

    public static void onClientTick(final Minecraft mc) {
        ensureClientWired();
        AgrisKeybinds.pollClientTick(mc);
    }

    public static void onWorldChanged(final ClientLevel newLevel) {
        ensureClientWired();
        STAGED_MATRICES.clear();
        activeChunkOffsetUniform = null;
        cachedLightmapTexId = 0;
        if (newLevel == null) {
            Agris.pipeline().destroyPipeline();
        } else {
            final String dimId = newLevel.dimension().location().toString();
            Agris.pipeline().setActiveDimension(dimId);
        }
    }

    public static void onBeginLevelRender(final LevelRenderer levelRenderer,
                                          final Camera camera,
                                          final float partialTick,
                                          final LightTexture lightTexture,
                                          final Matrix4f cameraModelView,
                                          final Matrix4f cameraProjection) {
        ensureClientWired();
        final Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) {
            return;
        }

        // Read Minecraft.getMainRenderTarget() right here inside renderLevel: if Aetherium's
        // ScaledRenderTarget is active, mainRenderTarget is ALREADY swapped to the scaled FBO,
        // so Agris renders at Aetherium's exact world scale while HUD stays crisp!
        final RenderTarget mainTarget = mc.getMainRenderTarget();
        if (mainTarget == null) {
            return;
        }

        final PipelineManager mgr = Agris.pipeline();
        final ShaderPipeline pipeline = mgr.prepareForFrame(
                mainTarget.width, mainTarget.height, activeLanguageCode(mc));
        if (pipeline == null) {
            return;
        }

        try {
            // Ensure vanilla's lightmap texture is bound on shader slot 2 so prepare/deferred/composite/final
            // passes always receive the real OpenGL lightmap texture ID rather than 0.
            if (lightTexture != null) {
                lightTexture.turnOnLightLayer();
            }
            populateWorldUniforms(mc, mc.level, camera, partialTick, mgr.uniformState());
            cameraModelView.get(TMP_MV);
            cameraProjection.get(TMP_PROJ);
            final Vec3 pos = camera.getPosition();
            final float skyAngle = mc.level.getTimeOfDay(partialTick);
            final int lightmapTex = resolveLightmapTextureId();

            pipeline.beginWorldFrame(
                    mainTarget.frameBufferId,
                    mainTarget.width,
                    mainTarget.height,
                    TMP_MV,
                    TMP_PROJ,
                    pos.x, pos.y, pos.z,
                    skyAngle,
                    lightmapTex);

            if (pipeline.isShadowPassAvailable() && levelRenderer instanceof LevelRendererInvoker invoker) {
                pipeline.executeShadowPass((stage, shadowMv, shadowProj) -> {
                    SHADOW_MV_JOML.set(shadowMv);
                    SHADOW_PROJ_JOML.set(shadowProj);
                    SHADOW_POSE_STACK.last().pose().set(SHADOW_MV_JOML);
                    if (stage == ShadowSceneCallback.Stage.OPAQUE_TERRAIN) {
                        pipeline.setPhase(WorldRenderingPhase.TERRAIN_SOLID);
                        invoker.agris$invokeRenderChunkLayer(
                                RenderType.solid(), SHADOW_POSE_STACK, pos.x, pos.y, pos.z, SHADOW_PROJ_JOML);
                        pipeline.setPhase(WorldRenderingPhase.TERRAIN_CUTOUT_MIPPED);
                        invoker.agris$invokeRenderChunkLayer(
                                RenderType.cutoutMipped(), SHADOW_POSE_STACK, pos.x, pos.y, pos.z, SHADOW_PROJ_JOML);
                        pipeline.setPhase(WorldRenderingPhase.TERRAIN_CUTOUT);
                        invoker.agris$invokeRenderChunkLayer(
                                RenderType.cutout(), SHADOW_POSE_STACK, pos.x, pos.y, pos.z, SHADOW_PROJ_JOML);
                    } else if (stage == ShadowSceneCallback.Stage.TRANSLUCENT_TERRAIN) {
                        pipeline.setPhase(WorldRenderingPhase.TERRAIN_TRANSLUCENT);
                        invoker.agris$invokeRenderChunkLayer(
                                RenderType.translucent(), SHADOW_POSE_STACK, pos.x, pos.y, pos.z, SHADOW_PROJ_JOML);
                    }
                });
            }
        } catch (final Throwable ex) {
            mgr.handleRuntimeRenderFailure(ex);
        }
    }

    public static void onSectionLayerBegin(final RenderType renderType) {
        final ShaderPipeline pipeline = Agris.pipeline().activePipelineOrNull();
        if (pipeline == null || !pipeline.isInsideWorldFrame() || pipeline.isRenderingShadowPass()) {
            return;
        }
        if (renderType == RenderType.solid()) {
            pipeline.setPhase(WorldRenderingPhase.TERRAIN_SOLID);
        } else if (renderType == RenderType.cutoutMipped()) {
            pipeline.setPhase(WorldRenderingPhase.TERRAIN_CUTOUT_MIPPED);
        } else if (renderType == RenderType.cutout()) {
            pipeline.setPhase(WorldRenderingPhase.TERRAIN_CUTOUT);
        } else if (renderType == RenderType.translucent()) {
            pipeline.onBeginTranslucents(resolveLightmapTextureId());
            pipeline.setPhase(WorldRenderingPhase.TERRAIN_TRANSLUCENT);
        } else if (renderType == RenderType.tripwire()) {
            pipeline.setPhase(WorldRenderingPhase.TRIPWIRE);
        }
    }

    public static void onPhaseChange(final WorldRenderingPhase phase) {
        final ShaderPipeline pipeline = Agris.pipeline().activePipelineOrNull();
        if (pipeline != null && pipeline.isInsideWorldFrame() && !pipeline.isRenderingShadowPass()) {
            if (phase == WorldRenderingPhase.SKY) {
                final float[] fogCol = RenderSystem.getShaderFogColor();
                if (fogCol != null && fogCol.length >= 3) {
                    final UniformState u = pipeline.uniformState();
                    u.fogColor[0] = fogCol[0];
                    u.fogColor[1] = fogCol[1];
                    u.fogColor[2] = fogCol[2];
                }
            }
            pipeline.setPhase(phase);
        }
    }

    public static void onBeginHandRender() {
        final ShaderPipeline pipeline = Agris.pipeline().activePipelineOrNull();
        if (pipeline != null && pipeline.isInsideWorldFrame() && !pipeline.isRenderingShadowPass()) {
            pipeline.onBeginHandRender();
            pipeline.setPhase(WorldRenderingPhase.HAND_SOLID);
        }
    }

    public static void onEndLevelRender() {
        final PipelineManager mgr = Agris.pipeline();
        final ShaderPipeline pipeline = mgr.activePipelineOrNull();
        if (pipeline == null || !pipeline.isInsideWorldFrame()) {
            return;
        }
        try {
            pipeline.endWorldFrame(resolveLightmapTextureId());
        } catch (final Throwable ex) {
            mgr.handleRuntimeRenderFailure(ex);
        }
    }

    public static void onUniformSetMat4(final Uniform self, final Matrix4f matrix) {
        if (self == null || matrix == null) {
            return;
        }
        // Always capture per-Uniform Matrix4f values without ring-buffer slot collisions:
        // renderChunkLayer and VertexBuffer._drawWithShader set MODEL_VIEW_MATRIX,
        // PROJECTION_MATRIX, and TEXTURE_MATRIX before calling ShaderInstance.apply().
        float[] buf = STAGED_MATRICES.get(self);
        if (buf == null) {
            buf = new float[16];
            STAGED_MATRICES.put(self, buf);
        }
        matrix.get(buf);
    }

    public static void onBeforeShaderApply() {
        activeChunkOffsetUniform = null;
        final ShaderPipeline pipeline = Agris.pipeline().activePipelineOrNull();
        if (pipeline != null && pipeline.isInsideWorldFrame()) {
            pipeline.onBeforeVanillaShaderApply();
        }
    }

    public static void onShaderApply(final ShaderInstance instance) {
        final PipelineManager mgr = Agris.pipeline();
        final ShaderPipeline pipeline = mgr.activePipelineOrNull();
        if (pipeline == null || !pipeline.isInsideWorldFrame()) {
            activeChunkOffsetUniform = null;
            return;
        }
        try {
            final float[] stagedMv = instance.MODEL_VIEW_MATRIX != null
                    ? STAGED_MATRICES.get(instance.MODEL_VIEW_MATRIX) : null;
            if (stagedMv != null) {
                System.arraycopy(stagedMv, 0, TMP_MV, 0, 16);
            } else {
                RenderSystem.getModelViewMatrix().get(TMP_MV);
            }

            final float[] stagedProj = instance.PROJECTION_MATRIX != null
                    ? STAGED_MATRICES.get(instance.PROJECTION_MATRIX) : null;
            if (stagedProj != null) {
                System.arraycopy(stagedProj, 0, TMP_PROJ, 0, 16);
            } else {
                RenderSystem.getProjectionMatrix().get(TMP_PROJ);
            }

            final float[] stagedTex = instance.TEXTURE_MATRIX != null
                    ? STAGED_MATRICES.get(instance.TEXTURE_MATRIX) : null;
            if (stagedTex != null) {
                System.arraycopy(stagedTex, 0, TMP_TEX, 0, 16);
            } else {
                RenderSystem.getTextureMatrix().get(TMP_TEX);
            }

            pipeline.uniformState().setPerDrawMatrices(TMP_MV, TMP_PROJ, TMP_TEX);

            final UniformState u = pipeline.uniformState();
            final float[] shaderColor = RenderSystem.getShaderColor();
            if (shaderColor != null && shaderColor.length >= 4) {
                System.arraycopy(shaderColor, 0, u.colorModulator, 0, 4);
            }
            final float[] fogCol = RenderSystem.getShaderFogColor();
            if (fogCol != null && fogCol.length >= 3) {
                u.fogColor[0] = fogCol[0];
                u.fogColor[1] = fogCol[1];
                u.fogColor[2] = fogCol[2];
            }
            u.fogStart = RenderSystem.getShaderFogStart();
            u.fogEnd = RenderSystem.getShaderFogEnd();
            final FogShape shape = RenderSystem.getShaderFogShape();
            u.fogShape = shape != null ? shape.getIndex() : 0;

            activeChunkOffsetUniform = instance.CHUNK_OFFSET;
            stagedChunkX = 0.0f;
            stagedChunkY = 0.0f;
            stagedChunkZ = 0.0f;

            final List<String> attrs = instance.getVertexFormat().getElementAttributeNames();
            pipeline.onVanillaShaderApply(
                    instance.getName(),
                    attrs,
                    RenderSystem.getShaderTexture(0),
                    RenderSystem.getShaderTexture(1),
                    resolveLightmapTextureId());
        } catch (final Throwable ex) {
            activeChunkOffsetUniform = null;
            mgr.handleRuntimeRenderFailure(ex);
        }
    }

    public static void onShaderClear() {
        activeChunkOffsetUniform = null;
        final ShaderPipeline pipeline = Agris.pipeline().activePipelineOrNull();
        if (pipeline != null && pipeline.isInsideWorldFrame()) {
            pipeline.onVanillaShaderClear();
        }
    }

    private static int resolveLightmapTextureId() {
        final int live = RenderSystem.getShaderTexture(2);
        if (live > 0) {
            cachedLightmapTexId = live;
            return live;
        }
        return cachedLightmapTexId;
    }

    public static void onUniformSet3f(final Uniform self, final float x, final float y, final float z) {
        if (self != null && self == activeChunkOffsetUniform) {
            stagedChunkX = x;
            stagedChunkY = y;
            stagedChunkZ = z;
        }
    }

    public static boolean isOverridingShaderProgram() {
        final ShaderPipeline pipeline = Agris.pipeline().activePipelineOrNull();
        return pipeline != null && pipeline.isOverridingShaderProgram();
    }

    public static void onCancelledUniformUpload(final Uniform self) {
        if (self != null && self == activeChunkOffsetUniform) {
            final ShaderPipeline pipeline = Agris.pipeline().activePipelineOrNull();
            if (pipeline != null) {
                pipeline.onChunkOffsetUpload(stagedChunkX, stagedChunkY, stagedChunkZ);
            }
        }
    }

    public static void appendDebugOverlayLines(final List<String> lines) {
        if (lines == null || !Agris.config().showDebugHud.get()) {
            return;
        }
        final PipelineManager mgr = Agris.pipeline();
        final ShaderPipeline p = mgr.activePipelineOrNull();
        if (p != null) {
            final int configuredCadence = Agris.config().mobileShadowCadence.get();
            lines.add("[Agris] Pack: " + p.pack().displayName()
                    + " | Shadow: " + (p.isShadowPassAvailable()
                    ? p.shadowResolution() + "x" + p.shadowResolution()
                    + (configuredCadence > 1 ? " (1/" + configuredCadence + "f)" : "")
                    : "off")
                    + " | " + Agris.aetherium().describeStatus());
            if (p.isMobileOptimizationActive() || mgr.glLimits().isMobileGpu()) {
                final float emaMs = mgr.mobileGovernor().smoothedFrameMs();
                lines.add(String.format("[Agris Mobile] %s | Scale: %d%% | EMA: %.1f ms (%.0f FPS)",
                        mgr.glLimits().mobileArchitecture().summary(),
                        p.activeScalePercent(),
                        emaMs,
                        emaMs > 0.05f ? 1000.0f / emaMs : 0.0f));
            }
        } else {
            lines.add("[Agris] " + Agris.describePipelineShort());
        }
    }

    private static void populateWorldUniforms(final Minecraft mc,
                                              final ClientLevel level,
                                              final Camera camera,
                                              final float partialTick,
                                              final UniformState u) {
        final long dayTime = level.getDayTime();
        u.worldTime = (int) (dayTime % 24000L);
        u.worldDay = (int) (dayTime / 24000L);
        u.moonPhase = level.getMoonPhase();
        u.rainStrength = level.getRainLevel(partialTick);
        u.near = 0.05f;
        u.far = mc.options != null ? mc.options.getEffectiveRenderDistance() * 16.0f : 192.0f;
        u.hideGUI = mc.options != null && mc.options.hideGui ? 1 : 0;

        final Vec3 sky = level.getSkyColor(camera.getPosition(), partialTick);
        if (sky != null) {
            u.skyColor[0] = (float) sky.x;
            u.skyColor[1] = (float) sky.y;
            u.skyColor[2] = (float) sky.z;
        }

        final FogType fluid = camera.getFluidInCamera();
        if (fluid == FogType.WATER) {
            u.isEyeInWater = 1;
        } else if (fluid == FogType.LAVA) {
            u.isEyeInWater = 2;
        } else if (fluid == FogType.POWDER_SNOW) {
            u.isEyeInWater = 3;
        } else {
            u.isEyeInWater = 0;
        }

        final BlockPos eyeBlockPos = camera.getBlockPosition();
        if (eyeBlockPos != null) {
            u.eyeBrightnessBlock = level.getBrightness(LightLayer.BLOCK, eyeBlockPos) * 16;
            u.eyeBrightnessSky = level.getBrightness(LightLayer.SKY, eyeBlockPos) * 16;
        }

        final LocalPlayer player = mc.player;
        if (player != null && Agris.config().bridgeDynamicLights.get()) {
            u.heldBlockLightValue = itemLightEmission(player.getMainHandItem());
            u.heldBlockLightValue2 = itemLightEmission(player.getOffhandItem());
        } else {
            u.heldBlockLightValue = 0;
            u.heldBlockLightValue2 = 0;
        }

        // Zero-stall CPU centerDepthSmooth via crosshair HitResult (avoids glReadPixels pipeline stall on mobile TBDR GPUs)
        float targetCenterZ = 0.9995f;
        final HitResult hit = mc.hitResult;
        final Vec3 camPos = camera.getPosition();
        if (hit != null && camPos != null && hit.getType() != HitResult.Type.MISS) {
            final Vec3 hp = hit.getLocation();
            if (hp != null) {
                final double dx = hp.x - camPos.x;
                final double dy = hp.y - camPos.y;
                final double dz = hp.z - camPos.z;
                final float dist = Math.max(u.near, Math.min(u.far, (float) Math.sqrt(dx * dx + dy * dy + dz * dz)));
                final float denom = Math.max(0.001f, (u.far - u.near) * dist);
                final float zNdc = ((u.far + u.near) * dist - 2.0f * u.far * u.near) / denom;
                targetCenterZ = MathUtil.clamp(0.5f * zNdc + 0.5f, 0.0f, 1.0f);
            }
        }
        final ShaderPipeline activePipe = Agris.pipeline().activePipelineOrNull();
        final float halfLife = activePipe != null
                ? activePipe.pack().directives().centerDepthHalfLife()
                : 1.0f;
        u.centerDepthSmooth = MathUtil.smoothHalfLife(u.centerDepthSmooth, targetCenterZ, u.frameTime, halfLife);
    }

    private static int itemLightEmission(final ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        try {
            final Block block = Block.byItem(stack.getItem());
            return block == null ? 0 : Math.max(0, Math.min(15, block.defaultBlockState().getLightEmission()));
        } catch (final Throwable ignored) {
            return 0;
        }
    }

    private static String activeLanguageCode(final Minecraft mc) {
        if (mc != null && mc.options != null && mc.options.languageCode != null) {
            return mc.options.languageCode;
        }
        return "en_us";
    }
}
