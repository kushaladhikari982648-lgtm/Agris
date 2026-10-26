package com.agris.pack;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parses {@code shaders/lang/*.lang} localization files from a shader pack to provide
 * human-friendly option labels, value names, tooltips, profile labels, and screen titles.
 */
public final class LanguageMap {
    private static final LanguageMap EMPTY = new LanguageMap(Collections.emptyMap());

    private final Map<String, String> entries;

    public LanguageMap(final Map<String, String> entries) {
        this.entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries));
    }

    public static LanguageMap empty() {
        return EMPTY;
    }

    public static LanguageMap parse(final String... langSources) {
        final Map<String, String> map = new LinkedHashMap<>();
        if (langSources != null) {
            for (final String src : langSources) {
                if (src == null || src.isEmpty()) {
                    continue;
                }
                for (final String rawLine : src.replace("\r\n", "\n").replace('\r', '\n').split("\n")) {
                    final String trimmed = rawLine.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("//")) {
                        continue;
                    }
                    final int eq = trimmed.indexOf('=');
                    if (eq <= 0) {
                        continue;
                    }
                    final String key = trimmed.substring(0, eq).trim();
                    final String val = stripFormattingCodes(trimmed.substring(eq + 1).trim());
                    if (!key.isEmpty() && !val.isEmpty()) {
                        map.put(key, val);
                    }
                }
            }
        }
        return map.isEmpty() ? EMPTY : new LanguageMap(map);
    }

    public String optionLabel(final String optionName) {
        return this.entries.getOrDefault("option." + optionName, prettifyIdentifier(optionName));
    }

    public String optionTooltip(final String optionName, final String fallbackComment) {
        final String fromLang = this.entries.get("option." + optionName + ".comment");
        if (fromLang != null && !fromLang.isBlank()) {
            return fromLang;
        }
        return fallbackComment == null ? "" : fallbackComment;
    }

    public String valueLabel(final String optionName, final String rawValue) {
        final String specific = this.entries.get("value." + optionName + '.' + rawValue);
        if (specific != null) {
            return specific;
        }
        if ("true".equalsIgnoreCase(rawValue)) {
            return "ON";
        }
        if ("false".equalsIgnoreCase(rawValue)) {
            return "OFF";
        }
        final String suffix = this.entries.getOrDefault("suffix." + optionName, "");
        final String prefix = this.entries.getOrDefault("prefix." + optionName, "");
        return prefix + rawValue + suffix;
    }

    public String profileLabel(final String profileName) {
        return this.entries.getOrDefault("profile." + profileName, profileName);
    }

    public String screenLabel(final String screenKey) {
        return this.entries.getOrDefault("screen." + screenKey, prettifyIdentifier(screenKey));
    }

    public int size() {
        return this.entries.size();
    }

    private static String stripFormattingCodes(final String text) {
        return text.replaceAll("\u00A7[0-9A-FK-ORa-fk-or]", "");
    }

    private static String prettifyIdentifier(final String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        if (raw.equals(raw.toUpperCase()) && raw.contains("_")) {
            final String[] parts = raw.toLowerCase().split("_");
            final StringBuilder sb = new StringBuilder();
            for (final String p : parts) {
                if (p.isEmpty()) {
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
            }
            return sb.toString();
        }
        return raw;
    }
}
