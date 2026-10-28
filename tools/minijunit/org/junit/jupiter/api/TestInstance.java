package org.junit.jupiter.api;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface TestInstance {
    Lifecycle value();

    enum Lifecycle { PER_METHOD, PER_CLASS }
}
