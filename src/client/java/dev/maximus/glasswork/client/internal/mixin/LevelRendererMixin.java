package dev.maximus.glasswork.client.internal.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.*;
import dev.maximus.glasswork.api.GlassworkAPI;
import dev.maximus.glasswork.api.InjectedQuad;
import dev.maximus.glasswork.api.QuadVertex;
import dev.maximus.glasswork.client.internal.mesh.TranslucentMeshStore;
import dev.maximus.glasswork.util.Log;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Injects translucent geometry into section rendering without forcing a full vanilla rebuild.
 * Two redirections:
 * <ol>
 *   <li><b>isEmpty override</b> – reports the translucent layer as non-empty if we have user quads or a tracked VBO.</li>
 *   <li><b>getBuffer redirect</b> – builds/merges/sorts and uploads a one-off translucent mesh into the section VBO.</li>
 * </ol>
 * Fail-safety: any failure in build/merge/sort/upload returns the vanilla VBO (or null) so the frame continues.
 */
@Mixin(net.minecraft.client.renderer.LevelRenderer.class)
public class LevelRendererMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private ObjectArrayList<SectionRenderDispatcher.RenderSection> visibleSections;

    /**
     * If vanilla thinks the section is empty for the translucent layer, check our sources (quads/tracked/VBO)
     * and force non-empty so the renderer asks us for a buffer.
     */
    @Redirect(
            method = "renderSectionLayer(Lnet/minecraft/client/renderer/RenderType;DDDLorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher$CompiledSection;isEmpty(Lnet/minecraft/client/renderer/RenderType;)Z")
    )
    private boolean glasswork$overrideIsEmpty(SectionRenderDispatcher.CompiledSection compiled,
                                              RenderType layer,
                                              @Local SectionRenderDispatcher.RenderSection section) {
        final boolean vanillaEmpty = !((CompiledSectionAccessor) compiled).getHasBlocks().contains(layer);
        if (layer != RenderType.translucent() || section == null) return vanillaEmpty;

        if (!vanillaEmpty) return false; // vanilla already has translucent geometry

        final SectionPos sec = SectionPos.of(section.getOrigin());
        final boolean haveQuads    = !GlassworkAPI._getQuads(sec).isEmpty();
        final boolean haveVbo      = ((RenderSectionAccessor) section).getBufferMap().get(RenderType.translucent()) != null;
        final boolean haveTracked  = TranslucentMeshStore.get(section.getOrigin()) != null;
        final boolean needsUpload  = GlassworkAPI._needsUpload(sec);

        // Non-empty if we either have quads and (somewhere to put them OR need to upload)
        final boolean nonEmpty = haveQuads && (haveTracked || haveVbo || needsUpload);
        Log.t("[mixin.isEmpty] sec=%s quads=%s vbo=%s tracked=%s needsUp=%s -> nonEmpty=%s",
                sec, haveQuads, haveVbo, haveTracked, needsUpload, nonEmpty);

        return !nonEmpty;
    }

    /**
     * When the renderer asks for the translucent buffer, inject our combined/merged mesh into the section VBO.
     * On any failure, returns the vanilla VBO (or null) to avoid breaking the frame.
     */
    @Redirect(
            method = "renderSectionLayer(Lnet/minecraft/client/renderer/RenderType;DDDLorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher$RenderSection;getBuffer(Lnet/minecraft/client/renderer/RenderType;)Lcom/mojang/blaze3d/vertex/VertexBuffer;")
    )
    private VertexBuffer glasswork$redirectGetBuffer(SectionRenderDispatcher.RenderSection section, RenderType layer) {
        // Only intercept translucent, and only when a level exists
        if (layer != RenderType.translucent() || this.minecraft.level == null) {
            return ((RenderSectionAccessor) section).getBufferMap().get(layer);
        }

        final BlockPos origin = section.getOrigin();
        final SectionPos secPos = SectionPos.of(origin);
        final Map<RenderType, VertexBuffer> origMap = ((RenderSectionAccessor) section).getBufferMap();
        VertexBuffer vanillaVbo = origMap.get(RenderType.translucent());

        // Fast exit if nothing to do
        final List<InjectedQuad> quads = GlassworkAPI._getQuads(secPos);
        if (quads.isEmpty()) return vanillaVbo;

        // Choose a vertex format/mode: prefer the tracked mesh if present
        final @Nullable TranslucentMeshStore.TrackedMesh tracked = TranslucentMeshStore.get(origin);
        final VertexFormat fmt  = (tracked != null) ? tracked.mesh().drawState().format() : DefaultVertexFormat.BLOCK;
        final VertexFormat.Mode mode = (tracked != null) ? tracked.mesh().drawState().mode()   : VertexFormat.Mode.QUADS;

        // Estimate a conservative buffer size: vertices * bytesPerVertex (cap at min 1k)
        final int estimate = Math.max(1024, quads.size() * 4 * fmt.getVertexSize());
        ByteBufferBuilder backing = null;
        MeshData injected = null;
        TranslucentMeshStore.TrackedMesh mergedTracked = null;

        try {
            // 1) Build injected mesh from quads
            backing = new ByteBufferBuilder(estimate);
            final BufferBuilder builder = new BufferBuilder(backing, mode, fmt);

            // Note: avoid logging in this loop; can be hot when many quads
            for (InjectedQuad q : quads) {
                if (q == null) continue; // be tolerant of bad inputs
                final QuadVertex v1 = q.v1(), v2 = q.v2(), v3 = q.v3(), v4 = q.v4();
                // Local-space to section origin
                addVertex(builder, v1, origin);
                addVertex(builder, v2, origin);
                addVertex(builder, v3, origin);
                addVertex(builder, v4, origin);
            }
            injected = builder.buildOrThrow();

            // 2) Merge with any tracked mesh
            MeshData merged = injected;
            if (tracked != null) {
                try {
                    mergedTracked = TranslucentMeshStore.merge(tracked, injected);
                    merged = mergedTracked.mesh();
                } catch (Throwable mergeErr) {
                    // merge() logs specifics; fall back to just our injected mesh
                    Log.w("[mixin.getBuffer] merge failed sec=%s: %s", secPos, mergeErr.getMessage());
                    mergedTracked = null;
                    merged = injected;
                }
            }

            // 3) Sort by camera distance using the fixed translucent buffer as scratch
            final SectionRenderDispatcher dispatcher = this.minecraft.levelRenderer.getSectionRenderDispatcher();
            final var fixed = ((SectionRenderDispatcherAccessor) dispatcher).getFixedBuffers();
            final Vec3 cam = this.minecraft.gameRenderer.getMainCamera().getPosition();
            final MeshData.SortState sortState = merged.sortQuads(
                    fixed.buffer(RenderType.translucent()),
                    VertexSorting.byDistance(
                            (float) (cam.x - origin.getX()),
                            (float) (cam.y - origin.getY()),
                            (float) (cam.z - origin.getZ())
                    )
            );

            // 4) Ensure the buffer map is mutable before inserting our VBO
            Map<RenderType, VertexBuffer> map = origMap;
            final boolean needClone = (map == java.util.Collections.EMPTY_MAP) || !(map instanceof HashMap<?, ?>);
            if (needClone) {
                map = new HashMap<>(map);
                ((RenderSectionAccessor) section).setBufferMap(map);
                vanillaVbo = map.get(RenderType.translucent()); // refresh lookup if we replaced map
            }

            // 5) Upload merged mesh into the section VBO
            final VertexBuffer vbo = (vanillaVbo != null) ? vanillaVbo : new VertexBuffer(VertexBuffer.Usage.STATIC);
            try {
                vbo.bind();
                vbo.upload(merged);
            } finally {
                VertexBuffer.unbind();
            }
            if (vanillaVbo == null) map.put(RenderType.translucent(), vbo);

            // 6) Ensure compiled section and mark translucent present + sort state
            SectionRenderDispatcher.CompiledSection compiled = section.getCompiled();
            if (compiled == null || ((CompiledSectionAccessor) compiled).getHasBlocks().isEmpty()) {
                final SectionRenderDispatcher.CompiledSection fresh = new SectionRenderDispatcher.CompiledSection();
                ((RenderSectionAccessor) section).invokeSetCompiled(fresh);
                compiled = fresh;
            }
            ((CompiledSectionAccessor) compiled).getHasBlocks().add(RenderType.translucent());
            ((CompiledSectionAccessor) compiled).setTransparencyState(sortState);

            // 7) Mark uploaded → prevents repeat work until version bumps
            GlassworkAPI._markUploaded(secPos);

            Log.d("[mixin.getBuffer] uploaded sec=%s quads=%d mode=%s fmt=%s", secPos, quads.size(), mode, fmt);
            return vbo;

        } catch (Throwable t) {
            // Any failure → fall back to vanilla VBO; keep the frame alive
            Log.e(t, "[mixin.getBuffer] upload failed sec=%s (returning vanilla VBO)", secPos);
            return vanillaVbo;
        } finally {
            // Free temporary/merged native buffers
            try {
                if (mergedTracked != null) mergedTracked.close();
            } catch (Throwable closeErr) {
                Log.d("[mixin.getBuffer] mergedTracked.close() failed: %s", closeErr.getMessage());
            }
            try {
                if (injected != null) injected.close();
            } catch (Throwable closeErr) {
                Log.d("[mixin.getBuffer] injected.close() failed: %s", closeErr.getMessage());
            }
            try {
                if (backing != null) backing.close();
            } catch (Throwable closeErr) {
                Log.d("[mixin.getBuffer] backing.close() failed: %s", closeErr.getMessage());
            }
        }
    }

    /** Add a vertex to the {@link BufferBuilder}, translating from world to section-local space. */
    private static void addVertex(BufferBuilder builder, QuadVertex v, BlockPos origin) {
        if (v == null) return;
        builder.addVertex(v.x() - origin.getX(), v.y() - origin.getY(), v.z() - origin.getZ())
                .setColor(v.color())
                .setUv(v.u(), v.v())
                .setOverlay(v.overlay())
                .setLight(v.light())
                .setNormal(v.nx(), v.ny(), v.nz());
    }
}