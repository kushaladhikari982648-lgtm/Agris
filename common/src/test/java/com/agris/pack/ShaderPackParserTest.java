package com.agris.pack;

import com.agris.gl.InternalTextureFormat;
import com.agris.glsl.ProgramDirectives;
import com.agris.glsl.ShaderDirectiveParser;
import com.agris.glsl.ShaderOptionParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class ShaderPackParserTest {

    @Test
    public void parsesOptionsProfilesScreensAndDynamicExpressions() {
        final Map<String, ShaderPackOption> options = new LinkedHashMap<>();
        ShaderOptionParser.scanSource("""
                #define BLOOM // [ON OFF]
                //#define MOTION_BLUR
                #define EXPOSURE 1.0 // [0.5 0.75 1.0 1.25 1.5]
                const int shadowMapResolution = 1024; // [512 1024 2048 4096]
                """, options);

        assertEquals(4, options.size());
        assertTrue(options.get("BLOOM").isBoolean());
        assertEquals("true", options.get("BLOOM").defaultValue());
        assertEquals("false", options.get("MOTION_BLUR").defaultValue());
        assertEquals(List.of("0.5", "0.75", "1.0", "1.25", "1.5"), options.get("EXPOSURE").allowedValues());
        assertEquals("1024", options.get("shadowMapResolution").defaultValue());

        final PackDirectives packDir = new PackDirectives();
        ShadersPropertiesParser.parse("""
                profile.LOW = !BLOOM !MOTION_BLUR shadowMapResolution=512 EXPOSURE=1.0
                profile.HIGH = BLOOM !MOTION_BLUR shadowMapResolution=2048 EXPOSURE=1.0
                profile.ULTRA = profile.HIGH MOTION_BLUR shadowMapResolution=4096

                screen = [LIGHTING] [POST] shadowMapResolution
                screen.POST = BLOOM MOTION_BLUR EXPOSURE
                sliders = EXPOSURE shadowMapResolution

                shadow.culling = false
                clouds = off
                separateAo = true
                variable.float.pulse = wetness * 0.5 + 0.5
                uniform.float.dangerGlow = if(isInLava, 1.0, pulse * 0.5)
                """, Map.of("BLOOM", "true"), packDir);

        assertTrue(packDir.separateAo());
        assertEquals("off", packDir.cloudSetting());
        assertTrue(packDir.sliders().contains("EXPOSURE"));
        assertEquals("false", packDir.profiles().get("LOW").get("BLOOM"));
        assertEquals("true", packDir.profiles().get("ULTRA").get("BLOOM"));
        assertEquals("true", packDir.profiles().get("ULTRA").get("MOTION_BLUR"));
        assertEquals("4096", packDir.profiles().get("ULTRA").get("shadowMapResolution"));

        final UniformExpression pulse = packDir.customVariables().get("pulse");
        final UniformExpression dangerGlow = packDir.customUniforms().get("dangerGlow");
        assertNotNull(pulse);
        assertNotNull(dangerGlow);

        final Map<String, Double> env = new LinkedHashMap<>();
        env.put("wetness", 1.0);
        env.put("isInLava", 0.0);
        final double pVal = pulse.evaluate(k -> env.getOrDefault(k, 0.0));
        assertEquals(1.0, pVal, 1e-5);
        env.put("pulse", pVal);
        assertEquals(0.5f, dangerGlow.evaluateFloat(k -> env.getOrDefault(k, 0.0)), 1e-5f);

        env.put("isInLava", 1.0);
        assertEquals(1.0f, dangerGlow.evaluateFloat(k -> env.getOrDefault(k, 0.0)), 1e-5f);
    }

    @Test
    public void parsesShaderDirectivesBufferFormatsAndIdMaps() {
        final String compositeFrag = """
                #version 120
                /*
                const int colortex0Format = RGBA16F;
                const int colortex1Format = RGB8;
                const bool colortex2Clear = false;
                const vec4 colortex2ClearColor = vec4(0.1, 0.2, 0.3, 1.0);
                const bool colortex0MipmapEnabled = true;
                const int shadowMapResolution = 2048;
                const float shadowDistance = 160.0;
                const float sunPathRotation = -35.0;
                */
                /* RENDERTARGETS: 0,2 */
                void main() {
                    gl_FragData[0] = vec4(1.0);
                    gl_FragData[1] = vec4(0.5);
                }
                """;

        final PackDirectives packDir = new PackDirectives();
        ShaderDirectiveParser.scanPackDirectives(compositeFrag, packDir);

        assertEquals(InternalTextureFormat.RGBA16F, packDir.bufferFormat(0));
        assertEquals(InternalTextureFormat.RGB8, packDir.bufferFormat(1));
        assertFalse(packDir.bufferClear(2));
        assertTrue(packDir.bufferMipmap(0));
        assertEquals(2048, packDir.shadowMapResolution());
        assertEquals(160.0f, packDir.shadowDistance(), 1e-4f);
        assertEquals(-35.0f, packDir.sunPathRotation(), 1e-4f);

        final ProgramDirectives pdir = ShaderDirectiveParser.parseProgramDirectives(compositeFrag, 0.1f, 1.0f);
        assertTrue(Arrays.equals(new int[]{0, 2}, pdir.drawBuffers()));

        final IdMap idMap = IdMap.parse(
                "block.10008 = minecraft:water minecraft:flowing_water\nblock.10020 = oak_leaves[persistent=false] birch_leaves\n",
                "item.20001 = minecraft:torch minecraft:lantern\n",
                "entity.30001 = minecraft:blaze\n",
                "dimension.world0 = minecraft:overworld\n");
        assertEquals(10008, idMap.blockId("minecraft:water"));
        assertEquals(10020, idMap.blockId("minecraft:oak_leaves"));
        assertEquals(20001, idMap.itemId("minecraft:lantern"));
        assertEquals(30001, idMap.entityId("minecraft:blaze"));
    }

    @Test
    public void loadsZipShaderPackAndPersistsOptionsRoundTrip() throws IOException {
        final Path tempDir = Files.createTempDirectory("agris-pack-test");
        final Path zipFile = tempDir.resolve("TestPack.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFile), StandardCharsets.UTF_8)) {
            writeZipEntry(zos, "shaders/shaders.properties", "profile.FAST = !BLOOM\nscreen = BLOOM\n");
            writeZipEntry(zos, "shaders/lang/en_us.lang", "option.BLOOM = Soft Bloom\nprofile.FAST = Fast Mode\n");
            writeZipEntry(zos, "shaders/final.vsh", "#version 120\nvarying vec2 texcoord;\nvoid main(){ gl_Position = ftransform(); texcoord = gl_MultiTexCoord0.xy; }\n");
            writeZipEntry(zos, "shaders/final.fsh", "#version 120\n#define BLOOM // [ON OFF]\nvarying vec2 texcoord;\nuniform sampler2D colortex0;\nvoid main(){ gl_FragColor = texture2D(colortex0, texcoord); }\n");
        }

        final ShaderPackOption bloomOpt = ShaderPackOption.booleanDefine("BLOOM", true, "");
        ShaderPackOptionStore.save(tempDir, "TestPack.zip", Map.of("BLOOM", bloomOpt), Map.of("BLOOM", "false"));
        final Map<String, String> loadedOverrides = ShaderPackOptionStore.load(tempDir, "TestPack.zip");
        assertEquals("false", loadedOverrides.get("BLOOM"));

        final ShaderPackSource src = ShaderPackSource.fromZip(zipFile);
        final ShaderPack pack = ShaderPack.load(src, loadedOverrides, "en_us");
        assertEquals("Soft Bloom", pack.languageMap().optionLabel("BLOOM"));
        assertEquals("false", pack.activeOptionValues().get("BLOOM"));
        assertNotNull(pack.resolveProgram("final", "minecraft:overworld"));
    }

    private static void writeZipEntry(final ZipOutputStream zos, final String name, final String content) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }
}
