package dev.maximus.glasswork;

import com.mojang.logging.LogUtils;
import dev.maximus.glasswork.api.GlassworkAPI;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.util.List;

public class Glasswork implements ModInitializer {
    public static final String MOD_ID = "glasswork";
    public static final Logger LOG = LogUtils.getLogger();

    @Override
    public void onInitialize() {
        LOG.info("Initializing Glasswork");
    }
}
