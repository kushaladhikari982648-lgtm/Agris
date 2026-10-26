package org.spongepowered.asm.mixin.refmap;
public interface IReferenceMapper {
    boolean isDefault();
    String getResourceName();
    String getStatus();
    String getContext();
    void setContext(String context);
    String remap(String className, String reference);
    String remapWithContext(String context, String className, String reference);
}
