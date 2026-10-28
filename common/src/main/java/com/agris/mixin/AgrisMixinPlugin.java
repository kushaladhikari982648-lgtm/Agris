package com.agris.mixin;

import com.agris.compat.ShaderConflictScanner;
import com.agris.platform.PlatformAdapter;
import com.agris.platform.PlatformServices;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Guards every Agris Mixin before class transformation:
 * <ul>
 *   <li>Skips all mixins on dedicated servers or when a blocking rendering/shader mod
 *       ({@code iris}, {@code oculus}, {@code optifine}, {@code vulkanmod}, {@code sodium},
 *       {@code embeddium}) is detected.</li>
 *   <li>Applies {@code AetheriumPagesMixin} strictly when Aetherium {@code 1.x}'s
 *       {@code com.aetherium.client.AetheriumPages} class exists on the classpath.</li>
 * </ul>
 */
public final class AgrisMixinPlugin implements IMixinConfigPlugin {
    private static final AtomicInteger APPLIED_COUNT = new AtomicInteger();

    private boolean allowMixins = true;
    private boolean aetheriumPagesPresent;

    @Override
    public void onLoad(final String mixinPackage) {
        final PlatformAdapter platform = PlatformServices.get();
        if (!platform.isClient()) {
            this.allowMixins = false;
            return;
        }
        final ShaderConflictScanner.ScanReport report = ShaderConflictScanner.scan(platform);
        if (report.blocksPipeline()) {
            this.allowMixins = false;
            return;
        }
        final ClassLoader cl = AgrisMixinPlugin.class.getClassLoader();
        this.aetheriumPagesPresent = cl != null
                && cl.getResource("com/aetherium/client/AetheriumPages.class") != null;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(final String targetClassName, final String mixinClassName) {
        if (!this.allowMixins) {
            return false;
        }
        if (mixinClassName.endsWith(".AetheriumPagesMixin")) {
            return this.aetheriumPagesPresent;
        }
        return true;
    }

    @Override
    public void acceptTargets(final Set<String> myTargets, final Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(final String targetClassName, final ClassNode targetClass,
                         final String mixinClassName, final IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(final String targetClassName, final ClassNode targetClass,
                          final String mixinClassName, final IMixinInfo mixinInfo) {
        APPLIED_COUNT.incrementAndGet();
    }

    public static int appliedMixinCount() {
        return APPLIED_COUNT.get();
    }
}
