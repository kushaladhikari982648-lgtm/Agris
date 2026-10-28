package com.agris.glsl;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves OptiFine / Iris {@code #include "/..."} and relative {@code #include "..."}
 * directives across a shader pack's virtual filesystem with cycle detection.
 */
public final class GlslIncludeResolver {
    private static final Pattern INCLUDE_PATTERN = Pattern.compile(
            "^\\s*#\\s*include\\s+[\"<]([^\">]+)[\">]\\s*(?://.*|/\\*.*\\*/\\s*)?$");
    private static final int MAX_INCLUDE_DEPTH = 32;

    private GlslIncludeResolver() {
    }

    /**
     * Inlines all {@code #include} directives starting from {@code rootPath} (normalized relative to
     * {@code /shaders}, e.g. {@code "/gbuffers_terrain.vsh"} or {@code "/world0/gbuffers_terrain.vsh"}).
     *
     * @param rootPath   starting shader path within {@code shaders/}
     * @param rootSource starting GLSL text
     * @param fileLoader function mapping a normalized path like {@code "/lib/common.glsl"} to its text or {@code null}
     * @return expanded GLSL source
     */
    public static String resolve(final String rootPath, final String rootSource,
                                 final Function<String, String> fileLoader) {
        Objects.requireNonNull(rootSource, "rootSource");
        Objects.requireNonNull(fileLoader, "fileLoader");
        final Deque<String> stack = new ArrayDeque<>();
        final String normalizedRoot = normalizePath("/", rootPath == null ? "/main.glsl" : rootPath);
        stack.push(normalizedRoot);
        final StringBuilder out = new StringBuilder(rootSource.length() + 512);
        expandInto(normalizedRoot, rootSource, fileLoader, stack, out);
        return out.toString();
    }

    private static void expandInto(final String currentPath, final String source,
                                   final Function<String, String> loader,
                                   final Deque<String> stack, final StringBuilder out) {
        final String currentDir = parentDir(currentPath);
        final String[] lines = splitLines(source);
        for (final String line : lines) {
            final Matcher matcher = INCLUDE_PATTERN.matcher(line);
            if (!matcher.matches()) {
                out.append(line).append('\n');
                continue;
            }
            final String rawTarget = matcher.group(1).trim();
            final String resolved = normalizePath(currentDir, rawTarget);
            if (stack.contains(resolved)) {
                out.append("// [agris] skipped cyclic #include ").append(resolved).append('\n');
                continue;
            }
            if (stack.size() >= MAX_INCLUDE_DEPTH) {
                throw new IllegalArgumentException("Maximum #include depth (" + MAX_INCLUDE_DEPTH + ") exceeded at " + resolved);
            }
            String included = loader.apply(resolved);
            if (included == null && resolved.startsWith("/")) {
                included = loader.apply(resolved.substring(1));
            }
            if (included == null) {
                throw new IllegalArgumentException("Missing #include file '" + resolved + "' referenced from '" + currentPath + "'");
            }
            stack.push(resolved);
            expandInto(resolved, included, loader, stack, out);
            stack.pop();
        }
    }

    public static String normalizePath(final String currentDir, final String target) {
        final String raw = target.replace('\\', '/').trim();
        final String combined;
        if (raw.startsWith("/")) {
            combined = raw;
        } else {
            final String base = currentDir.endsWith("/") ? currentDir : currentDir + '/';
            combined = base + raw;
        }
        final String[] segments = combined.split("/");
        final Deque<String> clean = new ArrayDeque<>();
        for (final String seg : segments) {
            if (seg.isEmpty() || ".".equals(seg)) {
                continue;
            }
            if ("..".equals(seg)) {
                if (!clean.isEmpty()) {
                    clean.removeLast();
                }
            } else {
                clean.addLast(seg);
            }
        }
        final StringBuilder sb = new StringBuilder("/");
        int idx = 0;
        for (final String part : clean) {
            if (idx++ > 0) {
                sb.append('/');
            }
            sb.append(part);
        }
        return sb.toString();
    }

    private static String parentDir(final String path) {
        final int slash = path.lastIndexOf('/');
        return slash <= 0 ? "/" : path.substring(0, slash);
    }

    private static String[] splitLines(final String text) {
        return text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
    }
}
