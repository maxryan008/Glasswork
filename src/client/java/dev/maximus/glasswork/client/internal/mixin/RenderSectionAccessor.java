package dev.maximus.glasswork.client.internal.mixin;


import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;

@Environment(EnvType.CLIENT)
@Mixin(SectionRenderDispatcher.RenderSection.class)
public interface RenderSectionAccessor {

    @Accessor("buffers")
    <K, V> Map<K, V> getBufferMap();

    @Invoker("setCompiled")
    void invokeSetCompiled(SectionRenderDispatcher.CompiledSection compiledSection);
}