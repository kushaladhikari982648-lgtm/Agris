package org.spongepowered.asm.mixin.injection;
public @interface ModifyConstant { String[] method() default {}; Slice[] slice() default {}; Constant[] constant() default {}; int require() default -1; int expect() default 1; int allow() default -1; boolean remap() default true; }
