# Agris Porting & Compatibility Matrix

Agris is built specifically for [**Aetherium**](https://github.com/entitybrian69-bit/aetherium) on **Fabric 1.20.1** (Java **17**), using Aetherium's clean `common` + `fabric` layout, offline signature-stub compiler (`tools/stubcheck.py`), and bytecode call-site verifier (`tools/mixincheck.py`).

## Primary Target Line

| Minecraft | Java | Fabric Loader | Fabric API (Compile-Only) | Aetherium Compatibility | Verification Status |
| --- | --- | --- | --- | --- | --- |
| **1.20.1** | **17** | **`>=0.14.24`** | **`0.92.12+1.20.1`** | **Aetherium `1.x` (`v1.17.0+1.20.1`) & `0.1.0+1.20.1`** | **PRIMARY — 81 Java 17 files compile, 26/26 Mixin sites verified against 1.20.1 bytecode, 20/20 JUnit 5 suites + live joint Aetherium 1.20.1 smoke test passing** |

## Aetherium Coexistence Matrix

| Installed Mods | Agris State | Aetherium State | Notes |
| --- | --- | --- | --- |
| **Agris + Aetherium (Fabric 1.20.1)** | **ACTIVE** | **ACTIVE** | Recommended setup: native 14-row `Shaders` tab in `AetheriumScreen`, `SectionCull` shadow guard, `ColorFilter` auto-deferral, `ScaledRenderTarget` integration, Android launcher & TBDR silicon detection |
| **Agris (Standalone Fabric 1.20.1)** | **ACTIVE** | — | Accessible via `O` keybind, Mod Menu, or Vanilla `Video Settings -> Shader Packs...` |
| **Agris + Aetherium + Distant Horizons** | **ACTIVE** | **ACTIVE** | Distant Horizons binds via Agris's `IrisApi` v0.2 implementation |
| **Agris + Standalone Iris** | **STANDBY** | Active / Delegate | `ShaderConflictScanner` detects external shader pipeline (`Severity.HARD`) and disables Agris mixins cleanly |
| **Agris + VulkanMod / OptiFine** | **STANDBY** | Standby | Non-OpenGL or monolithic replacement detected; Agris stands down with diagnostic log |
| **Agris + Sodium** | **STANDBY** | Delegate | Sodium replaces vanilla `LevelRenderer` chunk draw calls (`Severity.DELEGATE`); Agris targets Aetherium + vanilla OpenGL |

## Android & Mobile Launcher Matrix (Fabric 1.20.1)

| Launcher / Renderer | Detected Translator | GLSL Target | Recommended Profile |
| --- | --- | --- | --- |
| **PojavLauncher / Zalith (`MobileGlues`)** | `MOBILEGLUES` | `#version 330 core` + `AGRIS_MOBILE_GPU` | `(agris-mobile)` — `Mobile_Balanced` / `Mobile_60FPS_Plus` |
| **PojavLauncher (`GL4ES` / `Holy-GL4ES`)** | `GL4ES` | `#version 120` + `AGRIS_GL4ES` (`RGBA8` safe FBOs, 1 MRT) | `(agris-mobile)` — `Pojav_GL4ES` |
| **Zalith (`LTW`) / Native GLES 3.x** | `LTW` / `GLES_NATIVE` | `#version 330 core` / `#version 300 es` | `(agris-mobile)` — `Mobile_Balanced` |
| **Fold Craft / Mojo / Turnip (`Zink`)** | `ZINK` | `#version 330 core` + Adreno/Mali TBDR compaction | `(agris-mobile)` — `Mobile_60FPS_Plus` / `Flagship_120Hz` |
| **macOS Apple Silicon (`M1`/`M2`/`M3`/`M4`)** | `DESKTOP_NATIVE` (`APPLE_TBDR`) | `#version 330 core` + `AGRIS_TBDR` | `(agris-mobile)` `Flagship_120Hz` or `(agris-aetherial)` |
