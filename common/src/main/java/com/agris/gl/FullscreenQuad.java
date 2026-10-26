package com.agris.gl;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

/**
 * Core-profile VAO + VBO unit quad ({@code [0..1] x [0..1]}) used by fullscreen
 * {@code prepare*}, {@code deferred*}, {@code composite*}, and {@code final} passes.
 *
 * <p>Attribute 0 = {@code Position} / {@code vaPosition} ({@code vec3}),
 * Attribute 1 = {@code UV0} / {@code vaUV0} ({@code vec2}), matching
 * {@link #VERTEX_ATTRIBUTES} so both {@code gl_Vertex.xy}/{@code gl_MultiTexCoord0.xy}
 * and modern {@code vaPosition}/{@code vaUV0} fullscreen shaders work identically.</p>
 */
public final class FullscreenQuad implements AutoCloseable {
    public static final List<String> VERTEX_ATTRIBUTES = List.of("Position", "UV0");

    private static final int GL_ARRAY_BUFFER = 0x8892;
    private static final int GL_STATIC_DRAW = 0x88E4;
    private static final int STRIDE = 5 * Float.BYTES;

    private int vao;
    private int vbo;

    public FullscreenQuad() {
        this.vao = GL30.glGenVertexArrays();
        this.vbo = GL15.glGenBuffers();

        final float[] verts = {
                // Tri 1
                0.0f, 0.0f, 0.0f, 0.0f, 0.0f,
                1.0f, 0.0f, 0.0f, 1.0f, 0.0f,
                1.0f, 1.0f, 0.0f, 1.0f, 1.0f,
                // Tri 2
                0.0f, 0.0f, 0.0f, 0.0f, 0.0f,
                1.0f, 1.0f, 0.0f, 1.0f, 1.0f,
                0.0f, 1.0f, 0.0f, 0.0f, 1.0f
        };
        final ByteBuffer buf = BufferUtils.createByteBuffer(verts.length * Float.BYTES)
                .order(ByteOrder.nativeOrder());
        for (final float f : verts) {
            buf.putFloat(f);
        }
        buf.flip();

        GL30.glBindVertexArray(this.vao);
        GL15.glBindBuffer(GL_ARRAY_BUFFER, this.vbo);
        GL15.glBufferData(GL_ARRAY_BUFFER, buf, GL_STATIC_DRAW);

        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, STRIDE, 0L);

        GL20.glEnableVertexAttribArray(1);
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, STRIDE, 3L * Float.BYTES);

        GL15.glBindBuffer(GL_ARRAY_BUFFER, 0);
        GL30.glBindVertexArray(0);
    }

    public void draw() {
        final boolean depthWasEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        final boolean blendWasEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
        final boolean cullWasEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);

        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_CULL_FACE);

        GL30.glBindVertexArray(this.vao);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
        GL30.glBindVertexArray(0);

        GL11.glDepthMask(true);
        if (depthWasEnabled) {
            GL11.glEnable(GL11.GL_DEPTH_TEST);
        }
        if (blendWasEnabled) {
            GL11.glEnable(GL11.GL_BLEND);
        }
        if (cullWasEnabled) {
            GL11.glEnable(GL11.GL_CULL_FACE);
        }
    }

    @Override
    public void close() {
        if (this.vao != 0) {
            GL30.glDeleteVertexArrays(this.vao);
            this.vao = 0;
        }
        if (this.vbo != 0) {
            GL15.glDeleteBuffers(this.vbo);
            this.vbo = 0;
        }
    }
}
