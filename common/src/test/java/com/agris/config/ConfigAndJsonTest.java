package com.agris.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class ConfigAndJsonTest {

    @Test
    public void jsonParserSupportsCommentsTrailingCommasAndRoundTrip() {
        final String raw = """
                {
                  // Agris configuration test
                  "general.enabled": true,
                  "shadow.resolution_scale": 150,
                  "nested": {
                    "list": ["alpha", "beta",],
                  },
                }
                """;
        final Map<String, Object> parsed = Json.parseObject(raw);
        assertEquals(Boolean.TRUE, parsed.get("general.enabled"));
        assertEquals(150L, ((Number) parsed.get("shadow.resolution_scale")).longValue());

        final String formatted = Json.stringify(parsed);
        final Map<String, Object> reparsed = Json.parseObject(formatted);
        assertEquals(parsed.get("general.enabled"), reparsed.get("general.enabled"));
    }

    @Test
    public void configStoreClampsRangesPreservesUnknownKeysAndRecoversCorruptFile() throws IOException {
        final Path dir = Files.createTempDirectory("agris-cfg-test");
        final Path cfgPath = dir.resolve("agris.json");

        Files.writeString(cfgPath, """
                {
                  "general.enabled": true,
                  "shadow.resolution_scale": 999999,
                  "quality.render_scale": 10,
                  "future.unknown_option": "keep-me"
                }
                """, StandardCharsets.UTF_8);

        final AgrisConfig cfg = AgrisConfig.createDefaults();
        final ConfigStore store = new ConfigStore(cfgPath, cfg);
        store.load();

        assertTrue(cfg.enabled.get());
        assertEquals(200, (int) cfg.shadowResolutionScale.get(), "Out-of-bounds int must clamp to max");
        assertEquals(50, (int) cfg.renderScale.get(), "Below-min int must clamp to min");

        cfg.shaderPack.set("(agris-lite)");
        store.save();

        final String savedText = Files.readString(cfgPath, StandardCharsets.UTF_8);
        assertTrue(savedText.contains("\"future.unknown_option\": \"keep-me\""));
        assertTrue(savedText.contains("\"(agris-lite)\""));

        // Corrupt JSON recovery
        Files.writeString(cfgPath, "{ broken json !!!", StandardCharsets.UTF_8);
        final AgrisConfig recovered = AgrisConfig.createDefaults();
        final ConfigStore recoveryStore = new ConfigStore(cfgPath, recovered);
        recoveryStore.load();
        assertFalse(recovered.enabled.get(), "Corrupt config must fall back cleanly to defaults");
        assertTrue(Files.exists(dir.resolve("agris.json.bak")), "Corrupt config must be backed up to .bak");
    }
}
