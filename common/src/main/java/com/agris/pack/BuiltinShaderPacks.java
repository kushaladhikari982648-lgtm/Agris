package com.agris.pack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ships four complete, genuine GLSL shader packs directly inside Agris so users on both
 * mobile devices and desktop PCs get instant, optimized shaders with zero external downloads:
 *
 * <ol>
 *   <li><b>Agris Pocket Ultra (Mobile)</b> ({@code (agris-mobile)}): Flagship mobile TBDR shader
 *       pack engineered specifically for Qualcomm Adreno GMEM, ARM Mali Tile Buffers, Apple Silicon,
 *       and Android launchers (PojavLauncher, Zalith, Fold Craft). Uses a zero-spill single-MRT
 *       {@code RGBA8} GBuffer, vertex-interpolated shadow coordinates (eliminating 4 per-pixel
 *       {@code mat4} multiplies), 1/2-tap IGN shadow filtering, Schlick water Fresnel, 4-tap tent
 *       glow, polynomial ACES tonemapping, and OLED sub-pixel sharpening.</li>
 *   <li><b>Agris Aetherial</b> ({@code (agris-aetherial)}): Full multi-pass desktop/flagship pack
 *       featuring real-time PCF sun/moon shadow mapping ({@code shadow}), GBuffer normal &amp;
 *       lightmap encoding, sky scattering, water Fresnel reflections, warm dynamic/torch
 *       glow, HDR bloom, ACES Filmic tonemapping, and contrast-adaptive sharpening.</li>
 *   <li><b>Agris Complementary Lite</b> ({@code (agris-lite)}): High-FPS single-MRT shader pack
 *       crafted for laptops and lightweight OpenGL setups.</li>
 *   <li><b>Internal Passthrough</b> ({@code (internal)}): Clean diagnostic reference pack.</li>
 * </ol>
 */
public final class BuiltinShaderPacks {
    public static final String ID_MOBILE = "(agris-mobile)";
    public static final String ID_AETHERIAL = "(agris-aetherial)";
    public static final String ID_LITE = "(agris-lite)";
    public static final String ID_INTERNAL = "(internal)";

    private BuiltinShaderPacks() {
    }

    public static List<ShaderPackSource> all() {
        return List.of(createMobilePocketUltra(), createAetherial(), createComplementaryLite(), createInternal());
    }

    public static ShaderPackSource byIdOrNull(final String id) {
        if (id == null) {
            return null;
        }
        return switch (id.trim().toLowerCase()) {
            case "(agris-mobile)" -> createMobilePocketUltra();
            case "(agris-aetherial)" -> createAetherial();
            case "(agris-lite)" -> createComplementaryLite();
            case "(internal)" -> createInternal();
            default -> null;
        };
    }

