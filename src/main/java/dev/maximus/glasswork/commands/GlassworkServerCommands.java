package dev.maximus.glasswork.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.maximus.glasswork.GlassworkMetrics;
import dev.maximus.glasswork.util.Log;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.Comparator;
import java.util.Map;

public final class GlassworkServerCommands {
    private GlassworkServerCommands() {}

    /** Hook the Fabric v2 registration callback. */
    public static void register() {
        CommandRegistrationCallback.EVENT.register(GlassworkServerCommands::registerImpl);
    }

    private static void registerImpl(CommandDispatcher<CommandSourceStack> dispatcher,
                                     CommandBuildContext ctx,
                                     Commands.CommandSelection env) {
        try {
            // Build and register the main tree: /glasswork ...
            final LiteralCommandNode<CommandSourceStack> root = buildGlassworkRoot(dispatcher);

            // Alias: /gw → redirects the entire subtree of /glasswork
            dispatcher.register(
                    Commands.literal("gw")
                            .requires(src -> src.hasPermission(2))
                            .redirect(root)
            );

            Log.d("[cmd] Registered /glasswork (and alias /gw)");
        } catch (Throwable t) {
            // Never crash command registration; log clearly so it’s easy to diagnose
            Log.e(t, "[cmd] Failed to register Glasswork commands");
        }
    }

    private static LiteralCommandNode<CommandSourceStack> buildGlassworkRoot(
            CommandDispatcher<CommandSourceStack> dispatcher) {

        return dispatcher.register(
                Commands.literal("glasswork")
                        .requires(src -> src.hasPermission(2))

                        // /glasswork stats
                        .then(Commands.literal("stats").executes(c -> {
                            final var src = c.getSource();

                            final long put   = GlassworkMetrics.totalPacketsPut();
                            final long rem   = GlassworkMetrics.totalPacketsRemove();
                            final long quads = GlassworkMetrics.totalQuadsSent();

                            src.sendSuccess(() -> Component.literal("§b[Glasswork] Server Stats"), false);
                            src.sendSuccess(() -> Component.literal("  §7Put packets: §f" + put), false);
                            src.sendSuccess(() -> Component.literal("  §7Remove packets: §f" + rem), false);
                            src.sendSuccess(() -> Component.literal("  §7Quads sent: §f" + quads), false);

                            final Map<String, Long> perPlayerPackets = GlassworkMetrics.perPlayerPacketSnapshot();
                            final Map<String, Long> perPlayerQuads   = GlassworkMetrics.perPlayerQuadSnapshot();

                            if (!perPlayerPackets.isEmpty()) {
                                src.sendSuccess(() -> Component.literal("  §7Per-player (top 8 by packets):"), false);
                                perPlayerPackets.entrySet().stream()
                                        .sorted(Comparator.<Map.Entry<String, Long>>comparingLong(Map.Entry::getValue).reversed())
                                        .limit(8)
                                        .forEach(e -> {
                                            final long q = perPlayerQuads.getOrDefault(e.getKey(), 0L);
                                            src.sendSuccess(() -> Component.literal(
                                                    "    §f" + e.getKey() + "§7 packets=" + e.getValue() + " quads=" + q
                                            ), false);
                                        });
                            }
                            return 1;
                        }))

                        // /glasswork debug <true|false>
                        .then(Commands.literal("debug")
                                .then(Commands.argument("enabled", BoolArgumentType.bool()).executes(c -> {
                                    final boolean on = BoolArgumentType.getBool(c, "enabled");
                                    Log.setDebug(on);
                                    c.getSource().sendSuccess(() -> Component.literal("§b[Glasswork] debug=" + on), false);
                                    return 1;
                                }))
                        )

                        // /glasswork trace <true|false>
                        .then(Commands.literal("trace")
                                .then(Commands.argument("enabled", BoolArgumentType.bool()).executes(c -> {
                                    final boolean on = BoolArgumentType.getBool(c, "enabled");
                                    Log.setTrace(on);
                                    c.getSource().sendSuccess(() -> Component.literal("§b[Glasswork] trace=" + on), false);
                                    return 1;
                                }))
                        )
        );
    }
}