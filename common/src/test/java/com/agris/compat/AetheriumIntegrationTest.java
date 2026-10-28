package com.agris.compat;

import com.agris.Agris;
import com.agris.config.AgrisConfig;
import com.agris.platform.PlatformAdapter;
import com.agris.platform.PlatformServices;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class AetheriumIntegrationTest {

    @Test
    public void conflictScannerAllowsAetheriumAndBlocksDuplicateShaderOwners() {
        final PlatformAdapter cleanWithAetherium = stubPlatform(
                Set.of("agris", "aetherium", "distanthorizons"),
                Set.of("aetherium", "distanthorizons"));
        final ShaderConflictScanner.ScanReport r1 = ShaderConflictScanner.scan(cleanWithAetherium);
        assertFalse(r1.blocksPipeline());
        assertEquals(2, r1.findings().size());

        // Sodium replaces chunk terrain rendering, so Agris stands down cleanly via DELEGATE
        final PlatformAdapter withSodium = stubPlatform(Set.of("agris", "sodium"), Set.of("sodium"));
        assertTrue(ShaderConflictScanner.scan(withSodium).blocksPipeline());

        // Agris provides "iris" in fabric.mod.json, so isModLoaded("iris") == true while isExternalModLoaded("iris") == false
        final PlatformAdapter selfProvidesIris = stubPlatform(Set.of("agris", "iris", "aetherium"), Set.of("aetherium"));
        assertFalse(ShaderConflictScanner.scan(selfProvidesIris).blocksPipeline(),
                "Agris's own provides:[\"iris\"] must never self-conflict");

        // External standalone Iris or VulkanMod must trigger HARD stand-down
        final PlatformAdapter standaloneIris = stubPlatform(Set.of("agris", "iris"), Set.of("iris"));
        assertTrue(ShaderConflictScanner.scan(standaloneIris).blocksPipeline());

        final PlatformAdapter vulkan = stubPlatform(Set.of("agris", "vulkanmod"), Set.of("vulkanmod"));
        assertTrue(ShaderConflictScanner.scan(vulkan).blocksPipeline());
    }

    @Test
    public void aetheriumBridgeCoordinatesColorFilterSectionCullAndTabInjection() throws Exception {
        final Path gameDir = Files.createTempDirectory("agris-aetherium-bridge");
        PlatformServices.install(new PlatformAdapter() {
            @Override
            public String platformName() {
                return "fabric";
            }

            @Override
            public String minecraftVersion() {
                return "1.20.1";
            }

            @Override
            public Path gameDirectory() {
                return gameDir;
            }

            @Override
            public Path configDirectory() {
                return gameDir.resolve("config");
            }

            @Override
            public boolean isClient() {
                return true;
            }

            @Override
            public boolean isDevelopmentEnvironment() {
                return true;
            }

            @Override
            public boolean isModLoaded(final String modId) {
                return "agris".equals(modId) || "aetherium".equals(modId);
            }

            @Override
            public String getModVersion(final String modId) {
                return "aetherium".equals(modId) ? "1.17.0" : "1.0.0";
            }
        });
        Agris.initialize();

        // Set mock Aetherium config to VIBRANT before Agris engages shaders
        com.aetherium.Aetherium.CONFIG.shaderPreset.set(com.aetherium.config.AetheriumConfig.ShaderPreset.VIBRANT);
        com.aetherium.perf.Tuning.shaderPreset = 1;
        com.aetherium.perf.Tuning.directionalCulling = true;
        com.aetherium.perf.Tuning.glStateCache = true;

        final AetheriumBridge bridge = new AetheriumBridge(AetheriumIntegrationTest.class.getClassLoader());
        bridge.probe();
        assertTrue(bridge.isPresent());
        assertTrue(bridge.isModernAetherium());
        assertTrue(bridge.isAetheriumActive());
        assertEquals(85, bridge.getAetheriumRenderScalePercent());

        final AgrisConfig cfg = Agris.config();
        // Engaging Agris shaders must pause Aetherium's built-in ColorFilter (VIBRANT -> OFF) & disable glStateCache
        bridge.syncShaderPackActive(true, cfg);
        assertEquals(com.aetherium.config.AetheriumConfig.ShaderPreset.OFF,
                com.aetherium.Aetherium.CONFIG.shaderPreset.get());
        assertEquals(0, com.aetherium.perf.Tuning.shaderPreset);
        assertFalse(com.aetherium.perf.Tuning.glStateCache);
        assertTrue(bridge.isColorFilterDeferred());

        // Shadow pass hook must disarm Aetherium's SectionCull and directionalCulling, then restore after shadow pass
        com.aetherium.client.SectionCull.endCalls = 0;
        bridge.beginShadowPass(cfg);
        assertEquals(1, com.aetherium.client.SectionCull.endCalls);
        assertFalse(com.aetherium.perf.Tuning.directionalCulling);
        bridge.endShadowPass();
        assertTrue(com.aetherium.perf.Tuning.directionalCulling);

        // Turning Agris shaders OFF must restore Aetherium's previous VIBRANT preset
        bridge.syncShaderPackActive(false, cfg);
        assertEquals(com.aetherium.config.AetheriumConfig.ShaderPreset.VIBRANT,
                com.aetherium.Aetherium.CONFIG.shaderPreset.get());
        assertEquals(1, com.aetherium.perf.Tuning.shaderPreset);

        // Verify AetheriumTabInjector appends one "Shaders" Page right before "About" and stays idempotent
        final List<com.aetherium.gui.Page> initialPages = new ArrayList<>();
        initialPages.add(new com.aetherium.gui.Page("General", "General", "General", com.aetherium.gui.PixelArt.SPARKLES));
        initialPages.add(new com.aetherium.gui.Page("About", "About", "About", com.aetherium.gui.PixelArt.SPARKLES));

        final AtomicInteger reloads = new AtomicInteger();
        final List<?> afterFirst = AetheriumTabInjector.appendShadersPage(
                initialPages, () -> {}, () -> {}, reloads::incrementAndGet, () -> "ACTIVE: (agris-aetherial)");
        assertEquals(3, afterFirst.size());
        final com.aetherium.gui.Page shadersPage = (com.aetherium.gui.Page) afterFirst.get(1);
        assertEquals("Shaders", shadersPage.name);
        assertTrue(shadersPage.settings.size() >= 8, "Shaders page must expose rich controls");

        final List<?> afterSecond = AetheriumTabInjector.appendShadersPage(
                afterFirst, () -> {}, () -> {}, reloads::incrementAndGet, () -> "ACTIVE: (agris-aetherial)");
        assertEquals(3, afterSecond.size(), "appendShadersPage must be idempotent");
    }

    private static PlatformAdapter stubPlatform(final Set<String> loaded, final Set<String> external) {
        return new PlatformAdapter() {
            @Override
            public String platformName() {
                return "fabric";
            }

            @Override
            public String minecraftVersion() {
                return "1.20.1";
            }

            @Override
            public Path gameDirectory() {
                return Path.of(".");
            }

            @Override
            public Path configDirectory() {
                return Path.of("config");
            }

            @Override
            public boolean isClient() {
                return true;
            }

            @Override
            public boolean isDevelopmentEnvironment() {
                return true;
            }

            @Override
            public boolean isModLoaded(final String modId) {
                return loaded.contains(modId);
            }

            @Override
            public boolean isExternalModLoaded(final String modId) {
                return external.contains(modId);
            }

            @Override
            public String getModVersion(final String modId) {
                return "1.0.0";
            }
        };
    }
}
