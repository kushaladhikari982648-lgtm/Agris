package com.llamalad7.mixinextras.injector;
public @interface ModifyReturnValue { String[] method() default {}; org.spongepowered.asm.mixin.injection.At[] at(); int require() default -1; int expect() default 1; int allow() default -1; boolean remap() default true; }
