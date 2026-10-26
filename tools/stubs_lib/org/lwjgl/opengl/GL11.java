package org.lwjgl.opengl;

import java.nio.FloatBuffer;

/** Offline compile stand-in: only the LWJGL 3 GL11 methods Aetherium calls (signatures as in 3.2+). */
public class GL11 {
    public static String glGetString(int name) { return null; }
    public static boolean glIsEnabled(int cap) { return false; }
    public static int glGetInteger(int pname) { return 0; }
    public static float glGetFloat(int pname) { return 0f; }
    public static void glGetFloatv(int pname, FloatBuffer params) { }
    public static void glDrawElements(int mode, int count, int type, long indices) { }
    public static void glDrawArrays(int mode, int first, int count) { }
    public static int glGetError() { return 0; }
    public static void glViewport(int x, int y, int w, int h) { }
    public static void glEnableClientState(int cap) { }
    public static void glDisableClientState(int cap) { }
    public static void glVertexPointer(int size, int type, int stride, long pointer) { }
    public static void glColorPointer(int size, int type, int stride, long pointer) { }
    public static void glTexCoordPointer(int size, int type, int stride, long pointer) { }
    public static void glNormalPointer(int type, int stride, long pointer) { }
    public static void glPixelStorei(int pname, int param) { }
    public static int glGenTextures() { return 0; }
    public static void glDeleteTextures(int texture) { }
    public static void glBindTexture(int target, int texture) { }
    public static void glTexParameteri(int target, int pname, int param) { }
    public static void glTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, java.nio.ByteBuffer pixels) { }
    public static void glGetIntegerv(int pname, int[] params) { }
    public static void glEnable(int cap) { }
    public static void glDisable(int cap) { }
    public static void glGetBooleanv(int pname, java.nio.ByteBuffer params) { }
    public static void glDepthFunc(int func) { }
    public static void glDepthMask(boolean flag) { }
    public static void glColorMask(boolean red, boolean green, boolean blue, boolean alpha) { }
    public static void glReadPixels(int x, int y, int width, int height, int format, int type, java.nio.ByteBuffer pixels) { }
    public static final int GL_ALPHA_TEST = 0xBC0;
    public static final int GL_ALWAYS = 0x207;
    public static final int GL_BLEND = 0xBE2;
    public static final int GL_BYTE = 0x1400;
    public static final int GL_COLOR_BUFFER_BIT = 0x4000;
    public static final int GL_COLOR_CLEAR_VALUE = 0xC22;
    public static final int GL_CULL_FACE = 0xB44;
    public static final int GL_DEPTH_BUFFER_BIT = 0x100;
    public static final int GL_DEPTH_COMPONENT = 0x1902;
    public static final int GL_DEPTH_TEST = 0xB71;
    public static final int GL_DEPTH_WRITEMASK = 0xB72;
    public static final int GL_ENABLE_BIT = 0x2000;
    public static final int GL_EXTENSIONS = 0x1F03;
    public static final int GL_FALSE = 0x0;
    public static final int GL_FLOAT = 0x1406;
    public static final int GL_FOG = 0xB60;
    public static final int GL_INT = 0x1404;
    public static final int GL_LEQUAL = 0x203;
    public static final int GL_LINEAR = 0x2601;
    public static final int GL_LINEAR_MIPMAP_LINEAR = 0x2703;
    public static final int GL_LINES = 0x1;
    public static final int GL_MODELVIEW = 0x1700;
    public static final int GL_NEAREST = 0x2600;
    public static final int GL_NONE = 0x0;
    public static final int GL_PROJECTION = 0x1701;
    public static final int GL_QUADS = 0x7;
    public static final int GL_RENDERER = 0x1F01;
    public static final int GL_REPEAT = 0x2901;
    public static final int GL_RGB = 0x1907;
    public static final int GL_RGBA = 0x1908;
    public static final int GL_RGBA8 = 0x8058;
    public static final int GL_SHORT = 0x1402;
    public static final int GL_TEXTURE_2D = 0xDE1;
    public static final int GL_TEXTURE_BINDING_2D = 0x8069;
    public static final int GL_TEXTURE_MAG_FILTER = 0x2800;
    public static final int GL_TEXTURE_MIN_FILTER = 0x2801;
    public static final int GL_TEXTURE_WRAP_S = 0x2802;
    public static final int GL_TEXTURE_WRAP_T = 0x2803;
    public static final int GL_TRIANGLES = 0x4;
    public static final int GL_TRUE = 0x1;
    public static final int GL_UNSIGNED_BYTE = 0x1401;
    public static final int GL_UNSIGNED_INT = 0x1405;
    public static final int GL_UNSIGNED_SHORT = 0x1403;
    public static final int GL_VENDOR = 0x1F00;
    public static final int GL_VERSION = 0x1F02;
    public static final int GL_VIEWPORT = 0xBA2;
    public static void glBegin(int mode) { }
    public static void glEnd() { }
    public static void glVertex3f(float x, float y, float z) { }
    public static void glTexCoord2f(float s, float t) { }
    public static void glClear(int mask) { }
    public static void glClearColor(float red, float green, float blue, float alpha) { }
    public static void glCopyTexSubImage2D(int target, int level, int xoffset, int yoffset, int x, int y, int width, int height) { }
    public static void glDrawBuffer(int buf) { }
    public static void glReadBuffer(int src) { }
    public static void glLoadIdentity() { }
    public static void glLoadMatrixf(java.nio.FloatBuffer m) { }
    public static void glMatrixMode(int mode) { }
    public static void glOrtho(double l, double r, double b, double t, double zNear, double zFar) { }
    public static void glPushMatrix() { }
    public static void glPopMatrix() { }
    public static void glPushAttrib(int mask) { }
    public static void glPopAttrib() { }
    public static void glReadPixels(int x, int y, int width, int height, int format, int type, java.nio.FloatBuffer pixels) { }
}
