package com.agris.fabric;

import com.agris.gui.AgrisShaderPackScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Optional Mod Menu integration opening {@link AgrisShaderPackScreen}.
 */
public final class AgrisModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return AgrisShaderPackScreen::new;
    }
}
