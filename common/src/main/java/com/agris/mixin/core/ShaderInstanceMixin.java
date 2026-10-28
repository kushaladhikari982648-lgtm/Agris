package com.agris.mixin.core;

import com.agris.client.AgrisClientHooks;
import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts {@code ShaderInstance} draw setup on Minecraft 1.20.1 during active
 * world/shadow rendering so Agris's compiled {@code gbuffers_*} / {@code shadow}
 * GL program takes over seamlessly after vanilla binds texture units 0..2, and
 * invalidates {@code lastProgramId} / {@code lastAppliedShader} so Blaze3D never
 * skips program binding on subsequent vanilla draws.
 */
@Mixin(ShaderInstance.class)
public abstract class ShaderInstanceMixin {
    @Shadow
    private static ShaderInstance lastAppliedShader;

    @Shadow
    private static int lastProgramId;

    @Inject(method = "apply()V", at = @At("HEAD"))
    private void agris$beforeApply(final CallbackInfo ci) {
        AgrisClientHooks.onBeforeShaderApply();
    }

    @Inject(method = "apply()V", at = @At("RETURN"))
    private void agris$afterApply(final CallbackInfo ci) {
        AgrisClientHooks.onShaderApply((ShaderInstance) (Object) this);
        if (AgrisClientHooks.isOverridingShaderProgram()) {
            lastAppliedShader = null;
            lastProgramId = -1;
        }
    }

    @Inject(method = "clear()V", at = @At("HEAD"))
    private void agris$beforeClear(final CallbackInfo ci) {
        AgrisClientHooks.onShaderClear();
    }
}
