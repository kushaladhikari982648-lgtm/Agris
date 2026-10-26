package com.agris.glsl;

import com.agris.compat.AndroidGlProfile;
import com.agris.pack.BuiltinShaderPacks;
import com.agris.pack.ShaderPack;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class GlslTransformerTest {

    private static final AndroidGlProfile DESKTOP =
            new AndroidGlProfile(false, AndroidGlProfile.Translator.DESKTOP_NATIVE, "NVIDIA RTX", "4.6.0");
    private static final AndroidGlProfile MOBILE_GL4ES =
            new AndroidGlProfile(true, AndroidGlProfile.Translator.GL4ES, "GL4ES wrapper", "OpenGL 2.1");

    @Test
    public void transformsLegacyCompatibilityVertexShaderToCore330() {
        final String legacyVert = """
                #version 120
                attribute vec4 mc_Entity;
                attribute vec4 at_tangent;
                varying vec2 texcoord;
                varying vec2 lmcoord;
                varying vec4 glcolor;
                varying vec3 normalVec;

                void main() {
                    gl_Position = ftransform();
                    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
                    lmcoord  = (gl_TextureMatrix[1] * gl_MultiTexCoord1).xy;
                    glcolor  = gl_Color;
                    normalVec = normalize(gl_NormalMatrix * gl_Normal);
                }
                """;

        final String out = GlslTransformer.transform(
                legacyVert,
                GlslTransformer.Stage.VERTEX,
                ProgramDirectives.defaults(),
                DESKTOP,
                1.0f,
                1.0f);

        assertTrue(out.startsWith("#version 330 core\n"), "Must upgrade #version to 330 core");
        assertFalse(out.contains("attribute "), "Legacy attribute keyword must be rewritten");
        assertFalse(out.contains("varying "), "Legacy varying keyword must be rewritten");
        assertFalse(out.contains("ftransform()"), "ftransform() must be expanded");
        assertTrue(out.contains("uniform mat4 gbufferModelView;") || out.contains("agris_ModelViewMat"),
                "Must inject core-profile matrix uniforms");
        assertTrue(out.contains("out vec2 texcoord;"), "Vertex varyings must become out declarations");
    }

    @Test
    public void transformsLegacyMrtFragmentShaderWithRenderTargetsComment() {
        final String legacyFrag = """
                #version 150 compatibility
                varying vec2 texcoord;
                varying vec4 glcolor;
                uniform sampler2D texture;
                uniform sampler2D shadowtex0;

                /* RENDERTARGETS: 0,1,2 */
                void main() {
                    vec4 albedo = texture2D(texture, texcoord) * glcolor;
                    float vis = shadow2D(shadowtex0, vec3(0.5, 0.5, 0.5)).r;
                    gl_FragData[0] = albedo;
                    gl_FragData[1] = vec4(0.5, 0.5, 1.0, 1.0);
                    gl_FragData[2] = vec4(vis, 0.0, 0.0, 1.0);
                }
                """;

        final ProgramDirectives dir = ShaderDirectiveParser.parseProgramDirectives(legacyFrag, 0.1f, 1.0f);
        assertTrue(Arrays.equals(new int[]{0, 1, 2}, dir.drawBuffers()));

        final String out = GlslTransformer.transform(
                legacyFrag,
                GlslTransformer.Stage.FRAGMENT,
                dir,
                DESKTOP,
                1.0f,
                1.0f);

        assertTrue(out.startsWith("#version 330 core\n"));
        assertTrue(out.contains("layout(location = 0) out vec4 agris_FragData0;"));
        assertTrue(out.contains("layout(location = 1) out vec4 agris_FragData1;"));
        assertTrue(out.contains("layout(location = 2) out vec4 agris_FragData2;"));
        assertFalse(out.contains("gl_FragData["), "gl_FragData[] must be rewritten to explicit layout outputs");
        assertFalse(out.contains("texture2D("), "texture2D must be rewritten to texture()");
    }

    @Test
    public void transformsCompositeShaderWithOptionOverridesAndAndroidGl4esAdaptation() {
        final String compositeFrag = """
                #version 120
                varying vec2 texcoord;
                uniform sampler2D colortex0;
                #define EXPOSURE 1.0 // [0.5 1.0 1.5 2.0]

                void main() {
                    vec3 c = texture2DLod(colortex0, texcoord, 0.0).rgb * EXPOSURE;
                    gl_FragColor = vec4(c, 1.0);
                }
                """;

        final Map<String, com.agris.pack.ShaderPackOption> discovered = new java.util.LinkedHashMap<>();
        ShaderOptionParser.scanSource(compositeFrag, discovered);
        final String withOverrides = ShaderOptionParser.applyOverrides(
                compositeFrag, discovered, Map.of("EXPOSURE", "1.5"));
        assertTrue(withOverrides.contains("1.5"));

        final String out = GlslTransformer.transform(
                withOverrides,
                GlslTransformer.Stage.FRAGMENT,
                ProgramDirectives.defaults(),
                MOBILE_GL4ES,
                1.0f,
                1.0f);

        assertNotNull(out);
        assertEquals(4, MOBILE_GL4ES.translator().recommendedMaxDrawBuffers());
        assertFalse(MOBILE_GL4ES.translator().supportsFloatFboReliably());
    }

    @Test
    public void resolvesNestedIncludesAndPreventsCycles() {
        final Map<String, String> files = Map.of(
                "/composite.fsh", """
                        #version 120
                        #include "/lib/common.glsl"
                        #include "lib/tonemap.glsl"
                        void main() {
                            gl_FragColor = vec4(applyTonemap(vec3(GAMMA)), 1.0);
                        }
                        """,
                "/lib/common.glsl", """
                        #define GAMMA 2.2
                        #include "/lib/tonemap.glsl"
                        """,
                "/lib/tonemap.glsl", """
                        #include "/lib/common.glsl"
                        vec3 applyTonemap(vec3 x) { return x / (x + vec3(1.0)); }
                        """
        );

        final String resolved = GlslIncludeResolver.resolve(
                "/composite.fsh", files.get("/composite.fsh"), files::get);
        assertTrue(resolved.contains("#define GAMMA 2.2"));
        assertTrue(resolved.contains("vec3 applyTonemap(vec3 x)"));
    }

    @Test
    public void allBuiltinShaderPacksTransformCleanlyForDesktopAndMobileProfiles() {
        final List<AndroidGlProfile> profiles = List.of(
                DESKTOP,
                new AndroidGlProfile(true, AndroidGlProfile.Translator.MOBILEGLUES, "MobileGlues", "3.3"),
                new AndroidGlProfile(true, AndroidGlProfile.Translator.GLES_NATIVE, "Adreno (TM) 740", "OpenGL ES 3.2"),
                MOBILE_GL4ES
        );

        for (final String packId : List.of(
                BuiltinShaderPacks.ID_MOBILE,
                BuiltinShaderPacks.ID_AETHERIAL,
                BuiltinShaderPacks.ID_LITE,
                BuiltinShaderPacks.ID_INTERNAL)) {
            final ShaderPack pack = ShaderPack.load(BuiltinShaderPacks.byIdOrNull(packId), Map.of(), "en_us");
            int verifiedStages = 0;
            for (final String path : pack.source().allPaths()) {
                if (!path.endsWith(".fsh")) {
                    continue;
                }
                final int slash = path.lastIndexOf('/');
                final String progName = path.substring(slash + 1, path.length() - 4);
                final ShaderPack.ProgramSource src = pack.resolveProgram(progName, "minecraft:overworld");
                assertNotNull(src, packId + " must resolve " + progName);
                verifiedStages++;
                for (final AndroidGlProfile profile : profiles) {
                    final ProgramDirectives dir = ShaderDirectiveParser.parseProgramDirectives(
                            src.fragmentSource(), 0.1f, 1.0f);
                    final String vsh = GlslTransformer.transform(
                            src.vertexSource(), GlslTransformer.Stage.VERTEX, dir, profile, 1.0f, 1.0f);
                    final String fsh = GlslTransformer.transform(
                            src.fragmentSource(), GlslTransformer.Stage.FRAGMENT, dir, profile, 1.0f, 1.0f);
                    assertFalse(vsh.isBlank());
                    assertFalse(fsh.isBlank());
                    assertTrue(vsh.contains("#define MC_VERSION 12001"));
                    if (profile.translator() == AndroidGlProfile.Translator.GLES_NATIVE) {
                        assertTrue(vsh.startsWith("#version 300 es"),
                                () -> packId + ":" + progName + " expected #version 300 es");
                        assertTrue(fsh.contains("precision highp float;"));
                        assertTrue(fsh.contains("#define AGRIS_GLES 1"));
                        assertFalse(fsh.contains("gl_FragColor"));
                        assertFalse(fsh.contains("gl_FragData["));
                    } else if (!profile.translator().legacyGlslPreferred()) {
                        assertTrue(vsh.startsWith("#version 330 core"),
                                () -> packId + ":" + progName + " expected #version 330 core, got: "
                                        + Arrays.toString(vsh.lines().limit(2).toArray()));
                        assertFalse(fsh.contains("gl_FragColor"));
                        assertFalse(fsh.contains("gl_FragData["));
                    }
                }
            }
            assertTrue(verifiedStages >= 3, packId + " must ship at least 3 shader stages");
        }
    }
}
