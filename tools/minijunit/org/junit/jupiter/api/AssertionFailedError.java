package org.junit.jupiter.api;

public class AssertionFailedError extends AssertionError {
    public AssertionFailedError(final String message) {
        super(message);
    }
}
