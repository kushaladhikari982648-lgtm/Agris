package com.agris.compat;

import com.agris.config.AgrisConfig;
import com.agris.util.AgrisLog;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * First-class runtime bridge between <b>Agris</b> and <b>Aetherium</b> ({@code 1.x} and {@code 0.1.0}).
 *
 * <p>Uses zero hard compile-time imports against {@code com.aetherium.*} so Agris compiles
 * cleanly against Mojang mappings and runs both alongside Aetherium and standalone. When
 * Aetherium is present, this bridge coordinates:</p>
 * <ul>
 *   <li><b>Shadow-pass culling guard</b>: calls {@code SectionCull.end()} and snapshots
 *       {@code Tuning.directionalCulling} during the directional sun/moon shadow pass so
 *       chunks behind the player's camera still cast accurate shadows in front of the player.</li>
 *   <li><b>Post-processing ColorFilter deferral</b>: suppresses {@code Tuning.shaderPreset}
 *       and temporarily switches {@code AetheriumConfig.shaderPreset} to {@code OFF} while an
 *       Agris shader pack owns tonemapping so frames are never double-graded, restoring the
 *       user's Aetherium preset automatically when shaders turn off.</li>
 *   <li><b>Render scale &amp; theme sync</b>: reads Aetherium's {@code renderScale} and
 *       {@code darkMode} so Agris's UI and status HUD match Aetherium seamlessly.</li>
 *   <li><b>Android GL translator discovery</b>: queries Aetherium's {@code AndroidEnvironment}
 *       for {@code gl4es}, {@code NGGL4ES}, {@code MobileGlues}, {@code Zink}, {@code LTW},
 *       {@code ANGLE}, or {@code VirGL}.</li>
 * </ul>
 */
public final class AetheriumBridge {
    private final ClassLoader loader;
    private boolean probed;
    private boolean present;
    private boolean modernLine; // true = Aetherium 1.x, false = Aetherium 0.1.0

    private Class<?> aetheriumClass;
    private Method isActiveMethod;
    private Method configMethod;
    private Method androidMethod;

    // SectionCull & Tuning reflection handles
    private Method sectionCullEndMethod;
    private Field tuningShaderPresetField;
    private Field tuningDirectionalCullingField;
    private Field tuningGlStateCacheField;

    // Config reflection handles
    private Object configInstance;
    private Method configValueGetMethod;
    private Method configValueSetMethod;
    private Object shaderPresetConfigValue;
    private Object shaderPresetOffConstant;
    private Object renderScaleConfigValue;
    private Object darkModeConfigValue;

    // Shadow-pass & ColorFilter saved state
    private boolean shadowGuardActive;
    private boolean savedDirectionalCulling;
    private boolean colorFilterDeferred;
    private int savedTuningShaderPreset;
    private Object savedConfigShaderPreset;

    public AetheriumBridge() {
        this(AetheriumBridge.class.getClassLoader());
    }

    public AetheriumBridge(final ClassLoader loader) {
        this.loader = loader == null ? ClassLoader.getSystemClassLoader() : loader;
    }

    public synchronized void probe() {
        if (this.probed) {
            return;
        }
        this.probed = true;
        try {
            this.aetheriumClass = Class.forName("com.aetherium.Aetherium", false, this.loader);
            this.present = true;
        } catch (final ClassNotFoundException ex) {
            this.present = false;
            return;
        }

        try {
            this.isActiveMethod = this.aetheriumClass.getMethod("isActive");
            this.configMethod = this.aetheriumClass.getMethod("config");
            this.modernLine = true;
        } catch (final NoSuchMethodException ex) {
            this.modernLine = false;
        }

        try {
            this.androidMethod = this.aetheriumClass.getMethod("android");
        } catch (final NoSuchMethodException ignored) {
            this.androidMethod = null;
        }

        try {
            final Class<?> sectionCullClass = Class.forName("com.aetherium.client.SectionCull", false, this.loader);
            this.sectionCullEndMethod = sectionCullClass.getMethod("end");
        } catch (final ReflectiveOperationException ignored) {
            this.sectionCullEndMethod = null;
        }

        try {
            final Class<?> tuningClass = Class.forName("com.aetherium.perf.Tuning", false, this.loader);
            this.tuningShaderPresetField = findField(tuningClass, "shaderPreset");
            this.tuningDirectionalCullingField = findField(tuningClass, "directionalCulling");
            this.tuningGlStateCacheField = findField(tuningClass, "glStateCache");
        } catch (final ClassNotFoundException ignored) {
            // Older Aetherium line without com.aetherium.perf.Tuning.
        }

        try {
            final Class<?> cvClass = Class.forName("com.aetherium.config.ConfigValue", false, this.loader);
            this.configValueGetMethod = cvClass.getMethod("get");
            this.configValueSetMethod = cvClass.getMethod("set", Object.class);
        } catch (final ReflectiveOperationException ignored) {
            this.configValueGetMethod = null;
        }
    }

