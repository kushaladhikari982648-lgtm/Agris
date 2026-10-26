package com.agris.pack;

import com.agris.util.AgrisLog;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parses {@code shaders/shaders.properties}, including C-style preprocessor conditionals
 * ({@code #ifdef}, {@code #ifndef}, {@code #if}, {@code #elif}, {@code #else}, {@code #endif}),
 * profile inheritance, screen layouts, slider declarations, buffer scaling/flipping,
 * custom uniform/variable expressions, and custom texture bindings.
 */
public final class ShadersPropertiesParser {
    private ShadersPropertiesParser() {
    }

    public static void parse(final String source,
                             final Map<String, String> activeDefines,
                             final PackDirectives out) {
        if (source == null || source.isBlank()) {
            return;
        }
        final Map<String, String> rawProfiles = new LinkedHashMap<>();
        final Deque<CondFrame> condStack = new ArrayDeque<>();

        for (final String rawLine : IdMap.joinContinuationLines(source)) {
            final String trimmed = rawLine.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("//")) {
                continue;
            }
            if (trimmed.startsWith("#")) {
                if (handleDirective(trimmed, activeDefines, condStack)) {
                    continue;
                }
                // Non-preprocessor '#' line is a standard properties comment.
                continue;
            }
            if (!isCurrentlyActive(condStack)) {
                continue;
            }

            final int eq = findKeyValueSeparator(trimmed);
            if (eq <= 0) {
                continue;
            }
            final String key = trimmed.substring(0, eq).trim();
            final String val = trimmed.substring(eq + 1).trim();
            applyProperty(key, val, rawProfiles, out);
        }

        resolveProfiles(rawProfiles, out);
    }

    private static void applyProperty(final String key,
                                      final String val,
                                      final Map<String, String> rawProfiles,
                                      final PackDirectives out) {
        if (key.startsWith("profile.")) {
            rawProfiles.put(key.substring("profile.".length()).trim(), val);
            return;
        }
        if ("screen".equals(key) || (key.startsWith("screen.") && !key.endsWith(".columns"))) {
            final String screenName = "screen".equals(key) ? "main" : key.substring("screen.".length()).trim();
            out.putScreen(screenName, splitTokens(val));
            return;
        }
        if ("sliders".equals(key)) {
            for (final String token : splitTokens(val)) {
                out.addSlider(token);
            }
            return;
        }
        if (key.startsWith("scale.")) {
            final String pass = key.substring("scale.".length()).trim();
            final String[] parts = val.split("\\s+");
            parseFloatSafe(parts[0], s -> out.setPassScale(pass, s));
            return;
        }
        if (key.startsWith("size.buffer.colortex")) {
            final String suffix = key.substring("size.buffer.colortex".length()).trim();
            try {
                final int idx = Integer.parseInt(suffix);
                final String[] parts = val.split("\\s+");
                parseFloatSafe(parts[0], s -> out.setBufferScale(idx, s));
            } catch (final NumberFormatException ignored) {
                // Fixed pixel buffer sizes handled at default 1.0 scale.
            }
            return;
        }
        if (key.startsWith("alphaTest.")) {
            final String pass = key.substring("alphaTest.".length()).trim();
            if ("off".equalsIgnoreCase(val) || "false".equalsIgnoreCase(val)) {
                out.setPassAlphaRef(pass, 0.0f);
            } else {
                final String[] parts = val.split("\\s+");
                parseFloatSafe(parts[parts.length - 1], ref -> out.setPassAlphaRef(pass, ref));
            }
            return;
        }
        if (key.startsWith("flip.")) {
            // flip.<pass>.<colortexN|gcolor|...> = true|false
            final String rest = key.substring("flip.".length());
            final int dot = rest.indexOf('.');
            if (dot > 0) {
                final String pass = rest.substring(0, dot).trim();
                final Integer bufIdx = parseBufferIndex(rest.substring(dot + 1).trim());
                if (bufIdx != null) {
                    out.setExplicitFlip(pass, bufIdx, Boolean.parseBoolean(val));
                }
            }
            return;
        }
        if (key.startsWith("variable.")) {
            // variable.<type>.<name> = <expr>
            final String rest = key.substring("variable.".length());
            final int dot = rest.indexOf('.');
            if (dot > 0) {
                final String varName = rest.substring(dot + 1).trim();
                compileCustomExpr(varName, val, true, out);
            }
            return;
        }
        if (key.startsWith("uniform.")) {
            // uniform.<type>.<name> = <expr>
            final String rest = key.substring("uniform.".length());
            final int dot = rest.indexOf('.');
            if (dot > 0) {
                final String uniformName = rest.substring(dot + 1).trim();
                compileCustomExpr(uniformName, val, false, out);
            }
            return;
        }
        if (key.startsWith("texture.")) {
            out.putCustomTexture(key.substring("texture.".length()).trim(), val);
            return;
        }

        switch (key) {
            case "shadow.enabled", "shadowPass" -> out.setShadowEnabled(Boolean.parseBoolean(val));
            case "oldLighting" -> {
                if (!"default".equalsIgnoreCase(val)) {
                    out.setOldLighting(Boolean.parseBoolean(val));
                }
            }
            case "separateAo" -> out.setSeparateAo(Boolean.parseBoolean(val));
            case "rain.depth" -> out.setRainDepth(Boolean.parseBoolean(val));
            case "vignette" -> out.setVignette(Boolean.parseBoolean(val));
            case "underwaterOverlay" -> out.setUnderwaterOverlay(Boolean.parseBoolean(val));
            case "sky.enabled" -> out.setSkyEnabled(Boolean.parseBoolean(val));
            case "clouds" -> out.setCloudSetting(val.toLowerCase(Locale.ROOT));
            case "sunPathRotation" -> parseFloatSafe(val, out::setSunPathRotation);
            default -> {
            }
        }
    }

    private static void resolveProfiles(final Map<String, String> rawProfiles, final PackDirectives out) {
        for (final String name : rawProfiles.keySet()) {
            final Map<String, String> resolved = new LinkedHashMap<>();
            expandProfileRecursive(name, rawProfiles, resolved, new ArrayList<>());
            out.putProfile(name, resolved);
        }
    }

    private static void expandProfileRecursive(final String name,
                                               final Map<String, String> rawProfiles,
                                               final Map<String, String> outValues,
                                               final List<String> visited) {
        if (visited.contains(name)) {
            return;
        }
        visited.add(name);
        final String body = rawProfiles.get(name);
        if (body == null) {
            return;
        }
        for (final String token : body.trim().split("\\s+")) {
            if (token.isEmpty()) {
                continue;
            }
            if (token.startsWith("profile.")) {
                expandProfileRecursive(token.substring("profile.".length()), rawProfiles, outValues, visited);
            } else if (token.startsWith("!")) {
                outValues.put(token.substring(1), "false");
            } else {
                final int eq = token.indexOf('=');
                if (eq > 0) {
                    outValues.put(token.substring(0, eq), token.substring(eq + 1));
                } else {
                    final int colon = token.indexOf(':');
                    if (colon > 0) {
                        outValues.put(token.substring(0, colon), token.substring(colon + 1));
                    } else {
                        outValues.put(token, "true");
                    }
                }
            }
        }
    }

    private static void compileCustomExpr(final String name, final String exprText,
                                          final boolean isVariable, final PackDirectives out) {
        try {
            final UniformExpression compiled = UniformExpression.compile(exprText);
            if (isVariable) {
                out.putCustomVariable(name, compiled);
            } else {
                out.putCustomUniform(name, compiled);
            }
        } catch (final IllegalArgumentException ex) {
            AgrisLog.debug("Skipping unsupported custom expression '{} = {}': {}", name, exprText, ex.getMessage());
        }
    }

    private static boolean handleDirective(final String line,
                                           final Map<String, String> defines,
                                           final Deque<CondFrame> stack) {
        final String rest = line.substring(1).trim();
        if (rest.startsWith("ifdef ")) {
            final String sym = rest.substring("ifdef ".length()).trim();
            final boolean truth = isDefinedTruthy(sym, defines);
            stack.push(new CondFrame(truth, truth));
            return true;
        }
        if (rest.startsWith("ifndef ")) {
            final String sym = rest.substring("ifndef ".length()).trim();
            final boolean truth = !isDefinedTruthy(sym, defines);
            stack.push(new CondFrame(truth, truth));
            return true;
        }
        if (rest.startsWith("if ")) {
            final boolean truth = evalPreprocessorExpr(rest.substring("if ".length()).trim(), defines);
            stack.push(new CondFrame(truth, truth));
            return true;
        }
        if (rest.startsWith("elif ")) {
            if (!stack.isEmpty()) {
                final CondFrame top = stack.peek();
                if (top.matchedAny) {
                    top.active = false;
                } else {
                    final boolean truth = evalPreprocessorExpr(rest.substring("elif ".length()).trim(), defines);
                    top.active = truth;
                    top.matchedAny = truth;
                }
            }
            return true;
        }
        if ("else".equals(rest) || rest.startsWith("else ")) {
            if (!stack.isEmpty()) {
                final CondFrame top = stack.peek();
                top.active = !top.matchedAny;
                top.matchedAny = true;
            }
            return true;
        }
        if ("endif".equals(rest) || rest.startsWith("endif ")) {
            if (!stack.isEmpty()) {
                stack.pop();
            }
            return true;
        }
        return false;
    }

    private static boolean isCurrentlyActive(final Deque<CondFrame> stack) {
        for (final CondFrame f : stack) {
            if (!f.active) {
                return false;
            }
        }
        return true;
    }

    private static boolean isDefinedTruthy(final String sym, final Map<String, String> defines) {
        if ("IS_IRIS".equals(sym) || "IS_AGRIS".equals(sym)) {
            return true;
        }
        if (defines == null) {
            return false;
        }
        final String v = defines.get(sym);
        return v != null && !"false".equalsIgnoreCase(v);
    }

    private static boolean evalPreprocessorExpr(final String expr, final Map<String, String> defines) {
        final String expanded = expr.replaceAll("defined\\s*\\(\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*\\)", "$1_DEFINED_CHECK");
        try {
            final UniformExpression compiled = UniformExpression.compile(expanded);
            final double result = compiled.evaluate(name -> {
                if (name.endsWith("_DEFINED_CHECK")) {
                    final String base = name.substring(0, name.length() - "_DEFINED_CHECK".length());
                    return isDefinedTruthy(base, defines) ? 1.0 : 0.0;
                }
                if ("MC_VERSION".equals(name)) {
                    return com.agris.Capabilities.MC_VERSION_INT;
                }
                if (defines != null && defines.containsKey(name)) {
                    final String raw = defines.get(name);
                    if ("true".equalsIgnoreCase(raw)) {
                        return 1.0;
                    }
                    if ("false".equalsIgnoreCase(raw)) {
                        return 0.0;
                    }
                    try {
                        return Double.parseDouble(raw);
                    } catch (final NumberFormatException ignored) {
                        return 1.0;
                    }
                }
                return isDefinedTruthy(name, defines) ? 1.0 : 0.0;
            });
            return result != 0.0;
        } catch (final IllegalArgumentException ignored) {
            return true;
        }
    }

    private static Integer parseBufferIndex(final String name) {
        if (name.startsWith("colortex")) {
            try {
                final int idx = Integer.parseInt(name.substring("colortex".length()));
                return idx >= 0 && idx < 16 ? idx : null;
            } catch (final NumberFormatException ignored) {
                return null;
            }
        }
        return switch (name) {
            case "gcolor" -> 0;
            case "gdepth" -> 1;
            case "gnormal" -> 2;
            case "composite" -> 3;
            case "gaux1" -> 4;
            case "gaux2" -> 5;
            case "gaux3" -> 6;
            case "gaux4" -> 7;
            default -> null;
        };
    }

    private static int findKeyValueSeparator(final String line) {
        final int eq = line.indexOf('=');
        return eq > 0 ? eq : -1;
    }

    private static List<String> splitTokens(final String raw) {
        final String cleaned = raw.replace('[', ' ').replace(']', ' ').trim();
        if (cleaned.isEmpty()) {
            return List.of();
        }
        final List<String> list = new ArrayList<>();
        for (final String tok : cleaned.split("\\s+")) {
            if (!tok.isEmpty()) {
                list.add(tok);
            }
        }
        return list;
    }

    private static void parseFloatSafe(final String raw, final FloatConsumer sink) {
        try {
            sink.accept(Float.parseFloat(raw.trim()));
        } catch (final NumberFormatException ignored) {
            // Ignore symbolic/invalid float.
        }
    }

    @FunctionalInterface
    private interface FloatConsumer {
        void accept(float value);
    }

    private static final class CondFrame {
        boolean active;
        boolean matchedAny;

        CondFrame(final boolean active, final boolean matchedAny) {
            this.active = active;
            this.matchedAny = matchedAny;
        }
    }
}
