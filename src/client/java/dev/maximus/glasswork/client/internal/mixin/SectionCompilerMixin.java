package dev.maximus.glasswork.client.internal.mixin;

import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexSorting;
import dev.maximus.glasswork.api.GlassworkAPI;
import dev.maximus.glasswork.client.internal.mesh.TranslucentMeshStore;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SectionCompiler.class)
public class SectionCompilerMixin {

    @Inject(method = "compile", at = @At("RETURN"))
    private void glasswork$onCompile(SectionPos pos,
                                     RenderChunkRegion region,
                                     VertexSorting sort,
                                     SectionBufferBuilderPack buffers,
                                     CallbackInfoReturnable<SectionCompiler.Results> cir) {
        SectionCompiler.Results results = cir.getReturnValue();
        MeshData translucent = results.renderedLayers.get(RenderType.translucent());

        TranslucentMeshStore.storeOrRemove(pos.origin(), translucent);

        GlassworkAPI._bumpGeneration(pos);
    }
}