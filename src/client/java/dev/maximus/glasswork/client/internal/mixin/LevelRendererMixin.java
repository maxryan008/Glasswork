package dev.maximus.glasswork.client.internal.mixin;

import com.mojang.blaze3d.vertex.*;
import dev.maximus.glasswork.api.GlassworkAPI;
import dev.maximus.glasswork.api.InjectedQuad;
import dev.maximus.glasswork.api.QuadVertex;
import dev.maximus.glasswork.client.internal.mesh.TranslucentMeshStore;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;

@Mixin(net.minecraft.client.renderer.LevelRenderer.class)
public class LevelRendererMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    @Final
    private ObjectArrayList<SectionRenderDispatcher.RenderSection> visibleSections;

    @Inject(method = "renderLevel", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;renderSectionLayer(Lnet/minecraft/client/renderer/RenderType;DDDLorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V",
            shift = At.Shift.BEFORE
    ))
    private void glasswork$inject(
            DeltaTracker tickCounter,
            boolean renderOutline,
            Camera camera,
            GameRenderer renderer,
            LightTexture lightmap,
            Matrix4f projection,
            Matrix4f viewMatrix,
            CallbackInfo ci
    ) {
        if (this.minecraft.level == null) return;

        SectionRenderDispatcher dispatcher = this.minecraft.levelRenderer.getSectionRenderDispatcher();
        var cameraPos = camera.getPosition();

        for (SectionRenderDispatcher.RenderSection section : List.copyOf(this.visibleSections)) {
            BlockPos origin = section.getOrigin();
            SectionPos sectionPos = SectionPos.of(origin);

            // Skip if no changes
            List<InjectedQuad> quads = GlassworkAPI._getQuads(sectionPos);
            if (quads.isEmpty() || !GlassworkAPI._needsUpload(sectionPos)) continue;

            // Merge base (vanilla translucent) + injected
            TranslucentMeshStore.TrackedMesh tracked = TranslucentMeshStore.get(origin);

            VertexFormat format = (tracked != null) ? tracked.mesh().drawState().format() : DefaultVertexFormat.BLOCK;
            VertexFormat.Mode mode = (tracked != null) ? tracked.mesh().drawState().mode()   : VertexFormat.Mode.QUADS;

            int estimate = Math.max(1024, quads.size() * 4 * format.getVertexSize());
            ByteBufferBuilder bytes = new ByteBufferBuilder(estimate);
            BufferBuilder builder = new BufferBuilder(bytes, mode, format);

            MeshData injected = null;
            try {
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
            } catch (Exception e) {
                // Prevent leak on failure
                bytes.close();
                continue;
            }

            TranslucentMeshStore.TrackedMesh mergedTracked = TranslucentMeshStore.merge(tracked, injected);
            MeshData merged = mergedTracked.mesh();

            // Sort relative to section origin and camera
            var fixed = ((SectionRenderDispatcherAccessor) dispatcher).getFixedBuffers();
            MeshData.SortState sortState = merged.sortQuads(
                    fixed.buffer(RenderType.translucent()),
                    VertexSorting.byDistance(
                            (float) (cameraPos.x - origin.getX()),
                            (float) (cameraPos.y - origin.getY()),
                            (float) (cameraPos.z - origin.getZ())
                    )
            );

            // Upload to section translucent VBO
            Map<RenderType, VertexBuffer> map = ((RenderSectionAccessor) section).getBufferMap();
            Object old = map.get(RenderType.translucent());
            VertexBuffer vbo = (old instanceof VertexBuffer vb) ? vb : new VertexBuffer(VertexBuffer.Usage.STATIC);

            vbo.bind();
            vbo.upload(merged);
            VertexBuffer.unbind();

            if (!(old instanceof VertexBuffer)) {
                map.put(RenderType.translucent(), vbo);
            }

            // Ensure compiled flags
            SectionRenderDispatcher.CompiledSection compiled = section.getCompiled();
            if (((CompiledSectionAccessor) compiled).getHasBlocks().isEmpty()) {
                SectionRenderDispatcher.CompiledSection fresh = new SectionRenderDispatcher.CompiledSection();
                ((RenderSectionAccessor) section).invokeSetCompiled(fresh);
                compiled = fresh;
            }
            if (((RenderSectionAccessor) section).getBufferMap().get(RenderType.translucent()) instanceof VertexBuffer) {
                ((CompiledSectionAccessor) compiled).getHasBlocks().add(RenderType.translucent());
                ((CompiledSectionAccessor) compiled).setTransparencyState(sortState);
            }

            // Mark uploaded and close temps
            GlassworkAPI._markUploaded(sectionPos);
            if (injected != null) injected.close(); // IMPORTANT: free temp
            mergedTracked.close();                   // free merged builder (keeps vanilla tracked mesh untouched)
        }

        // Note: a future "global overlay" draw pass can be added here if desired.
    }
}