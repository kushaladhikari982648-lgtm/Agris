package net.coderbot.iris;

import java.util.Optional;

/**
 * Legacy Iris 1.6 package shim forwarding to {@link net.irisshaders.iris.Iris}.
 */
public final class Iris {
    private Iris() {
    }

    public static net.irisshaders.iris.Iris.IrisConfigCompat getIrisConfig() {
        return net.irisshaders.iris.Iris.getIrisConfig();
    }

    public static String getCurrentPackName() {
        return net.irisshaders.iris.Iris.getCurrentPackName();
    }

    public static Optional<String> getShaderPackName() {
        return net.irisshaders.iris.Iris.getIrisConfig().getShaderPackName();
    }
}
