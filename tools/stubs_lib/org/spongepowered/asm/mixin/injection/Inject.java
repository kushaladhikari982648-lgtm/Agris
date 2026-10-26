package org.spongepowered.asm.mixin.injection;
public @interface Inject { String id() default ""; String[] method() default {}; At[] at(); boolean cancellable() default false; int require() default -1; int expect() default 1; int allow() default -1; boolean remap() default true; }
