package dev.maximus.glasswork.util;

import dev.maximus.glasswork.Constant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Log {
    private static final Logger L = LoggerFactory.getLogger(Constant.MOD_ID);

    private static volatile boolean DEBUG =
            "true".equalsIgnoreCase(System.getProperty(Constant.MOD_ID + ".debug", "false"));
    private static volatile boolean TRACE =
            "true".equalsIgnoreCase(System.getProperty(Constant.MOD_ID + ".trace", "false"));

    private Log() {}

    public static boolean isDebugEnabled() { return DEBUG; }
    public static boolean isTraceEnabled() { return TRACE; }

    public static void setDebug(boolean enabled) { DEBUG = enabled; }
    public static void setTrace(boolean enabled) { TRACE = enabled; }

    public static void i(String fmt, Object... args) { L.info(fmt, args); }
    public static void w(String fmt, Object... args) { L.warn(fmt, args); }
    public static void e(String fmt, Object... args) { L.error(fmt, args); }
    public static void e(Throwable t, String fmt, Object... args) { L.error(String.format(fmt, args), t); }

    public static void d(String fmt, Object... args) { if (DEBUG) L.debug(fmt, args); }
    public static void t(String fmt, Object... args) { if (TRACE) L.trace(fmt, args); }
}