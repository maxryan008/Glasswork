package dev.maximus.glasswork.client.internal.mesh;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class TranslucentMeshStore {
    public static final class TrackedMesh {
        private final MeshData mesh;
        private final ByteBufferBuilder builder;

        public TrackedMesh(MeshData mesh, ByteBufferBuilder builder) {
            this.mesh = mesh;
            this.builder = builder;
        }
        public MeshData mesh() { return mesh; }
        public void close() { if (builder != null) builder.close(); }
    }

    private static final Map<BlockPos, TrackedMesh> STORE = new ConcurrentHashMap<>();
    private static final Set<BlockPos> DIRTY = ConcurrentHashMap.newKeySet();

    private TranslucentMeshStore() {}

    public static void storeOrRemove(BlockPos origin, MeshData mesh) {
        BlockPos key = origin.immutable();
        TrackedMesh old = STORE.remove(key);
        if (old != null) old.close();
        if (mesh == null) return;
        STORE.put(key, deepCopy(mesh));
    }

    public static void replace(BlockPos origin, MeshData fresh) {
        origin = origin.immutable();
        if (fresh == null) { clear(origin); return; }
        TrackedMesh old = STORE.put(origin, deepCopy(fresh));
        if (old != null) old.close();
        DIRTY.remove(origin);
    }

    public static @Nullable TrackedMesh get(BlockPos origin) {
        return STORE.get(origin.immutable());
    }

    public static void markDirty(BlockPos origin) {
        DIRTY.add(origin.immutable());
    }

    private static TrackedMesh deepCopy(MeshData mesh) {
        MeshData.DrawState d = mesh.drawState();
        ByteBuffer src = mesh.vertexBuffer();
        int size = src.remaining();

        ByteBufferBuilder builder = new ByteBufferBuilder(size);
        long dest = builder.reserve(size);
        MemoryUtil.memCopy(MemoryUtil.memAddress(src), dest, size);

        MeshData copy = new MeshData(
                builder.build(),
                new MeshData.DrawState(d.format(), d.vertexCount(), d.indexCount(), d.mode(), d.indexType())
        );
        return new TrackedMesh(copy, builder);
    }

    public static void clear(BlockPos origin) {
        origin = origin.immutable();
        DIRTY.remove(origin);
        TrackedMesh t = STORE.remove(origin);
        if (t != null) t.close();
    }

    public static void clearAll() {
        DIRTY.clear();
        STORE.values().forEach(TrackedMesh::close);
        STORE.clear();
    }

    public static TrackedMesh merge(TrackedMesh a, MeshData b) {
        if (a == null) return new TrackedMesh(b, null);

        MeshData am = a.mesh();
        MeshData.DrawState ad = am.drawState();
        MeshData.DrawState bd = b.drawState();

        VertexFormat format = ad.format();
        if (!format.equals(bd.format())) throw new IllegalStateException("Vertex formats differ");
        if (ad.mode() != bd.mode()) throw new IllegalStateException("Vertex modes differ");

        int vertexSize = format.getVertexSize();
        int aVerts = ad.vertexCount();
        int bVerts = bd.vertexCount();
        int aBytes = aVerts * vertexSize;
        int bBytes = bVerts * vertexSize;

        ByteBuffer abuf = am.vertexBuffer();
        ByteBuffer bbuf = b.vertexBuffer();
        if (abuf.remaining() < aBytes || bbuf.remaining() < bBytes) throw new IllegalStateException("Insufficient buffer data");

        ByteBufferBuilder builder = new ByteBufferBuilder(aBytes + bBytes);
        long dest = builder.reserve(aBytes + bBytes);
        MemoryUtil.memCopy(MemoryUtil.memAddress(abuf), dest, aBytes);
        MemoryUtil.memCopy(MemoryUtil.memAddress(bbuf), dest + aBytes, bBytes);

        MeshData.DrawState mergedDraw = new MeshData.DrawState(
                format,
                aVerts + bVerts,
                ad.indexCount() + (bVerts / 4 * 6),
                ad.mode(),
                ad.indexType()
        );

        return new TrackedMesh(new MeshData(builder.build(), mergedDraw), builder);
    }
}