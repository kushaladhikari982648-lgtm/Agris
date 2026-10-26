package com.agris.client;

import com.agris.Agris;
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
import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.irisshaders.iris.apiimpl.IrisApiV0Impl;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
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

import java.util.List;

/**
 * Render-thread bridge connecting Minecraft's {@code LevelRenderer}, {@code GameRenderer},
 * {@code ShaderInstance}, and {@code Uniform} hooks to Agris's {@link PipelineManager}
 * and {@link ShaderPipeline}.
 */
public final class AgrisClientHooks {
    private static final float[] TMP_MV = new float[16];
    private static final float[] TMP_PROJ = new float[16];
    private static final float[] TMP_TEX = new float[16];
    private static final float[] STAGED_MAT_0 = new float[16];
    private static final float[] STAGED_MAT_1 = new float[16];
    private static final Matrix4f SHADOW_MV_JOML = new Matrix4f();
    private static final Matrix4f SHADOW_PROJ_JOML = new Matrix4f();
    private static final PoseStack SHADOW_POSE_STACK = new PoseStack();

    private static Uniform stagedMatUniform0;
    private static Uniform stagedMatUniform1;
    private static int stagedMatSlot;
    private static Uniform activeChunkOffsetUniform;
    private static float stagedChunkX;
    private static float stagedChunkY;
    private static float stagedChunkZ;
    private static boolean clientWired;

    private AgrisClientHooks() {
    }

    public static void ensureClientWired() {
        if (clientWired) {
            return;
        }
        clientWired = true;
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
            populateWorldUniforms(mc, mc.level, camera, partialTick, mgr.uniformState());
            cameraModelView.get(TMP_MV);
            cameraProjection.get(TMP_PROJ);
            final Vec3 pos = camera.getPosition();
            final float skyAngle = mc.level.getTimeOfDay(partialTick);
            final int lightmapTex = RenderSystem.getShaderTexture(2);

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
            pipeline.onBeginTranslucents(RenderSystem.getShaderTexture(2));
            pipeline.setPhase(WorldRenderingPhase.TERRAIN_TRANSLUCENT);
        } else if (renderType == RenderType.tripwire()) {
            pipeline.setPhase(WorldRenderingPhase.TRIPWIRE);
        }
    }

    public static void onPhaseChange(final WorldRenderingPhase phase) {
        final ShaderPipeline pipeline = Agris.pipeline().activePipelineOrNull();
        if (pipeline != null && pipeline.isInsideWorldFrame() && !pipeline.isRenderingShadowPass()) {
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
            pipeline.endWorldFrame(RenderSystem.getShaderTexture(2));
        } catch (final Throwable ex) {
            mgr.handleRuntimeRenderFailure(ex);
        }
    }

    public static void onUniformSetMat4(final Uniform self, final Matrix4f matrix) {
        if (self == null || matrix == null) {
            return;
        }
        final ShaderPipeline pipeline = Agris.pipeline().activePipelineOrNull();
        if (pipeline == null || !pipeline.isInsideWorldFrame()) {
            return;
        }
        if ((stagedMatSlot & 1) == 0) {
            stagedMatUniform0 = self;
            matrix.get(STAGED_MAT_0);
        } else {
            stagedMatUniform1 = self;
            matrix.get(STAGED_MAT_1);
        }
        stagedMatSlot++;
    }

    public static void onShaderApply(final ShaderInstance instance) {
        final PipelineManager mgr = Agris.pipeline();
        final ShaderPipeline pipeline = mgr.activePipelineOrNull();
        if (pipeline == null || !pipeline.isInsideWorldFrame()) {
            stagedMatUniform0 = null;
            stagedMatUniform1 = null;
            activeChunkOffsetUniform = null;
            return;
        }
        try {
            if (instance.MODEL_VIEW_MATRIX != null && instance.MODEL_VIEW_MATRIX == stagedMatUniform0) {
                System.arraycopy(STAGED_MAT_0, 0, TMP_MV, 0, 16);
            } else if (instance.MODEL_VIEW_MATRIX != null && instance.MODEL_VIEW_MATRIX == stagedMatUniform1) {
                System.arraycopy(STAGED_MAT_1, 0, TMP_MV, 0, 16);
            } else {
                RenderSystem.getModelViewMatrix().get(TMP_MV);
            }

            if (instance.PROJECTION_MATRIX != null && instance.PROJECTION_MATRIX == stagedMatUniform0) {
                System.arraycopy(STAGED_MAT_0, 0, TMP_PROJ, 0, 16);
            } else if (instance.PROJECTION_MATRIX != null && instance.PROJECTION_MATRIX == stagedMatUniform1) {
                System.arraycopy(STAGED_MAT_1, 0, TMP_PROJ, 0, 16);
            } else {
                RenderSystem.getProjectionMatrix().get(TMP_PROJ);
            }

            RenderSystem.getTextureMatrix().get(TMP_TEX);
            pipeline.uniformState().setPerDrawMatrices(TMP_MV, TMP_PROJ, TMP_TEX);
            stagedMatUniform0 = null;
            stagedMatUniform1 = null;

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
                    RenderSystem.getShaderTexture(2));
        } catch (final Throwable ex) {
            activeChunkOffsetUniform = null;
            mgr.handleRuntimeRenderFailure(ex);
        }
    }

    public static void onShaderClear() {
        stagedMatUniform0 = null;
        stagedMatUniform1 = null;
        activeChunkOffsetUniform = null;
        final ShaderPipeline pipeline = Agris.pipeline().activePipelineOrNull();
        if (pipeline != null && pipeline.isInsideWorldFrame()) {
            pipeline.onVanillaShaderClear();
        }
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
