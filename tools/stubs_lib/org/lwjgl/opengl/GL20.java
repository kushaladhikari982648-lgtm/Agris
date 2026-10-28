package org.lwjgl.opengl;

import java.nio.FloatBuffer;

/** Offline compile stand-in: only the LWJGL 3 GL20 methods Aetherium calls. */
public class GL20 {
    public static int glCreateShader(int type) { return 0; }
    public static void glShaderSource(int shader, CharSequence source) { }
    public static void glCompileShader(int shader) { }
    public static int glGetShaderi(int shader, int pname) { return 0; }
    public static String glGetShaderInfoLog(int shader) { return ""; }
    public static void glDeleteShader(int shader) { }
    public static int glCreateProgram() { return 0; }
    public static void glAttachShader(int program, int shader) { }
    public static void glBindAttribLocation(int program, int index, CharSequence name) { }
    public static void glLinkProgram(int program) { }
    public static int glGetProgrami(int program, int pname) { return 0; }
    public static String glGetProgramInfoLog(int program) { return ""; }
    public static void glDeleteProgram(int program) { }
    public static void glUseProgram(int program) { }
    public static int glGetUniformLocation(int program, CharSequence name) { return -1; }
    public static int glGetAttribLocation(int program, CharSequence name) { return -1; }
    public static void glUniform1i(int location, int v0) { }
    public static void glUniform1f(int location, float v0) { }
    public static void glUniform3f(int location, float v0, float v1, float v2) { }
    public static void glUniform4f(int location, float v0, float v1, float v2, float v3) { }
    public static void glUniformMatrix4fv(int location, boolean transpose, FloatBuffer value) { }
    public static void glUniformMatrix4fv(int location, boolean transpose, float[] value) { }
    public static void glUniformMatrix3fv(int location, boolean transpose, FloatBuffer value) { }
    public static void glUniformMatrix3fv(int location, boolean transpose, float[] value) { }
    public static void glVertexAttrib4f(int index, float v0, float v1, float v2, float v3) { }
    public static void glEnableVertexAttribArray(int index) { }
    public static void glDisableVertexAttribArray(int index) { }
    public static void glVertexAttribPointer(int index, int size, int type, boolean normalized, int stride, long pointer) { }
    public static void glUniform2f(int location, float v0, float v1) { }
    public static final int GL_ACTIVE_UNIFORMS = 0x8B86;
    public static final int GL_BOOL = 0x8B56;
    public static final int GL_BOOL_VEC2 = 0x8B57;
    public static final int GL_BOOL_VEC3 = 0x8B58;
    public static final int GL_BOOL_VEC4 = 0x8B59;
    public static final int GL_COMPILE_STATUS = 0x8B81;
    public static final int GL_CURRENT_PROGRAM = 0x8B8D;
    public static final int GL_FLOAT_MAT4 = 0x8B5C;
    public static final int GL_FLOAT_VEC2 = 0x8B50;
    public static final int GL_FLOAT_VEC3 = 0x8B51;
    public static final int GL_FLOAT_VEC4 = 0x8B52;
    public static final int GL_FRAGMENT_SHADER = 0x8B30;
    public static final int GL_INT_VEC2 = 0x8B53;
    public static final int GL_INT_VEC3 = 0x8B54;
    public static final int GL_INT_VEC4 = 0x8B55;
    public static final int GL_LINK_STATUS = 0x8B82;
    public static final int GL_MAX_DRAW_BUFFERS = 0x8824;
    public static final int GL_MAX_TEXTURE_IMAGE_UNITS = 0x8872;
    public static final int GL_SAMPLER_2D = 0x8B5E;
    public static final int GL_SAMPLER_2D_SHADOW = 0x8B62;
    public static final int GL_SHADING_LANGUAGE_VERSION = 0x8B8C;
    public static final int GL_VERTEX_SHADER = 0x8B31;
    public static void glDrawBuffers(java.nio.IntBuffer bufs) { }
    public static void glDrawBuffers(int buf) { }
    public static void glDrawBuffers(int[] bufs) { }
    public static String glGetActiveUniform(int program, int index, int maxLength, java.nio.IntBuffer size, java.nio.IntBuffer type) { return ""; }
    public static void glUniform2i(int location, int v0, int v1) { }
    public static void glUniform3i(int location, int v0, int v1, int v2) { }
    public static void glUniform4i(int location, int v0, int v1, int v2, int v3) { }
}
