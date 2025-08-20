package dev.maximus.glasswork.client.internal.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.*;
import dev.maximus.glasswork.api.GlassworkAPI;
import dev.maximus.glasswork.api.InjectedQuad;
import dev.maximus.glasswork.api.QuadVertex;
import dev.maximus.glasswork.client.internal.mesh.TranslucentMeshStore;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;
import java.util.Map;

@Mixin(net.minecraft.client.renderer.LevelRenderer.class)
public class LevelRendererMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private ObjectArrayList<SectionRenderDispatcher.RenderSection> visibleSections;

    @Redirect(
            method = "renderSectionLayer(Lnet/minecraft/client/renderer/RenderType;DDDLorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher$CompiledSection;isEmpty(Lnet/minecraft/client/renderer/RenderType;)Z"
            )
    )
    private boolean glasswork$overrideIsEmpty(SectionRenderDispatcher.CompiledSection compiled,
                                              RenderType layer,
                                              @Local SectionRenderDispatcher.RenderSection section) {
        boolean vanillaEmpty = !((CompiledSectionAccessor) compiled).getHasBlocks().contains(layer);
        if (layer != RenderType.translucent() || section == null) return vanillaEmpty;

        if (!vanillaEmpty) return false;

        SectionPos sec = SectionPos.of(section.getOrigin());
        boolean haveQuads   = !GlassworkAPI._getQuads(sec).isEmpty();
        boolean haveVbo     = ((RenderSectionAccessor) section).getBufferMap().get(RenderType.translucent()) != null;
        boolean haveTracked = TranslucentMeshStore.get(section.getOrigin()) != null;
        boolean needsUpload = GlassworkAPI._needsUpload(sec);

        return !(haveQuads && (haveTracked || haveVbo || needsUpload));
    }

    @Redirect(
            method = "renderSectionLayer(Lnet/minecraft/client/renderer/RenderType;DDDLorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher$RenderSection;getBuffer(Lnet/minecraft/client/renderer/RenderType;)Lcom/mojang/blaze3d/vertex/VertexBuffer;"
            )
    )
    private VertexBuffer glasswork$redirectGetBuffer(SectionRenderDispatcher.RenderSection section, RenderType layer) {
        if (layer != RenderType.translucent() || this.minecraft.level == null) {
            return (VertexBuffer) ((RenderSectionAccessor) section).getBufferMap().get(layer);
        }

        final BlockPos origin   = section.getOrigin();
        final SectionPos secPos = SectionPos.of(origin);

        final List<InjectedQuad> quads = GlassworkAPI._getQuads(secPos);

        Map<RenderType, VertexBuffer> map = ((RenderSectionAccessor) section).getBufferMap();
        VertexBuffer vanillaVbo = map.get(RenderType.translucent());
        if (quads.isEmpty()) return vanillaVbo;

        final TranslucentMeshStore.TrackedMesh tracked = TranslucentMeshStore.get(origin);

        final VertexFormat fmt         = (tracked != null) ? tracked.mesh().drawState().format() : DefaultVertexFormat.BLOCK;
        final VertexFormat.Mode mode   = (tracked != null) ? tracked.mesh().drawState().mode()   : VertexFormat.Mode.QUADS;
        final int estimate = Math.max(1024, quads.size() * 4 * fmt.getVertexSize());

        ByteBufferBuilder bytes = new ByteBufferBuilder(estimate);
        MeshData injected;
        try {
            BufferBuilder builder = new BufferBuilder(bytes, mode, fmt);
            for (InjectedQuad q : quads) {
                QuadVertex[] vs = { q.v1(), q.v2(), q.v3(), q.v4() };
                for (QuadVertex v : vs) {
                    builder.addVertex(v.x() - origin.getX(), v.y() - origin.getY(), v.z() - origin.getZ())
                            .setColor(v.color())
                            .setUv(v.u(), v.v())
                            .setOverlay(v.overlay())
                            .setLight(v.light())
                            .setNormal(v.nx(), v.ny(), v.nz());
                }
            }
            injected = builder.buildOrThrow();
        } catch (Throwable t) {
            bytes.close();
            return vanillaVbo;
        }

        TranslucentMeshStore.TrackedMesh mergedTracked = null;
        MeshData merged = injected;
        if (tracked != null) {
            mergedTracked = TranslucentMeshStore.merge(tracked, injected);
            merged = mergedTracked.mesh();
        }

        final SectionRenderDispatcher dispatcher = this.minecraft.levelRenderer.getSectionRenderDispatcher();
        final var fixed = ((SectionRenderDispatcherAccessor) dispatcher).getFixedBuffers();
        final Vec3 cam = this.minecraft.gameRenderer.getMainCamera().getPosition();

        MeshData.SortState sortState = merged.sortQuads(
                fixed.buffer(RenderType.translucent()),
                VertexSorting.byDistance(
                        (float) (cam.x - origin.getX()),
                        (float) (cam.y - origin.getY()),
                        (float) (cam.z - origin.getZ())
                )
        );

        boolean needClone = (map == java.util.Collections.EMPTY_MAP) || !(map instanceof java.util.HashMap);
        if (needClone) {
            map = new java.util.HashMap<>(map);
            ((RenderSectionAccessor) section).setBufferMap(map);
            vanillaVbo = map.get(RenderType.translucent());
        }

        VertexBuffer vbo = (vanillaVbo != null) ? vanillaVbo : new VertexBuffer(VertexBuffer.Usage.STATIC);
        try {
            vbo.bind();
            vbo.upload(merged);
        } finally {
            VertexBuffer.unbind();
        }
        if (vanillaVbo == null) map.put(RenderType.translucent(), vbo);

        SectionRenderDispatcher.CompiledSection compiled = section.getCompiled();
        if (compiled == null || ((CompiledSectionAccessor) compiled).getHasBlocks().isEmpty()) {
            SectionRenderDispatcher.CompiledSection fresh = new SectionRenderDispatcher.CompiledSection();
            ((RenderSectionAccessor) section).invokeSetCompiled(fresh);
            compiled = fresh;
        }
        ((CompiledSectionAccessor) compiled).getHasBlocks().add(RenderType.translucent());
        ((CompiledSectionAccessor) compiled).setTransparencyState(sortState);

        GlassworkAPI._markUploaded(secPos);
        if (mergedTracked != null) {
            try { mergedTracked.close(); } finally { injected.close(); }
        } else {
            injected.close();
        }

        return vbo;
    }
}