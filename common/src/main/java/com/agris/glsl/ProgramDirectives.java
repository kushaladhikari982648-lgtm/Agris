package com.agris.glsl;

import java.util.Arrays;

/**
 * Per-program directives extracted from a shader pair's source and {@code shaders.properties}.
 */
public final class ProgramDirectives {
    private static final int[] DEFAULT_DRAW_BUFFERS = {0};

    private final int[] drawBuffers;
    private final boolean explicitDrawBuffers;
    private final float alphaTestRef;
    private final float viewportScale;
    private final int[] mipmapInputs;

    public ProgramDirectives(final int[] drawBuffers, final boolean explicitDrawBuffers,
                             final float alphaTestRef, final float viewportScale,
                             final int[] mipmapInputs) {
        this.drawBuffers = drawBuffers == null || drawBuffers.length == 0
                ? DEFAULT_DRAW_BUFFERS.clone()
                : drawBuffers.clone();
        this.explicitDrawBuffers = explicitDrawBuffers;
        this.alphaTestRef = alphaTestRef;
        this.viewportScale = viewportScale <= 0.0f ? 1.0f : viewportScale;
        this.mipmapInputs = mipmapInputs == null ? new int[0] : mipmapInputs.clone();
    }

    public static ProgramDirectives defaults() {
        return new ProgramDirectives(DEFAULT_DRAW_BUFFERS, false, -1.0f, 1.0f, new int[0]);
    }

    public int[] drawBuffers() {
        return this.drawBuffers.clone();
    }

    public boolean hasExplicitDrawBuffers() {
        return this.explicitDrawBuffers;
    }

    public float alphaTestRef() {
        return this.alphaTestRef;
    }

    public float viewportScale() {
        return this.viewportScale;
    }

    public int[] mipmapInputs() {
        return this.mipmapInputs.clone();
    }

    @Override
    public String toString() {
        return "ProgramDirectives{drawBuffers=" + Arrays.toString(this.drawBuffers)
                + ", scale=" + this.viewportScale + '}';
    }
}
