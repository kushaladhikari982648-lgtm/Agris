package com.agris.pipeline;

/**
 * Callback implemented by {@code AgrisClientHooks} to issue the actual shadow-pass
 * terrain, entity, block-entity, and translucent terrain draw calls while the
 * shadow framebuffer and shadow projection/modelView matrices are active.
 */
@FunctionalInterface
public interface ShadowSceneCallback {
    enum Stage {
        OPAQUE_TERRAIN,
        ENTITIES_AND_BLOCK_ENTITIES,
        TRANSLUCENT_TERRAIN
    }

    void renderStage(Stage stage, float[] shadowModelView, float[] shadowProjection);
}
