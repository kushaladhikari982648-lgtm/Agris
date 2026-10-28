package com.agris.gl;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * Synchronizes Agris's low-level OpenGL calls with the host renderer's cached state tracker
 * (Blaze3D {@code GlStateManager}, {@code BufferUploader}, and {@code RenderTarget} on
 * Minecraft 1.20.1), while keeping {@code com.agris.gl} and {@code com.agris.pipeline}
 * cleanly testable without Minecraft classes initialized.
 */
public interface GlStateSync {
    int GL_SCISSOR_TEST_ENUM = 0x0C11;
    int SCRATCH_TEXTURE_UNIT = GL13.GL_TEXTURE0 + 15;

    /** Default raw-LWJGL implementation used in standalone/unit-test contexts. */
    GlStateSync RAW = new GlStateSync() {
        @Override
        public void bindFramebuffer(final int target, final int fbo) {
            GL30.glBindFramebuffer(target, fbo);
        }

        @Override
        public void viewport(final int x, final int y, final int width, final int height) {
            GL11.glViewport(x, y, width, height);
        }

        @Override
        public void useProgram(final int programId) {
            GL20.glUseProgram(programId);
        }

        @Override
        public void bindTexture2D(final int unitIndex, final int textureId) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + unitIndex);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
        }

        @Override
        public void activeTextureUnit0() {
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
        }

        @Override
        public void bindScratchTexture2D(final int textureId) {
            GL13.glActiveTexture(SCRATCH_TEXTURE_UNIT);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
        }

        @Override
        public void endScratchTexture2D() {
            GL13.glActiveTexture(SCRATCH_TEXTURE_UNIT);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
        }

        @Override
        public void bindVertexArray(final int vao) {
            GL30.glBindVertexArray(vao);
        }

        @Override
        public void unbindVertexArray() {
            GL30.glBindVertexArray(0);
        }

        @Override
        public void prepareForClear() {
            GL11.glDisable(GL_SCISSOR_TEST_ENUM);
            GL11.glColorMask(true, true, true, true);
            GL11.glDepthMask(true);
        }

        @Override
        public void prepareFullscreenQuadState() {
            GL11.glDisable(GL_SCISSOR_TEST_ENUM);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glColorMask(true, true, true, true);
        }

        @Override
        public void restoreAfterFullscreenQuad() {
            GL11.glDepthMask(true);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
        }

        @Override
        public void prepareShadowPassState() {
            GL11.glDisable(GL_SCISSOR_TEST_ENUM);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(true);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glColorMask(true, true, true, true);
        }

        @Override
        public void restoreAfterShadowPass() {
            GL11.glEnable(GL11.GL_CULL_FACE);
        }

        @Override
        public void restoreMainRenderTarget(final int mainFbo, final int mainWidth, final int mainHeight) {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, mainFbo);
            GL11.glViewport(0, 0, mainWidth, mainHeight);
            GL11.glColorMask(true, true, true, true);
            GL11.glDepthMask(true);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL20.glUseProgram(0);
            GL30.glBindVertexArray(0);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
        }
    };

    final class Holder {
        static volatile GlStateSync active = RAW;

        private Holder() {
        }
    }

    static GlStateSync current() {
        return Holder.active;
    }

    static void install(final GlStateSync impl) {
        Holder.active = impl == null ? RAW : impl;
    }

    void bindFramebuffer(int target, int fbo);

    void viewport(int x, int y, int width, int height);

    void useProgram(int programId);

    void bindTexture2D(int unitIndex, int textureId);

    void activeTextureUnit0();

    void bindScratchTexture2D(int textureId);

    void endScratchTexture2D();

    void bindVertexArray(int vao);

    void unbindVertexArray();

    void prepareForClear();

    void prepareFullscreenQuadState();

    void restoreAfterFullscreenQuad();

    void prepareShadowPassState();

    void restoreAfterShadowPass();

    void restoreMainRenderTarget(int mainFbo, int mainWidth, int mainHeight);
}
