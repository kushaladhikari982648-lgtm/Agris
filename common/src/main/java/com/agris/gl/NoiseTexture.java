package com.agris.gl;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.util.SplittableRandom;

/**
 * Generates the standard deterministic RGBA {@code noisetex} texture expected by
 * OptiFine and Iris shader packs (clouds, dithering, water waves, soft-shadow jitter).
 */
public final class NoiseTexture implements AutoCloseable {
    private int textureId;
    private final int resolution;

    public NoiseTexture(final int resolution) {
        this.resolution = Math.max(16, Math.min(2048, resolution));
        this.textureId = GL11.glGenTextures();
        upload();
    }

    private void upload() {
        final int pixelCount = this.resolution * this.resolution * 4;
        final ByteBuffer pixels = BufferUtils.createByteBuffer(pixelCount);
        final byte[] raw = generateDeterministicRgba(this.resolution);
        pixels.put(raw).flip();

        final int prev = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.textureId);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8,
                this.resolution, this.resolution, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, prev);
    }

    /** Pure-Java generator (testable without an OpenGL context). */
    public static byte[] generateDeterministicRgba(final int resolution) {
        final byte[] data = new byte[resolution * resolution * 4];
        final SplittableRandom rng = new SplittableRandom(0xA6215_5EA1L);
        rng.nextBytes(data);
        return data;
    }

    public int textureId() {
        return this.textureId;
    }

    public int resolution() {
        return this.resolution;
    }

    @Override
    public void close() {
        if (this.textureId != 0) {
            GL11.glDeleteTextures(this.textureId);
            this.textureId = 0;
        }
    }
}
