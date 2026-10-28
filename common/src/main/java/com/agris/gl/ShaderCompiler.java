package com.agris.gl;

import com.agris.util.AgrisLog;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.util.List;
import java.util.Map;

/**
 * Compiles and links OpenGL vertex/geometry/fragment shader programs, binding vertex
 * attribute indices to match the active {@code VertexFormat} element order exactly
 * (plus extended Iris attributes {@code mc_Entity}, {@code mc_midTexCoord},
 * {@code at_tangent} on non-colliding indices 11..13) and binding fragment outputs
 * {@code agris_FragData0..N} to MRT attachment locations.
 */
public final class ShaderCompiler {
    public static final int ATTR_FALLBACK_COLOR = 8;
    public static final int ATTR_FALLBACK_UV0 = 9;
    public static final int ATTR_FALLBACK_UV1 = 10;
    public static final int ATTR_FALLBACK_UV2 = 11;
    public static final int ATTR_FALLBACK_NORMAL = 12;
    public static final int ATTR_MC_ENTITY = 13;
    public static final int ATTR_MC_MID_TEX_COORD = 14;
    public static final int ATTR_AT_TANGENT = 15;

    private static final int GL_GEOMETRY_SHADER = 0x8DD9;

    private static final Map<String, String> VANILLA_TO_IRIS_ATTR = Map.of(
            "Position", "vaPosition",
            "Color", "vaColor",
            "UV", "vaUV0",
            "UV0", "vaUV0",
            "UV1", "vaUV1",
            "UV2", "vaUV2",
            "Normal", "vaNormal"
    );

    private static final Map<String, Integer> FALLBACK_IRIS_ATTR_SLOTS = Map.of(
            "vaColor", ATTR_FALLBACK_COLOR,
            "vaUV0", ATTR_FALLBACK_UV0,
            "vaUV1", ATTR_FALLBACK_UV1,
            "vaUV2", ATTR_FALLBACK_UV2,
            "vaNormal", ATTR_FALLBACK_NORMAL
    );

    private ShaderCompiler() {
    }

    /**
     * Establishes safe non-zero/non-black OpenGL context-global generic vertex attribute values
     * for disabled VAO attribute slots (e.g., white {@code vaColor} on {@code POSITION_TEX} sun/moon
     * quads, full lightmap {@code (240, 240)} on unlit draws, and unit {@code +Y} {@code vaNormal}
     * on particle/weather quads so {@code normalize(gl_NormalMatrix * gl_Normal)} never produces {@code NaN}).
     */
    public static void applyDefaultFallbackAttributes() {
        GL20.glVertexAttrib4f(ATTR_FALLBACK_COLOR, 1.0f, 1.0f, 1.0f, 1.0f);
        GL20.glVertexAttrib4f(ATTR_FALLBACK_UV0, 0.0f, 0.0f, 0.0f, 1.0f);
        GL30.glVertexAttribI4i(ATTR_FALLBACK_UV1, 0, 10, 0, 1);
        GL30.glVertexAttribI4i(ATTR_FALLBACK_UV2, 240, 240, 0, 1);
        GL20.glVertexAttrib4f(ATTR_FALLBACK_NORMAL, 0.0f, 1.0f, 0.0f, 0.0f);
        GL20.glVertexAttrib4f(ATTR_MC_ENTITY, -1.0f, -1.0f, 0.0f, 1.0f);
        GL20.glVertexAttrib4f(ATTR_MC_MID_TEX_COORD, 0.0f, 0.0f, 0.0f, 1.0f);
        GL20.glVertexAttrib4f(ATTR_AT_TANGENT, 1.0f, 0.0f, 0.0f, 1.0f);
    }

