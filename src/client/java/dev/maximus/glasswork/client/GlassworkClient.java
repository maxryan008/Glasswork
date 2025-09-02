package dev.maximus.glasswork.client;

import dev.maximus.glasswork.Glasswork;
import dev.maximus.glasswork.api.GlassworkAPI;
import dev.maximus.glasswork.client.commands.GlassworkClientCommands;
import dev.maximus.glasswork.client.internal.mesh.TranslucentMeshStore;
import dev.maximus.glasswork.client.net.GlassworkNet;
import dev.maximus.glasswork.util.Log;
import dev.maximus.glasswork.util.Safe;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

@Environment(EnvType.CLIENT)
public final class GlassworkClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        Log.i("[boot] {} initializing (client)", Glasswork.MOD_ID);

        Safe.run("registerClientReceivers", GlassworkNet::registerClientReceivers);
        Safe.run("registerClientCommands", GlassworkClientCommands::register);

        // Teardown on disconnect
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            Log.d("[lifecycle] Client DISCONNECT -> clearing client state");
            clearClientState("disconnect");
        });

        // Teardown when client stops (quits to desktop)
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            Log.d("[lifecycle] Client STOPPING -> clearing client state");
            clearClientState("client_stopping");
        });

        Log.i("[boot] {} initialized (client)", Glasswork.MOD_ID);
    }

    /**
     * Centralized, idempotent cleanup for client-side systems.
     * Keep this fast and exception-free. Use Safe.run to make failures visible but non-fatal.
     */
    private static void clearClientState(final String reason) {
        Safe.run("clearClientState[" + reason + "]:GlassworkAPI._internalClearAll",
                GlassworkAPI:: _internalClearAll);
        Safe.run("clearClientState[" + reason + "]:TranslucentMeshStore.clearAll",
                TranslucentMeshStore::clearAll);
        Log.d("[lifecycle] Client state cleared (%s)", reason);
    }
}