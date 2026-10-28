package com.agris.pipeline;

import com.agris.compat.AndroidGlProfile;
import com.agris.gl.ShaderCompiler;
import com.agris.glsl.GlslTransformer;
import com.agris.glsl.ProgramDirectives;
import com.agris.pack.BuiltinShaderPacks;
import com.agris.pack.ShaderPackSource;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression suite covering the MC 1.20.1 black-screen rendering root causes:
 * fullscreen composite/final orthographic matrix &amp; {@code chunkOffset} reset,
 * {@code #version 400/430 compatibility} preservation, integer {@code vaUV1}/{@code vaUV2}
 * declarations, non-overlapping fallback vertex attribute slots, and built-in pack
 * sky/sun/moon alpha &amp; zero-normal {@code NaN} guards.
 */
public final class BlackScreenRegressionTest {
    @Test
    public void fullscreenQuadStateMapsUnitQuadToNdcAndPreserves3dGbufferMatrices() {
        final UniformState state = new UniformState();
        final float[] worldMv = {
                0.707f, 0.0f, -0.707f, 0.0f,
                0.0f, 1.0f, 0.0f, 0.0f,
                0.707f, 0.0f, 0.707f, 0.0f,
                0.0f, 0.0f, -10.0f, 1.0f
        };
        final float[] worldProj = {
                1.2f, 0.0f, 0.0f, 0.0f,
                0.0f, 2.1f, 0.0f, 0.0f,
                0.0f, 0.0f, -1.0005f, -1.0f,
                0.0f, 0.0f, -0.10002f, 0.0f
        };
        System.arraycopy(worldMv, 0, state.gbufferModelView, 0, 16);
        System.arraycopy(worldProj, 0, state.gbufferProjection, 0, 16);

        // Simulate dirty per-draw state left behind by the last chunk draw call.
        state.setPerDrawMatrices(worldMv, worldProj, null);
        state.chunkOffset[0] = 160.0f;
        state.chunkOffset[1] = -48.0f;
        state.chunkOffset[2] = -224.0f;
        state.colorModulator[0] = 0.25f;

        state.setFullscreenQuadState();

        // chunkOffset and colorModulator must be cleanly reset so fullscreen vertices never shift off-screen.
        assertEquals(0.0f, state.chunkOffset[0], 1e-6f);
        assertEquals(0.0f, state.chunkOffset[1], 1e-6f);
        assertEquals(0.0f, state.chunkOffset[2], 1e-6f);
        assertEquals(1.0f, state.colorModulator[0], 1e-6f);
        assertEquals(1.0f, state.colorModulator[3], 1e-6f);

        // Verify (0, 0, 0, 1) -> (-1, -1, 0, 1) and (1, 1, 0, 1) -> (+1, +1, 0, 1) under drawProjection * drawModelView.
        final float[] bl = mulMat4Vec4(state.drawProjection, mulMat4Vec4(state.drawModelView, new float[]{0f, 0f, 0f, 1f}));
        final float[] tr = mulMat4Vec4(state.drawProjection, mulMat4Vec4(state.drawModelView, new float[]{1f, 1f, 0f, 1f}));
        assertEquals(-1.0f, bl[0], 1e-5f);
        assertEquals(-1.0f, bl[1], 1e-5f);
        assertEquals(0.0f, bl[2], 1e-5f);
        assertEquals(1.0f, bl[3], 1e-5f);
        assertEquals(1.0f, tr[0], 1e-5f);
        assertEquals(1.0f, tr[1], 1e-5f);
        assertEquals(0.0f, tr[2], 1e-5f);
        assertEquals(1.0f, tr[3], 1e-5f);

        // 3D camera gbuffer matrices must remain untouched for deferred/composite depth reconstruction!
        assertEquals(worldMv[14], state.gbufferModelView[14], 1e-6f);
        assertEquals(worldProj[11], state.gbufferProjection[11], 1e-6f);
    }

    @Test
    public void glslTransformerPreservesHighCompatibilityVersionsAndIntegerUv1() {
        final AndroidGlProfile desktop = new AndroidGlProfile(
                false, AndroidGlProfile.Translator.DESKTOP_NATIVE, "NVIDIA", "4.6.0");
        final ProgramDirectives dirs = new ProgramDirectives(new int[]{0, 1}, true, 0.1f, 1.0f, new int[0]);

        final String vsh = """
                #version 430 compatibility
                varying vec2 texcoord;
                void main() {
                    ivec2 ov = vaUV1;
                    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy + vec2(ov) * 0.0;
                    gl_Position = ftransform();
                }
                """;
        final String out = GlslTransformer.transform(
                vsh, GlslTransformer.Stage.VERTEX, dirs, desktop, 1.0f, 1.0f);

        assertTrue(out.startsWith("#version 430 core\n"), "Expected #version 430 core header, got:\n" + out);
        assertTrue(out.contains("in ivec2 vaUV1;"), "Expected integer vaUV1 input for MC 1.20.1 glVertexAttribIPointer");
    }

    @Test
    public void fallbackVertexAttributeSlotsDoNotCollideWithVanillaFormats() {
        final Set<Integer> slots = Set.of(
                ShaderCompiler.ATTR_FALLBACK_COLOR,
                ShaderCompiler.ATTR_FALLBACK_UV0,
                ShaderCompiler.ATTR_FALLBACK_UV1,
                ShaderCompiler.ATTR_FALLBACK_UV2,
                ShaderCompiler.ATTR_FALLBACK_NORMAL,
                ShaderCompiler.ATTR_MC_ENTITY,
                ShaderCompiler.ATTR_MC_MID_TEX_COORD,
                ShaderCompiler.ATTR_AT_TANGENT
        );
        assertEquals(8, slots.size(), "All 8 fallback/extended vertex attribute slots must be distinct");
        for (final int slot : slots) {
            assertTrue(slot >= 8 && slot <= 15, "Fallback slot must be in [8..15], got " + slot);
        }
    }

    @Test
    public void builtinMobilePackPreservesSkyAlphaAndGuardsZeroNormals() {
        final ShaderPackSource mobile = BuiltinShaderPacks.byIdOrNull(BuiltinShaderPacks.ID_MOBILE);
        assertNotNull(mobile);
        final String skyBasic = mobile.readText("/gbuffers_skybasic.fsh");
        final String skyTex = mobile.readText("/gbuffers_skytextured.fsh");
        final String terrainVsh = mobile.readText("/gbuffers_terrain.vsh");
        assertNotNull(skyBasic);
        assertNotNull(skyTex);
        assertNotNull(terrainVsh);

        assertTrue(skyBasic.contains("vColor.a);"),
                "gbuffers_skybasic.fsh must preserve vColor.a under SRC_ALPHA blend");
        assertTrue(skyTex.contains("c.a);"),
                "gbuffers_skytextured.fsh must preserve c.a on sun/moon");
        assertFalse(terrainVsh.contains("normalize(gl_NormalMatrix * gl_Normal)"),
                "Built-in terrain shader must guard against zero-length normal NaNs");
    }

    private static float[] mulMat4Vec4(final float[] m, final float[] v) {
        final float[] r = new float[4];
        for (int row = 0; row < 4; row++) {
            r[row] = m[row] * v[0]
                    + m[4 + row] * v[1]
                    + m[8 + row] * v[2]
                    + m[12 + row] * v[3];
        }
        return r;
    }
}
