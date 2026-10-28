package com.agris.compat;

import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Identifies desktop OpenGL vs Android GL translation layers (PojavLauncher, Zalith,
 * Fold Craft Launcher, Krypton / NG-GL4ES, MobileGlues, Zink, LTW, ANGLE, VirGL)
 * and computes safe shader dialect, MRT draw-buffer limits, and FBO precision rules.
 */
public final class AndroidGlProfile {
    public enum Translator {
        DESKTOP_NATIVE("desktop", "Desktop OpenGL Core", 8, true, 330, false),
        GL4ES("gl4es", "gl4es (GLES 2.0/3.0 shim)", 4, false, 120, true),
        NG_GL4ES("nggl4es", "NG-GL4ES / Krypton (GLES 3.x)", 8, true, 330, false),
        ZINK("zink", "Mesa Zink (GL over Vulkan)", 8, true, 330, false),
        MOBILEGLUES("mobileglues", "MobileGlues (Vulkan-backed GL)", 8, true, 330, false),
        LTW("ltw", "LTW (GL 3.3 Core translator)", 8, true, 330, false),
        ANGLE("angle", "ANGLE (GLES/Vulkan backend)", 8, true, 330, false),
        GLES_NATIVE("gles", "Native OpenGL ES 3.x", 4, true, 300, false),
        VIRGL("virgl", "VirGL (virtual GPU)", 4, false, 330, false),
        ANDROID_CONSERVATIVE("android", "Generic Android GL layer", 4, false, 150, true);

        private final String id;
        private final String displayName;
        private final int recommendedMaxDrawBuffers;
        private final boolean supportsFloatFboReliably;
        private final int targetGlslVersion;
        private final boolean legacyGlslPreferred;

        Translator(final String id, final String displayName, final int recommendedMaxDrawBuffers,
                   final boolean supportsFloatFboReliably, final int targetGlslVersion,
                   final boolean legacyGlslPreferred) {
            this.id = id;
            this.displayName = displayName;
            this.recommendedMaxDrawBuffers = recommendedMaxDrawBuffers;
            this.supportsFloatFboReliably = supportsFloatFboReliably;
            this.targetGlslVersion = targetGlslVersion;
            this.legacyGlslPreferred = legacyGlslPreferred;
        }

        public String id() {
            return this.id;
        }

        public String displayName() {
            return this.displayName;
        }

        public int recommendedMaxDrawBuffers() {
            return this.recommendedMaxDrawBuffers;
        }

        public boolean supportsFloatFboReliably() {
            return this.supportsFloatFboReliably;
        }

        public int targetGlslVersion() {
            return this.targetGlslVersion;
        }

        public boolean legacyGlslPreferred() {
            return this.legacyGlslPreferred;
        }
    }

    private final boolean androidHost;
    private final Translator translator;
    private final String glRendererString;
    private final String glVersionString;

    public AndroidGlProfile(final boolean androidHost, final Translator translator,
                            final String glRendererString, final String glVersionString) {
        this.androidHost = androidHost;
        this.translator = translator == null ? Translator.DESKTOP_NATIVE : translator;
        this.glRendererString = glRendererString == null ? "" : glRendererString;
        this.glVersionString = glVersionString == null ? "" : glVersionString;
    }

