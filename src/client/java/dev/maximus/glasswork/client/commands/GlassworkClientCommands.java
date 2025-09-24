package dev.maximus.glasswork.client.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.maximus.glasswork.GlassworkMetrics;
import dev.maximus.glasswork.api.GlassworkAPI;
import dev.maximus.glasswork.api.GlassworkAPI.UVMode;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;

public final class GlassworkClientCommands {
    private GlassworkClientCommands() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> registerImpl(dispatcher));
    }

    private static void registerImpl(CommandDispatcher<FabricClientCommandSource> d) {
        d.register(
                ClientCommandManager.literal("gwc")
                        // ---------- stats ----------
                        .then(ClientCommandManager.literal("stats").executes(ctx -> {
                            FabricClientCommandSource src = ctx.getSource();

                            long submits = GlassworkMetrics.clientFrameSubmits();
                            long drains  = GlassworkMetrics.clientFrameDrains();
                            long uploads = GlassworkMetrics.clientUploadsTriggered();

                            int sections   = getClientSectionCount();
                            int totalQuads = getClientQuadCount();

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

                        // ---------- put block ----------
                        // /gwc put block <block_id> <lower> <upper> [tint=#AARRGGBB] [light=15728880] [opacity=1.0] [uv=tile|stretch] [face=north]
                        .then(ClientCommandManager.literal("put")
                                .then(ClientCommandManager.literal("block")
                                        .then(ClientCommandManager.argument("block_id", StringArgumentType.string())
                                                .then(ClientCommandManager.argument("lower", ClientVec3Argument.vec3())
                                                        .then(ClientCommandManager.argument("upper", ClientVec3Argument.vec3())
                                                                .executes(ctx -> {
                                                                    return putBlockCmd(
                                                                            ctx.getSource(),
                                                                            StringArgumentType.getString(ctx, "block_id"),
                                                                            ctx.getArgument("lower", Vec3.class),
                                                                            ctx.getArgument("upper", Vec3.class),
                                                                            /*tint*/ "0xFFFFFFFF",
                                                                            /*light*/ "15728880", // 0x00F000F0
                                                                            /*opacity*/ 1.0f,
                                                                            /*uv*/ "tile",
                                                                            /*face*/ "north"
                                                                    );
                                                                })
                                                                // optional tint (string so we accept hex like 0xAARRGGBB or #RRGGBB)
                                                                .then(ClientCommandManager.argument("tint", StringArgumentType.string())
                                                                        .executes(ctx -> putBlockCmd(
                                                                                ctx.getSource(),
                                                                                StringArgumentType.getString(ctx, "block_id"),
                                                                                ctx.getArgument("lower", Vec3.class),
                                                                                ctx.getArgument("upper", Vec3.class),
                                                                                StringArgumentType.getString(ctx, "tint"),
                                                                                "15728880", 1.0f, "tile", "north"
                                                                        ))
                                                                        .then(ClientCommandManager.argument("light", StringArgumentType.string())
                                                                                .executes(ctx -> putBlockCmd(
                                                                                        ctx.getSource(),
                                                                                        StringArgumentType.getString(ctx, "block_id"),
                                                                                        ctx.getArgument("lower", Vec3.class),
                                                                                        ctx.getArgument("upper", Vec3.class),
                                                                                        StringArgumentType.getString(ctx, "tint"),
                                                                                        StringArgumentType.getString(ctx, "light"),
                                                                                        1.0f, "tile", "north"
                                                                                ))
                                                                                .then(ClientCommandManager.argument("opacity", FloatArgumentType.floatArg(0f, 1f))
                                                                                        .executes(ctx -> putBlockCmd(
                                                                                                ctx.getSource(),
                                                                                                StringArgumentType.getString(ctx, "block_id"),
                                                                                                ctx.getArgument("lower", Vec3.class),
                                                                                                ctx.getArgument("upper", Vec3.class),
                                                                                                StringArgumentType.getString(ctx, "tint"),
                                                                                                StringArgumentType.getString(ctx, "light"),
                                                                                                FloatArgumentType.getFloat(ctx, "opacity"),
                                                                                                "tile", "north"
                                                                                        ))
                                                                                        .then(ClientCommandManager.argument("uv", StringArgumentType.word())
                                                                                                .executes(ctx -> putBlockCmd(
                                                                                                        ctx.getSource(),
                                                                                                        StringArgumentType.getString(ctx, "block_id"),
                                                                                                        ctx.getArgument("lower", Vec3.class),
                                                                                                        ctx.getArgument("upper", Vec3.class),
                                                                                                        StringArgumentType.getString(ctx, "tint"),
                                                                                                        StringArgumentType.getString(ctx, "light"),
                                                                                                        FloatArgumentType.getFloat(ctx, "opacity"),
                                                                                                        StringArgumentType.getString(ctx, "uv"),
                                                                                                        "north"
                                                                                                ))
                                                                                                .then(ClientCommandManager.argument("face", StringArgumentType.word())
                                                                                                        .executes(ctx -> putBlockCmd(
                                                                                                                ctx.getSource(),
                                                                                                                StringArgumentType.getString(ctx, "block_id"),
                                                                                                                ctx.getArgument("lower", Vec3.class),
                                                                                                                ctx.getArgument("upper", Vec3.class),
                                                                                                                StringArgumentType.getString(ctx, "tint"),
                                                                                                                StringArgumentType.getString(ctx, "light"),
                                                                                                                FloatArgumentType.getFloat(ctx, "opacity"),
                                                                                                                StringArgumentType.getString(ctx, "uv"),
                                                                                                                StringArgumentType.getString(ctx, "face")
                                                                                                        )))))))))))

                                // ---------- put fluid ----------
                                // /gwc put fluid <fluid_id> <lower> <upper> [tint] [light] [opacity] [uv] [animated=true|false]
                                .then(ClientCommandManager.literal("fluid")
                                        .then(ClientCommandManager.argument("fluid_id", StringArgumentType.string())
                                                .then(ClientCommandManager.argument("lower", ClientVec3Argument.vec3())
                                                        .then(ClientCommandManager.argument("upper", ClientVec3Argument.vec3())
                                                                .executes(ctx -> putFluidCmd(
                                                                        ctx.getSource(),
                                                                        StringArgumentType.getString(ctx, "fluid_id"),
                                                                        ctx.getArgument("lower", Vec3.class),
                                                                        ctx.getArgument("upper", Vec3.class),
                                                                        "0x80FFFFFF", // default semi-transparent white
                                                                        "15728880",
                                                                        1.0f,
                                                                        "tile",
                                                                        true
                                                                ))
                                                                .then(ClientCommandManager.argument("tint", StringArgumentType.string())
                                                                        .executes(ctx -> putFluidCmd(
                                                                                ctx.getSource(),
                                                                                StringArgumentType.getString(ctx, "fluid_id"),
                                                                                ctx.getArgument("lower", Vec3.class),
                                                                                ctx.getArgument("upper", Vec3.class),
                                                                                StringArgumentType.getString(ctx, "tint"),
                                                                                "15728880",
                                                                                1.0f,
                                                                                "tile",
                                                                                true
                                                                        ))
                                                                        .then(ClientCommandManager.argument("light", StringArgumentType.string())
                                                                                .executes(ctx -> putFluidCmd(
                                                                                        ctx.getSource(),
                                                                                        StringArgumentType.getString(ctx, "fluid_id"),
                                                                                        ctx.getArgument("lower", Vec3.class),
                                                                                        ctx.getArgument("upper", Vec3.class),
                                                                                        StringArgumentType.getString(ctx, "tint"),
                                                                                        StringArgumentType.getString(ctx, "light"),
                                                                                        1.0f,
                                                                                        "tile",
                                                                                        true
                                                                                ))
                                                                                .then(ClientCommandManager.argument("opacity", FloatArgumentType.floatArg(0f, 1f))
                                                                                        .executes(ctx -> putFluidCmd(
                                                                                                ctx.getSource(),
                                                                                                StringArgumentType.getString(ctx, "fluid_id"),
                                                                                                ctx.getArgument("lower", Vec3.class),
                                                                                                ctx.getArgument("upper", Vec3.class),
                                                                                                StringArgumentType.getString(ctx, "tint"),
                                                                                                StringArgumentType.getString(ctx, "light"),
                                                                                                FloatArgumentType.getFloat(ctx, "opacity"),
                                                                                                "tile",
                                                                                                true
                                                                                        ))
                                                                                        .then(ClientCommandManager.argument("uv", StringArgumentType.word())
                                                                                                .executes(ctx -> putFluidCmd(
                                                                                                        ctx.getSource(),
                                                                                                        StringArgumentType.getString(ctx, "fluid_id"),
                                                                                                        ctx.getArgument("lower", Vec3.class),
                                                                                                        ctx.getArgument("upper", Vec3.class),
                                                                                                        StringArgumentType.getString(ctx, "tint"),
                                                                                                        StringArgumentType.getString(ctx, "light"),
                                                                                                        FloatArgumentType.getFloat(ctx, "opacity"),
                                                                                                        StringArgumentType.getString(ctx, "uv"),
                                                                                                        true
                                                                                                ))
                                                                                                .then(ClientCommandManager.argument("animated", BoolArgumentType.bool())
                                                                                                        .executes(ctx -> putFluidCmd(
                                                                                                                ctx.getSource(),
                                                                                                                StringArgumentType.getString(ctx, "fluid_id"),
                                                                                                                ctx.getArgument("lower", Vec3.class),
                                                                                                                ctx.getArgument("upper", Vec3.class),
                                                                                                                StringArgumentType.getString(ctx, "tint"),
                                                                                                                StringArgumentType.getString(ctx, "light"),
                                                                                                                FloatArgumentType.getFloat(ctx, "opacity"),
                                                                                                                StringArgumentType.getString(ctx, "uv"),
                                                                                                                BoolArgumentType.getBool(ctx, "animated")
                                                                                                        ))))))))))))
                        .then(ClientCommandManager.literal("clear")
                                .then(ClientCommandManager.literal("all").executes(ctx -> {
                                    var src = ctx.getSource();
                                    int sections = clearAllQuads();
                                    src.sendFeedback(Component.literal("§a[Glasswork] Cleared quads in " + sections + " section(s)."));
                                    return 1;
                                }))
                                .then(ClientCommandManager.literal("section").executes(ctx -> {
                                    var src = ctx.getSource();
                                    var player = src.getPlayer();
                                    if (player == null) { src.sendError(Component.literal("§cNo player found.")); return 0; }
                                    SectionPos sec = GlassworkAPI.sectionFor(player.blockPosition());
                                    int before = GlassworkAPI._getQuads(sec).size();
                                    GlassworkAPI.removeAll(sec);
                                    src.sendFeedback(Component.literal("§a[Glasswork] Cleared " + before + " quad(s) in your section " + sec + "."));
                                    return 1;
                                }))
                        )
        );
    }

    /* ===========================
       Exec helpers
       =========================== */

    private static int clearAllQuads() {
        var keys = new java.util.ArrayList<>(GlassworkAPI._debugSnapshot().keySet());
        for (var sec : keys) GlassworkAPI.removeAll(sec);
        return keys.size();
    }

    private static int putBlockCmd(FabricClientCommandSource src,
                                   String blockId,
                                   Vec3 lower, Vec3 upper,
                                   String tintStr, String lightStr, float opacity,
                                   String uvStr, String faceStr) {
        Block block = resolveBlock(blockId);
        if (block == null) {
            src.sendError(Component.literal("§cUnknown block: " + blockId));
            return 0;
        }

        // Build a vertical quad from lower (bottom-left) to upper (top-right)
        Quad quad = verticalQuad(lower, upper);

        int tint  = parseColor(tintStr, 0xFFFFFFFF);
        int light = parseIntFlexible(lightStr, 0x00F000F0);
        UVMode uv = parseUV(uvStr);

        SectionPos sec = GlassworkAPI.sectionFor(net.minecraft.core.BlockPos.containing(lower));
        GlassworkAPI.putBlockTexture(
                sec, block, faceStr,
                quad.v1, quad.v2, quad.v3, quad.v4,
                tint, light, opacity, uv
        );

        src.sendFeedback(Component.literal("§a[Glasswork] Added block quad: " + blockId + " face=" + faceStr
                + " uv=" + uv + " tint=" + hex(tint) + " light=" + light + " opacity=" + opacity));
        return 1;
    }

    private static int putFluidCmd(FabricClientCommandSource src,
                                   String fluidId,
                                   Vec3 lower, Vec3 upper,
                                   String tintStr, String lightStr, float opacity,
                                   String uvStr, boolean animated) {
        Fluid fluid = resolveFluid(fluidId);
        if (fluid == null) {
            src.sendError(Component.literal("§cUnknown fluid: " + fluidId));
            return 0;
        }

        Quad quad = verticalQuad(lower, upper);

        int tint  = parseColor(tintStr, 0x80FFFFFF);
        int light = parseIntFlexible(lightStr, 0x00F000F0);
        UVMode uv = parseUV(uvStr);

        SectionPos sec = GlassworkAPI.sectionFor(net.minecraft.core.BlockPos.containing(lower));
        GlassworkAPI.putLiquidTexture(
                sec, fluid, animated,
                quad.v1, quad.v2, quad.v3, quad.v4,
                tint, light, opacity, uv
        );

        src.sendFeedback(Component.literal("§a[Glasswork] Added fluid quad: " + fluidId
                + " animated=" + animated + " uv=" + uv
                + " tint=" + hex(tint) + " light=" + light + " opacity=" + opacity));
        return 1;
    }

    /* ===========================
       Utilities
       =========================== */

    /** Build a vertical rectangle quad from lower(bottom-left) to upper(top-right). */
    private static Quad verticalQuad(Vec3 lower, Vec3 upper) {
        // Decide orientation by dominant horizontal delta: use constant Z if |dx| >= |dz|, else constant X
        double dx = upper.x - lower.x;
        double dz = upper.z - lower.z;
        double x0 = lower.x, y0 = lower.y, z0 = lower.z;
        double x1 = upper.x, y1 = upper.y, z1 = upper.z;

        Vector3f v1, v2, v3, v4; // order: bottom-left -> bottom-right -> top-right -> top-left

        if (Math.abs(dx) >= Math.abs(dz)) {
            double z = z0; // constant Z plane
            // left/right by x
            v1 = new Vector3f((float)x0, (float)y0, (float)z);
            v2 = new Vector3f((float)x1, (float)y0, (float)z);
            v3 = new Vector3f((float)x1, (float)y1, (float)z);
            v4 = new Vector3f((float)x0, (float)y1, (float)z);
        } else {
            double x = x0; // constant X plane
            // left/right by z
            v1 = new Vector3f((float)x, (float)y0, (float)z0);
            v2 = new Vector3f((float)x, (float)y0, (float)z1);
            v3 = new Vector3f((float)x, (float)y1, (float)z1);
            v4 = new Vector3f((float)x, (float)y1, (float)z0);
        }
        return new Quad(v1, v2, v3, v4);
    }

    private record Quad(Vector3f v1, Vector3f v2, Vector3f v3, Vector3f v4) {}

    private static Block resolveBlock(String id) {
        ResourceLocation rl = safeId(id);
        return (rl == null) ? null : BuiltInRegistries.BLOCK.get(rl);
    }

    private static Fluid resolveFluid(String id) {
        ResourceLocation rl = safeId(id);
        return (rl == null) ? null : BuiltInRegistries.FLUID.get(rl);
    }

    private static ResourceLocation safeId(String s) {
        try {
            return ResourceLocation.parse(s);
        } catch (Throwable t) {
            return null;
        }
    }

    private static UVMode parseUV(String s) {
        if (s == null) return UVMode.TILE;
        String k = s.toLowerCase();
        return switch (k) {
            case "stretch", "s" -> UVMode.STRETCH;
            default -> UVMode.TILE;
        };
    }

    /** Accepts "0xAARRGGBB", "#AARRGGBB", "#RRGGBB", or decimal int; defaults alpha to 0xFF if missing. */
    private static int parseColor(String s, int fallback) {
        if (s == null) return fallback;
        try {
            String t = s.trim().toLowerCase();
            if (t.startsWith("#")) t = t.substring(1);
            if (t.startsWith("0x")) t = t.substring(2);
            long val = Long.parseUnsignedLong(t, 16);
            if (t.length() == 6) { // RRGGBB -> add full alpha
                val |= 0xFF000000L;
            }
            return (int) val;
        } catch (Throwable ignored) {
            try {
                return Integer.parseInt(s);
            } catch (Throwable ignored2) {
                return fallback;
            }
        }
    }

    /** Accepts decimal or hex with 0x prefix. */
    private static int parseIntFlexible(String s, int fallback) {
        if (s == null) return fallback;
        try {
            String t = s.trim().toLowerCase();
            if (t.startsWith("0x")) return (int) Long.parseUnsignedLong(t.substring(2), 16);
            return Integer.parseInt(t);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static String hex(int argb) {
        return String.format("0x%08X", argb);
    }

    private static int getClientSectionCount() {
        return (int) GlassworkAPI._debugSnapshot().values().stream().filter(v -> !v.isEmpty()).count();
    }

    private static int getClientQuadCount() {
        return GlassworkAPI._debugSnapshot().values().stream().mapToInt(java.util.List::size).sum();
    }
}