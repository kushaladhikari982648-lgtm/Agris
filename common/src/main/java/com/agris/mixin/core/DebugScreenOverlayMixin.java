package com.agris.mixin.core;

import com.agris.client.AgrisClientHooks;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Appends Agris's active shader pack, shadow resolution, and Aetherium bridge line
 * to the right-hand system column of the F3 debug screen.
 */
@Mixin(DebugScreenOverlay.class)
public abstract class DebugScreenOverlayMixin {
    @Inject(method = "getSystemInformation()Ljava/util/List;", at = @At("RETURN"))
    private void agris$appendSystemDebug(final CallbackInfoReturnable<List<String>> cir) {
        AgrisClientHooks.appendDebugOverlayLines(cir.getReturnValue());
    }
}
