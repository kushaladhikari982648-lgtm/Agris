package org.lwjgl.opengl;

/** Offline compile stand-in: only the LWJGL 3 GL30 methods Aetherium calls. */
public class GL30 {
    public static void glBindFramebuffer(int target, int framebuffer) { }
    public static int glGenFramebuffers() { return 0; }
    public static void glDeleteFramebuffers(int framebuffer) { }
    public static void glFramebufferTexture2D(int target, int attachment, int textarget, int texture, int level) { }
    public static int glCheckFramebufferStatus(int target) { return 0; }
    public static void glBlitFramebuffer(int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) { }
    public static int glGenVertexArrays() { return 0; }
    public static void glBindVertexArray(int array) { }
    public static void glDeleteVertexArrays(int array) { }
    public static void glBindFragDataLocation(int program, int colorNumber, CharSequence name) { }
    public static final int GL_COLOR_ATTACHMENT0 = 0x8CE0;
    public static final int GL_DEPTH_ATTACHMENT = 0x8D00;
    public static final int GL_DRAW_FRAMEBUFFER = 0x8CA9;
    public static final int GL_FRAMEBUFFER = 0x8D40;
    public static final int GL_FRAMEBUFFER_BINDING = 0x8CA6;
    public static final int GL_FRAMEBUFFER_COMPLETE = 0x8CD5;
    public static final int GL_MAX_COLOR_ATTACHMENTS = 0x8CDF;
    public static final int GL_NUM_EXTENSIONS = 0x821D;
    public static final int GL_READ_FRAMEBUFFER = 0x8CA8;
    public static void glGenerateMipmap(int target) { }
    public static String glGetStringi(int name, int index) { return null; }
}
