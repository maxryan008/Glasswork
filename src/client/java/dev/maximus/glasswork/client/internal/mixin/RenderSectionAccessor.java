package dev.maximus.glasswork.client.internal.mixin;


import com.mojang.blaze3d.vertex.VertexBuffer;
import dev.maximus.glasswork.api.GlassworkAPI;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Environment(EnvType.CLIENT)
@Mixin(SectionRenderDispatcher.RenderSection.class)
public interface RenderSectionAccessor {

    @Accessor("buffers")
    Map<RenderType, VertexBuffer> getBufferMap();

    @Accessor("buffers")
    void setBufferMap(Map<RenderType, VertexBuffer> map);

    @Invoker void invokeSetCompiled(SectionRenderDispatcher.CompiledSection compiled);
}