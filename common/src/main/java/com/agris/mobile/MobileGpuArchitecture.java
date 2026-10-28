package com.agris.mobile;

import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Classifies mobile Tile-Based Deferred Rendering (TBDR) GPU microarchitectures
 * (Qualcomm Adreno, ARM Mali / Immortalis, Imagination PowerVR, Samsung Xclipse,
 * Apple Silicon TBDR, NVIDIA Tegra) and Android Minecraft launchers
 * (PojavLauncher, Zalith, Fold Craft, Amethyst, MojoLauncher).
 *
 * <p>Mobile GPUs differ fundamentally from desktop immediate-mode GPUs: on-chip tile
 * SRAM ({@code GMEM} on Adreno, {@code Tile Buffer} on Mali) is blazingly fast while
 * off-chip LPDDR4X/LPDDR5 bandwidth is tightly constrained and thermally expensive.
 * Knowing the exact microarchitecture lets Agris size MRT attachments, packed float
 * formats ({@code R11F_G11F_B10F}), shadow cadence, and tile-discard hints accurately.</p>
 */
public final class MobileGpuArchitecture {

    public enum Silicon {
        ADRENO("Adreno TBDR", true, 2, true, 85, 8, 2),
        MALI("ARM Mali / Immortalis TBDR", true, 1, true, 80, 6, 2),
        POWERVR("PowerVR TBDR", true, 1, false, 75, 6, 2),
        XCLIPSE("Samsung Xclipse Mobile RDNA", true, 2, true, 85, 8, 2),
        APPLE_TBDR("Apple Silicon TBDR", true, 4, true, 90, 10, 1),
        TEGRA("NVIDIA Tegra Mobile", false, 4, true, 85, 8, 2),
        GENERIC_MOBILE("Mobile GPU (TBDR)", true, 1, false, 75, 6, 2),
        DESKTOP("Desktop Immediate-Mode GPU", false, 8, false, 100, 12, 1);

        private final String displayName;
        private final boolean tileBased;
        private final int maxZeroSpillMrt;
        private final boolean prefersPackedR11G11B10Hdr;
        private final int recommendedRenderScalePercent;
        private final int recommendedShadowChunks;
        private final int recommendedShadowCadence;

        Silicon(final String displayName,
                final boolean tileBased,
                final int maxZeroSpillMrt,
                final boolean prefersPackedR11G11B10Hdr,
                final int recommendedRenderScalePercent,
                final int recommendedShadowChunks,
                final int recommendedShadowCadence) {
            this.displayName = displayName;
            this.tileBased = tileBased;
            this.maxZeroSpillMrt = maxZeroSpillMrt;
            this.prefersPackedR11G11B10Hdr = prefersPackedR11G11B10Hdr;
            this.recommendedRenderScalePercent = recommendedRenderScalePercent;
            this.recommendedShadowChunks = recommendedShadowChunks;
            this.recommendedShadowCadence = recommendedShadowCadence;
        }

        public String displayName() {
            return this.displayName;
        }

        public boolean isTileBased() {
            return this.tileBased;
        }

        /** Maximum MRT color attachments before tile SRAM typically spills to system RAM. */
        public int maxZeroSpillMrt() {
            return this.maxZeroSpillMrt;
        }

        /** True when 32-bit {@code R11F_G11F_B10F} is preferred over 64-bit {@code RGBA16F} for HDR buffers. */
        public boolean prefersPackedR11G11B10Hdr() {
            return this.prefersPackedR11G11B10Hdr;
        }

        public int recommendedRenderScalePercent() {
            return this.recommendedRenderScalePercent;
        }

        public int recommendedShadowChunks() {
            return this.recommendedShadowChunks;
        }

        public int recommendedShadowCadence() {
            return this.recommendedShadowCadence;
        }
    }

    public enum Launcher {
        NONE("Desktop JVM"),
        POJAV("PojavLauncher"),
        ZALITH("Zalith Launcher"),
        FOLD_CRAFT("Fold Craft Launcher"),
        AMETHYST("Amethyst Launcher"),
        MOJO("MojoLauncher"),
        GENERIC_ANDROID("Android JVM");

        private final String displayName;

        Launcher(final String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return this.displayName;
        }

        public boolean isMobileLauncher() {
            return this != NONE;
        }
    }

    private final Silicon silicon;
    private final Launcher launcher;
    private final boolean glesNativeContext;