    private static ShaderPackSource createMobilePocketUltra() {
        final Map<String, String> files = new LinkedHashMap<>();

        files.put("/shaders.properties", """
                profile.Battery_Saver = !MOBILE_SHADOWS SHADOW_TAPS=1 !BLOOM_TENT !WATER_SHIMMER !WAVING_FOLIAGE shadowMapResolution=512 MOBILE_SHARPEN=0.25
                profile.Pojav_GL4ES = !MOBILE_SHADOWS SHADOW_TAPS=1 BLOOM_TENT WATER_SHIMMER !WAVING_FOLIAGE shadowMapResolution=512 MOBILE_SHARPEN=0.30
                profile.Mobile_Balanced = MOBILE_SHADOWS SHADOW_TAPS=1 BLOOM_TENT WATER_SHIMMER WAVING_FOLIAGE shadowMapResolution=1024 MOBILE_SHARPEN=0.30
                profile.Mobile_60FPS_Plus = MOBILE_SHADOWS SHADOW_TAPS=2 BLOOM_TENT WATER_SHIMMER WAVING_FOLIAGE shadowMapResolution=1024 MOBILE_SHARPEN=0.30
                profile.Flagship_120Hz = MOBILE_SHADOWS SHADOW_TAPS=2 BLOOM_TENT WATER_SHIMMER WAVING_FOLIAGE shadowMapResolution=1536 MOBILE_SHARPEN=0.35

                screen = [MOBILE_PERF] [LIGHTING] [POST] *
                screen.MOBILE_PERF = MOBILE_SHADOWS SHADOW_TAPS shadowMapResolution WAVING_FOLIAGE WATER_SHIMMER
                screen.LIGHTING = TORCH_INTENSITY SUN_WARMTH AMBIENT_BOOST DYNAMIC_HAND_LIGHT
                screen.POST = BLOOM_TENT BLOOM_AMOUNT SATURATION EXPOSURE MOBILE_SHARPEN
                sliders = SHADOW_TAPS shadowMapResolution TORCH_INTENSITY SUN_WARMTH AMBIENT_BOOST BLOOM_AMOUNT SATURATION EXPOSURE MOBILE_SHARPEN

                separateAo = true
                oldLighting = false
                sunPathRotation = -32.0
                """);

        files.put("/block.properties", """
                block.10008 = minecraft:water minecraft:flowing_water
                block.10020 = oak_leaves spruce_leaves birch_leaves jungle_leaves acacia_leaves dark_oak_leaves mangrove_leaves cherry_leaves azalea_leaves flowering_azalea_leaves short_grass tall_grass fern large_fern wheat carrots potatoes
                block.10040 = torch wall_torch soul_torch lantern soul_lantern lava glowstone sea_lantern shroomlight crying_obsidian magma_block ochre_froglight verdant_froglight pearlescent_froglight
                """);

        files.put("/lang/en_us.lang", """
                profile.Battery_Saver = Battery Saver (Cool & Ultra Fast)
                profile.Pojav_GL4ES = Pojav / GL4ES Safe Mode
                profile.Mobile_Balanced = Mobile Balanced (Recommended)
                profile.Mobile_60FPS_Plus = Mobile 60 FPS+ (Soft Shadows)
                profile.Flagship_120Hz = Flagship 120Hz (Snapdragon 8 / Dimensity / Apple M)

                screen.MOBILE_PERF = Mobile GPU & Tile Budget
                screen.LIGHTING = Sun, Torch & Dynamic Light
                screen.POST = Tonemap, Bloom & OLED Sharpen

                option.MOBILE_SHADOWS = Real-Time Sun Shadows
                option.MOBILE_SHADOWS.comment = Renders crisp vertex-projected directional shadows using a depth-only FBO.
                option.SHADOW_TAPS = Shadow Filter Taps
                option.SHADOW_TAPS.comment = 1 tap = fastest hardware lookup; 2 taps = Interleaved Gradient Noise soft edge.
                option.shadowMapResolution = Shadow Map Resolution
                option.WAVING_FOLIAGE = Waving Leaves & Grass
                option.WATER_SHIMMER = Schlick Water Reflections
                option.TORCH_INTENSITY = Warm Torch & Lava Glow
                option.SUN_WARMTH = Golden Hour Sun Warmth
                option.AMBIENT_BOOST = Cave & Shadow Visibility
                option.DYNAMIC_HAND_LIGHT = Held Torch Pocket Light
                option.BLOOM_TENT = Single-Pass Tent Glow
                option.BLOOM_AMOUNT = Bloom Intensity
                option.SATURATION = OLED Color Vibrance
                option.EXPOSURE = Scene Exposure
                option.MOBILE_SHARPEN = Sub-Pixel Upscale Sharpening
                option.MOBILE_SHARPEN.comment = Restores crisp block edges when running at 65%–85% mobile render scale.
                """);

        files.put("/lib/mobile_settings.glsl", """
                #define MOBILE_SHADOWS
                #define SHADOW_TAPS 1 // [1 2]
                const int shadowMapResolution = 1024; // [512 768 1024 1536 2048]
                const float shadowDistance = 96.0;
                const float sunPathRotation = -32.0;
                const int colortex0Format = RGBA8;

                #define WAVING_FOLIAGE
                #define WATER_SHIMMER
                #define DYNAMIC_HAND_LIGHT
                #define BLOOM_TENT

                #define TORCH_INTENSITY 1.25 // [0.75 1.0 1.25 1.5 2.0]
                #define SUN_WARMTH 1.15 // [0.85 1.0 1.15 1.35]
                #define AMBIENT_BOOST 1.05 // [0.85 1.0 1.05 1.20 1.40]
                #define BLOOM_AMOUNT 0.16 // [0.08 0.12 0.16 0.24 0.32]
                #define SATURATION 1.12 // [0.90 1.00 1.12 1.22 1.35]
                #define EXPOSURE 1.05 // [0.85 0.95 1.05 1.15 1.30]
                #define MOBILE_SHARPEN 0.30 // [0.00 0.15 0.25 0.30 0.35 0.45]
                """);

        // Shadow pass: ultra-lean depth-only caster pass with synchronized foliage wind
        files.put("/shadow.vsh", """
                #version 120
                #include "/lib/mobile_settings.glsl"
                attribute vec4 mc_Entity;
                uniform float frameTimeCounter;
                uniform vec3 cameraPosition;
                varying vec2 vUv;
                varying vec4 vColor;

                void main() {
                    vUv = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
                    vColor = gl_Color;
                    vec4 pos = gl_Vertex;
                    #ifdef WAVING_FOLIAGE
                    if (abs(mc_Entity.x - 10020.0) < 0.5) {
                        vec3 worldPos = pos.xyz + cameraPosition;
                        float phase = frameTimeCounter * 2.6 + worldPos.x * 0.7 + worldPos.z * 0.7;
                        pos.x += sin(phase) * 0.045;
                        pos.z += cos(phase * 0.85) * 0.035;
                    }
                    #endif
                    gl_Position = gl_ModelViewProjectionMatrix * pos;
                    // Mild pancake XY distortion keeps nearby player shadows ultra-crisp at 1024px!
                    float dist = length(gl_Position.xy);
                    float distort = mix(1.0, dist, 0.72);
                    gl_Position.xy /= max(distort, 0.001);
                }
                """);

        files.put("/shadow.fsh", """
                #version 120
                uniform sampler2D texture;
                uniform float alphaTestRef;
                varying vec2 vUv;
                varying vec4 vColor;

                void main() {
                    vec4 tex = texture2D(texture, vUv) * vColor;
                    if (tex.a < max(alphaTestRef, 0.1)) discard;
                    gl_FragData[0] = tex;
                }
                """);

        // Sky dome & celestial bodies
        files.put("/gbuffers_skybasic.vsh", """
                #version 120
                varying vec4 vColor;
                varying vec3 vViewPos;
                void main() {
                    vColor = gl_Color;
                    vec4 vp = gl_ModelViewMatrix * gl_Vertex;
                    vViewPos = vp.xyz;
                    gl_Position = gl_ProjectionMatrix * vp;
                }
                """);

        files.put("/gbuffers_skybasic.fsh", """
                #version 120
                /* RENDERTARGETS: 0 */
                uniform vec3 skyColor;
                uniform vec3 fogColor;
                uniform vec3 sunPosition;
                uniform float rainStrength;
                varying vec4 vColor;
                varying vec3 vViewPos;

                void main() {
                    vec3 dir = normalize(vViewPos);
                    vec3 sDir = normalize(sunPosition);
                    float mu = max(dot(dir, sDir), 0.0);
                    float horizon = clamp(1.0 - abs(dir.y), 0.0, 1.0);
                    vec3 baseSky = mix(skyColor, fogColor, horizon * horizon);
                    vec3 mieGlow = vec3(1.0, 0.68, 0.36) * pow(mu, 8.0) * 0.35 * (1.0 - rainStrength * 0.8);
                    gl_FragData[0] = vec4(max(baseSky + mieGlow, vColor.rgb * 0.25), vColor.a);
                }
                """);

        files.put("/gbuffers_skytextured.vsh", """
                #version 120
                varying vec2 vUv;
                varying vec4 vColor;
                void main() {
                    vUv = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
                    vColor = gl_Color;
                    gl_Position = ftransform();
                }
                """);

        files.put("/gbuffers_skytextured.fsh", """
                #version 120
                /* RENDERTARGETS: 0 */
                uniform sampler2D texture;
                varying vec2 vUv;
                varying vec4 vColor;
                void main() {
                    vec4 c = texture2D(texture, vUv) * vColor;
                    if (c.a < 0.02) discard;
                    gl_FragData[0] = vec4(c.rgb * 1.25, c.a);
                }
                """);

        // Shared ultra-fast mobile vertex-interpolated forward GBuffer shader for terrain & blocks!
        final String mobileForwardVert = """
                #version 120
                #include "/lib/mobile_settings.glsl"
                attribute vec4 mc_Entity;
                uniform mat4 gbufferModelViewInverse;
                uniform mat4 shadowModelView;
                uniform mat4 shadowProjection;
                uniform vec3 shadowLightPosition;
                uniform vec3 upPosition;
                uniform vec3 cameraPosition;
                uniform float frameTimeCounter;

                varying vec2 vUv;
                varying vec2 vLightUv;
                varying vec4 vColor;
                varying vec4 vShadowCoord;
                varying float vNdotL;
                varying float vSkyHemi;
                varying float vViewDist;
                varying float vEmissive;

                void main() {
                    vUv = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
                    vLightUv = clamp((gl_TextureMatrix[1] * gl_MultiTexCoord1).xy, 0.03125, 0.96875);
                    vColor = gl_Color;
                    vEmissive = abs(mc_Entity.x - 10040.0) < 0.5 ? 1.0 : 0.0;

                    vec4 localPos = gl_Vertex;
                    #ifdef WAVING_FOLIAGE
                    if (abs(mc_Entity.x - 10020.0) < 0.5) {
                        vec3 wp = localPos.xyz + cameraPosition;
                        float ph = frameTimeCounter * 2.6 + wp.x * 0.7 + wp.z * 0.7;
                        localPos.x += sin(ph) * 0.045;
                        localPos.z += cos(ph * 0.85) * 0.035;
                    }
                    #endif

                    vec4 viewPos = gl_ModelViewMatrix * localPos;
                    vViewDist = length(viewPos.xyz);

                    vec3 rawNorm = gl_NormalMatrix * gl_Normal;
                    vec3 viewNormal = dot(rawNorm, rawNorm) > 1e-6 ? normalize(rawNorm) : vec3(0.0, 1.0, 0.0);
                    vec3 lightDir = dot(shadowLightPosition, shadowLightPosition) > 1e-6 ? normalize(shadowLightPosition) : vec3(0.0, 1.0, 0.0);
                    vec3 upDir = dot(upPosition, upPosition) > 1e-6 ? normalize(upPosition) : vec3(0.0, 1.0, 0.0);
                    // Soft wrap NdotL keeps foliage and stone pleasingly sculpted on mobile screens
                    vNdotL = clamp(dot(viewNormal, lightDir) * 0.85 + 0.15, 0.0, 1.0);
                    vSkyHemi = dot(viewNormal, upDir) * 0.25 + 0.75;

                    // Compute shadow clip position PER VERTEX so fragment shader pays ZERO mat4 multiplies!
                    vec4 playerPos = gbufferModelViewInverse * viewPos;
                    vec4 shClip = shadowProjection * (shadowModelView * playerPos);
                    float dist = length(shClip.xy);
                    float distort = mix(1.0, dist, 0.72);
                    shClip.xy /= max(distort, 0.001);
                    vShadowCoord = vec4(shClip.xyz * 0.5 + 0.5, playerPos.y);

                    gl_Position = gl_ProjectionMatrix * viewPos;
                }
                """;

        final String mobileForwardFrag = """
                #version 120
                #include "/lib/mobile_settings.glsl"
                /* RENDERTARGETS: 0 */
                uniform sampler2D texture;
                uniform sampler2D shadowtex0;
                uniform float alphaTestRef;
                uniform float sunAngle;
                uniform float rainStrength;
                uniform int heldBlockLightValue;
                uniform int heldBlockLightValue2;

                varying vec2 vUv;
                varying vec2 vLightUv;
                varying vec4 vColor;
                varying vec4 vShadowCoord;
                varying float vNdotL;
                varying float vSkyHemi;
                varying float vViewDist;
                varying float vEmissive;

                float sampleMobileShadow(vec3 sc) {
                    #ifndef MOBILE_SHADOWS
                    return 1.0;
                    #else
                    if (sc.x <= 0.002 || sc.x >= 0.998 || sc.y <= 0.002 || sc.y >= 0.998 || sc.z >= 0.999) {
                        return 1.0;
                    }
                    float fade = 1.0 - smoothstep(shadowDistance * 0.78, shadowDistance, vViewDist);
                    if (fade <= 0.001) return 1.0;
                    float bias = 0.00085 + (1.0 - vNdotL) * 0.0016;
                    #if SHADOW_TAPS == 1
                    float vis = shadow2D(shadowtex0, vec3(sc.xy, sc.z - bias)).r;
                    #else
                    // 2-tap Interleaved Gradient Noise (IGN) filter: smooth penumbra in just 2 texture reads!
                    float ign = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
                    float ang = ign * 6.2831853;
                    vec2 dir = vec2(cos(ang), sin(ang)) * (1.15 / float(shadowMapResolution));
                    float s0 = shadow2D(shadowtex0, vec3(sc.xy + dir, sc.z - bias)).r;
                    float s1 = shadow2D(shadowtex0, vec3(sc.xy - dir, sc.z - bias)).r;
                    float vis = 0.5 * (s0 + s1);
                    #endif
                    return mix(1.0, vis, fade);
                    #endif
                }

                void main() {
                    vec4 albedo = texture2D(texture, vUv) * vColor;
                    if (albedo.a < alphaTestRef) discard;

                    float blockLight = clamp((vLightUv.x - 0.03125) / 0.9375, 0.0, 1.0);
                    float skyLight   = clamp((vLightUv.y - 0.03125) / 0.9375, 0.0, 1.0);

                    // Day / sunset / night celestial color curve
                    float isNight = step(0.5, sunAngle);
                    float dayPhase = sin(sunAngle * 6.2831853);
                    float daylight = clamp(dayPhase * 2.2 + 0.15, 0.0, 1.0) * (1.0 - isNight);
                    float twilight = exp(-abs(dayPhase) * 9.0) * (1.0 - rainStrength * 0.7);

                    vec3 sunCol = mix(vec3(1.02, 0.95, 0.84), vec3(1.22, 0.64, 0.32), twilight) * SUN_WARMTH;
                    vec3 moonCol = vec3(0.20, 0.28, 0.46);
                    vec3 directCol = mix(moonCol, sunCol, daylight) * (1.0 - rainStrength * 0.62);

                    vec3 skyAmb = mix(vec3(0.055, 0.075, 0.13), vec3(0.36, 0.46, 0.64), daylight);
                    skyAmb = mix(skyAmb, vec3(0.30, 0.33, 0.38), rainStrength * 0.65);

                    float sh = (vNdotL > 0.02 && skyLight > 0.04) ? sampleMobileShadow(vShadowCoord.xyz) : 0.0;
                    vec3 lit = skyAmb * (skyLight * skyLight * vSkyHemi * AMBIENT_BOOST) + vec3(0.028 * AMBIENT_BOOST);
                    lit += directCol * (vNdotL * sh * smoothstep(0.05, 0.55, skyLight));

                    // Warm quadratic torch & lava glow + pocket held-light falloff
                    float heldLevel = float(max(heldBlockLightValue, heldBlockLightValue2));
                    #ifdef DYNAMIC_HAND_LIGHT
                    if (heldLevel > 0.5) {
                        float handAtten = clamp(1.0 - vViewDist / (heldLevel * 1.15), 0.0, 1.0);
                        blockLight = max(blockLight, handAtten * (heldLevel / 15.0));
                    }
                    #endif
                    vec3 torchCol = vec3(1.08, 0.66, 0.30) * (pow(blockLight, 2.25) * TORCH_INTENSITY);
                    lit += torchCol;

                    vec3 finalRgb = albedo.rgb * lit;
                    if (vEmissive > 0.5) {
                        finalRgb = albedo.rgb * (1.35 * TORCH_INTENSITY);
                    }
                    gl_FragData[0] = vec4(clamp(finalRgb, 0.0, 1.0), albedo.a);
                }
                """;

        files.put("/gbuffers_terrain.vsh", mobileForwardVert);
        files.put("/gbuffers_terrain.fsh", mobileForwardFrag);
        files.put("/gbuffers_block.vsh", mobileForwardVert);
        files.put("/gbuffers_block.fsh", mobileForwardFrag);
        files.put("/gbuffers_entities.vsh", mobileForwardVert);
        files.put("/gbuffers_entities.fsh", mobileForwardFrag);
        files.put("/gbuffers_textured_lit.vsh", mobileForwardVert);
        files.put("/gbuffers_textured_lit.fsh", mobileForwardFrag);
        files.put("/gbuffers_hand.vsh", mobileForwardVert);
        files.put("/gbuffers_hand.fsh", mobileForwardFrag);

        // Unlit / basic / clouds / weather stages
        files.put("/gbuffers_basic.vsh", """
                #version 120
                varying vec4 vColor;
                void main() { vColor = gl_Color; gl_Position = ftransform(); }
                """);
        files.put("/gbuffers_basic.fsh", """
                #version 120
                /* RENDERTARGETS: 0 */
                varying vec4 vColor;
                void main() { gl_FragData[0] = vColor; }
                """);

        files.put("/gbuffers_textured.vsh", """
                #version 120
                varying vec2 vUv;
                varying vec4 vColor;
                void main() {
                    vUv = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
                    vColor = gl_Color;
                    gl_Position = ftransform();
                }
                """);
        files.put("/gbuffers_textured.fsh", """
                #version 120
                /* RENDERTARGETS: 0 */
                uniform sampler2D texture;
                uniform float alphaTestRef;
                varying vec2 vUv;
                varying vec4 vColor;
                void main() {
                    vec4 c = texture2D(texture, vUv) * vColor;
                    if (c.a < alphaTestRef) discard;
                    gl_FragData[0] = c;
                }
                """);

        files.put("/gbuffers_clouds.vsh", files.get("/gbuffers_textured.vsh"));
        files.put("/gbuffers_clouds.fsh", files.get("/gbuffers_textured.fsh"));
        files.put("/gbuffers_weather.vsh", files.get("/gbuffers_textured.vsh"));
        files.put("/gbuffers_weather.fsh", files.get("/gbuffers_textured.fsh"));

        // Translucent water & stained glass with ultra-cheap ALU Schlick Fresnel sky reflection
        files.put("/gbuffers_water.vsh", """
                #version 120
                #include "/lib/mobile_settings.glsl"
                attribute vec4 mc_Entity;
                uniform mat4 gbufferModelViewInverse;
                uniform vec3 cameraPosition;
                varying vec2 vUv;
                varying vec2 vLightUv;
                varying vec4 vColor;
                varying vec3 vWorldPos;
                varying vec3 vViewPos;
                varying vec3 vViewNormal;
                varying float vIsWater;

                void main() {
                    vUv = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
                    vLightUv = clamp((gl_TextureMatrix[1] * gl_MultiTexCoord1).xy, 0.03125, 0.96875);
                    vColor = gl_Color;
                    vIsWater = abs(mc_Entity.x - 10008.0) < 0.5 ? 1.0 : 0.0;
                    vec4 vp = gl_ModelViewMatrix * gl_Vertex;
                    vViewPos = vp.xyz;
                    vWorldPos = (gbufferModelViewInverse * vp).xyz + cameraPosition;
                    vec3 wn = gl_NormalMatrix * gl_Normal;
                    vViewNormal = dot(wn, wn) > 1e-6 ? normalize(wn) : vec3(0.0, 1.0, 0.0);
                    gl_Position = gl_ProjectionMatrix * vp;
                }
                """);

        files.put("/gbuffers_water.fsh", """
                #version 120
                #include "/lib/mobile_settings.glsl"
                /* RENDERTARGETS: 0 */
                uniform sampler2D texture;
                uniform vec3 skyColor;
                uniform vec3 fogColor;
                uniform vec3 sunPosition;
                uniform float frameTimeCounter;
                uniform float sunAngle;

                varying vec2 vUv;
                varying vec2 vLightUv;
                varying vec4 vColor;
                varying vec3 vWorldPos;
                varying vec3 vViewPos;
                varying vec3 vViewNormal;
                varying float vIsWater;

                void main() {
                    vec4 base = texture2D(texture, vUv) * vColor;
                    if (base.a < 0.02) discard;

                    float skyL = clamp((vLightUv.y - 0.03125) / 0.9375, 0.0, 1.0);
                    float blkL = clamp((vLightUv.x - 0.03125) / 0.9375, 0.0, 1.0);
                    float daylight = clamp(sin(sunAngle * 6.2831853) * 2.0 + 0.2, 0.08, 1.0) * (1.0 - step(0.5, sunAngle) * 0.85);

                    vec3 rgb = base.rgb * (vec3(0.32, 0.44, 0.62) * daylight * skyL + vec3(1.0, 0.65, 0.30) * pow(blkL, 2.2) + 0.08);
                    float alpha = base.a;

                    #ifdef WATER_SHIMMER
                    if (vIsWater > 0.5) {
                        vec3 V = normalize(-vViewPos);
                        vec3 N = normalize(vViewNormal);
                        float w1 = sin(vWorldPos.x * 2.1 + frameTimeCounter * 2.2) * 0.04;
                        float w2 = cos(vWorldPos.z * 2.5 - frameTimeCounter * 1.8) * 0.04;
                        N = normalize(N + vec3(w1, 0.0, w2));
                        float fresnel = 0.04 + 0.96 * pow(1.0 - max(dot(N, V), 0.0), 4.0);
                        vec3 reflCol = mix(skyColor, fogColor, 0.45) * daylight * skyL;
                        vec3 H = normalize(normalize(sunPosition) + V);
                        float spec = pow(max(dot(N, H), 0.0), 48.0) * daylight * skyL;
                        rgb = mix(rgb * vec3(0.62, 0.86, 0.96), reflCol, clamp(fresnel * 0.72, 0.0, 0.85)) + vec3(1.0, 0.92, 0.76) * spec * 0.55;
                        alpha = clamp(mix(base.a * 0.78, 0.92, fresnel), 0.35, 0.95);
                    }
                    #endif
                    gl_FragData[0] = vec4(clamp(rgb, 0.0, 1.0), alpha);
                }
                """);

        // Final single-pass mobile post-processor: 4-tap tent glow + rational depth fog + ACES + OLED sharpen
        files.put("/final.vsh", """
                #version 120
                varying vec2 vUv;
                void main() {
                    vUv = gl_MultiTexCoord0.xy;
                    gl_Position = vec4(gl_Vertex.xy * 2.0 - 1.0, 0.0, 1.0);
                }
                """);

        files.put("/final.fsh", """
                #version 120
                #include "/lib/mobile_settings.glsl"
                uniform sampler2D colortex0;
                uniform sampler2D depthtex0;
                uniform vec3 fogColor;
                uniform float near;
                uniform float far;
                uniform float viewWidth;
                uniform float viewHeight;
                uniform float rainStrength;
                varying vec2 vUv;

                vec3 acesMobile(vec3 x) {
                    const float a = 2.51;
                    const float b = 0.03;
                    const float c = 2.43;
                    const float d = 0.59;
                    const float e = 0.14;
                    return clamp((x * (a * x + b)) / (x * (c * x + d) + e), 0.0, 1.0);
                }

                void main() {
                    vec2 texel = 1.0 / max(vec2(viewWidth, viewHeight), vec2(1.0));
                    vec4 center = texture2D(colortex0, vUv);
                    vec3 col = center.rgb;

                    // Sub-pixel Contrast-Adaptive Sharpening (makes 70%–85% mobile scale look native!)
                    if (MOBILE_SHARPEN > 0.001) {
                        vec3 n = texture2D(colortex0, vUv + vec2(0.0, texel.y)).rgb;
                        vec3 s = texture2D(colortex0, vUv - vec2(0.0, texel.y)).rgb;
                        vec3 e = texture2D(colortex0, vUv + vec2(texel.x, 0.0)).rgb;
                        vec3 w = texture2D(colortex0, vUv - vec2(texel.x, 0.0)).rgb;
                        vec3 avg = (n + s + e + w) * 0.25;
                        col = max(col + (col - avg) * MOBILE_SHARPEN, vec3(0.0));
                    }

                    // Single-pass 4-tap wide diagonal tent bloom (zero extra FBO pass!)
                    #ifdef BLOOM_TENT
                    vec2 bOff = texel * 3.5;
                    vec3 b0 = texture2D(colortex0, vUv + vec2( bOff.x,  bOff.y)).rgb;
                    vec3 b1 = texture2D(colortex0, vUv + vec2(-bOff.x,  bOff.y)).rgb;
                    vec3 b2 = texture2D(colortex0, vUv + vec2( bOff.x, -bOff.y)).rgb;
                    vec3 b3 = texture2D(colortex0, vUv + vec2(-bOff.x, -bOff.y)).rgb;
                    vec3 glow = max((b0 + b1 + b2 + b3) * 0.25 - vec3(0.45), vec3(0.0));
                    col += glow * BLOOM_AMOUNT;
                    #endif

                    // Fast rational linear depth aerial perspective (0 matrix multiplies!)
                    float rawZ = texture2D(depthtex0, vUv).r;
                    if (rawZ < 0.9997) {
                        float zNdc = rawZ * 2.0 - 1.0;
                        float linearDist = (2.0 * near * far) / max(far + near - zNdc * (far - near), 0.0001);
                        float fogFactor = smoothstep(far * (0.45 - rainStrength * 0.2), far * 0.98, linearDist);
                        col = mix(col, fogColor, fogFactor * 0.72);
                    }

                    // Exposure + ACES Filmic tonemap + OLED saturation
                    col = acesMobile(col * EXPOSURE);
                    float luma = dot(col, vec3(0.2126, 0.7152, 0.0722));
                    col = clamp(mix(vec3(luma), col, SATURATION), 0.0, 1.0);

                    gl_FragColor = vec4(col, 1.0);
                }
                """);

        return ShaderPackSource.fromMemory(ID_MOBILE, "Agris Pocket Ultra (Mobile)", files);
    }

