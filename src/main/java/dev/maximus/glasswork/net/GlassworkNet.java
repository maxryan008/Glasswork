package dev.maximus.glasswork.net;

import dev.maximus.glasswork.api.GlassworkAPI;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public final class GlassworkNet {
    private GlassworkNet() {}

    /** Call from mod common init. */
    public static void registerCommon() {
        PayloadTypeRegistry.playS2C().register(GlassworkPackets.PutQuads.TYPE, GlassworkPackets.PutQuads.CODEC);
        PayloadTypeRegistry.playS2C().register(GlassworkPackets.RemoveQuads.TYPE, GlassworkPackets.RemoveQuads.CODEC);
    }
}