# Agris Mobile TBDR & Android Optimization Guide

Mobile GPUs (**Qualcomm Adreno**, **ARM Mali / Immortalis**, **Imagination PowerVR**, **Samsung Xclipse**, **Apple Silicon**) use **Tile-Based Deferred Rendering (TBDR)**. Instead of writing every triangle directly to system DRAM like desktop GPUs, TBDR GPUs bin geometry into small screen tiles ($16\times 16$ or $32\times 32$ pixels), shade an entire tile inside ultra-fast on-chip SRAM (**GMEM** on Adreno, **Tile Buffer** on Mali), and flush only the final resolved color tile out to LPDDR4X/LPDDR5 RAM.

Desktop Minecraft shader packs accidentally break TBDR fast paths in four ways:
1. Binding 4–8 wide `RGBA16F`/`RGBA32F` MRT attachments overflows on-chip tile SRAM (128 bits/pixel ceiling on midrange Mali), forcing every pixel to spill to off-chip DRAM.
2. Reconstructing world space from depth in fragment shaders costs 4 `mat4 * vec4` multiplies (64 FP32 MADs) per pixel across 2.5M+ pixels per frame.
3. Calling `glCopyTexSubImage2D` on depth textures or `glReadPixels` for `centerDepthSmooth` stalls the GPU tile-binning pipeline.
4. Re-rendering full chunk shadow maps every single frame at 60–120 Hz saturates mobile big.LITTLE CPU cores and vertex tilers.

Agris solves all four at both the **engine level** and the **shader pack level**.

---

## 1. Engine-Level Mobile Optimizations

### 1.1 Silicon & Launcher Detection (`MobileGpuArchitecture`)
At GL context init, Agris inspects `GL_VENDOR`, `GL_RENDERER`, `GL_VERSION`, Aetherium's `AndroidEnvironment`, and launcher environment variables (`POJAV_RENDERER`, `ZALITH_VERSION_CODE`, `FCL_NATIVEDIR`, `AMETHYST_RENDERER`, `MOJO_RENDERER`) to classify both the launcher and underlying GPU microarchitecture (`ADRENO`, `MALI`, `POWERVR`, `XCLIPSE`, `APPLE_TBDR`, `TEGRA`, `GENERIC_MOBILE`, `DESKTOP`).

### 1.2 Zero-Allocation Dynamic FPS Governor (`MobileFrameGovernor`)
- Tracks an outlier-clamped exponential moving average (`alpha = 0.12`) of frame time with zero heap allocations.
- Steps internal GBuffer/composite resolution in quantized **5% increments** (`mobile.min_scale` .. `quality.render_scale`) behind a **1.5-second resize cooldown** so FBOs never thrash during single-frame spikes.

### 1.3 World-Locked Shadow Interleaving (`UniformState.latchedShadowModelView`)
When `mobile.shadow_cadence` is `2` (half-rate) or `3` (third-rate) — or `0` (`Auto` on mobile):
- On active shadow frames, Agris renders the shadow FBO and records `latchedShadowModelView` + `(latchedShadowCamX, Y, Z)`.
- On interleaved frames, Agris **skips the entire shadow render pass** (0 shadow draw calls!) and post-multiplies `latchedShadowModelView` by `T(camX - latchedCamX, camY - latchedCamY, camZ - latchedCamZ)`. Because `playerPos = worldPos - cam`, the camera delta cancels algebraically to IEEE-754 precision — **eliminating shadow swimming completely while cutting shadow CPU/GPU cost by 50–67%**.
- Sky-angle jumps (`/time set`) or player teleports (`> 32` blocks) automatically trigger an immediate shadow refresh.

### 1.4 Static Attachment Stripping & `R11F_G11F_B10F` Compaction
- `PackSamplerUsage.analyze` scans active GLSL stages once at pipeline load. Unreferenced `depthtex1`, `depthtex2`, `shadowtex1`, and `shadowcolor0/1` are never allocated and never blitted.
- When `shadowcolor0/1` are unused, `RenderTargetSet` builds a pure **depth-only shadow FBO** (`glDrawBuffers(GL_NONE)` / `glReadBuffer(GL_NONE)`).
- On TBDR GPUs with `mobile.half_bandwidth_hdr = true`, 64-bit `RGBA16F` color buffers whose `.a` channel is unused are compacted to 32-bit `R11F_G11F_B10F` (halving tile memory bandwidth).
- Depth snapshots use hardware DMA `GL30.glBlitFramebuffer`, and transient GBuffer depth tiles are invalidated before final presentation via `glInvalidateFramebuffer` (`GlTileHint`) so the GPU skips writing depth back to LPDDR RAM.

### 1.5 Native GLES 3.0 Shader Emission (`GlslTransformer`)
When running on a native OpenGL ES 3.x context (`Translator.GLES_NATIVE`), `GlslTransformer` emits `#version 300 es` with explicit `precision highp float; precision highp int; precision mediump sampler2D; precision highp sampler2DShadow;` qualifiers and injects `#define AGRIS_MOBILE_GPU 1`, `#define AGRIS_TBDR 1`, and `#define AGRIS_GLES 1`.

---

## 2. `Agris Pocket Ultra (Mobile)` Architecture (`(agris-mobile)`)

Shipped both built-in as `(agris-mobile)` and as a standalone `.zip` at `shaderpacks/Agris-Pocket-Ultra-Mobile.zip`:

- **Single `RGBA8` Color Attachment (`RENDERTARGETS: 0`)**: 32 bits Color + 32 bits Depth = **64 bits/pixel** total tile footprint — fits inside **every** Adreno GMEM and ARM Mali Tile Buffer ever shipped for OpenGL ES 3.0+ with zero DRAM spill.
- **Vertex-Projected Shadow Coordinates**: `gbuffers_terrain.vsh` transforms vertices into `vShadowCoord` once per vertex and lets the hardware triangle setup unit interpolate across pixels, eliminating 4 `mat4` multiplies per fragment.
- **1-Tap Hardware / 2-Tap IGN Rotated Shadow Filter**: Produces smooth, temporally stable penumbras in just 1–2 `sampler2DShadow` taps.
- **Single-Pass `final.fsh`**: Combines sub-pixel contrast sharpening (`MOBILE_SHARPEN` — making 70–85% render scale look native on high-DPI OLED phone screens), 4-tap diagonal tent glow (`BLOOM_TENT`), rational linear-depth aerial fog, and 5-instruction Narkowicz ACES Filmic tonemapping in a single fullscreen pass.
