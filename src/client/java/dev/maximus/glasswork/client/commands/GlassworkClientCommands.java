package dev.maximus.glasswork.client.commands;

import com.mojang.brigadier.CommandDispatcher;
import dev.maximus.glasswork.GlassworkMetrics;
import dev.maximus.glasswork.api.GlassworkAPI;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

public final class GlassworkClientCommands {
    private GlassworkClientCommands() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> registerImpl(dispatcher));
    }

    private static void registerImpl(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(
                ClientCommandManager.literal("gwc")
                        .then(ClientCommandManager.literal("stats").executes(ctx -> {
                            FabricClientCommandSource src = ctx.getSource();

                            // Frame + upload counters
                            long submits = GlassworkMetrics.clientFrameSubmits();
                            long drains  = GlassworkMetrics.clientFrameDrains();
                            long uploads = GlassworkMetrics.clientUploadsTriggered();

                            // Section + quad counts from API snapshot
                            int sections   = getClientSectionCount();
                            int totalQuads = getClientQuadCount();

                            // Mesh counters
                            long meshStores   = GlassworkMetrics.clientMeshStores();
                            long meshReplaces = GlassworkMetrics.clientMeshReplaces();
                            long meshMerges   = GlassworkMetrics.clientMeshMerges();
                            long meshRemoves  = GlassworkMetrics.clientMeshRemoves();
                            long meshBytesIn  = GlassworkMetrics.clientMeshBytesIn();
                            long meshBytesOut = GlassworkMetrics.clientMeshBytesOut();
                            long meshErrors   = GlassworkMetrics.clientMeshErrors();

                            src.sendFeedback(Component.literal("§b[Glasswork] Client Stats"));
                            src.sendFeedback(Component.literal("  §7Sections with quads: §f" + sections));
                            src.sendFeedback(Component.literal("  §7Total persistent quads: §f" + totalQuads));
                            src.sendFeedback(Component.literal("  §7Frame submits: §f" + submits + "  §7drains: §f" + drains));
                            src.sendFeedback(Component.literal("  §7Uploads triggered: §f" + uploads));
                            src.sendFeedback(Component.literal("  §7Meshes: §fstores=" + meshStores
                                    + " §7replaces=" + meshReplaces
                                    + " §7merges=" + meshMerges
                                    + " §7removes=" + meshRemoves));
                            src.sendFeedback(Component.literal("  §7Mesh bytes: §fin=" + meshBytesIn
                                    + " §7out=" + meshBytesOut
                                    + " §7errors=" + meshErrors));
                            return 1;
                        }))
        );
    }

    private static int getClientSectionCount() {
        // Count sections that currently have at least one persistent quad
        return (int) GlassworkAPI._debugSnapshot().values().stream().filter(v -> !v.isEmpty()).count();
    }

    private static int getClientQuadCount() {
        // Sum all persistent quads across sections
        return GlassworkAPI._debugSnapshot().values().stream().mapToInt(java.util.List::size).sum();
    }
}