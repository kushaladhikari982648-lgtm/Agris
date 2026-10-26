package org.junit.jupiter.api.condition;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface EnabledIfSystemProperty {
    String named();

    String matches();
}