    public static int compileAndLink(final String programName,
                                     final String vertexGlsl,
                                     final String geometryGlsl,
                                     final String fragmentGlsl,
                                     final List<String> vertexAttributeNames,
                                     final int drawBufferCount,
                                     final boolean verboseLogs) {
        final int vsh = compileStage(programName + ".vsh", GL20.GL_VERTEX_SHADER, vertexGlsl, verboseLogs);
        int gsh = 0;
        int fsh = 0;
        int program = 0;
        try {
            if (geometryGlsl != null && !geometryGlsl.isBlank()) {
                gsh = compileStage(programName + ".gsh", GL_GEOMETRY_SHADER, geometryGlsl, verboseLogs);
            }
            fsh = compileStage(programName + ".fsh", GL20.GL_FRAGMENT_SHADER, fragmentGlsl, verboseLogs);

            program = GL20.glCreateProgram();
            if (program == 0) {
                throw new IllegalStateException("glCreateProgram() returned 0 for " + programName);
            }
            GL20.glAttachShader(program, vsh);
            if (gsh != 0) {
                GL20.glAttachShader(program, gsh);
            }
            GL20.glAttachShader(program, fsh);

            // Bind attribute indices 0..N-1 to match vanilla VertexFormat.getElementAttributeNames() 1-to-1,
            // and bind any absent canonical Iris attributes to dedicated fallback slots 8..12 whose default
            // generic values are pre-populated by applyDefaultFallbackAttributes().
            final java.util.HashSet<String> boundIrisAliases = new java.util.HashSet<>(8);
            if (vertexAttributeNames != null) {
                for (int idx = 0; idx < vertexAttributeNames.size(); idx++) {
                    final String vanillaAttr = vertexAttributeNames.get(idx);
                    GL20.glBindAttribLocation(program, idx, vanillaAttr);
                    final String irisAlias = VANILLA_TO_IRIS_ATTR.get(vanillaAttr);
                    if (irisAlias != null) {
                        GL20.glBindAttribLocation(program, idx, irisAlias);
                        boundIrisAliases.add(irisAlias);
                    }
                }
            }
            for (final Map.Entry<String, Integer> entry : FALLBACK_IRIS_ATTR_SLOTS.entrySet()) {
                if (!boundIrisAliases.contains(entry.getKey())) {
                    GL20.glBindAttribLocation(program, entry.getValue(), entry.getKey());
                }
            }

            // Bind extended Iris/OptiFine vertex attributes to fixed high slots so default generic
            // glVertexAttrib4f(13..15, ...) values feed cleanly whenever a VAO leaves them disabled.
            GL20.glBindAttribLocation(program, ATTR_MC_ENTITY, "mc_Entity");
            GL20.glBindAttribLocation(program, ATTR_MC_MID_TEX_COORD, "mc_midTexCoord");
            GL20.glBindAttribLocation(program, ATTR_AT_TANGENT, "at_tangent");

            final int outputs = Math.max(1, Math.min(16, drawBufferCount));
            for (int loc = 0; loc < outputs; loc++) {
                GL30.glBindFragDataLocation(program, loc, "agris_FragData" + loc);
                GL30.glBindFragDataLocation(program, loc, "outColor" + loc);
            }

            GL20.glLinkProgram(program);
            final int linked = GL20.glGetProgrami(program, GL20.GL_LINK_STATUS);
            if (linked == GL11.GL_FALSE) {
                final String log = GL20.glGetProgramInfoLog(program);
                GL20.glDeleteProgram(program);
                program = 0;
                throw new IllegalStateException("Program link failed for '" + programName + "': " + summarizeLog(log));
            }
            return program;
        } finally {
            if (vsh != 0) {
                GL20.glDeleteShader(vsh);
            }
            if (gsh != 0) {
                GL20.glDeleteShader(gsh);
            }
            if (fsh != 0) {
                GL20.glDeleteShader(fsh);
            }
        }
    }

    private static int compileStage(final String stageLabel, final int glStageType,
                                    final String source, final boolean verboseLogs) {
        final int shader = GL20.glCreateShader(glStageType);
        if (shader == 0) {
            throw new IllegalStateException("glCreateShader() returned 0 for " + stageLabel);
        }
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        final int compiled = GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS);
        if (compiled == GL11.GL_FALSE) {
            final String infoLog = GL20.glGetShaderInfoLog(shader);
            GL20.glDeleteShader(shader);
            if (verboseLogs) {
                AgrisLog.error("GLSL compilation failure in {}:\n{}\n--- Source ---\n{}",
                        stageLabel, infoLog, numberLines(source));
            }
            throw new IllegalStateException("Shader compile error in " + stageLabel + ": " + summarizeLog(infoLog));
        }
        return shader;
    }

    public static String numberLines(final String source) {
        final String[] lines = source.split("\n", -1);
        final StringBuilder sb = new StringBuilder(source.length() + lines.length * 6);
        for (int i = 0; i < lines.length; i++) {
            sb.append(String.format("%4d | %s\n", i + 1, lines[i]));
        }
        return sb.toString();
    }

    private static String summarizeLog(final String log) {
        if (log == null || log.isBlank()) {
            return "Unknown OpenGL compiler error";
        }
        final String trimmed = log.trim();
        final int nl = trimmed.indexOf('\n');
        return nl > 0 ? trimmed.substring(0, Math.min(nl, 220)) : trimmed.substring(0, Math.min(trimmed.length(), 220));
    }
}