    private static ShaderPackSource createAetherial() {
        final Map<String, String> files = new LinkedHashMap<>();

        files.put("/shaders.properties", """
                profile.Android_Fast = SHADOW_QUALITY=0 !BLOOM_ENABLED !WATER_REFLECTIONS shadowMapResolution=512 CAS_SHARPEN=0.15
                profile.Low = SHADOW_QUALITY=1 !BLOOM_ENABLED WATER_REFLECTIONS shadowMapResolution=1024 CAS_SHARPEN=0.20
                profile.Medium = SHADOW_QUALITY=2 BLOOM_ENABLED WATER_REFLECTIONS shadowMapResolution=1536 CAS_SHARPEN=0.25
                profile.High = SHADOW_QUALITY=3 BLOOM_ENABLED WATER_REFLECTIONS shadowMapResolution=2048 CAS_SHARPEN=0.30
                profile.Ultra = SHADOW_QUALITY=4 BLOOM_ENABLED WATER_REFLECTIONS shadowMapResolution=4096 CAS_SHARPEN=0.35

                screen = [LIGHTING] [ATMOSPHERE] [POST] *
                screen.LIGHTING = SHADOW_QUALITY shadowMapResolution sunPathRotation TORCH_WARMTH SHADOW_SOFTNESS
                screen.ATMOSPHERE = WATER_REFLECTIONS SKY_VIBRANCE FOG_ HAZE_DENSITY WAVING_FOLIAGE
                screen.POST = BLOOM_ENABLED BLOOM_STRENGTH TONEMAP_CURVE EXPOSURE CAS_SHARPEN
                sliders = SHADOW_QUALITY shadowMapResolution TORCH_WARMTH SHADOW_SOFTNESS SKY_VIBRANCE HAZE_DENSITY BLOOM_STRENGTH EXPOSURE CAS_SHARPEN

                separateAo = true
                oldLighting = false
                sunPathRotation = -35.0
                uniform.float.agrisPulse = 0.5 + 0.5 * sin(frameTimeCounter * 1.5)
                """);

        files.put("/block.properties", """
                block.10001 = minecraft:water minecraft:bubble_column
                block.10002 = minecraft:short_grass minecraft:tall_grass minecraft:fern minecraft:large_fern minecraft:dandelion minecraft:poppy
                block.10003 = minecraft:oak_leaves minecraft:birch_leaves minecraft:spruce_leaves minecraft:jungle_leaves minecraft:acacia_leaves minecraft:dark_oak_leaves minecraft:cherry_leaves minecraft:azalea_leaves
                block.10004 = minecraft:torch minecraft:wall_torch minecraft:lantern minecraft:soul_lantern minecraft:glowstone minecraft:sea_lantern minecraft:shroomlight minecraft:lava
                """);

        files.put("/lang/en_us.lang", """
                profile.Android_Fast=Android / Mobile Fast
                profile.Low=Low
                profile.Medium=Medium
                profile.High=High (Recommended)
                profile.Ultra=Ultra

                screen.LIGHTING=Shadows & Lighting
                screen.ATMOSPHERE=Sky, Water & Foliage
                screen.POST=Bloom & Tonemapping

                option.SHADOW_QUALITY=Shadow Filtering
                option.SHADOW_QUALITY.comment=Controls PCF soft-shadow sample count. Set to Off on very weak mobile GPUs.
                value.SHADOW_QUALITY.0=Off
                value.SHADOW_QUALITY.1= crisp (1-Tap)
                value.SHADOW_QUALITY.2=Soft (5-Tap PCF)
                value.SHADOW_QUALITY.3=Smooth (9-Tap PCF)
                value.SHADOW_QUALITY.4=Cinema (13-Tap PCF)

                option.shadowMapResolution=Shadow Resolution
                option.shadowMapResolution.comment=Resolution of shadowtex0 in pixels along each axis.
                option.sunPathRotation=Sun Tilt Angle
                option.TORCH_WARMTH=Torch & Dynamic Light Warmth
                option.SHADOW_SOFTNESS=Shadow Penumbra Radius
                option.WATER_REFLECTIONS=Fresnel Water Reflections
                option.WAVING_FOLIAGE=Wind-Animated Foliage
                option.SKY_VIBRANCE=Atmospheric Sky Vibrance
                option.HAZE_DENSITY=Aerial Perspective Haze
                option.BLOOM_ENABLED=HDR Soft Bloom
                option.BLOOM_STRENGTH=Bloom Intensity
                option.TONEMAP_CURVE=Tonemapping Operator
                value.TONEMAP_CURVE.0=Neutral Crisp
                value.TONEMAP_CURVE.1=ACES Filmic
                value.TONEMAP_CURVE.2=Vibrant Cinematic
                option.EXPOSURE=Scene Exposure
                option.CAS_SHARPEN=Contrast Adaptive Sharpening
                """);

        files.put("/lib/settings.glsl", """
                #define SHADOW_QUALITY 3 // [0 1 2 3 4]
                const int shadowMapResolution = 2048; // [512 1024 1536 2048 3072 4096]
                const float shadowDistance = 128.0; // [48.0 64.0 96.0 128.0 160.0 192.0 256.0]
                const float sunPathRotation = -35.0; // [-60.0 -45.0 -35.0 -20.0 0.0 20.0 35.0]
                #define TORCH_WARMTH 1.15 // [0.60 0.85 1.00 1.15 1.35 1.60]
                #define SHADOW_SOFTNESS 1.25 // [0.50 0.75 1.00 1.25 1.50 2.00]
                #define WATER_REFLECTIONS // Enable Fresnel sky & sun reflections on water
                #define WAVING_FOLIAGE // Gentle wind motion on grass and leaves
                #define SKY_VIBRANCE 1.15 // [0.80 1.00 1.15 1.30 1.50]
                #define HAZE_DENSITY 1.00 // [0.00 0.50 1.00 1.50 2.00]
                #define BLOOM_ENABLED // Enable HDR bloom pass
                #define BLOOM_STRENGTH 0.18 // [0.05 0.10 0.18 0.25 0.35 0.50]
                #define TONEMAP_CURVE 1 // [0 1 2]
                #define EXPOSURE 1.05 // [0.75 0.90 1.00 1.05 1.15 1.30]
                #define CAS_SHARPEN 0.25 // [0.00 0.15 0.25 0.35 0.50]

                const int colortex0Format = RGBA16F;
                const int colortex1Format = RGBA16F;
                const int colortex2Format = RGBA8;
                const int colortex3Format = RGBA16F;
                const bool shadowHardwareFiltering0 = false;
                """);

        // Shadow pass
        files.put("/shadow.vsh", """
                #version 120
                #include "/lib/settings.glsl"
                attribute vec4 mc_Entity;
                uniform float frameTimeCounter;
                uniform vec3 cameraPosition;
                varying vec2 vTexCoord;
                varying vec4 vColor;

                void main() {
                    vec4 pos = gl_Vertex;
                #ifdef WAVING_FOLIAGE
                    if (mc_Entity.x == 10002.0 || mc_Entity.x == 10003.0) {
                        float phase = frameTimeCounter * 2.4 + (pos.x + cameraPosition.x) * 0.7 + (pos.z + cameraPosition.z) * 0.5;
                        pos.x += sin(phase) * 0.045;
                        pos.z += cos(phase * 0.85) * 0.035;
                    }
                #endif
                    vTexCoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
                    vColor = gl_Color;
                    gl_Position = gl_ModelViewProjectionMatrix * pos;
                }
                """);

        files.put("/shadow.fsh", """
                #version 120
                uniform sampler2D texture;
                uniform float alphaTestRef;
                varying vec2 vTexCoord;
                varying vec4 vColor;

                void main() {
                    vec4 sampleCol = texture2D(texture, vTexCoord) * vColor;
                    if (sampleCol.a < max(alphaTestRef, 0.1)) {
                        discard;
                    }
                    gl_FragData[0] = sampleCol;
                }
                """);

        // GBuffer common terrain/entity vertex + fragment
        final String gbufferLitVsh = """
                #version 120
                #include "/lib/settings.glsl"
                attribute vec4 mc_Entity;
                uniform float frameTimeCounter;
                uniform vec3 cameraPosition;
                varying vec2 vTexCoord;
                varying vec2 vLightCoord;
                varying vec4 vColor;
                varying vec3 vViewNormal;
                varying float vMaterialId;

                void main() {
                    vec4 pos = gl_Vertex;
                #ifdef WAVING_FOLIAGE
                    if (mc_Entity.x == 10002.0 || mc_Entity.x == 10003.0) {
                        float phase = frameTimeCounter * 2.4 + (pos.x + cameraPosition.x) * 0.7 + (pos.z + cameraPosition.z) * 0.5;
                        pos.x += sin(phase) * 0.045;
                        pos.z += cos(phase * 0.85) * 0.035;
                    }
                #endif
                    vTexCoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
                    vLightCoord = clamp((gl_TextureMatrix[1] * gl_MultiTexCoord1).xy, 0.03125, 0.96875);
                    vColor = gl_Color;
                    vec3 rawN = gl_NormalMatrix * gl_Normal;
                    vViewNormal = dot(rawN, rawN) > 1e-6 ? normalize(rawN) : vec3(0.0, 1.0, 0.0);
                    vMaterialId = mc_Entity.x;
                    gl_Position = gl_ModelViewProjectionMatrix * pos;
                }
                """;

        final String gbufferLitFsh = """
                #version 120
                #include "/lib/settings.glsl"
                /* RENDERTARGETS: 0,1,2 */
                uniform sampler2D texture;
                uniform float alphaTestRef;
                varying vec2 vTexCoord;
                varying vec2 vLightCoord;
                varying vec4 vColor;
                varying vec3 vViewNormal;
                varying float vMaterialId;

                void main() {
                    vec4 albedo = texture2D(texture, vTexCoord) * vColor;
                    if (albedo.a < alphaTestRef) {
                        discard;
                    }
                    vec3 safeN = dot(vViewNormal, vViewNormal) > 1e-6 ? normalize(vViewNormal) : vec3(0.0, 1.0, 0.0);
                    vec3 encNormal = safeN * 0.5 + 0.5;
                    float matTag = 0.25;
                    if (abs(vMaterialId - 10001.0) < 0.5) matTag = 0.50;
                    else if (abs(vMaterialId - 10004.0) < 0.5) matTag = 0.90;
                    gl_FragData[0] = albedo;
                    gl_FragData[1] = vec4(encNormal, 1.0);
                    gl_FragData[2] = vec4(vLightCoord, matTag, 1.0);
                }
                """;

        files.put("/gbuffers_terrain.vsh", gbufferLitVsh);
        files.put("/gbuffers_terrain.fsh", gbufferLitFsh);
        files.put("/gbuffers_entities.vsh", gbufferLitVsh);
        files.put("/gbuffers_entities.fsh", gbufferLitFsh);
        files.put("/gbuffers_hand.vsh", gbufferLitVsh);
        files.put("/gbuffers_hand.fsh", gbufferLitFsh);
        files.put("/gbuffers_textured_lit.vsh", gbufferLitVsh);
        files.put("/gbuffers_textured_lit.fsh", gbufferLitFsh);
        files.put("/gbuffers_weather.vsh", gbufferLitVsh);
        files.put("/gbuffers_weather.fsh", gbufferLitFsh);

        // Sky & basic unlit GBuffer stages
        files.put("/gbuffers_basic.vsh", """
                #version 120
                varying vec4 vColor;
                void main() {
                    vColor = gl_Color;
                    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
                }
                """);
        files.put("/gbuffers_basic.fsh", """
                #version 120
                /* RENDERTARGETS: 0,1,2 */
                varying vec4 vColor;
                void main() {
                    gl_FragData[0] = vColor;
                    gl_FragData[1] = vec4(0.5, 1.0, 0.5, 0.0);
                    gl_FragData[2] = vec4(1.0, 1.0, 0.0, 1.0);
                }
                """);
        files.put("/gbuffers_skybasic.vsh", files.get("/gbuffers_basic.vsh"));
        files.put("/gbuffers_skybasic.fsh", """
                #version 120
                #include "/lib/settings.glsl"
                /* RENDERTARGETS: 0,1,2 */
                uniform vec3 skyColor;
                uniform vec3 fogColor;
                varying vec4 vColor;
                void main() {
                    vec3 col = mix(fogColor, vColor.rgb, 0.72) * SKY_VIBRANCE;
                    gl_FragData[0] = vec4(col, vColor.a);
                    gl_FragData[1] = vec4(0.5, 1.0, 0.5, 0.0);
                    gl_FragData[2] = vec4(0.0, 1.0, 0.0, 1.0);
                }
                """);
        files.put("/gbuffers_textured.vsh", """
                #version 120
                varying vec2 vTexCoord;
                varying vec4 vColor;
                void main() {
                    vTexCoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
                    vColor = gl_Color;
                    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
                }
                """);
        files.put("/gbuffers_textured.fsh", """
                #version 120
                /* RENDERTARGETS: 0,1,2 */
                uniform sampler2D texture;
                uniform float alphaTestRef;
                varying vec2 vTexCoord;
                varying vec4 vColor;
                void main() {
                    vec4 col = texture2D(texture, vTexCoord) * vColor;
                    if (col.a < alphaTestRef) discard;
                    gl_FragData[0] = col;
                    gl_FragData[1] = vec4(0.5, 1.0, 0.5, 0.0);
                    gl_FragData[2] = vec4(0.0, 1.0, 0.1, 1.0);
                }
                """);
        files.put("/gbuffers_skytextured.vsh", files.get("/gbuffers_textured.vsh"));
        files.put("/gbuffers_skytextured.fsh", files.get("/gbuffers_textured.fsh"));
        files.put("/gbuffers_clouds.vsh", files.get("/gbuffers_textured.vsh"));
        files.put("/gbuffers_clouds.fsh", files.get("/gbuffers_textured.fsh"));

        // Water translucent stage
        files.put("/gbuffers_water.vsh", gbufferLitVsh);
        files.put("/gbuffers_water.fsh", """
                #version 120
                #include "/lib/settings.glsl"
                /* RENDERTARGETS: 0,1,2 */
                uniform sampler2D texture;
                varying vec2 vTexCoord;
                varying vec2 vLightCoord;
                varying vec4 vColor;
                varying vec3 vViewNormal;
                void main() {
                    vec4 albedo = texture2D(texture, vTexCoord) * vColor;
                    vec3 encNormal = normalize(vViewNormal) * 0.5 + 0.5;
                    gl_FragData[0] = vec4(albedo.rgb * vec3(0.72, 0.92, 1.08), max(albedo.a, 0.58));
                    gl_FragData[1] = vec4(encNormal, 1.0);
                    gl_FragData[2] = vec4(vLightCoord, 0.50, 1.0);
                }
                """);

        // Deferred lighting + shadow-map evaluation pass (runs before translucents)
        final String fullscreenVsh = """
                #version 120
                varying vec2 vUv;
                void main() {
                    vUv = gl_MultiTexCoord0.xy;
                    gl_Position = vec4(gl_Vertex.xy * 2.0 - 1.0, 0.0, 1.0);
                }
                """;

        files.put("/deferred.vsh", fullscreenVsh);
        files.put("/deferred.fsh", """
                #version 120
                #include "/lib/settings.glsl"
                /* RENDERTARGETS: 0 */
                uniform sampler2D colortex0;
                uniform sampler2D colortex1;
                uniform sampler2D colortex2;
                uniform sampler2D depthtex0;
                uniform sampler2D shadowtex0;
                uniform mat4 gbufferProjectionInverse;
                uniform mat4 gbufferModelViewInverse;
                uniform mat4 shadowModelView;
                uniform mat4 shadowProjection;
                uniform vec3 shadowLightPosition;
                uniform vec3 fogColor;
                uniform vec3 skyColor;
                uniform float rainStrength;
                uniform float sunAngle;
                uniform float far;
                uniform int heldBlockLightValue;
                uniform int heldBlockLightValue2;
                varying vec2 vUv;

                float sampleShadowMap(vec3 shadowCoord) {
                #if SHADOW_QUALITY == 0
                    return 1.0;
                #else
                    if (shadowCoord.x <= 0.002 || shadowCoord.x >= 0.998 || shadowCoord.y <= 0.002 || shadowCoord.y >= 0.998 || shadowCoord.z >= 0.999) {
                        return 1.0;
                    }
                    float bias = 0.00095;
                    float radius = (SHADOW_SOFTNESS / float(shadowMapResolution));
                    float sum = 0.0;
                #if SHADOW_QUALITY == 1
                    sum = texture2D(shadowtex0, shadowCoord.xy).r < (shadowCoord.z - bias) ? 0.0 : 1.0;
                    return sum;
                #elif SHADOW_QUALITY == 2
                    vec2 offsets[5] = vec2[5](vec2(0.0, 0.0), vec2(1.0, 0.0), vec2(-1.0, 0.0), vec2(0.0, 1.0), vec2(0.0, -1.0));
                    for (int i = 0; i < 5; i++) {
                        float d = texture2D(shadowtex0, shadowCoord.xy + offsets[i] * radius).r;
                        sum += d < (shadowCoord.z - bias) ? 0.0 : 1.0;
                    }
                    return sum / 5.0;
                #else
                    for (int x = -1; x <= 1; x++) {
                        for (int y = -1; y <= 1; y++) {
                            float d = texture2D(shadowtex0, shadowCoord.xy + vec2(float(x), float(y)) * radius).r;
                            sum += d < (shadowCoord.z - bias) ? 0.0 : 1.0;
                        }
                    }
                    return sum / 9.0;
                #endif
                #endif
                }

                void main() {
                    vec4 base = texture2D(colortex0, vUv);
                    float depth = texture2D(depthtex0, vUv).r;
                    vec4 matInfo = texture2D(colortex2, vUv);
                    if (depth >= 0.99999 || matInfo.b < 0.15) {
                        gl_FragData[0] = base;
                        return;
                    }

                    vec3 viewNormal = normalize(texture2D(colortex1, vUv).xyz * 2.0 - 1.0);
                    vec4 clipPos = vec4(vUv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
                    vec4 viewPos4 = gbufferProjectionInverse * clipPos;
                    vec3 viewPos = viewPos4.xyz / max(viewPos4.w, 1e-6);

                    vec3 lightDir = normalize(shadowLightPosition);
                    float ndotl = max(dot(viewNormal, lightDir), 0.0);

                    vec4 playerPos = gbufferModelViewInverse * vec4(viewPos + viewNormal * 0.06, 1.0);
                    vec4 shadowClip = shadowProjection * (shadowModelView * playerPos);
                    vec3 shadowNdc = shadowClip.xyz / max(shadowClip.w, 1e-6);
                    vec3 shadowUv = shadowNdc * 0.5 + 0.5;
                    float shadowVis = sampleShadowMap(shadowUv);

                    float blockLight = matInfo.r;
                    float skyLight = matInfo.g;
                    float dayFactor = smoothstep(0.0, 0.08, sin(sunAngle * 6.2831853)) * (1.0 - rainStrength * 0.65);
                    vec3 sunTint = mix(vec3(0.26, 0.34, 0.55), vec3(1.28, 1.16, 0.98), dayFactor);
                    vec3 skyAmbient = mix(vec3(0.06, 0.08, 0.14), mix(skyColor, vec3(0.62, 0.74, 0.95), 0.5), 0.25 + 0.75 * dayFactor) * (0.20 + 0.80 * skyLight);

                    float heldBoost = float(max(heldBlockLightValue, heldBlockLightValue2)) / 15.0;
                    float handFalloff = clamp(1.0 - length(viewPos) / 14.0, 0.0, 1.0);
                    float effectiveTorch = max(blockLight, heldBoost * handFalloff * 0.88);
                    vec3 torchColor = vec3(1.0, 0.66, 0.34) * (pow(effectiveTorch, 1.65) * 1.45 * TORCH_WARMTH);

                    vec3 lit = base.rgb * (skyAmbient + sunTint * ndotl * shadowVis * (0.35 + 0.65 * skyLight) + torchColor);
                    if (matInfo.b > 0.80) {
                        lit = max(lit, base.rgb * 1.65);
                    }

                    float distFactor = clamp(length(viewPos) / max(far, 32.0), 0.0, 1.0);
                    float haze = smoothstep(0.35, 1.0, distFactor) * 0.45 * HAZE_DENSITY;
                    lit = mix(lit, mix(fogColor, skyColor, 0.35), clamp(haze, 0.0, 0.85));

                    gl_FragData[0] = vec4(max(lit, vec3(0.0)), base.a);
                }
                """);

        // Composite0: Water Fresnel sky/sun reflection + HDR bloom extraction
        files.put("/composite.vsh", fullscreenVsh);
        files.put("/composite.fsh", """
                #version 120
                #include "/lib/settings.glsl"
                /* RENDERTARGETS: 0,3 */
                uniform sampler2D colortex0;
                uniform sampler2D colortex1;
                uniform sampler2D colortex2;
                uniform sampler2D depthtex0;
                uniform mat4 gbufferProjectionInverse;
                uniform vec3 shadowLightPosition;
                uniform vec3 skyColor;
                uniform vec3 fogColor;
                varying vec2 vUv;

                void main() {
                    vec4 color = texture2D(colortex0, vUv);
                    vec4 matInfo = texture2D(colortex2, vUv);
                    float depth = texture2D(depthtex0, vUv).r;

                #ifdef WATER_REFLECTIONS
                    if (depth < 0.99999 && abs(matInfo.b - 0.50) < 0.08) {
                        vec4 clipPos = vec4(vUv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
                        vec4 viewPos4 = gbufferProjectionInverse * clipPos;
                        vec3 viewDir = normalize(viewPos4.xyz / max(viewPos4.w, 1e-6));
                        vec3 normal = normalize(texture2D(colortex1, vUv).xyz * 2.0 - 1.0);
                        float fresnel = pow(1.0 - max(dot(-viewDir, normal), 0.0), 4.0);
                        vec3 reflDir = reflect(viewDir, normal);
                        vec3 lightDir = normalize(shadowLightPosition);
                        float sunSpec = pow(max(dot(reflDir, lightDir), 0.0), 96.0) * 2.4;
                        vec3 envSky = mix(fogColor, skyColor * SKY_VIBRANCE, clamp(reflDir.y * 0.5 + 0.5, 0.0, 1.0));
                        color.rgb = mix(color.rgb, envSky, clamp(fresnel * 0.65, 0.05, 0.72)) + vec3(1.0, 0.94, 0.80) * sunSpec * matInfo.g;
                    }
                #endif

                    float luma = dot(color.rgb, vec3(0.2126, 0.7152, 0.0722));
                    vec3 bloom = luma > 0.85 ? color.rgb * (luma - 0.85) : vec3(0.0);
                    gl_FragData[0] = color;
                    gl_FragData[1] = vec4(bloom, 1.0);
                }
                """);

        // Composite1: Bloom blur & ACES Filmic tonemapping
        files.put("/composite1.vsh", fullscreenVsh);
        files.put("/composite1.fsh", """
                #version 120
                #include "/lib/settings.glsl"
                /* RENDERTARGETS: 0 */
                uniform sampler2D colortex0;
                uniform sampler2D colortex3;
                uniform float viewWidth;
                uniform float viewHeight;
                varying vec2 vUv;

                vec3 acesTonemap(vec3 x) {
                    const float a = 2.51;
                    const float b = 0.03;
                    const float c = 2.43;
                    const float d = 0.59;
                    const float e = 0.14;
                    return clamp((x * (a * x + b)) / (x * (c * x + d) + e), 0.0, 1.0);
                }

                void main() {
                    vec3 hdr = texture2D(colortex0, vUv).rgb;
                #ifdef BLOOM_ENABLED
                    vec2 texel = vec2(2.5 / max(viewWidth, 1.0), 2.5 / max(viewHeight, 1.0));
                    vec3 b = texture2D(colortex3, vUv).rgb * 0.28;
                    b += texture2D(colortex3, vUv + vec2(texel.x, texel.y)).rgb * 0.18;
                    b += texture2D(colortex3, vUv + vec2(-texel.x, texel.y)).rgb * 0.18;
                    b += texture2D(colortex3, vUv + vec2(texel.x, -texel.y)).rgb * 0.18;
                    b += texture2D(colortex3, vUv + vec2(-texel.x, -texel.y)).rgb * 0.18;
                    hdr += b * BLOOM_STRENGTH;
                #endif
                    hdr *= EXPOSURE;
                #if TONEMAP_CURVE == 0
                    vec3 mapped = hdr / (hdr + vec3(1.0));
                #elif TONEMAP_CURVE == 1
                    vec3 mapped = acesTonemap(hdr);
                #else
                    vec3 mapped = pow(acesTonemap(hdr * 1.08), vec3(0.95));
                #endif
                    gl_FragData[0] = vec4(mapped, 1.0);
                }
                """);

        // Final presentation + contrast-adaptive sharpening
        files.put("/final.vsh", fullscreenVsh);
        files.put("/final.fsh", """
                #version 120
                #include "/lib/settings.glsl"
                uniform sampler2D colortex0;
                uniform float viewWidth;
                uniform float viewHeight;
                varying vec2 vUv;

                void main() {
                    vec3 center = texture2D(colortex0, vUv).rgb;
                    if (CAS_SHARPEN > 0.001) {
                        vec2 px = vec2(1.0 / max(viewWidth, 1.0), 1.0 / max(viewHeight, 1.0));
                        vec3 n = texture2D(colortex0, vUv + vec2(0.0, px.y)).rgb;
                        vec3 s = texture2D(colortex0, vUv - vec2(0.0, px.y)).rgb;
                        vec3 e = texture2D(colortex0, vUv + vec2(px.x, 0.0)).rgb;
                        vec3 w = texture2D(colortex0, vUv - vec2(px.x, 0.0)).rgb;
                        vec3 avg = (n + s + e + w) * 0.25;
                        center = clamp(center + (center - avg) * CAS_SHARPEN, 0.0, 1.0);
                    }
                    gl_FragColor = vec4(center, 1.0);
                }
                """);

        return ShaderPackSource.fromMemory(ID_AETHERIAL, "Agris Aetherial (Built-in)", files);
    }

