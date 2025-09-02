package dev.maximus.glasswork;

import dev.maximus.glasswork.commands.GlassworkServerCommands;
import dev.maximus.glasswork.net.GlassworkNet;
import dev.maximus.glasswork.util.Log;
import dev.maximus.glasswork.util.Safe;
import net.fabricmc.api.ModInitializer;

public final class Glasswork implements ModInitializer {
    public static final String MOD_ID = "glasswork";

    @Override
    public void onInitialize() {
        Log.i("[boot] {} initializing (common/server)", MOD_ID);

        Safe.run("registerCommonNetwork", GlassworkNet::registerCommon);
        Safe.run("registerServerCommands", GlassworkServerCommands::register);

        Log.i("[boot] {} initialized (common/server)", MOD_ID);
    }
}