package net.irisshaders.iris.api.v0;

/**
 * Configuration view exposed via {@link IrisApi#getConfig()}.
 */
public interface IrisApiConfig {
    boolean areShadersEnabled();

    void setShadersEnabledAndApply(boolean enabled);
}
