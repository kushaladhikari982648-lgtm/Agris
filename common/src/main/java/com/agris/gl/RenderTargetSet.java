package com.agris.gl;

import com.agris.pack.PackDirectives;
import com.agris.util.AgrisLog;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Owns all GPU textures and framebuffers for an active shader pipeline, with dedicated
 * Tile-Based Deferred Rendering (TBDR) mobile optimizations:
 * <ul>
 *   <li><b>Demand-driven attachment stripping</b> via {@link PackSamplerUsage}: skips
 *       allocating and blitting {@code depthtex1}, {@code depthtex2}, {@code shadowtex1},
 *       and {@code shadowcolor0/1} when unused by the active shader pack.</li>
 *   <li><b>GLES 3.0+ compliant depth blitting</b> via {@code glBlitFramebuffer} instead of
 *       desktop-only {@code glCopyTexSubImage2D} on depth textures.</li>
 *   <li><b>Immutable cached GBuffer &amp; Composite FBOs</b> + redundant FBO/viewport elision
 *       so consecutive opaque chunk layers stay inside ONE uninterrupted TBDR tile pass.</li>
 *   <li><b>50% bandwidth HDR compaction</b> ({@code RGBA16F -> R11F_G11F_B10F}) on TBDR GPUs
 *       when buffer alpha is unreferenced, plus automatic {@code RGBA8} fallback on GL4ES.</li>
 *   <li><b>Tile SRAM discard hints</b> ({@link GlTileHint}) on transient depth tiles at frame end.</li>
 * </ul>
 */
public final class RenderTargetSet implements AutoCloseable {
    public static final int MAX_BUFFERS = PackDirectives.MAX_COLOR_BUFFERS;
    private static final int[] SINGLE_BUFFER_ZERO = {0};

    private final PackDirectives directives;
    private final PackSamplerUsage samplerUsage;
    private final boolean preferMobileSafeFormats;
    private final boolean compactMobileHdr;
    private final boolean tileDiscardEnabled;
    private final int maxDrawBuffers;

    private int width;
    private int height;
    private int shadowResolution;

    private final boolean[] allocatedBuffer = new boolean[MAX_BUFFERS];
    private final int[] mainColorTex = new int[MAX_BUFFERS];
    private final int[] altColorTex = new int[MAX_BUFFERS];
    private final boolean[] flipped = new boolean[MAX_BUFFERS];

    private int depthtex0;
    private int depthtex1;
    private int depthtex2;
    private int copyReadFbo;
    private int copyDrawFbo;

    private final boolean shadowAllocated;
    private int shadowFbo;
    private int shadowtex0;
    private int shadowtex1;
    private int shadowcolor0;
    private int shadowcolor1;

    private final Map<Long, Integer> cachedGbufferFbos = new HashMap<>();
    private final Map<Long, Integer> cachedCompositeFbos = new HashMap<>();

    // Tracked bound FBO & viewport within a world frame to avoid breaking TBDR tile passes
    private int activeFrameFbo = -1;
    private int activeViewportW = -1;
    private int activeViewportH = -1;

    public RenderTargetSet(final PackDirectives directives,
                           final int initialWidth,
                           final int initialHeight,
                           final int effectiveShadowResolution,
                           final boolean[] referencedColorBuffers,
                           final boolean allocateShadows,
                           final boolean preferMobileSafeFormats,
                           final int maxDrawBuffers) {
        this(directives, initialWidth, initialHeight, effectiveShadowResolution,
                referencedColorBuffers, allocateShadows, preferMobileSafeFormats,
                maxDrawBuffers, PackSamplerUsage.allUsed(), false, false);
    }