    private void refreshConfigHandles() {
        if (!this.present || this.configMethod == null || this.configInstance != null) {
            return;
        }
        try {
            final Object cfg = this.configMethod.invoke(null);
            if (cfg == null) {
                return;
            }
            this.configInstance = cfg;
            final Class<?> cfgClass = cfg.getClass();
            this.shaderPresetConfigValue = readFieldValue(cfgClass, cfg, "shaderPreset");
            this.renderScaleConfigValue = readFieldValue(cfgClass, cfg, "renderScale");
            this.darkModeConfigValue = readFieldValue(cfgClass, cfg, "darkMode");

            for (final Class<?> nested : cfgClass.getDeclaredClasses()) {
                if ("ShaderPreset".equals(nested.getSimpleName()) && nested.isEnum()) {
                    for (final Object constant : nested.getEnumConstants()) {
                        if ("OFF".equals(((Enum<?>) constant).name())) {
                            this.shaderPresetOffConstant = constant;
                            break;
                        }
                    }
                }
            }
        } catch (final Throwable ignored) {
            // Aetherium may not have initialized its Subsystems snapshot yet; retry lazily later.
        }
    }

    public boolean isPresent() {
        probe();
        return this.present;
    }

    public boolean isModernAetherium() {
        probe();
        return this.present && this.modernLine;
    }

    public boolean isAetheriumActive() {
        probe();
        if (!this.present) {
            return false;
        }
        if (this.isActiveMethod != null) {
            try {
                return Boolean.TRUE.equals(this.isActiveMethod.invoke(null));
            } catch (final Throwable ignored) {
                return false;
            }
        }
        return true;
    }

    /** Reads Aetherium's {@code darkMode} preference, defaulting to {@code true} (obsidian theme). */
    public boolean prefersDarkMode() {
        probe();
        refreshConfigHandles();
        if (this.darkModeConfigValue != null && this.configValueGetMethod != null) {
            try {
                final Object val = this.configValueGetMethod.invoke(this.darkModeConfigValue);
                if (val instanceof Boolean b) {
                    return b;
                }
            } catch (final Throwable ignored) {
                // Fall through to default dark theme.
            }
        }
        return true;
    }

    /** Reads Aetherium's {@code performance.render_scale} percentage (50..100), or 100 when absent. */
    public int getAetheriumRenderScalePercent() {
        probe();
        refreshConfigHandles();
        if (this.renderScaleConfigValue != null && this.configValueGetMethod != null) {
            try {
                final Object val = this.configValueGetMethod.invoke(this.renderScaleConfigValue);
                if (val instanceof Number n) {
                    return Math.max(25, Math.min(200, n.intValue()));
                }
            } catch (final Throwable ignored) {
                return 100;
            }
        }
        return 100;
    }

    /** Queries Aetherium's {@code AndroidEnvironment} for the detected Android renderer id, if any. */
    public String queryAndroidRendererId() {
        probe();
        if (!this.present || this.androidMethod == null) {
            return null;
        }
        try {
            final Object androidEnv = this.androidMethod.invoke(null);
            if (androidEnv == null) {
                return null;
            }
            final Method isAndroid = androidEnv.getClass().getMethod("isAndroid");
            if (!Boolean.TRUE.equals(isAndroid.invoke(androidEnv))) {
                return "none";
            }
            final Method getRenderer = androidEnv.getClass().getMethod("getRenderer");
            final Object renderer = getRenderer.invoke(androidEnv);
            if (renderer != null) {
                final Method getId = renderer.getClass().getMethod("getId");
                final Object id = getId.invoke(renderer);
                return id == null ? null : id.toString();
            }
        } catch (final Throwable ignored) {
            return null;
        }
        return null;
    }

    public boolean queryIsAndroidHost() {
        final String id = queryAndroidRendererId();
        return id != null && !"none".equalsIgnoreCase(id);
    }

    /**
     * Suspends Aetherium's camera-facing chunk culling for the duration of the shadow pass
     * so shadow casters behind the player's camera still project shadows onto visible terrain.
     */
    public void beginShadowPass(final AgrisConfig config) {
        probe();
        if (!this.present || !config.suspendCullInShadows.get() || this.shadowGuardActive) {
            return;
        }
        this.shadowGuardActive = true;
        if (this.sectionCullEndMethod != null) {
            try {
                this.sectionCullEndMethod.invoke(null);
            } catch (final Throwable ignored) {
                // Safe no-op if unavailable.
            }
        }
        if (this.tuningDirectionalCullingField != null) {
            try {
                this.savedDirectionalCulling = this.tuningDirectionalCullingField.getBoolean(null);
                if (this.savedDirectionalCulling) {
                    this.tuningDirectionalCullingField.setBoolean(null, false);
                }
            } catch (final Throwable ignored) {
                this.savedDirectionalCulling = false;
            }
        }
    }

