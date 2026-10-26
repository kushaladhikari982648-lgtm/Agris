package com.agris.mixin.core;

import com.agris.client.AgrisClientHooks;
import com.mojang.blaze3d.shaders.Uniform;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Captures per-draw {@code MODEL_VIEW_MATRIX} / {@code PROJECTION_MATRIX} updates and
 * redirects per-section {@code CHUNK_OFFSET.set(dx, dy, dz)} + {@code CHUNK_OFFSET.upload()}
 * inside {@code LevelRenderer.renderChunkLayer} directly onto the active Agris
 * {@code gbuffers_*} / {@code shadow} GL program on Minecraft 1.20.1.
 */
@Mixin(Uniform.class)
public abstract class UniformMixin {
    @Inject(method = "set(Lorg/joml/Matrix4f;)V", at = @At("HEAD"))
    private void agris$onSetMat4(final Matrix4f matrix, final CallbackInfo ci) {
        AgrisClientHooks.onUniformSetMat4((Uniform) (Object) this, matrix);
    }

    @Inject(method = "set(FFF)V", at = @At("HEAD"))
    private void agris$onSet3f(final float x, final float y, final float z, final CallbackInfo ci) {
        AgrisClientHooks.onUniformSet3f((Uniform) (Object) this, x, y, z);
    }

    @Inject(method = "upload()V", at = @At("HEAD"), cancellable = true)
    private void agris$onUpload(final CallbackInfo ci) {
        if (AgrisClientHooks.isOverridingShaderProgram()) {
            AgrisClientHooks.onCancelledUniformUpload((Uniform) (Object) this);
            ci.cancel();
        }
    }
}
