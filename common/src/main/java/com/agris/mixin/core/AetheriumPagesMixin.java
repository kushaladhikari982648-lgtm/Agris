package com.agris.mixin.core;

import com.agris.Agris;
import com.agris.client.AgrisClientHooks;
import com.agris.compat.AetheriumTabInjector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Injects Agris's native <b>Shaders</b> {@code com.aetherium.gui.Page} directly into
 * Aetherium {@code 1.x}'s {@code AetheriumPages.build()} sidebar.
 *
 * <p>Marked {@link Pseudo} and strictly gated by {@code AgrisMixinPlugin} so it is
 * loaded ONLY when Aetherium {@code 1.x} is present in the runtime classpath.</p>
 */
@Pseudo
@Mixin(targets = "com.aetherium.client.AetheriumPages", remap = false)
public abstract class AetheriumPagesMixin {
    @Inject(method = "build", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void agris$appendShadersTab(final CallbackInfoReturnable<List<?>> cir) {
        AgrisClientHooks.ensureClientWired();
        final List<?> updated = AetheriumTabInjector.appendShadersPage(
                cir.getReturnValue(),
                AgrisClientHooks::openShaderPackScreenFromCurrent,
                AgrisClientHooks::openShaderOptionsScreenFromCurrent,
                AgrisClientHooks::reloadShadersAndChunks,
                Agris::describePipelineShort);
        if (updated != null && updated != cir.getReturnValue()) {
            cir.setReturnValue(updated);
        }
    }
}
