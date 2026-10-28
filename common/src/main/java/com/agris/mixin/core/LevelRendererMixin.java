package com.agris.mixin.core;

import com.agris.client.AgrisClientHooks;
import com.agris.pipeline.WorldRenderingPhase;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Orchestrates Agris's world shader pipeline across {@code LevelRenderer.renderLevel}
 * on Fabric 1.20.1: frame setup, mobile dynamic resolution &amp; interleaved shadow pass,
 * sky/cloud/terrain/entity/weather phase tracking, pre-translucent {@code depthtex1} +
 * {@code deferred*} passes, and {@code composite*} + {@code final} presentation.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Inject(
            method = "renderLevel(Lcom/mojang/blaze3d/vertex/PoseStack;FJZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;)V",
            at = @At("HEAD")
    )
    private void agris$beginLevelRender(final PoseStack poseStack,
                                        final float partialTick,
                                        final long finishNanoTime,
                                        final boolean renderBlockOutline,
                                        final Camera camera,
                                        final GameRenderer gameRenderer,
                                        final LightTexture lightTexture,
                                        final Matrix4f projection,
                                        final CallbackInfo ci) {
        AgrisClientHooks.onBeginLevelRender(
                (LevelRenderer) (Object) this, camera, partialTick, lightTexture, poseStack.last().pose(), projection);
    }

    @Inject(
            method = "renderLevel(Lcom/mojang/blaze3d/vertex/PoseStack;FJZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;)V",
            at = @At("RETURN")
    )
    private void agris$endLevelRender(final PoseStack poseStack,
                                      final float partialTick,
                                      final long finishNanoTime,
                                      final boolean renderBlockOutline,
                                      final Camera camera,
                                      final GameRenderer gameRenderer,
                                      final LightTexture lightTexture,
                                      final Matrix4f projection,
                                      final CallbackInfo ci) {
        AgrisClientHooks.onEndLevelRender();
    }

    @Inject(
            method = "renderSky(Lcom/mojang/blaze3d/vertex/PoseStack;Lorg/joml/Matrix4f;FLnet/minecraft/client/Camera;ZLjava/lang/Runnable;)V",
            at = @At("HEAD")
    )
    private void agris$beginSky(final PoseStack poseStack, final Matrix4f projection,
                                final float partialTick, final Camera camera,
                                final boolean isFoggy, final Runnable setupFog,
                                final CallbackInfo ci) {
        AgrisClientHooks.onPhaseChange(WorldRenderingPhase.SKY);
    }

    @Inject(
            method = "renderSky(Lcom/mojang/blaze3d/vertex/PoseStack;Lorg/joml/Matrix4f;FLnet/minecraft/client/Camera;ZLjava/lang/Runnable;)V",
            at = @At("RETURN")
    )
    private void agris$endSky(final PoseStack poseStack, final Matrix4f projection,
                              final float partialTick, final Camera camera,
                              final boolean isFoggy, final Runnable setupFog,
                              final CallbackInfo ci) {
        AgrisClientHooks.onPhaseChange(WorldRenderingPhase.NONE);
    }

    @Inject(
            method = "renderChunkLayer(Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/PoseStack;DDDLorg/joml/Matrix4f;)V",
            at = @At("HEAD")
    )
    private void agris$beginChunkLayer(final RenderType renderType,
                                       final PoseStack poseStack,
                                       final double camX, final double camY, final double camZ,
                                       final Matrix4f projection,
                                       final CallbackInfo ci) {
        AgrisClientHooks.onSectionLayerBegin(renderType);
    }

    @Inject(
            method = "renderEntity(Lnet/minecraft/world/entity/Entity;DDDFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V",
            at = @At("HEAD")
    )
    private void agris$beginEntity(final Entity entity,
                                   final double camX, final double camY, final double camZ,
                                   final float partialTick, final PoseStack poseStack,
                                   final MultiBufferSource bufferSource,
                                   final CallbackInfo ci) {
        AgrisClientHooks.onPhaseChange(WorldRenderingPhase.ENTITIES);
    }

    @Inject(
            method = "renderClouds(Lcom/mojang/blaze3d/vertex/PoseStack;Lorg/joml/Matrix4f;FDDD)V",
            at = @At("HEAD")
    )
    private void agris$beginClouds(final PoseStack poseStack,
                                   final Matrix4f projection,
                                   final float partialTick,
                                   final double camX, final double camY, final double camZ,
                                   final CallbackInfo ci) {
        AgrisClientHooks.onPhaseChange(WorldRenderingPhase.CLOUDS);
    }

    @Inject(
            method = "renderSnowAndRain(Lnet/minecraft/client/renderer/LightTexture;FDDD)V",
            at = @At("HEAD")
    )
    private void agris$beginWeather(final LightTexture lightTexture, final float partialTick,
                                    final double camX, final double camY, final double camZ,
                                    final CallbackInfo ci) {
        AgrisClientHooks.onPhaseChange(WorldRenderingPhase.RAIN_SNOW);
    }

    @Inject(
            method = "renderWorldBorder(Lnet/minecraft/client/Camera;)V",
            at = @At("HEAD")
    )
    private void agris$beginWorldBorder(final Camera camera, final CallbackInfo ci) {
        AgrisClientHooks.onPhaseChange(WorldRenderingPhase.WORLD_BORDER);
    }
}
