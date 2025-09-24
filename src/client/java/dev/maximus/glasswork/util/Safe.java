package dev.maximus.glasswork.util;

import java.util.concurrent.Callable;

/**
 * Boundary-safe execution helpers.
 * Use for mod init, network registration, reload listeners, and teardown.
 * DO NOT use inside per-frame rendering or inner hot loops.
 */
public final class Safe {
    private Safe() {}

    /** Run and log any Throwable; keeps the game alive, logs context once. */
    public static void run(final String what, final Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            Log.e(t, "[safe] Uncaught error during: {}", what);
        }
    }

    /** Call and return fallback on failure; logs context once. */
    public static <T> T call(final String what, final Callable<T> c, final T fallback) {
        try {
            return c.call();
        } catch (Throwable t) {
            Log.e(t, "[safe] Uncaught error during: {} (returning fallback)", what);
            return fallback;
        }
    }
}