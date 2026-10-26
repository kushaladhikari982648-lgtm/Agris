package com.agris.mixin.core;

import com.agris.client.AgrisClientHooks;
import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts {@code ShaderInstance} draw setup on Minecraft 1.20.1 during active
 * world/shadow rendering so Agris's compiled {@code gbuffers_*} / {@code shadow}
 * GL program takes over seamlessly after vanilla binds texture units 0..2, and
 * restores cleanly in {@code clear()}.
 */
@Mixin(ShaderInstance.class)
public abstract class ShaderInstanceMixin {
    @Inject(method = "apply()V", at = @At("RETURN"))
    private void agris$afterApply(final CallbackInfo ci) {
        AgrisClientHooks.onShaderApply((ShaderInstance) (Object) this);
    }

    @Inject(method = "clear()V", at = @At("HEAD"))
    private void agris$beforeClear(final CallbackInfo ci) {
        AgrisClientHooks.onShaderClear();
    }
}