    public RenderTargetSet(final PackDirectives directives,
                           final int initialWidth,
                           final int initialHeight,
                           final int effectiveShadowResolution,
                           final boolean[] referencedColorBuffers,
                           final boolean allocateShadows,
                           final boolean preferMobileSafeFormats,
                           final int maxDrawBuffers,
                           final PackSamplerUsage samplerUsage,
                           final boolean compactMobileHdr,
                           final boolean tileDiscardEnabled) {
        this.directives = directives;
        this.samplerUsage = samplerUsage == null ? PackSamplerUsage.allUsed() : samplerUsage;
        this.preferMobileSafeFormats = preferMobileSafeFormats;
        this.compactMobileHdr = compactMobileHdr;
        this.tileDiscardEnabled = tileDiscardEnabled;
        this.maxDrawBuffers = Math.max(1, Math.min(16, maxDrawBuffers));
        this.width = Math.max(1, initialWidth);
        this.height = Math.max(1, initialHeight);
        this.shadowResolution = Math.max(64, effectiveShadowResolution);

        for (int i = 0; i < MAX_BUFFERS; i++) {
            this.allocatedBuffer[i] = i == 0 || (referencedColorBuffers != null
                    && i < referencedColorBuffers.length && referencedColorBuffers[i]);
        }
        this.shadowAllocated = allocateShadows;
        allocateAll();
    }

    public void resizeIfNeeded(final int newWidth, final int newHeight) {
        final int safeW = Math.max(1, newWidth);
        final int safeH = Math.max(1, newHeight);
        if (safeW == this.width && safeH == this.height) {
            return;
        }
        this.width = safeW;
        this.height = safeH;
        destroyMainTargets();
        allocateMainTargets();
    }

    public void beginFrameClears(final boolean clearShadowThisFrame) {
        Arrays.fill(this.flipped, false);
        this.activeFrameFbo = -1;
        this.activeViewportW = -1;
        this.activeViewportH = -1;

        // Clear depth + configured color buffers at the start of the world frame.
        for (int i = 0; i < MAX_BUFFERS; i++) {
            if (!this.allocatedBuffer[i] || !this.directives.bufferClear(i)) {
                continue;
            }
            final int fbo = getOrCreateGbufferFbo(i == 0 ? SINGLE_BUFFER_ZERO : new int[]{i}, i == 0);
            bindFrameFbo(fbo, this.width, this.height);
            final float[] clear = this.directives.bufferClearColorOrNull(i);
            if (clear != null) {
                GL11.glClearColor(clear[0], clear[1], clear[2], clear[3]);
            } else {
                GL11.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
            }
            GL11.glClear(i == 0 ? (GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT) : GL11.GL_COLOR_BUFFER_BIT);
        }

        if (clearShadowThisFrame && this.shadowAllocated && this.shadowFbo != 0) {
            bindFrameFbo(this.shadowFbo, this.shadowResolution, this.shadowResolution);
            if (this.shadowcolor0 != 0) {
                GL11.glClearColor(1.0f, 1.0f, 1.0f, 1.0f);
                GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
            } else {
                GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
            }
        }
    }

    /** Backwards-compatible overload clearing both main and shadow targets. */
    public void beginFrameClears() {
        beginFrameClears(true);
    }

    /** Returns the texture currently holding the latest written contents of {@code colortex<index>}. */
    public int readTexture(final int index) {
        if (index < 0 || index >= MAX_BUFFERS || !this.allocatedBuffer[index]) {
            return this.mainColorTex[0];
        }
        return this.flipped[index] ? this.altColorTex[index] : this.mainColorTex[index];
    }

    /** Returns the write target texture for {@code colortex<index>} during a ping-pong composite pass. */
    public int writeTextureForComposite(final int index) {
        if (index < 0 || index >= MAX_BUFFERS || !this.allocatedBuffer[index]) {
            return this.altColorTex[0];
        }
        return this.flipped[index] ? this.mainColorTex[index] : this.altColorTex[index];
    }

    /** Returns the direct GBuffer texture for {@code colortex<index>}. */
    public int gbufferTexture(final int index) {
        if (index < 0 || index >= MAX_BUFFERS || !this.allocatedBuffer[index]) {
            return this.mainColorTex[0];
        }
        return this.flipped[index] ? this.altColorTex[index] : this.mainColorTex[index];
    }

    public void flipBuffer(final int index) {
        if (index >= 0 && index < MAX_BUFFERS && this.allocatedBuffer[index]) {
            this.flipped[index] = !this.flipped[index];
        }
    }

