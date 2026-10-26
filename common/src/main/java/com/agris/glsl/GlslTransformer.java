package com.agris.glsl;

import com.agris.Capabilities;
import com.agris.compat.AndroidGlProfile;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Translates OptiFine / Iris GLSL shaders (both {@code #version 110/120/130} compatibility
 * shaders and {@code #version 150..460} core shaders) into clean, driver-valid OpenGL
 * Core Profile GLSL (or GL4ES-safe GLSL when running on an Android GLES2 shim).
 *
 * <h3>Why this transformer exists</h3>
 * <p>Minecraft 1.17+ opens an OpenGL 3.2+ Core Profile context in which compatibility
 * built-ins ({@code attribute}, {@code varying}, {@code gl_Vertex}, {@code gl_Color},
 * {@code gl_Normal}, {@code gl_MultiTexCoord0}, {@code gl_MultiTexCoord1},
 * {@code gl_ModelViewMatrix}, {@code gl_ProjectionMatrix}, {@code gl_NormalMatrix},
 * {@code gl_TextureMatrix[0..1]}, {@code gl_FragColor}, {@code gl_FragData[k]},
 * {@code texture2D}, {@code shadow2D}) are rejected by the GPU driver. {@link GlslTransformer}
 * upgrades those constructs cleanly while preserving modern Iris core-profile shaders
 * untouched where appropriate.</p>
 */
public final class GlslTransformer {
    public enum Stage {
        VERTEX,
        GEOMETRY,
        FRAGMENT
    }

    private static final Pattern VERSION_LINE = Pattern.compile(
            "(?m)^\\s*#\\s*version\\s+(\\d+)(?:\\s+([A-Za-z_]+))?\\s*$");
    private static final Pattern ATTRIBUTE_DECL = Pattern.compile(
            "(?m)^(\\s*)attribute\\s+");
    private static final Pattern VARYING_DECL = Pattern.compile(
            "(?m)^(\\s*)(?:((?:flat|smooth|noperspective)\\s+))?varying\\s+");
    private static final Pattern GL_FRAG_DATA = Pattern.compile(
            "\\bgl_FragData\\s*\\[\\s*(\\d+)\\s*]");
    private static final Pattern GL_TEX_MATRIX_INDEX = Pattern.compile(
            "\\bgl_TextureMatrix\\s*\\[\\s*(\\d+)\\s*]");
    private static final Pattern SHADOW_2D_CALL = Pattern.compile(
            "\\bshadow2D(Proj|Lod)?\\s*\\(");
    private static final Pattern LEGACY_TEX_FN = Pattern.compile(
            "\\btexture(?:1D|2D|3D|Cube)(Proj|Lod|Grad)?\\s*\\(");

    private GlslTransformer() {
    }

    public static String transform(final String rawSource,
                                   final Stage stage,
                                   final ProgramDirectives directives,
                                   final AndroidGlProfile profile,
                                   final float renderQuality,
                                   final float shadowQuality) {
        if (rawSource == null) {
            throw new IllegalArgumentException("Shader source must not be null");
        }
        final AndroidGlProfile effectiveProfile = profile == null
                ? new AndroidGlProfile(false, AndroidGlProfile.Translator.DESKTOP_NATIVE, "", "")
                : profile;

        String body = stripCommentsPreservingLines(rawSource);

        // Extract and strip original #version directive so our header sits right after #version.
        int declaredVersion = 120;
        String declaredProfile = "";
        final Matcher vm = VERSION_LINE.matcher(body);
        if (vm.find()) {
            declaredVersion = Integer.parseInt(vm.group(1));
            declaredProfile = vm.group(2) == null ? "" : vm.group(2).trim().toLowerCase(Locale.ROOT);
            body = vm.replaceFirst("");
        }

        final boolean gl4esLegacy = effectiveProfile.translator().legacyGlslPreferred();
        final boolean glesNative = effectiveProfile.translator() == AndroidGlProfile.Translator.GLES_NATIVE;
        final int targetVersion;
        final String versionHeader;
        if (gl4esLegacy) {
            targetVersion = 120;
            versionHeader = "#version 120\n";
        } else if (glesNative) {
            targetVersion = 300;
            versionHeader = "#version 300 es\n";
        } else {
            targetVersion = Math.max(330, Math.min(460,
                    declaredVersion >= 330 && !"compatibility".equals(declaredProfile) ? declaredVersion : 330));
            versionHeader = "#version " + targetVersion + " core\n";
        }

        final StringBuilder preamble = new StringBuilder(1024);
        preamble.append(versionHeader);
        if (glesNative) {
            preamble.append("precision highp float;\n");
            preamble.append("precision highp int;\n");
            preamble.append("precision mediump sampler2D;\n");
            preamble.append("precision highp sampler2DShadow;\n");
        }
        appendStandardMacros(preamble, targetVersion, effectiveProfile, renderQuality, shadowQuality);

        if (!gl4esLegacy) {
            // Upgrade storage qualifiers for Core Profile.
            if (stage == Stage.VERTEX) {
                body = ATTRIBUTE_DECL.matcher(body).replaceAll("$1in ");
                body = rewriteVarying(body, "out ");
            } else if (stage == Stage.FRAGMENT) {
                body = rewriteVarying(body, "in ");
            }

            // Polyfill legacy compatibility built-ins when referenced by the shader body.
            body = rewriteCompatibilityBuiltins(body, stage, directives, preamble);
        } else {
            // Even on GL4ES (#version 120), inject standard Iris macros & chunkOffset helper if needed.
            if (stage == Stage.VERTEX && containsIdentifier(body, "vaPosition")
                    && !declaresIdentifier(body, "vaPosition")) {
                preamble.append("attribute vec3 vaPosition;\n");
            }
            if (containsIdentifier(body, "chunkOffset") && !declaresIdentifier(body, "chunkOffset")) {
                preamble.append("uniform vec3 chunkOffset;\n");
            }
        }

        return preamble.append(body).toString();
    }

    private static void appendStandardMacros(final StringBuilder out,
                                             final int glslVersion,
                                             final AndroidGlProfile profile,
                                             final float renderQuality,
                                             final float shadowQuality) {
        out.append("#define IS_IRIS\n");
        out.append("#define IS_AGRIS\n");
        out.append("#define MC_VERSION ").append(Capabilities.MC_VERSION_INT).append('\n');
        out.append("#define MC_GL_VERSION ").append(glslVersion >= 330 ? 330 : 210).append('\n');
        out.append("#define MC_GLSL_VERSION ").append(glslVersion).append('\n');
        out.append("#define MC_HAND_DEPTH 0.125\n");
        out.append(String.format(Locale.ROOT, "#define MC_RENDER_QUALITY %.4f\n", renderQuality));
        out.append(String.format(Locale.ROOT, "#define MC_SHADOW_QUALITY %.4f\n", shadowQuality));
        if (profile.isAndroidHost()) {
            out.append("#define MC_OS_ANDROID\n");
            out.append("#define MC_OS_LINUX\n");
            out.append("#define AGRIS_MOBILE_GPU 1\n");
            out.append("#define AGRIS_TBDR 1\n");
            if (profile.translator() == AndroidGlProfile.Translator.GLES_NATIVE) {
                out.append("#define AGRIS_GLES 1\n");
            } else if (profile.translator() == AndroidGlProfile.Translator.GL4ES) {
                out.append("#define AGRIS_GL4ES 1\n");
            }
        } else {
            final String os = System.getProperty("os.name", "linux").toLowerCase(Locale.ROOT);
            if (os.contains("win")) {
                out.append("#define MC_OS_WINDOWS\n");
            } else if (os.contains("mac") || os.contains("darwin")) {
                out.append("#define MC_OS_MAC\n");
            } else {
                out.append("#define MC_OS_LINUX\n");
            }
        }
    }

    private static String rewriteCompatibilityBuiltins(String body,
                                                       final Stage stage,
                                                       final ProgramDirectives directives,
                                                       final StringBuilder preamble) {
        // 1. Legacy texture lookup functions -> GLSL 150+ texture(...) overloads.
        if (SHADOW_2D_CALL.matcher(body).find()) {
            preamble.append("vec4 agris_shadow2D(sampler2DShadow s, vec3 c) { return vec4(texture(s, c)); }\n");
            preamble.append("vec4 agris_shadow2D(sampler2D s, vec3 c) { return vec4(texture(s, c.xy).r < c.z ? 0.0 : 1.0); }\n");
            preamble.append("vec4 agris_shadow2DProj(sampler2DShadow s, vec4 c) { return vec4(textureProj(s, c)); }\n");
            preamble.append("vec4 agris_shadow2DProj(sampler2D s, vec4 c) { vec3 p = c.xyz / max(c.w, 1e-6); return agris_shadow2D(s, p); }\n");
            body = body.replaceAll("\\bshadow2DProj\\s*\\(", "agris_shadow2DProj(")
                    .replaceAll("\\bshadow2DLod\\s*\\(", "agris_shadow2D(")
                    .replaceAll("\\bshadow2D\\s*\\(", "agris_shadow2D(");
        }
        body = LEGACY_TEX_FN.matcher(body).replaceAll("texture$1(");

        // 2. Vertex compatibility attributes -> explicit Core Profile inputs matching VertexFormat.
        if (stage == Stage.VERTEX) {
            body = body.replaceAll("\\bftransform\\s*\\(\\s*\\)", "(gl_ModelViewProjectionMatrix * gl_Vertex)");
            final boolean usesGlVertex = containsIdentifier(body, "gl_Vertex");
            final boolean usesGlColor = containsIdentifier(body, "gl_Color");
            final boolean usesGlNormal = containsIdentifier(body, "gl_Normal");
            final boolean usesTex0 = containsIdentifier(body, "gl_MultiTexCoord0");
            final boolean usesTex1 = containsIdentifier(body, "gl_MultiTexCoord1")
                    || containsIdentifier(body, "gl_MultiTexCoord2");

            if (usesGlVertex) {
                if (!declaresIdentifier(body, "vaPosition")) {
                    preamble.append("in vec3 vaPosition;\n");
                }
                preamble.append("uniform vec3 agris_ChunkOffset;\n");
                body = replaceIdentifier(body, "gl_Vertex", "vec4(vaPosition + agris_ChunkOffset, 1.0)");
            }
            if (usesGlColor) {
                if (!declaresIdentifier(body, "vaColor")) {
                    preamble.append("in vec4 vaColor;\n");
                }
                preamble.append("uniform vec4 agris_ColorModulator;\n");
                body = replaceIdentifier(body, "gl_Color", "(vaColor * agris_ColorModulator)");
            }
            if (usesGlNormal) {
                if (!declaresIdentifier(body, "vaNormal")) {
                    preamble.append("in vec3 vaNormal;\n");
                }
                body = replaceIdentifier(body, "gl_Normal", "vaNormal");
            }
            if (usesTex0) {
                if (!declaresIdentifier(body, "vaUV0")) {
                    preamble.append("in vec2 vaUV0;\n");
                }
                body = replaceIdentifier(body, "gl_MultiTexCoord0", "vec4(vaUV0, 0.0, 1.0)");
            }
            if (usesTex1) {
                if (!declaresIdentifier(body, "vaUV2")) {
                    preamble.append("in ivec2 vaUV2;\n");
                }
                body = replaceIdentifier(body, "gl_MultiTexCoord1", "vec4(vec2(vaUV2), 0.0, 1.0)");
                body = replaceIdentifier(body, "gl_MultiTexCoord2", "vec4(vec2(vaUV2), 0.0, 1.0)");
            }
        }

        // 3. Legacy compatibility matrix uniforms -> Agris injected Core uniforms.
        final boolean usesMvp = containsIdentifier(body, "gl_ModelViewProjectionMatrix");
        final boolean usesMv = usesMvp || containsIdentifier(body, "gl_ModelViewMatrix");
        final boolean usesProj = usesMvp || containsIdentifier(body, "gl_ProjectionMatrix");
        final boolean usesMvInv = containsIdentifier(body, "gl_ModelViewMatrixInverse");
        final boolean usesProjInv = containsIdentifier(body, "gl_ProjectionMatrixInverse");
        final boolean usesNormMat = containsIdentifier(body, "gl_NormalMatrix");
        final boolean usesTexMat = containsIdentifier(body, "gl_TextureMatrix");

        if (usesMv) {
            preamble.append("uniform mat4 agris_ModelViewMatrix;\n");
        }
        if (usesProj) {
            preamble.append("uniform mat4 agris_ProjectionMatrix;\n");
        }
        if (usesMvInv) {
            preamble.append("uniform mat4 agris_ModelViewMatrixInverse;\n");
            body = replaceIdentifier(body, "gl_ModelViewMatrixInverse", "agris_ModelViewMatrixInverse");
        }
        if (usesProjInv) {
            preamble.append("uniform mat4 agris_ProjectionMatrixInverse;\n");
            body = replaceIdentifier(body, "gl_ProjectionMatrixInverse", "agris_ProjectionMatrixInverse");
        }
        if (usesMvp) {
            body = replaceIdentifier(body, "gl_ModelViewProjectionMatrix",
                    "(agris_ProjectionMatrix * agris_ModelViewMatrix)");
        }
        if (usesMv) {
            body = replaceIdentifier(body, "gl_ModelViewMatrix", "agris_ModelViewMatrix");
        }
        if (usesProj) {
            body = replaceIdentifier(body, "gl_ProjectionMatrix", "agris_ProjectionMatrix");
        }
        if (usesNormMat) {
            preamble.append("uniform mat3 agris_NormalMatrix;\n");
            body = replaceIdentifier(body, "gl_NormalMatrix", "agris_NormalMatrix");
        }
        if (usesTexMat) {
            preamble.append("uniform mat4 agris_TextureMatrix;\n");
            preamble.append("uniform mat4 agris_LightmapTextureMatrix;\n");
            final Matcher tm = GL_TEX_MATRIX_INDEX.matcher(body);
            final StringBuffer sb = new StringBuffer();
            while (tm.find()) {
                final int idx = Integer.parseInt(tm.group(1));
                tm.appendReplacement(sb, idx == 1 || idx == 2 ? "agris_LightmapTextureMatrix" : "agris_TextureMatrix");
            }
            tm.appendTail(sb);
            body = sb.toString();
        }

        // 4. Legacy gl_Fog struct -> Agris fog uniform struct.
        if (containsIdentifier(body, "gl_Fog")) {
            preamble.append("struct AgrisFogParameters { vec4 color; float density; float start; float end; float scale; };\n");
            preamble.append("uniform AgrisFogParameters agris_Fog;\n");
            body = replaceIdentifier(body, "gl_Fog", "agris_Fog");
        }
        if (containsIdentifier(body, "gl_FogFragCoord")) {
            preamble.append(stage == Stage.VERTEX ? "out float agris_FogFragCoord;\n" : "in float agris_FogFragCoord;\n");
            body = replaceIdentifier(body, "gl_FogFragCoord", "agris_FogFragCoord");
        }

        // 5. Fragment outputs: gl_FragColor / gl_FragData[k] -> explicit layout(location = k) out vec4.
        if (stage == Stage.FRAGMENT) {
            final int[] drawBuffers = directives == null ? new int[]{0} : directives.drawBuffers();
            final boolean usesFragColor = containsIdentifier(body, "gl_FragColor");
            final boolean usesFragData = GL_FRAG_DATA.matcher(body).find();

            if (usesFragColor) {
                preamble.append("layout(location = 0) out vec4 agris_FragData0;\n");
                body = replaceIdentifier(body, "gl_FragColor", "agris_FragData0");
            } else if (usesFragData) {
                final int count = Math.max(1, drawBuffers.length);
                for (int loc = 0; loc < count; loc++) {
                    preamble.append("layout(location = ").append(loc).append(") out vec4 agris_FragData").append(loc).append(";\n");
                }
                final Matcher fdm = GL_FRAG_DATA.matcher(body);
                final StringBuffer sb = new StringBuffer();
                while (fdm.find()) {
                    final int idx = Integer.parseInt(fdm.group(1));
                    final int clamped = Math.max(0, Math.min(count - 1, idx));
                    fdm.appendReplacement(sb, "agris_FragData" + clamped);
                }
                fdm.appendTail(sb);
                body = sb.toString();
            }
        }
        return body;
    }

    private static String rewriteVarying(final String body, final String replacementKeyword) {
        final Matcher m = VARYING_DECL.matcher(body);
        final StringBuffer sb = new StringBuffer(body.length() + 32);
        while (m.find()) {
            final String indent = m.group(1);
            final String interp = m.group(2) == null ? "" : m.group(2);
            m.appendReplacement(sb, Matcher.quoteReplacement(indent + interp + replacementKeyword));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static boolean containsIdentifier(final String source, final String ident) {
        return Pattern.compile("\\b" + Pattern.quote(ident) + "\\b").matcher(source).find();
    }

    private static boolean declaresIdentifier(final String source, final String ident) {
        return Pattern.compile("\\b(?:in|attribute|uniform)\\s+(?:highp\\s+|mediump\\s+|lowp\\s+)?[A-Za-z0-9_]+\\s+"
                + Pattern.quote(ident) + "\\b").matcher(source).find();
    }

    private static String replaceIdentifier(final String source, final String ident, final String replacement) {
        return Pattern.compile("\\b" + Pattern.quote(ident) + "\\b")
                .matcher(source)
                .replaceAll(Matcher.quoteReplacement(replacement));
    }

    /**
     * Strips {@code //} and {@code /* *&#47;} comments while preserving exact newline counts
     * so compiler line numbers match the original shader file, and never strips directive
     * comments before {@link ShaderDirectiveParser} has already parsed the raw source.
     */
    private static String stripCommentsPreservingLines(final String src) {
        final StringBuilder out = new StringBuilder(src.length());
        final int len = src.length();
        int i = 0;
        while (i < len) {
            final char c = src.charAt(i);
            if (c == '/' && i + 1 < len) {
                final char next = src.charAt(i + 1);
                if (next == '/') {
                    i += 2;
                    while (i < len && src.charAt(i) != '\n') {
                        i++;
                    }
                    continue;
                }
                if (next == '*') {
                    i += 2;
                    while (i < len) {
                        if (src.charAt(i) == '*' && i + 1 < len && src.charAt(i + 1) == '/') {
                            i += 2;
                            break;
                        }
                        if (src.charAt(i) == '\n') {
                            out.append('\n');
                        }
                        i++;
                    }
                    out.append(' ');
                    continue;
                }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }
}
