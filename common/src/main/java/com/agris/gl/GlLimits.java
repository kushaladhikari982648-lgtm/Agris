package com.agris.gl;

import com.agris.compat.AndroidGlProfile;
import com.agris.mobile.MobileGpuArchitecture;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * Queries hardware OpenGL limits and combines them with {@link AndroidGlProfile}
 * and {@link MobileGpuArchitecture} to determine safe MRT attachment counts,
 * TBDR tile budgets, texture unit limits, and FBO precision.
 */
public final class GlLimits {
    private final String vendor;
    private final String renderer;
    private final String version;
    private final String glslVersion;
    private final int maxDrawBuffers;
    private final int maxColorAttachments;
    private final int maxTextureImageUnits;
    private final AndroidGlProfile androidProfile;
    private final MobileGpuArchitecture mobileArchitecture;

    public GlLimits(final String vendor, final String renderer, final String version,
                    final String glslVersion, final int maxDrawBuffers,
                    final int maxColorAttachments, final int maxTextureImageUnits,
                    final AndroidGlProfile androidProfile) {
        this(vendor, renderer, version, glslVersion, maxDrawBuffers,
                maxColorAttachments, maxTextureImageUnits, androidProfile, null);
    }

    public GlLimits(final String vendor, final String renderer, final String version,
                    final String glslVersion, final int maxDrawBuffers,
                    final int maxColorAttachments, final int maxTextureImageUnits,
                    final AndroidGlProfile androidProfile,
                    final MobileGpuArchitecture mobileArchitecture) {
        this.vendor = vendor == null ? "Unknown" : vendor;
        this.renderer = renderer == null ? "Unknown" : renderer;
        this.version = version == null ? "Unknown" : version;
        this.glslVersion = glslVersion == null ? "Unknown" : glslVersion;
        this.maxDrawBuffers = Math.max(1, maxDrawBuffers);
        this.maxColorAttachments = Math.max(1, maxColorAttachments);
        this.maxTextureImageUnits = Math.max(8, maxTextureImageUnits);
        this.androidProfile = androidProfile == null
                ? new AndroidGlProfile(false, AndroidGlProfile.Translator.DESKTOP_NATIVE, this.renderer, this.version)
                : androidProfile;
        this.mobileArchitecture = mobileArchitecture == null
                ? MobileGpuArchitecture.detect(
                        this.vendor, this.renderer, this.version, this.androidProfile.isAndroidHost(), System::getenv)
                : mobileArchitecture;
    }

    public static GlLimits queryLive(final String aetheriumRendererId, final boolean aetheriumAndroid) {
        final String vendor = safeGetString(GL11.GL_VENDOR);
        final String renderer = safeGetString(GL11.GL_RENDERER);
        final String version = safeGetString(GL11.GL_VERSION);
        final String glsl = safeGetString(GL20.GL_SHADING_LANGUAGE_VERSION);
        final int drawBuffers = Math.max(1, GL11.glGetInteger(GL20.GL_MAX_DRAW_BUFFERS));
        final int attachments = Math.max(1, GL11.glGetInteger(GL30.GL_MAX_COLOR_ATTACHMENTS));
        final int texUnits = Math.max(8, GL11.glGetInteger(GL20.GL_MAX_TEXTURE_IMAGE_UNITS));
        final AndroidGlProfile profile = AndroidGlProfile.detect(
                aetheriumRendererId, aetheriumAndroid, renderer, version, System::getenv);
        final MobileGpuArchitecture arch = MobileGpuArchitecture.detect(
                vendor, renderer, version, profile.isAndroidHost(), System::getenv);
        return new GlLimits(vendor, renderer, version, glsl, drawBuffers, attachments, texUnits, profile, arch);
    }

    public String vendor() {
        return this.vendor;
    }

    public String renderer() {
        return this.renderer;
    }

    public String version() {
        return this.version;
    }

    public String glslVersion() {
        return this.glslVersion;
    }

    public int maxDrawBuffers() {
        return Math.min(this.maxDrawBuffers, this.maxColorAttachments);
    }

    public int maxTextureImageUnits() {
        return this.maxTextureImageUnits;
    }

    public AndroidGlProfile androidProfile() {
        return this.androidProfile;
    }

    public MobileGpuArchitecture mobileArchitecture() {
        return this.mobileArchitecture;
    }

    public boolean isMobileGpu() {
        return this.androidProfile.isAndroidHost() || this.mobileArchitecture.isMobileOrTbdr();
    }

    public int effectiveDrawBufferLimit(final int userConfiguredCap) {
        return this.androidProfile.effectiveMaxDrawBuffers(maxDrawBuffers(), userConfiguredCap);
    }

    private static String safeGetString(final int pname) {
        try {
            final String s = GL11.glGetString(pname);
            return s == null ? "" : s;
        } catch (final Throwable ignored) {
            return "";
        }
    }
}
