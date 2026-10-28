package net.irisshaders.iris.apiimpl;

import com.agris.Agris;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.api.v0.IrisApiConfig;
import net.irisshaders.iris.api.v0.IrisTextVertexSink;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.function.Function;
import java.util.function.IntFunction;

/**
 * Agris implementation of {@link IrisApi} v0.2, matching both modern Iris's
 * singleton contract ({@code IrisApiV0Impl.INSTANCE}) and Aetherium's reflective
 * {@code IrisBridge} contract.
 */
public final class IrisApiV0Impl implements IrisApi {
    public static final IrisApiV0Impl INSTANCE = new IrisApiV0Impl();

    private static volatile Function<Object, Object> screenFactory;

    private final IrisApiConfig apiConfig = new IrisApiConfig() {
        @Override
        public boolean areShadersEnabled() {
            return Agris.config().enabled.get();
        }

        @Override
        public void setShadersEnabledAndApply(final boolean enabled) {
            Agris.config().enabled.set(enabled);
            Agris.saveConfigAndReload(null);
        }
    };

    private IrisApiV0Impl() {
    }

    public static void installScreenFactory(final Function<Object, Object> factory) {
        screenFactory = factory;
    }

    @Override
    public int getMinorApiRevision() {
        return 2;
    }

    @Override
    public boolean isShaderPackInUse() {
        return Agris.pipeline().isShaderPackInUse();
    }

    @Override
    public boolean isRenderingShadowPass() {
        return Agris.pipeline().isRenderingShadowPass();
    }

    @Override
    public Object openMainIrisScreenObj(final Object parent) {
        final Function<Object, Object> factory = screenFactory;
        return factory != null ? factory.apply(parent) : parent;
    }

    @Override
    public String getMainScreenLanguageKey() {
        return "options.iris.shaderPackSelection";
    }

    @Override
    public IrisApiConfig getConfig() {
        return this.apiConfig;
    }

    @Override
    public IrisTextVertexSink createTextVertexSink(final int maxQuadCount,
                                                   final IntFunction<ByteBuffer> bufferProvider) {
        // POSITION_COLOR_TEX_LIGHTMAP = 12 + 4 + 8 + 4 = 28 bytes/vertex, 4 vertices/quad = 112 bytes/quad.
        final int stride = 28;
        final ByteBuffer buf = bufferProvider.apply(Math.max(1, maxQuadCount) * 4 * stride)
                .order(ByteOrder.nativeOrder());
        return new AgrisTextVertexSink(buf);
    }

    @Override
    public float getSunPathRotation() {
        return Agris.pipeline().currentSunPathRotation();
    }

    private static final class AgrisTextVertexSink implements IrisTextVertexSink {
        private final ByteBuffer buffer;

        AgrisTextVertexSink(final ByteBuffer buffer) {
            this.buffer = buffer;
        }

        @Override
        public VertexFormat getUnderlyingVertexFormat() {
            return DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP;
        }

        @Override
        public ByteBuffer getUnderlyingByteBuffer() {
            return this.buffer;
        }

        @Override
        public void quad(final float x1, final float y1, final float x2, final float y2, final float z,
                         final int color, final float u1, final float v1, final float u2, final float v2,
                         final int light) {
            putVertex(x1, y1, z, color, u1, v1, light);
            putVertex(x1, y2, z, color, u1, v2, light);
            putVertex(x2, y2, z, color, u2, v2, light);
            putVertex(x2, y1, z, color, u2, v1, light);
        }

        private void putVertex(final float x, final float y, final float z,
                               final int abgr, final float u, final float v, final int packedLight) {
            this.buffer.putFloat(x);
            this.buffer.putFloat(y);
            this.buffer.putFloat(z);
            this.buffer.putInt(abgr);
            this.buffer.putFloat(u);
            this.buffer.putFloat(v);
            this.buffer.putInt(packedLight);
        }
    }
}
