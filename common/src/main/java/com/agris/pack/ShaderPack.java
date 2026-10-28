package com.agris.pack;

import com.agris.glsl.GlslIncludeResolver;
import com.agris.glsl.ProgramDirectives;
import com.agris.glsl.ShaderDirectiveParser;
import com.agris.glsl.ShaderOptionParser;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Fully parsed, option-resolved, dimension-aware representation of a loaded shader pack.
 */
public final class ShaderPack {
    public record ProgramSource(
            String name,
            String vertexSource,
            String geometrySource,
            String fragmentSource,
            ProgramDirectives directives) {
    }

    private final ShaderPackSource source;
    private final Map<String, ShaderPackOption> options;
    private final Map<String, String> activeOptionValues;
    private final PackDirectives directives;
    private final LanguageMap languageMap;
    private final IdMap idMap;

    private ShaderPack(final ShaderPackSource source,
                       final Map<String, ShaderPackOption> options,
                       final Map<String, String> activeOptionValues,
                       final PackDirectives directives,
                       final LanguageMap languageMap,
                       final IdMap idMap) {
        this.source = source;
        this.options = Collections.unmodifiableMap(options);
        this.activeOptionValues = Collections.unmodifiableMap(activeOptionValues);
        this.directives = directives;
        this.languageMap = languageMap;
        this.idMap = idMap;
    }

    public static ShaderPack load(final ShaderPackSource source,
                                  final Map<String, String> userOverrides,
                                  final String activeLocale) {
        Objects.requireNonNull(source, "source");

        // 1. Scan all .vsh, .gsh, .fsh, .glsl, .inc files for declared #define & const options.
        final Map<String, ShaderPackOption> discoveredOptions = new LinkedHashMap<>();
        for (final String path : source.allPaths()) {
            if (isGlslSourcePath(path)) {
                ShaderOptionParser.scanSource(source.readText(path), discoveredOptions);
            }
        }

        // 2. Seed effective option map with defaults, then apply profile/user overrides.
        final Map<String, String> effectiveValues = new LinkedHashMap<>();
        for (final ShaderPackOption opt : discoveredOptions.values()) {
            effectiveValues.put(opt.name(), opt.defaultValue());
        }
        if (userOverrides != null) {
            for (final Map.Entry<String, String> e : userOverrides.entrySet()) {
                final ShaderPackOption opt = discoveredOptions.get(e.getKey());
                if (opt == null || opt.isValidValue(e.getValue())) {
                    effectiveValues.put(e.getKey(), e.getValue());
                }
            }
        }

        // 3. Parse shaders.properties (honoring #ifdef conditionals against effectiveValues).
        final PackDirectives packDirectives = new PackDirectives();
        final String shadersProps = source.readText("/shaders.properties");
        ShadersPropertiesParser.parse(shadersProps, effectiveValues, packDirectives);

        // If userOverrides specifies "profile=<Name>", expand that profile's option values too.
        if (userOverrides != null && userOverrides.containsKey("profile")) {
            final Map<String, String> prof = packDirectives.profiles().get(userOverrides.get("profile"));
            if (prof != null) {
                for (final Map.Entry<String, String> pe : prof.entrySet()) {
                    if (!userOverrides.containsKey(pe.getKey())) {
                        effectiveValues.put(pe.getKey(), pe.getValue());
                    }
                }
            }
        }

        // Mark slider options declared in shaders.properties.
        for (final String sliderName : packDirectives.sliders()) {
            final ShaderPackOption opt = discoveredOptions.get(sliderName);
            if (opt != null) {
                opt.setSlider(true);
            }
        }

        // 4. Scan resolved GLSL sources for pack-level const directives (shadowMapResolution, colortexNFormat, etc.).
        for (final String path : source.allPaths()) {
            if (isStageEntryPath(path)) {
                final String raw = source.readText(path);
                if (raw != null) {
                    final String overridden = ShaderOptionParser.applyOverrides(raw, discoveredOptions, effectiveValues);
                    final String expanded = safeResolveIncludes(path, overridden, source, discoveredOptions, effectiveValues);
                    ShaderDirectiveParser.scanPackDirectives(expanded, packDirectives);
                }
            }
        }

        // 5. Load localization & ID maps.
        final String enUs = firstNonNull(source.readText("/lang/en_us.lang"), source.readText("/lang/EN_US.lang"));
        final String localeText = activeLocale == null || "en_us".equalsIgnoreCase(activeLocale)
                ? null
                : source.readText("/lang/" + activeLocale.toLowerCase() + ".lang");
        final LanguageMap lang = LanguageMap.parse(enUs, localeText);

        final IdMap ids = IdMap.parse(
                source.readText("/block.properties"),
                source.readText("/item.properties"),
                source.readText("/entity.properties"),
                source.readText("/dimension.properties"));

        return new ShaderPack(source, discoveredOptions, effectiveValues, packDirectives, lang, ids);
    }

