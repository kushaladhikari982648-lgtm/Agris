package com.agris.mixin.core;

import com.agris.gui.AgrisShaderPackScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.VideoSettingsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds a "Shader Packs..." button to vanilla's Video Settings screen on Minecraft 1.20.1
 * so Agris is always reachable from Options -&gt; Video Settings even when Aetherium's
 * custom screen is disabled or absent.
 */
@Mixin(VideoSettingsScreen.class)
public abstract class VideoSettingsScreenMixin extends Screen {
    protected VideoSettingsScreenMixin(final Component title) {
        super(title);
    }

    @Inject(method = "init()V", at = @At("RETURN"), require = 0)
    private void agris$addShaderPacksButton(final CallbackInfo ci) {
        this.addRenderableWidget(
                Button.builder(Component.literal("Shader Packs..."), btn -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new AgrisShaderPackScreen(this));
                    }
                }).bounds(Math.max(8, this.width - 116), 6, 108, 20).build()
        );
    }
}
