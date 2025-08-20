package dev.maximus.glasswork.client.net;

import dev.maximus.glasswork.api.GlassworkAPI;
import dev.maximus.glasswork.net.GlassworkPackets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

public class GlassworkNet {

    /** Call from client init. */
    public static void registerClientReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(GlassworkPackets.PutQuads.TYPE, (payload, ctx) -> {
            var sec = payload.section();
            var list = payload.quads();
            ctx.client().execute(() -> {
                var level = Minecraft.getInstance().level;
                if (level != null) GlassworkAPI.put(level, sec, list);
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(GlassworkPackets.RemoveQuads.TYPE, (payload, ctx) -> {
            var sec = payload.section();
            ctx.client().execute(() -> {
                var level = Minecraft.getInstance().level;
                if (level != null) GlassworkAPI.removeAll(level, sec);
            });
        });
    }
}
