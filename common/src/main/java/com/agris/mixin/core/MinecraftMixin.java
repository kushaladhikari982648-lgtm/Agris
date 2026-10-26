package com.agris.mixin.core;

import com.agris.Agris;
import com.agris.client.AgrisClientHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks client tick keybind polling, world/dimension transitions, and clean shutdown
 * on Minecraft 1.20.1.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "tick()V", at = @At("RETURN"))
    private void agris$onClientTick(final CallbackInfo ci) {
        AgrisClientHooks.onClientTick((Minecraft) (Object) this);
    }

    @Inject(
            method = "setLevel(Lnet/minecraft/client/multiplayer/ClientLevel;)V",
            at = @At("HEAD")
    )
    private void agris$onSetLevel(final ClientLevel level, final CallbackInfo ci) {
        AgrisClientHooks.onWorldChanged(level);
    }

    @Inject(method = "close()V", at = @At("HEAD"))
    private void agris$onShutdown(final CallbackInfo ci) {
        Agris.shutdown();
    }
}
