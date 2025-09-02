package dev.maximus.glasswork.util;

import dev.maximus.glasswork.Glasswork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lightweight structured logger with runtime toggles:
 *   -Dglasswork.debug=true
 *   -Dglasswork.trace=true
 *
 * You can also flip these at runtime via the setters (e.g., command handler).
 */
public final class Log {
    private static final Logger L = LoggerFactory.getLogger(Glasswork.MOD_ID);

    private static volatile boolean DEBUG =
            "true".equalsIgnoreCase(System.getProperty(Glasswork.MOD_ID + ".debug", "false"));
    private static volatile boolean TRACE =
            "true".equalsIgnoreCase(System.getProperty(Glasswork.MOD_ID + ".trace", "false"));

    private Log() {}

    public static boolean isDebugEnabled() { return DEBUG; }
    public static boolean isTraceEnabled() { return TRACE; }

    public static void setDebug(boolean enabled) { DEBUG = enabled; }
    public static void setTrace(boolean enabled) { TRACE = enabled; }

    /** Info: always on. */
    public static void i(String fmt, Object... args) { L.info(fmt, args); }

    /** Warn: always on. */
    public static void w(String fmt, Object... args) { L.warn(fmt, args); }

    /** Error: always on. */
    public static void e(String fmt, Object... args) { L.error(fmt, args); }

    /** Error with throwable first (preferred for consistent stack placement). */
    public static void e(Throwable t, String fmt, Object... args) {
        // Format first to reduce log pollution with two lines.
        L.error(String.format(fmt, args), t);
    }

    /** Debug: guarded. */
    public static void d(String fmt, Object... args) {
        if (DEBUG) L.debug(fmt, args);
    }

    /** Trace: heavily guarded. Only enable during profiling. */
    public static void t(String fmt, Object... args) {
        if (TRACE) L.trace(fmt, args);
    }
}