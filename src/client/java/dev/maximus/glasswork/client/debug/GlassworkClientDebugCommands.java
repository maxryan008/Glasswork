package dev.maximus.glasswork.client.debug;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.maximus.glasswork.api.GlassworkAPI;
import dev.maximus.glasswork.api.InjectedQuad;
import dev.maximus.glasswork.api.QuadVertex;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

@Environment(EnvType.CLIENT)
public final class GlassworkClientDebugCommands {

    private static final int COLOR_ARGB = 0xBF0000FF;
    private static final int PACKED_LIGHT = 0x00F000F0;

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("debugcmd").executes(GlassworkClientDebugCommands::run)
            );
        });
    }

    private static int run(CommandContext<FabricClientCommandSource> ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return 0;

        BlockPos base = mc.player.blockPosition();
        int size = 3;

        TextureAtlas atlas = mc.getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS);
        TextureAtlasSprite sprite = atlas.getSprite(ResourceLocation.fromNamespaceAndPath("minecraft", "block/white_concrete"));

        float u0 = sprite.getU0(), v0 = sprite.getV0();
        float u1 = sprite.getU1(), v1 = sprite.getV1();

        float y = base.getY();
        float x0 = base.getX();
        float z0 = base.getZ();
        float x1 = x0 + size;
        float z1 = z0 + size;

        InjectedQuad up = new InjectedQuad(
                new QuadVertex(x0, y, z0, u0, v0, COLOR_ARGB, PACKED_LIGHT, 0, 0, 1, 0),
                new QuadVertex(x1, y, z0, u1, v0, COLOR_ARGB, PACKED_LIGHT, 0, 0, 1, 0),
                new QuadVertex(x1, y, z1, u1, v1, COLOR_ARGB, PACKED_LIGHT, 0, 0, 1, 0),
                new QuadVertex(x0, y, z1, u0, v1, COLOR_ARGB, PACKED_LIGHT, 0, 0, 1, 0)
        );

        InjectedQuad down = new InjectedQuad(
                new QuadVertex(x0, y, z1, u0, v1, COLOR_ARGB, PACKED_LIGHT, 0, 0, -1, 0),
                new QuadVertex(x1, y, z1, u1, v1, COLOR_ARGB, PACKED_LIGHT, 0, 0, -1, 0),
                new QuadVertex(x1, y, z0, u1, v0, COLOR_ARGB, PACKED_LIGHT, 0, 0, -1, 0),
                new QuadVertex(x0, y, z0, u0, v0, COLOR_ARGB, PACKED_LIGHT, 0, 0, -1, 0)
        );

        SectionPos sec = SectionPos.of(base);
        GlassworkAPI.put(mc.level, sec, List.of(up, down));

        ctx.getSource().sendFeedback(Component.literal("Spawned 3x3 blue quad (up) using white_concrete UVs."));
        return 1;
    }
}