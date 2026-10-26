# Agris Technical Architecture

Agris is structured as a clean **Fabric 1.20.1** (`common` + `fabric`, Java **17**) repository matching [Aetherium](https://github.com/entitybrian69-bit/aetherium)'s architectural conventions: zero `@Overwrite` mixins, single-live-instance atomic JSON configuration, mobile TBDR bandwidth optimization, and reflective cross-mod coordination so Agris runs both **alongside Aetherium 1.20.1** and **standalone**.

---

## 1. Module Layout

```text
Agris/
├── common/       96% of all code: GL/TBDR resources, GLSL compiler/transformer, shader pack loader,
│                 mobile frame governor, shadow/GBuffer/composite pipeline, Iris API v0.2,
│                 touch-ready GUI screens, Mixins, and 20 JUnit 5 tests
├── fabric/       AgrisFabric entrypoint, FabricPlatformAdapter, ModMenu hook, fabric.mod.json
├── shaderpacks/  Standalone distributable Agris-Pocket-Ultra-Mobile.zip shaderpack
└── tools/        Offline ECJ + MC 1.20.1 signature-stub compiler, Mixin bytecode verifier, JUnit 5 runner
```

---

## 2. Frame Lifecycle & 8-Stage Rendering Pipeline

During `LevelRenderer.renderLevel(...)`, `LevelRendererMixin`, `GameRendererMixin`, `ShaderInstanceMixin`, and `UniformMixin` drive `AgrisClientHooks` -> `PipelineManager` -> `ShaderPipeline` through a deterministic frame graph:

1. **Frame Begin (`onBeginLevelRender`)**:
   - Captures `mc.getMainRenderTarget()` inside `LevelRenderer.renderLevel` — automatically inheriting Aetherium's `ScaledRenderTarget` FBO (`renderScale`) while keeping the HUD crisp at native window resolution.
   - Updates `UniformState` (camera position & previous-frame camera delta, `gbufferModelView`, `gbufferProjection`, inverses, sun/moon vectors with `sunPathRotation`, `rainStrength`, exponentially smoothed `wetness` and `eyeBrightnessSmooth`, held-item light levels, fog parameters, and custom RPN `shaders.properties` expressions).
   - Evaluates `AetheriumBridge.syncShaderPackActive(...)` to defer Aetherium's post-processing `ColorFilter` while Agris owns tonemapping.
2. **Real-Time Directional Shadow Pass (`executeShadowPass`)**:
   - Calls `AetheriumBridge.beginShadowPass()` to disarm `SectionCull` and `Tuning.directionalCulling` so shadow casters behind the player's camera are not culled.
   - Computes texel-snapped orthographic `shadowProjection` and `shadowModelView` (`MathUtil.createShadowOrthoMatrix` / `createShadowModelView`) to prevent sub-pixel shadow shimmering.
   - Renders `solid`, `cutout_mipped`, and `cutout` chunk layers via `LevelRendererInvoker`, snapshots `shadowtex1` (pre-translucent shadow depth), renders `translucent` stained glass/water into `shadowcolor0`/`shadowcolor1`, and restores Aetherium's culling state.
3. **Opaque Sky & Terrain GBuffer Passes**:
   - Routes vanilla `ShaderInstance.apply()` calls to compiled `gbuffers_*` programs based on `WorldRenderingPhase` (`ProgramId.resolveForDraw`), binding the active program's `/* RENDERTARGETS: ... */` FBO attachments and redirecting `Uniform.set(FFF)` / `Uniform.upload()` (`CHUNK_OFFSET`) directly onto `agris_ChunkOffset` with zero vanilla uniform overhead.
4. **Deferred Lighting Passes (`deferred` .. `deferred15`)**:
   - Copies `depthtex0` -> `depthtex1` (pre-translucent depth) before translucent water/particles draw, generates requested mipmaps, and runs fullscreen `deferred*` lighting passes with ping-pong attachment flipping.
5. **Translucent Terrain, Weather & Hand Depth**:
   - Renders `translucent` and `tripwire` chunk layers (`gbuffers_water`) and weather (`gbuffers_weather`).
   - Captures `depthtex0` -> `depthtex2` right before `GameRenderer.renderItemInHand` (`GameRendererMixin`) so Depth-of-Field shaders keep held items sharp.
6. **Composite Post-Processing (`composite` .. `composite15`)**:
   - Executes fullscreen composite passes (bloom, volumetric fog, reflections, motion blur), updating exponentially smoothed `centerDepthSmooth` from center-screen `depthtex0`.
7. **Final Output (`final`)**:
   - Blits and tonemaps the final ping-pong `colortex0` directly into the captured main `RenderTarget` FBO (`mainFramebufferId`), restoring every OpenGL state binding (`FBO`, `VAO`, `Program`, `ActiveTexture`, `Blend`, `DepthMask`, `Viewport`) cleanly for vanilla GUI and Aetherium HUD overlays.

---

## 3. GLSL Compatibility-to-Core / GLES 3.0 Transformer (`GlslTransformer`)

Minecraft 1.20.1 runs on an **OpenGL 3.2/3.3 Core Profile** context on desktop (and **OpenGL ES 3.x / GL4ES / MobileGlues / Zink** on mobile), whereas the majority of OptiFine/Iris shader packs begin with `#version 120` or `#version 150 compatibility`. `GlslTransformer` upgrades shaders deterministically at load time (`#define MC_VERSION 12001`):

- Resolves nested relative and root-anchored `#include` directives (`GlslIncludeResolver`) and substitutes user `#define` / `const` option overrides (`ShaderOptionParser`).
- Upgrades `#version` to `#version 330 core` on desktop/Vulkan translators (`MobileGlues`, `Zink`, `LTW`, `NG-GL4ES`), `#version 300 es` with explicit precision qualifiers on native OpenGL ES 3.x (`GLES_NATIVE`), or preserves `#version 120` compatibility semantics on `GL4ES`.
- Injects `#define AGRIS_MOBILE_GPU 1`, `#define AGRIS_TBDR 1`, `#define AGRIS_GLES 1`, and `#define AGRIS_GL4ES 1` when applicable so shader packs can specialize mobile code paths.
- Rewrites `attribute` / `varying` declarations into `in` / `out`, expands `ftransform()`, replaces `gl_Vertex`, `gl_Color`, `gl_Normal`, `gl_MultiTexCoord0/1` with `VertexFormat`-bound inputs (`vaPosition`, `vaColor`, `vaNormal`, `vaUV0`, `vaUV2`), and maps `gl_ModelViewMatrix`, `gl_ProjectionMatrix`, `gl_NormalMatrix`, `gl_TextureMatrix[0..1]`, and `gl_Fog` to Agris uniforms.
- Converts `gl_FragColor` and `gl_FragData[k]` into explicit `layout(location = i) out vec4 agris_FragDataN;` outputs compacted strictly to the program's `RENDERTARGETS` / `DRAWBUFFERS` list.
- Rewrites legacy `texture2D`, `texture2DLod`, `shadow2D`, and `shadow2DProj` calls into overloaded GLSL 330 sampling helpers supporting both `sampler2D` and hardware `sampler2DShadow`.

---

## 4. Coexistence Contract with Aetherium

| Subsystem | Aetherium Behavior | Agris Coordination |
| --- | --- | --- |
| `ModConflictScanner` | Treats `"iris"` as `Severity.INFO` (`SHADER_OWNER`); treats `"sodium"` as `Severity.DELEGATE` | `fabric.mod.json` declares `"provides": ["iris"]` with zero Sodium dependency; Aetherium stays 100% active |
| `RenderMods` (`gl_state_cache`) | Auto-disables raw GL state cache when `net.irisshaders.iris.Iris` or `net.coderbot.iris.Iris` exists | Agris ships both classes AND `AetheriumBridge` clears `Tuning.glStateCache` when shaders engage |
| `ColorFilter` | Runs post-world vibrance/LUT shader when `Tuning.shaderPreset != 0` | `AetheriumBridge` saves & switches `shaderPreset` to `OFF` while Agris tonemaps, restoring it when shaders turn off |
| `SectionCull` | Culls chunk sections behind the player's camera | `AetheriumBridge` calls `SectionCull.end()` and pauses `Tuning.directionalCulling` strictly during the shadow pass |
| `ScaledRenderTarget` | Binds scaled world FBO inside `LevelRenderer.renderLevel` | Agris captures `mc.getMainRenderTarget()` inside `renderLevel` and outputs `final` directly into that FBO |
| `AetheriumPages` | Builds sidebar pages for `AetheriumScreen` | `AetheriumPagesMixin` (plugin-gated) + `AetheriumTabInjector` insert a native **Shaders** `Page` |
