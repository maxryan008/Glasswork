package dev.maximus.glasswork;

import com.mojang.logging.LogUtils;
import dev.maximus.glasswork.net.GlassworkNet;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;

public class Glasswork implements ModInitializer {
    public static final String MOD_ID = "glasswork";
    public static final Logger LOG = LogUtils.getLogger();

    @Override
    public void onInitialize() {
        LOG.info("Initializing Glasswork");
        GlassworkNet.registerCommon();
    }
}
