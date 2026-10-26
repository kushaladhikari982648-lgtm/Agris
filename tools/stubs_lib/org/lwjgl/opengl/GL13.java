package org.lwjgl.opengl;

/** Offline compile stand-in: only the LWJGL 3 methods Aetherium calls. */
public class GL13 {
    public static void glActiveTexture(int texture) { }
    public static void glClientActiveTexture(int texture) { }
    public static final int GL_ACTIVE_TEXTURE = 0x84E0;
    public static final int GL_TEXTURE0 = 0x84C0;
}