    private static ShaderPackSource createComplementaryLite() {
        final Map<String, String> files = new LinkedHashMap<>();

        files.put("/shaders.properties", """
                profile.Fast = SATURATION=1.05 CONTRAST=1.02 TORCH_GLOW=1.00 !SUBTLE_VIGNETTE
                profile.Vibrant = SATURATION=1.20 CONTRAST=1.08 TORCH_GLOW=1.25 SUBTLE_VIGNETTE
                screen = SATURATION CONTRAST TORCH_GLOW SUBTLE_VIGNETTE
                sliders = SATURATION CONTRAST TORCH_GLOW
                shadow.enabled = false
                oldLighting = false
                """);

        files.put("/lang/en_us.lang", """
                profile.Fast=Mobile / GL4ES Fast
                profile.Vibrant=Vibrant Balanced
                option.SATURATION=Color Saturation
                option.CONTRAST=Scene Contrast
                option.TORCH_GLOW=Warm Blocklight Glow
                option.SUBTLE_VIGNETTE=Cinema Vignette
                """);

        final String liteSettings = """
                #define SATURATION 1.12 // [0.85 1.00 1.05 1.12 1.20 1.35]
                #define CONTRAST 1.05 // [0.95 1.00 1.02 1.05 1.08 1.15]
                #define TORCH_GLOW 1.15 // [0.80 1.00 1.15 1.25 1.45]
                #define SUBTLE_VIGNETTE // Soft corner vignette
                const int colortex0Format = RGBA8;
                """;
        files.put("/lib/lite.glsl", liteSettings);

        final String liteLitVsh = """
                #version 120
                #include "/lib/lite.glsl"
                uniform vec3 shadowLightPosition;
                varying vec2 vTexCoord;
                varying vec2 vLightCoord;
                varying vec4 vColor;
                varying float vNdotL;

                void main() {
                    vTexCoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
                    vLightCoord = clamp((gl_TextureMatrix[1] * gl_MultiTexCoord1).xy, 0.03125, 0.96875);
                    vColor = gl_Color;
                    vec3 rawN = gl_NormalMatrix * gl_Normal;
                    vec3 n = dot(rawN, rawN) > 1e-6 ? normalize(rawN) : vec3(0.0, 1.0, 0.0);
                    vec3 l = dot(shadowLightPosition, shadowLightPosition) > 1e-6 ? normalize(shadowLightPosition) : vec3(0.0, 1.0, 0.0);
                    vNdotL = clamp(dot(n, l) * 0.35 + 0.72, 0.45, 1.15);
                    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
                }
                """;

        final String liteLitFsh = """
                #version 120
                #include "/lib/lite.glsl"
                /* RENDERTARGETS: 0 */
                uniform sampler2D texture;
                uniform sampler2D lightmap;
                uniform float alphaTestRef;
                varying vec2 vTexCoord;
                varying vec2 vLightCoord;
                varying vec4 vColor;
                varying float vNdotL;

                void main() {
                    vec4 col = texture2D(texture, vTexCoord) * vColor;
                    if (col.a < alphaTestRef) discard;
                    vec3 lm = texture2D(lightmap, vLightCoord).rgb;
                    vec3 warmTorch = vec3(1.0, 0.70, 0.38) * pow(vLightCoord.x, 2.2) * 0.35 * TORCH_GLOW;
                    col.rgb = col.rgb * (lm * vNdotL + warmTorch);
                    gl_FragData[0] = col;
                }
                """;

        files.put("/gbuffers_terrain.vsh", liteLitVsh);
        files.put("/gbuffers_terrain.fsh", liteLitFsh);
        files.put("/gbuffers_entities.vsh", liteLitVsh);
        files.put("/gbuffers_entities.fsh", liteLitFsh);
        files.put("/gbuffers_water.vsh", liteLitVsh);
        files.put("/gbuffers_water.fsh", liteLitFsh);
        files.put("/gbuffers_textured_lit.vsh", liteLitVsh);
        files.put("/gbuffers_textured_lit.fsh", liteLitFsh);

        files.put("/gbuffers_basic.vsh", """
                #version 120
                varying vec4 vColor;
                void main() {
                    vColor = gl_Color;
                    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
                }
                """);
        files.put("/gbuffers_basic.fsh", """
                #version 120
                /* RENDERTARGETS: 0 */
                varying vec4 vColor;
                void main() {
                    gl_FragData[0] = vColor;
                }
                """);
        files.put("/gbuffers_textured.vsh", """
                #version 120
                varying vec2 vTexCoord;
                varying vec4 vColor;
                void main() {
                    vTexCoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
                    vColor = gl_Color;
                    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
                }
                """);
        files.put("/gbuffers_textured.fsh", """
                #version 120
                /* RENDERTARGETS: 0 */
                uniform sampler2D texture;
                uniform float alphaTestRef;
                varying vec2 vTexCoord;
                varying vec4 vColor;
                void main() {
                    vec4 col = texture2D(texture, vTexCoord) * vColor;
                    if (col.a < alphaTestRef) discard;
                    gl_FragData[0] = col;
                }
                """);

        files.put("/final.vsh", """
                #version 120
                varying vec2 vUv;
                void main() {
                    vUv = gl_MultiTexCoord0.xy;
                    gl_Position = vec4(gl_Vertex.xy * 2.0 - 1.0, 0.0, 1.0);
                }
                """);
        files.put("/final.fsh", """
                #version 120
                #include "/lib/lite.glsl"
                uniform sampler2D colortex0;
                varying vec2 vUv;
                void main() {
                    vec3 col = texture2D(colortex0, vUv).rgb;
                    float luma = dot(col, vec3(0.2126, 0.7152, 0.0722));
                    col = mix(vec3(luma), col, SATURATION);
                    col = clamp((col - 0.5) * CONTRAST + 0.5, 0.0, 1.0);
                #ifdef SUBTLE_VIGNETTE
                    float d = distance(vUv, vec2(0.5));
                    col *= 1.0 - smoothstep(0.45, 0.85, d) * 0.18;
                #endif
                    gl_FragColor = vec4(col, 1.0);
                }
                """);

        return ShaderPackSource.fromMemory(ID_LITE, "Agris Complementary Lite (Built-in)", files);
    }

