# Agris Shader Pack Specification

Agris loads shader packs from `<gameDir>/shaderpacks/` (both `.zip` archives and extracted folders containing a `shaders/` directory) in addition to four built-in packs (`(agris-mobile)`, `(agris-aetherial)`, `(agris-lite)`, `(internal)`).

---

## 1. Supported Program Stages

Dimension-specific overrides in `/shaders/world0/` (Overworld), `/shaders/world-1/` (Nether), `/shaders/world1/` (The End), or custom folders mapped in `dimension.properties` take precedence over `/shaders/`.

| Stage | Purpose | Fallback Chain |
| --- | --- | --- |
| `shadow` / `shadow_solid` / `shadow_cutout` | Directional sun/moon shadow map | — |
| `gbuffers_basic` | Untextured lines, debug boxes, flat geometry | — |
| `gbuffers_skybasic` | Sky dome & horizon gradient | `gbuffers_basic` |
| `gbuffers_skytextured` | Sun, moon, custom skyboxes | `gbuffers_textured` -> `gbuffers_basic` |
| `gbuffers_clouds` | Vanilla cloud geometry | `gbuffers_textured` -> `gbuffers_basic` |
| `gbuffers_terrain` | Solid, cutout, and cutout-mipped chunk terrain | `gbuffers_textured_lit` -> `gbuffers_textured` -> `gbuffers_basic` |
| `gbuffers_textured` | Unlit textured geometry & particles | `gbuffers_basic` |
| `gbuffers_textured_lit` | Lit particles & fallback lit geometry | `gbuffers_textured` -> `gbuffers_basic` |
| `gbuffers_block` | Block entities (chests, signs, banners, beds) | `gbuffers_terrain` |
| `gbuffers_entities` | Living entities, dropped items, projectiles | `gbuffers_textured_lit` |
| `gbuffers_armor_glint` | Enchantment glint overlay | `gbuffers_textured` |
| `gbuffers_spidereyes` | Emissive entity eyes (spider, enderman, phantom) | `gbuffers_textured` |
| `gbuffers_hand` | Opaque first-person held items & arm | `gbuffers_textured_lit` |
| `gbuffers_weather` | Rain & snow sheets | `gbuffers_textured_lit` |
| `gbuffers_water` | Translucent water, stained glass, ice | `gbuffers_terrain` |
| `gbuffers_hand_water` | Translucent held items | `gbuffers_hand` |
| `deferred`, `deferred1`..`deferred15` | Pre-translucent fullscreen lighting passes | Optional |
| `composite`, `composite1`..`composite15` | Post-translucent fullscreen post-processing | Optional |
| `final` | Final fullscreen output to screen / `ScaledRenderTarget` | Passthrough blit |

---

## 2. Standard Samplers & Framebuffer Attachments

| Sampler Name(s) | Description |
| --- | --- |
| `texture` / `gtexture` / `tex` | Bound block atlas, entity skin, or particle texture (unit 0) |
| `lightmap` | Vanilla block/sky lightmap texture (unit 1) |
| `normals` / `specular` | PBR normal / specular companion samplers |
| `colortex0` .. `colortex15` | Ping-pong GBuffer & composite color buffers (`gcolor`, `gdepth`, `gnormal`, `composite`, `gaux1..4` aliases supported) |
| `depthtex0` | Live scene depth buffer (includes translucents & hand) |
| `depthtex1` | Pre-translucent opaque scene depth snapshot |
| `depthtex2` | Pre-hand scene depth snapshot |
| `shadowtex0` / `shadow` | Full shadow map depth (includes translucent casters) |
| `shadowtex1` | Solid/cutout-only shadow map depth (for colored shadow tinting) |
| `shadowcolor0` / `shadowcolor` | Primary shadow color attachment (stained glass / water tint) |
| `shadowcolor1` | Secondary shadow color attachment |
| `noisetex` | Deterministic 256×256 RGBA pseudo-random noise texture |

---

## 3. Standard Uniforms & Attributes

### Matrices
- `gbufferModelView`, `gbufferModelViewInverse`, `gbufferPreviousModelView`
- `gbufferProjection`, `gbufferProjectionInverse`, `gbufferPreviousProjection`
- `shadowModelView`, `shadowModelViewInverse`, `shadowProjection`, `shadowProjectionInverse`

### Camera, World & Time
- `cameraPosition`, `previousCameraPosition`, `eyeAltitude`, `viewWidth`, `viewHeight`, `aspectRatio`, `near`, `far`
- `frameCounter`, `frameTime`, `frameTimeCounter`, `worldTime`, `worldDay`, `moonPhase`, `skyAngle`, `sunAngle`, `shadowAngle`
- `sunPosition`, `moonPosition`, `shadowLightPosition`, `upPosition`, `skyColor`, `fogColor`, `fogStart`, `fogEnd`, `fogDensity`, `fogShape`, `fogMode`
- `rainStrength`, `wetness`, `thunderStrength`, `isEyeInWater`, `blindness`, `nightVision`, `darknessFactor`, `screenBrightness`, `centerDepthSmooth`
- `eyeBrightness`, `eyeBrightnessSmooth`, `heldItemId`, `heldBlockLightValue`, `heldItemId2`, `heldBlockLightValue2`, `entityColor`, `entityId`, `blockEntityId`, `alphaTestRef`

### Vertex Attributes
- Core inputs: `vaPosition` (`Position`), `vaColor` (`Color`), `vaUV0` (`UV0`), `vaUV1` (`UV1`), `vaUV2` (`UV2`), `vaNormal` (`Normal`)
- Extended attributes: `mc_Entity` (location 11), `mc_midTexCoord` (location 12), `at_tangent` (location 13)

---

## 4. Android & Mobile GL Translator Adaptation

| Translator | Environment | Target GLSL | Max MRT | Float FBO Behavior |
| --- | --- | --- | --- | --- |
| `DESKTOP_NATIVE` | Windows / Linux / macOS OpenGL 3.3+ | `330 core` | 8 | Full `RGBA16F` / `RGBA32F` |
| `MOBILEGLUES` | Fold Craft / Zalith Vulkan translator | `330 core` | 8 | Full `RGBA16F` |
| `ZINK` | Mesa GL-on-Vulkan | `330 core` | 8 | Full `RGBA16F` |
| `LTW` | Zalith Launcher GL 3.3 translator | `330 core` | 8 | Full `RGBA16F` |
| `NG_GL4ES` | Krypton / Pojav GLES 3.x | `330 core` | 8 | Full `RGBA16F` |
| `ANGLE` | GLES / Vulkan ANGLE | `330 core` | 8 | Full `RGBA16F` |
| `GL4ES` | Classic PojavLauncher GLES 2.0 shim | `120` | 4 | Auto-demotes float FBOs -> `RGBA8` |
| `VIRGL` | Virtualized GPU | `330 core` | 4 | Auto-demotes float FBOs -> `RGBA8` |
