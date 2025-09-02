package dev.maximus.glasswork.util;

import static dev.maximus.glasswork.util.Log.*;

public final class Timing implements AutoCloseable {
    private final String name;
    private final long startNs;
    private final long warnBudgetUs;

    private Timing(String name, long warnBudgetUs) {
        this.name = name;
        this.warnBudgetUs = warnBudgetUs;
        this.startNs = System.nanoTime();
    }

    public static Timing trace(String name, long warnBudgetUs) {
        return isTraceEnabled() ? new Timing(name, warnBudgetUs) : null;
    }

    @Override public void close() {
        if (!isTraceEnabled()) return;
        long us = (System.nanoTime() - startNs) / 1000;
        if (us > warnBudgetUs) {
            w("[timing] {} took {} µs (> {} µs)", name, us, warnBudgetUs);
        } else {
            t("[timing] {} took {} µs", name, us);
        }
    }
}
