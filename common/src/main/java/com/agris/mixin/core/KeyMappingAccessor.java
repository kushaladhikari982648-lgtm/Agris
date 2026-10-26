package com.agris.mixin.core;

import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/**
 * Exposes {@code KeyMapping.CATEGORY_SORT_ORDER} so Agris's keybindings appear under
 * their own clean "Agris Shaders" category in Controls without requiring Fabric API.
 */
@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {
    @Accessor("CATEGORY_SORT_ORDER")
    static Map<String, Integer> agris$categorySortOrder() {
        throw new AssertionError("Mixin not applied");
    }
}
