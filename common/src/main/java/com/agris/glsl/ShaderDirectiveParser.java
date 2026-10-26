package com.agris.glsl;

import com.agris.gl.InternalTextureFormat;
import com.agris.pack.PackDirectives;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts both per-program directives ({@code RENDERTARGETS}, {@code DRAWBUFFERS})
 * and pack-wide constants ({@code shadowMapResolution}, {@code shadowDistance},
 * {@code sunPathRotation}, {@code colortexNFormat}, {@code colortexNClear},
 * {@code colortexNMipmapEnabled}, {@code centerDepthHalflife}, etc.) from GLSL code.
 */
public final class ShaderDirectiveParser {
    private static final Pattern RENDERTARGETS = Pattern.compile(
            "/\\*\\s*RENDERTARGETS\\s*:\\s*([0-9,\\s]+)\\*/");
    private static final Pattern DRAWBUFFERS = Pattern.compile(
            "/\\*\\s*DRAWBUFFERS\\s*:\\s*([0-9A-Fa-f\\s]+)\\*/");
    private static final Pattern FRAG_DATA_INDEX = Pattern.compile(
            "\\bgl_FragData\\s*\\[\\s*(\\d+)\\s*]");

    private static final Pattern COMMENT_KV = Pattern.compile(
            "/\\*\\s*(SHADOWRES|SHADOWHPL|SUNPATHROTATION|AMBIENTOCCLUSIONLEVEL)\\s*:\\s*([^*/\\s]+)\\s*\\*/");
    private static final Pattern COMMENT_FORMAT = Pattern.compile(
            "/\\*\\s*(GCOLORFORMAT|GDEPTHFORMAT|GNORMALFORMAT|COMPOSITEFORMAT|GAUX1FORMAT|GAUX2FORMAT|GAUX3FORMAT|GAUX4FORMAT)\\s*:\\s*([A-Za-z0-9_]+)\\s*\\*/");

    private static final Pattern CONST_INT = Pattern.compile(
            "(?m)^\\s*const\\s+int\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*([A-Za-z0-9_+-]+)\\s*;");
    private static final Pattern CONST_FLOAT = Pattern.compile(
            "(?m)^\\s*const\\s+float\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*([0-9.eE+-]+f?)\\s*;");
    private static final Pattern CONST_BOOL = Pattern.compile(
            "(?m)^\\s*const\\s+bool\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*(true|false)\\s*;");
    private static final Pattern CONST_VEC4_CLEAR = Pattern.compile(
            "(?m)^\\s*const\\s+vec4\\s+colortex(\\d+)ClearColor\\s*=\\s*vec4\\s*\\(([^)]+)\\)\\s*;");

    private static final Map<String, Integer> LEGACY_BUFFER_INDICES = Map.of(
            "gcolor", 0,
            "gdepth", 1,
            "gnormal", 2,
            "composite", 3,
            "gaux1", 4,
            "gaux2", 5,
            "gaux3", 6,
            "gaux4", 7
    );

    private ShaderDirectiveParser() {
    }

