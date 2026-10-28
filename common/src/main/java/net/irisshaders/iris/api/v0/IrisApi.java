package net.irisshaders.iris.api.v0;

import java.nio.ByteBuffer;
import java.util.function.IntFunction;

/**
 * Public Iris API v0 (minor revision 2) entry point implemented by Agris so
 * Aetherium, Distant Horizons, Entity Model Features, and other Iris-aware mods
 * interoperate natively with Agris without requiring Sodium.
 */
public interface IrisApi {
    static IrisApi getInstance() {
        return IrisApiInternal.INSTANCE;
    }

    int getMinorApiRevision();

    boolean isShaderPackInUse();

    boolean isRenderingShadowPass();

    Object openMainIrisScreenObj(Object parent);

    String getMainScreenLanguageKey();

    IrisApiConfig getConfig();

    IrisTextVertexSink createTextVertexSink(int maxQuadCount, IntFunction<ByteBuffer> bufferProvider);

    float getSunPathRotation();
}
