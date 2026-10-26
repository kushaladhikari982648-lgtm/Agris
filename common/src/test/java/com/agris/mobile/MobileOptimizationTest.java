package com.agris.mobile;

import com.agris.config.AgrisConfig;
import com.agris.gl.InternalTextureFormat;
import com.agris.gl.PackSamplerUsage;
import com.agris.pack.BuiltinShaderPacks;
import com.agris.pack.PackDirectives;
import com.agris.pack.ShaderPack;
import com.agris.pack.ShaderPackSource;
import com.agris.pipeline.UniformState;
import com.agris.util.MathUtil;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class MobileOptimizationTest {

    @Test
    public void detectsMobileTbdrSiliconAndAndroidLaunchersAccurately() {
        final MobileGpuArchitecture adrenoPojav = MobileGpuArchitecture.detectFromMap(
                Map.of("POJAV_RENDERER", "opengles3_mobileglues"),
                "Qualcomm",
                "Adreno (TM) 740",
                "OpenGL ES 3.2 V@0676.0",
                true);
        assertEquals(MobileGpuArchitecture.Silicon.ADRENO, adrenoPojav.silicon());
        assertEquals(MobileGpuArchitecture.Launcher.POJAV, adrenoPojav.launcher());
        assertTrue(adrenoPojav.isGlesNativeContext());
        assertTrue(adrenoPojav.isMobileOrTbdr());
        assertTrue(adrenoPojav.silicon().isTileBased());
        assertTrue(adrenoPojav.silicon().prefersPackedR11G11B10Hdr());

        final MobileGpuArchitecture maliZalith = MobileGpuArchitecture.detectFromMap(
                Map.of("ZALITH_VERSION_CODE", "140"),
                "ARM",
                "Immortalis-G715 MC11",
                "3.3.0 MobileGlues",
                true);
        assertEquals(MobileGpuArchitecture.Silicon.MALI, maliZalith.silicon());
        assertEquals(MobileGpuArchitecture.Launcher.ZALITH, maliZalith.launcher());
        assertEquals(1, maliZalith.silicon().maxZeroSpillMrt());

        final MobileGpuArchitecture fclPowerVr = MobileGpuArchitecture.detectFromMap(
                Map.of("FCL_NATIVEDIR", "/data/user/0/com.tungsten.fcl"),
                "Imagination Technologies",
                "PowerVR Rogue GE8320",
                "OpenGL ES 3.2",
                true);
        assertEquals(MobileGpuArchitecture.Silicon.POWERVR, fclPowerVr.silicon());
        assertEquals(MobileGpuArchitecture.Launcher.FOLD_CRAFT, fclPowerVr.launcher());

        final MobileGpuArchitecture appleM = MobileGpuArchitecture.detectFromMap(
                Map.of(),
                "Apple",
                "Apple M3 Pro",
                "4.1 Metal - 88",
                false);
        assertEquals(MobileGpuArchitecture.Silicon.APPLE_TBDR, appleM.silicon());
        assertTrue(appleM.isMobileOrTbdr());

        final MobileGpuArchitecture desktopNvidia = MobileGpuArchitecture.detectFromMap(
                Map.of(),
                "NVIDIA Corporation",
                "NVIDIA GeForce RTX 4070/PCIe/SSE2",
                "4.6.0 NVIDIA 560.94",
                false);
        assertEquals(MobileGpuArchitecture.Silicon.DESKTOP, desktopNvidia.silicon());
        assertEquals(MobileGpuArchitecture.Launcher.NONE, desktopNvidia.launcher());
        assertFalse(desktopNvidia.isMobileOrTbdr());
    }

    @Test
    public void frameGovernorSmoothlyScalesResolutionAndCadencesShadows() {
        final MobileFrameGovernor gov = new MobileFrameGovernor();
        gov.reset(85);

        long t = 1_000_000_000L;
        int scale = 85;
        // Feed slow 28 ms frames (~35 FPS) against a 60 FPS (16.67 ms) target with 2s spacing
        for (int i = 0; i < 8; i++) {
            t += 2_000_000_000L;
            scale = gov.recordFrameAndComputeScale(0.028f, t, true, 60, 60, 85);
        }
        assertTrue(scale < 85 && scale >= 60, "Governor must step down render scale under heavy GPU load, got " + scale);
        assertEquals(0, scale % 5, "Dynamic render scale must stay quantized to 5% increments");

        // During cooldown (< 1.5s), scale must NOT thrash
        final int lockedScale = gov.recordFrameAndComputeScale(0.030f, t + 200_000_000L, true, 60, 60, 85);
        assertEquals(scale, lockedScale, "Governor must honor resize cooldown to prevent FBO reallocation stutters");

        // Feed fast 8 ms frames -> governor recovers back up toward ceiling
        for (int i = 0; i < 20; i++) {
            t += 2_000_000_000L;
            scale = gov.recordFrameAndComputeScale(0.008f, t, true, 60, 60, 85);
        }
        assertEquals(85, scale, "Governor must recover scale smoothly once GPU headroom returns");

        // Verify half-rate shadow cadence (interval = 2) + instant override on sky jump or teleport
        gov.reset(80);
        assertTrue(gov.shouldRenderShadowThisFrame(2, true, 60, 0.25f, 0.0, 64.0, 0.0), "Frame 0 must render shadow");
        assertFalse(gov.shouldRenderShadowThisFrame(2, true, 60, 0.2501f, 0.5, 64.0, 0.2), "Frame 1 must reuse latched shadow");
        assertTrue(gov.shouldRenderShadowThisFrame(2, true, 60, 0.2502f, 1.0, 64.0, 0.4), "Frame 2 must refresh shadow");
        assertFalse(gov.shouldRenderShadowThisFrame(2, true, 60, 0.2503f, 1.2, 64.0, 0.5), "Frame 3 must reuse latched shadow");
        // /time set jump forces immediate refresh even on an off-beat frame!
        assertTrue(gov.shouldRenderShadowThisFrame(2, true, 60, 0.75f, 1.2, 64.0, 0.5), "Sky-angle jump must force shadow refresh");
    }

    @Test
    public void pocketUltraMobilePackHasZeroSpillAttachmentFootprintAndStandaloneZipLoads() throws Exception {
        final ShaderPack mobilePack = ShaderPack.load(
                BuiltinShaderPacks.byIdOrNull(BuiltinShaderPacks.ID_MOBILE), Map.of(), "en_us");
        assertNotNull(mobilePack);

        final List<String> sources = new ArrayList<>();
        for (final String path : mobilePack.source().allPaths()) {
            if (path.endsWith(".vsh") || path.endsWith(".fsh")) {
                sources.add(mobilePack.source().readText(path));
            }
        }
        final PackSamplerUsage usage = PackSamplerUsage.analyze(sources);
        assertFalse(usage.usesDepthtex1(), "(agris-mobile) must not require pre-translucent depthtex1 copy");
        assertFalse(usage.usesDepthtex2(), "(agris-mobile) must not require pre-hand depthtex2 copy");
        assertFalse(usage.usesShadowtex1(), "(agris-mobile) must not allocate shadowtex1");
        assertFalse(usage.usesShadowColor0(), "(agris-mobile) must use a pure depth-only shadow FBO");
        assertFalse(usage.usesShadowColor1(), "(agris-mobile) must not allocate shadowcolor1");

        // Verify 50% HDR bandwidth compaction (RGBA16F -> R11F_G11F_B10F when alpha is unreferenced)
        assertEquals(InternalTextureFormat.R11F_G11F_B10F,
                InternalTextureFormat.RGBA16F.mobileBandwidthCompact(false));
        assertEquals(InternalTextureFormat.RGBA16F,
                InternalTextureFormat.RGBA16F.mobileBandwidthCompact(true));

        // Verify standalone distributable shaderpacks/Agris-Pocket-Ultra-Mobile.zip loads cleanly
        final Path standaloneZip = Path.of("shaderpacks", "Agris-Pocket-Ultra-Mobile.zip");
        assertTrue(Files.isRegularFile(standaloneZip), "Standalone Agris-Pocket-Ultra-Mobile.zip must exist");
        final ShaderPack zipPack = ShaderPack.load(
                ShaderPackSource.fromZip(standaloneZip), Map.of("SHADOW_TAPS", "2"), "en_us");
        assertEquals("2", zipPack.activeOptionValues().get("SHADOW_TAPS"));
        assertTrue(zipPack.directives().profiles().containsKey("Mobile_Balanced"));
        assertTrue(zipPack.directives().profiles().containsKey("Pojav_GL4ES"));
        assertNotNull(zipPack.resolveProgram("gbuffers_terrain", "minecraft:overworld"));
        assertNotNull(zipPack.resolveProgram("final", "minecraft:overworld"));

        // Verify MobilePreset one-tap configuration
        final AgrisConfig cfg = AgrisConfig.createDefaults();
        MobilePreset.BATTERY_SAVER.applyTo(cfg, MobileGpuArchitecture.detectFromMap(Map.of(), "ARM", "Mali-G57", "OpenGL ES 3.2", true));
        assertEquals(BuiltinShaderPacks.ID_MOBILE, cfg.shaderPack.get());
        assertFalse(cfg.shadowEnabled.get());
        assertEquals(65, (int) cfg.renderScale.get());
    }

    @Test
    public void worldLockedShadowMatrixLatchingEliminatesShadowSwimmingOnSkippedFrames() {
        final UniformState u = new UniformState();
        final PackDirectives dir = new PackDirectives();
        final float[] mv = MathUtil.identity4(new float[16]);
        final float[] proj = MathUtil.identity4(new float[16]);

        // Frame 1: camera at (100.0, 65.0, -40.0), shadow map IS rendered (renderShadowThisFrame = true)
        final double cam1X = 100.0, cam1Y = 65.0, cam1Z = -40.0;
        u.updateWorldMatricesAndState(mv, proj, cam1X, cam1Y, cam1Z, 0.22f, dir, 96.0f, true);

        // Fixed world-space block corner at W = (112.5, 68.0, -33.25)
        final double wx = 112.5, wy = 68.0, wz = -33.25;
        final float[] clipFrame1 = projectPlayerPointToShadowClip(
                u, (float) (wx - cam1X), (float) (wy - cam1Y), (float) (wz - cam1Z));

        // Frame 2: player sprints + jumps to (104.25, 66.5, -31.25), shadow pass is SKIPPED (renderShadowThisFrame = false)
        final double cam2X = 104.25, cam2Y = 66.5, cam2Z = -31.25;
        u.updateWorldMatricesAndState(mv, proj, cam2X, cam2Y, cam2Z, 0.2202f, dir, 96.0f, false);

        final float[] clipFrame2 = projectPlayerPointToShadowClip(
                u, (float) (wx - cam2X), (float) (wy - cam2Y), (float) (wz - cam2Z));

        assertEquals(clipFrame1[0], clipFrame2[0], 1.0e-5f, "Latched shadow X must stay world-locked when player moves");
        assertEquals(clipFrame1[1], clipFrame2[1], 1.0e-5f, "Latched shadow Y must stay world-locked when player moves");
        assertEquals(clipFrame1[2], clipFrame2[2], 1.0e-5f, "Latched shadow Z must stay world-locked when player moves");
    }

    private static float[] projectPlayerPointToShadowClip(final UniformState u,
                                                          final float px, final float py, final float pz) {
        final float[] m = u.shadowModelView;
        final float vx = m[0] * px + m[4] * py + m[8] * pz + m[12];
        final float vy = m[1] * px + m[5] * py + m[9] * pz + m[13];
        final float vz = m[2] * px + m[6] * py + m[10] * pz + m[14];
        final float[] p = u.shadowProjection;
        return new float[]{
                p[0] * vx + p[4] * vy + p[8] * vz + p[12],
                p[1] * vx + p[5] * vy + p[9] * vz + p[13],
                p[2] * vx + p[6] * vy + p[10] * vz + p[14]
        };
    }
}
