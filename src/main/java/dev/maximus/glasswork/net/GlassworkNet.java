package dev.maximus.glasswork.net;

import dev.maximus.glasswork.util.Log;
import dev.maximus.glasswork.util.Safe;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/**
 * Registers custom payload types. Keep this tiny and boundary-safe.
 */
public final class GlassworkNet {
    private GlassworkNet() {}

    /** Call from mod common init. */
    public static void registerCommon() {
        Safe.run("net.registerCommon", () -> {
            PayloadTypeRegistry.playS2C().register(
                    GlassworkPackets.PutQuads.TYPE, GlassworkPackets.PutQuads.CODEC
            );
            PayloadTypeRegistry.playS2C().register(
                    GlassworkPackets.RemoveQuads.TYPE, GlassworkPackets.RemoveQuads.CODEC
            );
            Log.i("[net] Registered S2C payloads: PutQuads, RemoveQuads");
        });
    }
}