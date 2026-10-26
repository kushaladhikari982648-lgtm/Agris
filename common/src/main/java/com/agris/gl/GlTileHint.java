package com.agris.gl;

import org.lwjgl.opengl.GL30;

import java.lang.reflect.Method;

/**
 * Issues OpenGL 4.3 / OpenGL ES 3.0 {@code glInvalidateFramebuffer} tile-discard hints
 * so Tile-Based Deferred Rendering (TBDR) mobile GPUs (Adreno GMEM, Mali Tile Buffer,
 * Apple Silicon, PowerVR) discard transient depth/color tile attachments in on-chip SRAM
 * rather than flushing megabytes of dead pixel data back to LPDDR RAM at frame end.
 *
 * <p>Resolved reflectively once at class load so Agris compiles and runs cleanly even on
 * stripped offline stubs or legacy OpenGL 3.2 drivers that lack {@code GL43}.</p>
 */
public final class GlTileHint {
    private static final int[] DEPTH_ONLY_ATTACHMENT = {GL30.GL_DEPTH_ATTACHMENT};
    private static final Method INVALIDATE_METHOD = resolveInvalidateMethod();

    private GlTileHint() {
    }

    private static Method resolveInvalidateMethod() {
        for (final String clsName : new String[]{
                "org.lwjgl.opengl.GL43",
                "org.lwjgl.opengl.ARBInvalidateSubdata"
        }) {
            try {
                final Class<?> cls = Class.forName(clsName);
                return cls.getMethod("glInvalidateFramebuffer", int.class, int[].class);
            } catch (final Throwable ignored) {
                // Probe next candidate.
            }
        }
        return null;
    }

    public static boolean isSupported() {
        return INVALIDATE_METHOD != null;
    }

    /** Discards {@code GL_DEPTH_ATTACHMENT} on the currently bound framebuffer if supported. */
    public static void invalidateBoundDepthAttachment() {
        if (INVALIDATE_METHOD == null) {
            return;
        }
        try {
            INVALIDATE_METHOD.invoke(null, GL30.GL_FRAMEBUFFER, DEPTH_ONLY_ATTACHMENT);
        } catch (final Throwable ignored) {
            // Never fail rendering if an Android translator stubs glInvalidateFramebuffer.
        }
    }

    /** Discards arbitrary {@code GL_COLOR_ATTACHMENT0 + k} / {@code GL_DEPTH_ATTACHMENT} targets. */
    public static void invalidateBoundAttachments(final int[] attachments) {
        if (INVALIDATE_METHOD == null || attachments == null || attachments.length == 0) {
            return;
        }
        try {
            INVALIDATE_METHOD.invoke(null, GL30.GL_FRAMEBUFFER, attachments);
        } catch (final Throwable ignored) {
            // Safe no-op on unsupported contexts.
        }
    }
}
