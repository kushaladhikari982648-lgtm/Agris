package com.agris.pack;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Parses {@code block.properties}, {@code item.properties}, {@code entity.properties},
 * and {@code dimension.properties} so shader packs receive accurate numeric IDs in
 * {@code mc_Entity}, {@code entityId}, {@code heldItemId}, and dimension routing
 * ({@code world0}, {@code world-1}, {@code world1}).
 */
public final class IdMap {
    private final Map<String, Integer> blockIds;
    private final Map<String, Integer> itemIds;
    private final Map<String, Integer> entityIds;
    private final Map<String, String> dimensionFolders;

    public IdMap(final Map<String, Integer> blockIds,
                 final Map<String, Integer> itemIds,
                 final Map<String, Integer> entityIds,
                 final Map<String, String> dimensionFolders) {
        this.blockIds = Collections.unmodifiableMap(new HashMap<>(blockIds));
        this.itemIds = Collections.unmodifiableMap(new HashMap<>(itemIds));
        this.entityIds = Collections.unmodifiableMap(new HashMap<>(entityIds));
        this.dimensionFolders = Collections.unmodifiableMap(new LinkedHashMap<>(dimensionFolders));
    }

    public static IdMap empty() {
        return new IdMap(Map.of(), Map.of(), Map.of(), defaultDimensions());
    }

    public static IdMap parse(final String blockProps,
                              final String itemProps,
                              final String entityProps,
                              final String dimensionProps) {
        final Map<String, Integer> blocks = parseNumericCategory(blockProps, "block.");
        final Map<String, Integer> items = parseNumericCategory(itemProps, "item.");
        final Map<String, Integer> entities = parseNumericCategory(entityProps, "entity.");
        final Map<String, String> dims = defaultDimensions();
        parseDimensions(dimensionProps, dims);
        return new IdMap(blocks, items, entities, dims);
    }

    public int blockId(final String namespacedBlockId) {
        return lookup(this.blockIds, namespacedBlockId);
    }

    public int itemId(final String namespacedItemId) {
        final int explicit = lookup(this.itemIds, namespacedItemId);
        return explicit >= 0 ? explicit : lookup(this.blockIds, namespacedItemId);
    }

    public int entityId(final String namespacedEntityId) {
        return lookup(this.entityIds, namespacedEntityId);
    }

    /**
     * Resolves the dimension shader subfolder (e.g. {@code "world0"}, {@code "world-1"}, {@code "world1"})
     * for a Minecraft dimension identifier such as {@code "minecraft:overworld"}.
     */
    public String dimensionFolder(final String dimensionId) {
        if (dimensionId == null || dimensionId.isBlank()) {
            return "world0";
        }
        final String norm = normalizeId(dimensionId);
        final String direct = this.dimensionFolders.get(norm);
        if (direct != null) {
            return direct;
        }
        final int colon = norm.indexOf(':');
        if (colon >= 0) {
            final String bare = norm.substring(colon + 1);
            final String byBare = this.dimensionFolders.get("minecraft:" + bare);
            if (byBare != null) {
                return byBare;
            }
        }
        return "world0";
    }

    private static int lookup(final Map<String, Integer> table, final String rawId) {
        if (rawId == null || rawId.isEmpty() || table.isEmpty()) {
            return -1;
        }
        final String norm = normalizeId(rawId);
        final Integer exact = table.get(norm);
        if (exact != null) {
            return exact;
        }
        // Strip blockstate properties if present (e.g. "minecraft:oak_leaves:persistent=false").
        final int secondColon = norm.indexOf(':', norm.indexOf(':') + 1);
        if (secondColon > 0) {
            final Integer base = table.get(norm.substring(0, secondColon));
            if (base != null) {
                return base;
            }
        }
        return -1;
    }

    private static Map<String, Integer> parseNumericCategory(final String source, final String prefix) {
        if (source == null || source.isBlank()) {
            return Map.of();
        }
        final Map<String, Integer> result = new HashMap<>();
        for (final String line : joinContinuationLines(source)) {
            final String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("//") || !trimmed.startsWith(prefix)) {
                continue;
            }
            final int eq = trimmed.indexOf('=');
            if (eq <= prefix.length()) {
                continue;
            }
            final String idPart = trimmed.substring(prefix.length(), eq).trim();
            final int numericId;
            try {
                numericId = Integer.parseInt(idPart);
            } catch (final NumberFormatException ignored) {
                continue;
            }
            final String rhs = trimmed.substring(eq + 1).trim();
            for (final String token : rhs.split("\\s+")) {
                if (token.isEmpty()) {
                    continue;
                }
                final String baseToken = stripPropertyQualifiers(token);
                result.putIfAbsent(normalizeId(baseToken), numericId);
                result.putIfAbsent(normalizeId(token), numericId);
            }
        }
        return result;
    }

    private static void parseDimensions(final String source, final Map<String, String> out) {
        if (source == null || source.isBlank()) {
            return;
        }
        for (final String line : joinContinuationLines(source)) {
            final String trimmed = line.trim();
            if (!trimmed.startsWith("dimension.")) {
                continue;
            }
            final int eq = trimmed.indexOf('=');
            if (eq <= "dimension.".length()) {
                continue;
            }
            final String folder = trimmed.substring("dimension.".length(), eq).trim();
            final String rhs = trimmed.substring(eq + 1).trim();
            for (final String token : rhs.split("\\s+")) {
                if (!token.isEmpty() && !"*".equals(token)) {
                    out.put(normalizeId(token), folder);
                }
            }
        }
    }

    private static Map<String, String> defaultDimensions() {
        final Map<String, String> map = new LinkedHashMap<>();
        map.put("minecraft:overworld", "world0");
        map.put("minecraft:the_nether", "world-1");
        map.put("minecraft:the_end", "world1");
        return map;
    }

    private static String stripPropertyQualifiers(final String token) {
        final int bracket = token.indexOf('[');
        if (bracket > 0) {
            return token.substring(0, bracket);
        }
        final int eq = token.indexOf('=');
        if (eq < 0) {
            return token;
        }
        final int colonBeforeEq = token.lastIndexOf(':', eq);
        return colonBeforeEq > 0 ? token.substring(0, colonBeforeEq) : token;
    }

    private static String normalizeId(final String id) {
        final String clean = id.trim().toLowerCase(Locale.ROOT);
        return clean.indexOf(':') >= 0 ? clean : "minecraft:" + clean;
    }

    static java.util.List<String> joinContinuationLines(final String source) {
        final String[] raw = source.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        final java.util.List<String> joined = new java.util.ArrayList<>(raw.length);
        final StringBuilder acc = new StringBuilder();
        for (final String line : raw) {
            final String rtrimmed = line.stripTrailing();
            if (rtrimmed.endsWith("\\")) {
                acc.append(rtrimmed, 0, rtrimmed.length() - 1).append(' ');
            } else {
                acc.append(line);
                joined.add(acc.toString());
                acc.setLength(0);
            }
        }
        if (acc.length() > 0) {
            joined.add(acc.toString());
        }
        return joined;
    }
}
