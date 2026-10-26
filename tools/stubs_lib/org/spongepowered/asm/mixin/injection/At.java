package org.spongepowered.asm.mixin.injection;
public @interface At { String value(); String target() default ""; int ordinal() default -1; boolean remap() default true; Shift shift() default Shift.NONE; int by() default 0; String[] args() default {}; int opcode() default -1;
  enum Shift { NONE, BEFORE, AFTER, BY } }
