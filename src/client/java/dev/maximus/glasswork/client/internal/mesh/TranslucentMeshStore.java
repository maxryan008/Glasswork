package dev.maximus.glasswork.client.internal.mesh;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.maximus.glasswork.GlassworkMetrics;
import dev.maximus.glasswork.util.Log;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side store for translucent meshes keyed by an origin {@link BlockPos}.
 * <p>Thread-safe, backed by concurrent collections. Each stored {@link TrackedMesh} owns native memory via
 * {@link ByteBufferBuilder} and must be {@link TrackedMesh#close()}d when removed.</p>
 */
public final class TranslucentMeshStore {
    private static final Map<BlockPos, TrackedMesh> STORE = new ConcurrentHashMap<>();
    private static final Set<BlockPos> DIRTY = ConcurrentHashMap.newKeySet();

    private TranslucentMeshStore() {}

    /**
     * Store a deep-copied mesh at {@code origin}, or remove if {@code mesh} is null.
     * Frees any previously stored mesh at that key.
     */
    public static void storeOrRemove(BlockPos origin, @Nullable MeshData mesh) {
        if (origin == null) {
            Log.w("[mesh.storeOrRemove] origin=null -> no-op");
            return;
        }
        final BlockPos key = origin.immutable();

        final TrackedMesh old = STORE.remove(key);
        if (old != null) {
            old.close();
            Log.d("[mesh.storeOrRemove] freed previous mesh @{}", key);
        }

        if (mesh == null) {
            DIRTY.remove(key);
            GlassworkMetrics.recordClientMeshRemove(0, 1);
            Log.d("[mesh.storeOrRemove] removed mesh @{}", key);
            return;
        }

        final TrackedMesh copy = deepCopy(mesh);
        STORE.put(key, copy);
        GlassworkMetrics.recordClientMeshStore(sizeOf(mesh), sizeOf(copy.mesh()));
        Log.d("[mesh.storeOrRemove] stored mesh @{} bytes={}", key, sizeOf(copy.mesh()));
    }

    /**
     * Replace mesh at {@code origin} with {@code fresh}. If {@code fresh} is null, clears the entry.
     * Clears the DIRTY flag for this key.
     */
    public static void replace(BlockPos origin, @Nullable MeshData fresh) {
        if (origin == null) {
            Log.w("[mesh.replace] origin=null -> no-op");
            return;
        }
        origin = origin.immutable();

        if (fresh == null) {
            clear(origin);
            return;
        }

        final TrackedMesh copy = deepCopy(fresh);
        final TrackedMesh old = STORE.put(origin, copy);
        if (old != null) old.close();
        DIRTY.remove(origin);

        GlassworkMetrics.recordClientMeshReplace(sizeOf(fresh), sizeOf(copy.mesh()));
        Log.d("[mesh.replace] replaced mesh @{} bytes={}", origin, sizeOf(copy.mesh()));
    }

    /** Get the tracked mesh for {@code origin} (do not close it). */
    public static @Nullable TrackedMesh get(BlockPos origin) {
        if (origin == null) return null;
        return STORE.get(origin.immutable());
    }

    /** Mark the entry for {@code origin} as dirty (renderer may refresh dependent state). */
    public static void markDirty(BlockPos origin) {
        if (origin == null) return;
        DIRTY.add(origin.immutable());
        Log.t("[mesh.markDirty] {}", origin);
    }

    /**
     * Deep-copy a {@link MeshData} by cloning its vertex buffer and draw state.
     * <p>On failure (stale buffer, etc.), logs and returns a zero-byte mesh with the same format/mode but zero counts.</p>
     */
    private static TrackedMesh deepCopy(MeshData mesh) {
        final MeshData.DrawState d = mesh.drawState();

        try {
            final ByteBuffer src = mesh.vertexBuffer();
            final int size = src.remaining();

            final ByteBufferBuilder builder = new ByteBufferBuilder(size);
            final long dest = builder.reserve(size);
            MemoryUtil.memCopy(MemoryUtil.memAddress(src), dest, size);

            final MeshData copy = new MeshData(
                    builder.build(),
                    new MeshData.DrawState(d.format(), d.vertexCount(), d.indexCount(), d.mode(), d.indexType())
            );
            return new TrackedMesh(copy, builder);
        } catch (Throwable t) {
            // Fallback: valid MeshData with zero bytes; avoids crashes downstream.
            Log.w("[mesh.deepCopy] failed ({}) -> returning zero-length mesh", t.getMessage());
            final ByteBufferBuilder builder = new ByteBufferBuilder(0);
            builder.reserve(0);
            final MeshData copy = new MeshData(
                    builder.build(),
                    new MeshData.DrawState(d.format(), 0, 0, d.mode(), d.indexType())
            );
            return new TrackedMesh(copy, builder);
        }
    }

    /** Remove and free the mesh at {@code origin}, if present. */
    public static void clear(BlockPos origin) {
        if (origin == null) return;
        origin = origin.immutable();
        DIRTY.remove(origin);
        final TrackedMesh t = STORE.remove(origin);
        if (t != null) {
            final int bytes = sizeOf(t.mesh());
            t.close();
            GlassworkMetrics.recordClientMeshRemove(bytes, 1);
            Log.d("[mesh.clear] cleared mesh @{} bytesFreed={}", origin, bytes);
        }
    }

    /** Remove and free all tracked meshes. */
    public static void clearAll() {
        DIRTY.clear();
        long totalBytes = 0;
        int count = 0;
        for (TrackedMesh t : STORE.values()) {
            totalBytes += sizeOf(t.mesh());
            count++;
            t.close();
        }
        STORE.clear();
        GlassworkMetrics.recordClientMeshRemove(totalBytes, count);
        Log.d("[mesh.clearAll] cleared {} meshes, bytesFreed={}", count, totalBytes);
    }

    /**
     * Merge {@code b} into {@code a}, returning a <b>new</b> {@link TrackedMesh} that owns a new buffer.
     * <ul>
     *   <li>If {@code a} is null → returns a deep copy of {@code b}.</li>
     *   <li>If formats/modes mismatch, sizes are corrupt, or {@code a} is stale → logs and returns deep copy of {@code b}.</li>
     * </ul>
     */
    public static TrackedMesh merge(@Nullable TrackedMesh a, MeshData b) {
        if (a == null) {
            // own a copy of b so we never rely on the caller keeping b alive
            final TrackedMesh cp = deepCopy(b);
            GlassworkMetrics.recordClientMeshMerge(0, sizeOf(b), sizeOf(cp.mesh()));
            return cp;
        }

        final MeshData am = a.mesh();
        final MeshData.DrawState ad = am.drawState();
        final MeshData.DrawState bd = b.drawState();

        final VertexFormat format = ad.format();
        if (!format.equals(bd.format())) {
            Log.e("[mesh.merge] Vertex formats differ: a={} b={} -> copy(b)", ad.format(), bd.format());
            GlassworkMetrics.recordClientMeshMergeError("format_mismatch");
            return deepCopy(b);
        }
        if (ad.mode() != bd.mode()) {
            Log.e("[mesh.merge] Draw modes differ: a={} b={} -> copy(b)", ad.mode(), bd.mode());
            GlassworkMetrics.recordClientMeshMergeError("mode_mismatch");
            return deepCopy(b);
        }

        final ByteBuffer abuf;
        try {
            abuf = am.vertexBuffer();  // may throw if stale
        } catch (IllegalStateException e) {
            Log.w("[mesh.merge] stale a.vertexBuffer(): {} -> copy(b)", e.getMessage());
            GlassworkMetrics.recordClientMeshMergeError("stale_a");
            return deepCopy(b);        // tracked went stale; own b safely
        }
        final ByteBuffer bbuf = b.vertexBuffer();

        final int vertexSize = format.getVertexSize();
        final int aVerts = ad.vertexCount();
        final int bVerts = bd.vertexCount();
        final int aBytes = aVerts * vertexSize;
        final int bBytes = bVerts * vertexSize;

        if (abuf.remaining() < aBytes || bbuf.remaining() < bBytes) {
            Log.e("[mesh.merge] corrupt sizes: aRem={} aBytes={} bRem={} bBytes={} -> copy(b)",
                    abuf.remaining(), aBytes, bbuf.remaining(), bBytes);
            GlassworkMetrics.recordClientMeshMergeError("size_mismatch");
            return deepCopy(b);
        }

        final ByteBufferBuilder builder = new ByteBufferBuilder(aBytes + bBytes);
        final long dest = builder.reserve(aBytes + bBytes);
        MemoryUtil.memCopy(MemoryUtil.memAddress(abuf), dest, aBytes);
        MemoryUtil.memCopy(MemoryUtil.memAddress(bbuf), dest + aBytes, bBytes);

        final int mergedVerts = aVerts + bVerts;
        final int mergedIndices = ad.indexCount() + (bVerts / 4 * 6);

        final MeshData.DrawState mergedDraw = new MeshData.DrawState(
                format,
                mergedVerts,
                mergedIndices,
                ad.mode(),
                ad.indexType()
        );

        final MeshData merged = new MeshData(builder.build(), mergedDraw);
        final TrackedMesh out = new TrackedMesh(merged, builder);
        GlassworkMetrics.recordClientMeshMerge(aBytes, bBytes, aBytes + bBytes);
        Log.d("[mesh.merge] merged a({} B)+b({} B) -> {} verts @{} B", aBytes, bBytes, mergedVerts, aBytes + bBytes);
        return out;
    }

    /** Best-effort size of a mesh's vertex buffer (bytes). */
    private static int sizeOf(MeshData mesh) {
        try {
            return mesh.vertexBuffer().remaining();
        } catch (Throwable t) {
            Log.d("[mesh.sizeOf] failed to read size: {}", t.getMessage());
            return 0;
        }
    }

    /** A mesh plus its owning {@link ByteBufferBuilder}. Call {@link #close()} to free native memory (idempotent). */
    public static final class TrackedMesh implements AutoCloseable {
        private final MeshData mesh;
        private final ByteBufferBuilder builder;

        public TrackedMesh(MeshData mesh, ByteBufferBuilder builder) {
            this.mesh = mesh;
            this.builder = builder;
        }

        /** Borrowed reference; do not free externally. */
        public MeshData mesh() { return mesh; }

        @Override public void close() {
            if (builder != null) builder.close();
        }
    }
}