    public MobileGpuArchitecture(final Silicon silicon,
                                 final Launcher launcher,
                                 final boolean glesNativeContext) {
        this.silicon = silicon == null ? Silicon.DESKTOP : silicon;
        this.launcher = launcher == null ? Launcher.NONE : launcher;
        this.glesNativeContext = glesNativeContext;
    }

    public static MobileGpuArchitecture detect(final String vendor,
                                               final String renderer,
                                               final String glVersion,
                                               final boolean androidHost,
                                               final Function<String, String> envLookup) {
        final Function<String, String> env = envLookup == null ? System::getenv : envLookup;
        final Launcher launcher = detectLauncher(androidHost, env);
        final String combined = ((vendor == null ? "" : vendor) + ' '
                + (renderer == null ? "" : renderer)).toLowerCase(Locale.ROOT);
        final String verLower = glVersion == null ? "" : glVersion.toLowerCase(Locale.ROOT);
        final boolean isGlesContext = verLower.contains("opengl es");

        final Silicon silicon = classifySilicon(combined, androidHost || launcher.isMobileLauncher());
        return new MobileGpuArchitecture(silicon, launcher, isGlesContext);
    }

    public static MobileGpuArchitecture detectFromMap(final Map<String, String> env,
                                                      final String vendor,
                                                      final String renderer,
                                                      final String glVersion,
                                                      final boolean androidHost) {
        return detect(vendor, renderer, glVersion, androidHost, env::get);
    }

    private static Launcher detectLauncher(final boolean androidHost, final Function<String, String> env) {
        if (hasEnv(env, "ZALITH_RENDERER") || hasEnv(env, "ZALITH_VERSION_CODE")) {
            return Launcher.ZALITH;
        }
        if (hasEnv(env, "FCL_RENDERER") || hasEnv(env, "FCL_NATIVEDIR") || hasEnv(env, "FCL_VERSION_CODE")) {
            return Launcher.FOLD_CRAFT;
        }
        if (hasEnv(env, "AMETHYST_RENDERER") || containsIgnoreCase(env.apply("POJAV_NATIVEDIR"), "amethyst")) {
            return Launcher.AMETHYST;
        }
        if (hasEnv(env, "MOJO_RENDERER") || containsIgnoreCase(env.apply("POJAV_NATIVEDIR"), "mojo")) {
            return Launcher.MOJO;
        }
        if (hasEnv(env, "POJAV_RENDERER") || hasEnv(env, "POJAV_ENVIRON") || hasEnv(env, "POJAV_NATIVEDIR")) {
            return Launcher.POJAV;
        }
        if (androidHost || hasEnv(env, "ANDROID_ROOT") || hasEnv(env, "ANDROID_DATA")) {
            return Launcher.GENERIC_ANDROID;
        }
        return Launcher.NONE;
    }

    private static Silicon classifySilicon(final String combined, final boolean mobileHost) {
        if (combined.contains("adreno") || combined.contains("freedreno") || combined.contains("turnip")
                || combined.contains("qualcomm")) {
            return Silicon.ADRENO;
        }
        if (combined.contains("mali") || combined.contains("immortalis") || combined.contains("panfrost")
                || combined.contains("panthor")) {
            return Silicon.MALI;
        }
        if (combined.contains("powervr") || combined.contains("imagination")) {
            return Silicon.POWERVR;
        }
        if (combined.contains("xclipse") || combined.contains("samsung")) {
            return Silicon.XCLIPSE;
        }
        if (combined.contains("apple m") || combined.contains("apple a") || combined.contains("apple gpu")) {
            return Silicon.APPLE_TBDR;
        }
        if (combined.contains("tegra")) {
            return Silicon.TEGRA;
        }
        return mobileHost ? Silicon.GENERIC_MOBILE : Silicon.DESKTOP;
    }

    public Silicon silicon() {
        return this.silicon;
    }

    public Launcher launcher() {
        return this.launcher;
    }

    public boolean isGlesNativeContext() {
        return this.glesNativeContext;
    }

    /** True on any Android launcher, mobile GPU silicon, or TBDR GPU (including Apple TBDR). */
    public boolean isMobileOrTbdr() {
        return this.launcher.isMobileLauncher() || this.silicon != Silicon.DESKTOP;
    }

    public String summary() {
        if (this.launcher.isMobileLauncher()) {
            return this.launcher.displayName() + " / " + this.silicon.displayName();
        }
        return this.silicon.displayName();
    }

    private static boolean hasEnv(final Function<String, String> env, final String key) {
        final String v = env.apply(key);
        return v != null && !v.isBlank();
    }

    private static boolean containsIgnoreCase(final String haystack, final String needle) {
        return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }
}
