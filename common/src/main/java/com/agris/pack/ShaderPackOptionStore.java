package com.agris.pack;

import com.agris.util.AgrisLog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads and writes OptiFine / Iris compatible per-pack {@code <packname>.txt}
 * option files inside {@code <game>/shaderpacks/}, so existing shader configurations
 * created in Iris or OptiFine work seamlessly in Agris and vice-versa.
 */
public final class ShaderPackOptionStore {
    private ShaderPackOptionStore() {
    }

    public static Path optionFileFor(final Path shaderpacksDir, final String packId) {
        final String safeName = sanitizePackId(packId) + ".txt";
        return shaderpacksDir.resolve(safeName);
    }

    public static Map<String, String> load(final Path shaderpacksDir, final String packId) {
        if (shaderpacksDir == null || packId == null || packId.isBlank()) {
            return Collections.emptyMap();
        }
        final Path file = optionFileFor(shaderpacksDir, packId);
        if (!Files.isRegularFile(file)) {
            return Collections.emptyMap();
        }
        final Map<String, String> values = new LinkedHashMap<>();
        try {
            for (final String rawLine : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                final String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) {
                    continue;
                }
                final int eq = line.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                final String key = line.substring(0, eq).trim();
                final String val = line.substring(eq + 1).trim();
                if (!key.isEmpty()) {
                    values.put(key, val);
                }
            }
        } catch (final IOException ex) {
            AgrisLog.warn("Unable to read shader pack options {}: {}", file, ex.getMessage());
        }
        return values;
    }

    public static void save(final Path shaderpacksDir,
                            final String packId,
                            final Map<String, ShaderPackOption> declaredOptions,
                            final Map<String, String> currentValues) {
        if (shaderpacksDir == null || packId == null || packId.isBlank()) {
            return;
        }
        final Path file = optionFileFor(shaderpacksDir, packId);
        final StringBuilder out = new StringBuilder(256);
        out.append("# Agris / Iris compatible shader pack options\n");
        for (final Map.Entry<String, String> entry : currentValues.entrySet()) {
            final String key = entry.getKey();
            final String val = entry.getValue();
            if (key == null || val == null) {
                continue;
            }
            final ShaderPackOption opt = declaredOptions == null ? null : declaredOptions.get(key);
            // Only write non-default overrides (matching Iris/OptiFine behavior so pack updates inherit new defaults).
            if (opt == null || !opt.defaultValue().equals(val)) {
                out.append(key).append('=').append(val).append('\n');
            }
        }
        try {
            Files.createDirectories(shaderpacksDir);
            Files.writeString(file, out.toString(), StandardCharsets.UTF_8);
        } catch (final IOException ex) {
            AgrisLog.warn("Unable to write shader pack options {}: {}", file, ex.getMessage());
        }
    }

    private static String sanitizePackId(final String packId) {
        String s = packId.trim();
        if (s.startsWith("(") && s.endsWith(")") && s.length() > 2) {
            s = "agris_" + s.substring(1, s.length() - 1);
        }
        return s.replaceAll("[^A-Za-z0-9._+-]", "_");
    }
}
