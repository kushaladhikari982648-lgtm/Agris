package com.agris.pack;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Unified virtual filesystem for reading shader pack files (relative to {@code shaders/})
 * from a {@code .zip} archive, an unpacked directory in {@code shaderpacks/}, or an
 * in-memory built-in Agris shader pack.
 *
 * <p>Automatically unwraps single-folder {@code .zip} layouts (e.g.
 * {@code MyPack.zip/MyPack-v2/shaders/gbuffers_terrain.vsh} -&gt; {@code /gbuffers_terrain.vsh}),
 * matching Iris's archive normalization.</p>
 */
public final class ShaderPackSource {
    private final String id;
    private final String displayName;
    private final boolean builtin;
    private final Map<String, byte[]> files; // keys normalized as "/path/within/shaders"

    private ShaderPackSource(final String id, final String displayName,
                             final boolean builtin, final Map<String, byte[]> files) {
        this.id = Objects.requireNonNull(id, "id");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.builtin = builtin;
        this.files = Collections.unmodifiableMap(files);
    }

    public static ShaderPackSource fromMemory(final String id, final String displayName,
                                              final Map<String, String> textFiles) {
        final Map<String, byte[]> binary = new LinkedHashMap<>();
        for (final Map.Entry<String, String> entry : textFiles.entrySet()) {
            binary.put(normalizeShadersKey(entry.getKey()), entry.getValue().getBytes(StandardCharsets.UTF_8));
        }
        return new ShaderPackSource(id, displayName, true, binary);
    }

    public static ShaderPackSource fromDirectory(final Path rootDir) throws IOException {
        Objects.requireNonNull(rootDir, "rootDir");
        Path shadersRoot = rootDir.resolve("shaders");
        if (!Files.isDirectory(shadersRoot)) {
            // Check for single nested subdirectory containing shaders/
            try (Stream<Path> children = Files.list(rootDir)) {
                final Path candidate = children.filter(Files::isDirectory)
                        .map(p -> p.resolve("shaders"))
                        .filter(Files::isDirectory)
                        .findFirst()
                        .orElse(null);
                if (candidate == null) {
                    throw new IOException("No shaders/ directory found inside " + rootDir);
                }
                shadersRoot = candidate;
            }
        }
        final Path base = shadersRoot;
        final Map<String, byte[]> loaded = new LinkedHashMap<>();
        try (Stream<Path> walk = Files.walk(base)) {
            for (final Path file : (Iterable<Path>) walk::iterator) {
                if (!Files.isRegularFile(file)) {
                    continue;
                }
                final String rel = '/' + base.relativize(file).toString().replace('\\', '/');
                loaded.put(rel, Files.readAllBytes(file));
            }
        }
        final String name = rootDir.getFileName().toString();
        return new ShaderPackSource(name, name, false, loaded);
    }

    public static ShaderPackSource fromZip(final Path zipPath) throws IOException {
        Objects.requireNonNull(zipPath, "zipPath");
        final Map<String, byte[]> loaded = new LinkedHashMap<>();
        try (ZipFile zip = new ZipFile(zipPath.toFile(), StandardCharsets.UTF_8)) {
            final String shadersPrefix = locateShadersPrefix(zip);
            if (shadersPrefix == null) {
                throw new IOException("Archive does not contain a shaders/ directory: " + zipPath);
            }
            final Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                final ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                final String entryName = entry.getName().replace('\\', '/');
                if (!entryName.startsWith(shadersPrefix)) {
                    continue;
                }
                final String rel = '/' + entryName.substring(shadersPrefix.length());
                try (InputStream in = zip.getInputStream(entry)) {
                    loaded.put(rel, readAll(in));
                }
            }
        }
        final String fileName = zipPath.getFileName().toString();
        final String cleanTitle = fileName.toLowerCase().endsWith(".zip")
                ? fileName.substring(0, fileName.length() - 4)
                : fileName;
        return new ShaderPackSource(fileName, cleanTitle, false, loaded);
    }

    public static boolean isValidShaderZip(final Path zipPath) {
        try (ZipFile zip = new ZipFile(zipPath.toFile(), StandardCharsets.UTF_8)) {
            return locateShadersPrefix(zip) != null;
        } catch (final IOException ex) {
            return false;
        }
    }

    public static boolean isValidShaderDirectory(final Path dir) {
        if (!Files.isDirectory(dir)) {
            return false;
        }
        if (Files.isDirectory(dir.resolve("shaders"))) {
            return true;
        }
        try (Stream<Path> children = Files.list(dir)) {
            return children.filter(Files::isDirectory).anyMatch(p -> Files.isDirectory(p.resolve("shaders")));
        } catch (final IOException ex) {
            return false;
        }
    }

    private static String locateShadersPrefix(final ZipFile zip) {
        final Enumeration<? extends ZipEntry> entries = zip.entries();
        String fallbackNested = null;
        while (entries.hasMoreElements()) {
            final ZipEntry entry = entries.nextElement();
            final String name = entry.getName().replace('\\', '/');
            if (name.startsWith("shaders/") && name.length() > "shaders/".length()) {
                return "shaders/";
            }
            final int idx = name.indexOf("/shaders/");
            if (idx > 0 && fallbackNested == null) {
                fallbackNested = name.substring(0, idx + "/shaders/".length());
            }
        }
        return fallbackNested;
    }

    public String id() {
        return this.id;
    }

    public String displayName() {
        return this.displayName;
    }

    public boolean isBuiltin() {
        return this.builtin;
    }

    public boolean exists(final String path) {
        return this.files.containsKey(normalizeShadersKey(path));
    }

    public String readText(final String path) {
        final byte[] bytes = this.files.get(normalizeShadersKey(path));
        return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
    }

    public byte[] readBytes(final String path) {
        final byte[] bytes = this.files.get(normalizeShadersKey(path));
        return bytes == null ? null : bytes.clone();
    }

    public Set<String> allPaths() {
        return this.files.keySet();
    }

    /** Exports this shader pack source into a standard OptiFine/Iris {@code shaders/...} zip archive. */
    public void exportToZip(final Path outZip) throws IOException {
        Objects.requireNonNull(outZip, "outZip");
        final Path parent = outZip.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(
                Files.newOutputStream(outZip), StandardCharsets.UTF_8)) {
            for (final Map.Entry<String, byte[]> entry : this.files.entrySet()) {
                final String rel = entry.getKey().startsWith("/") ? entry.getKey().substring(1) : entry.getKey();
                final ZipEntry ze = new ZipEntry("shaders/" + rel);
                ze.setTime(1730073600000L);
                zos.putNextEntry(ze);
                zos.write(entry.getValue());
                zos.closeEntry();
            }
        }
    }

    private static String normalizeShadersKey(final String raw) {
        String clean = raw.replace('\\', '/').trim();
        if (clean.startsWith("shaders/")) {
            clean = clean.substring("shaders".length());
        } else if (clean.startsWith("/shaders/")) {
            clean = clean.substring("/shaders".length());
        }
        return clean.startsWith("/") ? clean : '/' + clean;
    }

    private static byte[] readAll(final InputStream in) throws IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream(4096);
        final byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) >= 0) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }
}