    /** Restores Aetherium's culling state immediately after the shadow pass finishes. */
    public void endShadowPass() {
        if (!this.shadowGuardActive) {
            return;
        }
        this.shadowGuardActive = false;
        if (this.tuningDirectionalCullingField != null && this.savedDirectionalCulling) {
            try {
                this.tuningDirectionalCullingField.setBoolean(null, true);
            } catch (final Throwable ignored) {
                // Safe no-op.
            }
            this.savedDirectionalCulling = false;
        }
    }

    /**
     * Coordinates Aetherium's GL state cache and post-processing {@code ColorFilter}
     * whenever Agris enters or leaves active shader-pack rendering.
     */
    public void syncShaderPackActive(final boolean shaderPackActive, final AgrisConfig config) {
        probe();
        if (!this.present) {
            return;
        }
        refreshConfigHandles();

        // Ensure Aetherium's GL state cache stays disabled while custom GL programs run.
        if (shaderPackActive && this.tuningGlStateCacheField != null) {
            try {
                this.tuningGlStateCacheField.setBoolean(null, false);
            } catch (final Throwable ignored) {
                // Safe no-op.
            }
        }

        final boolean shouldDefer = shaderPackActive && config.deferAetheriumColorFilter.get();
        if (shouldDefer && !this.colorFilterDeferred) {
            deferColorFilter();
        } else if (!shouldDefer && this.colorFilterDeferred) {
            restoreColorFilter();
        }
    }

    private void deferColorFilter() {
        this.colorFilterDeferred = true;
        if (this.tuningShaderPresetField != null) {
            try {
                this.savedTuningShaderPreset = this.tuningShaderPresetField.getInt(null);
                if (this.savedTuningShaderPreset != 0) {
                    this.tuningShaderPresetField.setInt(null, 0);
                }
            } catch (final Throwable ignored) {
                this.savedTuningShaderPreset = 0;
            }
        }
        if (this.shaderPresetConfigValue != null
                && this.shaderPresetOffConstant != null
                && this.configValueGetMethod != null
                && this.configValueSetMethod != null) {
            try {
                final Object current = this.configValueGetMethod.invoke(this.shaderPresetConfigValue);
                if (current != null && !current.equals(this.shaderPresetOffConstant)) {
                    this.savedConfigShaderPreset = current;
                    this.configValueSetMethod.invoke(this.shaderPresetConfigValue, this.shaderPresetOffConstant);
                    AgrisLog.info("Deferred Aetherium ColorFilter ({}) while Agris shader pack owns tonemapping.", current);
                }
            } catch (final Throwable ignored) {
                this.savedConfigShaderPreset = null;
            }
        }
    }

    private void restoreColorFilter() {
        this.colorFilterDeferred = false;
        if (this.tuningShaderPresetField != null && this.savedTuningShaderPreset != 0) {
            try {
                this.tuningShaderPresetField.setInt(null, this.savedTuningShaderPreset);
            } catch (final Throwable ignored) {
                // Safe no-op.
            }
            this.savedTuningShaderPreset = 0;
        }
        if (this.shaderPresetConfigValue != null
                && this.savedConfigShaderPreset != null
                && this.configValueSetMethod != null) {
            try {
                this.configValueSetMethod.invoke(this.shaderPresetConfigValue, this.savedConfigShaderPreset);
                AgrisLog.info("Restored Aetherium ColorFilter ({}).", this.savedConfigShaderPreset);
            } catch (final Throwable ignored) {
                // Safe no-op.
            }
            this.savedConfigShaderPreset = null;
        }
    }

    public boolean isShadowGuardActive() {
        return this.shadowGuardActive;
    }

    public boolean isColorFilterDeferred() {
        return this.colorFilterDeferred;
    }

    public String describeStatus() {
        probe();
        if (!this.present) {
            return "Standalone (Aetherium not installed)";
        }
        final StringBuilder sb = new StringBuilder();
        sb.append(this.modernLine ? "Aetherium 1.x" : "Aetherium 0.1");
        sb.append(isAetheriumActive() ? " [Active" : " [Standby");
        final int scale = getAetheriumRenderScalePercent();
        if (scale != 100) {
            sb.append(", Scale ").append(scale).append('%');
        }
        if (this.colorFilterDeferred) {
            sb.append(", ColorFilter->Agris");
        }
        sb.append(']');
        return sb.toString();
    }

    private static Field findField(final Class<?> owner, final String name) {
        try {
            return owner.getField(name);
        } catch (final NoSuchFieldException ignored) {
            return null;
        }
    }

    private static Object readFieldValue(final Class<?> owner, final Object instance, final String name) {
        try {
            final Field f = owner.getField(name);
            return f.get(instance);
        } catch (final ReflectiveOperationException ignored) {
            return null;
        }
    }
}
