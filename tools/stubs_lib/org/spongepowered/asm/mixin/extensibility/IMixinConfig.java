package org.spongepowered.asm.mixin.extensibility;
public interface IMixinConfig {
    String getName();
    String getMixinPackage();
    int getPriority();
    IMixinConfigPlugin getPlugin();
    boolean isRequired();
    java.util.Set<String> getTargets();
}
