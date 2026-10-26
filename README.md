# Agris

**Agris** is a **mobile-optimized**, Iris- and OptiFine-compatible OpenGL shader mod built specifically for [**Aetherium**](https://github.com/entitybrian69-bit/aetherium) on **Fabric 1.20.1** (Java **17**).

Unlike standalone Iris or Oculus — which require Sodium and therefore force Aetherium to delegate its own chunk/rendering pipeline — **Agris runs directly on Aetherium + vanilla OpenGL with zero Sodium dependency**, keeping Aetherium 100% active while adding a Tile-Based Deferred Rendering (**TBDR**) mobile shader engine, dynamic FPS resolution scaling, camera-locked shadow interleaving, Android GL translator adaptation (`GL4ES`, `MobileGlues`, `GLES 3.0`, `Zink`, `LTW`), and four built-in shader packs led by **Agris Pocket Ultra (Mobile)**.

---

## Why Agris Exists for Aetherium (Especially on Mobile & Fabric 1.20.1)

Minecraft **1.20.1 on Fabric** is the flagship version across both desktop modpacks and Android launchers (**PojavLauncher**, **Zalith Launcher**, **Fold Craft Launcher**, **Amethyst**, **MojoLauncher**). However:
1. Standalone Iris requires **Sodium**, which Aetherium's `ModConflictScanner` classifies as `Severity.DELEGATE` — disabling Aetherium's own chunk pipeline.
2. Desktop shader packs hammer mobile GPUs (**Qualcomm Adreno GMEM**, **ARM Mali / Immortalis Tile Buffers**, **PowerVR**, **Apple Silicon**) with 4–8 `RGBA16F`/`RGBA32F` MRT buffers, per-pixel `mat4` shadow reconstructions, `glCopyTexSubImage2D` depth stalls, and `glReadPixels` sync flushes — forcing off-chip LPDDR4X/LPDDR5 tile spills and rapid thermal throttling.

**Agris solves both problems natively:**
- **Zero-Conflict Coexistence with Aetherium 1.20.1**: Declares `"provides": ["iris"]` in `fabric.mod.json` and exposes `net.irisshaders.iris.Iris` (`IrisApi` v0.2) + `net.coderbot.iris.Iris`. Aetherium recognizes Agris as a friendly `SHADER_OWNER` (`Severity.INFO`), disables its raw GL state cache, and keeps all of Aetherium's terrain, culling, memory, frame-pacing, HUD, zoom, and Android features active.
- **Native `Shaders` Tab inside `AetheriumScreen`**: `AetheriumPagesMixin` + `AetheriumTabInjector` inject a 14-row **Shaders** tab directly into Aetherium's settings screen — complete with Mobile TBDR mode, Dynamic FPS Resolution governor, Target FPS slider, World-Locked Shadow Cadence, and one-tap preset switching.
- **Built-In Mobile TBDR Shader Pack (`(agris-mobile)` + `shaderpacks/Agris-Pocket-Ultra-Mobile.zip`)**:
  - **Zero-Spill Single-MRT `RGBA8` GBuffer**: Fits 100% inside on-chip Adreno GMEM and ARM Mali Tile SRAM with zero secondary depth blits (`depthtex1 = 0`, `depthtex2 = 0`, `shadowtex1 = 0`, `shadowcolor* = 0`).
  - **Vertex-Interpolated Shadow Projection**: Computes `shadowProjection * (shadowModelView * playerPos)` per vertex in `gbuffers_terrain.vsh` rather than per pixel — eliminating **4 `mat4 * vec4` multiplies (64 FP32 MADs) per pixel**!
  - **1-Tap / 2-Tap Interleaved Gradient Noise (IGN) Shadows**, **Synchronized Waving Foliage**, **Schlick Fresnel Water Shimmer**, **Single-Pass 4-Tap Tent Bloom**, **5-Instruction Narkowicz ACES Filmic Tonemapping**, and **OLED Sub-Pixel Contrast Sharpening**.

---

## Mobile GPU & Android Launcher Optimizations

| Subsystem | Mobile Optimization | Hardware Benefit |
| --- | --- | --- |
| **`MobileGpuArchitecture`** | Auto-detects **Adreno**, **Mali/Immortalis**, **PowerVR**, **Samsung Xclipse**, **Apple TBDR**, and **Pojav / Zalith / Fold Craft / Mojo / Amethyst** | Selects optimal tile MRT cap, render scale, and GLSL dialect (`#version 300 es`, `#version 330 core`, or `#version 120` GL4ES) |
| **`MobileFrameGovernor`** | Zero-allocation EMA frame-time governor stepping scale in **5% increments** with a **1.5 s resize cooldown** | Sustains 60 / 90 / 120 FPS under thermal load without FBO reallocation stutter |
| **World-Locked Shadow Interleaving** | Re-renders shadow map at `1/2` or `1/3` frame cadence while translating `latchedShadowModelView` by `(cam - latchedCam)` | Cuts shadow-pass vertex/draw cost by **50–67%** with **zero shadow swimming** when moving |
| **`PackSamplerUsage` Buffer Stripping** | Static GLSL scan skips allocating/blitting unreferenced `depthtex1`, `depthtex2`, `shadowtex1`, and `shadowcolor0/1` | Saves ~30–40 MB VRAM and ~1.5 GB/s of LPDDR memory bandwidth at 60 FPS |
| **`R11F_G11F_B10F` HDR Compaction** | Compacts 64-bit `RGBA16F` color targets to 32-bit `R11F_G11F_B10F` when alpha is unused | **50% reduction** in HDR tile read/write bandwidth |
| **Hardware `glBlitFramebuffer` & Tile Discard** | Replaces `glCopyTexSubImage2D` on depth with DMA `glBlitFramebuffer` and calls `glInvalidateFramebuffer` on transient depth at frame end | Eliminates CPU-stalling depth format conversions and end-of-frame LPDDR depth writeback |
| **Zero-Stall CPU `centerDepthSmooth`** | Computes DoF focus depth analytically from `mc.hitResult` instead of `glReadPixels` | Zero GPU pipeline flush on tile-based GPUs |
| **Touchscreen-First GUI** | Finger drag list scrolling (`mouseDragged`) + direct horizontal slider scrubbing + one-tap `MobilePreset` button | Effortless touch navigation on Android phones & tablets |

---

## Four Built-In Shader Packs (+ Standalone `.zip`)

1. **`(agris-mobile)` — Agris Pocket Ultra (Mobile)** *(Default)*: Flagship zero-spill mobile TBDR shader pack with 5 profiles (`Battery_Saver`, `Pojav_GL4ES`, `Mobile_Balanced`, `Mobile_60FPS_Plus`, `Flagship_120Hz`). Also exported as a standalone archive at [`shaderpacks/Agris-Pocket-Ultra-Mobile.zip`](shaderpacks/Agris-Pocket-Ultra-Mobile.zip).
2. **`(agris-aetherial)` — Agris Aetherial**: Full multi-pass showcase pack with 4-tap rotated Poisson PCF shadows, GBuffer normal/lightmap encoding, sky scattering, HDR bloom, ACES Filmic tonemapping, and CAS sharpening.
3. **`(agris-lite)` — Agris Complementary Lite**: Lightweight single-MRT pack for laptops and older OpenGL drivers.
4. **`(internal)` — Internal Passthrough**: Clean diagnostic reference pipeline.

External `.zip` and folder shader packs placed in `<minecraft>/shaderpacks/` are also fully supported!

---

## Default Keybindings & Touch Controls

| Shortcut / Menu | Action |
| --- | --- |
| **`O`** | Open **Agris Shader Pack Manager** (supports touch swipe scrolling & one-tap `Mobile Preset`) |
| **`K`** | Toggle shaders **ON / OFF** live (zero restart) |
| **`R`** | Hot-reload active shader pack & recompile GLSL programs |
| **Aetherium Video Settings -> `Shaders` tab** | Native integrated Aetherium shader & mobile governor controls |
| **Vanilla Video Settings -> `Shader Packs...`** | Opens Agris even when running standalone without Aetherium |
| **`F3` Debug Overlay** | Shows `[Agris]` pack/shadow info + `[Agris Mobile]` GPU silicon, dynamic scale %, and EMA FPS |

---

## Building & Verifying (Fabric 1.20.1, Java 17)

### Standard Gradle Build

```bash
./gradlew check build
```

Produces the remapped Fabric 1.20.1 mod jar in `fabric/build/libs/`.

### Offline Verification Suite (ECJ + Real MC 1.20.1 Bytecode Probe + JUnit 5)

```bash
python3 tools/check.py
```

Verifies source hygiene, `CONFIG_SCHEMA.json` sync (29 options), all 26 Mixin injection targets against real Minecraft **1.20.1** bytecode (`tools/probe/1.20.1.txt`), strict Java 17 compilation (`common` + `fabric`), and all 20 JUnit 5 test suites.

---

## Documentation

- [`docs/MOBILE_OPTIMIZATION.md`](docs/MOBILE_OPTIMIZATION.md) — Deep dive into Agris's TBDR mobile engine, `Agris Pocket Ultra` shader math, and Android launcher tuning.
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — End-to-end rendering architecture, GLSL rewriter (`#version 330 core` / `#version 300 es` / `#version 120`), and Aetherium bridge design.
- [`docs/SHADERPACK_SPEC.md`](docs/SHADERPACK_SPEC.md) — Supported shader pack stages, uniforms, vertex attributes, directives, and mobile preprocessor macros.
- [`PORTING_MATRIX.md`](PORTING_MATRIX.md) — Fabric 1.20.1 target pins and Aetherium coexistence matrix.
- [`CONFIG_SCHEMA.json`](CONFIG_SCHEMA.json) — Machine-readable schema of all 29 `agris.json` options.

## License

Licensed under **GNU GPLv3** (`LICENSE`), matching Iris Shaders and Aetherium compatibility conventions.
