package org.spongepowered.asm.mixin.injection;
public @interface ModifyVariable { String[] method() default {}; At at(); int ordinal() default -1; int index() default -1; boolean argsOnly() default false; int require() default -1; boolean remap() default true; }
