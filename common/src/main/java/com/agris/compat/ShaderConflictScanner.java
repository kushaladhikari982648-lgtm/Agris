package com.agris.compat;

import com.agris.platform.PlatformAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Scans the active mod list for companion and conflicting rendering/shader mods.
 *
 * <p>Agris is engineered specifically for <b>Aetherium</b> and vanilla OpenGL:
 * Aetherium is recognized as a first-class {@link Severity#COMPANION}, whereas
 * duplicate shader engines ({@code iris}, {@code oculus}, {@code optifine}) or
 * non-OpenGL/incompatible terrain replacements ({@code vulkanmod}, {@code sodium},
 * {@code embeddium}) cause Agris to stand down cleanly rather than fighting over
 * the GL pipeline.</p>
 */
public final class ShaderConflictScanner {
    public enum Severity {
        INFO,
        COMPANION,
        DELEGATE,
        HARD
    }

    public record KnownEntry(
            String modId,
            String displayName,
            Severity severity,
            String explanation) {
    }

    public record DetectedMod(
            String modId,
            String displayName,
            String version,
            Severity severity,
            String explanation) {
    }

    public record ScanReport(
            Severity highestSeverity,
            boolean aetheriumPresent,
            String aetheriumVersion,
            List<DetectedMod> findings) {

        public boolean blocksPipeline() {
            return this.highestSeverity == Severity.HARD || this.highestSeverity == Severity.DELEGATE;
        }

        public String summary() {
            if (this.findings.isEmpty()) {
                return "Standalone OpenGL mode (no companion or conflicting mods detected).";
            }
            final StringBuilder out = new StringBuilder();
            for (int i = 0; i < this.findings.size(); i++) {
                if (i > 0) {
                    out.append("; ");
                }
                final DetectedMod d = this.findings.get(i);
                out.append(d.displayName()).append(' ').append(d.version())
                        .append(" [").append(d.severity()).append(']');
            }
            return out.toString();
        }
    }

    private static final List<KnownEntry> KNOWN = List.of(
            new KnownEntry(
                    "aetherium",
                    "Aetherium",
                    Severity.COMPANION,
                    "Companion renderer detected: Agris binds natively to Aetherium's screen, culling guard, render scale, and Android profile."),
            new KnownEntry(
                    "iris",
                    "Iris Shaders",
                    Severity.HARD,
                    "Standalone Iris is already loaded; Agris stands down so only one shader pipeline runs."),
            new KnownEntry(
                    "oculus",
                    "Oculus",
                    Severity.HARD,
                    "Oculus is already loaded; Agris stands down so only one shader pipeline runs."),
            new KnownEntry(
                    "optifine",
                    "OptiFine",
                    Severity.HARD,
                    "OptiFine replaces the OpenGL pipeline; Agris stands down."),
            new KnownEntry(
                    "optifabric",
                    "OptiFabric",
                    Severity.HARD,
                    "OptiFabric loads OptiFine; Agris stands down."),
            new KnownEntry(
                    "vulkanmod",
                    "VulkanMod",
                    Severity.HARD,
                    "VulkanMod replaces OpenGL with Vulkan; Agris requires an OpenGL context."),
            new KnownEntry(
                    "sodium",
                    "Sodium",
                    Severity.DELEGATE,
                    "Sodium replaces chunk terrain rendering; Agris targets Aetherium + vanilla OpenGL and stands down when Sodium is present."),
            new KnownEntry(
                    "embeddium",
                    "Embeddium",
                    Severity.DELEGATE,
                    "Embeddium replaces chunk terrain rendering; Agris targets Aetherium + vanilla OpenGL and stands down when Embeddium is present."),
            new KnownEntry(
                    "distanthorizons",
                    "Distant Horizons",
                    Severity.INFO,
                    "Distant Horizons detected; IrisApi v0 hooks are exposed by Agris.")
    );

    private ShaderConflictScanner() {
    }

    public static ScanReport scan(final PlatformAdapter platform) {
        Objects.requireNonNull(platform, "platform");
        final List<DetectedMod> found = new ArrayList<>();
        Severity highest = Severity.INFO;
        boolean aetherium = false;
        String aetheriumVer = "none";

        for (final KnownEntry known : KNOWN) {
            if (!platform.isExternalModLoaded(known.modId())) {
                continue;
            }
            final String version = platform.getModVersion(known.modId());
            found.add(new DetectedMod(known.modId(), known.displayName(), version, known.severity(), known.explanation()));
            if ("aetherium".equals(known.modId())) {
                aetherium = true;
                aetheriumVer = version;
            }
            if (known.severity().ordinal() > highest.ordinal()) {
                highest = known.severity();
            }
        }
        return new ScanReport(highest, aetherium, aetheriumVer, Collections.unmodifiableList(found));
    }
}
