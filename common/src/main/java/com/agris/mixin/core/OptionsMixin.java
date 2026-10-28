package com.agris.mixin.core;

import com.agris.client.AgrisKeybinds;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Appends Agris's three shader keybindings ({@code O}, {@code R}, {@code K}) to
 * {@code Options.keyMappings} right before {@code options.txt} is parsed.
 */
@Mixin(Options.class)
public abstract class OptionsMixin {
    @Shadow
    @Final
    @Mutable
    public KeyMapping[] keyMappings;

    @Inject(method = "load()V", at = @At("HEAD"), require = 0)
    private void agris$registerKeys(final CallbackInfo ci) {
        this.keyMappings = AgrisKeybinds.appendTo(this.keyMappings);
    }
}
