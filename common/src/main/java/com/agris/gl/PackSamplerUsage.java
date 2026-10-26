package com.agris.gl;

import com.agris.pack.PackDirectives;

import java.util.Collection;
import java.util.regex.Pattern;

/**
 * Static analyzer that scans a shader pack's active GLSL sources once at pipeline build time
 * to determine which expensive depth snapshots and shadow color attachments are actually sampled.
 *
 * <p>On mobile TBDR GPUs, skipping unused {@code depthtex1}, {@code depthtex2},
 * {@code shadowtex1}, and {@code shadowcolor0/1} saves ~30–40 MB of VRAM and eliminates up to
 * three full-frame depth-blit resolves per frame (~1.5 GB/s of LPDDR bandwidth at 60 FPS).</p>
 */
public final class PackSamplerUsage {
    private static final Pattern DEPTHTEX1_RE = Pattern.compile("\\bdepthtex1\\b");
    private static final Pattern DEPTHTEX2_RE = Pattern.compile("\\bdepthtex2\\b");
    private static final Pattern SHADOWTEX1_RE = Pattern.compile("\\bshadowtex1\\b");
    private static final Pattern SHADOWCOLOR0_RE = Pattern.compile("\\bshadowcolor0?\\b");
    private static final Pattern SHADOWCOLOR1_RE = Pattern.compile("\\bshadowcolor1\\b");

    private final boolean usesDepthtex1;
    private final boolean usesDepthtex2;
    private final boolean usesShadowtex1;
    private final boolean usesShadowColor0;
    private final boolean usesShadowColor1;
    private final boolean[] samplesColorAlpha;

    public PackSamplerUsage(final boolean usesDepthtex1,
                            final boolean usesDepthtex2,
                            final boolean usesShadowtex1,
                            final boolean usesShadowColor0,
                            final boolean usesShadowColor1,
                            final boolean[] samplesColorAlpha) {
        this.usesDepthtex1 = usesDepthtex1;
        this.usesDepthtex2 = usesDepthtex2;
        this.usesShadowtex1 = usesShadowtex1;
        this.usesShadowColor0 = usesShadowColor0;
        this.usesShadowColor1 = usesShadowColor1;
        this.samplesColorAlpha = samplesColorAlpha == null
                ? new boolean[PackDirectives.MAX_COLOR_BUFFERS]
                : samplesColorAlpha.clone();
    }

    /** Conservative desktop profile assuming all optional attachments may be sampled. */
    public static PackSamplerUsage allUsed() {
        final boolean[] alpha = new boolean[PackDirectives.MAX_COLOR_BUFFERS];
        java.util.Arrays.fill(alpha, true);
        return new PackSamplerUsage(true, true, true, true, true, alpha);
    }

    /** Scans the GLSL sources of all resolved stages in the active pack. */
    public static PackSamplerUsage analyze(final Collection<String> glslSources) {
        boolean d1 = false;
        boolean d2 = false;
        boolean s1 = false;
        boolean sc0 = false;
        boolean sc1 = false;
        final boolean[] alpha = new boolean[PackDirectives.MAX_COLOR_BUFFERS];
        // colortex0 alpha is commonly used for material/translucent masks; keep alpha on colortex0 by default.
        alpha[0] = true;

        if (glslSources != null) {
            for (final String src : glslSources) {
                if (src == null || src.isEmpty()) {
                    continue;
                }
                if (!d1 && DEPTHTEX1_RE.matcher(src).find()) {
                    d1 = true;
                }
                if (!d2 && DEPTHTEX2_RE.matcher(src).find()) {
                    d2 = true;
                }
                if (!s1 && SHADOWTEX1_RE.matcher(src).find()) {
                    s1 = true;
                }
                if (!sc0 && SHADOWCOLOR0_RE.matcher(src).find()) {
                    sc0 = true;
                }
                if (!sc1 && SHADOWCOLOR1_RE.matcher(src).find()) {
                    sc1 = true;
                }
                for (int i = 1; i < PackDirectives.MAX_COLOR_BUFFERS; i++) {
                    if (!alpha[i] && src.contains("colortex" + i) && (src.contains(".a") || src.contains(".w"))) {
                        alpha[i] = true;
                    }
                }
            }
        }
        return new PackSamplerUsage(d1, d2, s1, sc0, sc1, alpha);
    }

    public boolean usesDepthtex1() {
        return this.usesDepthtex1;
    }

    public boolean usesDepthtex2() {
        return this.usesDepthtex2;
    }

    public boolean usesShadowtex1() {
        return this.usesShadowtex1;
    }

    public boolean usesShadowColor0() {
        return this.usesShadowColor0;
    }

    public boolean usesShadowColor1() {
        return this.usesShadowColor1;
    }

    public boolean samplesColorAlpha(final int bufferIndex) {
        return bufferIndex < 0 || bufferIndex >= this.samplesColorAlpha.length || this.samplesColorAlpha[bufferIndex];
    }
}
