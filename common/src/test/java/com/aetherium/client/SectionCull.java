package com.aetherium.client;

public final class SectionCull {
    public static int endCalls;

    private SectionCull() {
    }

    public static void end() {
        endCalls++;
    }
}
