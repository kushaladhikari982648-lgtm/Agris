package com.agris.client;

import com.agris.Agris;
import com.agris.gui.AgrisShaderPackScreen;
import com.agris.mixin.core.KeyMappingAccessor;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.Map;

/**
 * Registers and polls Agris's three standard shader keybindings (matching Iris defaults):
 * <ul>
 *   <li>{@code O} — Open Shader Pack Manager screen</li>
 *   <li>{@code R} — Reload active shader pipeline</li>
 *   <li>{@code K} — Toggle shaders ON / OFF live</li>
 * </ul>
 */
public final class AgrisKeybinds {
    public static final String CATEGORY = "Agris Shaders";

    private static KeyMapping openPacksKey;
    private static KeyMapping reloadKey;
    private static KeyMapping toggleKey;
    private static boolean initialized;

    private AgrisKeybinds() {
    }

    private static void ensureCreated() {
        if (initialized) {
            return;
        }
        initialized = true;
        try {
            final Map<String, Integer> order = KeyMappingAccessor.agris$categorySortOrder();
            if (order != null && !order.containsKey(CATEGORY)) {
                int max = 0;
                for (final Integer v : order.values()) {
                    if (v != null && v > max) {
                        max = v;
                    }
                }
                order.put(CATEGORY, max + 1);
            }
        } catch (final Throwable ignored) {
            // Safe fallback if category sort map is immutable on a modded client.
        }
        openPacksKey = new KeyMapping("key.agris.shaderPackSelection", GLFW.GLFW_KEY_O, CATEGORY);
        reloadKey = new KeyMapping("key.agris.reloadShaders", GLFW.GLFW_KEY_R, CATEGORY);
        toggleKey = new KeyMapping("key.agris.toggleShaders", GLFW.GLFW_KEY_K, CATEGORY);
    }

    public static KeyMapping[] appendTo(final KeyMapping[] existing) {
        ensureCreated();
        if (existing == null) {
            return new KeyMapping[]{openPacksKey, reloadKey, toggleKey};
        }
        for (final KeyMapping km : existing) {
            if (km == openPacksKey) {
                return existing;
            }
        }
        final KeyMapping[] merged = new KeyMapping[existing.length + 3];
        System.arraycopy(existing, 0, merged, 0, existing.length);
        merged[existing.length] = openPacksKey;
        merged[existing.length + 1] = reloadKey;
        merged[existing.length + 2] = toggleKey;
        return merged;
    }

    public static void pollClientTick(final Minecraft mc) {
        if (!initialized || mc == null) {
            return;
        }
        while (openPacksKey.consumeClick()) {
            if (mc.screen == null) {
                mc.setScreen(new AgrisShaderPackScreen(null));
            }
        }
        while (reloadKey.consumeClick()) {
            Agris.pipeline().requestReload();
            if (mc.levelRenderer != null) {
                mc.levelRenderer.allChanged();
            }
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal("[Agris] Reloading shader pipeline..."), true);
            }
        }
        while (toggleKey.consumeClick()) {
            final boolean next = !Agris.config().enabled.get();
            Agris.config().enabled.set(next);
            Agris.saveConfigAndReload(() -> {
                if (mc.levelRenderer != null) {
                    mc.levelRenderer.allChanged();
                }
            });
            if (mc.player != null) {
                mc.player.displayClientMessage(
                        Component.literal("[Agris] Shaders: " + (next ? "ON (" + Agris.config().shaderPack.get() + ")" : "OFF")),
                        true);
            }
        }
    }
}
