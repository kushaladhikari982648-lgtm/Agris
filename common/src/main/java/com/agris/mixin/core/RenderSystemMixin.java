package com.agris.mixin.core;

import com.agris.client.AgrisClientHooks;
import com.mojang.blaze3d.systems.RenderSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Captures OpenGL context initialization so {@code GlLimits} and {@code AndroidGlProfile}
 * probe hardware capabilities as soon as Blaze3D finishes creating the GL context.
 */
@Mixin(RenderSystem.class)
public abstract class RenderSystemMixin {
    @Inject(method = "initRenderer(IZ)V", at = @At("RETURN"), remap = false)
    private static void agris$onInitRenderer(final int debugVerbosity,
                                             final boolean synchronous,
                                             final CallbackInfo ci) {
        AgrisClientHooks.onRenderSystemInit();
    }
}
