package com.agris.pipeline;

import com.agris.gl.FullscreenQuad;
import com.agris.gl.RenderTargetSet;
import com.agris.pack.PackDirectives;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * Represents one compiled fullscreen post-processing stage ({@code prepare*},
 * {@code deferred*}, {@code composite*}, or {@code final}).
 */
public final class CompositePass implements AutoCloseable {
    private final String passName;
    private final CompiledProgramBinding binding;
    private final boolean finalPresentationPass;

    public CompositePass(final String passName,
                         final CompiledProgramBinding binding,
                         final boolean finalPresentationPass) {
        this.passName = passName;
        this.binding = binding;
        this.finalPresentationPass = finalPresentationPass;
    }

    public String passName() {
        return this.passName;
    }

    public CompiledProgramBinding binding() {
        return this.binding;
    }

    public void execute(final UniformState state,
                        final RenderTargetSet targets,
                        final FullscreenQuad quad,
                        final PackDirectives packDirectives,
                        final int noiseTexId,
                        final int vanillaLightmapTexId,
                        final int destinationFbo,
                        final int destWidth,
                        final int destHeight) {
        final int[] mipInputs = this.binding.directives().mipmapInputs();
        if (mipInputs.length > 0) {
            targets.generateMipmapsFor(mipInputs);
        }

        final int[] drawBuffers = this.binding.directives().drawBuffers();
        if (this.finalPresentationPass) {
            targets.prepareForFinalPresentation();
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, destinationFbo);
            org.lwjgl.opengl.GL11.glViewport(0, 0, destWidth, destHeight);
        } else {
            targets.bindCompositeWriteFbo(drawBuffers);
        }

        this.binding.bindAndUpload(state, targets, noiseTexId, 0, 0, vanillaLightmapTexId, 0.0f);
        quad.draw();
        GL20.glUseProgram(0);

        if (!this.finalPresentationPass) {
            for (final int bufIdx : drawBuffers) {
                final Boolean explicitFlip = packDirectives.explicitFlip(this.passName, bufIdx);
                if (explicitFlip == null || explicitFlip) {
                    targets.flipBuffer(bufIdx);
                }
            }
        }
    }

    @Override
    public void close() {
        this.binding.close();
    }
}