    /** Extracts per-program {@link ProgramDirectives} from a fragment shader source. */
    public static ProgramDirectives parseProgramDirectives(final String fragmentSource,
                                                           final float configuredAlphaRef,
                                                           final float configuredScale) {
        if (fragmentSource == null || fragmentSource.isEmpty()) {
            return new ProgramDirectives(new int[]{0}, false, configuredAlphaRef, configuredScale, new int[0]);
        }

        // 1. Modern /* RENDERTARGETS: 0,1,4,7 */ takes precedence over legacy /* DRAWBUFFERS: ... */.
        final Matcher rt = RENDERTARGETS.matcher(fragmentSource);
        int[] buffers = null;
        boolean explicit = false;
        while (rt.find()) {
            final String[] parts = rt.group(1).trim().split("[,\\s]+");
            final List<Integer> parsed = new ArrayList<>(parts.length);
            for (final String p : parts) {
                if (!p.isEmpty()) {
                    final int idx = Integer.parseInt(p);
                    if (idx >= 0 && idx < 16) {
                        parsed.add(idx);
                    }
                }
            }
            if (!parsed.isEmpty()) {
                buffers = toIntArray(parsed);
                explicit = true;
            }
        }

        // 2. Legacy /* DRAWBUFFERS: 0124 */ (each digit/hex character is one buffer 0..15).
        if (buffers == null) {
            final Matcher db = DRAWBUFFERS.matcher(fragmentSource);
            while (db.find()) {
                final String digits = db.group(1).replaceAll("\\s+", "");
                final List<Integer> parsed = new ArrayList<>(digits.length());
                for (int i = 0; i < digits.length(); i++) {
                    final int idx = Character.digit(digits.charAt(i), 16);
                    if (idx >= 0 && idx < 16) {
                        parsed.add(idx);
                    }
                }
                if (!parsed.isEmpty()) {
                    buffers = toIntArray(parsed);
                    explicit = true;
                }
            }
        }

        // 3. Implicit gl_FragData[k] scan when no comment directive is present.
        if (buffers == null) {
            final TreeSet<Integer> referenced = new TreeSet<>();
            final Matcher fd = FRAG_DATA_INDEX.matcher(fragmentSource);
            while (fd.find()) {
                final int idx = Integer.parseInt(fd.group(1));
                if (idx >= 0 && idx < 16) {
                    referenced.add(idx);
                }
            }
            if (!referenced.isEmpty()) {
                final int max = referenced.last();
                buffers = new int[max + 1];
                for (int i = 0; i <= max; i++) {
                    buffers[i] = i;
                }
            } else {
                buffers = new int[]{0};
            }
        }

        final List<Integer> mipInputs = new ArrayList<>();
        final Matcher bm = CONST_BOOL.matcher(fragmentSource);
        while (bm.find()) {
            final String name = bm.group(1);
            final boolean val = Boolean.parseBoolean(bm.group(2));
            if (val) {
                final Integer bufIdx = resolveBufferPrefix(name, "MipmapEnabled");
                if (bufIdx != null && !mipInputs.contains(bufIdx)) {
                    mipInputs.add(bufIdx);
                }
            }
        }

        return new ProgramDirectives(buffers, explicit, configuredAlphaRef, configuredScale, toIntArray(mipInputs));
    }