    /**
     * Resolves a stage program (e.g. {@code "gbuffers_terrain"}, {@code "shadow"}, {@code "deferred"},
     * {@code "composite"}, {@code "final"}) for {@code dimensionId}, checking the dimension subfolder
     * (e.g. {@code /world0/gbuffers_terrain.vsh}) before falling back to root {@code /gbuffers_terrain.vsh}.
     */
    public ProgramSource resolveProgram(final String programName, final String dimensionId) {
        final String dimFolder = this.idMap.dimensionFolder(dimensionId);
        String vshPath = '/' + dimFolder + '/' + programName + ".vsh";
        String fshPath = '/' + dimFolder + '/' + programName + ".fsh";
        String gshPath = '/' + dimFolder + '/' + programName + ".gsh";

        if (!this.source.exists(vshPath) || !this.source.exists(fshPath)) {
            vshPath = '/' + programName + ".vsh";
            fshPath = '/' + programName + ".fsh";
            gshPath = '/' + programName + ".gsh";
        }
        if (!this.source.exists(vshPath) || !this.source.exists(fshPath)) {
            return null;
        }

        final String vsh = loadAndExpand(vshPath);
        final String fsh = loadAndExpand(fshPath);
        final String gsh = this.source.exists(gshPath) ? loadAndExpand(gshPath) : null;

        final float alphaRef = this.directives.passAlphaRef(programName, -1.0f);
        final float scale = this.directives.passScale(programName, 1.0f);
        final ProgramDirectives progDirectives = ShaderDirectiveParser.parseProgramDirectives(fsh, alphaRef, scale);
        return new ProgramSource(programName, vsh, gsh, fsh, progDirectives);
    }

    public boolean hasShadowProgram(final String dimensionId) {
        return this.directives.isShadowEnabled()
                && (resolveProgram("shadow", dimensionId) != null
                || resolveProgram("shadow_solid", dimensionId) != null);
    }

    /** Computes which profile currently matches {@link #activeOptionValues()}, or {@code "Custom"}. */
    public String detectActiveProfileName() {
        for (final Map.Entry<String, Map<String, String>> entry : this.directives.profiles().entrySet()) {
            boolean allMatch = !entry.getValue().isEmpty();
            for (final Map.Entry<String, String> req : entry.getValue().entrySet()) {
                final String cur = this.activeOptionValues.get(req.getKey());
                if (cur != null && !cur.equals(req.getValue())) {
                    allMatch = false;
                    break;
                }
            }
            if (allMatch) {
                return entry.getKey();
            }
        }
        return "Custom";
    }

    public ShaderPackSource source() {
        return this.source;
    }

    public String id() {
        return this.source.id();
    }

    public String displayName() {
        return this.source.displayName();
    }

    public Map<String, ShaderPackOption> options() {
        return this.options;
    }

    public Map<String, String> activeOptionValues() {
        return this.activeOptionValues;
    }

    public PackDirectives directives() {
        return this.directives;
    }

    public LanguageMap languageMap() {
        return this.languageMap;
    }

    public IdMap idMap() {
        return this.idMap;
    }

    private String loadAndExpand(final String path) {
        final String raw = this.source.readText(path);
        final String overridden = ShaderOptionParser.applyOverrides(raw, this.options, this.activeOptionValues);
        return GlslIncludeResolver.resolve(path, overridden, incPath -> {
            final String incRaw = this.source.readText(incPath);
            return incRaw == null ? null : ShaderOptionParser.applyOverrides(incRaw, this.options, this.activeOptionValues);
        });
    }

    private static String safeResolveIncludes(final String path,
                                              final String overridden,
                                              final ShaderPackSource source,
                                              final Map<String, ShaderPackOption> options,
                                              final Map<String, String> values) {
        try {
            return GlslIncludeResolver.resolve(path, overridden, incPath -> {
                final String incRaw = source.readText(incPath);
                return incRaw == null ? null : ShaderOptionParser.applyOverrides(incRaw, options, values);
            });
        } catch (final IllegalArgumentException ignored) {
            return overridden;
        }
    }

    private static boolean isGlslSourcePath(final String path) {
        final String lower = path.toLowerCase();
        return lower.endsWith(".vsh") || lower.endsWith(".fsh") || lower.endsWith(".gsh")
                || lower.endsWith(".csh") || lower.endsWith(".glsl") || lower.endsWith(".inc");
    }

    private static boolean isStageEntryPath(final String path) {
        final String lower = path.toLowerCase();
        return lower.endsWith(".vsh") || lower.endsWith(".fsh") || lower.endsWith(".gsh");
    }

    private static String firstNonNull(final String a, final String b) {
        return a != null ? a : b;
    }
}
