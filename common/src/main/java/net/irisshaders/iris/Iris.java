package net.irisshaders.iris;

import com.agris.Agris;

import java.util.Optional;

/**
 * Compatibility facade matching {@code net.irisshaders.iris.Iris} (Iris 1.7+) so:
 * <ul>
 *   <li>Aetherium {@code 1.x}'s {@code RenderMods.PRESENT} detects an active shader mod
 *       and disables its {@code gl_state_cache} cleanly.</li>
 *   <li>Aetherium {@code 0.1.0}'s {@code IrisBridge} reads {@code Iris.getIrisConfig().getShaderPackName()}.</li>
 *   <li>Third-party Iris add-ons calling {@code Iris.getCurrentPackName()} work out of the box.</li>
 * </ul>
 */
public final class Iris {
    private static final IrisConfigCompat CONFIG_SHIM = new IrisConfigCompat();

    private Iris() {
    }

    public static IrisConfigCompat getIrisConfig() {
        return CONFIG_SHIM;
    }

    public static String getCurrentPackName() {
        if (!Agris.config().enabled.get()) {
            return "(off)";
        }
        final var pack = Agris.pipeline().loadedPackOrNull();
        if (pack != null) {
            return pack.displayName();
        }
        final String configured = Agris.config().shaderPack.get();
        return configured.isBlank() ? "(off)" : configured;
    }

    public static boolean isPackInUseQuick() {
        return Agris.pipeline().isShaderPackInUse();
    }

    public static final class IrisConfigCompat {
        public Optional<String> getShaderPackName() {
            final String name = Agris.config().shaderPack.get();
            if (!Agris.config().enabled.get() || name == null || name.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(name);
        }

        public boolean areShadersEnabled() {
            return Agris.config().enabled.get();
        }
    }
}
