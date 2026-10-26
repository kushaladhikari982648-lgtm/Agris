package com.agris.pipeline;

/**
 * Granular world rendering phases tracked across {@code LevelRenderer.renderLevel}
 * and {@code GameRenderer.renderItemInHand}, matching Iris's phase semantics.
 */
public enum WorldRenderingPhase {
    NONE,
    SKY,
    SUNSET,
    CUSTOM_SKY,
    SUN,
    MOON,
    STARS,
    VOID,
    TERRAIN_SOLID,
    TERRAIN_CUTOUT_MIPPED,
    TERRAIN_CUTOUT,
    ENTITIES,
    BLOCK_ENTITIES,
    DESTROY,
    OUTLINE,
    DEBUG,
    HAND_SOLID,
    TERRAIN_TRANSLUCENT,
    TRIPWIRE,
    PARTICLES,
    CLOUDS,
    RAIN_SNOW,
    WORLD_BORDER,
    HAND_TRANSLUCENT;

    public boolean isSkyPhase() {
        return this == SKY || this == SUNSET || this == CUSTOM_SKY
                || this == SUN || this == MOON || this == STARS || this == VOID;
    }

    public boolean isTerrainPhase() {
        return this == TERRAIN_SOLID || this == TERRAIN_CUTOUT_MIPPED
                || this == TERRAIN_CUTOUT || this == TERRAIN_TRANSLUCENT || this == TRIPWIRE;
    }
}
