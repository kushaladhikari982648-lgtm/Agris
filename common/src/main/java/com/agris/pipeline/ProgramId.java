package com.agris.pipeline;

/**
 * Canonical OptiFine / Iris GBuffer and Shadow program identifiers with exact
 * fallback hierarchy (e.g. {@code gbuffers_terrain_cutout -> gbuffers_terrain ->
 * gbuffers_textured_lit -> gbuffers_textured -> gbuffers_basic}).
 */
public enum ProgramId {
    Basic("gbuffers_basic", null, 0.0f),
    Line("gbuffers_line", Basic, 0.0f),
    SkyBasic("gbuffers_skybasic", Basic, 0.0f),

    Textured("gbuffers_textured", Basic, 0.0f),
    SkyTextured("gbuffers_skytextured", Textured, 0.0f),
    Clouds("gbuffers_clouds", Textured, 0.1f),
    ArmorGlint("gbuffers_armor_glint", Textured, 0.1f),
    SpiderEyes("gbuffers_spidereyes", Textured, 0.1f),
    BeaconBeam("gbuffers_beaconbeam", Textured, 0.1f),

    TexturedLit("gbuffers_textured_lit", Textured, 0.0f),
    Terrain("gbuffers_terrain", TexturedLit, 0.1f),
    TerrainSolid("gbuffers_terrain_solid", Terrain, 0.0f),
    TerrainCutout("gbuffers_terrain_cutout", Terrain, 0.1f),
    DamagedBlock("gbuffers_damagedblock", Terrain, 0.1f),
    Water("gbuffers_water", Terrain, 0.0f),

    Block("gbuffers_block", Terrain, 0.1f),
    BlockTrans("gbuffers_block_translucent", Block, 0.1f),

    Entities("gbuffers_entities", TexturedLit, 0.1f),
    EntitiesTrans("gbuffers_entities_translucent", Entities, 0.1f),
    EntitiesGlowing("gbuffers_entities_glowing", Entities, 0.1f),
    Lightning("gbuffers_lightning", Entities, 0.0f),

    Particles("gbuffers_particles", TexturedLit, 0.1f),
    ParticlesTrans("gbuffers_particles_translucent", Particles, 0.1f),
    Weather("gbuffers_weather", TexturedLit, 0.1f),

    Hand("gbuffers_hand", TexturedLit, 0.1f),
    HandWater("gbuffers_hand_water", Hand, 0.1f),

    Shadow("shadow", null, 0.1f),
    ShadowSolid("shadow_solid", Shadow, 0.0f),
    ShadowCutout("shadow_cutout", Shadow, 0.1f),
    ShadowWater("shadow_water", Shadow, 0.1f),
    ShadowEntities("shadow_entities", Shadow, 0.1f),
    ShadowBlock("shadow_block", Shadow, 0.1f),

    Final("final", null, 0.0f);

    private final String sourceName;
    private final ProgramId fallback;
    private final float defaultAlphaRef;

    ProgramId(final String sourceName, final ProgramId fallback, final float defaultAlphaRef) {
        this.sourceName = sourceName;
        this.fallback = fallback;
        this.defaultAlphaRef = defaultAlphaRef;
    }

    public String sourceName() {
        return this.sourceName;
    }

    public ProgramId fallback() {
        return this.fallback;
    }

    public float defaultAlphaRef() {
        return this.defaultAlphaRef;
    }

    /**
     * Maps the active {@link WorldRenderingPhase}, shadow-pass flag, and vanilla
     * {@code ShaderInstance.getName()} to the canonical {@link ProgramId}.
     */
    public static ProgramId resolveForDraw(final WorldRenderingPhase phase,
                                           final boolean shadowPass,
                                           final String vanillaShaderName) {
        final String name = vanillaShaderName == null ? "" : vanillaShaderName;
        if (shadowPass) {
            if (phase == WorldRenderingPhase.TERRAIN_SOLID) {
                return ShadowSolid;
            }
            if (phase == WorldRenderingPhase.TERRAIN_CUTOUT || phase == WorldRenderingPhase.TERRAIN_CUTOUT_MIPPED) {
                return ShadowCutout;
            }
            if (phase == WorldRenderingPhase.TERRAIN_TRANSLUCENT) {
                return ShadowWater;
            }
            if (phase == WorldRenderingPhase.ENTITIES) {
                return ShadowEntities;
            }
            if (phase == WorldRenderingPhase.BLOCK_ENTITIES) {
                return ShadowBlock;
            }
            return Shadow;
        }

        return switch (phase) {
            case SKY, SUNSET, VOID -> SkyBasic;
            case SUN, MOON, CUSTOM_SKY -> SkyTextured;
            case STARS -> SkyBasic;
            case CLOUDS -> Clouds;
            case TERRAIN_SOLID -> TerrainSolid;
            case TERRAIN_CUTOUT, TERRAIN_CUTOUT_MIPPED, TRIPWIRE -> TerrainCutout;
            case TERRAIN_TRANSLUCENT -> Water;
            case DESTROY -> DamagedBlock;
            case OUTLINE -> Line;
            case RAIN_SNOW -> Weather;
            case WORLD_BORDER -> Textured;
            case HAND_SOLID -> Hand;
            case HAND_TRANSLUCENT -> HandWater;
            case PARTICLES -> Particles;
            case BLOCK_ENTITIES -> name.contains("translucent") ? BlockTrans : Block;
            case ENTITIES -> classifyEntityShader(name);
            default -> classifyVanillaShader(name);
        };
    }

    private static ProgramId classifyEntityShader(final String name) {
        if (name.contains("glint")) {
            return ArmorGlint;
        }
        if (name.contains("eyes")) {
            return SpiderEyes;
        }
        if (name.contains("beacon_beam")) {
            return BeaconBeam;
        }
        if (name.contains("lightning")) {
            return Lightning;
        }
        if (name.contains("outline")) {
            return EntitiesGlowing;
        }
        if (name.contains("translucent")) {
            return EntitiesTrans;
        }
        return Entities;
    }

    private static ProgramId classifyVanillaShader(final String name) {
        if (name.contains("lines")) {
            return Line;
        }
        if (name.contains("glint")) {
            return ArmorGlint;
        }
        if (name.contains("beacon_beam")) {
            return BeaconBeam;
        }
        if (name.contains("particle")) {
            return Particles;
        }
        if (name.contains("solid")) {
            return TerrainSolid;
        }
        if (name.contains("cutout")) {
            return TerrainCutout;
        }
        if (name.contains("translucent")) {
            return Water;
        }
        if (name.contains("entity") || name.contains("lightmap")) {
            return TexturedLit;
        }
        if (name.contains("tex")) {
            return Textured;
        }
        return Basic;
    }
}