    public static AndroidGlProfile detect(final String aetheriumRendererId,
                                          final boolean aetheriumAndroidFlag,
                                          final String glRenderer,
                                          final String glVersion,
                                          final Function<String, String> envLookup) {
        final Function<String, String> env = envLookup == null ? System::getenv : envLookup;
        final String pojavRenderer = firstNonBlank(
                env.apply("POJAV_RENDERER"),
                env.apply("FCL_RENDERER"),
                env.apply("ZALITH_RENDERER"),
                env.apply("LIBGL_ES"));
        final boolean androidEnv = aetheriumAndroidFlag
                || notBlank(env.apply("POJAV_ENVIRON"))
                || notBlank(env.apply("POJAV_NATIVEDIR"))
                || notBlank(env.apply("FCL_NATIVEDIR"))
                || notBlank(env.apply("ANDROID_ROOT"))
                || notBlank(pojavRenderer);

        // 1. Honor Aetherium's already-probed AndroidRenderer id when available.
        if (notBlank(aetheriumRendererId) && !"none".equalsIgnoreCase(aetheriumRendererId)) {
            final Translator mapped = mapToken(aetheriumRendererId);
            if (mapped != null) {
                return new AndroidGlProfile(true, mapped, glRenderer, glVersion);
            }
        }

        // 2. Inspect GL_RENDERER / GL_VERSION strings reported by the active context.
        final String combinedGl = ((glRenderer == null ? "" : glRenderer) + ' '
                + (glVersion == null ? "" : glVersion)).toLowerCase(Locale.ROOT);
        final Translator fromGl = mapToken(combinedGl);
        if (fromGl != null) {
            return new AndroidGlProfile(true, fromGl, glRenderer, glVersion);
        }

        // 3. Inspect launcher environment variables.
        if (notBlank(pojavRenderer)) {
            final Translator fromEnv = mapToken(pojavRenderer);
            if (fromEnv != null) {
                return new AndroidGlProfile(true, fromEnv, glRenderer, glVersion);
            }
        }

        if (androidEnv) {
            return new AndroidGlProfile(true, Translator.ANDROID_CONSERVATIVE, glRenderer, glVersion);
        }
        return new AndroidGlProfile(false, Translator.DESKTOP_NATIVE, glRenderer, glVersion);
    }

    public static AndroidGlProfile detectFromMap(final Map<String, String> env,
                                                 final String glRenderer,
                                                 final String glVersion) {
        return detect(null, false, glRenderer, glVersion, env::get);
    }

    private static Translator mapToken(final String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        final String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.contains("mobileglues") || s.contains("movileglues") || s.contains("movile_glues")) {
            return Translator.MOBILEGLUES;
        }
        if (s.contains("zink") || s.contains("turnip") || s.contains("freedreno") || s.contains("panfrost")) {
            return Translator.ZINK;
        }
        if (s.contains("ltw") || s.contains("whiten")) {
            return Translator.LTW;
        }
        if (s.contains("nggl4es") || s.contains("ng-gl4es") || s.contains("ng_gl4es")
                || s.contains("krypton") || s.startsWith("opengles3")) {
            return Translator.NG_GL4ES;
        }
        if (s.contains("gl4es") || s.startsWith("opengles2")) {
            return Translator.GL4ES;
        }
        if (s.contains("virgl")) {
            return Translator.VIRGL;
        }
        if (s.contains("angle")) {
            return Translator.ANGLE;
        }
        if (s.contains("opengl es 3.")) {
            return Translator.GLES_NATIVE;
        }
        return null;
    }

    public boolean isAndroidHost() {
        return this.androidHost;
    }

    public Translator translator() {
        return this.translator;
    }

    public String glRendererString() {
        return this.glRendererString;
    }

    public String glVersionString() {
        return this.glVersionString;
    }

    public int effectiveMaxDrawBuffers(final int hardwareMax, final int userCap) {
        int cap = Math.max(1, Math.min(16, hardwareMax));
        cap = Math.min(cap, this.translator.recommendedMaxDrawBuffers());
        if (userCap > 0) {
            cap = Math.min(cap, userCap);
        }
        return Math.max(1, cap);
    }

    public String describe() {
        if (!this.androidHost && this.translator == Translator.DESKTOP_NATIVE) {
            return this.translator.displayName();
        }
        return this.translator.displayName() + " (maxDrawBuffers=" + this.translator.recommendedMaxDrawBuffers()
                + ", glsl=" + this.translator.targetGlslVersion() + ')';
    }

    private static boolean notBlank(final String s) {
        return s != null && !s.isBlank();
    }

    private static String firstNonBlank(final String... candidates) {
        for (final String c : candidates) {
            if (notBlank(c)) {
                return c;
            }
        }
        return null;
    }
}
