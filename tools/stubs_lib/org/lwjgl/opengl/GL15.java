package org.lwjgl.opengl;

import java.nio.ShortBuffer;

/** Offline compile stand-in: only the LWJGL 3 GL15 methods Aetherium calls. */
public class GL15 {
    public static int glGenBuffers() { return 0; }
    public static void glBindBuffer(int target, int buffer) { }
    public static void glBufferData(int target, ShortBuffer data, int usage) { }
    public static void glBufferData(int target, java.nio.ByteBuffer data, int usage) { }
    public static void glBufferData(int target, long size, int usage) { }
    public static void glBufferSubData(int target, long offset, java.nio.ByteBuffer data) { }
    public static void glDeleteBuffers(int buffer) { }
    public static final int GL_SAMPLES_PASSED = 0x8914;
    public static final int GL_QUERY_RESULT = 0x8866;
    public static final int GL_QUERY_RESULT_AVAILABLE = 0x8867;
    public static int glGenQueries() { return 0; }
    public static void glDeleteQueries(int id) { }
    public static void glBeginQuery(int target, int id) { }
    public static void glEndQuery(int target) { }
    public static int glGetQueryObjecti(int id, int pname) { return 0; }
}
