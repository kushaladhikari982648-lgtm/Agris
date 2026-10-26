package com.agris.config;

import com.agris.util.AgrisLog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Loads and atomically saves {@link AgrisConfig} to {@code <config>/agris.json},
 * preserving unknown keys written by newer versions or companion tools and falling
 * back cleanly on Android FUSE filesystems where atomic rename across mounts can throw.
 */
public final class ConfigStore {
    private static final String SCHEMA_KEY = "_schema_version";

    private final Path file;
    private final AgrisConfig config;
    private final Map<String, Object> preservedUnknown = new LinkedHashMap<>();

    public ConfigStore(final Path file, final AgrisConfig config) {
        this.file = Objects.requireNonNull(file, "file");
        this.config = Objects.requireNonNull(config, "config");
    }

    public Path file() {
        return this.file;
    }

    public AgrisConfig config() {
        return this.config;
    }

    public synchronized void load() {
        if (!Files.isRegularFile(this.file)) {
            save();
            return;
        }
        final String content;
        try {
            content = Files.readString(this.file, StandardCharsets.UTF_8);
        } catch (final IOException ex) {
            AgrisLog.warn("Failed reading config {}, keeping defaults: {}", this.file, ex.getMessage());
            return;
        }
        final Map<String, Object> root;
        try {
            root = Json.parseObject(content);
        } catch (final IllegalArgumentException ex) {
            AgrisLog.warn("Corrupt JSON in {}, backing up and regenerating defaults: {}", this.file, ex.getMessage());
            backupCorruptFile();
            save();
            return;
        }
        this.preservedUnknown.clear();
        for (final Map.Entry<String, Object> entry : root.entrySet()) {
            final String key = entry.getKey();
            if (SCHEMA_KEY.equals(key)) {
                continue;
            }
            final ConfigValue<?> holder = this.config.get(key);
            if (holder != null) {
                holder.deserializeFromJson(entry.getValue());
            } else {
                this.preservedUnknown.put(key, entry.getValue());
            }
        }
    }

    public synchronized void save() {
        final Map<String, Object> root = new LinkedHashMap<>();
        root.put(SCHEMA_KEY, AgrisConfig.CURRENT_VERSION);
        for (final ConfigValue<?> holder : this.config.allValues()) {
            root.put(holder.key(), holder.serializeToJson());
        }
        root.putAll(this.preservedUnknown);
        final String serialized = Json.stringify(root);

        try {
            final Path dir = this.file.getParent();
            if (dir != null) {
                Files.createDirectories(dir);
            }
            final Path temp = this.file.resolveSibling(this.file.getFileName().toString() + ".tmp");
            Files.writeString(temp, serialized, StandardCharsets.UTF_8);
            try {
                Files.move(temp, this.file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (final IOException moveError) {
                try {
                    Files.move(temp, this.file, StandardCopyOption.REPLACE_EXISTING);
                } catch (final IOException secondError) {
                    Files.writeString(this.file, serialized, StandardCharsets.UTF_8);
                    Files.deleteIfExists(temp);
                }
            }
        } catch (final IOException ex) {
            AgrisLog.warn("Unable to save config {}: {}", this.file, ex.getMessage());
        }
    }

    private void backupCorruptFile() {
        try {
            final Path backup = this.file.resolveSibling(this.file.getFileName().toString() + ".bak");
            Files.copy(this.file, backup, StandardCopyOption.REPLACE_EXISTING);
        } catch (final IOException ignored) {
            // Best-effort backup only.
        }
    }
}
