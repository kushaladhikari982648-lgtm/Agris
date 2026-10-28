package com.agris.api;

import com.agris.Agris;
import com.agris.platform.PlatformAdapter;
import com.agris.platform.PlatformServices;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.api.v0.IrisTextVertexSink;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class IrisApiCompatibilityTest {

    @Test
    public void irisApiSingletonTextVertexSinkAndLegacyShimWork() throws Exception {
        final Path dir = Files.createTempDirectory("agris-irisapi-test");
        PlatformServices.install(new PlatformAdapter() {
            @Override
            public String platformName() {
                return "fabric";
            }

            @Override
            public String minecraftVersion() {
                return "1.20.1";
            }

            @Override
            public Path gameDirectory() {
                return dir;
            }

            @Override
            public Path configDirectory() {
                return dir.resolve("config");
            }

            @Override
            public boolean isClient() {
                return true;
            }

            @Override
            public boolean isDevelopmentEnvironment() {
                return true;
            }

            @Override
            public boolean isModLoaded(final String modId) {
                return "agris".equals(modId);
            }

            @Override
            public String getModVersion(final String modId) {
                return "1.0.0";
            }
        });
        Agris.initialize();

        final IrisApi api = IrisApi.getInstance();
        assertNotNull(api);
        assertEquals(2, api.getMinorApiRevision());
        assertFalse(api.isRenderingShadowPass());

        api.getConfig().setShadersEnabledAndApply(false);
        assertFalse(api.getConfig().areShadersEnabled());
        api.getConfig().setShadersEnabledAndApply(true);
        assertTrue(api.getConfig().areShadersEnabled());

        assertNotNull(Iris.getCurrentPackName());
        assertEquals(Iris.getCurrentPackName(), net.coderbot.iris.Iris.getCurrentPackName());

        // Verify IrisTextVertexSink emits 4 * 28 = 112 bytes per quad without touching GL
        final AtomicReference<ByteBuffer> lastAllocated = new AtomicReference<>();
        final IrisTextVertexSink sink = api.createTextVertexSink(2, size -> {
            final ByteBuffer buf = ByteBuffer.allocate(size);
            lastAllocated.set(buf);
            return buf;
        });

        sink.quad(0f, 0f, 16f, 8f, 0.5f, 0xFF112233, 0f, 0f, 1f, 1f, 0x00F000F0);
        assertEquals(112, sink.getUnderlyingByteBuffer().position(),
                "1 quad must advance ByteBuffer by 4 * 28 = 112 bytes");
    }
}