    /** Scans a GLSL source for pack-wide constants and merges them into {@code packDirectives}. */
    public static void scanPackDirectives(final String source, final PackDirectives packDirectives) {
        if (source == null || source.isEmpty()) {
            return;
        }

        // Legacy comment directives
        final Matcher ckv = COMMENT_KV.matcher(source);
        while (ckv.find()) {
            final String key = ckv.group(1);
            final String raw = ckv.group(2);
            try {
                switch (key) {
                    case "SHADOWRES" -> packDirectives.setShadowMapResolution(Integer.parseInt(raw));
                    case "SHADOWHPL" -> packDirectives.setShadowDistance(Float.parseFloat(raw));
                    case "SUNPATHROTATION" -> packDirectives.setSunPathRotation(Float.parseFloat(raw));
                    case "AMBIENTOCCLUSIONLEVEL" -> packDirectives.setAmbientOcclusionLevel(Float.parseFloat(raw));
                    default -> {
                    }
                }
            } catch (final NumberFormatException ignored) {
                // Ignore malformed comment directive.
            }
        }

        final Matcher cfmt = COMMENT_FORMAT.matcher(source);
        while (cfmt.find()) {
            final String prefix = cfmt.group(1).substring(0, cfmt.group(1).length() - "FORMAT".length()).toLowerCase();
            final Integer idx = LEGACY_BUFFER_INDICES.get(prefix);
            if (idx != null) {
                InternalTextureFormat.byName(cfmt.group(2))
                        .ifPresent(fmt -> packDirectives.setBufferFormat(idx, fmt));
            }
        }

        // const int ...
        final Matcher ci = CONST_INT.matcher(source);
        while (ci.find()) {
            final String name = ci.group(1);
            final String raw = ci.group(2);
            if ("shadowMapResolution".equals(name)) {
                parseIntSafe(raw, packDirectives::setShadowMapResolution);
            } else if ("noiseTextureResolution".equals(name)) {
                parseIntSafe(raw, packDirectives::setNoiseTextureResolution);
            } else if (name.endsWith("Format")) {
                final Integer bufIdx = resolveBufferPrefix(name, "Format");
                if (bufIdx != null) {
                    InternalTextureFormat.byName(raw).ifPresent(fmt -> packDirectives.setBufferFormat(bufIdx, fmt));
                } else if ("shadowcolor0Format".equals(name)) {
                    InternalTextureFormat.byName(raw).ifPresent(fmt -> packDirectives.setShadowColorFormat(0, fmt));
                } else if ("shadowcolor1Format".equals(name)) {
                    InternalTextureFormat.byName(raw).ifPresent(fmt -> packDirectives.setShadowColorFormat(1, fmt));
                }
            }
        }

        // const float ...
        final Matcher cf = CONST_FLOAT.matcher(source);
        while (cf.find()) {
            final String name = cf.group(1);
            final String raw = cf.group(2).replace("f", "");
            try {
                final float val = Float.parseFloat(raw);
                switch (name) {
                    case "shadowDistance" -> packDirectives.setShadowDistance(val);
                    case "shadowIntervalSize" -> packDirectives.setShadowIntervalSize(val);
                    case "sunPathRotation" -> packDirectives.setSunPathRotation(val);
                    case "ambientOcclusionLevel" -> packDirectives.setAmbientOcclusionLevel(val);
                    case "centerDepthHalflife" -> packDirectives.setCenterDepthHalfLife(val);
                    case "wetnessHalfLife" -> packDirectives.setWetnessHalfLife(val);
                    case "drynessHalfLife" -> packDirectives.setDrynessHalfLife(val);
                    case "eyeBrightnessHalflife" -> packDirectives.setEyeBrightnessHalfLife(val);
                    default -> {
                    }
                }
            } catch (final NumberFormatException ignored) {
                // Ignore malformed float literal.
            }
        }

        // const bool ...
        final Matcher cb = CONST_BOOL.matcher(source);
        while (cb.find()) {
            final String name = cb.group(1);
            final boolean val = Boolean.parseBoolean(cb.group(2));
            if (name.endsWith("Clear")) {
                final Integer bufIdx = resolveBufferPrefix(name, "Clear");
                if (bufIdx != null) {
                    packDirectives.setBufferClear(bufIdx, val);
                }
            } else if (name.endsWith("MipmapEnabled")) {
                final Integer bufIdx = resolveBufferPrefix(name, "MipmapEnabled");
                if (bufIdx != null && val) {
                    packDirectives.setBufferMipmap(bufIdx, true);
                }
            } else if ("shadowHardwareFiltering".equals(name) || "shadowHardwareFiltering0".equals(name)) {
                packDirectives.setShadowHardwareFiltering(0, val);
            } else if ("shadowHardwareFiltering1".equals(name)) {
                packDirectives.setShadowHardwareFiltering(1, val);
            }
        }

        // const vec4 colortexNClearColor = vec4(...);
        final Matcher cv = CONST_VEC4_CLEAR.matcher(source);
        while (cv.find()) {
            final int idx = Integer.parseInt(cv.group(1));
            if (idx >= 0 && idx < 16) {
                final float[] rgba = parseVec4Components(cv.group(2));
                if (rgba != null) {
                    packDirectives.setBufferClearColor(idx, rgba[0], rgba[1], rgba[2], rgba[3]);
                }
            }
        }
    }

    private static Integer resolveBufferPrefix(final String fullName, final String suffix) {
        if (!fullName.endsWith(suffix)) {
            return null;
        }
        final String prefix = fullName.substring(0, fullName.length() - suffix.length());
        if (prefix.startsWith("colortex")) {
            try {
                final int idx = Integer.parseInt(prefix.substring("colortex".length()));
                return idx >= 0 && idx < 16 ? idx : null;
            } catch (final NumberFormatException ignored) {
                return null;
            }
        }
        return LEGACY_BUFFER_INDICES.get(prefix);
    }

    private static float[] parseVec4Components(final String body) {
        final String[] parts = body.trim().split(",");
        if (parts.length != 1 && parts.length != 4) {
            return null;
        }
        try {
            if (parts.length == 1) {
                final float v = Float.parseFloat(parts[0].trim().replace("f", ""));
                return new float[]{v, v, v, v};
            }
            return new float[]{
                    Float.parseFloat(parts[0].trim().replace("f", "")),
                    Float.parseFloat(parts[1].trim().replace("f", "")),
                    Float.parseFloat(parts[2].trim().replace("f", "")),
                    Float.parseFloat(parts[3].trim().replace("f", ""))
            };
        } catch (final NumberFormatException ignored) {
            return null;
        }
    }

    private static void parseIntSafe(final String raw, final java.util.function.IntConsumer sink) {
        try {
            sink.accept(Integer.parseInt(raw.trim()));
        } catch (final NumberFormatException ignored) {
            // Symbolic constant or invalid integer.
        }
    }

    private static int[] toIntArray(final List<Integer> list) {
        final int[] arr = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            arr[i] = list.get(i);
        }
        return arr;
    }
}
