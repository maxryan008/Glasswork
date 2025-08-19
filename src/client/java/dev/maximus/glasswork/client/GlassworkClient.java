package dev.maximus.glasswork.client;

import dev.maximus.glasswork.Glasswork;
import dev.maximus.glasswork.api.GlassworkAPI;
import dev.maximus.glasswork.client.internal.mesh.TranslucentMeshStore;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public class GlassworkClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        Glasswork.LOG.info("[Glasswork] Client init");
        // Clean up on disconnect/stop
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            GlassworkAPI._internalClearAll();
            TranslucentMeshStore.clearAll();
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            GlassworkAPI._internalClearAll();
            TranslucentMeshStore.clearAll();
        });
    }
}
