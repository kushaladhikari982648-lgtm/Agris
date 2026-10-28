package org.lwjgl;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/** Offline compile stand-in for LWJGL 3's BufferUtils. */
public final class BufferUtils {
    public static ByteBuffer createByteBuffer(final int capacity) { return ByteBuffer.allocateDirect(capacity); }
    public static FloatBuffer createFloatBuffer(final int capacity) { return createByteBuffer(capacity << 2).asFloatBuffer(); }
    public static IntBuffer createIntBuffer(final int capacity) { return createByteBuffer(capacity << 2).asIntBuffer(); }
}
