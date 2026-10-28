package com.agris.glsl;

import com.agris.pack.ShaderPackOption;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Discovers OptiFine / Iris user-configurable shader options in GLSL sources and
 * rewrites active user/profile overrides back into the shader text before compilation.
 *
 * <p>Supported forms (matching OptiFine &amp; Iris specification):</p>
 * <ul>
 *   <li>Active boolean define: {@code #define SHADOW_FILTER // Smooth shadows}</li>
 *   <li>Commented-out boolean define: {@code // #define SHADOW_FILTER // Smooth shadows}</li>
 *   <li>Value define with range comment: {@code #define SHADOW_QUALITY 2 // [0 1 2 3 4]}</li>
 *   <li>Const option with range comment: {@code const int shadowMapResolution = 2048; // [512 1024 2048 4096]}</li>
 *   <li>Boolean const option: {@code const bool shadowHardwareFiltering = true;}</li>
 * </ul>
 */
public final class ShaderOptionParser {
    private static final Pattern ACTIVE_BOOL_DEFINE = Pattern.compile(
            "^(\\s*)#\\s*define\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*(?://\\s*(.*))?$");
    private static final Pattern INACTIVE_BOOL_DEFINE = Pattern.compile(
            "^(\\s*)//\\s*#\\s*define\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*(?://\\s*(.*))?$");
    private static final Pattern VALUE_DEFINE = Pattern.compile(
            "^(\\s*)#\\s*define\\s+([A-Za-z_][A-Za-z0-9_]*)\\s+([^/\\s]+)\\s*//\\s*(.*)$");
    private static final Pattern CONST_OPTION = Pattern.compile(
            "^(\\s*)const\\s+(int|float|bool)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*([^;]+);\\s*(?://\\s*(.*))?$");
    private static final Pattern BRACKET_VALUES = Pattern.compile("\\[([^\\]]+)]");

    private static final java.util.Set<String> RECOGNIZED_BARE_BOOL_CONSTS = java.util.Set.of(
            "shadowHardwareFiltering",
            "shadowHardwareFiltering0",
            "shadowHardwareFiltering1",
            "shadowtex0Mipmap",
            "shadowtexMipmap",
            "shadowtex1Mipmap",
            "shadowcolor0Mipmap",
            "shadowcolor1Mipmap",
            "generateShadowMipmap",
            "generateShadowColorMipmap",
            "oldHandLight"
    );

    private ShaderOptionParser() {
    }

    /** Scans one or more GLSL files and populates {@code outOptions} by option name. */
    public static void scanSource(final String source, final Map<String, ShaderPackOption> outOptions) {
        if (source == null || source.isEmpty()) {
            return;
        }
        final String[] lines = source.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        for (final String line : lines) {
            final Matcher valMatcher = VALUE_DEFINE.matcher(line);
            if (valMatcher.matches()) {
                final String name = valMatcher.group(2);
                final String defVal = valMatcher.group(3).trim();
                final String comment = cleanComment(valMatcher.group(4));
                final List<String> allowed = parseBracketList(valMatcher.group(4), defVal);
                if (!allowed.isEmpty() && !isInternalGuard(name)) {
                    outOptions.putIfAbsent(name, ShaderPackOption.valueDefine(name, defVal, allowed, comment));
                }
                continue;
            }

            final Matcher activeBool = ACTIVE_BOOL_DEFINE.matcher(line);
            if (activeBool.matches()) {
                final String name = activeBool.group(2);
                final String rawComment = activeBool.group(3);
                if (rawComment != null && !isInternalGuard(name)) {
                    outOptions.putIfAbsent(name, ShaderPackOption.booleanDefine(name, true, cleanComment(rawComment)));
                }
                continue;
            }

            final Matcher inactiveBool = INACTIVE_BOOL_DEFINE.matcher(line);
            if (inactiveBool.matches()) {
                final String name = inactiveBool.group(2);
                final String rawComment = inactiveBool.group(3);
                if (!isInternalGuard(name)) {
                    outOptions.putIfAbsent(name, ShaderPackOption.booleanDefine(
                            name, false, rawComment == null ? "" : cleanComment(rawComment)));
                }
                continue;
            }

            final Matcher constMatcher = CONST_OPTION.matcher(line);
            if (constMatcher.matches()) {
                final String type = constMatcher.group(2);
                final String name = constMatcher.group(3);
                final String defVal = constMatcher.group(4).trim();
                final String rawComment = constMatcher.group(5);
                if ("bool".equals(type) && ("true".equals(defVal) || "false".equals(defVal))) {
                    if (rawComment != null || RECOGNIZED_BARE_BOOL_CONSTS.contains(name) || name.endsWith("MipmapEnabled")) {
                        outOptions.putIfAbsent(name, ShaderPackOption.constBoolean(
                                name, Boolean.parseBoolean(defVal), rawComment == null ? "" : cleanComment(rawComment)));
                    }
                } else if (rawComment != null) {
                    final List<String> allowed = parseBracketList(rawComment, defVal);
                    if (!allowed.isEmpty()) {
                        outOptions.putIfAbsent(name, ShaderPackOption.constValue(
                                name, type, defVal, allowed, cleanComment(rawComment)));
                    }
                }
            }
        }
    }

    /**
     * Applies {@code overrides} (mapping {@code optionName -> valueString}) directly to the
     * matching {@code #define}, {@code // #define}, and {@code const} lines in {@code source}.
     */
    public static String applyOverrides(final String source,
                                        final Map<String, ShaderPackOption> declaredOptions,
                                        final Map<String, String> overrides) {
        if (source == null || overrides == null || overrides.isEmpty()) {
            return source;
        }
        final String[] lines = source.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        final StringBuilder out = new StringBuilder(source.length() + 64);

        for (int i = 0; i < lines.length; i++) {
            final String line = lines[i];
            final String rewritten = rewriteLine(line, declaredOptions, overrides);
            out.append(rewritten);
            if (i + 1 < lines.length) {
                out.append('\n');
            }
        }
        return out.toString();
    }

    private static String rewriteLine(final String line,
                                      final Map<String, ShaderPackOption> declared,
                                      final Map<String, String> overrides) {
        final Matcher valMatcher = VALUE_DEFINE.matcher(line);
        if (valMatcher.matches()) {
            final String indent = valMatcher.group(1);
            final String name = valMatcher.group(2);
            final String next = overrides.get(name);
            if (next != null && isAllowed(declared.get(name), next)) {
                return indent + "#define " + name + ' ' + next + " // " + valMatcher.group(4);
            }
            return line;
        }

        final Matcher activeBool = ACTIVE_BOOL_DEFINE.matcher(line);
        if (activeBool.matches()) {
            final String indent = activeBool.group(1);
            final String name = activeBool.group(2);
            final String next = overrides.get(name);
            if (next != null) {
                final boolean on = Boolean.parseBoolean(next);
                final String commentSuffix = activeBool.group(3) != null ? " // " + activeBool.group(3) : "";
                return on ? indent + "#define " + name + commentSuffix
                        : indent + "// #define " + name + commentSuffix;
            }
            return line;
        }

        final Matcher inactiveBool = INACTIVE_BOOL_DEFINE.matcher(line);
        if (inactiveBool.matches()) {
            final String indent = inactiveBool.group(1);
            final String name = inactiveBool.group(2);
            final String next = overrides.get(name);
            if (next != null) {
                final boolean on = Boolean.parseBoolean(next);
                final String commentSuffix = inactiveBool.group(3) != null ? " // " + inactiveBool.group(3) : "";
                return on ? indent + "#define " + name + commentSuffix
                        : indent + "// #define " + name + commentSuffix;
            }
            return line;
        }

        final Matcher constMatcher = CONST_OPTION.matcher(line);
        if (constMatcher.matches()) {
            final String indent = constMatcher.group(1);
            final String type = constMatcher.group(2);
            final String name = constMatcher.group(3);
            final String next = overrides.get(name);
            if (next != null && isAllowed(declared.get(name), next)) {
                final String commentSuffix = constMatcher.group(5) != null ? " // " + constMatcher.group(5) : "";
                return indent + "const " + type + ' ' + name + " = " + next + ';' + commentSuffix;
            }
        }
        return line;
    }

    private static boolean isAllowed(final ShaderPackOption option, final String candidate) {
        if (option == null) {
            return true;
        }
        return option.isValidValue(candidate);
    }

    private static boolean isInternalGuard(final String name) {
        return name.endsWith("_INCLUDED") || name.endsWith("_GLSL") || name.startsWith("MC_") || "IS_IRIS".equals(name);
    }

    private static List<String> parseBracketList(final String rawComment, final String defaultVal) {
        if (rawComment == null) {
            return List.of();
        }
        final Matcher m = BRACKET_VALUES.matcher(rawComment);
        if (!m.find()) {
            return List.of();
        }
        final String inside = m.group(1).trim();
        if (inside.isEmpty()) {
            return List.of();
        }
        final String[] tokens = inside.split("[\\s,]+");
        final Map<String, Boolean> unique = new LinkedHashMap<>();
        boolean sawDefault = false;
        for (final String tok : tokens) {
            if (!tok.isEmpty()) {
                unique.put(tok, Boolean.TRUE);
                if (tok.equals(defaultVal)) {
                    sawDefault = true;
                }
            }
        }
        final List<String> result = new ArrayList<>(unique.keySet());
        if (!sawDefault && defaultVal != null && !defaultVal.isEmpty()) {
            result.add(0, defaultVal);
        }
        return result;
    }

    private static String cleanComment(final String raw) {
        if (raw == null) {
            return "";
        }
        return BRACKET_VALUES.matcher(raw).replaceAll("").trim();
    }
}