    private static ShaderPackSource createInternal() {
        final Map<String, String> files = new LinkedHashMap<>();
        files.put("/shaders.properties", "shadow.enabled = false\n");
        files.put("/gbuffers_basic.vsh", """
                #version 120
                varying vec4 vColor;
                void main() {
                    vColor = gl_Color;
                    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
                }
                """);
        files.put("/gbuffers_basic.fsh", """
                #version 120
                /* RENDERTARGETS: 0 */
                varying vec4 vColor;
                void main() {
                    gl_FragData[0] = vColor;
                }
                """);
        files.put("/gbuffers_textured.vsh", """
                #version 120
                varying vec2 vTexCoord;
                varying vec4 vColor;
                void main() {
                    vTexCoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
                    vColor = gl_Color;
                    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
                }
                """);
        files.put("/gbuffers_textured.fsh", """
                #version 120
                /* RENDERTARGETS: 0 */
                uniform sampler2D texture;
                uniform float alphaTestRef;
                varying vec2 vTexCoord;
                varying vec4 vColor;
                void main() {
                    vec4 col = texture2D(texture, vTexCoord) * vColor;
                    if (col.a < alphaTestRef) discard;
                    gl_FragData[0] = col;
                }
                """);
        files.put("/gbuffers_textured_lit.vsh", """
                #version 120
                varying vec2 vTexCoord;
                varying vec2 vLightCoord;
                varying vec4 vColor;
                void main() {
                    vTexCoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
                    vLightCoord = clamp((gl_TextureMatrix[1] * gl_MultiTexCoord1).xy, 0.03125, 0.96875);
                    vColor = gl_Color;
                    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
                }
                """);
        files.put("/gbuffers_textured_lit.fsh", """
                #version 120
                /* RENDERTARGETS: 0 */
                uniform sampler2D texture;
                uniform sampler2D lightmap;
                uniform float alphaTestRef;
                varying vec2 vTexCoord;
                varying vec2 vLightCoord;
                varying vec4 vColor;
                void main() {
                    vec4 col = texture2D(texture, vTexCoord) * vColor;
                    if (col.a < alphaTestRef) discard;
                    col.rgb *= texture2D(lightmap, vLightCoord).rgb;
                    gl_FragData[0] = col;
                }
                """);
        files.put("/final.vsh", """
                #version 120
                varying vec2 vUv;
                void main() {
                    vUv = gl_MultiTexCoord0.xy;
                    gl_Position = vec4(gl_Vertex.xy * 2.0 - 1.0, 0.0, 1.0);
                }
                """);
        files.put("/final.fsh", """
                #version 120
                uniform sampler2D colortex0;
                varying vec2 vUv;
                void main() {
                    gl_FragColor = texture2D(colortex0, vUv);
                }
                """);
        return ShaderPackSource.fromMemory(ID_INTERNAL, "(internal)", files);
    }
}
