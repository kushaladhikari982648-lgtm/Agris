package com.agris.pack;

import com.agris.util.AgrisLog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Scans {@code <game>/shaderpacks/} for external {@code .zip} and folder shader packs,
 * combines them with Agris's built-in shader packs, and opens selected packs with their
 * persisted {@code <pack>.txt} user option overrides.
 */
public final class ShaderPackDiscovery {
    public enum PackKind {
        BUILTIN,
        ZIP,
        DIRECTORY
    }

    public record PackEntry(
            String id,
            String displayName,
            PackKind kind,
            Path diskPath) {

        public String badge() {
            return switch (this.kind) {
                case BUILTIN -> "BUILT-IN";
                case ZIP -> "ZIP";
                case DIRECTORY -> "FOLDER";
            };
        }
    }

    private final Path shaderpacksDir;

    public ShaderPackDiscovery(final Path shaderpacksDir) {
        this.shaderpacksDir = Objects.requireNonNull(shaderpacksDir, "shaderpacksDir");
    }

    public Path shaderpacksDirectory() {
        return this.shaderpacksDir;
    }

    public void ensureDirectoryExists() {
        try {
            Files.createDirectories(this.shaderpacksDir);
        } catch (final IOException ex) {
            AgrisLog.warn("Unable to create shaderpacks directory {}: {}", this.shaderpacksDir, ex.getMessage());
        }
    }

    public List<PackEntry> discoverPacks() {
        ensureDirectoryExists();
        final List<PackEntry> result = new ArrayList<>();
        for (final ShaderPackSource builtin : BuiltinShaderPacks.all()) {
            result.add(new PackEntry(builtin.id(), builtin.displayName(), PackKind.BUILTIN, null));
        }

        if (!Files.isDirectory(this.shaderpacksDir)) {
            return result;
        }

        final List<PackEntry> external = new ArrayList<>();
        try (Stream<Path> children = Files.list(this.shaderpacksDir)) {
            for (final Path entry : (Iterable<Path>) children::iterator) {
                final String name = entry.getFileName().toString();
                if (name.startsWith(".")) {
                    continue;
                }
                if (Files.isDirectory(entry) && ShaderPackSource.isValidShaderDirectory(entry)) {
                    external.add(new PackEntry(name, name, PackKind.DIRECTORY, entry));
                } else if (Files.isRegularFile(entry)
                        && name.toLowerCase(Locale.ROOT).endsWith(".zip")
                        && ShaderPackSource.isValidShaderZip(entry)) {
                    final String title = name.substring(0, name.length() - 4);
                    external.add(new PackEntry(name, title, PackKind.ZIP, entry));
                }
            }
        } catch (final IOException ex) {
            AgrisLog.warn("Error scanning shaderpacks directory {}: {}", this.shaderpacksDir, ex.getMessage());
        }
        external.sort(Comparator.comparing(e -> e.displayName().toLowerCase(Locale.ROOT)));
        result.addAll(external);
        return List.copyOf(result);
    }

    public ShaderPack loadPack(final String packId, final String activeLocale) throws IOException {
        if (packId == null || packId.isBlank() || "off".equalsIgnoreCase(packId)) {
            return null;
        }
        final ShaderPackSource source = openSource(packId);
        final Map<String, String> savedOptions = ShaderPackOptionStore.load(this.shaderpacksDir, source.id());
        return ShaderPack.load(source, savedOptions, activeLocale);
    }

    public ShaderPackSource openSource(final String packId) throws IOException {
        final ShaderPackSource builtin = BuiltinShaderPacks.byIdOrNull(packId);
        if (builtin != null) {
            return builtin;
        }
        final Path candidate = this.shaderpacksDir.resolve(packId);
        if (Files.isDirectory(candidate)) {
            return ShaderPackSource.fromDirectory(candidate);
        }
        if (Files.isRegularFile(candidate)) {
            return ShaderPackSource.fromZip(candidate);
        }
        final Path withZip = this.shaderpacksDir.resolve(packId + ".zip");
        if (Files.isRegularFile(withZip)) {
            return ShaderPackSource.fromZip(withZip);
        }
        throw new IOException("Shader pack '" + packId + "' not found in " + this.shaderpacksDir);
    }
}
