package org.spongepowered.asm.mixin.extensibility;
public interface IMixinInfo {
    IMixinConfig getConfig();
    String getName();
    String getClassName();
    String getClassRef();
    byte[] getClassBytes();
    boolean isDetachedSuper();
    org.objectweb.asm.tree.ClassNode getClassNode(int flags);
    java.util.List<String> getTargetClasses();
    int getPriority();
}