    /**
     * Binds an MRT GBuffer FBO writing directly to {@code drawBuffers} + {@code depthtex0}.
     * Elides redundant {@code glBindFramebuffer} calls when already bound so consecutive
     * opaque world layers stay inside a single uninterrupted TBDR tile pass on mobile GPUs.
     */
    public void bindGbufferFbo(final int[] drawBuffers) {
        final int fbo = getOrCreateGbufferFbo(drawBuffers, true);
        bindFrameFbo(fbo, this.width, this.height);
    }

    public void bindDefaultGbufferFbo() {
        bindGbufferFbo(SINGLE_BUFFER_ZERO);
    }

    /**
     * Binds an immutable cached composite/deferred FBO writing into the opposite ping-pong
     * side of each buffer in {@code drawBuffers} — zero per-frame {@code glFramebufferTexture2D}
     * re-attachment overhead.
     */
    public void bindCompositeWriteFbo(final int[] drawBuffers) {
        final int fbo = getOrCreateCompositeFbo(drawBuffers);
        bindFrameFbo(fbo, this.width, this.height);
    }

    public void bindShadowFbo() {
        if (this.shadowAllocated && this.shadowFbo != 0) {
            bindFrameFbo(this.shadowFbo, this.shadowResolution, this.shadowResolution);
        }
    }

    /** Marks external FBO transition (e.g., final pass binding mainFramebufferId) and discards transient tile depth. */
    public void prepareForFinalPresentation() {
        if (this.tileDiscardEnabled) {
            final int gbuf0 = getOrCreateGbufferFbo(SINGLE_BUFFER_ZERO, true);
            bindFrameFbo(gbuf0, this.width, this.height);
            GlTileHint.invalidateBoundDepthAttachment();
        }
        this.activeFrameFbo = -1;
        this.activeViewportW = -1;
        this.activeViewportH = -1;
    }

    /** Copies live {@code depthtex0} into {@code depthtex1} only when {@code depthtex1} is sampled by the pack. */
    public void copyPreTranslucentDepth() {
        if (this.depthtex1 != 0) {
            blitDepthTexture(this.depthtex0, this.depthtex1, this.width, this.height);
        }
    }

    /** Copies live {@code depthtex0} into {@code depthtex2} only when {@code depthtex2} is sampled by the pack. */
    public void copyPreHandDepth() {
        if (this.depthtex2 != 0) {
            blitDepthTexture(this.depthtex0, this.depthtex2, this.width, this.height);
        }
    }

    /** Copies {@code shadowtex0} into {@code shadowtex1} only when {@code shadowtex1} is sampled by the pack. */
    public void copyPreTranslucentShadowDepth() {
        if (this.shadowAllocated && this.shadowtex0 != 0 && this.shadowtex1 != 0) {
            blitDepthTexture(this.shadowtex0, this.shadowtex1, this.shadowResolution, this.shadowResolution);
        }
    }

