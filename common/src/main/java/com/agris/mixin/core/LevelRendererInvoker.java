package com.agris.mixin.core;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes {@code LevelRenderer.renderChunkLayer} on Minecraft 1.20.1 so Agris's
 * directional shadow pass can draw chunk terrain layers using the orthographic
 * sun/moon shadow {@link PoseStack} and projection matrix.
 */
@Mixin(LevelRenderer.class)
public interface LevelRendererInvoker {
    @Invoker("renderChunkLayer")
    void agris$invokeRenderChunkLayer(RenderType renderType,
                                      PoseStack poseStack,
                                      double camX, double camY, double camZ,
                                      Matrix4f projection);
}
