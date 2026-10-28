package org.spongepowered.asm.mixin;
public @interface Shadow { String prefix() default "shadow$"; boolean remap() default true; String[] aliases() default {}; }