    public void generateMipmapsFor(final int[] bufferIndices) {
        if (bufferIndices == null || bufferIndices.length == 0) {
            return;
        }
        for (final int idx : bufferIndices) {
            if (idx >= 0 && idx < MAX_BUFFERS && this.allocatedBuffer[idx]) {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, readTexture(idx));
                GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
            }
        }
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }

    public int depthtex0() {
        return this.depthtex0;
    }

    /** Falls back cleanly to {@code depthtex0} if {@code depthtex1} was stripped as unused. */
    public int depthtex1() {
        return this.depthtex1 != 0 ? this.depthtex1 : this.depthtex0;
    }

    /** Falls back cleanly to {@code depthtex0} if {@code depthtex2} was stripped as unused. */
    public int depthtex2() {
        return this.depthtex2 != 0 ? this.depthtex2 : this.depthtex0;
    }

    public int shadowtex0() {
        return this.shadowtex0;
    }

    public int shadowtex1() {
        return this.shadowtex1 != 0 ? this.shadowtex1 : this.shadowtex0;
    }

    public int shadowcolor0() {
        return this.shadowcolor0;
    }

    public int shadowcolor1() {
        return this.shadowcolor1;
    }

    public int width() {
        return this.width;
    }

    public int height() {
        return this.height;
    }

    public int shadowResolution() {
        return this.shadowResolution;
    }

    public boolean isShadowAllocated() {
        return this.shadowAllocated && this.shadowFbo != 0;
    }

    private void bindFrameFbo(final int fbo, final int vpW, final int vpH) {
        if (this.activeFrameFbo != fbo) {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
            this.activeFrameFbo = fbo;
        }
        if (this.activeViewportW != vpW || this.activeViewportH != vpH) {
            GL11.glViewport(0, 0, vpW, vpH);
            this.activeViewportW = vpW;
            this.activeViewportH = vpH;
        }
    }

    private void allocateAll() {
        allocateMainTargets();
        if (this.shadowAllocated) {
            allocateShadowTargets();
        }
    }

    private void allocateMainTargets() {
        this.depthtex0 = createDepthTexture(this.width, this.height, false);
        this.depthtex1 = this.samplerUsage.usesDepthtex1()
                ? createDepthTexture(this.width, this.height, false) : 0;
        this.depthtex2 = this.samplerUsage.usesDepthtex2()
                ? createDepthTexture(this.width, this.height, false) : 0;

        this.copyReadFbo = GL30.glGenFramebuffers();
        this.copyDrawFbo = GL30.glGenFramebuffers();

        for (int i = 0; i < MAX_BUFFERS; i++) {
            if (!this.allocatedBuffer[i]) {
                continue;
            }
            final int bufW = Math.max(1, Math.round(this.width * this.directives.bufferScale(i)));
            final int bufH = Math.max(1, Math.round(this.height * this.directives.bufferScale(i)));
            InternalTextureFormat fmt = this.directives.bufferFormat(i);
            if (this.preferMobileSafeFormats) {
                fmt = fmt.mobileSafeFallback();
            } else if (this.compactMobileHdr) {
                fmt = fmt.mobileBandwidthCompact(this.samplerUsage.samplesColorAlpha(i));
            }
            final boolean mipmap = this.directives.bufferMipmap(i);
            this.mainColorTex[i] = createColorTexture(bufW, bufH, fmt, mipmap);
            this.altColorTex[i] = createColorTexture(bufW, bufH, fmt, mipmap);

            // Verify FBO completeness for floating-point formats and auto-downgrade to RGBA8 if incomplete.
            if (fmt.isFloatingPoint()) {
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.copyDrawFbo);
                GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                        GL11.GL_TEXTURE_2D, this.mainColorTex[i], 0);
                final int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
                if (status != 0 && status != GL30.GL_FRAMEBUFFER_COMPLETE) {
                    AgrisLog.warn("Driver rejected {} on colortex{}, downgrading cleanly to RGBA8.", fmt, i);
                    GL11.glDeleteTextures(this.mainColorTex[i]);
                    GL11.glDeleteTextures(this.altColorTex[i]);
                    this.mainColorTex[i] = createColorTexture(bufW, bufH, InternalTextureFormat.RGBA8, mipmap);
                    this.altColorTex[i] = createColorTexture(bufW, bufH, InternalTextureFormat.RGBA8, mipmap);
                }
                GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, 0, 0);
            }
        }

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
    }

    private void allocateShadowTargets() {
        this.shadowtex0 = createDepthTexture(this.shadowResolution, this.shadowResolution,
                this.directives.shadowHardwareFiltering(0));
        this.shadowtex1 = this.samplerUsage.usesShadowtex1()
                ? createDepthTexture(this.shadowResolution, this.shadowResolution,
                        this.directives.shadowHardwareFiltering(1))
                : 0;

        final boolean needColor0 = this.samplerUsage.usesShadowColor0();
        final boolean needColor1 = this.samplerUsage.usesShadowColor1() && this.maxDrawBuffers >= 2;

        if (needColor0) {
            InternalTextureFormat sc0 = this.directives.shadowColorFormat(0);
            if (this.preferMobileSafeFormats) {
                sc0 = sc0.mobileSafeFallback();
            }
            this.shadowcolor0 = createColorTexture(this.shadowResolution, this.shadowResolution, sc0, false);
        }
        if (needColor1) {
            InternalTextureFormat sc1 = this.directives.shadowColorFormat(1);
            if (this.preferMobileSafeFormats) {
                sc1 = sc1.mobileSafeFallback();
            }
            this.shadowcolor1 = createColorTexture(this.shadowResolution, this.shadowResolution, sc1, false);
        }

        this.shadowFbo = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.shadowFbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                GL11.GL_TEXTURE_2D, this.shadowtex0, 0);

        if (needColor0 && needColor1) {
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D, this.shadowcolor0, 0);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0 + 1,
                    GL11.GL_TEXTURE_2D, this.shadowcolor1, 0);
            GL20.glDrawBuffers(new int[]{GL30.GL_COLOR_ATTACHMENT0, GL30.GL_COLOR_ATTACHMENT0 + 1});
        } else if (needColor0) {
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D, this.shadowcolor0, 0);
            GL20.glDrawBuffers(new int[]{GL30.GL_COLOR_ATTACHMENT0});
        } else {
            // Pure depth-only shadow FBO: halves mobile shadow-pass tile & LPDDR bandwidth!
            GL20.glDrawBuffers(new int[]{GL11.GL_NONE});
        }

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
    }

    private int getOrCreateGbufferFbo(final int[] drawBuffers, final boolean withDepth) {
        final int count = Math.min(this.maxDrawBuffers, drawBuffers.length);
        long key = withDepth ? 1L : 0L;
        for (int i = 0; i < count; i++) {
            final int idx = drawBuffers[i];
            final int sideBit = (idx >= 0 && idx < MAX_BUFFERS && this.flipped[idx]) ? 1 : 0;
            key = key * 37L + (idx * 2L + sideBit + 2L);
        }
        final Integer existing = this.cachedGbufferFbos.get(key);
        if (existing != null) {
            return existing;
        }
        final int fbo = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        this.activeFrameFbo = fbo;
        final int[] attachments = new int[count];
        for (int loc = 0; loc < count; loc++) {
            final int bufIdx = drawBuffers[loc];
            final int att = GL30.GL_COLOR_ATTACHMENT0 + loc;
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, att, GL11.GL_TEXTURE_2D, gbufferTexture(bufIdx), 0);
            attachments[loc] = att;
        }
        if (withDepth) {
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                    GL11.GL_TEXTURE_2D, this.depthtex0, 0);
        }
        GL20.glDrawBuffers(attachments);
        this.cachedGbufferFbos.put(key, fbo);
        return fbo;
    }

    private int getOrCreateCompositeFbo(final int[] drawBuffers) {
        final int count = Math.min(this.maxDrawBuffers, drawBuffers.length);
        long key = 7L;
        for (int i = 0; i < count; i++) {
            final int idx = drawBuffers[i];
            final int sideBit = (idx >= 0 && idx < MAX_BUFFERS && this.flipped[idx]) ? 1 : 0;
            key = key * 41L + (idx * 2L + sideBit + 2L);
        }
        final Integer existing = this.cachedCompositeFbos.get(key);
        if (existing != null) {
            return existing;
        }
        final int fbo = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        this.activeFrameFbo = fbo;
        final int[] attachments = new int[count];
        for (int loc = 0; loc < count; loc++) {
            final int bufIdx = drawBuffers[loc];
            final int att = GL30.GL_COLOR_ATTACHMENT0 + loc;
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, att, GL11.GL_TEXTURE_2D, writeTextureForComposite(bufIdx), 0);
            attachments[loc] = att;
        }
        GL20.glDrawBuffers(attachments);
        this.cachedCompositeFbos.put(key, fbo);
        return fbo;
    }

    /**
     * GLES 3.0+ &amp; OpenGL 3.0+ compliant hardware depth blit (replaces {@code glCopyTexSubImage2D}
     * which fails with {@code GL_INVALID_OPERATION} on depth formats under OpenGL ES).
     */
    private void blitDepthTexture(final int srcDepthTex, final int dstDepthTex, final int w, final int h) {
        if (srcDepthTex == 0 || dstDepthTex == 0) {
            return;
        }
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, this.copyReadFbo);
        GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                GL11.GL_TEXTURE_2D, srcDepthTex, 0);

        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, this.copyDrawFbo);
        GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                GL11.GL_TEXTURE_2D, dstDepthTex, 0);

        GL30.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);

        // Force next bindFrameFbo call to re-establish the active draw & read FBO cleanly.
        this.activeFrameFbo = -1;
    }

    private static int createColorTexture(final int w, final int h,
                                          final InternalTextureFormat format,
                                          final boolean mipmap) {
        final int tex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        final int minFilter = format.isIntegerFormat()
                ? GL11.GL_NEAREST
                : (mipmap ? GL11.GL_LINEAR_MIPMAP_LINEAR : GL11.GL_LINEAR);
        final int magFilter = format.isIntegerFormat() ? GL11.GL_NEAREST : GL11.GL_LINEAR;
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, minFilter);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, magFilter);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, format.glInternalFormat(),
                w, h, 0, format.glPixelFormat(), format.glPixelType(), (ByteBuffer) null);
        return tex;
    }

    private static int createDepthTexture(final int w, final int h, final boolean hardwareCompare) {
        final int tex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        final int filter = hardwareCompare ? GL11.GL_LINEAR : GL11.GL_NEAREST;
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filter);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, filter);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        if (hardwareCompare) {
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_MODE, GL14.GL_COMPARE_R_TO_TEXTURE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_FUNC, GL11.GL_LEQUAL);
        } else {
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_MODE, GL11.GL_NONE);
        }
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL14.GL_DEPTH_COMPONENT24,
                w, h, 0, GL11.GL_DEPTH_COMPONENT, GL11.GL_UNSIGNED_INT, (ByteBuffer) null);
        return tex;
    }

    private void destroyMainTargets() {
        for (final int fbo : this.cachedGbufferFbos.values()) {
            GL30.glDeleteFramebuffers(fbo);
        }
        this.cachedGbufferFbos.clear();
        for (final int fbo : this.cachedCompositeFbos.values()) {
            GL30.glDeleteFramebuffers(fbo);
        }
        this.cachedCompositeFbos.clear();
        if (this.copyReadFbo != 0) {
            GL30.glDeleteFramebuffers(this.copyReadFbo);
            this.copyReadFbo = 0;
        }
        if (this.copyDrawFbo != 0) {
            GL30.glDeleteFramebuffers(this.copyDrawFbo);
            this.copyDrawFbo = 0;
        }
        deleteTex(this.depthtex0);
        deleteTex(this.depthtex1);
        deleteTex(this.depthtex2);
        this.depthtex0 = 0;
        this.depthtex1 = 0;
        this.depthtex2 = 0;
        for (int i = 0; i < MAX_BUFFERS; i++) {
            deleteTex(this.mainColorTex[i]);
            deleteTex(this.altColorTex[i]);
            this.mainColorTex[i] = 0;
            this.altColorTex[i] = 0;
        }
        this.activeFrameFbo = -1;
    }

    private void destroyShadowTargets() {
        if (this.shadowFbo != 0) {
            GL30.glDeleteFramebuffers(this.shadowFbo);
            this.shadowFbo = 0;
        }
        deleteTex(this.shadowtex0);
        deleteTex(this.shadowtex1);
        deleteTex(this.shadowcolor0);
        deleteTex(this.shadowcolor1);
        this.shadowtex0 = 0;
        this.shadowtex1 = 0;
        this.shadowcolor0 = 0;
        this.shadowcolor1 = 0;
    }

    private static void deleteTex(final int tex) {
        if (tex != 0) {
            GL11.glDeleteTextures(tex);
        }
    }

    @Override
    public void close() {
        destroyMainTargets();
        destroyShadowTargets();
    }
}
