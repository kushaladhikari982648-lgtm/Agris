package com.agris.util;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Daemon thread factory with deterministic thread names for async pack indexing.
 */
public final class NamedThreadFactory implements ThreadFactory {
    private final String prefix;
    private final AtomicInteger counter = new AtomicInteger(1);

    public NamedThreadFactory(final String prefix) {
        this.prefix = prefix == null || prefix.isEmpty() ? "agris-worker" : prefix;
    }

    @Override
    public Thread newThread(final Runnable runnable) {
        final Thread thread = new Thread(runnable, this.prefix + '-' + this.counter.getAndIncrement());
        thread.setDaemon(true);
        thread.setPriority(Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 1));
        return thread;
    }
}
