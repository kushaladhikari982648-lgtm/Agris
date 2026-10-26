package com.agris.mixin.core;

import com.agris.client.AgrisClientHooks;
import com.agris.pipeline.WorldRenderingPhase;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tracks first-person hand rendering inside {@code GameRenderer.renderLevel} on
 * Minecraft 1.20.1 so {@code depthtex2} captures pre-hand scene depth (when used)
 * and {@code gbuffers_hand} binds cleanly.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(
            method = "renderItemInHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/Camera;F)V",
            at = @At("HEAD")
    )
    private void agris$beginHand(final PoseStack poseStack, final Camera camera,
                                 final float partialTick, final CallbackInfo ci) {
        AgrisClientHooks.onBeginHandRender();
    }

    @Inject(
            method = "renderItemInHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/Camera;F)V",
            at = @At("RETURN")
    )
    private void agris$endHand(final PoseStack poseStack, final Camera camera,
                               final float partialTick, final CallbackInfo ci) {
        AgrisClientHooks.onPhaseChange(WorldRenderingPhase.NONE);
    }
}
