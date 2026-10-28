package org.spongepowered.asm.mixin.injection;
public @interface Redirect { String[] method() default {}; At at(); int require() default -1; int expect() default 1; int allow() default -1; boolean remap() default true; }
